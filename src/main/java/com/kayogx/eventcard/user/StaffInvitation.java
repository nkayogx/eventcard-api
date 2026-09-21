package com.kayogx.eventcard.user;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.TenantId;

import java.time.Instant;
import java.util.UUID;

/**
 * An invitation for a new staff member to join a company.
 * The owner shares a link containing the secret {@link #code}; the invited
 * person opens it, chooses a password, and becomes a user of that company.
 */
@Entity
@Table(name = "staff_invitations")
@Getter
@Setter
public class StaffInvitation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** The company the person is invited to. Filled in and filtered automatically. */
    @TenantId
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role;

    /** The secret part of the invitation link. */
    @Column(nullable = false, unique = true)
    private String code;

    @Column(nullable = false)
    private Instant expiresAt;

    /** Filled in when the invitation is used. A used invitation cannot be used again. */
    private Instant acceptedAt;

    @Column(nullable = false)
    private UUID invitedByUserId;

    @CreationTimestamp
    private Instant createdAt;

    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    public boolean isAlreadyUsed() {
        return acceptedAt != null;
    }
}
