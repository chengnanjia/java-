package com.musicplayer.player;

import com.musicplayer.player.decode.AudioDecoder;
import com.musicplayer.player.output.PcmOutput;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

public final class PcmPlaybackSession implements AutoCloseable, Runnable {
    private static final Logger LOGGER = Logger.getLogger(PcmPlaybackSession.class.getName());

    private final AudioDecoder decoder;
    private final PcmOutput output;
    private final LongConsumer onPosition;
    private final Runnable onEnd;
    private final Consumer<Exception> onError;
    private final AtomicBoolean stopped = new AtomicBoolean();
    private final AtomicBoolean resourcesClosed = new AtomicBoolean();
    private volatile Thread thread;

    public PcmPlaybackSession(AudioDecoder decoder, PcmOutput output,
                              LongConsumer onPosition, Runnable onEnd,
                              Consumer<Exception> onError) {
        this.decoder = decoder;
        this.output = output;
        this.onPosition = onPosition;
        this.onEnd = onEnd;
        this.onError = onError;
    }

    public synchronized void start() {
        if (thread != null) throw new IllegalStateException("PCM 播放会话已经启动");
        thread = new Thread(this, "music-pcm-playback");
        thread.setDaemon(true);
        thread.start();
    }

    public long getPositionMillis() {
        return output.getPositionMillis();
    }

    public void setVolume(double volume) {
        output.setVolume(volume);
    }

    @Override
    public void run() {
        boolean completed = false;
        IOException failure = null;
        try {
            int frameSize = decoder.channels() * 2;
            byte[] buffer = new byte[Math.max(frameSize, 8192 / frameSize * frameSize)];
            while (!stopped.get()) {
                int count = decoder.read(buffer);
                if (count < 0) {
                    completed = true;
                    break;
                }
                if (count == 0) continue;
                output.write(buffer, 0, count);
                onPosition.accept(output.getPositionMillis());
            }
        } catch (IOException exception) {
            failure = exception;
        } finally {
            IOException closeFailure = closeResources();
            if (failure == null) failure = closeFailure;
            if (failure != null && !stopped.get()) {
                stopped.set(true);
                onError.accept(failure);
            }
            if (completed && !stopped.get()) onEnd.run();
        }
    }

    @Override
    public void close() {
        stopped.set(true);
        IOException closeFailure = closeResources();
        if (closeFailure != null) {
            LOGGER.log(Level.WARNING, "关闭 PCM 解码器失败", closeFailure);
        }
        Thread activeThread = thread;
        if (activeThread != null) activeThread.interrupt();
    }

    private IOException closeResources() {
        if (!resourcesClosed.compareAndSet(false, true)) return null;
        output.close();
        try {
            decoder.close();
        } catch (IOException exception) {
            return exception;
        }
        return null;
    }
}
