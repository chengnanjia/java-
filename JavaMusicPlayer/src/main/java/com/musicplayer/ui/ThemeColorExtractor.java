package com.musicplayer.ui;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

public final class ThemeColorExtractor {
    public static final Color DEFAULT = new Color(82, 211, 170);
    private static final int HUE_BINS = 36;

    private ThemeColorExtractor() {
    }

    public static Color extract(byte[] artwork) {
        if (artwork == null || artwork.length == 0) return DEFAULT;
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(artwork));
            if (image == null) return DEFAULT;
            return extract(image);
        } catch (IOException ignored) {
            return DEFAULT;
        }
    }

    public static Color extract(BufferedImage source) {
        if (source == null || source.getWidth() == 0 || source.getHeight() == 0) return DEFAULT;
        int[] counts = new int[HUE_BINS];
        long[] red = new long[HUE_BINS];
        long[] green = new long[HUE_BINS];
        long[] blue = new long[HUE_BINS];

        int strideX = Math.max(1, source.getWidth() / 64);
        int strideY = Math.max(1, source.getHeight() / 64);
        for (int y = 0; y < source.getHeight(); y += strideY) {
            for (int x = 0; x < source.getWidth(); x += strideX) {
                Color pixel = new Color(source.getRGB(x, y), true);
                if (pixel.getAlpha() < 80) continue;
                float[] hsb = Color.RGBtoHSB(pixel.getRed(), pixel.getGreen(), pixel.getBlue(), null);
                if (hsb[1] < 0.18f || hsb[2] < 0.13f || hsb[2] > 0.94f) continue;
                int bin = Math.min(HUE_BINS - 1, (int) (hsb[0] * HUE_BINS));
                int weight = 1 + (int) (hsb[1] * 2.0f);
                counts[bin] += weight;
                red[bin] += (long) pixel.getRed() * weight;
                green[bin] += (long) pixel.getGreen() * weight;
                blue[bin] += (long) pixel.getBlue() * weight;
            }
        }

        int dominant = 0;
        for (int bin = 1; bin < HUE_BINS; bin++) {
            if (counts[bin] > counts[dominant]) dominant = bin;
        }
        if (counts[dominant] == 0) return DEFAULT;
        Color raw = new Color((int) (red[dominant] / counts[dominant]),
                (int) (green[dominant] / counts[dominant]),
                (int) (blue[dominant] / counts[dominant]));
        return raw.brighter().brighter();
    }
}