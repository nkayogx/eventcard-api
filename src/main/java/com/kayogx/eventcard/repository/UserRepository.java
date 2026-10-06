package com.kayogx.eventcard.repository;

import com.kayogx.eventcard.model.User;
import com.kayogx.eventcard.model.UserRole;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Users are filtered by company automatically, so these methods only ever
 * return users of the current company (unless running as "all companies").
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    boolean existsByRole(UserRole role);

    List<User> findAllByOrderByFullNameAsc();

    long countByRoleAndActiveTrue(UserRole role);

    long countByCompanyIdAndActiveTrue(UUID companyId);
}
