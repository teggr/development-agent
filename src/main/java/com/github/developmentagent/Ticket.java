package com.github.developmentagent;

/**
 * Represents a work ticket retrieved from a ticket source such as Jira.
 *
 * @param id          Unique identifier (e.g. {@code "DEV-42"}).
 * @param title       Short, human-readable title of the task.
 * @param description Full description or acceptance criteria for the task.
 */
public record Ticket(String id, String title, String description) {}
