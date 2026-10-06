package com.kayogx.eventcard.util;

import com.kayogx.eventcard.model.CardFont;
import com.kayogx.eventcard.model.TextAlign;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * A picture we draw a card on, with a few easy drawing helpers
 * (text in a box, centred text, wrapped text, pictures, frames).
 *
 * Wraps Java's built-in Graphics2D so the card drawers stay short and readable.
 */
public class CardCanvas {

    private final BufferedImage picture;
    private final Graphics2D pen;

    /** A blank canvas filled with one colour. */
    public CardCanvas(int width, int height, Color background) {
        this(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB));
        pen.setColor(background);
        pen.fillRect(0, 0, width, height);
    }

    /** A canvas that starts with a copy of the given artwork. */
    public static CardCanvas startingWith(BufferedImage artwork) {
        CardCanvas canvas = new CardCanvas(new BufferedImage(artwork.getWidth(), artwork.getHeight(), BufferedImage.TYPE_INT_RGB));
        canvas.pen.drawImage(artwork, 0, 0, null);
        return canvas;
    }

    private CardCanvas(BufferedImage picture) {
        this.picture = picture;
        this.pen = picture.createGraphics();
        // Smooth edges on text and shapes
        pen.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        pen.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        pen.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        pen.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
    }

    public int width() {
        return picture.getWidth();
    }

    public int height() {
        return picture.getHeight();
    }

    /**
     * Writes one line of text inside a box. If the text is too wide for the box,
     * the letters are made smaller until it fits.
     */
    public void textInBox(String text, CardFont font, float fontSize, Color color, TextAlign align, Rectangle box) {
        Font chosenFont = shrinkToFit(text, font, fontSize, box.width);
        FontMetrics measure = pen.getFontMetrics(chosenFont);
        int textWidth = measure.stringWidth(text);

        int x = switch (align) {
            case LEFT -> box.x;
            case CENTER -> box.x + (box.width - textWidth) / 2;
            case RIGHT -> box.x + box.width - textWidth;
        };
        // Put the middle of the letters in the middle of the box
        int y = box.y + (box.height - measure.getHeight()) / 2 + measure.getAscent();

        pen.setFont(chosenFont);
        pen.setColor(color);
        pen.drawString(text, x, y);
    }

    /**
     * Writes a line of text centred across the card, with its top at {@code top}.
     * Returns where the next line should start.
     */
    public int centeredLine(String text, CardFont font, float fontSize, Color color, int top, int maxWidth) {
        Font chosenFont = shrinkToFit(text, font, fontSize, maxWidth);
        FontMetrics measure = pen.getFontMetrics(chosenFont);
        pen.setFont(chosenFont);
        pen.setColor(color);
        pen.drawString(text, (width() - measure.stringWidth(text)) / 2, top + measure.getAscent());
        return top + measure.getHeight();
    }

    /** Like {@link #centeredLine}, but long text is split over several lines. Returns where the next line starts. */
    public int centeredParagraph(String text, CardFont font, float fontSize, Color color, int top, int maxWidth) {
        FontMetrics measure = pen.getFontMetrics(font.atSize(fontSize));
        int nextTop = top;
        for (String line : splitIntoLines(text, measure, maxWidth)) {
            nextTop = centeredLine(line, font, fontSize, color, nextTop, maxWidth);
        }
        return nextTop;
    }

    /** Draws a picture scaled to fit inside the box, keeping its shape, centred. */
    public void pictureInBox(Image image, Rectangle box) {
        int imageWidth = image.getWidth(null);
        int imageHeight = image.getHeight(null);
        double scale = Math.min((double) box.width / imageWidth, (double) box.height / imageHeight);
        int drawWidth = (int) (imageWidth * scale);
        int drawHeight = (int) (imageHeight * scale);
        pen.drawImage(image, box.x + (box.width - drawWidth) / 2, box.y + (box.height - drawHeight) / 2,
                drawWidth, drawHeight, null);
    }

    public void filledRectangle(Rectangle area, Color color) {
        pen.setColor(color);
        pen.fill(area);
    }

    public void frame(int inset, float lineWidth, Color color) {
        pen.setColor(color);
        pen.setStroke(new BasicStroke(lineWidth));
        pen.drawRect(inset, inset, width() - 2 * inset, height() - 2 * inset);
    }

    public void horizontalLine(int y, int length, float lineWidth, Color color) {
        pen.setColor(color);
        pen.setStroke(new BasicStroke(lineWidth));
        int start = (width() - length) / 2;
        pen.drawLine(start, y, start + length, y);
    }

    /** The finished card as PNG file bytes. */
    public byte[] toPng() {
        pen.dispose();
        try (ByteArrayOutputStream file = new ByteArrayOutputStream()) {
            ImageIO.write(picture, "png", file);
            return file.toByteArray();
        } catch (IOException problem) {
            throw new UncheckedIOException(problem);
        }
    }

    // ---------- helpers ----------

    private Font shrinkToFit(String text, CardFont font, float fontSize, int maxWidth) {
        float size = fontSize;
        Font candidate = font.atSize(size);
        while (size > 8 && pen.getFontMetrics(candidate).stringWidth(text) > maxWidth) {
            size = size * 0.95f;
            candidate = font.atSize(size);
        }
        return candidate;
    }

    private static List<String> splitIntoLines(String text, FontMetrics measure, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder currentLine = new StringBuilder();
        for (String word : text.split("\\s+")) {
            String tryLine = currentLine.isEmpty() ? word : currentLine + " " + word;
            if (measure.stringWidth(tryLine) > maxWidth && !currentLine.isEmpty()) {
                lines.add(currentLine.toString());
                currentLine = new StringBuilder(word);
            } else {
                currentLine = new StringBuilder(tryLine);
            }
        }
        if (!currentLine.isEmpty()) {
            lines.add(currentLine.toString());
        }
        return lines;
    }

    /** Turns "#8B1E3F" into a colour. */
    public static Color colorFromHex(String hex, Color fallback) {
        if (hex == null || !hex.matches("^#[0-9A-Fa-f]{6}$")) {
            return fallback;
        }
        return Color.decode(hex);
    }
}
