package com.kayogx.eventcard.service;

import com.kayogx.eventcard.dto.CardContent;
import com.kayogx.eventcard.exception.NotFoundException;
import com.kayogx.eventcard.model.CardDesign;
import com.kayogx.eventcard.model.CardTemplate;
import com.kayogx.eventcard.model.CardType;
import com.kayogx.eventcard.model.Company;
import com.kayogx.eventcard.model.Event;
import com.kayogx.eventcard.model.Guest;
import com.kayogx.eventcard.repository.CardDesignFieldRepository;
import com.kayogx.eventcard.repository.CardDesignRepository;
import com.kayogx.eventcard.repository.CardTypeRepository;
import com.kayogx.eventcard.repository.CompanyRepository;
import com.kayogx.eventcard.repository.EventRepository;
import com.kayogx.eventcard.util.TemplateCardDrawer;
import com.kayogx.eventcard.util.UploadedCardDrawer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Makes card images:
 *  - {@link #cardForGuest} - a real guest's card (kept in {@link CardImageCache});
 *  - {@link #sampleCard}   - a preview with a sample name, for the design editor.
 *
 * Callers must already be inside a transaction that can see the event's company.
 */
@Component
@Slf4j
public class CardMaker {

    public static final String BACKGROUNDS_FOLDER = "card-backgrounds";
    private static final String SAMPLE_GUEST_NAME = "Mr & Mrs Sample";

    private final CardDesignRepository designRepository;
    private final CardDesignFieldRepository fieldRepository;
    private final CardTypeRepository cardTypeRepository;
    private final EventRepository eventRepository;
    private final CompanyRepository companyRepository;
    private final FileStorage fileStorage;
    private final CardImageCache cardImageCache;
    private final InvitationLinks invitationLinks;

    public CardMaker(CardDesignRepository designRepository,
                     CardDesignFieldRepository fieldRepository,
                     CardTypeRepository cardTypeRepository,
                     EventRepository eventRepository,
                     CompanyRepository companyRepository,
                     FileStorage fileStorage,
                     CardImageCache cardImageCache,
                     InvitationLinks invitationLinks) {
        this.designRepository = designRepository;
        this.fieldRepository = fieldRepository;
        this.cardTypeRepository = cardTypeRepository;
        this.eventRepository = eventRepository;
        this.companyRepository = companyRepository;
        this.fileStorage = fileStorage;
        this.cardImageCache = cardImageCache;
        this.invitationLinks = invitationLinks;
    }

    /** The guest's card as a PNG. Drawn once, then reused until something on it changes. */
    public byte[] cardForGuest(Guest guest) {
        Event event = eventRepository.findById(guest.getEventId()).orElseThrow(() -> new NotFoundException("Event not found"));
        Company company = companyRepository.findById(event.getCompanyId()).orElseThrow();
        CardType cardType = cardTypeRepository.findById(guest.getCardTypeId()).orElseThrow();
        CardDesign design = designOrDefault(event);
        String link = invitationLinks.linkFor(company, guest.getInvitationCode());

        String fingerprint = String.join("|",
                "design", String.valueOf(design.getId()), String.valueOf(design.getVersion()), design.getKind().name(),
                "type", cardType.getId().toString(), cardType.getName(), String.valueOf(cardType.getSeats()),
                String.valueOf(cardType.getBackgroundFile()),
                "guest", guest.getNameOnCard(), link,
                "event", String.valueOf(event.getUpdatedAt()),
                "company", String.valueOf(company.getUpdatedAt()));

        return cardImageCache.getOrDraw(fingerprint,
                () -> draw(design, cardType, content(guest.getNameOnCard(), cardType, link, event, company)));
    }

    /**
     * A preview card with a sample guest name (never saved).
     * @param templateOrNull draw this template instead of the saved design (for template thumbnails)
     */
    public byte[] sampleCard(Event event, UUID cardTypeIdOrNull, CardTemplate templateOrNull) {
        Company company = companyRepository.findById(event.getCompanyId()).orElseThrow();
        List<CardType> cardTypes = cardTypeRepository.findByEventIdOrderBySortOrderAsc(event.getId());
        CardType cardType = cardTypes.stream()
                .filter(type -> type.getId().equals(cardTypeIdOrNull))
                .findFirst()
                .orElse(cardTypes.get(cardTypes.size() > 1 ? 1 : 0)); // "Double" looks best as a sample
        String sampleLink = invitationLinks.linkFor(company, "SAMPLE");
        CardContent content = content(SAMPLE_GUEST_NAME, cardType, sampleLink, event, company);

        CardDesign design = designOrDefault(event);
        if (templateOrNull != null) {
            return TemplateCardDrawer.draw(templateOrNull, design.getInvitationText(), content);
        }
        return draw(design, cardType, content);
    }

    private byte[] draw(CardDesign design, CardType cardType, CardContent content) {
        if (!design.isUploaded()) {
            return TemplateCardDrawer.draw(design.getTemplateName(), design.getInvitationText(), content);
        }
        // A card type may have its own artwork (e.g. gold for VIP); otherwise use the main artwork
        String artworkFile = cardType.getBackgroundFile() != null ? cardType.getBackgroundFile() : design.getBackgroundFile();
        BufferedImage artwork = readPicture(fileStorage.read(BACKGROUNDS_FOLDER, artworkFile));
        return UploadedCardDrawer.draw(artwork, fieldRepository.findByDesignId(design.getId()), content);
    }

    /** The saved design, or - if the vendor never opened the designer - the default Classic template. */
    private CardDesign designOrDefault(Event event) {
        return designRepository.findByEventId(event.getId()).orElseGet(CardDesign::new);
    }

    private CardContent content(String guestName, CardType cardType, String link, Event event, Company company) {
        return new CardContent(guestName, cardType.getName(), cardType.getSeats(), link,
                event.getName(), event.getHostNames(), event.getStartsAt(), event.getVenueName(), event.getDressCode(),
                company.getPrimaryColor(), company.getSecondaryColor(), logoOf(company));
    }

    /** The company logo as a picture, or null if there is none (or it cannot be read, e.g. SVG). */
    private BufferedImage logoOf(Company company) {
        if (company.getLogoUrl() == null) {
            return null;
        }
        try {
            String fileName = company.getLogoUrl().substring(company.getLogoUrl().lastIndexOf('/') + 1);
            return ImageIO.read(new ByteArrayInputStream(fileStorage.read("logos", fileName)));
        } catch (IOException | RuntimeException unreadable) {
            log.info("Company logo could not be drawn on the card: {}", unreadable.getMessage());
            return null;
        }
    }

    static BufferedImage readPicture(byte[] file) {
        try {
            BufferedImage picture = ImageIO.read(new ByteArrayInputStream(file));
            if (picture == null) {
                throw new IllegalStateException("Not a picture");
            }
            return picture;
        } catch (IOException problem) {
            throw new IllegalStateException("Could not read the card artwork", problem);
        }
    }
}
