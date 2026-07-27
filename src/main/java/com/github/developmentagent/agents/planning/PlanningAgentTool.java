package com.github.developmentagent.agents.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.AllowCopilotExperimental;
import com.github.copilot.CopilotClient;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.tool.CopilotTool;
import com.github.copilot.tool.CopilotToolParam;
import com.github.developmentagent.agents.workflow.ExecutionPlan;
import com.github.developmentagent.agents.workflow.PlanningTool;
import com.github.developmentagent.agents.workflow.TicketBrief;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.springframework.stereotype.Component;

/**
 * Planning Agent tool invoked by the Delivery Agent to produce an implementation plan.
 *
 * <p>The Delivery Agent defines the contract: it calls {@link #createExecutionPlan} with
 * the JSON output of the Jira Agent and expects back a JSON-serialised {@link ExecutionPlan}
 * with ordered steps, likely affected packages/files, required tests, and known risks.
 *
 * <p>Each invocation opens a dedicated Copilot sub-session, directs it to act as a senior
 * architect, and closes the session once the plan is produced. The plan is also persisted
 * to disk so it can be consumed by subsequent agents.
 */
@Component
class PlanningAgentTool implements PlanningTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final CopilotClient client;

    /**
     * Called after each successful plan is produced.  The Delivery Agent uses this to
     * capture the plan without polling or side-channels.
     */
 //   private final Consumer<String> onPlanProduced;

    /**
     * Creates a Planning Agent tool that opens sub-sessions via the supplied client.
     *
     * @param client         an already-started {@link CopilotClient}.
     * @param onPlanProduced callback invoked with the raw plan JSON once the plan is ready.
     */
    public PlanningAgentTool(CopilotClient client) {
        // Capture the plan JSON produced by the Planning Agent tool
        var planJsonHolder = new AtomicReference<String>();

        this.client = client;
        //this.onPlanProduced = onPlanProduced;
    }

    /**
     * Creates a detailed implementation plan from a ticket brief.
     *
     * <p>This is the tool contract as defined by the Delivery Agent: it specifies what
     * input is needed (the {@link TicketBrief} as JSON from the Jira Agent) and what
     * structured output is expected (an {@link ExecutionPlan} serialised as JSON).
     *
     * @param ticketBriefJson JSON string representing the {@link TicketBrief} from the Jira Agent.
     * @return JSON string representing an {@link ExecutionPlan}.
     */
    @Override
    @AllowCopilotExperimental
    @CopilotTool("Creates a detailed implementation plan from a ticket brief JSON produced by the "
            + "Jira Agent. Returns an execution plan as JSON with: ticketId, steps (ordered list), "
            + "likelyPackages, likelyFiles, requiredTests, risks, and doneCriteria.")
    public ExecutionPlan createExecutionPlan(  TicketBrief ticketBriefJson) {

        System.out.println("[PlanningAgent] Creating execution plan...");

        var lastContent = new AtomicReference<String>();

        try {
            var session = client.createSession(new SessionConfig()
                    .setModel("auto")
                    .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
            ).get();

            session.on(AssistantMessageEvent.class, event ->
                    lastContent.set(event.getData().content()));

            String prompt = """
                    You are the Planning Agent — a senior software architect. Your responsibility is
                    to analyse a ticket brief and produce a thorough, machine-readable implementation
                    plan that a Code Agent can execute step by step.

                    Ticket brief (JSON):
                    ---
                    %s
                    ---

                    Produce a detailed implementation plan. Return ONLY a valid JSON object with this
                    exact structure. Do not include markdown fences, explanations, or any other text —
                    pure JSON only:

                    {
                      "ticketId": "ticket id from the brief",
                      "steps": [
                        "Step 1: ...",
                        "Step 2: ..."
                      ],
                      "likelyPackages": [
                        "com.example.package"
                      ],
                      "likelyFiles": [
                        "src/main/java/com/example/Foo.java"
                      ],
                      "requiredTests": [
                        "Unit test for ...",
                        "Integration test for ..."
                      ],
                      "risks": [
                        "Risk: ..."
                      ],
                      "doneCriteria": "single sentence describing when the ticket is complete"
                    }
                    """.formatted(ticketBriefJson);

            session.sendAndWait(new MessageOptions().setPrompt(prompt)).get();
            session.close();

            String content = lastContent.get();
            if (content != null && !content.isBlank()) {
                String json = extractJson(content);
                // Validate parseable before returning
                ExecutionPlan plan = MAPPER.readValue(json, ExecutionPlan.class);
                System.out.println("[PlanningAgent] Execution plan produced for ticket: " + plan.ticketId());
                persistPlan(plan.ticketId(), json);
               // onPlanProduced.accept(json);
                return plan;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("[PlanningAgent] Interrupted while creating plan: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("[PlanningAgent] Error creating plan: " + e.getMessage());
        }

        // Fallback to a minimal structural response so the workflow can continue
        // String fallback = buildFallbackPlan(ticketBriefJson);
        // onPlanProduced.accept(fallback);
        return null;
    }

    /**
     * Persists the execution plan as a Markdown file in the current working directory.
     *
     * @param ticketId the ticket identifier used to name the file.
     * @param planJson the raw JSON of the execution plan.
     */
    private static void persistPlan(String ticketId, String planJson) {
        try {
            ExecutionPlan plan = MAPPER.readValue(planJson, ExecutionPlan.class);
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FMT);
            String sanitizedId = ticketId.replaceAll("[^A-Za-z0-9_\\-]", "_");
            Path outputPath = Path.of("implementation-plan-" + sanitizedId + "-" + timestamp + ".md");

            String markdown = buildMarkdown(plan);
            Files.writeString(outputPath, markdown);
            System.out.println("[PlanningAgent] Implementation plan persisted to: " + outputPath);
        } catch (IOException e) {
            System.err.println("[PlanningAgent] Failed to persist plan: " + e.getMessage());
        }
    }

    private static String buildMarkdown(ExecutionPlan plan) {
        var sb = new StringBuilder();
        sb.append("# Implementation Plan: ").append(plan.ticketId()).append("\n\n");

        sb.append("## Steps\n\n");
        for (String step : plan.steps()) {
            sb.append("- ").append(step).append("\n");
        }

        sb.append("\n## Likely Packages\n\n");
        for (String pkg : plan.likelyPackages()) {
            sb.append("- `").append(pkg).append("`\n");
        }

        sb.append("\n## Likely Files\n\n");
        for (String file : plan.likelyFiles()) {
            sb.append("- `").append(file).append("`\n");
        }

        sb.append("\n## Required Tests\n\n");
        for (String test : plan.requiredTests()) {
            sb.append("- ").append(test).append("\n");
        }

        sb.append("\n## Risks\n\n");
        for (String risk : plan.risks()) {
            sb.append("- ").append(risk).append("\n");
        }

        sb.append("\n## Done Criteria\n\n");
        sb.append(plan.doneCriteria()).append("\n");

        return sb.toString();
    }

    /**
     * Strips optional markdown code fences from an AI response and returns the first
     * JSON object found.
     */
    private static String extractJson(String content) {
        if (content == null) {
            return "{}";
        }
        String stripped = content
                .replaceAll("(?s)```(?:json)?\\s*", "")
                .replaceAll("(?s)```\\s*$", "")
                .trim();

        int start = stripped.indexOf('{');
        int end = stripped.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return stripped.substring(start, end + 1);
        }
        return stripped;
    }

    private static String buildFallbackPlan(String ticketBriefJson) {
        String ticketId = "UNKNOWN";
        try {
            TicketBrief brief = MAPPER.readValue(ticketBriefJson, TicketBrief.class);
            ticketId = brief.id();
        } catch (Exception ignored) {
            // Use default ticketId
        }
        try {
            return MAPPER.writeValueAsString(new ExecutionPlan(
                    ticketId,
                    List.of("Analyse requirements", "Implement solution", "Add tests", "Review"),
                    List.of(),
                    List.of(),
                    List.of("Unit tests for new code"),
                    List.of("Unable to produce detailed plan — manual review required"),
                    "All acceptance criteria met"
            ));
        } catch (Exception e) {
            return "{\"ticketId\":\"" + ticketId + "\",\"steps\":[],\"likelyPackages\":[],"
                    + "\"likelyFiles\":[],\"requiredTests\":[],\"risks\":[],\"doneCriteria\":\"\"}";
        }
    }
}
