package com.kayogx.eventcard.billing;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessagePriceRepository extends JpaRepository<MessagePrice, MessageChannel> {
}
