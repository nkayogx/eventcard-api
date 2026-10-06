package com.kayogx.eventcard.repository;

import com.kayogx.eventcard.model.MessageChannel;
import com.kayogx.eventcard.model.MessagePrice;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MessagePriceRepository extends JpaRepository<MessagePrice, MessageChannel> {
}
