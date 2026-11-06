package com.musicplayer.player.output;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class PcmOutputTest {
    @Test
    void positionContinuesAcrossPauseAndResume() throws IOException {
        FakeSink firstSink = new FakeSink(1000, 1);
        PcmOutput first = new PcmOutput(1000, 1, 1.0, 0, firstSink);
        first.write(new byte[2000], 0, 2000);
        long pausedAt = first.getPositionMillis();
        first.close();

        FakeSink resumedSink = new FakeSink(1000, 1);
        PcmOutput resumed = new PcmOutput(1000, 1, 1.0, pausedAt, resumedSink);
        resumed.write(new byte[1000], 0, 1000);

        assertEquals(1500, resumed.getPositionMillis());
    }

    @Test
    void seekOffsetIsIncludedInAbsolutePosition() throws IOException {
        PcmOutput output = new PcmOutput(1000, 1, 1.0, 5000,
                new FakeSink(1000, 1));
        output.write(new byte[1000], 0, 1000);

        assertEquals(5500, output.getPositionMillis());
    }

    @Test
    void softwareVolumeScalesSignedLittleEndianSamples() throws IOException {
        FakeSink sink = new FakeSink(1000, 1);
        PcmOutput output = new PcmOutput(1000, 1, 0.5, 0, sink);
        byte[] samples = { (byte) 0x00, (byte) 0x40, 0x00, (byte) 0xc0 };
        output.write(samples, 0, samples.length);

        assertArrayEquals(new byte[]{0, 0x20, 0, (byte) 0xe0}, sink.bytes);
    }

    private static final class FakeSink implements PcmOutput.Sink {
        private final int bytesPerSecond;
        private byte[] bytes = new byte[0];

        private FakeSink(int sampleRate, int channels) {
            bytesPerSecond = sampleRate * channels * 2;
        }

        @Override
        public void write(byte[] pcm, int offset, int length) {
            byte[] updated = new byte[bytes.length + length];
            System.arraycopy(bytes, 0, updated, 0, bytes.length);
            System.arraycopy(pcm, offset, updated, bytes.length, length);
            bytes = updated;
        }

        @Override public long positionMillis() { return bytes.length * 1000L / bytesPerSecond; }
        @Override public void flush() { }
        @Override public void close() { }
    }
}
