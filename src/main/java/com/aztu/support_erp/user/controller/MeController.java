package com.aztu.support_erp.user.controller;

import com.aztu.support_erp.common.ApiResponse;
import com.aztu.support_erp.user.dto.UserMapper;
import com.aztu.support_erp.user.dto.UserRefResponse;
import com.aztu.support_erp.user.dto.UserSummary;
import com.aztu.support_erp.user.service.UserRefService;
import com.aztu.support_erp.violation.dto.ViolationStatusResponse;
import com.aztu.support_erp.violation.service.ViolationService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Identity endpoints. {@code /me} is what the frontend calls on load to learn which roles the
 * token carries, so it knows whether to offer the board; {@code /me/violation-status} is what the
 * ticket form checks before it lets someone type.
 */
@RestController
@RequestMapping("/api/support")
public class MeController {

    private final UserRefService service;
    private final ViolationService violations;

    public MeController(UserRefService service, ViolationService violations) {
        this.service = service;
        this.violations = violations;
    }

    @GetMapping("/me")
    public ApiResponse<UserRefResponse> me() {
        return ApiResponse.ok(UserMapper.toResponse(service.resolveCurrent()));
    }

    /** Whether the caller has been warned, and how close they are to being blocked. */
    @GetMapping("/me/violation-status")
    public ApiResponse<ViolationStatusResponse> violationStatus() {
        return ApiResponse.ok(violations.statusFor(service.resolveCurrent().getId()));
    }

    /** Who a ticket can be handed to, for the board's assignee picker (DEV only). */
    @GetMapping("/devs")
    public ApiResponse<List<UserSummary>> devs() {
        return ApiResponse.ok(service.listDevs().stream().map(UserMapper::toSummary).toList());
    }
}
