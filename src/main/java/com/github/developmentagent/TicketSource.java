package com.github.developmentagent;

import java.util.List;

/**
 * Abstraction over a ticket management system (e.g. Jira, GitHub Issues,
 * Linear). Implementations are responsible for authenticating with the
 * external system and retrieving open tickets that the development agent
 * should work on.
 */
public interface TicketSource {

    /**
     * Returns a list of open tickets that are ready to be worked on.
     *
     * @return non-null, possibly empty list of {@link Ticket} instances.
     * @throws Exception if the underlying system cannot be reached.
     */
    List<Ticket> fetchOpenTickets() throws Exception;
}
