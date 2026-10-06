package com.kayogx.eventcard.model;

import java.awt.Font;
import java.awt.FontFormatException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Map;

/**
 * The fonts vendors can choose for text on their cards.
 * The font files live in src/main/resources/fonts (free to use - see the README there).
 */
public enum CardFont {
    PLAYFAIR("PlayfairDisplay-Regular.ttf"),
    PLAYFAIR_BOLD("PlayfairDisplay-Bold.ttf"),
    MONTSERRAT("Montserrat-Regular.ttf"),
    MONTSERRAT_BOLD("Montserrat-Bold.ttf"),
    GREAT_VIBES("GreatVibes-Regular.ttf");

    private final String fileName;

    // Each font file is read only once, then kept in memory
    private static final Map<CardFont, Font> loadedFonts = new EnumMap<>(CardFont.class);

    CardFont(String fileName) {
        this.fileName = fileName;
    }

    /** The font at the given size in pixels. */
    public Font atSize(float sizeInPixels) {
        return baseFont().deriveFont(sizeInPixels);
    }

    private synchronized Font baseFont() {
        return loadedFonts.computeIfAbsent(this, font -> {
            try (InputStream file = CardFont.class.getResourceAsStream("/fonts/" + font.fileName)) {
                if (file == null) {
                    throw new IllegalStateException("Font file is missing: " + font.fileName);
                }
                return Font.createFont(Font.TRUETYPE_FONT, file);
            } catch (IOException problem) {
                throw new UncheckedIOException(problem);
            } catch (FontFormatException problem) {
                throw new IllegalStateException("Font file is damaged: " + font.fileName, problem);
            }
        });
    }
}
