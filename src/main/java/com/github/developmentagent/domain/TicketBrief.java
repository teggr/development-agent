package com.github.developmentagent.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Structured representation of a Jira ticket produced by the Jira Agent.
 *
 * <p>This is the contract that the Delivery Agent requires from the Jira Agent.
 * It normalises raw Jira data into an execution-ready brief for downstream agents.
 */
public record TicketBrief(
        @JsonProperty("id") String id,
        @JsonProperty("title") String title,
        @JsonProperty("goal") String goal,
        @JsonProperty("description") String description,
        @JsonProperty("acceptanceCriteria") List<String> acceptanceCriteria,
        @JsonProperty("linkedIssues") List<String> linkedIssues
) {
    @JsonCreator
    public TicketBrief(
            @JsonProperty("id") String id,
            @JsonProperty("title") String title,
            @JsonProperty("goal") String goal,
            @JsonProperty("description") String description,
            @JsonProperty("acceptanceCriteria") List<String> acceptanceCriteria,
            @JsonProperty("linkedIssues") List<String> linkedIssues) {
        this.id = id;
        this.title = title;
        this.goal = goal;
        this.description = description;
        this.acceptanceCriteria = acceptanceCriteria != null ? acceptanceCriteria : List.of();
        this.linkedIssues = linkedIssues != null ? linkedIssues : List.of();
    }
}
