# development-agent

A GitHub Copilot SDK powered development agent for picking and implementing tasks from a ticket source such as Jira.

## Overview

The agent follows a three-phase workflow for each ticket:

1. **Plan** – a planning agent analyses the ticket and produces a step-by-step implementation plan.
2. **Implement** – a coding agent uses the plan to generate code changes.
3. **Review** – a review agent verifies the implementation meets the acceptance criteria.

## Prerequisites

- **Java 25** JDK
- **Maven 3.9+**
- **GitHub Copilot CLI** installed, authenticated, and available in `PATH`
  - See [Getting started with the Copilot SDK](https://docs.github.com/en/copilot/how-tos/copilot-sdk/getting-started)

## Framework

- **Spring Boot 4** (CLI-style application, no web starter dependencies)

## Building

```bash
mvn package -q
```

## Running

```bash
java --enable-preview -jar target/development-agent-1.0.0-SNAPSHOT.jar
```

Or directly via Maven during development:

```bash
mvn spring-boot:run -Dspring-boot.run.arguments=MY-123
```

## SDK References

| Resource | URL |
|----------|-----|
| Java SDK source & README | https://github.com/github/copilot-sdk/tree/main/java |
| Java Cookbook (recipes) | https://github.com/github/awesome-copilot/blob/main/cookbook/copilot-sdk/java/README.md |
| Getting started guide | https://docs.github.com/en/copilot/how-tos/copilot-sdk/getting-started |
| Javadoc (1.0.8) | https://github.github.com/copilot-sdk-java/1.0.8/ |

## Project Structure

```
src/main/java/com/github/developmentagent/
├── DevelopmentAgent.java   – Main entry point; starts the Copilot client
├── AgentWorkflow.java      – Multi-agent workflow (plan → implement → review)
├── Ticket.java             – Value object representing a work ticket
└── TicketSource.java       – Interface for fetching tickets (implement for Jira, etc.)
```

## Extending the agent

- **Implement `TicketSource`** to connect to your ticket system (Jira, GitHub Issues, Linear, etc.).
- **Add tools** using `@CopilotTool` annotations to give agents access to your codebase, CI/CD pipelines, or external APIs.
- **Add more agent phases** by extending `AgentWorkflow` (e.g. a documentation-writing agent).

## Architecture

- First-pass Agents-as-Tools orchestration design: `docs/architecture/agents-as-tools-first-pass.md`
