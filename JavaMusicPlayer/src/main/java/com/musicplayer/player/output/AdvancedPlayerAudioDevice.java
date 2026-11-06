package com.musicplayer.player.output;

import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;

import java.io.IOException;

public final class AdvancedPlayerAudioDevice implements AudioDevice {
    @FunctionalInterface
    interface OutputFactory {
        PcmOutput create(int sampleRate, int channels, double volume, long startOffsetMillis)
                throws IOException;
    }

    private final long startOffsetMillis;
    private final OutputFactory outputFactory;
    private volatile double volume;
    private volatile Decoder decoder;
    private volatile PcmOutput output;
    private volatile boolean closed;

    public AdvancedPlayerAudioDevice(double volume, long startOffsetMillis) {
        this(volume, startOffsetMillis, PcmOutput::new);
    }

    AdvancedPlayerAudioDevice(double volume, long startOffsetMillis, OutputFactory outputFactory) {
        this.volume = Math.max(0, Math.min(1, volume));
        this.startOffsetMillis = Math.max(0, startOffsetMillis);
        this.outputFactory = outputFactory;
    }

    @Override
    public void open(Decoder decoder) throws JavaLayerException {
        // JLayer 此时尚未解码首帧，采样率和声道数仍为 0；输出线路延迟到首次写入时打开。
        this.decoder = decoder;
        output = null;
        closed = false;
    }

    @Override public boolean isOpen() {
        return decoder != null && !closed;
    }

    @Override
    public void write(short[] samples, int offset, int length) throws JavaLayerException {
        PcmOutput current = getOrOpenOutput();
        byte[] pcm = new byte[length * 2];
        for (int index = 0; index < length; index++) {
            short sample = samples[offset + index];
            pcm[index * 2] = (byte) sample;
            pcm[index * 2 + 1] = (byte) (sample >> 8);
        }
        try {
            // JLayer 输出为 short[]；PCM 输出层统一接收小端 16 位字节。
            current.write(pcm, 0, pcm.length);
        } catch (IOException exception) {
            throw new JavaLayerException("PCM 音频输出失败：" + exception.getMessage(), exception);
        }
    }

    private synchronized PcmOutput getOrOpenOutput() throws JavaLayerException {
        if (closed) throw new JavaLayerException("PCM 音频输出已关闭");
        if (output != null) return output;
        Decoder activeDecoder = decoder;
        if (activeDecoder == null) {
            throw new JavaLayerException("JLayer 解码器尚未打开");
        }
        int sampleRate = activeDecoder.getOutputFrequency();
        int channels = activeDecoder.getOutputChannels();
        if (sampleRate <= 0 || channels <= 0) {
            throw new JavaLayerException("JLayer 尚未提供有效的 PCM 采样率和声道数");
        }
        try {
            output = outputFactory.create(sampleRate, channels, volume, startOffsetMillis);
            return output;
        } catch (IOException | IllegalArgumentException exception) {
            throw new JavaLayerException("无法打开 PCM 音频输出："
                    + exception.getMessage(), exception);
        }
    }

    @Override
    public void close() {
        closed = true;
        decoder = null;
        PcmOutput current = output;
        if (current != null) current.close();
    }

    @Override public void flush() {
        PcmOutput current = output;
        if (current != null) current.flush();
    }

    @Override public int getPosition() { return (int) getPositionMillis(); }

    public long getPositionMillis() {
        PcmOutput current = output;
        return current == null ? startOffsetMillis : current.getPositionMillis();
    }

    public void setVolume(double volume) {
        this.volume = Math.max(0, Math.min(1, volume));
        PcmOutput current = output;
        if (current != null) current.setVolume(this.volume);
    }
}
