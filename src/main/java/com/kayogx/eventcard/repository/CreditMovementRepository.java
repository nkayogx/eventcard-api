package com.kayogx.eventcard.repository;

import com.kayogx.eventcard.model.CreditMovement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/** Credit statements are automatically limited to the logged-in user's company. */
public interface CreditMovementRepository extends JpaRepository<CreditMovement, UUID> {

    Page<CreditMovement> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
