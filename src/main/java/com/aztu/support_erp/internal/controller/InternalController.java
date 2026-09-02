package com.aztu.support_erp.internal.controller;

import com.aztu.support_erp.common.ApiResponse;
import com.aztu.support_erp.internal.dto.BlockStatusResponse;
import com.aztu.support_erp.violation.service.ViolationService;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Service-to-service surface for the auth microservice. Authenticated by the shared
 * {@code X-Service-Token} header, never by an SSO token — the caller is a service, not a person
 * (see {@code ServiceTokenFilter}).
 *
 * <p>Support decides who is blocked and pushes that decision out through the outbox; this
 * endpoint is the pull half of the same contract, so an auth service that missed a delivery — or
 * that would rather ask than store — can check at sign-in and still get the right answer.
 */
@RestController
@RequestMapping("/api/support/internal")
public class InternalController {

    private final ViolationService violations;

    public InternalController(ViolationService violations) {
        this.violations = violations;
    }

    /** {@code ssoUserId} is the auth service's own id for the account. */
    @GetMapping("/users/{ssoUserId}/block-status")
    public ApiResponse<BlockStatusResponse> blockStatus(@PathVariable UUID ssoUserId) {
        return ApiResponse.ok(violations.blockStatusBySso(ssoUserId));
    }
}
