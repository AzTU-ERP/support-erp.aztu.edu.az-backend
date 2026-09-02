package com.aztu.support_erp.ticket.service;

import com.aztu.support_erp.catalog.service.CatalogService;
import com.aztu.support_erp.common.Codes;
import com.aztu.support_erp.common.PageResponse;
import com.aztu.support_erp.common.enums.BoardColumn;
import com.aztu.support_erp.common.enums.TicketStatus;
import com.aztu.support_erp.common.exception.BadRequestException;
import com.aztu.support_erp.common.exception.ForbiddenException;
import com.aztu.support_erp.common.exception.NotFoundException;
import com.aztu.support_erp.common.exception.TooManyRequestsException;
import com.aztu.support_erp.infrastructure.storage.FileStorageService;
import com.aztu.support_erp.infrastructure.storage.StoredFile;
import com.aztu.support_erp.ticket.domain.Ticket;
import com.aztu.support_erp.ticket.domain.TicketAttachment;
import com.aztu.support_erp.ticket.domain.TicketStatusHistory;
import com.aztu.support_erp.ticket.dto.AssignRequest;
import com.aztu.support_erp.ticket.dto.BoardResponse;
import com.aztu.support_erp.ticket.dto.ChangeStatusRequest;
import com.aztu.support_erp.ticket.dto.CreateTicketForm;
import com.aztu.support_erp.ticket.dto.TicketMapper;
import com.aztu.support_erp.ticket.dto.TicketResponse;
import com.aztu.support_erp.ticket.repository.TicketAttachmentRepository;
import com.aztu.support_erp.ticket.repository.TicketRepository;
import com.aztu.support_erp.ticket.repository.TicketStatusHistoryRepository;
import com.aztu.support_erp.user.domain.UserRef;
import com.aztu.support_erp.user.service.UserRefService;
import com.aztu.support_erp.violation.service.ViolationRules;
import com.aztu.support_erp.violation.service.ViolationService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * The ticket lifecycle.
 *
 * <p>Two audiences share these methods: the reporter, who may open a ticket and read their own,
 * and the DEV, who owns everything else. Coarse role gating lives in {@code SecurityConfig};
 * the ownership checks here are what stop one reporter reading another's ticket by guessing an
 * id, which the URL alone cannot prevent.
 */
@Service
public class TicketService {

    private static final Logger log = LoggerFactory.getLogger(TicketService.class);

    /** All codes, used as the "no status filter" argument so the `in` clause stays valid. */
    private static final Set<String> ALL_STATUSES = Codes.allowed(TicketStatus.class);

    /** How many cards a board column carries before the DEV is expected to filter instead. */
    private static final int BOARD_COLUMN_LIMIT = 100;

    private final TicketRepository repository;
    private final TicketAttachmentRepository attachmentRepository;
    private final TicketStatusHistoryRepository historyRepository;
    private final CatalogService catalog;
    private final FileStorageService storage;
    private final ViolationService violations;
    private final UserRefService users;
    private final int maxAttachments;
    private final int rateLimitMax;
    private final int rateLimitWindowMinutes;

    public TicketService(TicketRepository repository,
                         TicketAttachmentRepository attachmentRepository,
                         TicketStatusHistoryRepository historyRepository,
                         CatalogService catalog,
                         FileStorageService storage,
                         ViolationService violations,
                         UserRefService users,
                         @Value("${app.support.attachments.max-per-ticket:3}") int maxAttachments,
                         @Value("${app.support.rate-limit.max-tickets:5}") int rateLimitMax,
                         @Value("${app.support.rate-limit.window-minutes:10}") int rateLimitWindowMinutes) {
        this.repository = repository;
        this.attachmentRepository = attachmentRepository;
        this.historyRepository = historyRepository;
        this.catalog = catalog;
        this.storage = storage;
        this.violations = violations;
        this.users = users;
        this.maxAttachments = maxAttachments;
        this.rateLimitMax = rateLimitMax;
        this.rateLimitWindowMinutes = rateLimitWindowMinutes;
    }

    // ---- reporter ----

    @Transactional
    public TicketResponse create(CreateTicketForm form, UserRef reporter) {
        catalog.requireOfferedSection(form.getModule(), form.getSection());
        requireWithinRateLimit(reporter);

        List<MultipartFile> screenshots = presentFiles(form.getScreenshots());
        if (screenshots.size() > maxAttachments) {
            throw new BadRequestException(
                    "A ticket may carry at most " + maxAttachments + " screenshots");
        }

        Ticket ticket = new Ticket();
        ticket.setReference(nextReference());
        ticket.setUser(reporter);
        ticket.setModule(form.getModule());
        ticket.setSection(form.getSection());
        ticket.setDescription(form.getDescription().trim());
        ticket.setStatus(TicketStatus.OPEN.code());
        ticket.setPageUrl(form.getPageUrl());
        ticket.setUserAgent(form.getUserAgent());
        Ticket saved = repository.save(ticket);

        storeScreenshots(saved, screenshots);
        recordHistory(saved, null, TicketStatus.OPEN.code(), reporter, null);

        log.info("Ticket {} opened by {} against {}/{}",
                saved.getReference(), reporter.getId(), saved.getModule(), saved.getSection());
        return TicketMapper.toDetail(saved,
                attachmentRepository.findByTicketIdOrderByCreatedAtAsc(saved.getId()),
                historyRepository.findByTicketIdOrderByCreatedAtAsc(saved.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> listMine(UUID reporterId, int page, int size) {
        Page<Ticket> found = repository.findByUserIdOrderByCreatedAtDesc(
                reporterId, PageRequest.of(Math.max(page, 0), boundedSize(size)));
        return PageResponse.from(found, withAttachmentCounts(found.getContent()));
    }

    /** The reporter's own detail view — the id must be theirs, not merely a valid one. */
    @Transactional(readOnly = true)
    public TicketResponse getMine(UUID id, UUID reporterId) {
        Ticket ticket = repository.findByIdAndUserId(id, reporterId)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
        return detailOf(ticket);
    }

    // ---- DEV queue ----

    @Transactional(readOnly = true)
    public PageResponse<TicketResponse> search(String status,
                                               String module,
                                               String section,
                                               UUID userId,
                                               UUID assignedTo,
                                               LocalDateTime from,
                                               LocalDateTime to,
                                               String query,
                                               int page,
                                               int size,
                                               String sort,
                                               String direction) {
        Page<Ticket> found = repository.search(
                statusFilter(status),
                blankToNull(module),
                blankToNull(section),
                userId,
                assignedTo,
                from,
                to,
                blankToNull(query),
                PageRequest.of(Math.max(page, 0), boundedSize(size), sortOf(sort, direction)));
        return PageResponse.from(found, withAttachmentCounts(found.getContent()));
    }

    @Transactional(readOnly = true)
    public TicketResponse get(UUID id) {
        return detailOf(find(id));
    }

    /**
     * The board, grouped the way it is drawn. Each column is capped rather than paged: a queue
     * long enough to hit the cap is one the DEV should be filtering, not scrolling.
     */
    @Transactional(readOnly = true)
    public BoardResponse board(String module,
                               String section,
                               UUID assignedTo,
                               String query,
                               String status,
                               LocalDateTime from,
                               LocalDateTime to) {
        // A status filter narrows each column to that status rather than replacing the columns:
        // the board's shape is the lifecycle, and a DEV asking for BLOCKED wants to see it where
        // it lives. A column the status cannot appear in simply comes back empty.
        Set<String> requested = statusFilter(status);

        List<BoardResponse.Column> columns = new ArrayList<>();
        for (BoardColumn column : BoardColumn.ordered()) {
            Set<String> statuses = new LinkedHashSet<>(column.statuses());
            statuses.retainAll(requested);

            Page<Ticket> found = statuses.isEmpty()
                    ? Page.empty()
                    : repository.search(
                            statuses,
                            blankToNull(module),
                            blankToNull(section),
                            null,
                            assignedTo,
                            from,
                            to,
                            blankToNull(query),
                            PageRequest.of(0, BOARD_COLUMN_LIMIT,
                                    Sort.by(Sort.Direction.DESC, "createdAt")));
            columns.add(new BoardResponse.Column(
                    column.code(),
                    List.copyOf(column.statuses()),
                    TicketRules.defaultStatusFor(column),
                    found.getTotalElements(),
                    withAttachmentCounts(found.getContent())));
        }
        return new BoardResponse(columns);
    }

    /**
     * The only way a ticket's status changes. Cancelling as irrelevant is the same call, because
     * the cancellation and its consequence for the reporter have to commit together.
     */
    @Transactional
    public TicketResponse changeStatus(UUID id, ChangeStatusRequest req, UserRef dev) {
        Ticket ticket = find(id);
        String target = Codes.require(TicketStatus.class, req.status());

        TicketRules.validateCancellation(target, req.irrelevant(), req.cancelReason());
        TicketRules.requireTransition(ticket.getStatus(), target);

        String previous = ticket.getStatus();
        ticket.setStatus(target);
        if (TicketStatus.CANCELED.code().equals(target)) {
            ticket.setCancelReason(req.cancelReason());
            ticket.setIrrelevant(req.irrelevant());
        }
        if (TicketStatus.BLOCKED.code().equals(target) && req.cancelReason() != null) {
            // BLOCKED means "waiting on something else"; the reason is worth keeping when given.
            ticket.setCancelReason(req.cancelReason());
        }
        ticket.setResolvedAt(TicketStatus.RESOLVED.code().equals(target) ? LocalDateTime.now() : null);
        repository.save(ticket);

        recordHistory(ticket, previous, target, dev, historyComment(req));

        if (req.irrelevant()) {
            ViolationRules.Outcome outcome =
                    violations.recordIrrelevant(ticket.getUser(), dev, req.cancelReason());
            log.info("Ticket {} cancelled as irrelevant by DEV {} — reporter outcome {}",
                    ticket.getReference(), dev.getId(), outcome);
        } else {
            log.info("Ticket {} moved {} -> {} by DEV {}",
                    ticket.getReference(), previous, target, dev.getId());
        }
        return detailOf(ticket);
    }

    /** Assigning, including taking it oneself. A null id hands the ticket back to the queue. */
    @Transactional
    public TicketResponse assign(UUID id, AssignRequest req, UserRef dev) {
        Ticket ticket = find(id);
        if (TicketRules.isTerminal(ticket.getStatus())) {
            throw new BadRequestException("This ticket is already closed");
        }
        UserRef assignee = req.devId() == null ? null : users.find(req.devId());
        ticket.setAssignedDev(assignee);
        repository.save(ticket);
        log.info("Ticket {} assigned to {} by DEV {}",
                ticket.getReference(), assignee == null ? "nobody" : assignee.getId(), dev.getId());
        return detailOf(ticket);
    }

    // ---- attachments ----

    /**
     * The stored file behind an attachment, once the caller is entitled to it. Screenshots often
     * catch more of the page than the reporter meant to share, so they are never public: only the
     * reporter and a DEV get the bytes.
     */
    @Transactional(readOnly = true)
    public TicketAttachment findAttachment(UUID ticketId, UUID attachmentId, UserRef caller, boolean isDev) {
        Ticket ticket = find(ticketId);
        if (!isDev && !ticket.getUser().getId().equals(caller.getId())) {
            throw new ForbiddenException("You can only view your own tickets");
        }
        return attachmentRepository.findByIdAndTicketId(attachmentId, ticketId)
                .orElseThrow(() -> new NotFoundException("Attachment not found"));
    }

    // ---- internals ----

    private Ticket find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("Ticket not found"));
    }

    private TicketResponse detailOf(Ticket ticket) {
        return TicketMapper.toDetail(ticket,
                attachmentRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()),
                historyRepository.findByTicketIdOrderByCreatedAtAsc(ticket.getId()));
    }

    /**
     * A ticket's number comes from a sequence rather than a count of rows, so a number is never
     * handed out twice even after a ticket is deleted.
     */
    private String nextReference() {
        return String.format("SUP-%06d", repository.nextReferenceNumber());
    }

    /**
     * The rate limit is a count of rows, not a counter in memory: it survives a restart and holds
     * across every instance of the service, which an in-process window would not.
     */
    private void requireWithinRateLimit(UserRef reporter) {
        if (rateLimitMax <= 0) return;
        LocalDateTime since = LocalDateTime.now().minusMinutes(rateLimitWindowMinutes);
        if (repository.countByUserIdAndCreatedAtAfter(reporter.getId(), since) >= rateLimitMax) {
            throw new TooManyRequestsException(
                    "You have opened too many tickets in the last " + rateLimitWindowMinutes
                            + " minutes, please try again later");
        }
    }

    private void storeScreenshots(Ticket ticket, List<MultipartFile> screenshots) {
        List<String> stored = new ArrayList<>();
        try {
            for (MultipartFile file : screenshots) {
                StoredFile saved = storage.storeScreenshot(file, ticket.getId().toString());
                stored.add(saved.storagePath());
                TicketAttachment attachment = new TicketAttachment();
                attachment.setTicket(ticket);
                attachment.setStorageKey(saved.storagePath());
                attachment.setOriginalName(saved.originalName());
                attachment.setMimeType(saved.mimeType());
                attachment.setSizeBytes(saved.sizeBytes());
                attachmentRepository.save(attachment);
            }
        } catch (RuntimeException ex) {
            // The transaction will take the rows back; the bytes on disk need saying so explicitly.
            stored.forEach(storage::deleteQuietly);
            throw ex;
        }
    }

    private void recordHistory(Ticket ticket, String from, String to, UserRef actor, String comment) {
        TicketStatusHistory entry = new TicketStatusHistory();
        entry.setTicket(ticket);
        entry.setFromStatus(from);
        entry.setToStatus(to);
        entry.setChangedBy(actor);
        entry.setComment(comment);
        historyRepository.save(entry);
    }

    /** The reason is the more useful note when both are given, and the only one on a cancellation. */
    private static String historyComment(ChangeStatusRequest req) {
        if (req.comment() != null && !req.comment().isBlank()) {
            return req.cancelReason() == null || req.cancelReason().isBlank()
                    ? req.comment()
                    : req.cancelReason() + " — " + req.comment();
        }
        return req.cancelReason();
    }

    /** Attachment counts for a page of tickets, in one query rather than one per card. */
    private List<TicketResponse> withAttachmentCounts(List<Ticket> tickets) {
        if (tickets.isEmpty()) return List.of();
        Map<UUID, Integer> counts = new HashMap<>();
        List<UUID> ids = tickets.stream().map(Ticket::getId).toList();
        for (Object[] row : attachmentRepository.countByTicketIds(ids)) {
            counts.put((UUID) row[0], ((Number) row[1]).intValue());
        }
        return tickets.stream()
                .map(t -> TicketMapper.toSummary(t, counts.getOrDefault(t.getId(), 0)))
                .toList();
    }

    private static Set<String> statusFilter(String status) {
        if (status == null || status.isBlank()) return ALL_STATUSES;
        return Set.of(Codes.require(TicketStatus.class, status));
    }

    private static List<MultipartFile> presentFiles(List<MultipartFile> files) {
        if (files == null) return List.of();
        return files.stream().filter(f -> f != null && !f.isEmpty()).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static int boundedSize(int size) {
        return Math.min(Math.max(size, 1), 100);
    }

    /** Only the columns a DEV can meaningfully order by; anything else falls back to newest first. */
    private static Sort sortOf(String sort, String direction) {
        String property = switch (sort == null ? "" : sort) {
            case "updatedAt" -> "updatedAt";
            case "status" -> "status";
            case "module" -> "module";
            case "reference" -> "reference";
            default -> "createdAt";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(direction) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, property);
    }
}
