package com.kayogx.eventcard.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Payments are automatically limited to the logged-in user's company.
 * The platform admin works "as all companies", so sees every company's payments.
 */
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    List<Payment> findAllByOrderByCreatedAtDesc();

    List<Payment> findByStatusOrderByCreatedAtAsc(Payment.Status status);

    boolean existsByReference(String reference);
}
