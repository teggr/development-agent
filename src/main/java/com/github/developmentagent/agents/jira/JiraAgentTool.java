package com.github.developmentagent.agents.jira;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.AllowCopilotExperimental;
import com.github.copilot.CopilotClient;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.tool.CopilotTool;
import com.github.copilot.tool.CopilotToolParam;
import com.github.developmentagent.agents.workflow.TicketBrief;
import com.github.developmentagent.agents.workflow.TicketTool;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

/**
 * Jira Agent tool invoked by the Delivery Agent to fetch and normalise ticket data.
 *
 * <p>The Delivery Agent defines the contract: it calls {@link #fetchTicketBrief} with a
 * ticket key and expects back a JSON-serialised {@link TicketBrief} containing the goal,
 * acceptance criteria, description, and linked issues.
 *
 * <p>Each invocation opens a dedicated Copilot sub-session, directs it to act as a Jira
 * integration specialist, and closes the session once the brief is produced.
 */
@Component
class JiraAgentTool implements TicketTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Sample ticket data keyed by ticket ID, used when a real Jira instance is unavailable. */
    private static final Map<String, String> SAMPLE_TICKETS = Map.of(
            "DEV-1", """
                    Ticket: DEV-1
                    Title: Implement MVP orchestration: Delivery Agent → Jira Agent → Planning Agent
                    Description:
                    Implement the first working orchestration where a Delivery Agent delegates work
                    to specialist agents. Initial flow: Delivery Agent → Jira Agent → Planning Agent
                    → Persist implementation plan. The focus is orchestration, delegation, and state
                    flow rather than code generation.
                    Acceptance Criteria:
                    - Delivery Agent orchestrates specialist agents without embedding their logic.
                    - Agent outputs are passed between agents using structured data.
                    - A complete implementation plan is generated from a Jira ticket.
                    - The implementation aligns with the agents-as-tools architecture.
                    Links: none
                    """
    );

    private final CopilotClient client;

    /**
     * Creates a Jira Agent tool that opens sub-sessions via the supplied client.
     *
     * @param client an already-started {@link CopilotClient}.
     */
    public JiraAgentTool(CopilotClient client) {
        this.client = client;
    }

    /**
     * Fetches a Jira ticket by key and returns a structured ticket brief.
     *
     * <p>This is the tool contract as defined by the Delivery Agent: it specifies
     * what input is needed (a ticket key) and what structured output is expected
     * (a {@link TicketBrief} serialised as JSON).
     *
     * @param ticketKey the Jira ticket key, e.g. {@code "DEV-1"}.
     * @return JSON string representing a {@link TicketBrief}.
     */
    @Override
    @AllowCopilotExperimental
    @CopilotTool("Fetches a Jira ticket by key and returns a structured ticket brief as JSON. "
            + "The brief contains: id, title, goal, description, acceptanceCriteria (array), "
            + "and linkedIssues (array).")
    public TicketBrief fetchTicketBrief(
            @CopilotToolParam("The Jira ticket key to fetch, e.g. DEV-123") String ticketKey) {

        System.out.println("[JiraAgent] Fetching ticket brief for: " + ticketKey);

        String rawTicketData = SAMPLE_TICKETS.getOrDefault(ticketKey,
                "Ticket key: " + ticketKey + " (no additional data – generate a plausible brief)");

        var lastContent = new AtomicReference<String>();

        try {
            var session = client.createSession(new SessionConfig()
                    .setModel("auto")
                    .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
            ).get();

            session.on(AssistantMessageEvent.class, event ->
                    lastContent.set(event.getData().content()));

            String prompt = """
                    You are the Jira Agent. Your responsibility is to extract and normalise ticket
                    information into a structured brief for downstream agents.

                    Raw Jira ticket data:
                    ---
                    %s
                    ---

                    Extract and return ONLY a valid JSON object with this exact structure. Do not
                    include markdown fences, explanations, or any other text — pure JSON only:

                    {
                      "id": "ticket key",
                      "title": "short descriptive title",
                      "goal": "single sentence describing what needs to be achieved",
                      "description": "full description of the work",
                      "acceptanceCriteria": ["criterion 1", "criterion 2"],
                      "linkedIssues": ["linked ticket key or empty array"]
                    }
                    """.formatted(rawTicketData);

            session.sendAndWait(new MessageOptions().setPrompt(prompt)).get();
            session.close();

            String content = lastContent.get();
            if (content != null && !content.isBlank()) {
                String json = extractJson(content);
                // Validate parseable before returning
                return MAPPER.readValue(json, TicketBrief.class);
                // System.out.println("[JiraAgent] Ticket brief produced for: " + ticketKey);
                // return json;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[JiraAgent] Interrupted while fetching ticket: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[JiraAgent] Error fetching ticket: " + e.getMessage());
        }

        // Fallback to a minimal structural response so the workflow can continue
        return null;
    }

    /**
     * Strips optional markdown code fences from an AI response and returns the first
     * JSON object found.
     */
    private static String extractJson(String content) {
        if (content == null) {
            return "{}";
        }
        // Remove ```json ... ``` or ``` ... ``` fences
        String stripped = content
                .replaceAll("(?s)```(?:json)?\\s*", "")
                .replaceAll("(?s)```\\s*$", "")
                .trim();

        // Find the first '{' and last '}' to isolate the JSON object
        int start = stripped.indexOf('{');
        int end = stripped.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return stripped.substring(start, end + 1);
        }
        return stripped;
    }

    private static String buildFallbackBrief(String ticketKey) {
        try {
            return MAPPER.writeValueAsString(new TicketBrief(
                    ticketKey,
                    "Ticket " + ticketKey,
                    "Implement the requirements described in ticket " + ticketKey,
                    "No description available",
                    List.of("Implementation meets the requirements"),
                    List.of()
            ));
        } catch (Exception e) {
            return "{\"id\":\"" + ticketKey + "\",\"title\":\"" + ticketKey + "\","
                    + "\"goal\":\"Implement ticket requirements\","
                    + "\"description\":\"No description\","
                    + "\"acceptanceCriteria\":[],\"linkedIssues\":[]}";
        }
    }
}
