package com.kayogx.eventcard.dto;

import com.kayogx.eventcard.model.CardDesignField;
import com.kayogx.eventcard.model.CardDesignKind;
import com.kayogx.eventcard.model.CardField;
import com.kayogx.eventcard.model.CardFont;
import com.kayogx.eventcard.model.CardTemplate;
import com.kayogx.eventcard.model.TextAlign;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/** The shapes of card design data sent to and from the API. */
public final class CardDesignForms {

    private CardDesignForms() {
    }

    /** One box on an uploaded design (guest name, card type or QR code) and how its text looks. */
    public record FieldSettings(

            @NotNull(message = "Please say which field this is")
            CardField field,

            @DecimalMin(value = "0", message = "Boxes must stay inside the picture")
            @DecimalMax(value = "100", message = "Boxes must stay inside the picture")
            double x,

            @DecimalMin(value = "0", message = "Boxes must stay inside the picture")
            @DecimalMax(value = "100", message = "Boxes must stay inside the picture")
            double y,

            @DecimalMin(value = "1", message = "Boxes must be at least 1% wide")
            @DecimalMax(value = "100", message = "Boxes must stay inside the picture")
            double width,

            @DecimalMin(value = "1", message = "Boxes must be at least 1% high")
            @DecimalMax(value = "100", message = "Boxes must stay inside the picture")
            double height,

            @NotNull(message = "Please choose a font")
            CardFont font,

            @Min(value = 8, message = "Text size must be at least 8")
            @Max(value = 400, message = "Text size can be at most 400")
            int fontSize,

            @NotNull
            @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Colour must look like #1F1A1C")
            String color,

            @NotNull(message = "Please choose an alignment")
            TextAlign align,

            boolean visible
    ) {

        public static FieldSettings from(CardDesignField field) {
            return new FieldSettings(field.getField(), field.getX(), field.getY(), field.getWidth(), field.getHeight(),
                    field.getFont(), field.getFontSize(), field.getColor(), field.getAlign(), field.isVisible());
        }
    }

    /** Saving the design from the editor. */
    public record SaveCardDesignRequest(

            @NotNull(message = "Please choose uploaded design or template")
            CardDesignKind kind,

            CardTemplate templateName,

            @Size(max = 300, message = "The invitation wording can be at most 300 characters")
            String invitationText,

            List<@Valid FieldSettings> fields
    ) {
    }

    /** A card type and its optional special artwork. */
    public record CardTypeLook(UUID id, String name, int seats, String backgroundUrl) {
    }

    /** Everything the design editor needs. {@code backgroundUrl}, width and height are empty for templates. */
    public record CardDesignDetails(
            CardDesignKind kind,
            CardTemplate templateName,
            String invitationText,
            String backgroundUrl,
            Integer width,
            Integer height,
            int version,
            List<FieldSettings> fields,
            List<CardTypeLook> cardTypes
    ) {
    }
}
