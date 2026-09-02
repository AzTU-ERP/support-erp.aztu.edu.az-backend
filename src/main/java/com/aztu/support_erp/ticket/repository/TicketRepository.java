package com.aztu.support_erp.ticket.repository;

import com.aztu.support_erp.ticket.domain.Ticket;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketRepository extends JpaRepository<Ticket, UUID> {

    /** The next human-facing number. The sequence, not a count, so a deleted row never repeats one. */
    @Query(value = "SELECT nextval('support_ticket_reference_seq')", nativeQuery = true)
    long nextReferenceNumber();

    /**
     * The DEV queue. Every filter is optional — a null argument drops out of the predicate —
     * except {@code statuses}, which callers pass as "all codes" when they mean no filter, so the
     * {@code in} clause stays valid.
     *
     * <p>The assignee is joined explicitly and on the left: the implicit join behind
     * {@code t.assignedDev.id} would be an inner one and would quietly hide every unassigned
     * ticket, filter or no filter.
     */
    @Query(value = """
            select t from Ticket t
            left join t.assignedDev dev
            where t.status in :statuses
              and (:module is null or t.module = :module)
              and (:section is null or t.section = :section)
              and (:userId is null or t.user.id = :userId)
              and (:assignedTo is null or dev.id = :assignedTo)
              and (:from is null or t.createdAt >= :from)
              and (:to is null or t.createdAt <= :to)
              and (:q is null or lower(t.description) like lower(concat('%', :q, '%'))
                              or lower(t.reference) like lower(concat('%', :q, '%')))
            """,
            countQuery = """
            select count(t) from Ticket t
            left join t.assignedDev dev
            where t.status in :statuses
              and (:module is null or t.module = :module)
              and (:section is null or t.section = :section)
              and (:userId is null or t.user.id = :userId)
              and (:assignedTo is null or dev.id = :assignedTo)
              and (:from is null or t.createdAt >= :from)
              and (:to is null or t.createdAt <= :to)
              and (:q is null or lower(t.description) like lower(concat('%', :q, '%'))
                              or lower(t.reference) like lower(concat('%', :q, '%')))
            """)
    Page<Ticket> search(@Param("statuses") Collection<String> statuses,
                        @Param("module") String module,
                        @Param("section") String section,
                        @Param("userId") UUID userId,
                        @Param("assignedTo") UUID assignedTo,
                        @Param("from") LocalDateTime from,
                        @Param("to") LocalDateTime to,
                        @Param("q") String q,
                        Pageable pageable);

    Page<Ticket> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Optional<Ticket> findByIdAndUserId(UUID id, UUID userId);

    /** The board loads whole columns rather than pages — capped by the caller, newest first. */
    List<Ticket> findByStatusInOrderByCreatedAtDesc(Collection<String> statuses, Pageable pageable);

    /** How many tickets this reporter has opened since the given instant, for the rate limit. */
    long countByUserIdAndCreatedAtAfter(UUID userId, LocalDateTime since);
}
