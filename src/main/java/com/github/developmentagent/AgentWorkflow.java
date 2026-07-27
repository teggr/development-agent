package com.github.developmentagent;

import com.github.copilot.CopilotSession;
import com.github.copilot.rpc.MessageOptions;

/**
 * Orchestrates the multi-agent workflow for a single {@link Ticket}.
 *
 * <p>The workflow consists of three sequential phases:
 * <ol>
 *   <li><b>Plan</b> – a planning agent analyses the ticket and produces an
 *       implementation plan.</li>
 *   <li><b>Implement</b> – a coding agent uses the plan to generate code
 *       changes.</li>
 *   <li><b>Review</b> – a review agent verifies the implementation meets the
 *       acceptance criteria described in the ticket.</li>
 * </ol>
 *
 * <p>Each phase sends a prompt to the shared Copilot session and waits for the
 * response before proceeding to the next phase. The session accumulates context
 * across all phases, so later agents benefit from earlier responses.
 */
public class AgentWorkflow {

    private final CopilotSession session;

    /**
     * Creates a workflow that sends prompts through the supplied Copilot session.
     *
     * @param session an active Copilot session.
     */
    public AgentWorkflow(CopilotSession session) {
        this.session = session;
    }

    /**
     * Runs the full planning → implementation → review cycle for the given ticket.
     *
     * @param ticket the ticket to process.
     * @throws Exception if any agent phase fails.
     */
    public void run(Ticket ticket) throws Exception {
        System.out.println("[Workflow] Starting workflow for ticket: " + ticket.id());

        plan(ticket);
        implement(ticket);
        review(ticket);

        System.out.println("[Workflow] Workflow complete for ticket: " + ticket.id());
    }

    // -------------------------------------------------------------------------
    // Private agent phases
    // -------------------------------------------------------------------------

    private void plan(Ticket ticket) throws Exception {
        System.out.println("[Planner] Analysing ticket and producing implementation plan...");
        String prompt = String.format(
                "You are a senior software architect. Analyse the following development ticket "
                + "and produce a clear, step-by-step implementation plan.\n\n"
                + "Ticket ID  : %s\n"
                + "Title      : %s\n"
                + "Description: %s\n\n"
                + "Provide numbered steps only, no code yet.",
                ticket.id(), ticket.title(), ticket.description());

        session.sendAndWait(new MessageOptions().setPrompt(prompt)).get();
    }

    private void implement(Ticket ticket) throws Exception {
        System.out.println("[Coder] Generating implementation based on the plan...");
        String prompt = String.format(
                "You are a senior software engineer. Using the plan you just produced, "
                + "write the Java implementation for ticket %s. "
                + "Include all necessary classes and unit tests.",
                ticket.id());

        session.sendAndWait(new MessageOptions().setPrompt(prompt)).get();
    }

    private void review(Ticket ticket) throws Exception {
        System.out.println("[Reviewer] Reviewing the implementation...");
        String prompt = String.format(
                "You are a code reviewer. Review the implementation you just produced for "
                + "ticket %s and verify it meets the acceptance criteria: \"%s\". "
                + "Provide a concise review with a PASS or FAIL verdict.",
                ticket.id(), ticket.description());

        session.sendAndWait(new MessageOptions().setPrompt(prompt)).get();
    }
}
