package com.musicplayer.ui;

import com.musicplayer.model.Music;
import com.musicplayer.player.MusicPlayer;
import com.musicplayer.lyric.LyricManager;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MusicPlayerUI extends JFrame {
    private List<Music> musicList = new ArrayList<>();
    private MusicPlayer musicPlayer;
    private LyricManager lyricManager;
    private JList<Music> musicJList;
    private DefaultListModel<Music> listModel;
    private JButton playBtn;
    private JButton stopBtn;
    private JLabel lyricLabel;
    private String currentMusicPath;
    private String currentMusicName;

    public MusicPlayerUI() {
        initFrame();
        initComponents();
        initLayout();
        initListener();
        loadLocalMusic();
        // 初始化歌词管理器并绑定回调
        initLyricManager();
    }

    // 初始化歌词管理器，绑定更新回调
    private void initLyricManager() {
        lyricManager = new LyricManager();
        // 设置歌词更新回调，更新Swing标签（确保在EDT线程）
        lyricManager.setLyricUpdateCallback(lyricContent -> {
            SwingUtilities.invokeLater(() -> {
                lyricLabel.setText(lyricContent);
            });
        });
    }

    private void initFrame() {
        this.setTitle("Java音乐播放器");
        this.setSize(600, 400);
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setLocationRelativeTo(null);
    }

    private void initComponents() {
        listModel = new DefaultListModel<>();
        musicJList = new JList<>(listModel);
        musicJList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        playBtn = new JButton("播放");
        stopBtn = new JButton("停止");
        lyricLabel = new JLabel("暂无播放", SwingConstants.CENTER);
        lyricLabel.setFont(new Font("微软雅黑", Font.PLAIN, 18));
    }

    private void initLayout() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        this.add(mainPanel);
        JScrollPane musicScrollPane = new JScrollPane(musicJList);
        musicScrollPane.setPreferredSize(new Dimension(200, 300));
        mainPanel.add(musicScrollPane, BorderLayout.WEST);
        JPanel rightPanel = new JPanel(new BorderLayout());
        mainPanel.add(rightPanel, BorderLayout.CENTER);
        JPanel lyricPanel = new JPanel(new BorderLayout());
        lyricPanel.add(lyricLabel, BorderLayout.CENTER);
        rightPanel.add(lyricPanel, BorderLayout.CENTER);
        JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 10));
        btnPanel.add(playBtn);
        btnPanel.add(stopBtn);
        rightPanel.add(btnPanel, BorderLayout.SOUTH);
    }

    private void initListener() {
        playBtn.addActionListener(e -> {
            Music selectedMusic = musicJList.getSelectedValue();
            if (selectedMusic == null) {
                JOptionPane.showMessageDialog(this, "请先选择一首音乐！");
                return;
            }

            if (musicPlayer == null || !musicPlayer.isPlaying()) {
                // 记录当前音乐名称
                currentMusicName = selectedMusic.getName();
                currentMusicPath = selectedMusic.getPath();
                // 初始化播放器并播放
                musicPlayer = new MusicPlayer(currentMusicPath);
                musicPlayer.play();
                playBtn.setText("暂停");
                // 加载歌词 + 启动歌词滚动（同步音乐播放）
                loadLyric(currentMusicName);
                lyricManager.startLyricScroll();
            } else {
                // 暂停音乐 + 停止歌词滚动
                musicPlayer.pause();
                playBtn.setText("播放");
                lyricManager.stopLyricScroll();
                lyricLabel.setText("暂停播放");
            }
        });

        stopBtn.addActionListener(e -> {
            if (musicPlayer != null) {
                // 停止音乐 + 停止歌词滚动 + 清空歌词状态
                musicPlayer.stop();
                musicPlayer = null;
                playBtn.setText("播放");
                lyricManager.stopLyricScroll();
                lyricManager.clearLyric();
                lyricLabel.setText("暂无播放");
                currentMusicPath = null;
                currentMusicName = null;
            }
        });

        // 双击播放：同步启动歌词滚动
        musicJList.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    Music selectedMusic = musicJList.getSelectedValue();
                    if (selectedMusic != null) {
                        // 停止原有播放和歌词滚动
                        if (musicPlayer != null) {
                            musicPlayer.stop();
                        }
                        lyricManager.stopLyricScroll();

                        // 新建播放器并播放
                        currentMusicName = selectedMusic.getName();
                        currentMusicPath = selectedMusic.getPath();
                        musicPlayer = new MusicPlayer(currentMusicPath);
                        musicPlayer.play();
                        playBtn.setText("暂停");

                        // 加载歌词并启动滚动
                        loadLyric(currentMusicName);
                        lyricManager.startLyricScroll();
                    }
                }
            }
        });
    }

    private void loadLocalMusic() {
        String musicDirPath = System.getProperty("user.dir") + "/resources/music";
        File musicDir = new File(musicDirPath);
        // 自动创建音乐目录（若不存在）
        if (!musicDir.exists()) {
            musicDir.mkdirs();
            JOptionPane.showMessageDialog(this, "音乐目录已创建，请放入MP3文件！");
            return;
        }

        File[] musicFiles = musicDir.listFiles((dir, name) -> name.endsWith(".mp3"));
        if (musicFiles == null || musicFiles.length == 0) {
            JOptionPane.showMessageDialog(this, "音乐目录下无MP3文件！");
            return;
        }

        for (File file : musicFiles) {
            String musicName = file.getName().replace(".mp3", "");
            Music music = new Music(musicName, file.getAbsolutePath());
            musicList.add(music);
            listModel.addElement(music);
        }
    }

    private void loadLyric(String musicName) {
        String lyricDirPath = System.getProperty("user.dir") + "/resources/lyrics";
        File lyricDir = new File(lyricDirPath);
        // 自动创建歌词目录（若不存在）
        if (!lyricDir.exists()) {
            lyricDir.mkdirs();
        }

        File lyricFile = new File(lyricDirPath + "/" + musicName + ".lrc");
        if (lyricFile.exists()) {
            // 加载歌词
            lyricManager.loadLyric(lyricFile.getAbsolutePath());
        } else {
            // 无歌词文件：直接更新标签，无需引用未定义的callback
            lyricManager.clearLyric();
            SwingUtilities.invokeLater(() -> {
                lyricLabel.setText("无对应歌词文件");
            });
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            MusicPlayerUI playerUI = new MusicPlayerUI();
            playerUI.setVisible(true);
        });
    }
}
