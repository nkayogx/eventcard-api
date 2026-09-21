package com.kayogx.eventcard.card;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

/**
 * Where one thing (guest name, card type or QR code) is printed on an uploaded design,
 * and how it looks.
 *
 * Positions are in PERCENT of the picture (x = 50 means half-way across), so they stay
 * correct whatever size the picture is shown at in the editor.
 */
@Entity
@Table(name = "card_design_fields")
@Getter
@Setter
public class CardDesignField {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID designId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardField field;

    /** Left edge, in percent of the picture's width. */
    private double x;

    /** Top edge, in percent of the picture's height. */
    private double y;

    private double width;

    private double height;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardFont font = CardFont.PLAYFAIR_BOLD;

    /** Text size in pixels of the original picture. Long text is shrunk to fit the box. */
    private int fontSize;

    /** Text colour, e.g. "#1F1A1C". */
    @Column(nullable = false, length = 7)
    private String color = "#1F1A1C";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TextAlign align = TextAlign.CENTER;

    private boolean visible = true;
}
