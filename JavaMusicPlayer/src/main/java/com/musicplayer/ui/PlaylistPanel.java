package com.musicplayer.ui;

import com.musicplayer.model.Music;
import com.musicplayer.search.SongFilter;

import javax.swing.AbstractListModel;
import javax.swing.DefaultListModel;
import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.TransferHandler;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.undo.UndoManager;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.CardLayout;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.InputMethodEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.KeyEvent;
import java.text.CharacterIterator;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PlaylistPanel extends JPanel {
    private final DefaultListModel<Music> tracks = new DefaultListModel<>();
    private final TrackListModel visibleTracks = new TrackListModel();
    private final JList<Music> list = new JList<>(visibleTracks);
    private final PlaylistCellRenderer renderer = new PlaylistCellRenderer();
    private boolean inputMethodComposing;
    private final JTextField search = new JTextField() {
        @Override protected void processInputMethodEvent(InputMethodEvent event) {
            inputMethodComposing = hasComposedText(event);
            super.processInputMethodEvent(event);
            if (!inputMethodComposing) filterTimer.restart();
        }
    };
    private final javax.swing.Timer filterTimer = new javax.swing.Timer(150, event -> {
        if (!inputMethodComposing) filter();
    });
    private final JPanel listArea = new JPanel(new CardLayout());
    private final JLabel emptyResult = new JLabel("没有找到匹配的歌曲", SwingConstants.CENTER);
    private final UndoManager searchUndoManager = new UndoManager();
    private final JLabel count = new JLabel("0 首歌曲");
    private final Supplier<Color> accentSupplier;
    private final IconButton clearSearchButton;
    private Consumer<Music> onPlay = track -> { };
    private Color accent = ThemeColorExtractor.DEFAULT;
    private String playingPath;
    private int equalizerFrame;

    public PlaylistPanel(Supplier<Color> accentSupplier) {
        this.accentSupplier = accentSupplier;
        clearSearchButton = new IconButton(IconPainter.IconType.CLEAR,
                24, false, this.accentSupplier, "清空搜索");
        filterTimer.setRepeats(false);
        setLayout(new BorderLayout(0, 16));
        setOpaque(false);
        setBorder(new EmptyBorder(15, 17, 15, 17));
        setPreferredSize(new Dimension(310, 0));
        buildHeader();
        buildList();
        filter();
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
        java.awt.Font textFont = UIManager.getFont("TextField.font");
        if (textFont != null) search.setFont(textFont.deriveFont(12f));
        search.setToolTipText("搜索歌名、歌手或专辑，支持中文拼音");
        searchUndoManager.setLimit(100);
        search.getDocument().addUndoableEditListener(event -> {
            if (!inputMethodComposing) searchUndoManager.addEdit(event.getEdit());
        });
        installSearchEditingActions();
        clearSearchButton.addActionListener(() -> search.setText(""));
        clearSearchButton.setVisible(false);
        searchBox.add(clearSearchButton, BorderLayout.EAST);
        search.addFocusListener(new FocusAdapter() {
            @Override public void focusLost(FocusEvent event) {
                inputMethodComposing = false;
                filterTimer.restart();
            }
        });
        search.getInputMap(JComponent.WHEN_FOCUSED).put(
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), "focus-track-list");
        search.getActionMap().put("focus-track-list", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                focusTrackList();
            }
        });
        search.getInputMap(JComponent.WHEN_FOCUSED).put(
                javax.swing.KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0), "clear-or-blur-search");
        search.getActionMap().put("clear-or-blur-search", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                if (!search.getText().isEmpty()) search.setText("");
                else focusTrackList();
            }
        });
        searchBox.add(search, BorderLayout.CENTER);
        search.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
            @Override public void insertUpdate(javax.swing.event.DocumentEvent event) { updateClearSearchButton(); scheduleFilter(); }
            @Override public void removeUpdate(javax.swing.event.DocumentEvent event) { updateClearSearchButton(); scheduleFilter(); }
            @Override public void changedUpdate(javax.swing.event.DocumentEvent event) { updateClearSearchButton(); scheduleFilter(); }
        });
        header.add(top, BorderLayout.NORTH);
        header.add(searchBox, BorderLayout.SOUTH);
        add(header, BorderLayout.NORTH);
    }

    private void installSearchEditingActions() {
        int menuMask;
        try {
            menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        } catch (HeadlessException exception) {
            menuMask = KeyEvent.CTRL_DOWN_MASK;
        }
        bindSearchAction("undo-search", KeyEvent.VK_Z, menuMask, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                if (searchUndoManager.canUndo()) searchUndoManager.undo();
            }
        });
        bindSearchAction("redo-search", KeyEvent.VK_Y, menuMask, new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent event) {
                if (searchUndoManager.canRedo()) searchUndoManager.redo();
            }
        });
        bindSearchAction("redo-search-shift-z", KeyEvent.VK_Z,
                menuMask | KeyEvent.SHIFT_DOWN_MASK,
                search.getActionMap().get("redo-search"));

        JPopupMenu menu = new JPopupMenu();
        menu.add("撤销").addActionListener(event -> {
            if (searchUndoManager.canUndo()) searchUndoManager.undo();
        });
        menu.add("重做").addActionListener(event -> {
            if (searchUndoManager.canRedo()) searchUndoManager.redo();
        });
        menu.addSeparator();
        menu.add("剪切").addActionListener(event -> search.cut());
        menu.add("复制").addActionListener(event -> search.copy());
        menu.add("粘贴").addActionListener(event -> search.paste());
        menu.addSeparator();
        menu.add("全选").addActionListener(event -> search.selectAll());
        search.setComponentPopupMenu(menu);
    }

    private void bindSearchAction(String name, int keyCode, int modifiers, javax.swing.Action action) {
        search.getInputMap(JComponent.WHEN_FOCUSED).put(
                javax.swing.KeyStroke.getKeyStroke(keyCode, modifiers), name);
        search.getActionMap().put(name, action);
    }

    private void updateClearSearchButton() {
        clearSearchButton.setVisible(!search.getText().isEmpty());
    }

    private boolean hasComposedText(InputMethodEvent event) {
        CharacterIterator text = event.getText();
        if (text == null) return false;
        int textLength = 0;
        for (char character = text.first(); character != CharacterIterator.DONE;
             character = text.next()) textLength++;
        // 在 JTextField 处理事件前更新状态，使组合期间产生的文档编辑不进入撤销栈。
        return textLength > event.getCommittedCharacterCount();
    }

    private void buildList() {
        list.setFocusable(true);
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
                renderer.setActivePaths(selectedPath(), index >= 0
                        ? visibleTracks.getElementAt(index).getPath() : null);
                renderer.setTargets(selectedPath(), index >= 0
                        ? visibleTracks.getElementAt(index).getPath() : null);
            }
        });
        JScrollPane scroll = new JScrollPane(list);
        scroll.setBorder(null);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        scroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.getVerticalScrollBar().setUnitIncrement(18);
        emptyResult.setForeground(new Color(145, 153, 166));
        listArea.setOpaque(false);
        listArea.add(scroll, "tracks");
        listArea.add(emptyResult, "empty");
        add(listArea, BorderLayout.CENTER);
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
                for (File file : toRead) {
                    Music music = Music.fromFile(file);
                    SongFilter.index(music);
                    loaded.add(music);
                }
                return loaded;
            }
            @Override protected void done() {
                try {
                    for (Music track : get()) tracks.addElement(track);
                    filter();
                    if (visibleTracks.getSize() > 0 && list.getSelectedIndex() < 0) list.setSelectedIndex(0);
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

    private void scheduleFilter() {
        filterTimer.restart();
    }

    private void filter() {
        String query = search.getText();
        Music selected = list.getSelectedValue();
        List<Music> filtered = SongFilter.filter(query, getTracks());
        int selectedIndex = -1;
        for (int index = 0; index < filtered.size(); index++) {
            if (selected != null && selected.getPath().equals(filtered.get(index).getPath())) {
                selectedIndex = index;
            }
        }
        visibleTracks.replaceAll(filtered);
        list.clearSelection();
        if (selectedIndex >= 0) {
            list.setSelectedIndex(selectedIndex);
        } else if (visibleTracks.getSize() > 0) {
            list.setSelectedIndex(0);
        }
        boolean noResults = visibleTracks.getSize() == 0;
        emptyResult.setText(query.isBlank() ? "曲库暂无歌曲" : "没有找到匹配的歌曲");
        ((CardLayout) listArea.getLayout()).show(listArea, noResults ? "empty" : "tracks");
        if (query.isBlank()) {
            count.setText("共 " + tracks.size() + " 首");
        } else {
            count.setText(visibleTracks.getSize() + " / 共 " + tracks.size() + " 首");
        }
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

    JTextField getSearchField() {
        return search;
    }

    void focusTrackList() {
        list.requestFocusInWindow();
    }

    public void loadInitialTracks(File directory) {
        File[] files = directory.listFiles(file -> file.isFile()
                && file.getName().toLowerCase(Locale.ROOT).endsWith(".mp3"));
        if (files != null && files.length > 0) addFiles(List.of(files));
    }

    public void playSelectedOrFirst() {
        Music selected = list.getSelectedValue();
        if (selected == null && visibleTracks.getSize() > 0) selected = visibleTracks.getElementAt(0);
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

    private static final class TrackListModel extends AbstractListModel<Music> {
        private List<Music> items = List.of();

        @Override public int getSize() {
            return items.size();
        }

        @Override public Music getElementAt(int index) {
            return items.get(index);
        }

        void replaceAll(List<Music> replacement) {
            if (items.equals(replacement)) return;
            int oldSize = items.size();
            items = List.copyOf(replacement);
            int newSize = items.size();
            // 用区间事件批量替换可见曲目，避免逐首增删造成连续重绘。
            if (oldSize > 0) fireIntervalRemoved(this, 0, oldSize - 1);
            if (newSize > 0) fireIntervalAdded(this, 0, newSize - 1);
        }
    }
}