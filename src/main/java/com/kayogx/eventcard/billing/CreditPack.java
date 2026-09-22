package com.kayogx.eventcard.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/** A bundle of message credits a company can buy, e.g. "500 credits for TSh 25,000". */
@Entity
@Table(name = "credit_packs")
@Getter
@Setter
public class CreditPack {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 60)
    private String name;

    private int credits;

    private long priceTzs;

    private boolean available = true;

    private int sortOrder;
}
