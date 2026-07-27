package com.github.developmentagent;

import com.github.copilot.CopilotClient;
import com.github.copilot.generated.AssistantMessageEvent;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.SessionConfig;

/**
 * DevelopmentAgent is the main entry point for the GitHub Copilot SDK powered
 * development agent. It orchestrates a workflow that:
 *
 * <ol>
 *   <li>Connects to a ticket source (e.g. Jira) to retrieve open tasks.</li>
 *   <li>For each ticket, creates a Copilot session and runs multiple
 *       specialised agents (planner, coder, reviewer) in sequence until the
 *       task is considered done.</li>
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

        // --- Quickstart: create a Copilot session and send a prompt -----------
        // See: https://docs.github.com/en/copilot/how-tos/copilot-sdk/getting-started
        try (var client = new CopilotClient()) {
            client.start().get();

            var session = client.createSession(
                    new SessionConfig()
                            .setModel("auto")
                            .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
            ).get();

            // Register a listener to print streamed assistant responses.
            session.on(AssistantMessageEvent.class, event ->
                    System.out.println("[Copilot] " + event.getData().content()));

            // Example: ask Copilot to outline a development plan for a ticket.
            var sampleTicket = new Ticket("DEV-1", "Implement login feature",
                    "Users need to be able to log in with username and password.");

            System.out.println("Processing ticket: " + sampleTicket.id() + " – " + sampleTicket.title());

            AgentWorkflow workflow = new AgentWorkflow(session);
            workflow.run(sampleTicket);

            client.stop().get();
        }

        System.out.println("=== Development Agent finished ===");
    }
}
