package com.github.developmentagent.agents.workflow;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Structured implementation plan produced by the Planning Agent.
 *
 * <p>This is the contract that the Delivery Agent requires from the Planning Agent.
 * It contains ordered steps, affected packages and files, required tests, known
 * risks, and the done criteria for the ticket.
 */
public record ExecutionPlan(
        @JsonProperty("ticketId") String ticketId,
        @JsonProperty("steps") List<String> steps,
        @JsonProperty("likelyPackages") List<String> likelyPackages,
        @JsonProperty("likelyFiles") List<String> likelyFiles,
        @JsonProperty("requiredTests") List<String> requiredTests,
        @JsonProperty("risks") List<String> risks,
        @JsonProperty("doneCriteria") String doneCriteria
) {
    @JsonCreator
    public ExecutionPlan(
            @JsonProperty("ticketId") String ticketId,
            @JsonProperty("steps") List<String> steps,
            @JsonProperty("likelyPackages") List<String> likelyPackages,
            @JsonProperty("likelyFiles") List<String> likelyFiles,
            @JsonProperty("requiredTests") List<String> requiredTests,
            @JsonProperty("risks") List<String> risks,
            @JsonProperty("doneCriteria") String doneCriteria) {
        this.ticketId = ticketId;
        this.steps = steps != null ? steps : List.of();
        this.likelyPackages = likelyPackages != null ? likelyPackages : List.of();
        this.likelyFiles = likelyFiles != null ? likelyFiles : List.of();
        this.requiredTests = requiredTests != null ? requiredTests : List.of();
        this.risks = risks != null ? risks : List.of();
        this.doneCriteria = doneCriteria;
    }
}
