package com.kayogx.eventcard.billing;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

/**
 * How many credits one message costs on each channel.
 * For SMS this is per SMS part (one part = up to 160 letters).
 */
@Entity
@Table(name = "message_prices")
@Getter
@Setter
public class MessagePrice {

    @Id
    @Enumerated(EnumType.STRING)
    private MessageChannel channel;

    private int credits;
}
