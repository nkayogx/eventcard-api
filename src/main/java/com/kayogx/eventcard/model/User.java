package com.kayogx.eventcard.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/** A person who can log in. Belongs to exactly one company (except platform admins). */
@Entity
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * The company this user belongs to.
     * {@code @TenantId} means Hibernate fills this in and filters by it automatically
     * (see tenant/HibernateTenantSetup). For platform admins it holds CurrentTenant.ALL_COMPANIES.
     */
    @TenantId
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String fullName;

    /** Login name. Unique across the whole platform. Always stored in lowercase. */
    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    /** Never the real password - only a one-way BCrypt "fingerprint" of it. */
    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /** The owner can switch this off to block a staff member from logging in. */
    private boolean active = true;

    @CreationTimestamp
    private Instant createdAt;

    public boolean isPlatformAdmin() {
        return role == UserRole.PLATFORM_ADMIN;
    }
}
