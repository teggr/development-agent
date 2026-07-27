package com.github.developmentagent.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.copilot.CopilotClient;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.SessionConfig;
import com.github.copilot.rpc.ToolDefinition;
import com.github.developmentagent.agents.jira.JiraAgentTool;
import com.github.developmentagent.agents.planning.PlanningAgentTool;
import com.github.developmentagent.domain.ExecutionPlan;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Delivery Agent — the top-level orchestrator for a single ticket.
 *
 * <p>The Delivery Agent owns the end-to-end workflow state machine. It does not perform
 * specialist work itself; instead it delegates to specialist agents exposed as tools:
 *
 * <ol>
 *   <li>{@link JiraAgentTool} — fetches and normalises the Jira ticket.</li>
 *   <li>{@link PlanningAgentTool} — produces the implementation plan.</li>
 * </ol>
 *
 * <p>This is the core of the <em>agents-as-tools</em> pattern: the Delivery Agent defines
 * the tool contract (what each specialist agent must accept and return), registers the
 * tools on its own Copilot session, then prompts the model to drive the orchestration.
 * The model calls the tools in sequence; each tool opens its own dedicated sub-session.
 */
public class DeliveryWorkflow {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final CopilotClient client;

    /**
     * Creates a delivery workflow that uses the supplied client to create sessions.
     *
     * @param client an already-started {@link CopilotClient}.
     */
    public DeliveryWorkflow(CopilotClient client) {
        this.client = client;
    }

    /**
     * Processes a Jira ticket through the full Delivery → Jira → Planning pipeline.
     *
     * <ol>
     *   <li>Registers the Jira Agent and Planning Agent as tools on the Delivery session.</li>
     *   <li>Prompts the Delivery Agent model to orchestrate the workflow.</li>
     *   <li>The model calls each tool; results are passed between agents as structured JSON.</li>
     *   <li>The Planning Agent persists the plan and notifies this workflow via callback.</li>
     * </ol>
     *
     * @param ticketKey the Jira ticket key to process, e.g. {@code "DEV-1"}.
     * @return the produced {@link ExecutionPlan}, or {@code null} if the workflow did not
     *         succeed in generating one.
     * @throws Exception if the Copilot session cannot be created or the prompt fails.
     */
    public ExecutionPlan processTicket(String ticketKey) throws Exception {
        System.out.println("[DeliveryAgent] Starting workflow for ticket: " + ticketKey);

        // Capture the plan JSON produced by the Planning Agent tool
        var planJsonHolder = new AtomicReference<String>();

        // Instantiate specialist agents. The Delivery Agent determines their contracts.
        var jiraAgentTool = new JiraAgentTool(client);
        var planningAgentTool = new PlanningAgentTool(client, planJsonHolder::set);

        // Register specialist agents as tools on the Delivery Agent session
        var tools = new ArrayList<ToolDefinition>();
        tools.addAll(ToolDefinition.fromObject(jiraAgentTool));
        tools.addAll(ToolDefinition.fromObject(planningAgentTool));

        var session = client.createSession(new SessionConfig()
                .setModel("auto")
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
                .setTools(tools)
        ).get();

        session.on(AssistantMessageEvent.class, event ->
                System.out.println("[DeliveryAgent] " + event.getData().content()));

        // Orchestration prompt: the Delivery Agent drives the workflow but does not
        // perform specialist work — it delegates to the registered tools.
        String prompt = """
                You are the Delivery Agent. Your role is to orchestrate the development workflow
                for a Jira ticket by delegating to specialist agents via their tools.

                Process ticket: %s

                Execute the following workflow in strict order — do not skip steps:

                Step 1 — Jira Agent: Call fetchTicketBrief with ticketKey="%s" to retrieve
                the structured ticket brief from the Jira system.

                Step 2 — Planning Agent: Call createExecutionPlan with the full JSON string
                returned by fetchTicketBrief in Step 1. Do not modify the JSON.

                Step 3 — Confirm: Report that the implementation plan has been created and
                persisted, and provide a brief summary of the plan's key steps and risks.

                Important: pass the exact JSON output from Step 1 as input to Step 2.
                """.formatted(ticketKey, ticketKey);

        session.sendAndWait(new MessageOptions().setPrompt(prompt)).get();
        session.close();

        System.out.println("[DeliveryAgent] Workflow complete for ticket: " + ticketKey);

        String planJson = planJsonHolder.get();
        if (planJson != null) {
            return MAPPER.readValue(planJson, ExecutionPlan.class);
        }
        return null;
    }
}
