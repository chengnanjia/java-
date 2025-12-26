package com.musicplayer.model;

public class Music {
    private String name; // 音乐名
    private String path; // 音乐文件路径

    // 双参数构造器（适配UI中的 new Music(String, String) 调用）
    public Music(String name, String path) {
        this.name = name;
        this.path = path;
    }

    // 获取音乐名（适配UI中的 getName() 调用）
    public String getName() {
        return this.name;
    }

    // 获取音乐路径（适配UI中的 getPath() 调用）
    public String getPath() {
        return this.path;
    }

    // 重写toString，使JList显示音乐名
    @Override
    public String toString() {
        return this.name + " - 未知歌手";
    }
}
