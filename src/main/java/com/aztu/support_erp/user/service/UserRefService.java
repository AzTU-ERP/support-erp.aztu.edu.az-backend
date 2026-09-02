package com.aztu.support_erp.user.service;

import com.aztu.support_erp.common.enums.SupportRole;
import com.aztu.support_erp.common.exception.NotFoundException;
import com.aztu.support_erp.security.CurrentUser;
import com.aztu.support_erp.security.SsoUser;
import com.aztu.support_erp.user.domain.UserRef;
import com.aztu.support_erp.user.repository.UserRefRepository;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Keeps {@code users_ref} in step with the SSO. Every authenticated request resolves the caller
 * to a local row so foreign keys elsewhere point at something stable, even though the auth
 * service remains the source of truth for identity and roles.
 */
@Service
public class UserRefService {

    private static final Logger log = LoggerFactory.getLogger(UserRefService.class);

    private final UserRefRepository repository;

    public UserRefService(UserRefRepository repository) {
        this.repository = repository;
    }

    /** The local row for the caller, created on first contact and refreshed when the token changed. */
    @Transactional
    public UserRef resolveCurrent() {
        return resolve(CurrentUser.get());
    }

    @Transactional
    public UserRef resolve(SsoUser sso) {
        return repository.findBySsoUserId(sso.userId())
                .map(existing -> refresh(existing, sso))
                .orElseGet(() -> create(sso));
    }

    @Transactional(readOnly = true)
    public UserRef find(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NotFoundException("User not found"));
    }

    /** Board filters: DEVs type part of a name or email to narrow the queue to one reporter. */
    @Transactional(readOnly = true)
    public List<UserRef> search(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        return repository.search(query.trim(), PageRequest.of(0, Math.min(Math.max(limit, 1), 50)));
    }

    /** Who a ticket may be assigned to, for the board's assignee picker. */
    @Transactional(readOnly = true)
    public List<UserRef> listDevs() {
        return repository.findByRole(SupportRole.DEV.code());
    }

    private UserRef create(SsoUser sso) {
        UserRef user = new UserRef();
        user.setSsoUserId(sso.userId());
        user.setFullName(sso.fullName());
        user.setEmail(sso.email());
        user.setRoles(new LinkedHashSet<>(sso.roles()));
        try {
            return repository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            // Two first-ever requests raced; the other one won, so adopt its row.
            log.debug("users_ref insert raced for {}, re-reading", sso.userId());
            return repository.findBySsoUserId(sso.userId())
                    .map(existing -> refresh(existing, sso))
                    .orElseThrow(() -> ex);
        }
    }

    private UserRef refresh(UserRef existing, SsoUser sso) {
        boolean dirty = false;
        if (sso.fullName() != null && !Objects.equals(existing.getFullName(), sso.fullName())) {
            existing.setFullName(sso.fullName());
            dirty = true;
        }
        if (sso.email() != null && !Objects.equals(existing.getEmail(), sso.email())) {
            existing.setEmail(sso.email());
            dirty = true;
        }
        // Roles are authoritative in the token — mirror them verbatim, including removals.
        if (!sso.roles().isEmpty() && !existing.getRoles().equals(sso.roles())) {
            existing.setRoles(new LinkedHashSet<>(sso.roles()));
            dirty = true;
        }
        return dirty ? repository.save(existing) : existing;
    }
}
