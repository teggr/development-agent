# Copilot AI Instructions

This repository is a GitHub Copilot SDK powered development agent written in Java 25 and built with Maven.
The agent takes tickets from a source system (e.g. Jira) and runs a multi-agent workflow to plan, implement,
and review each task.

## Core Principle: Agents as Tools

**The agents-as-tools pattern is the foundational design principle of this codebase.**

- Every specialist capability (Jira, Planning, Code, Test, Review, Git, PR) is implemented as a tool.
- A calling agent determines the **tool interface/contract** for each downstream agent.
  The caller specifies what it needs as input and what structured output it expects back.
- The Delivery Agent is the top-level orchestrator. It registers specialist agents as tools on its
  Copilot session and delegates all specialist work — it never performs specialist work directly.
- Each tool invocation opens a **dedicated sub-session** for the specialist agent, then closes it.
- Structured data flows between agents as typed, JSON-serialised domain objects
  (`TicketBrief`, `ExecutionPlan`, etc.).

### Agents-as-tools example

```java
// The Delivery Agent (caller) defines the tool contract for each downstream agent.
// It specifies what it needs (ticketKey) and what it expects back (TicketBrief JSON).

public class JiraAgentTool {
    @CopilotTool("Fetches a Jira ticket and returns a structured ticket brief as JSON")
    public String fetchTicketBrief(
            @CopilotToolParam("The Jira ticket key, e.g. DEV-123") String ticketKey) {
        // Open a dedicated sub-session for this specialist agent
        var session = client.createSession(new SessionConfig()
                .setModel("auto")
                .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
        ).get();
        // ... agent does specialist work ...
        session.close();
        return ticketBriefJson; // typed, structured output
    }
}

// In the Delivery Agent, register specialist agents as tools:
var tools = new ArrayList<>(ToolDefinition.fromObject(new JiraAgentTool(client)));
tools.addAll(ToolDefinition.fromObject(new PlanningAgentTool(client, onPlanProduced)));

var session = client.createSession(new SessionConfig()
        .setModel("auto")
        .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
        .setTools(tools)  // ← specialist agents registered as tools
).get();
```

## SDK References

When writing code that interacts with the GitHub Copilot SDK, always consult the following references:

| Resource | URL |
|----------|-----|
| Java SDK source & README | https://github.com/github/copilot-sdk/tree/main/java |
| Java Cookbook (recipes) | https://github.com/github/awesome-copilot/blob/main/cookbook/copilot-sdk/java/README.md |
| Getting started guide | https://docs.github.com/en/copilot/how-tos/copilot-sdk/getting-started |
| Javadoc (1.0.8) | https://github.github.com/copilot-sdk-java/1.0.8/ |

## Key SDK Patterns

### Creating a client and session

```java
import com.github.copilot.CopilotClient;
import com.github.copilot.rpc.MessageOptions;
import com.github.copilot.rpc.PermissionHandler;
import com.github.copilot.rpc.SessionConfig;

try (var client = new CopilotClient()) {
    client.start().get();

    var session = client.createSession(
            new SessionConfig()
                    .setModel("auto")
                    .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
    ).get();

    var response = session.sendAndWait(
            new MessageOptions().setPrompt("What is 2 + 2?")
    ).get();

    System.out.println(response.getData().content());

    client.stop().get();
}
```

### Listening for streamed responses

```java
import com.github.copilot.generated.AssistantMessageEvent;

session.on(AssistantMessageEvent.class, event ->
        System.out.println(event.getData().content()));
```

### Annotation-based tool definitions

```java
import com.github.copilot.rpc.ToolInvocation;
import com.github.copilot.tool.CopilotTool;
import com.github.copilot.tool.CopilotToolParam;

class MyTools {
    @CopilotTool("Description of what this tool does")
    public String myTool(
            @CopilotToolParam("Parameter description") String input,
            ToolInvocation invocation) {
        return "result for: " + input;
    }
}
```

### Registering tools on a session

```java
import com.github.copilot.rpc.ToolDefinition;

var tools = ToolDefinition.fromObject(new MyTools());
var session = client.createSession(new SessionConfig()
        .setTools(tools)
        .setOnPermissionRequest(PermissionHandler.APPROVE_ALL)
).get();
```

## Project Structure

```
src/main/java/com/github/developmentagent/
├── DevelopmentAgent.java         – Main entry point; starts the Copilot client
├── AgentWorkflow.java            – Legacy single-session workflow (kept for reference)
├── Ticket.java                   – Value object representing a work ticket
├── TicketSource.java             – Interface for fetching tickets (implement for Jira, etc.)
├── domain/
│   ├── TicketBrief.java          – Structured Jira ticket normalised by the Jira Agent
│   └── ExecutionPlan.java        – Implementation plan produced by the Planning Agent
├── agents/
│   ├── jira/JiraAgentTool.java   – Jira Agent: fetches and normalises Jira ticket data
│   └── planning/PlanningAgentTool.java – Planning Agent: produces implementation plans
└── workflow/
    └── DeliveryWorkflow.java     – Delivery Agent: top-level orchestrator
```

## Prerequisites

- Java 25 JDK
- GitHub Copilot CLI (`copilot` command) installed and authenticated in `PATH`
  - See: https://docs.github.com/en/copilot/how-tos/copilot-sdk/getting-started
- Maven 3.9+

## Building and running

```bash
# Build
mvn package -q

# Run with sample ticket DEV-1
java --enable-preview -jar target/development-agent-1.0.0-SNAPSHOT.jar

# Run with a specific ticket key
java --enable-preview -jar target/development-agent-1.0.0-SNAPSHOT.jar MY-123
```
