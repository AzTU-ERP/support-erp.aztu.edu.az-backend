package com.aztu.support_erp.violation.repository;

import com.aztu.support_erp.violation.domain.UserViolation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserViolationRepository extends JpaRepository<UserViolation, UUID> {

    Optional<UserViolation> findByUserId(UUID userId);

    Optional<UserViolation> findByUserSsoUserId(UUID ssoUserId);

    /**
     * The violations tab: everyone who has been warned or blocked, blocked accounts first
     * because they are the ones waiting on a DEV.
     */
    @Query("""
            select v from UserViolation v
            where v.irrelevantCount > 0
              and (:blockedOnly = false or v.blockedAt is not null)
            order by case when v.blockedAt is null then 1 else 0 end, v.updatedAt desc
            """)
    Page<UserViolation> listRecorded(@Param("blockedOnly") boolean blockedOnly, Pageable pageable);
}
