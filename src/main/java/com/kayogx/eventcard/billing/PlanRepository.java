package com.kayogx.eventcard.billing;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanRepository extends JpaRepository<Plan, UUID> {

    Optional<Plan> findFirstByFreePlanTrue();

    Optional<Plan> findByCode(String code);

    List<Plan> findAllByOrderBySortOrderAsc();

    List<Plan> findByAvailableTrueOrderBySortOrderAsc();
}
