package com.kayogx.eventcard.card;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.TenantId;

import java.util.UUID;

/** How the invitation cards of one event look. Every event has exactly one design. */
@Entity
@Table(name = "card_designs")
@Getter
@Setter
public class CardDesign {

    public static final String DEFAULT_INVITATION_TEXT = "request the pleasure of your company at";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @TenantId
    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(nullable = false, unique = true)
    private UUID eventId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardDesignKind kind = CardDesignKind.TEMPLATE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CardTemplate templateName = CardTemplate.CLASSIC;

    /** The uploaded artwork's file name in the "card-backgrounds" folder (uploaded designs only). */
    private String backgroundFile;

    /** Size of the uploaded artwork in pixels. */
    private Integer width;

    private Integer height;

    /** The wording on template cards, e.g. "request the pleasure of your company at". */
    @Column(length = 300)
    private String invitationText = DEFAULT_INVITATION_TEXT;

    /** Goes up by one on every change, so old card images are drawn again. */
    @Column(nullable = false)
    private int version = 1;

    public void markChanged() {
        version++;
    }

    public boolean isUploaded() {
        return kind == CardDesignKind.UPLOADED;
    }
}
