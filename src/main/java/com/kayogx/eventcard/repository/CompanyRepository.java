package com.kayogx.eventcard.repository;

import com.kayogx.eventcard.model.Company;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsBySlug(String slug);

    boolean existsByCustomDomainIgnoreCase(String customDomain);

    boolean existsByCustomDomainIgnoreCaseAndCustomDomainVerifiedTrue(String customDomain);

    /**
     * Loads the company and LOCKS its row until the transaction ends, so two changes
     * to its credit balance at the same moment cannot overwrite each other.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select company from Company company where company.id = :companyId")
    Optional<Company> findByIdAndLockIt(UUID companyId);

    /** Used by the platform admin's search box. */
    Page<Company> findByNameContainingIgnoreCase(String namePart, Pageable pageable);
}
