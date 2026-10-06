package com.kayogx.eventcard.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.function.Supplier;

/**
 * Keeps finished card images, so each card is drawn only once.
 *
 * Each image is saved under a "fingerprint" of everything printed on it
 * (guest name, design version, card type, event details...). When anything changes,
 * the fingerprint changes too, so a fresh card is drawn automatically.
 *
 * The folder is PRIVATE (not downloadable from /uploads), because cards contain guest names.
 */
@Component
public class CardImageCache {

    private final Path folder;

    public CardImageCache(@Value("${app.card-cache-folder}") String folder) {
        this.folder = Path.of(folder).toAbsolutePath().normalize();
    }

    /** Returns the saved image for this fingerprint, or draws it (and saves it) if there is none yet. */
    public byte[] getOrDraw(String fingerprint, Supplier<byte[]> drawCard) {
        Path file = folder.resolve(sha256(fingerprint) + ".png");
        try {
            if (Files.exists(file)) {
                return Files.readAllBytes(file);
            }
            byte[] card = drawCard.get();
            Files.createDirectories(folder);
            Files.write(file, card);
            return card;
        } catch (IOException problem) {
            throw new UncheckedIOException("Could not read or save a card image", problem);
        }
    }

    private static String sha256(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
