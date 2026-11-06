package com.musicplayer.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;

public final class IconPainter {
    public enum IconType {
        PLAY, PAUSE, STOP, PREVIOUS, NEXT, VOLUME, MUTED,
        REPEAT, REPEAT_ONE, SHUFFLE, ADD, CLOSE, MINIMIZE, MAXIMIZE,
        RESTORE, SEARCH, SIDEBAR, CLEAR
    }

    private IconPainter() {
    }

    public static void paint(Graphics2D original, IconType type, int x, int y,
                             int size, Color color) {
        Graphics2D g = (Graphics2D) original.create();
        g.translate(x, y);
        g.scale(size / 24.0, size / 24.0);
        g.setColor(color);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Path2D path = new Path2D.Double();
        switch (type) {
            case PLAY -> {
                path.moveTo(7, 4.5); path.lineTo(20, 12); path.lineTo(7, 19.5); path.closePath();
                g.fill(path);
            }
            case PAUSE -> {
                g.fill(new Rectangle2D.Double(6, 4.5, 4.5, 15));
                g.fill(new Rectangle2D.Double(13.5, 4.5, 4.5, 15));
            }
            case STOP -> g.fill(new Rectangle2D.Double(6.5, 6.5, 11, 11));
            case PREVIOUS -> {
                g.fill(new Rectangle2D.Double(5, 5, 2.5, 14));
                path.moveTo(19, 5); path.lineTo(8, 12); path.lineTo(19, 19); path.closePath(); g.fill(path);
            }
            case NEXT -> {
                g.fill(new Rectangle2D.Double(16.5, 5, 2.5, 14));
                path.moveTo(5, 5); path.lineTo(16, 12); path.lineTo(5, 19); path.closePath(); g.fill(path);
            }
            case VOLUME, MUTED -> {
                path.moveTo(3.5, 9); path.lineTo(7.5, 9); path.lineTo(13, 5); path.lineTo(13, 19);
                path.lineTo(7.5, 15); path.lineTo(3.5, 15); path.closePath(); g.fill(path);
                if (type == IconType.MUTED) {
                    g.drawLine(16, 9, 21, 15); g.drawLine(21, 9, 16, 15);
                } else {
                    path.reset(); path.append(new java.awt.geom.Arc2D.Double(12, 6.5, 8, 11, -60, 120,
                            java.awt.geom.Arc2D.OPEN), false); g.draw(path);
                    path.reset(); path.append(new java.awt.geom.Arc2D.Double(11, 3, 13, 18, -58, 116,
                            java.awt.geom.Arc2D.OPEN), false); g.draw(path);
                }
            }
            case REPEAT, REPEAT_ONE -> {
                path.moveTo(7, 7); path.lineTo(17, 7); path.lineTo(20, 10); g.draw(path);
                path.reset(); path.moveTo(17, 4); path.lineTo(20, 7); path.lineTo(17, 10); g.fill(path);
                path.reset(); path.moveTo(17, 17); path.lineTo(7, 17); path.lineTo(4, 14); g.draw(path);
                path.reset(); path.moveTo(7, 20); path.lineTo(4, 17); path.lineTo(7, 14); g.fill(path);
                if (type == IconType.REPEAT_ONE) {
                    g.setFont(g.getFont().deriveFont(java.awt.Font.BOLD, 8f));
                    g.drawString("1", 10, 14);
                }
            }
            case SHUFFLE -> {
                path.moveTo(4, 7); path.lineTo(7, 7); path.lineTo(17, 17); path.lineTo(20, 17); g.draw(path);
                path.reset(); path.moveTo(17, 14); path.lineTo(20, 17); path.lineTo(17, 20); g.draw(path);
                path.reset(); path.moveTo(4, 17); path.lineTo(7, 17); path.lineTo(17, 7); path.lineTo(20, 7); g.draw(path);
                path.reset(); path.moveTo(17, 4); path.lineTo(20, 7); path.lineTo(17, 10); g.draw(path);
            }
            case ADD -> { g.drawLine(12, 5, 12, 19); g.drawLine(5, 12, 19, 12); }
            case CLOSE -> { g.drawLine(6, 6, 18, 18); g.drawLine(18, 6, 6, 18); }
            case MINIMIZE -> g.drawLine(5, 12, 19, 12);
            case MAXIMIZE -> g.draw(new Rectangle2D.Double(5.5, 5.5, 13, 13));
            case RESTORE -> {
                g.draw(new Rectangle2D.Double(8, 5, 11, 11));
                g.draw(new Rectangle2D.Double(5, 8, 11, 11));
            }
            case SEARCH -> {
                g.draw(new Ellipse2D.Double(4.5, 4.5, 11, 11));
                g.drawLine(13, 13, 20, 20);
            }
            case SIDEBAR -> {
                g.draw(new RoundRectangle2D.Double(3.5, 4.5, 17, 15, 4, 4));
                g.drawLine(9, 5, 9, 19);
            }
            case CLEAR -> { g.drawLine(7, 7, 17, 17); g.drawLine(17, 7, 7, 17); }
        }
        g.dispose();
    }

    public static Color blend(Color a, Color b, float amount) {
        float t = Math.max(0, Math.min(1, amount));
        return new Color(Math.round(a.getRed() + (b.getRed() - a.getRed()) * t),
                Math.round(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                Math.round(a.getBlue() + (b.getBlue() - a.getBlue()) * t),
                Math.round(a.getAlpha() + (b.getAlpha() - a.getAlpha()) * t));
    }
}