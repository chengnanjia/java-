package com.musicplayer.player.output;

import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.JavaLayerException;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AdvancedPlayerAudioDeviceTest {
    @Test
    void opensPcmOutputOnlyAfterDecoderHasProducedFormat() throws JavaLayerException {
        int[] openedFormat = new int[2];
        PcmOutput[] outputHolder = new PcmOutput[1];
        RecordingSink sink = new RecordingSink();
        TestDecoder decoder = new TestDecoder();
        AdvancedPlayerAudioDevice device = new AdvancedPlayerAudioDevice(1.0, 0,
                (sampleRate, channels, volume, offset) -> {
                    openedFormat[0] = sampleRate;
                    openedFormat[1] = channels;
                    outputHolder[0] = new PcmOutput(sampleRate, channels, volume, offset,
                            sink);
                    return outputHolder[0];
                });

        device.open(decoder);
        assertTrue(device.isOpen());
        assertArrayEquals(new int[]{0, 0}, openedFormat);

        decoder.setFormat(44100, 2);
        device.write(new short[]{0x1234, (short) 0xfedc}, 0, 2);

        assertArrayEquals(new int[]{44100, 2}, openedFormat);
        assertArrayEquals(new byte[]{0x34, 0x12, (byte) 0xdc, (byte) 0xfe},
                sink.bytes);
        device.close();
        assertFalse(device.isOpen());
    }

    private static final class TestDecoder extends Decoder {
        private int sampleRate;
        private int channels;

        @Override public int getOutputFrequency() { return sampleRate; }
        @Override public int getOutputChannels() { return channels; }
        private void setFormat(int sampleRate, int channels) {
            this.sampleRate = sampleRate;
            this.channels = channels;
        }
    }

    private static final class RecordingSink implements PcmOutput.Sink {
        private byte[] bytes = new byte[0];

        @Override public void write(byte[] pcm, int offset, int length) {
            byte[] updated = new byte[bytes.length + length];
            System.arraycopy(bytes, 0, updated, 0, bytes.length);
            System.arraycopy(pcm, offset, updated, bytes.length, length);
            bytes = updated;
        }
        @Override public long positionMillis() { return 0; }
        @Override public void flush() { }
        @Override public void close() { }
    }
}
