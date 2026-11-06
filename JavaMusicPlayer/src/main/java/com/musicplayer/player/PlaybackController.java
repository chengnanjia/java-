package com.musicplayer.player;

import com.mpatric.mp3agic.Mp3File;
import com.musicplayer.model.Music;
import com.musicplayer.player.output.AdvancedPlayerAudioDevice;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.advanced.AdvancedPlayer;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PlaybackController {
    public interface Listener {
        void onReady(long durationMillis);
        void onPosition(long positionMillis);
        void onEnd();
        void onError(String message);
    }

    private final Object lock = new Object();
    private final Listener listener;
    private final ScheduledExecutorService positionTimer =
            Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, "music-position-updater");
                thread.setDaemon(true);
                return thread;
            });

    private Music currentMusic;
    private AdvancedPlayer activePlayer;
    private AdvancedPlayerAudioDevice activeDevice;
    private long durationMillis;
    private long positionMillis;
    private int frameCount;
    private long generation;
    private boolean paused;
    private volatile double volume = 0.72;

    public PlaybackController(Listener listener) {
        this.listener = listener;
        positionTimer.scheduleAtFixedRate(this::reportPosition, 0, 200,
                TimeUnit.MILLISECONDS);
    }

    public void play(Music music) {
        final long token;
        synchronized (lock) {
            stopActiveLocked();
            token = ++generation;
            currentMusic = music;
            durationMillis = 0;
            positionMillis = 0;
            frameCount = 0;
            paused = false;
        }
        Thread loader = new Thread(() -> prepareTrack(token, music), "music-track-loader");
        loader.setDaemon(true);
        loader.start();
    }

    private void prepareTrack(long token, Music music) {
        try {
            Mp3File mp3File = new Mp3File(music.getPath());
            synchronized (lock) {
                if (token != generation) return;
                durationMillis = mp3File.getLengthInMilliseconds();
                frameCount = mp3File.getFrameCount();
            }
            listener.onReady(durationMillis);
            synchronized (lock) {
                if (token != generation || paused) return;
            }
            startSession(token, music, 0, 0);
        } catch (Exception exception) {
            reportError(token, exception);
        }
    }

    private void startSession(long token, Music music, int startFrame, long startOffsetMillis) {
        try {
            AdvancedPlayerAudioDevice device =
                    new AdvancedPlayerAudioDevice(volume, startOffsetMillis);
            AdvancedPlayer player = new AdvancedPlayer(
                    new BufferedInputStream(new FileInputStream(music.getPath())), device);
            synchronized (lock) {
                if (token != generation || paused) {
                    player.close();
                    return;
                }
                activePlayer = player;
                activeDevice = device;
            }
            Thread playback = new Thread(() -> {
                boolean completed = false;
                try {
                    completed = player.play(startFrame, frameCount);
                } catch (JavaLayerException exception) {
                    reportError(token, exception);
                } finally {
                    player.close();
                    synchronized (lock) {
                        if (activePlayer == player) {
                            activePlayer = null;
                            activeDevice = null;
                        }
                        if (token == generation && !paused && completed) {
                            positionMillis = durationMillis;
                        } else {
                            completed = false;
                        }
                    }
                    if (completed) {
                        listener.onPosition(durationMillis);
                        listener.onEnd();
                    }
                }
            }, "music-playback");
            playback.setDaemon(true);
            playback.start();
        } catch (IOException | JavaLayerException exception) {
            reportError(token, exception);
        }
    }

    public void togglePause() {
        Music music;
        long token;
        int frame;
        boolean resume;
        synchronized (lock) {
            if (currentMusic == null) return;
            resume = paused;
            if (resume) {
                paused = false;
                token = ++generation;
                music = currentMusic;
                frame = frameAt(positionMillis);
            } else {
                if (activeDevice != null) positionMillis = activeDevice.getPositionMillis();
                paused = true;
                ++generation;
                stopActiveLocked();
                music = currentMusic;
                token = generation;
                frame = 0;
            }
        }
        if (resume) {
            if (frameCount == 0) prepareTrack(token, music);
            else startSession(token, music, frame, positionMillis);
        } else {
            listener.onPosition(positionMillis);
        }
    }

    public void stop() {
        synchronized (lock) {
            ++generation;
            stopActiveLocked();
            currentMusic = null;
            paused = false;
            positionMillis = 0;
        }
        listener.onPosition(0);
    }

    public void seek(long requestedPositionMillis) {
        Music music;
        long token;
        int frame;
        boolean restart;
        synchronized (lock) {
            if (currentMusic == null || durationMillis <= 0 || frameCount <= 0) return;
            positionMillis = Math.max(0, Math.min(durationMillis, requestedPositionMillis));
            frame = frameAt(positionMillis);
            music = currentMusic;
            restart = !paused;
            if (restart) {
                ++generation;
                token = generation;
                stopActiveLocked();
            } else {
                token = generation;
            }
        }
        listener.onPosition(positionMillis);
        if (restart) startSession(token, music, frame, positionMillis);
    }

    public void setVolume(double volume) {
        this.volume = Math.max(0, Math.min(1, volume));
        synchronized (lock) {
            if (activeDevice != null) activeDevice.setVolume(this.volume);
        }
    }

    public void shutdown() {
        stop();
        positionTimer.shutdownNow();
    }

    private void reportPosition() {
        long position;
        synchronized (lock) {
            if (paused) return;
            if (activeDevice != null) positionMillis = activeDevice.getPositionMillis();
            position = positionMillis;
        }
        listener.onPosition(position);
    }

    private int frameAt(long millis) {
        if (durationMillis <= 0 || frameCount <= 0) return 0;
        return (int) Math.min(frameCount - 1,
                millis * frameCount / durationMillis);
    }

    private void stopActiveLocked() {
        if (activePlayer != null) {
            activePlayer.close();
            activePlayer = null;
            activeDevice = null;
        }
    }

    private void reportError(long token, Exception exception) {
        synchronized (lock) {
            if (token != generation) return;
        }
        String message = exception.getMessage();
        listener.onError(message == null || message.trim().isEmpty()
                ? "无法播放该 MP3 文件" : message);
    }

}