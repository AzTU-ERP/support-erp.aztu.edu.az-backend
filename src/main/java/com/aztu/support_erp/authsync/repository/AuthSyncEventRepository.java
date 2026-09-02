package com.aztu.support_erp.authsync.repository;

import com.aztu.support_erp.authsync.domain.AuthSyncEvent;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSyncEventRepository extends JpaRepository<AuthSyncEvent, UUID> {

    /** Everything still owed to the auth service, oldest first, below the attempt ceiling. */
    List<AuthSyncEvent> findByStatusInAndAttemptsLessThanOrderByCreatedAtAsc(
            Collection<String> statuses, int maxAttempts);
}
