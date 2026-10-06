package com.kayogx.eventcard.repository;

import com.kayogx.eventcard.model.CardDesignField;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CardDesignFieldRepository extends JpaRepository<CardDesignField, UUID> {

    List<CardDesignField> findByDesignId(UUID designId);

    void deleteAllByDesignId(UUID designId);
}
