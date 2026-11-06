package com.musicplayer.player.output;

import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.player.AudioDevice;

import java.io.IOException;

public final class AdvancedPlayerAudioDevice implements AudioDevice {
    private final long startOffsetMillis;
    private volatile double volume;
    private volatile PcmOutput output;

    public AdvancedPlayerAudioDevice(double volume, long startOffsetMillis) {
        this.volume = Math.max(0, Math.min(1, volume));
        this.startOffsetMillis = Math.max(0, startOffsetMillis);
    }

    @Override
    public void open(Decoder decoder) throws JavaLayerException {
        try {
            output = new PcmOutput(decoder.getOutputFrequency(), decoder.getOutputChannels(),
                    volume, startOffsetMillis);
        } catch (IOException | IllegalArgumentException exception) {
            throw new JavaLayerException("无法打开 PCM 音频输出："
                    + exception.getMessage(), exception);
        }
    }

    @Override public boolean isOpen() {
        PcmOutput current = output;
        return current != null && current.isOpen();
    }

    @Override
    public void write(short[] samples, int offset, int length) throws JavaLayerException {
        PcmOutput current = output;
        if (current == null) throw new JavaLayerException("PCM 音频输出尚未打开");
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

    @Override public void close() {
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
