package com.musicplayer.ui;

import com.formdev.flatlaf.FlatDarkLaf;
import com.musicplayer.lyric.LyricManager;
import com.musicplayer.model.Music;
import com.musicplayer.player.PlaybackController;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.KeyEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public final class ModernMusicPlayerUI extends JFrame {
    private static final Color BACKGROUND = Color.decode("#14171B");
    private static final Color PANEL = Color.decode("#1C2026");
    private static final Color RAISED = Color.decode("#252B32");
    private static final Color TEXT = Color.decode("#F2F4F7");
    private static final Color MUTED = Color.decode("#99A3AD");
    private static final Color ACCENT = Color.decode("#55D6A7");

    private enum PlayMode { SEQUENCE, SINGLE, SHUFFLE }

    private final DefaultListModel<Music> listModel = new DefaultListModel<>();
    private final JList<Music> playlist = new JList<>(listModel);
    private final JLabel titleLabel = new JLabel("选择一首歌曲");
    private final JLabel artistLabel = new JLabel("曲库中的音乐会显示在这里");
    private final JLabel statusLabel = new JLabel("正在读取曲库…");
    private final JLabel lyricLabel = new JLabel("欢迎回来", SwingConstants.CENTER);
    private final JLabel elapsedLabel = new JLabel("0:00");
    private final JLabel durationLabel = new JLabel("0:00");
    private final JLabel countLabel = new JLabel("0 首歌曲");
    private final JButton playButton = iconButton("▶", true, 54);
    private final JButton modeButton = textButton("顺序播放");
    private final JSlider progressSlider = new JSlider(0, 1000, 0);
    private final JSlider volumeSlider = new JSlider(0, 100, 72);
    private final AlbumArt albumArt = new AlbumArt();

    private final LyricManager lyricManager = new LyricManager();
    private final PlaybackController playbackController;
    private int currentIndex = -1;
    private int hoveredIndex = -1;
    private long durationMillis;
    private int lastVolume = 72;
    private PlayMode playMode = PlayMode.SEQUENCE;
    private boolean muted;
    private boolean seeking;

    public ModernMusicPlayerUI() {
        super("Java 音乐播放器");
        playbackController = new PlaybackController(new PlaybackController.Listener() {
            @Override
            public void onReady(long duration) {
                SwingUtilities.invokeLater(() -> {
                    durationMillis = duration;
                    durationLabel.setText(formatTime(duration));
                    statusLabel.setText("正在播放");
                    playButton.setText("Ⅱ");
                    lyricManager.startLyricScroll();
                });
            }

            @Override
            public void onPosition(long position) {
                SwingUtilities.invokeLater(() -> {
                    if (!seeking && durationMillis > 0) {
                        progressSlider.setValue((int) Math.min(1000,
                                position * 1000 / durationMillis));
                    }
                    elapsedLabel.setText(formatTime(position));
                });
            }

            @Override
            public void onEnd() {
                SwingUtilities.invokeLater(ModernMusicPlayerUI.this::playNextAtEnd);
            }

            @Override
            public void onError(String message) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("播放失败");
                    playButton.setText("▶");
                    albumArt.setPlaying(false);
                    JOptionPane.showMessageDialog(ModernMusicPlayerUI.this,
                            message, "无法播放", JOptionPane.ERROR_MESSAGE);
                });
            }
        });

        configureLookAndFeel();
        configureFrame();
        buildInterface();
        bindInteractions();
        lyricManager.setLyricUpdateCallback(lyricContent ->
                SwingUtilities.invokeLater(() -> lyricLabel.setText(lyricContent)));
        loadLocalMusic();
    }

    private void configureLookAndFeel() {
        UIManager.put("defaultFont", new Font(resolveChineseFont(), Font.PLAIN, 13));
        UIManager.put("Component.focusWidth", 0);
        UIManager.put("Component.arc", 10);
        UIManager.put("Button.arc", 12);
        UIManager.put("TextComponent.arc", 8);
        UIManager.put("Component.accentColor", ACCENT);
    }

    private String resolveChineseFont() {
        String[] preferred = {"Microsoft YaHei UI", "Microsoft YaHei", "PingFang SC",
                "Noto Sans CJK SC", "WenQuanYi Micro Hei"};
        List<String> installed = new ArrayList<>();
        Collections.addAll(installed, GraphicsEnvironment.getLocalGraphicsEnvironment()
                .getAvailableFontFamilyNames());
        for (String family : preferred) {
            if (installed.contains(family)) return family;
        }
        return Font.SANS_SERIF;
    }

    private void configureFrame() {
        setMinimumSize(new Dimension(820, 560));
        setSize(1080, 700);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        getContentPane().setBackground(BACKGROUND);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent event) {
                lyricManager.stopLyricScroll();
                playbackController.shutdown();
            }
        });
    }

    private void buildInterface() {
        JPanel root = new JPanel(new BorderLayout(1, 0));
        root.setBackground(BACKGROUND);
        root.add(buildSidebar(), BorderLayout.WEST);
        root.add(buildPlayerPanel(), BorderLayout.CENTER);
        setContentPane(root);
        root.setTransferHandler(fileDropHandler());
    }

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 16));
        sidebar.setBackground(PANEL);
        sidebar.setBorder(new EmptyBorder(24, 18, 18, 18));
        sidebar.setPreferredSize(new Dimension(292, 0));

        JPanel heading = new JPanel(new BorderLayout(8, 8));
        heading.setOpaque(false);
        JPanel titles = new JPanel();
        titles.setOpaque(false);
        titles.setLayout(new BoxLayout(titles, BoxLayout.Y_AXIS));
        JLabel brand = label("MUSIC ROOM", 12, ACCENT, Font.BOLD);
        JLabel libraryTitle = label("你的曲库", 23, TEXT, Font.BOLD);
        libraryTitle.setBorder(new EmptyBorder(8, 0, 0, 0));
        titles.add(brand);
        titles.add(libraryTitle);
        JButton addButton = iconButton("＋", false, 38);
        addButton.setToolTipText("添加音频文件");
        heading.add(titles, BorderLayout.CENTER);
        heading.add(addButton, BorderLayout.EAST);

        playlist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playlist.setBackground(PANEL);
        playlist.setFixedCellHeight(68);
        playlist.setCellRenderer(new TrackRenderer());
        JScrollPane scrollPane = new JScrollPane(playlist);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());
        scrollPane.getViewport().setBackground(PANEL);
        scrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);

        JPanel footer = new JPanel(new BorderLayout(0, 6));
        footer.setOpaque(false);
        countLabel.setForeground(MUTED);
        footer.add(countLabel, BorderLayout.NORTH);
        footer.add(label("拖入音频文件即可添加", 11, MUTED, Font.PLAIN), BorderLayout.SOUTH);

        sidebar.add(heading, BorderLayout.NORTH);
        sidebar.add(scrollPane, BorderLayout.CENTER);
        sidebar.add(footer, BorderLayout.SOUTH);
        addButton.addActionListener(event -> chooseFiles());
        sidebar.setTransferHandler(fileDropHandler());
        scrollPane.setTransferHandler(fileDropHandler());
        playlist.setTransferHandler(fileDropHandler());
        return sidebar;
    }

    private JPanel buildPlayerPanel() {
        JPanel playerPanel = new JPanel(new BorderLayout(0, 16));
        playerPanel.setBackground(BACKGROUND);
        playerPanel.setBorder(new EmptyBorder(22, 30, 22, 30));
        playerPanel.add(buildTopBar(), BorderLayout.NORTH);
        playerPanel.add(buildTrackDetails(), BorderLayout.CENTER);
        playerPanel.add(buildControls(), BorderLayout.SOUTH);
        playerPanel.setTransferHandler(fileDropHandler());
        return playerPanel;
    }

    private JPanel buildTopBar() {
        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        topBar.add(label("正在聆听", 13, MUTED, Font.PLAIN), BorderLayout.WEST);
        statusLabel.setForeground(ACCENT);
        statusLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        topBar.add(statusLabel, BorderLayout.EAST);
        return topBar;
    }

    private JPanel buildTrackDetails() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.insets = new Insets(8, 12, 8, 28);
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 0;
        constraints.fill = GridBagConstraints.NONE;
        content.add(albumArt, constraints);

        JPanel details = new JPanel();
        details.setOpaque(false);
        details.setLayout(new BoxLayout(details, BoxLayout.Y_AXIS));
        JLabel nowPlaying = label("NOW PLAYING", 11, ACCENT, Font.BOLD);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 28f));
        titleLabel.setForeground(TEXT);
        artistLabel.setFont(artistLabel.getFont().deriveFont(Font.PLAIN, 15f));
        artistLabel.setForeground(MUTED);
        lyricLabel.setFont(lyricLabel.getFont().deriveFont(Font.PLAIN, 17f));
        lyricLabel.setForeground(TEXT);
        lyricLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
        lyricLabel.setBorder(new EmptyBorder(30, 0, 0, 0));
        details.add(nowPlaying);
        details.add(Box.createVerticalStrut(18));
        details.add(titleLabel);
        details.add(Box.createVerticalStrut(8));
        details.add(artistLabel);
        details.add(Box.createVerticalStrut(12));
        details.add(lyricLabel);

        constraints.gridx = 1;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(8, 0, 8, 0);
        content.add(details, constraints);
        return content;
    }

    private JPanel buildControls() {
        JPanel controls = new JPanel();
        controls.setOpaque(false);
        controls.setLayout(new BoxLayout(controls, BoxLayout.Y_AXIS));

        JPanel timeline = new JPanel(new BorderLayout(12, 0));
        timeline.setOpaque(false);
        elapsedLabel.setForeground(MUTED);
        durationLabel.setForeground(MUTED);
        progressSlider.setOpaque(false);
        progressSlider.setForeground(ACCENT);
        timeline.add(elapsedLabel, BorderLayout.WEST);
        timeline.add(progressSlider, BorderLayout.CENTER);
        timeline.add(durationLabel, BorderLayout.EAST);

        JPanel transport = new JPanel(new FlowLayout(FlowLayout.CENTER, 14, 8));
        transport.setOpaque(false);
        JButton previousButton = iconButton("⏮", false, 42);
        JButton stopButton = iconButton("■", false, 42);
        JButton nextButton = iconButton("⏭", false, 42);
        JButton muteButton = iconButton("♪", false, 38);
        previousButton.setToolTipText("上一首");
        playButton.setToolTipText("播放 / 暂停（空格）");
        stopButton.setToolTipText("停止");
        nextButton.setToolTipText("下一首");
        muteButton.setToolTipText("静音");
        modeButton.setToolTipText("切换播放模式");
        transport.add(previousButton);
        transport.add(playButton);
        transport.add(stopButton);
        transport.add(nextButton);

        JPanel options = new JPanel(new BorderLayout(12, 0));
        options.setOpaque(false);
        modeButton.setForeground(MUTED);
        options.add(modeButton, BorderLayout.WEST);
        JPanel volume = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        volume.setOpaque(false);
        volumeSlider.setPreferredSize(new Dimension(130, 28));
        volumeSlider.setOpaque(false);
        volumeSlider.setValue(lastVolume);
        volume.add(muteButton);
        volume.add(volumeSlider);
        options.add(volume, BorderLayout.EAST);

        controls.add(timeline);
        controls.add(Box.createVerticalStrut(12));
        controls.add(transport);
        controls.add(Box.createVerticalStrut(8));
        controls.add(options);

        previousButton.addActionListener(event -> playPrevious());
        stopButton.addActionListener(event -> stopPlayback());
        nextButton.addActionListener(event -> playNext());
        muteButton.addActionListener(event -> toggleMute());
        return controls;
    }

    private void bindInteractions() {
        playButton.addActionListener(event -> togglePlayback());
        modeButton.addActionListener(event -> cycleMode());
        playlist.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent event) {
                if (event.getClickCount() == 2) playSelected();
            }

            @Override
            public void mouseExited(java.awt.event.MouseEvent event) {
                hoveredIndex = -1;
                playlist.repaint();
            }
        });
        playlist.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {
            @Override
            public void mouseMoved(java.awt.event.MouseEvent event) {
                int index = playlist.locationToIndex(event.getPoint());
                if (index != hoveredIndex) {
                    hoveredIndex = index;
                    playlist.repaint();
                }
            }
        });
        progressSlider.addChangeListener(event -> {
            if (progressSlider.getValueIsAdjusting()) seeking = true;
            else if (seeking) {
                seeking = false;
                if (durationMillis > 0) {
                    playbackController.seek(durationMillis * progressSlider.getValue() / 1000);
                }
            }
        });
        volumeSlider.addChangeListener(event -> {
            int value = volumeSlider.getValue();
            if (!muted) playbackController.setVolume(value / 100.0);
            if (value > 0) lastVolume = value;
        });
        getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                .put(KeyStroke.getKeyStroke(KeyEvent.VK_SPACE, 0), "toggle-playback");
        getRootPane().getActionMap().put("toggle-playback", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent event) {
                togglePlayback();
            }
        });
    }

    private TransferHandler fileDropHandler() {
        return new TransferHandler() {
            @Override
            public boolean canImport(TransferSupport support) {
                return support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
            }

            @Override
            @SuppressWarnings("unchecked")
            public boolean importData(TransferSupport support) {
                if (!canImport(support)) return false;
                try {
                    addFiles((List<File>) support.getTransferable()
                            .getTransferData(DataFlavor.javaFileListFlavor));
                    return true;
                } catch (Exception exception) {
                    statusLabel.setText("无法读取拖入的文件");
                    return false;
                }
            }
        };
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser(new File("resources/music"));
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter(
            "MP3 音频文件", "mp3"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] files = chooser.getSelectedFiles();
            List<File> selected = new ArrayList<>();
            Collections.addAll(selected, files);
            addFiles(selected);
        }
    }

    private void loadLocalMusic() {
        File directory = new File("resources/music");
        if (!directory.exists()) directory.mkdirs();
        File[] files = directory.listFiles(file -> isAudioFile(file));
        if (files == null || files.length == 0) {
            statusLabel.setText("曲库为空，可添加或拖入音频");
            return;
        }
        List<File> initialFiles = new ArrayList<>();
        Collections.addAll(initialFiles, files);
        addFiles(initialFiles);
    }

    private void addFiles(List<File> files) {
        List<File> audioFiles = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && isAudioFile(file) && !containsPath(file)) audioFiles.add(file);
        }
        if (audioFiles.isEmpty()) return;
        statusLabel.setText("正在读取音频标签…");
        new SwingWorker<List<Music>, Void>() {
            @Override
            protected List<Music> doInBackground() {
                List<Music> tracks = new ArrayList<>();
                for (File file : audioFiles) tracks.add(Music.fromFile(file));
                return tracks;
            }

            @Override
            protected void done() {
                try {
                    for (Music track : get()) listModel.addElement(track);
                    updateCount();
                    statusLabel.setText("曲库已就绪");
                } catch (Exception exception) {
                    statusLabel.setText("读取音频标签失败");
                }
            }
        }.execute();
    }

    private boolean containsPath(File file) {
        String path = file.getAbsolutePath();
        for (int index = 0; index < listModel.size(); index++) {
            if (path.equals(listModel.get(index).getPath())) return true;
        }
        return false;
    }

    private boolean isAudioFile(File file) {
        String name = file.getName().toLowerCase(Locale.ROOT);
        return name.endsWith(".mp3");
    }

    private void updateCount() {
        countLabel.setText(listModel.size() + " 首歌曲");
    }

    private void togglePlayback() {
        if (currentIndex < 0) {
            playSelected();
            return;
        }
        playbackController.togglePause();
        if ("正在播放".equals(statusLabel.getText())) {
            statusLabel.setText("已暂停");
            playButton.setText("▶");
            lyricManager.stopLyricScroll();
        } else {
            statusLabel.setText("正在播放");
            playButton.setText("Ⅱ");
            lyricManager.startLyricScroll();
        }
    }

    private void playSelected() {
        int selected = playlist.getSelectedIndex();
        if (selected >= 0) startTrack(selected);
        else if (!listModel.isEmpty()) startTrack(0);
        else statusLabel.setText("曲库为空，请先添加音频");
    }

    private void startTrack(int index) {
        if (index < 0 || index >= listModel.size()) return;
        currentIndex = index;
        Music music = listModel.get(index);
        playlist.setSelectedIndex(index);
        playlist.ensureIndexIsVisible(index);
        titleLabel.setText(music.getName());
        artistLabel.setText(music.getArtist());
        statusLabel.setText("正在加载音频…");
        playButton.setText("Ⅱ");
        albumArt.setPlaying(true);
        durationMillis = 0;
        progressSlider.setValue(0);
        elapsedLabel.setText("0:00");
        durationLabel.setText("0:00");
        lyricManager.stopLyricScroll();
        loadLyric(music.getName());
        playlist.repaint();
        playbackController.play(music);
    }

    private void stopPlayback() {
        playbackController.stop();
        lyricManager.stopLyricScroll();
        statusLabel.setText("已停止");
        playButton.setText("▶");
        albumArt.setPlaying(false);
        progressSlider.setValue(0);
        elapsedLabel.setText("0:00");
        lyricLabel.setText("播放已停止");
    }

    private void playPrevious() {
        if (currentIndex < 0 || listModel.isEmpty()) return;
        if (progressSlider.getValue() > 20) {
            playbackController.seek(0);
            return;
        }
        startTrack((currentIndex - 1 + listModel.size()) % listModel.size());
    }

    private void playNext() {
        if (listModel.isEmpty()) return;
        int next = playMode == PlayMode.SHUFFLE && listModel.size() > 1
                ? new Random().nextInt(listModel.size()) : (currentIndex + 1) % listModel.size();
        startTrack(next);
    }

    private void playNextAtEnd() {
        if (playMode == PlayMode.SINGLE && currentIndex >= 0) {
            startTrack(currentIndex);
        } else if (playMode == PlayMode.SHUFFLE && listModel.size() > 1) {
            playNext();
        } else if (currentIndex + 1 < listModel.size()) {
            startTrack(currentIndex + 1);
        } else {
            statusLabel.setText("播放列表已结束");
            playButton.setText("▶");
            albumArt.setPlaying(false);
        }
    }

    private void cycleMode() {
        playMode = PlayMode.values()[(playMode.ordinal() + 1) % PlayMode.values().length];
        String modeText = playMode == PlayMode.SEQUENCE ? "顺序播放"
                : playMode == PlayMode.SINGLE ? "单曲循环" : "随机播放";
        modeButton.setText(modeText);
        modeButton.setToolTipText("当前模式：" + modeText + "（点击切换）");
    }

    private void toggleMute() {
        muted = !muted;
        if (muted) {
            lastVolume = Math.max(1, volumeSlider.getValue());
            volumeSlider.setValue(0);
            playbackController.setVolume(0);
        } else {
            volumeSlider.setValue(lastVolume);
            playbackController.setVolume(lastVolume / 100.0);
        }
    }

    private void loadLyric(String musicName) {
        File lyricFile = new File("resources/lyrics", musicName + ".lrc");
        if (lyricFile.isFile()) {
            lyricManager.loadLyric(lyricFile.getAbsolutePath());
            lyricLabel.setText("♪  歌词即将开始");
        } else {
            lyricManager.clearLyric();
            lyricLabel.setText("暂无歌词");
        }
    }

    private JLabel label(String text, int size, Color color, int style) {
        JLabel label = new JLabel(text);
        label.setFont(label.getFont().deriveFont(style, (float) size));
        label.setForeground(color);
        return label;
    }

    private static JButton iconButton(String text, boolean primary, int size) {
        JButton button = new JButton(text);
        button.setPreferredSize(new Dimension(size, size));
        button.setMinimumSize(new Dimension(size, size));
        button.setFont(button.getFont().deriveFont(Font.BOLD, primary ? 21f : 17f));
        button.setFocusPainted(false);
        button.setForeground(primary ? BACKGROUND : TEXT);
        button.setBackground(primary ? ACCENT : RAISED);
        button.putClientProperty("JButton.buttonType", "roundRect");
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private static JButton textButton(String text) {
        JButton button = new JButton(text);
        button.setFocusPainted(false);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFont(button.getFont().deriveFont(Font.PLAIN, 12f));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return button;
    }

    private String formatTime(long millis) {
        long seconds = Math.max(0, millis / 1000);
        return String.format(Locale.ROOT, "%d:%02d", seconds / 60, seconds % 60);
    }

    private final class TrackRenderer extends JPanel implements ListCellRenderer<Music> {
        private final JLabel number = new JLabel();
        private final JLabel trackTitle = new JLabel();
        private final JLabel artist = new JLabel();
        private final JLabel playing = new JLabel("▶");

        private TrackRenderer() {
            super(new BorderLayout(12, 0));
            setBorder(new EmptyBorder(8, 10, 8, 10));
            number.setForeground(MUTED);
            number.setPreferredSize(new Dimension(24, 24));
            JPanel text = new JPanel();
            text.setOpaque(false);
            text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
            trackTitle.setFont(trackTitle.getFont().deriveFont(Font.BOLD, 13f));
            artist.setFont(artist.getFont().deriveFont(Font.PLAIN, 11f));
            text.add(trackTitle);
            text.add(Box.createVerticalStrut(5));
            text.add(artist);
            playing.setForeground(ACCENT);
            add(number, BorderLayout.WEST);
            add(text, BorderLayout.CENTER);
            add(playing, BorderLayout.EAST);
        }

        @Override
        public Component getListCellRendererComponent(JList<? extends Music> list,
                Music music, int index, boolean selected, boolean cellHasFocus) {
            boolean active = index == currentIndex;
            boolean hover = index == hoveredIndex;
            setBackground(selected ? RAISED : hover ? new Color(37, 43, 50) : PANEL);
            number.setText(String.format(Locale.ROOT, "%02d", index + 1));
            trackTitle.setText(music.getName());
            artist.setText(music.getArtist());
            trackTitle.setForeground(selected || active ? TEXT : new Color(218, 223, 229));
            artist.setForeground(MUTED);
            playing.setVisible(active);
            number.setForeground(active ? ACCENT : MUTED);
            return this;
        }
    }

    private static final class AlbumArt extends JPanel {
        private boolean playing;

        private AlbumArt() {
            setPreferredSize(new Dimension(190, 190));
            setOpaque(false);
        }

        private void setPlaying(boolean playing) {
            this.playing = playing;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int diameter = Math.min(getWidth(), getHeight()) - 10;
            int x = (getWidth() - diameter) / 2;
            int y = (getHeight() - diameter) / 2;
            g.setColor(new Color(10, 12, 15));
            g.fillOval(x, y, diameter, diameter);
            for (int inset = 12; inset < diameter / 2; inset += 14) {
                g.setColor(new Color(255, 255, 255, 13));
                g.drawOval(x + inset, y + inset, diameter - inset * 2, diameter - inset * 2);
            }
            int center = diameter / 2;
            g.setColor(new Color(51, 91, 77));
            g.fillOval(x + center - 31, y + center - 31, 62, 62);
            g.setColor(ACCENT);
            g.fillOval(x + center - 5, y + center - 5, 10, 10);
            g.setFont(getFont().deriveFont(Font.BOLD, 18f));
            g.drawString(playing ? "♪" : "♫", x + center - 8, y + center + 46);
            g.dispose();
        }
    }

    public static void main(String[] args) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        System.setProperty("swing.aatext", "true");
        FlatDarkLaf.setup();
        SwingUtilities.invokeLater(() -> new ModernMusicPlayerUI().setVisible(true));
    }
}
