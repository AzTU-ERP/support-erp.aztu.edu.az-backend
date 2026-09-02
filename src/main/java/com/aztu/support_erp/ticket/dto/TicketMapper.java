package com.aztu.support_erp.ticket.dto;

import com.aztu.support_erp.common.enums.BoardColumn;
import com.aztu.support_erp.ticket.domain.Ticket;
import com.aztu.support_erp.ticket.domain.TicketAttachment;
import com.aztu.support_erp.ticket.domain.TicketStatusHistory;
import com.aztu.support_erp.user.dto.UserMapper;
import java.util.List;

public final class TicketMapper {
    private TicketMapper() {}

    /** List and board form: counts rather than contents, so a page of cards is one query each. */
    public static TicketResponse toSummary(Ticket t, int attachmentCount) {
        return build(t, attachmentCount, null, null);
    }

    /** Detail form: everything the drawer shows. */
    public static TicketResponse toDetail(Ticket t,
                                          List<TicketAttachment> attachments,
                                          List<TicketStatusHistory> history) {
        return build(t, attachments.size(),
                attachments.stream().map(TicketMapper::toResponse).toList(),
                history.stream().map(TicketMapper::toResponse).toList());
    }

    public static TicketAttachmentResponse toResponse(TicketAttachment a) {
        return new TicketAttachmentResponse(
                a.getId(),
                a.getOriginalName(),
                a.getMimeType(),
                a.getSizeBytes(),
                "/api/support/tickets/" + a.getTicket().getId() + "/attachments/" + a.getId());
    }

    public static TicketHistoryResponse toResponse(TicketStatusHistory h) {
        return new TicketHistoryResponse(
                h.getId(),
                h.getFromStatus(),
                h.getToStatus(),
                UserMapper.toSummary(h.getChangedBy()),
                h.getComment(),
                h.getCreatedAt());
    }

    private static TicketResponse build(Ticket t,
                                        int attachmentCount,
                                        List<TicketAttachmentResponse> attachments,
                                        List<TicketHistoryResponse> history) {
        return new TicketResponse(
                t.getId(),
                t.getReference(),
                UserMapper.toSummary(t.getUser()),
                t.getModule(),
                t.getSection(),
                t.getDescription(),
                t.getStatus(),
                BoardColumn.of(t.getStatus()).code(),
                t.isIrrelevant(),
                t.getCancelReason(),
                UserMapper.toSummary(t.getAssignedDev()),
                t.getPageUrl(),
                t.getUserAgent(),
                attachmentCount,
                attachments,
                history,
                t.getResolvedAt(),
                t.getCreatedAt(),
                t.getUpdatedAt());
    }
}
