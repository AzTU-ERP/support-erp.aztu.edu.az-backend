package com.aztu.support_erp.violation.controller;

import com.aztu.support_erp.common.ApiResponse;
import com.aztu.support_erp.common.PageResponse;
import com.aztu.support_erp.user.service.UserRefService;
import com.aztu.support_erp.violation.dto.ViolationResponse;
import com.aztu.support_erp.violation.service.ViolationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The violations tab. Everything here is DEV-only: this is where a block is lifted, and lifting
 * one wipes the reporter's count.
 */
@RestController
@RequestMapping("/api/support/violations")
public class ViolationController {

    private final ViolationService service;
    private final UserRefService userRefService;

    public ViolationController(ViolationService service, UserRefService userRefService) {
        this.service = service;
        this.userRefService = userRefService;
    }

    /** Everyone warned or blocked. {@code blockedOnly} narrows it to the accounts waiting on a DEV. */
    @GetMapping
    public ApiResponse<PageResponse<ViolationResponse>> list(
            @RequestParam(defaultValue = "false") boolean blockedOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(blockedOnly, page, size));
    }

    @PostMapping("/{userId}/unblock")
    public ApiResponse<ViolationResponse> unblock(@PathVariable UUID userId) {
        return ApiResponse.ok(
                service.unblock(userId, userRefService.resolveCurrent()),
                "User unblocked");
    }
}
