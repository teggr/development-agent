package com.github.developmentagent;

import com.github.copilot.CopilotClient;
import com.github.developmentagent.domain.ExecutionPlan;
import com.github.developmentagent.workflow.DeliveryWorkflow;

/**
 * DevelopmentAgent is the main entry point for the GitHub Copilot SDK powered
 * development agent.
 *
 * <p>It implements the <em>agents-as-tools</em> orchestration pattern described in
 * {@code docs/architecture/agents-as-tools-first-pass.md}:
 *
 * <ol>
 *   <li>The {@link DeliveryWorkflow} acts as the Delivery Agent — the top-level orchestrator.</li>
 *   <li>The Delivery Agent registers specialist agents (Jira, Planning) as tools on its session.</li>
 *   <li>The model drives the workflow by calling tools in sequence; each tool runs its own
 *       dedicated sub-session.</li>
 *   <li>Structured data ({@link com.github.developmentagent.domain.TicketBrief},
 *       {@link ExecutionPlan}) flows between agents as typed, JSON-serialised values.</li>
 * </ol>
 *
 * <p><b>SDK references:</b>
 * <ul>
 *   <li>Java SDK source: <a href="https://github.com/github/copilot-sdk/tree/main/java">
 *       https://github.com/github/copilot-sdk/tree/main/java</a></li>
 *   <li>Cookbook examples: <a href="https://github.com/github/awesome-copilot/blob/main/cookbook/copilot-sdk/java/README.md">
 *       https://github.com/github/awesome-copilot/blob/main/cookbook/copilot-sdk/java/README.md</a></li>
 *   <li>Getting started guide: <a href="https://docs.github.com/en/copilot/how-tos/copilot-sdk/getting-started">
 *       https://docs.github.com/en/copilot/how-tos/copilot-sdk/getting-started</a></li>
 *   <li>Javadoc: <a href="https://github.github.com/copilot-sdk-java/1.0.8/">
 *       https://github.github.com/copilot-sdk-java/1.0.8/</a></li>
 * </ul>
 */
public class DevelopmentAgent {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Development Agent starting ===");

        // Ticket key to process — use the first CLI argument or fall back to the sample.
        String ticketKey = args.length > 0 ? args[0] : "DEV-1";
        System.out.println("Processing ticket: " + ticketKey);

        try (var client = new CopilotClient()) {
            client.start().get();

            var workflow = new DeliveryWorkflow(client);
            ExecutionPlan plan = workflow.processTicket(ticketKey);

            if (plan != null) {
                System.out.println("\n=== Execution Plan Summary ===");
                System.out.println("Ticket  : " + plan.ticketId());
                System.out.println("Steps   : " + plan.steps().size());
                System.out.println("Risks   : " + plan.risks().size());
                System.out.println("Done criteria: " + plan.doneCriteria());
            } else {
                System.out.println("[DevelopmentAgent] No plan produced — check agent logs above.");
            }

            client.stop().get();
        }

        System.out.println("=== Development Agent finished ===");
    }
}
