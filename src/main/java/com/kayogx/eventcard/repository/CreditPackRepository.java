package com.kayogx.eventcard.repository;

import com.kayogx.eventcard.model.CreditPack;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CreditPackRepository extends JpaRepository<CreditPack, UUID> {

    List<CreditPack> findAllByOrderBySortOrderAsc();

    List<CreditPack> findByAvailableTrueOrderBySortOrderAsc();
}
