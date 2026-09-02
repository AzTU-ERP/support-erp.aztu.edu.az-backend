package com.aztu.support_erp.violation.service;

import com.aztu.support_erp.authsync.service.AuthSyncService;
import com.aztu.support_erp.common.PageResponse;
import com.aztu.support_erp.common.exception.ConflictException;
import com.aztu.support_erp.common.exception.NotFoundException;
import com.aztu.support_erp.internal.dto.BlockStatusResponse;
import com.aztu.support_erp.user.domain.UserRef;
import com.aztu.support_erp.violation.domain.UserViolation;
import com.aztu.support_erp.violation.dto.ViolationMapper;
import com.aztu.support_erp.violation.dto.ViolationResponse;
import com.aztu.support_erp.violation.dto.ViolationStatusResponse;
import com.aztu.support_erp.violation.repository.UserViolationRepository;
import jakarta.annotation.PostConstruct;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The warning and blocking ledger.
 *
 * <p>Every ticket a DEV cancels as irrelevant advances the reporter one step: a warning first,
 * then a block. Both thresholds are configuration, so "one warning then out" is a policy this
 * code happens to be configured with, not a rule baked into it.
 *
 * <p>A block is recorded here and pushed to the auth service through the outbox — sign-in is
 * theirs to refuse. Only a DEV lifts it, and doing so wipes the count: the reporter starts over
 * rather than sitting one report away from another block.
 */
@Service
public class ViolationService {

    private static final Logger log = LoggerFactory.getLogger(ViolationService.class);

    /**
     * What a blocked account is told at sign-in. It lives here rather than in the auth service so
     * the wording and the rule that produced it stay in one place.
     */
    private static final String BLOCKED_REASON =
            "Your account has been blocked for repeated irrelevant reports. Please contact support.";

    private final UserViolationRepository repository;
    private final AuthSyncService authSync;
    private final int warnThreshold;
    private final int blockThreshold;

    public ViolationService(UserViolationRepository repository,
                            AuthSyncService authSync,
                            @Value("${app.support.violation.warn-threshold:1}") int warnThreshold,
                            @Value("${app.support.violation.block-threshold:2}") int blockThreshold) {
        this.repository = repository;
        this.authSync = authSync;
        this.warnThreshold = warnThreshold;
        this.blockThreshold = blockThreshold;
    }

    /** Refuse to start on a policy that would block accounts it never warned. */
    @PostConstruct
    void validateConfiguration() {
        ViolationRules.requireSaneThresholds(warnThreshold, blockThreshold);
    }

    /**
     * Counts one irrelevant ticket against its reporter and returns where that leaves them.
     *
     * <p>Runs inside the caller's transaction — the ticket's cancellation and the consequence of
     * it are one decision, and a block queued for an auth service that then rolls back would be
     * a lie.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ViolationRules.Outcome recordIrrelevant(UserRef reporter, UserRef dev, String reason) {
        UserViolation violation = repository.findByUserId(reporter.getId())
                .orElseGet(() -> newLedgerFor(reporter));
        violation.setIrrelevantCount(violation.getIrrelevantCount() + 1);

        ViolationRules.Outcome outcome = ViolationRules.outcomeFor(
                violation.getIrrelevantCount(), warnThreshold, blockThreshold);

        if (outcome != ViolationRules.Outcome.NONE && violation.getWarnedAt() == null) {
            violation.setWarnedAt(LocalDateTime.now());
        }
        if (outcome == ViolationRules.Outcome.BLOCKED && !violation.isBlocked()) {
            violation.setBlockedAt(LocalDateTime.now());
            violation.setBlockedByDev(dev);
            // A fresh block supersedes any earlier one that was lifted.
            violation.setUnblockedAt(null);
            violation.setUnblockedByDev(null);
            authSync.enqueueBlock(reporter.getSsoUserId(), reason);
            log.info("Blocked user {} after {} irrelevant tickets (threshold {})",
                    reporter.getId(), violation.getIrrelevantCount(), blockThreshold);
        } else {
            log.info("Recorded irrelevant ticket {} for user {} — outcome {}",
                    violation.getIrrelevantCount(), reporter.getId(), outcome);
        }
        repository.save(violation);
        return outcome;
    }

    /** Lifting a block clears the slate; the reporter is not left one report from the next one. */
    @Transactional
    public ViolationResponse unblock(UUID userId, UserRef dev) {
        UserViolation violation = repository.findByUserId(userId)
                .orElseThrow(() -> new NotFoundException("This user has no violation record"));
        if (!violation.isBlocked()) {
            throw new ConflictException("This user is not blocked");
        }
        violation.setBlockedAt(null);
        violation.setBlockedByDev(null);
        violation.setUnblockedAt(LocalDateTime.now());
        violation.setUnblockedByDev(dev);
        violation.setIrrelevantCount(0);
        violation.setWarnedAt(null);
        authSync.enqueueUnblock(violation.getUser().getSsoUserId(), "Unblocked by DEV " + dev.getId());
        repository.save(violation);
        log.info("Unblocked user {} by DEV {}", userId, dev.getId());
        return ViolationMapper.toResponse(violation);
    }

    /** What the ticket form's banner is built from. A clean record answers "nothing recorded". */
    @Transactional(readOnly = true)
    public ViolationStatusResponse statusFor(UUID userId) {
        UserViolation violation = repository.findByUserId(userId).orElse(null);
        int count = violation == null ? 0 : violation.getIrrelevantCount();
        boolean blocked = violation != null && violation.isBlocked();
        ViolationRules.Outcome outcome = ViolationRules.outcomeFor(count, warnThreshold, blockThreshold);
        return new ViolationStatusResponse(
                count,
                outcome != ViolationRules.Outcome.NONE,
                blocked,
                warnThreshold,
                blockThreshold,
                Math.max(blockThreshold - count, 0));
    }

    @Transactional(readOnly = true)
    public PageResponse<ViolationResponse> list(boolean blockedOnly, int page, int size) {
        Page<UserViolation> found = repository.listRecorded(blockedOnly,
                PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
        List<ViolationResponse> mapped = found.getContent().stream()
                .map(ViolationMapper::toResponse)
                .toList();
        return PageResponse.from(found, mapped);
    }

    /**
     * What the auth service asks at sign-in, keyed by the id it knows the account by.
     *
     * <p>An account this service has never heard of is not blocked — the absence of a ledger is
     * an answer, not a reason to fail the sign-in.
     */
    @Transactional(readOnly = true)
    public BlockStatusResponse blockStatusBySso(UUID ssoUserId) {
        return repository.findByUserSsoUserId(ssoUserId)
                .map(v -> new BlockStatusResponse(
                        ssoUserId,
                        v.isBlocked(),
                        v.getBlockedAt(),
                        v.getIrrelevantCount(),
                        v.isBlocked() ? BLOCKED_REASON : null))
                .orElseGet(() -> new BlockStatusResponse(ssoUserId, false, null, 0, null));
    }

    private UserViolation newLedgerFor(UserRef reporter) {
        UserViolation violation = new UserViolation();
        violation.setUser(reporter);
        return violation;
    }
}
