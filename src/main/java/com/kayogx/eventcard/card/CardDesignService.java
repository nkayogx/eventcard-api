package com.kayogx.eventcard.card;

import com.kayogx.eventcard.card.CardDesignForms.*;
import com.kayogx.eventcard.common.ConflictException;
import com.kayogx.eventcard.common.InvalidInputException;
import com.kayogx.eventcard.common.NotFoundException;
import com.kayogx.eventcard.common.RandomCodes;
import com.kayogx.eventcard.event.CardType;
import com.kayogx.eventcard.event.CardTypeRepository;
import com.kayogx.eventcard.event.Event;
import com.kayogx.eventcard.event.EventFinder;
import com.kayogx.eventcard.guest.Guest;
import com.kayogx.eventcard.guest.GuestRepository;
import com.kayogx.eventcard.storage.FileStorage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * The card design editor: choosing a template or uploading artwork, placing the
 * boxes, card-type artwork, and previews. Works on the logged-in user's company only.
 */
@Service
public class CardDesignService {

    private static final int MAX_ARTWORK_BYTES = 5 * 1024 * 1024;

    private final CardDesignRepository designRepository;
    private final CardDesignFieldRepository fieldRepository;
    private final CardTypeRepository cardTypeRepository;
    private final GuestRepository guestRepository;
    private final EventFinder eventFinder;
    private final FileStorage fileStorage;
    private final CardMaker cardMaker;

    public CardDesignService(CardDesignRepository designRepository,
                             CardDesignFieldRepository fieldRepository,
                             CardTypeRepository cardTypeRepository,
                             GuestRepository guestRepository,
                             EventFinder eventFinder,
                             FileStorage fileStorage,
                             CardMaker cardMaker) {
        this.designRepository = designRepository;
        this.fieldRepository = fieldRepository;
        this.cardTypeRepository = cardTypeRepository;
        this.guestRepository = guestRepository;
        this.eventFinder = eventFinder;
        this.fileStorage = fileStorage;
        this.cardMaker = cardMaker;
    }

    @Transactional
    public CardDesignDetails getDesign(UUID eventId) {
        Event event = eventFinder.findEvent(eventId);
        return detailsOf(savedDesignFor(event));
    }

    @Transactional
    public CardDesignDetails saveDesign(UUID eventId, SaveCardDesignRequest request) {
        Event event = eventFinder.findChangeableEvent(eventId);
        CardDesign design = savedDesignFor(event);

        if (request.kind() == CardDesignKind.UPLOADED && design.getBackgroundFile() == null) {
            throw new InvalidInputException("Please upload your card artwork first", "kind");
        }
        design.setKind(request.kind());
        if (request.templateName() != null) {
            design.setTemplateName(request.templateName());
        }
        if (request.invitationText() != null) {
            design.setInvitationText(request.invitationText().trim());
        }
        if (request.fields() != null) {
            request.fields().forEach(settings -> updateField(design, settings));
        }
        design.markChanged();
        return detailsOf(design);
    }

    /** Uploading artwork switches the design to "uploaded". The first upload places the boxes in sensible spots. */
    @Transactional
    public CardDesignDetails uploadArtwork(UUID eventId, MultipartFile file) {
        Event event = eventFinder.findChangeableEvent(eventId);
        CardDesign design = savedDesignFor(event);
        Artwork artwork = readArtwork(file);

        boolean sizeChanged = design.getWidth() != null
                && (design.getWidth() != artwork.picture().getWidth() || design.getHeight() != artwork.picture().getHeight());
        if (sizeChanged) {
            // Card-type artwork must match the main artwork's size, so remove the old ones
            cardTypeRepository.findByEventIdOrderBySortOrderAsc(eventId).forEach(type -> type.setBackgroundFile(null));
        }

        design.setBackgroundFile(saveArtwork(eventId, artwork));
        design.setWidth(artwork.picture().getWidth());
        design.setHeight(artwork.picture().getHeight());
        design.setKind(CardDesignKind.UPLOADED);
        if (fieldRepository.findByDesignId(design.getId()).isEmpty()) {
            addDefaultFields(design);
        }
        design.markChanged();
        return detailsOf(design);
    }

    /** Special artwork for one card type (e.g. gold for VIP). Must be the same size as the main artwork. */
    @Transactional
    public CardDesignDetails uploadCardTypeArtwork(UUID eventId, UUID cardTypeId, MultipartFile file) {
        Event event = eventFinder.findChangeableEvent(eventId);
        CardDesign design = savedDesignFor(event);
        if (!design.isUploaded()) {
            throw new ConflictException("Special card type artwork is only for uploaded designs. Upload your main artwork first.");
        }
        CardType cardType = findCardType(eventId, cardTypeId);
        Artwork artwork = readArtwork(file);

        if (artwork.picture().getWidth() != design.getWidth() || artwork.picture().getHeight() != design.getHeight()) {
            throw new InvalidInputException("This picture is " + artwork.picture().getWidth() + " x " + artwork.picture().getHeight()
                    + " pixels. It must be exactly the same size as the main artwork (" + design.getWidth() + " x "
                    + design.getHeight() + ") so the name and QR code land in the same places.", "file");
        }
        cardType.setBackgroundFile(saveArtwork(eventId, artwork));
        return detailsOf(design);
    }

    @Transactional
    public CardDesignDetails removeCardTypeArtwork(UUID eventId, UUID cardTypeId) {
        Event event = eventFinder.findChangeableEvent(eventId);
        findCardType(eventId, cardTypeId).setBackgroundFile(null);
        return detailsOf(savedDesignFor(event));
    }

    @Transactional(readOnly = true)
    public byte[] previewCard(UUID eventId, UUID cardTypeIdOrNull, CardTemplate templateOrNull) {
        return cardMaker.sampleCard(eventFinder.findEvent(eventId), cardTypeIdOrNull, templateOrNull);
    }

    @Transactional
    public byte[] guestCard(UUID eventId, UUID guestId) {
        eventFinder.findEvent(eventId);
        Guest guest = guestRepository.findByIdAndEventId(guestId, eventId)
                .orElseThrow(() -> new NotFoundException("Guest not found"));
        return cardMaker.cardForGuest(guest);
    }

    /** Deletes an event's design and boxes (used when a draft event is deleted). */
    @Transactional
    public void deleteDesignOf(UUID eventId) {
        designRepository.findByEventId(eventId).ifPresent(design -> {
            fieldRepository.deleteAllByDesignId(design.getId());
            designRepository.delete(design);
        });
    }

    // ---------- helpers ----------

    /** The event's design; created (as the Classic template) the first time it is needed. */
    private CardDesign savedDesignFor(Event event) {
        return designRepository.findByEventId(event.getId()).orElseGet(() -> {
            CardDesign design = new CardDesign();
            design.setCompanyId(event.getCompanyId());
            design.setEventId(event.getId());
            return designRepository.save(design);
        });
    }

    private void updateField(CardDesign design, FieldSettings settings) {
        if (settings.x() + settings.width() > 100.01 || settings.y() + settings.height() > 100.01) {
            throw new InvalidInputException("The " + settings.field().name().toLowerCase().replace('_', ' ')
                    + " box goes outside the picture", "fields");
        }
        CardDesignField field = fieldRepository.findByDesignId(design.getId()).stream()
                .filter(existing -> existing.getField() == settings.field())
                .findFirst()
                .orElseThrow(() -> new InvalidInputException("Please upload your card artwork first", "fields"));

        field.setX(settings.x());
        field.setY(settings.y());
        field.setWidth(settings.width());
        field.setHeight(settings.height());
        field.setFont(settings.font());
        field.setFontSize(settings.fontSize());
        field.setColor(settings.color().toUpperCase());
        field.setAlign(settings.align());
        field.setVisible(settings.visible());
    }

    /** Where the boxes start on newly uploaded artwork: name in the middle, card type below, QR code near the bottom. */
    private void addDefaultFields(CardDesign design) {
        int pictureHeight = design.getHeight();
        double qrWidth = 20;
        double qrHeight = qrWidth * design.getWidth() / pictureHeight; // keeps the QR box square in pixels

        fieldRepository.save(newField(design, CardField.GUEST_NAME, 10, 40, 80, 10,
                CardFont.PLAYFAIR_BOLD, Math.max(8, pictureHeight / 20)));
        fieldRepository.save(newField(design, CardField.CARD_TYPE, 25, 52, 50, 5,
                CardFont.MONTSERRAT_BOLD, Math.max(8, pictureHeight / 40)));
        fieldRepository.save(newField(design, CardField.QR_CODE, 40, Math.min(70, 100 - qrHeight - 2), qrWidth, qrHeight,
                CardFont.MONTSERRAT, 12));
    }

    private static CardDesignField newField(CardDesign design, CardField which, double x, double y, double width,
                                            double height, CardFont font, int fontSize) {
        CardDesignField field = new CardDesignField();
        field.setCompanyId(design.getCompanyId());
        field.setDesignId(design.getId());
        field.setField(which);
        field.setX(x);
        field.setY(y);
        field.setWidth(width);
        field.setHeight(height);
        field.setFont(font);
        field.setFontSize(fontSize);
        return field;
    }

    private record Artwork(byte[] bytes, BufferedImage picture, String fileType) {
    }

    private static Artwork readArtwork(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new InvalidInputException("Please choose a picture", "file");
        }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length > MAX_ARTWORK_BYTES) {
                throw new InvalidInputException("The picture is too large. The maximum size is 5 MB.", "file");
            }
            boolean isPng = bytes.length > 4 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G';
            boolean isJpg = bytes.length > 3 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xD8;
            if (!isPng && !isJpg) {
                throw new InvalidInputException("The card artwork must be a PNG or JPG picture", "file");
            }
            return new Artwork(bytes, CardMaker.readPicture(bytes), isPng ? "png" : "jpg");
        } catch (IOException problem) {
            throw new UncheckedIOException(problem);
        } catch (IllegalStateException unreadable) {
            throw new InvalidInputException("We could not read this picture. Please save it again as PNG or JPG.", "file");
        }
    }

    private String saveArtwork(UUID eventId, Artwork artwork) {
        String fileName = eventId + "-" + RandomCodes.newCode() + "." + artwork.fileType();
        fileStorage.save(CardMaker.BACKGROUNDS_FOLDER, fileName, artwork.bytes());
        return fileName;
    }

    private CardType findCardType(UUID eventId, UUID cardTypeId) {
        return cardTypeRepository.findByIdAndEventId(cardTypeId, eventId)
                .orElseThrow(() -> new NotFoundException("Card type not found"));
    }

    private CardDesignDetails detailsOf(CardDesign design) {
        List<FieldSettings> fields = fieldRepository.findByDesignId(design.getId()).stream()
                .sorted(Comparator.comparing(CardDesignField::getField)) // always name, card type, QR
                .map(FieldSettings::from)
                .toList();
        List<CardTypeLook> cardTypes = cardTypeRepository.findByEventIdOrderBySortOrderAsc(design.getEventId()).stream()
                .map(type -> new CardTypeLook(type.getId(), type.getName(), type.getSeats(), artworkUrl(type.getBackgroundFile())))
                .toList();
        return new CardDesignDetails(design.getKind(), design.getTemplateName(), design.getInvitationText(),
                artworkUrl(design.getBackgroundFile()), design.getWidth(), design.getHeight(), design.getVersion(),
                fields, cardTypes);
    }

    private String artworkUrl(String fileName) {
        return fileName == null ? null : fileStorage.publicUrl(CardMaker.BACKGROUNDS_FOLDER, fileName);
    }
}
