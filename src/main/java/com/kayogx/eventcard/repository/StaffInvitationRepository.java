package com.kayogx.eventcard.repository;

import com.kayogx.eventcard.model.StaffInvitation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StaffInvitationRepository extends JpaRepository<StaffInvitation, UUID> {

    Optional<StaffInvitation> findByCode(String code);
}
