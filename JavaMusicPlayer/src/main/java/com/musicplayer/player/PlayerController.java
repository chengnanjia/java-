package com.musicplayer.player;

import com.musicplayer.model.Music;

import javax.swing.SwingUtilities;

public final class PlayerController {
    public interface Listener {
        void onReady(Music music, long durationMillis);
        void onPosition(Music music, long positionMillis);
        void onTrackEnded(Music music);
        void onError(Music music, String message);
    }

    private final PlaybackController playback;
    private volatile Music currentMusic;

    public PlayerController(Listener listener) {
        playback = new PlaybackController(new PlaybackController.Listener() {
            @Override public void onReady(long durationMillis) {
                Music music = currentMusic;
                dispatch(() -> listener.onReady(music, durationMillis));
            }
            @Override public void onPosition(long positionMillis) {
                Music music = currentMusic;
                dispatch(() -> listener.onPosition(music, positionMillis));
            }
            @Override public void onEnd() {
                Music music = currentMusic;
                dispatch(() -> listener.onTrackEnded(music));
            }
            @Override public void onError(String message) {
                Music music = currentMusic;
                dispatch(() -> listener.onError(music, message));
            }
        });
    }

    public void play(Music music) {
        currentMusic = music;
        playback.play(music);
    }

    public void togglePause() {
        playback.togglePause();
    }

    public void stop() {
        playback.stop();
    }

    public void seek(long positionMillis) {
        playback.seek(positionMillis);
    }

    public void setVolume(double volume) {
        playback.setVolume(volume);
    }

    public Music getCurrentMusic() {
        return currentMusic;
    }

    public void shutdown() {
        playback.shutdown();
        currentMusic = null;
    }

    private static void dispatch(Runnable action) {
        if (SwingUtilities.isEventDispatchThread()) action.run();
        else SwingUtilities.invokeLater(action);
    }
}