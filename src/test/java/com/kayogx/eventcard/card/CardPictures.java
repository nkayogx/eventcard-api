package com.kayogx.eventcard.card;

import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;

/** Small picture helpers for tests: make artwork, read cards, read QR codes. */
public final class CardPictures {

    private CardPictures() {
    }

    /** A plain PNG picture of one colour - stands in for a vendor's artwork. */
    public static byte[] plainPng(int width, int height, Color color) throws Exception {
        BufferedImage picture = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D pen = picture.createGraphics();
        pen.setColor(color);
        pen.fillRect(0, 0, width, height);
        pen.dispose();
        ByteArrayOutputStream file = new ByteArrayOutputStream();
        ImageIO.write(picture, "png", file);
        return file.toByteArray();
    }

    public static BufferedImage read(byte[] png) throws Exception {
        return ImageIO.read(new ByteArrayInputStream(png));
    }

    /** Finds the QR code on a card and returns what it says (the guest's link). */
    public static String readQrCode(byte[] png) throws Exception {
        var source = new BufferedImageLuminanceSource(read(png));
        var bitmap = new BinaryBitmap(new HybridBinarizer(source));
        var hints = Map.of(
                DecodeHintType.TRY_HARDER, Boolean.TRUE,
                DecodeHintType.POSSIBLE_FORMATS, List.of(BarcodeFormat.QR_CODE));
        return new MultiFormatReader().decode(bitmap, hints).getText();
    }
}
