package com.kayogx.eventcard.card;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.awt.image.BufferedImage;
import java.util.Map;

/** Makes QR code pictures (black squares on white) using the ZXing library. */
public final class QrCodes {

    private static final int BLACK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;

    private QrCodes() {
    }

    /** A square QR code picture of the given size in pixels, containing the given text (e.g. a link). */
    public static BufferedImage draw(String text, int sizeInPixels) {
        Map<EncodeHintType, Object> settings = Map.of(
                EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, // still readable if slightly damaged
                EncodeHintType.MARGIN, 1);                                // a thin white border
        try {
            BitMatrix squares = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizeInPixels, sizeInPixels, settings);
            BufferedImage picture = new BufferedImage(squares.getWidth(), squares.getHeight(), BufferedImage.TYPE_INT_RGB);
            for (int x = 0; x < squares.getWidth(); x++) {
                for (int y = 0; y < squares.getHeight(); y++) {
                    picture.setRGB(x, y, squares.get(x, y) ? BLACK : WHITE);
                }
            }
            return picture;
        } catch (WriterException problem) {
            throw new IllegalStateException("Could not make a QR code", problem);
        }
    }
}
