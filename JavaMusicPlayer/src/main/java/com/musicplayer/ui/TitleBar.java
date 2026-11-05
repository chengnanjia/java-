package com.musicplayer.ui;

import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.function.Supplier;

public final class TitleBar extends JPanel {
    private final JFrame frame;
    private final IconButton maximizeButton;
    private Runnable onToggleSidebar = () -> { };
    private Point dragOffset;
    private Color accent = ThemeColorExtractor.DEFAULT;

    public TitleBar(JFrame frame) {
        this.frame = frame;
        setLayout(new BorderLayout());
        setOpaque(false);
        setBorder(new EmptyBorder(5, 12, 4, 9));
        setPreferredSize(new Dimension(0, 42));

        IconButton sidebarButton = new IconButton(IconPainter.IconType.SIDEBAR,
                30, false, () -> accent, "显示或隐藏曲库");
        sidebarButton.addActionListener(() -> onToggleSidebar.run());
        JLabel brand = new JLabel("  JAVA MUSIC ROOM");
        brand.setForeground(new Color(175, 184, 197));
        brand.setFont(brand.getFont().deriveFont(java.awt.Font.BOLD, 10.5f));
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 0));
        left.setOpaque(false);
        left.add(sidebarButton);
        left.add(brand);

        JLabel title = new JLabel("Java 音乐播放器", JLabel.CENTER);
        title.setForeground(new Color(198, 204, 213));
        title.setFont(title.getFont().deriveFont(java.awt.Font.PLAIN, 12f));

        JPanel windowButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        windowButtons.setOpaque(false);
        IconButton minimize = new IconButton(IconPainter.IconType.MINIMIZE,
                29, false, () -> new Color(200, 207, 217), "最小化");
        maximizeButton = new IconButton(IconPainter.IconType.MAXIMIZE,
                29, false, () -> new Color(200, 207, 217), "最大化");
        IconButton close = new IconButton(IconPainter.IconType.CLOSE,
                29, false, () -> new Color(255, 112, 121), "关闭");
        minimize.addActionListener(() -> frame.setExtendedState(
            frame.getExtendedState() | JFrame.ICONIFIED));
        maximizeButton.addActionListener(this::toggleMaximize);
        close.addActionListener(frame::dispose);
        windowButtons.add(minimize);
        windowButtons.add(maximizeButton);
        windowButtons.add(close);

        add(left, BorderLayout.WEST);
        add(title, BorderLayout.CENTER);
        add(windowButtons, BorderLayout.EAST);
        MouseAdapter dragger = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) {
                if (event.getClickCount() == 1) dragOffset = event.getLocationOnScreen();
            }
            @Override public void mouseDragged(MouseEvent event) {
                if (dragOffset == null || isMaximized()) return;
                Point point = event.getLocationOnScreen();
                frame.setLocation(frame.getX() + point.x - dragOffset.x,
                        frame.getY() + point.y - dragOffset.y);
                dragOffset = point;
            }
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) toggleMaximize();
            }
        };
        addMouseListener(dragger);
        addMouseMotionListener(dragger);
        title.addMouseListener(dragger);
        title.addMouseMotionListener(dragger);
        brand.addMouseListener(dragger);
        brand.addMouseMotionListener(dragger);
    }

    public void setOnToggleSidebar(Runnable action) {
        onToggleSidebar = action == null ? () -> { } : action;
    }

    public void setAccent(Color accent) {
        this.accent = accent;
        repaint();
    }

    private void toggleMaximize() {
        if (isMaximized()) {
            frame.setExtendedState(JFrame.NORMAL);
            maximizeButton.setIconType(IconPainter.IconType.MAXIMIZE);
        } else {
            frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
            maximizeButton.setIconType(IconPainter.IconType.RESTORE);
        }
    }

    private boolean isMaximized() {
        return (frame.getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
    }
}