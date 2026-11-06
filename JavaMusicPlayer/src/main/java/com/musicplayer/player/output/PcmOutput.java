package com.musicplayer.player.output;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public final class PcmOutput implements AutoCloseable {
    interface Sink {
        void write(byte[] pcm, int offset, int length) throws IOException;
        long positionMillis();
        void flush();
        void close();
    }

    private final long startOffsetMillis;
    private final Sink sink;
    private volatile double volume;
    private long closedPositionMillis;
    private boolean closed;

    public PcmOutput(int sampleRate, int channels, double volume, long startOffsetMillis)
            throws IOException {
        this(sampleRate, channels, volume, startOffsetMillis,
                createSink(sampleRate, channels));
    }

    PcmOutput(int sampleRate, int channels, double volume, long startOffsetMillis, Sink sink) {
        if (sampleRate <= 0 || channels <= 0) {
            throw new IllegalArgumentException("采样率和声道数必须大于 0");
        }
        this.volume = clampVolume(volume);
        this.startOffsetMillis = Math.max(0, startOffsetMillis);
        this.sink = sink;
    }

    public synchronized void write(byte[] pcm, int offset, int length) throws IOException {
        if (closed) throw new IOException("PCM 输出已关闭");
        if (offset < 0 || length < 0 || offset + length > pcm.length) {
            throw new IndexOutOfBoundsException("PCM 数据范围无效");
        }
        byte[] adjusted = new byte[length];
        double gain = volume;
        for (int index = 0; index + 1 < length; index += 2) {
            int sample = (short) ((pcm[offset + index] & 0xff)
                    | (pcm[offset + index + 1] << 8));
            int adjustedSample = (int) (sample * gain);
            adjusted[index] = (byte) adjustedSample;
            adjusted[index + 1] = (byte) (adjustedSample >> 8);
        }
        if ((length & 1) != 0) adjusted[length - 1] = pcm[offset + length - 1];
        sink.write(adjusted, 0, adjusted.length);
    }

    public synchronized void flush() {
        if (!closed) sink.flush();
    }

    public synchronized long getPositionMillis() {
        return closed ? closedPositionMillis
                : startOffsetMillis + Math.max(0, sink.positionMillis());
    }

    public synchronized boolean isOpen() {
        return !closed;
    }

    public void setVolume(double volume) {
        this.volume = clampVolume(volume);
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closedPositionMillis = startOffsetMillis + Math.max(0, sink.positionMillis());
        closed = true;
        sink.close();
    }

    private static double clampVolume(double volume) {
        return Math.max(0, Math.min(1, volume));
    }

    private static Sink createSink(int sampleRate, int channels) throws IOException {
        AudioFormat format = new AudioFormat(sampleRate, 16, channels, true, false);
        DataLine.Info info = new DataLine.Info(SourceDataLine.class, format);
        List<Exception> lineFailures = new ArrayList<>();

        try {
            return new LineSink(openLine(AudioSystem.getMixer(null), info, format));
        } catch (LineUnavailableException | IllegalArgumentException exception) {
            lineFailures.add(exception);
        }

        // 默认混音器不一定是输出设备；尝试所有系统提供的音频混音器。
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            try {
                return new LineSink(openLine(AudioSystem.getMixer(mixerInfo), info, format));
            } catch (LineUnavailableException | IllegalArgumentException exception) {
                lineFailures.add(exception);
            }
        }

        IOException lineFailure = new IOException(
                describeLineFailures(lineFailures),
                lineFailures.isEmpty() ? null : lineFailures.get(0));
        for (int index = 1; index < lineFailures.size(); index++) {
            lineFailure.addSuppressed(lineFailures.get(index));
        }

        Process process = null;
        try {
            process = new ProcessBuilder("paplay", "--playback", "--raw",
                    "--format=s16le", "--rate=" + sampleRate,
                    "--channels=" + channels, "--latency-msec=100",
                    "--process-time-msec=50")
                    .redirectError(ProcessBuilder.Redirect.INHERIT)
                    .start();
            if (process.waitFor(200, TimeUnit.MILLISECONDS)) {
                throw new IOException("paplay 启动后立即退出，退出码：" + process.exitValue());
            }
            return new PulseSink(process, sampleRate, channels);
        } catch (IOException | InterruptedException pulseException) {
            if (process != null) process.destroyForcibly();
            if (pulseException instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            IOException failure = new IOException(
                    "无法打开系统音频输出。请检查音频设备/服务；Linux 可安装 paplay 并确认 PulseAudio 或 PipeWire 正在运行。"
                            + " Java Sound：" + lineFailure.getMessage()
                            + "；paplay：" + pulseException.getMessage(),
                    pulseException);
            failure.addSuppressed(lineFailure);
            throw failure;
        }
    }

    private static SourceDataLine openLine(Mixer mixer, DataLine.Info info, AudioFormat format)
            throws LineUnavailableException {
        SourceDataLine line = (SourceDataLine) mixer.getLine(info);
        try {
            line.open(format);
            line.start();
            return line;
        } catch (LineUnavailableException | IllegalArgumentException exception) {
            line.close();
            throw exception;
        }
    }

    private static String describeLineFailures(List<Exception> failures) {
        if (failures.isEmpty()) return "没有发现可用的 Java Sound 混音器";
        Exception lastFailure = failures.get(failures.size() - 1);
        String message = lastFailure.getMessage();
        return "未能打开 Java Sound 输出线路（尝试 " + failures.size() + " 次）："
                + (message == null || message.isBlank()
                ? lastFailure.getClass().getSimpleName() : message);
    }

    private static final class LineSink implements Sink {
        private final SourceDataLine line;

        private LineSink(SourceDataLine line) {
            this.line = line;
        }

        @Override public void write(byte[] pcm, int offset, int length) {
            line.write(pcm, offset, length);
        }
        @Override public long positionMillis() { return line.getMicrosecondPosition() / 1000; }
        @Override public void flush() { line.flush(); }
        @Override public void close() {
            line.stop();
            line.flush();
            line.close();
        }
    }

    private static final class PulseSink implements Sink {
        private final Process process;
        private final OutputStream output;
        private final int bytesPerSecond;
        private long bytesWritten;

        private PulseSink(Process process, int sampleRate, int channels) {
            this.process = process;
            this.output = process.getOutputStream();
            this.bytesPerSecond = sampleRate * channels * 2;
        }

        @Override public synchronized void write(byte[] pcm, int offset, int length) throws IOException {
            output.write(pcm, offset, length);
            output.flush();
            bytesWritten += length;
        }
        @Override public synchronized long positionMillis() { return bytesWritten * 1000 / bytesPerSecond; }
        @Override public void flush() {
            try {
                output.flush();
            } catch (IOException exception) {
                throw new IllegalStateException("PulseAudio 音频输出失败", exception);
            }
        }
        @Override public void close() {
            try {
                output.close();
            } catch (IOException ignored) {
                // 关闭输入流会通知 paplay 停止消费数据。
            } finally {
                process.destroy();
            }
        }
    }
}
