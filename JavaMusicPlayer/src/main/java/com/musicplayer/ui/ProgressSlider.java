package com.musicplayer.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.LongConsumer;
import java.util.function.Supplier;

public final class ProgressSlider extends JComponent {
    private final Supplier<Color> accentSupplier;
    private LongConsumer seekListener = value -> { };
    private long position;
    private long duration;
    private long hoverPosition;
    private boolean hovered;
    private boolean dragging;
    private float hoverAmount;
    private final Timer animationTimer;

    public ProgressSlider(Supplier<Color> accentSupplier) {
        this.accentSupplier = accentSupplier;
        setPreferredSize(new Dimension(220, 30));
        setMinimumSize(new Dimension(100, 24));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        animationTimer = new Timer(16, event -> {
            float target = hovered || dragging ? 1f : 0f;
            hoverAmount += (target - hoverAmount) * 0.25f;
            repaint();
            if (Math.abs(target - hoverAmount) < 0.01f) {
                hoverAmount = target;
                ((Timer) event.getSource()).stop();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { hovered = true; animationTimer.start(); }
            @Override public void mouseExited(MouseEvent event) { hovered = dragging; animationTimer.start(); }
            @Override public void mousePressed(MouseEvent event) { dragging = true; animationTimer.start(); updateFromMouse(event.getX()); }
            @Override public void mouseReleased(MouseEvent event) {
                if (dragging) seekListener.accept(position);
                dragging = false;
                animationTimer.start();
            }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) { hoverPosition = positionAt(event.getX()); }
            @Override public void mouseDragged(MouseEvent event) { updateFromMouse(event.getX()); }
        });
    }

    public void setSeekListener(LongConsumer listener) {
        seekListener = listener == null ? value -> { } : listener;
    }

    public void setDuration(long duration) {
        this.duration = Math.max(0, duration);
        repaint();
    }

    public void setPosition(long position) {
        if (!dragging) this.position = Math.max(0, Math.min(duration, position));
        repaint();
    }

    public long getPosition() {
        return position;
    }

    private void updateFromMouse(int x) {
        position = positionAt(x);
        hoverPosition = position;
        repaint();
    }

    private long positionAt(int x) {
        int left = 5;
        int usable = Math.max(1, getWidth() - 10);
        double fraction = Math.max(0, Math.min(1, (x - left) / (double) usable));
        return (long) (duration * fraction);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int left = 5;
        int right = getWidth() - 5;
        int y = getHeight() / 2 + 3;
        float ratio = duration <= 0 ? 0 : Math.min(1, position / (float) duration);
        int currentX = left + Math.round((right - left) * ratio);
        float lineWidth = 2f + hoverAmount * 1.7f;
        g.setStroke(new BasicStroke(lineWidth, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 65));
        g.drawLine(left, y, right, y);
        g.setColor(accentSupplier.get());
        g.drawLine(left, y, currentX, y);
        float knob = 5f + hoverAmount * 3f;
        g.fillOval(Math.round(currentX - knob), Math.round(y - knob), Math.round(knob * 2), Math.round(knob * 2));

        if (hovered && !dragging && duration > 0) {
            int tooltipX = left + Math.round((right - left) * (hoverPosition / (float) duration));
            String text = formatTime(hoverPosition);
            FontMetrics metrics = g.getFontMetrics();
            int width = metrics.stringWidth(text) + 14;
            int boxX = Math.max(2, Math.min(getWidth() - width - 2, tooltipX - width / 2));
            g.setColor(new Color(12, 15, 19, 230));
            g.fillRoundRect(boxX, 0, width, 19, 8, 8);
            g.setColor(Color.WHITE);
            g.drawString(text, boxX + 7, 14);
        }
        g.dispose();
    }

    public static String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}