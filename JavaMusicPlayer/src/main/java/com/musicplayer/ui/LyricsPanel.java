package com.musicplayer.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.LongConsumer;

public final class LyricsPanel extends JComponent {
    private static final int LINE_HEIGHT = 53;
    private final Timer animationTimer;
    private List<LrcParser.LyricLine> lines = Collections.emptyList();
    private LongConsumer seekListener = value -> { };
    private Color accent = ThemeColorExtractor.DEFAULT;
    private double displayedIndex;
    private double animationStartIndex;
    private double targetIndex;
    private long animationStartNanos;
    private int currentIndex = -1;

    public LyricsPanel() {
        setOpaque(false);
        setCursor(Cursor.getDefaultCursor());
        addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (lines.isEmpty()) return;
                double center = getHeight() * 0.48;
                int index = (int) Math.round(displayedIndex + (event.getY() - center) / LINE_HEIGHT);
                if (index >= 0 && index < lines.size()) seekListener.accept(lines.get(index).timeMillis());
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                double center = getHeight() * 0.48;
                int index = (int) Math.round(displayedIndex + (event.getY() - center) / LINE_HEIGHT);
                setCursor(!lines.isEmpty() && index >= 0 && index < lines.size()
                        ? Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
                        : Cursor.getDefaultCursor());
            }
        });
        animationTimer = new Timer(16, event -> animateScroll());
        animationTimer.start();
    }

    public void setLyrics(List<LrcParser.LyricLine> lyrics) {
        lines = lyrics == null ? Collections.emptyList() : new ArrayList<>(lyrics);
        currentIndex = lines.isEmpty() ? -1 : 0;
        displayedIndex = 0;
        targetIndex = 0;
        animationStartIndex = 0;
        repaint();
    }

    public void setSeekListener(LongConsumer listener) {
        seekListener = listener == null ? value -> { } : listener;
    }

    public void setAccent(Color accent) {
        this.accent = accent;
        repaint();
    }

    public void updatePosition(long positionMillis) {
        if (lines.isEmpty()) return;
        int nextIndex = 0;
        int low = 0;
        int high = lines.size() - 1;
        while (low <= high) {
            int middle = (low + high) >>> 1;
            if (lines.get(middle).timeMillis() <= positionMillis) {
                nextIndex = middle;
                low = middle + 1;
            } else {
                high = middle - 1;
            }
        }
        if (nextIndex != currentIndex) {
            currentIndex = nextIndex;
            animationStartIndex = displayedIndex;
            targetIndex = nextIndex;
            animationStartNanos = System.nanoTime();
        }
        repaint();
    }

    private void animateScroll() {
        if (Math.abs(targetIndex - displayedIndex) < 0.002) return;
        double progress = Math.min(1, (System.nanoTime() - animationStartNanos) / 420_000_000.0);
        double inverse = 1 - progress;
        double eased = 1 - inverse * inverse * inverse;
        displayedIndex = animationStartIndex + (targetIndex - animationStartIndex) * eased;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        double centerY = getHeight() * 0.48;
        if (lines.isEmpty()) {
            g.setFont(getFont().deriveFont(Font.PLAIN, 22f));
            g.setColor(new Color(235, 238, 242, 205));
            drawCentered(g, "纯音乐，请欣赏", getWidth() / 2, (int) centerY);
            g.dispose();
            return;
        }

        int first = Math.max(0, (int) Math.floor(displayedIndex) - 5);
        int last = Math.min(lines.size() - 1, (int) Math.ceil(displayedIndex) + 5);
        for (int index = first; index <= last; index++) {
            double distance = Math.abs(index - displayedIndex);
            float alpha = (float) Math.max(0.10, 1.0 - distance * 0.22);
            float fontSize = (float) Math.max(13, 23 - distance * 1.6);
            boolean active = Math.abs(index - displayedIndex) < 0.48;
            g.setFont(getFont().deriveFont(active ? Font.BOLD : Font.PLAIN,
                    active ? 23f : fontSize));
            Color base = active ? new Color(255, 255, 255) : new Color(224, 229, 236);
            int a = Math.round(alpha * (active ? 255 : 195));
            g.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), a));
            String text = lines.get(index).text();
            int y = (int) Math.round(centerY + (index - displayedIndex) * LINE_HEIGHT);
            drawFitted(g, text, getWidth() - 32, y);
            if (active) {
                g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 210));
                g.fillRoundRect(5, y - 5, 3, 10, 3, 3);
            }
        }
        g.dispose();
    }

    private void drawFitted(Graphics2D g, String text, int maxWidth, int centerY) {
        FontMetrics metrics = g.getFontMetrics();
        String fitted = text == null ? "" : text;
        while (!fitted.isEmpty() && metrics.stringWidth(fitted) > maxWidth) {
            fitted = fitted.substring(0, fitted.length() - 1);
        }
        if (!fitted.equals(text) && fitted.length() > 1) fitted = fitted.substring(0, fitted.length() - 1) + "…";
        drawCentered(g, fitted, getWidth() / 2, centerY + (metrics.getAscent() - metrics.getDescent()) / 2);
    }

    private void drawCentered(Graphics2D g, String text, int centerX, int baseline) {
        FontMetrics metrics = g.getFontMetrics();
        g.drawString(text, centerX - metrics.stringWidth(text) / 2, baseline);
    }
}