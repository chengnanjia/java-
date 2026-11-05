package com.musicplayer.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.DoubleConsumer;
import java.util.function.Supplier;

public final class VolumeSlider extends JComponent {
    private final Supplier<Color> accentSupplier;
    private DoubleConsumer changeListener = value -> { };
    private double value = 0.72;
    private float hover;
    private boolean hovered;
    private final Timer animationTimer;

    public VolumeSlider(Supplier<Color> accentSupplier) {
        this.accentSupplier = accentSupplier;
        setPreferredSize(new Dimension(112, 26));
        setMinimumSize(new Dimension(75, 26));
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        animationTimer = new Timer(16, event -> {
            hover += ((hovered ? 1f : 0f) - hover) * 0.25f;
            repaint();
            if (Math.abs((hovered ? 1f : 0f) - hover) < 0.01f) {
                hover = hovered ? 1f : 0f;
                ((Timer) event.getSource()).stop();
            }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { hovered = true; animationTimer.start(); }
            @Override public void mouseExited(MouseEvent event) { hovered = false; animationTimer.start(); }
            @Override public void mousePressed(MouseEvent event) { setFromX(event.getX()); }
        });
        addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseDragged(MouseEvent event) { setFromX(event.getX()); }
        });
    }

    public void setChangeListener(DoubleConsumer listener) {
        changeListener = listener == null ? amount -> { } : listener;
    }

    public double getValue() {
        return value;
    }

    public void setValue(double value) {
        this.value = Math.max(0, Math.min(1, value));
        repaint();
    }

    private void setFromX(int x) {
        value = Math.max(0, Math.min(1, (x - 5) / (double) Math.max(1, getWidth() - 10)));
        changeListener.accept(value);
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int left = 5;
        int right = getWidth() - 5;
        int y = getHeight() / 2;
        g.setStroke(new BasicStroke(2f + hover, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(255, 255, 255, 65));
        g.drawLine(left, y, right, y);
        int knobX = left + (int) Math.round((right - left) * value);
        g.setColor(accentSupplier.get());
        g.drawLine(left, y, knobX, y);
        int radius = 4 + Math.round(hover * 1.5f);
        g.fillOval(knobX - radius, y - radius, radius * 2, radius * 2);
        g.dispose();
    }
}