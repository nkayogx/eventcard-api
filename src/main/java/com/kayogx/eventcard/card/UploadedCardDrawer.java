package com.kayogx.eventcard.card;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Draws a card on the vendor's own artwork: prints the guest's name, the card type
 * and the QR code in the boxes the vendor placed in the editor.
 */
public final class UploadedCardDrawer {

    private UploadedCardDrawer() {
    }

    public static byte[] draw(BufferedImage artwork, List<CardDesignField> fields, CardContent content) {
        CardCanvas canvas = CardCanvas.startingWith(artwork);

        for (CardDesignField field : fields) {
            if (!field.isVisible()) {
                continue;
            }
            Rectangle box = boxInPixels(field, canvas);
            Color color = CardCanvas.colorFromHex(field.getColor(), Color.BLACK);

            switch (field.getField()) {
                case GUEST_NAME -> canvas.textInBox(content.guestName(), field.getFont(), field.getFontSize(),
                        color, field.getAlign(), box);
                case CARD_TYPE -> canvas.textInBox(content.cardTypeName().toUpperCase(), field.getFont(),
                        field.getFontSize(), color, field.getAlign(), box);
                case QR_CODE -> drawQrCode(canvas, content.invitationLink(), box);
            }
        }
        return canvas.toPng();
    }

    /** Turns the percent position saved in the editor into pixels on this picture. */
    private static Rectangle boxInPixels(CardDesignField field, CardCanvas canvas) {
        return new Rectangle(
                (int) Math.round(field.getX() / 100 * canvas.width()),
                (int) Math.round(field.getY() / 100 * canvas.height()),
                (int) Math.round(field.getWidth() / 100 * canvas.width()),
                (int) Math.round(field.getHeight() / 100 * canvas.height()));
    }

    /** The QR code is always square: as big as the smaller side of the box, centred in it. */
    private static void drawQrCode(CardCanvas canvas, String link, Rectangle box) {
        int size = Math.min(box.width, box.height);
        canvas.pictureInBox(QrCodes.draw(link, size), box);
    }
}
