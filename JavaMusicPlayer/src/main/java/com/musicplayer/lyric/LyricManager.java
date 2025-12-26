package com.musicplayer.lyric;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class LyricManager {
    // 歌词列表（存储时间和内容）
    private List<LyricItem> lyricList = new ArrayList<>();
    // 滚动线程
    private Thread lyricScrollThread;
    // 当前显示的歌词索引
    private int currentLyricIndex = 0;
    // 滚动开关
    private boolean isScrolling = false;
    // 歌词更新回调（给UI传递歌词）
    private LyricUpdateCallback callback;

    // 歌词更新回调接口
    public interface LyricUpdateCallback {
        void onLyricUpdate(String lyricContent);
    }

    // 设置回调
    public void setLyricUpdateCallback(LyricUpdateCallback callback) {
        this.callback = callback;
    }

    // 加载歌词
    public void loadLyric(String lyricPath) {
        clearLyric();
        try (BufferedReader br = new BufferedReader(new FileReader(lyricPath))) {
            String line;
            while ((line = br.readLine()) != null) {
                // 只解析核心时间标签 [mm:ss.SS]
                if (line.startsWith("[") && line.contains("]")) {
                    int timeEndIndex = line.indexOf("]");
                    String timeStr = line.substring(1, timeEndIndex);
                    String lyricContent = line.substring(timeEndIndex + 1).trim();
                    // 转换为毫秒数（更精准）
                    long lyricTimeMs = parseTimeToMillisecond(timeStr);
                    if (lyricTimeMs > 0) {
                        lyricList.add(new LyricItem(lyricTimeMs, lyricContent));
                    }
                }
            }
            // 按时间正序排序，确保滚动顺序正确
            Collections.sort(lyricList, Comparator.comparingLong(LyricItem::getLyricTimeMs));
        } catch (IOException e) {
            e.printStackTrace();
            if (callback != null) {
                callback.onLyricUpdate("歌词文件读取失败");
            }
        }
    }

    // 解析 [mm:ss.SS] 为毫秒数
    private long parseTimeToMillisecond(String timeStr) {
        String[] parts = timeStr.split(":");
        if (parts.length != 2) {
            return 0;
        }
        try {
            int minute = Integer.parseInt(parts[0]);
            String[] secondParts = parts[1].split("\\.");
            int second = Integer.parseInt(secondParts[0]);
            int millisecond = secondParts.length > 1 ? Integer.parseInt(secondParts[1]) : 0;
            // 转换为总毫秒数
            return (long) minute * 60 * 1000 + (long) second * 1000 + millisecond;
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // 启动歌词滚动（与音乐播放同步开始）
    public void startLyricScroll() {
        // 先停止原有线程
        stopLyricScroll();
        isScrolling = true;
        currentLyricIndex = 0;
        // 启动新的滚动线程
        lyricScrollThread = new Thread(() -> {
            long startTime = System.currentTimeMillis();
            while (isScrolling && currentLyricIndex < lyricList.size()) {
                long currentTime = System.currentTimeMillis();
                long elapsedTime = currentTime - startTime; // 已播放毫秒数

                // 匹配当前时间对应的歌词
                if (elapsedTime >= lyricList.get(currentLyricIndex).getLyricTimeMs()) {
                    // 回调更新UI歌词
                    if (callback != null) {
                        String lyric = lyricList.get(currentLyricIndex).getLyricContent();
                        callback.onLyricUpdate(lyric.isEmpty() ? "暂无歌词" : lyric);
                    }
                    currentLyricIndex++;
                }

                // 降低循环频率，减少资源占用
                try {
                    Thread.sleep(50); // 每50毫秒检查一次，更精准
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        lyricScrollThread.start();
    }

    // 停止歌词滚动（与音乐暂停/停止同步）
    public void stopLyricScroll() {
        isScrolling = false;
        if (lyricScrollThread != null && lyricScrollThread.isAlive()) {
            lyricScrollThread.interrupt();
        }
        currentLyricIndex = 0;
    }

    // 清空歌词
    public void clearLyric() {
        stopLyricScroll();
        lyricList.clear();
        currentLyricIndex = 0;
        if (callback != null) {
            callback.onLyricUpdate("暂无歌词");
        }
    }

    // 歌词项内部类
    private class LyricItem {
        private long lyricTimeMs; // 歌词对应的总毫秒数
        private String lyricContent; // 歌词内容

        public LyricItem(long lyricTimeMs, String lyricContent) {
            this.lyricTimeMs = lyricTimeMs;
            this.lyricContent = lyricContent;
        }

        public long getLyricTimeMs() {
            return lyricTimeMs;
        }

        public String getLyricContent() {
            return lyricContent;
        }
    }
}
