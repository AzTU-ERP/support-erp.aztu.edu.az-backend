package com.aztu.support_erp.user.repository;

import com.aztu.support_erp.user.domain.UserRef;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRefRepository extends JpaRepository<UserRef, UUID> {

    Optional<UserRef> findBySsoUserId(UUID ssoUserId);

    @Query("""
            select u from UserRef u
            where lower(u.fullName) like lower(concat('%', :q, '%'))
               or lower(u.email) like lower(concat('%', :q, '%'))
            order by u.fullName
            """)
    List<UserRef> search(@Param("q") String q, Pageable pageable);

    /**
     * Everyone the board can be assigned to. Only DEVs who have signed in at least once are
     * known here — the auth service holds the full roster, this is its local projection.
     */
    @Query("""
            select u from UserRef u
            where :role member of u.roles
            order by u.fullName
            """)
    List<UserRef> findByRole(@Param("role") String role);
}
