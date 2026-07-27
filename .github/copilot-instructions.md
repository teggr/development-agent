# Copilot AI Instructions

This repository is a GitHub Copilot SDK powered development agent written in Java 25 and built with Maven.
The agent takes tickets from a source system (e.g. Jira) and runs a multi-agent workflow to plan, implement,
and review each task.

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

## Project Structure

```
src/main/java/com/github/developmentagent/
├── DevelopmentAgent.java   – Main entry point; starts the Copilot client
├── AgentWorkflow.java      – Multi-agent workflow (plan → implement → review)
├── Ticket.java             – Value object representing a work ticket
└── TicketSource.java       – Interface for fetching tickets (implement for Jira, etc.)
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

# Run
java --enable-preview -jar target/development-agent-1.0.0-SNAPSHOT.jar
```
