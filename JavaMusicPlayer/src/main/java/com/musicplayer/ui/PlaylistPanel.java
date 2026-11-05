package com.musicplayer.ui;

import com.musicplayer.model.Music;

import javax.swing.DefaultListModel;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PlaylistPanel extends JPanel {
    private final DefaultListModel<Music> tracks = new DefaultListModel<>();
    private final DefaultListModel<Music> visibleTracks = new DefaultListModel<>();
    private final JList<Music> list = new JList<>(visibleTracks);
    private final PlaylistCellRenderer renderer = new PlaylistCellRenderer();
    private final JTextField search = new JTextField();
    private final JLabel count = new JLabel("0 首歌曲");
    private final Supplier<Color> accentSupplier;
    private Consumer<Music> onPlay = track -> { };
    private Color accent = ThemeColorExtractor.DEFAULT;
    private String playingPath;
    private int equalizerFrame;

    public PlaylistPanel(Supplier<Color> accentSupplier) {
        this.accentSupplier = accentSupplier;
        setLayout(new BorderLayout(0, 16));
        setOpaque(false);
        setBorder(new EmptyBorder(15, 17, 15, 17));
        setPreferredSize(new Dimension(310, 0));
        buildHeader();
        buildList();
        add(buildFooter(), BorderLayout.SOUTH);
        installDropHandler();
        new javax.swing.Timer(33, event -> {
            equalizerFrame = (equalizerFrame + 1) % 20;
            renderer.setEqualizerFrame(equalizerFrame);
            renderer.animate();
            if (playingPath != null) list.repaint();
        }).start();
    }

    private void buildHeader() {
        JPanel header = new JPanel(new BorderLayout(0, 12));
        header.setOpaque(false);
        JPanel top = new JPanel(new BorderLayout());
        top.setOpaque(false);
        JLabel title = new JLabel("你的曲库");
        title.setForeground(new Color(244, 245, 248));
        title.setFont(title.getFont().deriveFont(java.awt.Font.BOLD, 21f));
        top.add(title, BorderLayout.WEST);
        IconButton addButton = new IconButton(IconPainter.IconType.ADD, 36, false,
                accentSupplier, "添加歌曲");
        addButton.addActionListener(this::chooseFiles);
        top.add(addButton, BorderLayout.EAST);

        JPanel searchBox = new JPanel(new BorderLayout(8, 0));
        searchBox.setBackground(new Color(255, 255, 255, 16));
        searchBox.setBorder(new EmptyBorder(0, 10, 0, 8));
        IconCanvas searchIcon = new IconCanvas(IconPainter.IconType.SEARCH, 16,
                new Color(155, 164, 178));
        searchBox.add(searchIcon, BorderLayout.WEST);
        search.setOpaque(false);
        search.setForeground(new Color(231, 234, 239));
        search.setCaretColor(accent);
        search.setBorder(new EmptyBorder(9, 0, 9, 0));
        search.setFont(search.getFont().deriveFont(12f));
        search.setToolTipText("搜索歌曲或歌手");
        searchBox.add(search, BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { filter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { filter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { filter(); }
        });
        header.add(top, BorderLayout.NORTH);
        header.add(searchBox, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
    }

    private void buildList() {
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setOpaque(false);
        list.setBackground(new Color(0, 0, 0, 0));
        list.setFixedCellHeight(68);
        list.setCellRenderer(renderer);
        list.setBorder(new EmptyBorder(2, 0, 2, 0));
        list.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) updateRendererTargets();
        });
        list.addMouseListener(new MouseAdapter() {
            @Override public void mouseClicked(MouseEvent event) {
                if (event.getClickCount() == 2) {
                    Music selected = list.getSelectedValue();
                    if (selected != null) onPlay.accept(selected);
                }
            }
            @Override public void mouseExited(MouseEvent event) {
                renderer.setTargets(selectedPath(), null);
            }
        });
        list.addMouseMotionListener(new MouseAdapter() {
            @Override public void mouseMoved(MouseEvent event) {
                int index = list.locationToIndex(event.getPoint());
                renderer.setActivePaths(selectedPath(), index >= 0 ? visibleTracks.get(index).getPath() : null);
                renderer.setTargets(selectedPath(), index >= 0 ? visibleTracks.get(index).getPath() : null);
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        add(scroll, BorderLayout.CENTER);
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        footer.setOpaque(false);
        count.setForeground(new Color(145, 153, 166));
        count.setFont(count.getFont().deriveFont(11f));
        footer.add(count);
        return footer;
    }

    private void chooseFiles() {
        JFileChooser chooser = new JFileChooser(new File("resources/music"));
        chooser.setMultiSelectionEnabled(true);
        chooser.setFileFilter(new FileNameExtensionFilter("MP3 音频文件", "mp3"));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File[] files = chooser.getSelectedFiles();
            if (files.length == 0 && chooser.getSelectedFile() != null) files = new File[]{chooser.getSelectedFile()};
            addFiles(List.of(files));
        }
    }

    private void installDropHandler() {
        setTransferHandler(audioTransferHandler());
        list.setTransferHandler(audioTransferHandler());
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
                    addFiles((List<File>) support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor));
                    return true;
                } catch (Exception exception) {
                    return false;
                }
            }
        };
    }

    public void addFiles(List<File> files) {
        if (!javax.swing.SwingUtilities.isEventDispatchThread()) {
            javax.swing.SwingUtilities.invokeLater(() -> addFiles(files));
            return;
        }
        List<File> toRead = new ArrayList<>();
        for (File file : files) {
            if (file.isFile() && file.getName().toLowerCase(Locale.ROOT).endsWith(".mp3") && !contains(file)) {
                toRead.add(file);
            }
        }
        if (toRead.isEmpty()) return;
        new SwingWorker<List<Music>, Void>() {
            @Override protected List<Music> doInBackground() {
                List<Music> loaded = new ArrayList<>();
                for (File file : toRead) loaded.add(Music.fromFile(file));
                return loaded;
            }
            @Override protected void done() {
                try {
                    for (Music track : get()) tracks.addElement(track);
                    filter();
                    count.setText(tracks.size() + " 首歌曲");
                    if (visibleTracks.size() > 0 && list.getSelectedIndex() < 0) list.setSelectedIndex(0);
                } catch (Exception ignored) {
                    count.setText("读取歌曲失败");
                }
            }
        }.execute();
    }

    private boolean contains(File candidate) {
        String path = candidate.getAbsolutePath();
        for (int index = 0; index < tracks.size(); index++) {
            if (tracks.get(index).getPath().equals(path)) return true;
        }
        return false;
    }

    private void filter() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        Music selected = list.getSelectedValue();
        visibleTracks.clear();
        for (int index = 0; index < tracks.size(); index++) {
            Music track = tracks.get(index);
            if (query.isEmpty() || (track.getName() + " " + track.getArtist()).toLowerCase(Locale.ROOT).contains(query)) {
                visibleTracks.addElement(track);
            }
        }
        if (selected != null) {
            for (int index = 0; index < visibleTracks.size(); index++) {
                if (visibleTracks.get(index).getPath().equals(selected.getPath())) {
                    list.setSelectedIndex(index);
                    break;
                }
            }
        }
        count.setText(tracks.size() + " 首歌曲");
        updateRendererTargets();
    }

    private String selectedPath() {
        Music selected = list.getSelectedValue();
        return selected == null ? null : selected.getPath();
    }

    private void updateRendererTargets() {
        renderer.setActivePaths(selectedPath(), playingPath);
        renderer.setPlayingPath(playingPath);
        renderer.setTargets(selectedPath(), null);
        list.repaint();
    }

    public void setOnPlay(Consumer<Music> onPlay) {
        this.onPlay = onPlay == null ? track -> { } : onPlay;
    }

    public void setPlayingTrack(Music track) {
        playingPath = track == null ? null : track.getPath();
        renderer.setPlayingPath(playingPath);
        renderer.setAccent(accent);
        updateRendererTargets();
    }

    public void setAccent(Color accent) {
        this.accent = accent;
        renderer.setAccent(accent);
        repaint();
    }

    public List<Music> getTracks() {
        List<Music> result = new ArrayList<>();
        for (int index = 0; index < tracks.size(); index++) result.add(tracks.get(index));
        return Collections.unmodifiableList(result);
    }

    public void loadInitialTracks(File directory) {
        File[] files = directory.listFiles(file -> file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".mp3"));
        if (files != null && files.length > 0) addFiles(List.of(files));
    }

    public void playSelectedOrFirst() {
        Music selected = list.getSelectedValue();
        if (selected == null && !visibleTracks.isEmpty()) selected = visibleTracks.get(0);
        if (selected != null) onPlay.accept(selected);
    }

    private static final class IconCanvas extends javax.swing.JComponent {
        private final IconPainter.IconType type;
        private final int size;
        private final Color color;
        private IconCanvas(IconPainter.IconType type, int size, Color color) {
            this.type = type; this.size = size; this.color = color;
            setPreferredSize(new Dimension(size, size));
        }
        @Override protected void paintComponent(java.awt.Graphics graphics) {
            IconPainter.paint((java.awt.Graphics2D) graphics, type, 0, 0, size, color);
        }
    }
}