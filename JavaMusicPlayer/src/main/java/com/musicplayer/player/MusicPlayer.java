package com.musicplayer.player;

import java.io.FileInputStream;
import java.io.IOException;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.Player;

public class MusicPlayer {
    private Player player;
    private FileInputStream fis;
    private boolean isPlaying;

    public MusicPlayer(String filePath) {
        try {
            fis = new FileInputStream(filePath);
            player = new Player(fis);
            isPlaying = false;
        } catch (JavaLayerException | IOException e) {
            e.printStackTrace();
        }
    }

    public void play() {
        if (player == null) return;
        isPlaying = true;
        new Thread(() -> {
            try {
                player.play();
                isPlaying = false; // 播放结束后更新状态
            } catch (JavaLayerException e) {
                e.printStackTrace();
                isPlaying = false;
            }
        }).start();
    }

    public void pause() {
        isPlaying = false;
        if (player != null) {
            try {
                fis.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            player = null;
        }
    }

    public void stop() {
        pause();
    }

    public boolean isPlaying() {
        return isPlaying;
    }
}
