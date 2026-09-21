package com.kayogx.eventcard.company;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    boolean existsBySlug(String slug);

    boolean existsByCustomDomainIgnoreCase(String customDomain);

    boolean existsByCustomDomainIgnoreCaseAndCustomDomainVerifiedTrue(String customDomain);

    /** Used by the platform admin's search box. */
    Page<Company> findByNameContainingIgnoreCase(String namePart, Pageable pageable);
}
