package com.musicplayer.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.musicplayer.model.Music;
import com.musicplayer.player.PlayerController;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.text.JTextComponent;
import java.awt.AWTEvent;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.AWTEventListener;
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
    private final java.awt.KeyEventDispatcher playbackKeyDispatcher = this::dispatchPlaybackShortcut;
    private final AWTEventListener searchFocusMouseListener = this::releaseSearchFocusAfterClick;

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
        KeyboardFocusManager.getCurrentKeyboardFocusManager()
                .addKeyEventDispatcher(playbackKeyDispatcher);
        Toolkit.getDefaultToolkit().addAWTEventListener(searchFocusMouseListener,
                AWTEvent.MOUSE_EVENT_MASK);
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
                KeyboardFocusManager.getCurrentKeyboardFocusManager()
                        .removeKeyEventDispatcher(playbackKeyDispatcher);
                Toolkit.getDefaultToolkit().removeAWTEventListener(searchFocusMouseListener);
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

    }

    private boolean dispatchPlaybackShortcut(KeyEvent event) {
        if (event.getID() != KeyEvent.KEY_PRESSED
                || event.getModifiersEx() != 0
                || KeyboardFocusManager.getCurrentKeyboardFocusManager().getActiveWindow() != this) {
            return false;
        }
        Component focusOwner = KeyboardFocusManager.getCurrentKeyboardFocusManager().getFocusOwner();
        // 文本组件负责输入法组合与方向键导航，播放器快捷键必须让它优先处理。
        if (focusOwner instanceof JTextComponent) return false;

        Runnable action = switch (event.getKeyCode()) {
            case KeyEvent.VK_SPACE -> this::togglePlayback;
            case KeyEvent.VK_LEFT ->
                    () -> seekTo(Math.max(0, currentPositionMillis - 5000));
            case KeyEvent.VK_RIGHT ->
                    () -> seekTo(Math.min(durationMillis, currentPositionMillis + 5000));
            case KeyEvent.VK_UP -> () -> controls.adjustVolume(0.05);
            case KeyEvent.VK_DOWN -> () -> controls.adjustVolume(-0.05);
            default -> null;
        };
        if (action == null) return false;
        action.run();
        event.consume();
        return true;
    }

    private void releaseSearchFocusAfterClick(AWTEvent event) {
        if (!(event instanceof MouseEvent mouseEvent)
                || mouseEvent.getID() != MouseEvent.MOUSE_PRESSED
                || !(mouseEvent.getSource() instanceof Component clicked)
                || SwingUtilities.getWindowAncestor(clicked) != this) {
            return;
        }
        if (SwingUtilities.isDescendingFrom(clicked, playlist.getSearchField())) return;
        SwingUtilities.invokeLater(() -> {
            if (playlist.getSearchField().isFocusOwner()) playlist.focusTrackList();
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
                Path libraryLyrics = Path.of("resources", "lyrics");
                var lyricPath = LyricFileLocator.findLyrics(music, libraryLyrics);
                return lyricPath.isEmpty() ? List.of() : LrcParser.parse(lyricPath.get());
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