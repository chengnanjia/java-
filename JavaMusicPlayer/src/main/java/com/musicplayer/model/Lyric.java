package com.musicplayer.model;

/**
 * 歌词实体类，封装歌词内容和对应时间戳
 */
public class Lyric {
    // 歌词时间戳（单位：毫秒）
    private long time;
    // 歌词内容
    private String content;

    // 构造方法
    public Lyric(long time, String content) {
        this.time = time;
        this.content = content;
    }

    // Getter和Setter方法
    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}