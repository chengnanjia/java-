package com.musicplayer.ui;

import com.musicplayer.model.Music;

import javax.imageio.ImageIO;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

public final class PlaylistCellRenderer extends javax.swing.JPanel implements ListCellRenderer<Music> {
    private static final Color SURFACE = new Color(31, 35, 43);
    private static final Color TEXT = new Color(244, 245, 248);
    private static final Color MUTED = new Color(160, 168, 180);
    private final Map<String, Float> selectedBlend = new HashMap<>();
    private final Map<String, Float> hoverBlend = new HashMap<>();
    private Music music;
    private String number;
    private boolean playing;
    private float selectedAmount;
    private float hoveredAmount;
    private int equalizerFrame;
    private Color accent = ThemeColorExtractor.DEFAULT;

    public PlaylistCellRenderer() {
        setOpaque(false);
    }

    public void setAccent(Color accent) {
        this.accent = accent;
    }

    public void setEqualizerFrame(int frame) {
        equalizerFrame = frame;
    }

    public void setTargets(String selectedPath, String hoveredPath) {
        this.selectedPath = selectedPath;
        this.hoveredPath = hoveredPath;
        if (selectedPath != null) selectedBlend.putIfAbsent(selectedPath, 0f);
        if (hoveredPath != null) hoverBlend.putIfAbsent(hoveredPath, 0f);
    }

    public void animate() {
        animateMap(selectedBlend, selectedPath);
        animateMap(hoverBlend, hoveredPath);
    }

    private void animateMap(Map<String, Float> values, String targetPath) {
        for (Map.Entry<String, Float> entry : values.entrySet()) {
            float target = entry.getKey().equals(targetPath) ? 1f : 0f;
            entry.setValue(entry.getValue() + (target - entry.getValue()) * 0.22f);
        }
    }

    private String selectedPath;
    private String hoveredPath;

    public void setActivePaths(String selectedPath, String hoveredPath) {
        setTargets(selectedPath, hoveredPath);
    }

    @Override
    public Component getListCellRendererComponent(JList<? extends Music> list, Music value,
            int index, boolean isSelected, boolean cellHasFocus) {
        music = value;
        number = String.format("%02d", index + 1);
        selectedBlend.putIfAbsent(value.getPath(), 0f);
        hoverBlend.putIfAbsent(value.getPath(), 0f);
        selectedAmount = selectedBlend.getOrDefault(value.getPath(), isSelected ? 1f : 0f);
        hoveredAmount = hoverBlend.getOrDefault(value.getPath(), 0f);
        playing = value.getPath().equals(playingPath);
        setFont(list.getFont());
        return this;
    }

    private String playingPath;

    public void setPlayingPath(String path) {
        playingPath = path;
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        if (music == null) return;
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        int inset = 3;
        Color background = IconPainter.blend(new Color(38, 43, 52), accent, selectedAmount * 0.18f);
        background = IconPainter.blend(background, new Color(255, 255, 255, 22), hoveredAmount * 0.38f);
        g.setColor(background);
        g.fill(new RoundRectangle2D.Float(inset, 2, getWidth() - inset * 2,
                getHeight() - 4, 14, 14));

        int coverSize = 48;
        int coverX = 13;
        int coverY = (getHeight() - coverSize) / 2;
        paintCover(g, coverX, coverY, coverSize);

        int textX = coverX + coverSize + 13;
        int textWidth = Math.max(50, getWidth() - textX - 45);
        g.setFont(getFont().deriveFont(Font.BOLD, 13f));
        g.setColor(TEXT);
        drawEllipsized(g, music.getName(), textX, coverY + 19, textWidth);
        g.setFont(getFont().deriveFont(Font.PLAIN, 11f));
        g.setColor(MUTED);
        drawEllipsized(g, music.getArtist(), textX, coverY + 38, textWidth);

        int indicatorX = getWidth() - 28;
        if (playing) {
            g.setColor(accent);
            int[] heights = {8 + equalizerFrame % 4, 14 - equalizerFrame % 5, 9 + (equalizerFrame + 2) % 4};
            for (int bar = 0; bar < heights.length; bar++) {
                g.fillRoundRect(indicatorX + bar * 4, coverY + (coverSize - heights[bar]) / 2,
                        2, heights[bar], 2, 2);
            }
        } else {
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 150));
            g.setFont(getFont().deriveFont(Font.PLAIN, 9f));
            g.drawString(number, indicatorX - 7, coverY + 28);
        }
        g.dispose();
    }

    private void paintCover(Graphics2D g, int x, int y, int size) {
        byte[] art = music.getArtwork();
        if (art != null) {
            try {
                BufferedImage image = ImageIO.read(new ByteArrayInputStream(art));
                if (image != null) {
                    Graphics2D cover = (Graphics2D) g.create();
                    cover.setClip(new RoundRectangle2D.Float(x, y, size, size, 10, 10));
                    cover.drawImage(image, x, y, size, size, null);
                    cover.dispose();
                    return;
                }
            } catch (Exception ignored) {
                // 使用占位封面继续展示曲目。
            }
        }
        int hash = music.getName().hashCode();
        Color first = Color.getHSBColor((hash & 0xffff) / 65535f, 0.57f, 0.64f);
        Color second = Color.getHSBColor(((hash >>> 8) & 0xffff) / 65535f, 0.68f, 0.40f);
        g.setPaint(new java.awt.GradientPaint(x, y, first, x + size, y + size, second));
        g.fill(new RoundRectangle2D.Float(x, y, size, size, 10, 10));
        g.setColor(new Color(255, 255, 255, 225));
        g.setFont(getFont().deriveFont(Font.BOLD, 20f));
        String initial = music.getName().isEmpty() ? "?" : music.getName().substring(0, 1);
        java.awt.FontMetrics metrics = g.getFontMetrics();
        g.drawString(initial, x + (size - metrics.stringWidth(initial)) / 2,
                y + (size + metrics.getAscent() - metrics.getDescent()) / 2);
    }

    private void drawEllipsized(Graphics2D g, String text, int x, int y, int width) {
        String value = text == null ? "" : text;
        java.awt.FontMetrics metrics = g.getFontMetrics();
        while (!value.isEmpty() && metrics.stringWidth(value) > width) {
            value = value.substring(0, value.length() - 1);
        }
        if (!value.equals(text) && value.length() > 1) value = value.substring(0, value.length() - 1) + "…";
        g.drawString(value, x, y);
    }
}