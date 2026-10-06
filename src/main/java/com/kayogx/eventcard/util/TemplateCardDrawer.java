package com.kayogx.eventcard.util;

import com.kayogx.eventcard.dto.CardContent;
import com.kayogx.eventcard.model.CardFont;
import com.kayogx.eventcard.model.CardTemplate;

import java.awt.Color;
import java.awt.Rectangle;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * Draws our built-in card templates. Every template is a portrait card of 1080 x 1350 pixels
 * (a good shape for WhatsApp) and shows, from top to bottom:
 *
 *   logo · "YOU ARE INVITED" · hosts + invitation text · GUEST NAME · event name ·
 *   date & time · venue · card type · QR code · "Show this code at the entrance"
 *
 * The three templates differ only in colours, fonts and decoration.
 */
public final class TemplateCardDrawer {

    public static final int WIDTH = 1080;
    public static final int HEIGHT = 1350;

    private static final Color INK = new Color(0x1F1A1C);
    private static final Color SOFT_INK = new Color(0x6B6266);
    private static final Color DEFAULT_MAIN_COLOUR = new Color(0x7A1F3D);
    private static final Color DEFAULT_SECOND_COLOUR = new Color(0xF7E9EE);

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /** The fonts and colours one template uses. */
    private record Style(Color background, Color accent, Color text, Color softText,
                         CardFont headingFont, CardFont nameFont, float nameSize,
                         CardFont eventNameFont, float eventNameSize, CardFont bodyFont) {
    }

    private TemplateCardDrawer() {
    }

    public static byte[] draw(CardTemplate template, String invitationText, CardContent content) {
        Color mainColour = CardCanvas.colorFromHex(content.primaryColor(), DEFAULT_MAIN_COLOUR);
        Color secondColour = CardCanvas.colorFromHex(content.secondaryColor(), DEFAULT_SECOND_COLOUR);

        Style style = switch (template) {
            case CLASSIC -> new Style(Color.WHITE, mainColour, INK, SOFT_INK,
                    CardFont.MONTSERRAT_BOLD, CardFont.PLAYFAIR_BOLD, 76, CardFont.PLAYFAIR_BOLD, 50, CardFont.PLAYFAIR);
            case ELEGANT -> new Style(secondColour, mainColour, INK, SOFT_INK,
                    CardFont.PLAYFAIR, CardFont.GREAT_VIBES, 110, CardFont.GREAT_VIBES, 78, CardFont.PLAYFAIR);
            case MODERN -> new Style(Color.WHITE, mainColour, INK, SOFT_INK,
                    CardFont.MONTSERRAT_BOLD, CardFont.MONTSERRAT_BOLD, 70, CardFont.MONTSERRAT_BOLD, 46, CardFont.MONTSERRAT);
        };

        CardCanvas canvas = new CardCanvas(WIDTH, HEIGHT, style.background());
        int top = drawDecorationAndLogo(canvas, template, style, content);
        drawWording(canvas, style, template, invitationText, content, top);
        drawQrCodeAtBottom(canvas, style, content);
        return canvas.toPng();
    }

    /** Frames and header blocks. Returns where the wording should start. */
    private static int drawDecorationAndLogo(CardCanvas canvas, CardTemplate template, Style style, CardContent content) {
        Color headingColour = style.accent();
        int logoTop = 90;
        boolean hasLogo = content.logoOrNull() != null;
        // The Modern header band is shorter when there is no logo to show in it
        int modernBandHeight = hasLogo ? 300 : 200;

        switch (template) {
            case CLASSIC -> {
                canvas.frame(36, 5, style.accent());
                canvas.frame(52, 1.5f, style.accent());
            }
            case ELEGANT -> canvas.frame(44, 2, style.accent());
            case MODERN -> {
                canvas.filledRectangle(new Rectangle(0, 0, WIDTH, modernBandHeight), style.accent());
                headingColour = Color.WHITE;
                logoTop = hasLogo ? 50 : 22;
            }
        }

        if (hasLogo) {
            canvas.pictureInBox(content.logoOrNull(), new Rectangle((WIDTH - 260) / 2, logoTop, 260, 110));
        }
        int headingTop = hasLogo ? logoTop + 130 : logoTop + 60;
        int belowHeading = canvas.centeredLine("YOU ARE INVITED", style.headingFont(), 30, headingColour, headingTop, 900);
        if (template == CardTemplate.ELEGANT) {
            canvas.horizontalLine(belowHeading + 18, 220, 2, style.accent());
        }
        return template == CardTemplate.MODERN ? modernBandHeight + 50 : headingTop + 90;
    }

    private static void drawWording(CardCanvas canvas, Style style, CardTemplate template,
                                    String invitationText, CardContent content, int top) {
        int textWidth = 880;
        int y = top;

        if (content.hostNames() != null) {
            y = canvas.centeredLine(content.hostNames(), style.bodyFont(), 36, style.text(), y, textWidth);
        }
        if (invitationText != null && !invitationText.isBlank()) {
            y = canvas.centeredParagraph(invitationText, style.bodyFont(), 30, style.softText(), y + 6, textWidth);
        }

        // The guest's name is the star of the card
        y = canvas.centeredLine(content.guestName(), style.nameFont(), style.nameSize(), style.accent(), y + 24, textWidth);
        if (template != CardTemplate.ELEGANT) {
            canvas.horizontalLine(y + 16, 160, 2, style.accent());
        }

        y = canvas.centeredLine(content.eventName(), style.eventNameFont(), style.eventNameSize(), style.text(), y + 40, textWidth);
        y = canvas.centeredLine(DAY.format(content.startsAt()) + "  ·  " + TIME.format(content.startsAt()),
                style.bodyFont(), 30, style.text(), y + 18, textWidth);
        y = canvas.centeredLine(content.venueName(), style.bodyFont(), 30, style.text(), y + 4, textWidth);
        if (content.dressCode() != null) {
            canvas.centeredLine("Dress code: " + content.dressCode(), style.bodyFont(), 24, style.softText(), y + 4, textWidth);
        }
    }

    private static void drawQrCodeAtBottom(CardCanvas canvas, Style style, CardContent content) {
        int qrSize = 230;
        int qrTop = HEIGHT - 60 - 40 - qrSize - 50;
        String cardTypeLine = content.cardTypeName().toUpperCase() + "  ·  "
                + content.seats() + (content.seats() == 1 ? " SEAT" : " SEATS");
        canvas.centeredLine(cardTypeLine, CardFont.MONTSERRAT_BOLD, 24, style.accent(), qrTop - 50, 800);
        canvas.pictureInBox(QrCodes.draw(content.invitationLink(), qrSize),
                new Rectangle((WIDTH - qrSize) / 2, qrTop, qrSize, qrSize));
        canvas.centeredLine("Show this code at the entrance", CardFont.MONTSERRAT, 22, style.softText(),
                qrTop + qrSize + 14, 800);
    }
}
