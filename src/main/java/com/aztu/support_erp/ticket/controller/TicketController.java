package com.aztu.support_erp.ticket.controller;

import com.aztu.support_erp.common.ApiResponse;
import com.aztu.support_erp.common.PageResponse;
import com.aztu.support_erp.common.exception.NotFoundException;
import com.aztu.support_erp.infrastructure.storage.FileStorageService;
import com.aztu.support_erp.security.CurrentUser;
import com.aztu.support_erp.ticket.domain.TicketAttachment;
import com.aztu.support_erp.ticket.dto.AssignRequest;
import com.aztu.support_erp.ticket.dto.BoardResponse;
import com.aztu.support_erp.ticket.dto.ChangeStatusRequest;
import com.aztu.support_erp.ticket.dto.CreateTicketForm;
import com.aztu.support_erp.ticket.dto.TicketResponse;
import com.aztu.support_erp.ticket.service.TicketService;
import com.aztu.support_erp.user.service.UserRefService;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tickets. {@code /my} is the reporter's own view; the collection root, the board and the state
 * transitions are the DEV queue. Role gating lives in {@code SecurityConfig}; per-row ownership
 * is checked in the service.
 */
@RestController
@RequestMapping("/api/support/tickets")
public class TicketController {

    private final TicketService service;
    private final UserRefService userRefService;
    private final FileStorageService fileStorageService;

    public TicketController(TicketService service,
                            UserRefService userRefService,
                            FileStorageService fileStorageService) {
        this.service = service;
        this.userRefService = userRefService;
        this.fileStorageService = fileStorageService;
    }

    // ---- reporter ----

    /**
     * Opening a ticket. Multipart because the screenshots ride along with the form; page URL and
     * user agent are sent by the button rather than typed.
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TicketResponse> create(@Valid @ModelAttribute CreateTicketForm form) {
        TicketResponse created = service.create(form, userRefService.resolveCurrent());
        return ApiResponse.ok(created, "Ticket " + created.reference() + " submitted");
    }

    @GetMapping("/my")
    public ApiResponse<PageResponse<TicketResponse>> mine(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.listMine(userRefService.resolveCurrent().getId(), page, size));
    }

    @GetMapping("/my/{id}")
    public ApiResponse<TicketResponse> mine(@PathVariable UUID id) {
        return ApiResponse.ok(service.getMine(id, userRefService.resolveCurrent().getId()));
    }

    // ---- DEV queue ----

    @GetMapping
    public ApiResponse<PageResponse<TicketResponse>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sort,
            @RequestParam(defaultValue = "desc") String direction) {
        return ApiResponse.ok(service.search(status, module, section, userId, assignedTo,
                from, to, q, page, size, sort, direction));
    }

    /**
     * The board, already grouped into TO_DO / IN_PROGRESS / DONE. The filters are the list's,
     * minus the ones a board has no use for; {@code status} narrows each column rather than
     * replacing them.
     */
    @GetMapping("/board")
    public ApiResponse<BoardResponse> board(
            @RequestParam(required = false) String module,
            @RequestParam(required = false) String section,
            @RequestParam(required = false) UUID assignedTo,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ApiResponse.ok(service.board(module, section, assignedTo, q, status, from, to));
    }

    @GetMapping("/{id}")
    public ApiResponse<TicketResponse> get(@PathVariable UUID id) {
        return ApiResponse.ok(service.get(id));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<TicketResponse> changeStatus(@PathVariable UUID id,
                                                    @Valid @RequestBody ChangeStatusRequest req) {
        return ApiResponse.ok(
                service.changeStatus(id, req, userRefService.resolveCurrent()),
                "Ticket status updated");
    }

    @PatchMapping("/{id}/assign")
    public ApiResponse<TicketResponse> assign(@PathVariable UUID id,
                                              @RequestBody(required = false) AssignRequest req) {
        AssignRequest body = req != null ? req : new AssignRequest(null);
        return ApiResponse.ok(
                service.assign(id, body, userRefService.resolveCurrent()),
                body.devId() == null ? "Ticket returned to the queue" : "Ticket assigned");
    }

    // ---- attachments ----

    /**
     * A screenshot's bytes, for the reporter who filed it or any DEV. Served inline so the
     * lightbox can render it; an {@code <img>} tag cannot carry a bearer token, so the frontend
     * fetches it through the API client and hands the blob to the tag.
     */
    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<Resource> attachment(@PathVariable UUID id, @PathVariable UUID attachmentId) {
        TicketAttachment attachment = service.findAttachment(
                id, attachmentId, userRefService.resolveCurrent(), CurrentUser.get().isDev());
        Resource resource = new FileSystemResource(fileStorageService.resolve(attachment.getStorageKey()));
        if (!resource.exists()) {
            throw new NotFoundException("Screenshot is missing on the server");
        }
        String filename = attachment.getOriginalName() != null ? attachment.getOriginalName() : "screenshot";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getMimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + filename + "\"")
                .body(resource);
    }
}
