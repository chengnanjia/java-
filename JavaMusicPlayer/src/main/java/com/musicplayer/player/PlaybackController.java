package com.musicplayer.player;

import com.mpatric.mp3agic.Mp3File;
import com.musicplayer.model.Music;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.advanced.AdvancedPlayer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
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
    private PcmAudioDevice activeDevice;
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
            startSession(token, music, 0);
        } catch (Exception exception) {
            reportError(token, exception);
        }
    }

    private void startSession(long token, Music music, int startFrame) {
        try {
            PcmAudioDevice device = new PcmAudioDevice(volume);
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
            else startSession(token, music, frame);
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
        if (restart) startSession(token, music, frame);
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

    private static final class PcmAudioDevice implements AudioDevice {
        private volatile SourceDataLine line;
        private volatile Process pulseProcess;
        private volatile OutputStream pulseOutput;
        private volatile double volume;
        private volatile long lastPositionMillis;
        private volatile long bytesWritten;
        private volatile Decoder decoder;
        private int sampleRate;
        private int channels;

        private PcmAudioDevice(double volume) {
            this.volume = volume;
        }

        @Override
        public void open(Decoder decoder) throws JavaLayerException {
            this.decoder = decoder;
        }

        private void openOutput() throws JavaLayerException {
            Decoder currentDecoder = decoder;
            if (currentDecoder == null) throw new JavaLayerException("MP3 解码器尚未就绪");
            sampleRate = currentDecoder.getOutputFrequency();
            channels = currentDecoder.getOutputChannels();
            if (sampleRate <= 0 || channels <= 0) {
                throw new JavaLayerException("无法读取 MP3 音频格式");
            }
            AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
            DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
            SourceDataLine candidate = null;
            try {
                candidate = (SourceDataLine) AudioSystem.getLine(info);
                candidate.open(format);
                candidate.start();
                line = candidate;
                return;
            } catch (LineUnavailableException | IllegalArgumentException exception) {
                if (candidate != null) candidate.close();
                try {
                    pulseProcess = new ProcessBuilder("paplay", "--playback", "--raw",
                            "--format=s16le", "--rate=" + sampleRate,
                            "--channels=" + channels, "--latency-msec=100",
                            "--process-time-msec=50")
                            .redirectError(ProcessBuilder.Redirect.INHERIT)
                            .start();
                    pulseOutput = pulseProcess.getOutputStream();
                } catch (IOException pulseException) {
                    pulseException.addSuppressed(exception);
                    throw new JavaLayerException(
                            "无法打开系统音频输出设备，也无法连接 PulseAudio（paplay）",
                            pulseException);
                }
            }
        }

        @Override
        public boolean isOpen() {
            if (line != null) return line.isOpen();
            Process process = pulseProcess;
            if (process == null) return false;
            try {
                process.exitValue();
                return false;
            } catch (IllegalThreadStateException stillRunning) {
                return true;
            }
        }

        @Override
        public void write(short[] samples, int offset, int length) throws JavaLayerException {
            if (line == null && pulseOutput == null) openOutput();
            SourceDataLine currentLine = line;
            OutputStream currentPulseOutput = pulseOutput;
            if (currentLine == null && currentPulseOutput == null) {
                throw new JavaLayerException("音频输出设备尚未打开");
            }
            byte[] pcm = new byte[length * 2];
            double gain = volume;
            for (int index = 0; index < length; index++) {
                int sample = (int) (samples[offset + index] * gain);
                pcm[index * 2] = (byte) sample;
                pcm[index * 2 + 1] = (byte) (sample >> 8);
            }
            if (currentLine != null) {
                currentLine.write(pcm, 0, pcm.length);
                bytesWritten += pcm.length;
            } else {
                try {
                    currentPulseOutput.write(pcm);
                    currentPulseOutput.flush();
                    bytesWritten += pcm.length;
                } catch (IOException exception) {
                    throw new JavaLayerException("PulseAudio 音频输出失败："
                            + exception.getMessage(), exception);
                }
            }
        }

        @Override
        public void close() {
            lastPositionMillis = getPositionMillis();
            SourceDataLine currentLine = line;
            if (currentLine != null) {
                currentLine.stop();
                currentLine.flush();
                currentLine.close();
                line = null;
            }
            OutputStream currentPulseOutput = pulseOutput;
            Process currentPulseProcess = pulseProcess;
            pulseOutput = null;
            pulseProcess = null;
            if (currentPulseOutput != null) {
                try {
                    currentPulseOutput.close();
                } catch (IOException ignored) {
                    // Closing the stream also stops paplay during pause or stop.
                }
            }
            if (currentPulseProcess != null) currentPulseProcess.destroy();
        }

        @Override
        public void flush() {
            SourceDataLine currentLine = line;
            if (currentLine != null) currentLine.flush();
            else if (pulseOutput != null) {
                try {
                    pulseOutput.flush();
                } catch (IOException ignored) {
                    // The next write reports a disconnected PulseAudio stream.
                }
            }
        }

        @Override
        public int getPosition() {
            return (int) getPositionMillis();
        }

        private long getPositionMillis() {
            SourceDataLine currentLine = line;
            if (currentLine != null) return currentLine.getMicrosecondPosition() / 1000;
            if (sampleRate > 0 && channels > 0) {
                return bytesWritten * 1000 / (sampleRate * channels * 2L);
            }
            return lastPositionMillis;
        }

        private void setVolume(double volume) {
            this.volume = volume;
        }
    }
}