package com.musicplayer.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.musicplayer.model.Music;
import com.musicplayer.player.PlayerController;

import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class MainFrame extends JFrame {
    private static final Color WINDOW = new Color(17, 20, 25);
    private final PlaylistPanel playlist;
    private final NowPlayingPanel nowPlaying = new NowPlayingPanel();
    private final ControlBar controls;
    private final TitleBar titleBar;
    private final PlayerController player;
    private final JPanel sidebarHost;
    private final JPanel responsiveCenter;
    private Music currentTrack;
    private long durationMillis;
    private long currentPositionMillis;
    private boolean playing;
    private boolean userCollapsedSidebar;
    private boolean responsiveSidebarHidden;
    private boolean maximized;
    private final Random random = new Random();

    public MainFrame() {
        super("Java 音乐播放器");
        setUndecorated(true);
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(800, 520));
        setSize(1000, 640);
        setLocationRelativeTo(null);
        setBackground(WINDOW);

        playlist = new PlaylistPanel(nowPlaying::getAccent);
        controls = new ControlBar(nowPlaying::getAccent);
        titleBar = new TitleBar(this);
        sidebarHost = new SurfacePanel(new Color(23, 27, 33));
        sidebarHost.setLayout(new BorderLayout());
        sidebarHost.add(playlist, BorderLayout.CENTER);
        sidebarHost.setPreferredSize(new Dimension(310, 0));

        responsiveCenter = new JPanel(new BorderLayout());
        responsiveCenter.setOpaque(false);
        responsiveCenter.add(nowPlaying, BorderLayout.CENTER);
        responsiveCenter.add(controls, BorderLayout.SOUTH);

        player = new PlayerController(new PlayerController.Listener() {
            @Override public void onReady(Music music, long duration) {
                if (!sameTrack(music, currentTrack)) return;
                durationMillis = duration;
                controls.setDuration(duration);
                setPlaybackState(true);
            }
            @Override public void onPosition(Music music, long position) {
                if (!sameTrack(music, currentTrack)) return;
                currentPositionMillis = position;
                controls.setPosition(position);
                nowPlaying.updatePosition(position);
            }
            @Override public void onTrackEnded(Music music) {
                if (!sameTrack(music, currentTrack)) return;
                handleTrackEnded();
            }
            @Override public void onError(Music music, String message) {
                if (!sameTrack(music, currentTrack)) return;
                setPlaybackState(false);
                JOptionPane.showMessageDialog(MainFrame.this, message,
                        "播放失败", JOptionPane.ERROR_MESSAGE);
            }
        });
        buildWindow();
        bindActions();
        setTransferHandler(audioTransferHandler());
        nowPlaying.setAccentListener(this::applyAccent);
        playlist.setOnPlay(this::playTrack);
        playlist.loadInitialTracks(new File("resources/music"));
        addComponentListener(new java.awt.event.ComponentAdapter() {
            @Override public void componentResized(java.awt.event.ComponentEvent event) {
                updateResponsiveLayout();
            }
        });
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosed(java.awt.event.WindowEvent event) {
                player.shutdown();
            }
        });
        installResizeGrips();
        updateResponsiveLayout();
    }

    private void buildWindow() {
        SurfacePanel root = new SurfacePanel(WINDOW);
        root.setLayout(new BorderLayout());
        root.add(titleBar, BorderLayout.NORTH);
        root.add(sidebarHost, BorderLayout.WEST);
        root.add(responsiveCenter, BorderLayout.CENTER);
        root.setTransferHandler(audioTransferHandler());
        setContentPane(root);
        titleBar.setOnToggleSidebar(this::toggleSidebar);
    }

    private void bindActions() {
        controls.setOnPrevious(() -> changeTrack(-1));
        controls.setOnPlayPause(this::togglePlayback);
        controls.setOnStop(this::stopPlayback);
        controls.setOnNext(() -> changeTrack(1));
        controls.setOnSeek(this::seekTo);
        controls.setOnVolume(player::setVolume);
        controls.setOnModeChanged(mode -> { });
        nowPlaying.setSeekListener(this::seekTo);

        bindKey(KeyEvent.VK_SPACE, "play-pause", this::togglePlayback);
        bindKey(KeyEvent.VK_LEFT, "seek-back", () -> seekTo(Math.max(0, currentPositionMillis - 5000)));
        bindKey(KeyEvent.VK_RIGHT, "seek-forward", () -> seekTo(Math.min(durationMillis, currentPositionMillis + 5000)));
        bindKey(KeyEvent.VK_UP, "volume-up", () -> controls.adjustVolume(0.05));
        bindKey(KeyEvent.VK_DOWN, "volume-down", () -> controls.adjustVolume(-0.05));
    }

    private void bindKey(int keyCode, String name, Runnable action) {
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(javax.swing.KeyStroke.getKeyStroke(keyCode, 0), name);
        getRootPane().getActionMap().put(name, new javax.swing.AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                action.run();
            }
        });
    }

    private void playTrack(Music music) {
        currentTrack = music;
        currentPositionMillis = 0;
        durationMillis = 0;
        playlist.setPlayingTrack(music);
        nowPlaying.setCurrentMusic(music);
        nowPlaying.setLyrics(List.of());
        controls.setDuration(0);
        controls.setPosition(0);
        setPlaybackState(true);
        player.play(music);
        loadLyrics(music);
    }

    private void loadLyrics(Music music) {
        new javax.swing.SwingWorker<List<LrcParser.LyricLine>, Void>() {
            @Override protected List<LrcParser.LyricLine> doInBackground() throws Exception {
                Path audio = Path.of(music.getPath());
                String filename = audio.getFileName().toString();
                int dot = filename.lastIndexOf('.');
                String base = dot > 0 ? filename.substring(0, dot) : filename;
                Path sibling = audio.resolveSibling(base + ".lrc");
                Path libraryLyric = Path.of("resources", "lyrics", music.getName() + ".lrc");
                Path lyricPath = Files.isRegularFile(sibling) ? sibling : libraryLyric;
                if (!Files.isRegularFile(lyricPath)) return List.of();
                return LrcParser.parse(lyricPath);
            }
            @Override protected void done() {
                if (!sameTrack(currentTrack, music)) return;
                try {
                    nowPlaying.setLyrics(get());
                } catch (Exception ignored) {
                    nowPlaying.setLyrics(List.of());
                }
            }
        }.execute();
    }

    private void togglePlayback() {
        if (currentTrack == null) {
            playlist.playSelectedOrFirst();
            return;
        }
        player.togglePause();
        setPlaybackState(!playing);
    }

    private void stopPlayback() {
        player.stop();
        currentPositionMillis = 0;
        controls.setPosition(0);
        setPlaybackState(false);
    }

    private void seekTo(long positionMillis) {
        if (currentTrack == null) return;
        long bounded = Math.max(0, Math.min(durationMillis, positionMillis));
        currentPositionMillis = bounded;
        controls.setPosition(bounded);
        nowPlaying.updatePosition(bounded);
        player.seek(bounded);
    }

    private void changeTrack(int direction) {
        List<Music> tracks = playlist.getTracks();
        if (tracks.isEmpty()) return;
        if (currentTrack == null) {
            playTrack(tracks.get(0));
            return;
        }
        ControlBar.PlayMode mode = controls.getPlayMode();
        if (mode == ControlBar.PlayMode.SHUFFLE && direction > 0 && tracks.size() > 1) {
            Music next;
            do {
                next = tracks.get(random.nextInt(tracks.size()));
            } while (next.getPath().equals(currentTrack.getPath()));
            playTrack(next);
            return;
        }
        int index = trackIndex(tracks, currentTrack);
        int nextIndex = Math.floorMod(index + direction, tracks.size());
        playTrack(tracks.get(nextIndex));
    }

    private int trackIndex(List<Music> tracks, Music track) {
        for (int index = 0; index < tracks.size(); index++) {
            if (tracks.get(index).getPath().equals(track.getPath())) return index;
        }
        return 0;
    }

    private void handleTrackEnded() {
        if (controls.getPlayMode() == ControlBar.PlayMode.REPEAT_ONE && currentTrack != null) {
            playTrack(currentTrack);
        } else {
            List<Music> tracks = playlist.getTracks();
            int index = trackIndex(tracks, currentTrack);
            if (index + 1 < tracks.size() || controls.getPlayMode() == ControlBar.PlayMode.SHUFFLE) {
                changeTrack(1);
            } else {
                setPlaybackState(false);
            }
        }
    }

    private void setPlaybackState(boolean state) {
        playing = state;
        controls.setPlaying(state);
        nowPlaying.setPlaying(state);
    }

    private void applyAccent(Color accent) {
        playlist.setAccent(accent);
        titleBar.setAccent(accent);
        controls.setAccent(accent);
    }

    private void toggleSidebar() {
        userCollapsedSidebar = !userCollapsedSidebar;
        updateResponsiveLayout();
    }

    private void updateResponsiveLayout() {
        if (sidebarHost == null) return;
        boolean narrow = getWidth() < 900;
        responsiveSidebarHidden = narrow;
        boolean show = !narrow && !userCollapsedSidebar;
        sidebarHost.setVisible(show);
        revalidate();
    }

    private TransferHandler audioTransferHandler() {
        return new TransferHandler() {
            @Override public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }
            @Override @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    playlist.addFiles((List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor));
                    return true;
                } catch (Exception exception) {
                    return false;
                }
            }
        };
    }

    private void installResizeGrips() {
        JPanel root = (JPanel) getContentPane();
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(root, BorderLayout.CENTER);
        JPanel north = new JPanel(new BorderLayout());
        north.setOpaque(false);
        north.add(new ResizeGrip(Cursor.NW_RESIZE_CURSOR, Edge.NORTH_WEST), BorderLayout.WEST);
        north.add(new ResizeGrip(Cursor.N_RESIZE_CURSOR, Edge.NORTH), BorderLayout.CENTER);
        north.add(new ResizeGrip(Cursor.NE_RESIZE_CURSOR, Edge.NORTH_EAST), BorderLayout.EAST);
        JPanel south = new JPanel(new BorderLayout());
        south.setOpaque(false);
        south.add(new ResizeGrip(Cursor.SW_RESIZE_CURSOR, Edge.SOUTH_WEST), BorderLayout.WEST);
        south.add(new ResizeGrip(Cursor.S_RESIZE_CURSOR, Edge.SOUTH), BorderLayout.CENTER);
        south.add(new ResizeGrip(Cursor.SE_RESIZE_CURSOR, Edge.SOUTH_EAST), BorderLayout.EAST);
        north.setPreferredSize(new Dimension(0, 7));
        south.setPreferredSize(new Dimension(0, 7));
        wrapper.add(north, BorderLayout.NORTH);
        wrapper.add(south, BorderLayout.SOUTH);
        wrapper.add(new ResizeGrip(Cursor.W_RESIZE_CURSOR, Edge.WEST), BorderLayout.WEST);
        wrapper.add(new ResizeGrip(Cursor.E_RESIZE_CURSOR, Edge.EAST), BorderLayout.EAST);
        setContentPane(wrapper);
    }

    private boolean sameTrack(Music first, Music second) {
        return first != null && second != null && first.getPath().equals(second.getPath());
    }

    private enum Edge { NORTH, SOUTH, WEST, EAST, NORTH_WEST, NORTH_EAST, SOUTH_WEST, SOUTH_EAST }

    private final class ResizeGrip extends JPanel {
        private final Edge edge;
        private Point origin;
        private java.awt.Rectangle initial;

        private ResizeGrip(int cursor, Edge edge) {
            this.edge = edge;
            setOpaque(false);
            setCursor(Cursor.getPredefinedCursor(cursor));
            setPreferredSize(new Dimension(7, 7));
            MouseAdapter resize = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    origin = event.getLocationOnScreen();
                    initial = getBounds();
                    initial = MainFrame.this.getBounds();
                }
                @Override public void mouseDragged(MouseEvent event) {
                    if (origin == null || initial == null || isMaximized()) return;
                    Point current = event.getLocationOnScreen();
                    int dx = current.x - origin.x;
                    int dy = current.y - origin.y;
                    int x = initial.x, y = initial.y, width = initial.width, height = initial.height;
                    if (edge == Edge.WEST || edge == Edge.NORTH_WEST || edge == Edge.SOUTH_WEST) { x += dx; width -= dx; }
                    if (edge == Edge.EAST || edge == Edge.NORTH_EAST || edge == Edge.SOUTH_EAST) width += dx;
                    if (edge == Edge.NORTH || edge == Edge.NORTH_WEST || edge == Edge.NORTH_EAST) { y += dy; height -= dy; }
                    if (edge == Edge.SOUTH || edge == Edge.SOUTH_WEST || edge == Edge.SOUTH_EAST) height += dy;
                    int minW = getMinimumSize().width;
                    int minH = getMinimumSize().height;
                    if (width >= minW && height >= minH) setBounds(x, y, width, height);
                }
            };
            addMouseListener(resize);
            addMouseMotionListener(resize);
        }
    }

    private boolean isMaximized() {
        return (getExtendedState() & JFrame.MAXIMIZED_BOTH) == JFrame.MAXIMIZED_BOTH;
    }

    private static final class SurfacePanel extends JPanel {
        private final Color color;
        private SurfacePanel(Color color) {
            this.color = color;
            setOpaque(false);
        }
        @Override protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            g.setColor(color);
            g.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            g.dispose();
            super.paintComponent(graphics);
        }
    }

    private static void configureLookAndFeel() {
        FlatDarkLaf.setup();
        String fontName = resolveChineseFont();
        Font font = new Font(fontName, Font.PLAIN, 13);
        UIManager.put("defaultFont", font);
        UIManager.put("Label.font", font);
        UIManager.put("Button.font", font);
        UIManager.put("TextField.font", font);
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Component.arc", 11);
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
    }

    private static String resolveChineseFont() {
        List<String> installed = List.of(GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());
        for (String preferred : List.of("Microsoft YaHei UI", "PingFang SC", "Noto Sans CJK SC")) {
            if (installed.contains(preferred)) return preferred;
        }
        return Font.SANS_SERIF;
    }

    public static void main(String[] args) {
        configureLookAndFeel();
        SwingUtilities.invokeLater(() -> new MainFrame().setVisible(true));
    }
}