package com.musicplayer.ui;

import com.musicplayer.model.Music;

import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.function.Consumer;
import javax.imageio.ImageIO;
import javax.swing.Timer;

public final class NowPlayingPanel extends JPanel {
    private final JLabel title = new JLabel("选择一首歌曲");
    private final JLabel artist = new JLabel("沉浸聆听，从曲库开始");
    private final JLabel album = new JLabel("NOW PLAYING");
    private final AlbumCover cover = new AlbumCover();
    private final LyricsPanel lyrics = new LyricsPanel();
    private final ColorCanvas background = new ColorCanvas();
    private Consumer<Color> onAccentChanged = color -> { };
    private Music currentMusic;
    private Color accent = ThemeColorExtractor.DEFAULT;
    private Color transitionFrom = accent;
    private Color transitionTo = accent;
    private long transitionStart;
    private boolean playing;
    private float phase;
    private float breathing;

    public NowPlayingPanel() {
        setLayout(new BorderLayout());
        setOpaque(false);
        background.setLayout(new BorderLayout(24, 0));
        background.setBorder(new EmptyBorder(10, 30, 8, 30));
        buildContent();
        add(background, BorderLayout.CENTER);
        new Timer(50, event -> animate()).start();
    }

    private void buildContent() {
        JPanel details = new JPanel(new GridBagLayout());
        details.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.anchor = GridBagConstraints.CENTER;
        constraints.insets = new Insets(5, 10, 5, 14);
        constraints.weightx = 0;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.NONE;
        details.add(cover, constraints);

        JPanel lyricSide = new JPanel(new BorderLayout(0, 10));
        lyricSide.setOpaque(false);
        JPanel metadata = new JPanel();
        metadata.setOpaque(false);
        metadata.setLayout(new javax.swing.BoxLayout(metadata, javax.swing.BoxLayout.Y_AXIS));
        album.setForeground(new Color(235, 239, 244, 190));
        album.setFont(album.getFont().deriveFont(Font.BOLD, 10f));
        title.setForeground(Color.WHITE);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 25f));
        artist.setForeground(new Color(221, 226, 234, 185));
        artist.setFont(artist.getFont().deriveFont(Font.PLAIN, 14f));
        album.setAlignmentX(LEFT_ALIGNMENT);
        title.setAlignmentX(LEFT_ALIGNMENT);
        artist.setAlignmentX(LEFT_ALIGNMENT);
        metadata.add(album);
        metadata.add(javax.swing.Box.createVerticalStrut(12));
        metadata.add(title);
        metadata.add(javax.swing.Box.createVerticalStrut(6));
        metadata.add(artist);
        lyricSide.add(metadata, BorderLayout.NORTH);
        lyricSide.add(lyrics, BorderLayout.CENTER);
        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.insets = new Insets(5, 6, 5, 0);
        details.add(lyricSide, constraints);
        background.add(details, BorderLayout.CENTER);
    }

    public void setCurrentMusic(Music music) {
        currentMusic = music;
        if (music == null) {
            title.setText("选择一首歌曲");
            artist.setText("沉浸聆听，从曲库开始");
            album.setText("NOW PLAYING");
            cover.setArtwork(null, "Music");
            setAccent(ThemeColorExtractor.DEFAULT);
            return;
        }
        title.setText(music.getName());
        artist.setText(music.getArtist());
        album.setText(music.getAlbum().isEmpty() ? "NOW PLAYING" : music.getAlbum().toUpperCase());
        cover.setArtwork(music.getArtwork(), music.getName());
        setAccent(ThemeColorExtractor.extract(music.getArtwork()));
    }

    public void setLyrics(java.util.List<LrcParser.LyricLine> lyricLines) {
        lyrics.setLyrics(lyricLines);
    }

    public void setSeekListener(java.util.function.LongConsumer listener) {
        lyrics.setSeekListener(listener);
    }

    public void updatePosition(long positionMillis) {
        lyrics.updatePosition(positionMillis);
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public void setAccentListener(Consumer<Color> listener) {
        onAccentChanged = listener == null ? color -> { } : listener;
    }

    public Color getAccent() {
        return accent;
    }

    private void setAccent(Color next) {
        transitionFrom = accent;
        transitionTo = next;
        transitionStart = System.nanoTime();
    }

    private void animate() {
        phase = (phase + 0.0014f) % 1f;
        float targetBreathing = playing ? 1f : 0f;
        breathing += (targetBreathing - breathing) * 0.035f;
        double progress = Math.min(1, (System.nanoTime() - transitionStart) / 500_000_000.0);
        float eased = (float) (1 - Math.pow(1 - progress, 3));
        Color nextAccent = IconPainter.blend(transitionFrom, transitionTo, eased);
        if (!nextAccent.equals(accent)) {
            accent = nextAccent;
            lyrics.setAccent(accent);
            onAccentChanged.accept(accent);
        }
        background.repaint();
        if (playing) cover.repaint();
    }

    private final class ColorCanvas extends JPanel {
        private ColorCanvas() {
            setOpaque(false);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(new Color(18, 21, 26));
            g.fillRect(0, 0, getWidth(), getHeight());
            int width = getWidth();
            int height = getHeight();
            float drift = (float) Math.sin(phase * Math.PI * 2);
            paintGlow(g, width * (0.25f + drift * 0.07f), height * 0.28f,
                    Math.max(width, height) * 0.55f, accent, 0.55f);
            paintGlow(g, width * (0.80f - drift * 0.05f), height * 0.72f,
                    Math.max(width, height) * 0.62f, transitionTo, 0.34f);
            paintGlow(g, width * 0.52f, height * (0.50f + drift * 0.04f),
                    Math.max(width, height) * 0.42f, new Color(93, 79, 156), 0.16f);
            g.setColor(new Color(10, 13, 17, 179));
            g.fillRect(0, 0, width, height);
            g.dispose();
        }

        private void paintGlow(Graphics2D g, float x, float y, float radius,
                               Color color, float opacity) {
            float[] fractions = {0f, 0.45f, 1f};
            Color[] colors = {
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(opacity * 150)),
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.round(opacity * 54)),
                    new Color(color.getRed(), color.getGreen(), color.getBlue(), 0)
            };
            g.setPaint(new RadialGradientPaint(x, y, radius, fractions, colors));
            g.fill(new Ellipse2D.Float(x - radius, y - radius, radius * 2, radius * 2));
        }
    }

    private final class AlbumCover extends JPanel {
        private BufferedImage image;
        private String placeholder = "M";

        private AlbumCover() {
            setOpaque(false);
            setPreferredSize(new Dimension(292, 292));
            setMinimumSize(new Dimension(205, 205));
        }

        private void setArtwork(byte[] bytes, String name) {
            image = null;
            placeholder = name == null || name.isEmpty() ? "M" : name.substring(0, 1);
            if (bytes != null) {
                try {
                    image = ImageIO.read(new ByteArrayInputStream(bytes));
                } catch (Exception ignored) {
                    image = null;
                }
            }
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            int size = Math.min(Math.min(getWidth(), getHeight()) - 18, 280);
            float scale = 0.96f + breathing * 0.04f + (float) Math.sin(phase * 2 * Math.PI) * breathing * 0.015f;
            int scaled = Math.round(size * scale);
            int x = (getWidth() - scaled) / 2;
            int y = (getHeight() - scaled) / 2;
            g.setColor(new Color(0, 0, 0, 105));
            g.fill(new RoundRectangle2D.Float(x + 3, y + 9, scaled, scaled, 24, 24));
            g.clip(new RoundRectangle2D.Float(x, y, scaled, scaled, 22, 22));
            if (image != null) {
                g.drawImage(image.getScaledInstance(scaled, scaled, Image.SCALE_SMOOTH), x, y, null);
            } else {
                Color first = accent.brighter();
                Color second = accent.darker().darker();
                g.setPaint(new java.awt.GradientPaint(x, y, first, x + scaled, y + scaled, second));
                g.fillRect(x, y, scaled, scaled);
                g.setColor(new Color(255, 255, 255, 225));
                g.setFont(getFont().deriveFont(Font.BOLD, 82f));
                java.awt.FontMetrics metrics = g.getFontMetrics();
                g.drawString(placeholder, x + (scaled - metrics.stringWidth(placeholder)) / 2,
                        y + (scaled + metrics.getAscent() - metrics.getDescent()) / 2);
            }
            g.dispose();
        }
    }
}