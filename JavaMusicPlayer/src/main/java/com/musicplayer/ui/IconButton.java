package com.musicplayer.ui;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Ellipse2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public final class IconButton extends JComponent {
    private IconPainter.IconType icon;
    private final int diameter;
    private final boolean primary;
    private final Supplier<Color> accentSupplier;
    private final List<Runnable> actions = new ArrayList<>();
    private float hover;
    private float press;
    private float targetHover;
    private float targetPress;
    private Timer animationTimer;

    public IconButton(IconPainter.IconType icon, int diameter, boolean primary,
                      Supplier<Color> accentSupplier, String accessibleName) {
        this.icon = icon;
        this.diameter = diameter;
        this.primary = primary;
        this.accentSupplier = accentSupplier;
        setPreferredSize(new Dimension(diameter, diameter));
        setMinimumSize(getPreferredSize());
        setMaximumSize(getPreferredSize());
        setToolTipText(accessibleName);
        setName(accessibleName);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        animationTimer = new Timer(16, event -> {
            hover += (targetHover - hover) * 0.24f;
            press += (targetPress - press) * 0.32f;
            if (Math.abs(targetHover - hover) < 0.01f) hover = targetHover;
            if (Math.abs(targetPress - press) < 0.01f) press = targetPress;
            repaint();
            if (hover == targetHover && press == targetPress) ((Timer) event.getSource()).stop();
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mouseEntered(MouseEvent event) { targetHover = 1; animationTimer.start(); }
            @Override public void mouseExited(MouseEvent event) { targetHover = 0; targetPress = 0; animationTimer.start(); }
            @Override public void mousePressed(MouseEvent event) { targetPress = 1; animationTimer.start(); }
            @Override public void mouseReleased(MouseEvent event) {
                targetPress = 0;
                animationTimer.start();
                if (contains(event.getPoint())) for (Runnable action : List.copyOf(actions)) action.run();
            }
        });
    }

    public void addActionListener(Runnable action) {
        actions.add(action);
    }

    public void setIconType(IconPainter.IconType icon) {
        this.icon = icon;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        Color accent = accentSupplier.get();
        int inset = 2;
        int size = Math.min(getWidth(), getHeight()) - inset * 2;
        float scale = 1f - press * 0.07f;
        int diameter = Math.round(size * scale);
        int x = (getWidth() - diameter) / 2;
        int y = (getHeight() - diameter) / 2;
        if (primary) {
            g.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(),
                    Math.round(255 * (0.90f + 0.1f * hover))));
            g.fill(new Ellipse2D.Float(x, y, diameter, diameter));
            g.setColor(new Color(10, 13, 17));
        } else {
            g.setColor(new Color(255, 255, 255, Math.round(22 + hover * 24)));
            g.fill(new Ellipse2D.Float(x, y, diameter, diameter));
            g.setColor(IconPainter.blend(new Color(220, 224, 232), accent, hover * 0.7f));
        }
        int glyph = Math.max(14, Math.round(diameter * (primary ? 0.36f : 0.48f)));
        IconPainter.paint(g, icon, getWidth() / 2 - glyph / 2,
                getHeight() / 2 - glyph / 2, glyph, g.getColor());
        g.dispose();
    }
}