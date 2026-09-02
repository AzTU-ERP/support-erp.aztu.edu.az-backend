package com.aztu.support_erp.ticket.repository;

import com.aztu.support_erp.ticket.domain.TicketAttachment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TicketAttachmentRepository extends JpaRepository<TicketAttachment, UUID> {

    List<TicketAttachment> findByTicketIdOrderByCreatedAtAsc(UUID ticketId);

    Optional<TicketAttachment> findByIdAndTicketId(UUID id, UUID ticketId);

    long countByTicketId(UUID ticketId);

    /**
     * Attachment counts for a page of tickets in one query — the list and the board only show a
     * number, and fetching the rows themselves to count them would be a query per card.
     */
    @Query("""
            select a.ticket.id, count(a) from TicketAttachment a
            where a.ticket.id in :ticketIds
            group by a.ticket.id
            """)
    List<Object[]> countByTicketIds(@Param("ticketIds") Collection<UUID> ticketIds);
}
