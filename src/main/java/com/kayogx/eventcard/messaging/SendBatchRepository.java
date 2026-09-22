package com.kayogx.eventcard.messaging;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SendBatchRepository extends JpaRepository<SendBatch, UUID> {

    List<SendBatch> findTop10ByEventIdOrderByCreatedAtDesc(UUID eventId);
}
