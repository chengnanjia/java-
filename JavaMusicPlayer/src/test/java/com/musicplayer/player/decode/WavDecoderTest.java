package com.musicplayer.player.decode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WavDecoderTest {
    @TempDir Path tempDir;

    @Test
    void decodesPcmAtMultipleRatesDepthsAndChannelCounts() throws Exception {
        int[][] formats = {
                { 8000, 8, 1 },
                { 44100, 16, 2 },
                { 22050, 24, 1 },
                { 48000, 32, 2 }
        };
        for (int[] format : formats) {
            int frames = format[0] / 10;
            Path file = writePcmWave(format[0], format[1], format[2], frames);
            WavDecoder decoder = new WavDecoder();
            decoder.open(file);
            assertEquals(100, decoder.durationMillis());
            assertEquals(format[0], decoder.sampleRate());
            assertEquals(format[2], decoder.channels());
            byte[] pcm = new byte[frames * format[2] * 2];
            assertEquals(pcm.length, decoder.read(pcm));
            assertEquals(-1, decoder.read(new byte[format[2] * 2]));
            decoder.close();
        }
    }

    @Test
    void seekProducesSamePcmAsContinuousDecode() throws Exception {
        int sampleRate = 16000;
        int channels = 2;
        int frames = sampleRate;
        Path file = writePcmWave(sampleRate, 16, channels, frames);
        byte[] complete = new byte[frames * channels * 2];
        WavDecoder full = new WavDecoder();
        full.open(file);
        assertEquals(complete.length, full.read(complete));

        WavDecoder seeked = new WavDecoder();
        seeked.open(file);
        seeked.seekTo(250);
        byte[] afterSeek = new byte[512];
        assertEquals(afterSeek.length, seeked.read(afterSeek));
        int offset = 250 * sampleRate / 1000 * channels * 2;
        for (int index = 0; index < afterSeek.length; index++) {
            assertEquals(complete[offset + index], afterSeek[index]);
        }
        assertTrue(seeked.durationMillis() >= 1000);
    }

    @Test
    void decodesAlawAndMuLawWav() throws Exception {
        for (int encoding : new int[]{6, 7}) {
            Path file = writeCompandedWave(encoding, new byte[]{(byte) 0xd5, (byte) 0x55});
            WavDecoder decoder = new WavDecoder();
            decoder.open(file);
            byte[] pcm = new byte[4];
            assertEquals(4, decoder.read(pcm));
            assertEquals(-1, decoder.read(new byte[2]));
            decoder.close();
            assertTrue(pcm[0] != 0 || pcm[1] != 0 || pcm[2] != 0 || pcm[3] != 0);
        }
    }

    private Path writePcmWave(int rate, int bits, int channels, int frames) throws IOException {
        boolean unsigned = bits == 8;
        AudioFormat.Encoding encoding = unsigned
                ? AudioFormat.Encoding.PCM_UNSIGNED : AudioFormat.Encoding.PCM_SIGNED;
        int bytesPerSample = bits / 8;
        AudioFormat format = new AudioFormat(encoding, rate, bits, channels,
                channels * bytesPerSample, rate, false);
        byte[] bytes = new byte[frames * channels * bytesPerSample];
        for (int frame = 0; frame < frames; frame++) {
            short signal = (short) (Math.sin(frame * Math.PI * 2 * 440 / rate) * 12000);
            for (int channel = 0; channel < channels; channel++) {
                long sample = unsigned ? signal + 32768L : signal;
                sample <<= bits - 16;
                int offset = (frame * channels + channel) * bytesPerSample;
                for (int byteIndex = 0; byteIndex < bytesPerSample; byteIndex++) {
                    bytes[offset + byteIndex] = (byte) (sample >>> (8 * byteIndex));
                }
            }
        }
        Path file = tempDir.resolve("generated-" + rate + "-" + bits + "-" + channels + ".wav");
        try (AudioInputStream stream = new AudioInputStream(
                new ByteArrayInputStream(bytes), format, frames)) {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, file.toFile());
        }
        return file;
    }

    private Path writeCompandedWave(int encoding, byte[] samples) throws IOException {
        ByteArrayOutputStream wave = new ByteArrayOutputStream();
        wave.write("RIFF".getBytes(StandardCharsets.US_ASCII));
        littleEndian(wave, 36 + samples.length, 4);
        wave.write("WAVEfmt ".getBytes(StandardCharsets.US_ASCII));
        littleEndian(wave, 16, 4);
        littleEndian(wave, encoding, 2);
        littleEndian(wave, 1, 2);
        littleEndian(wave, 8000, 4);
        littleEndian(wave, 8000, 4);
        littleEndian(wave, 1, 2);
        littleEndian(wave, 8, 2);
        wave.write("data".getBytes(StandardCharsets.US_ASCII));
        littleEndian(wave, samples.length, 4);
        wave.write(samples);
        Path file = tempDir.resolve("companded-" + encoding + ".wav");
        java.nio.file.Files.write(file, wave.toByteArray());
        return file;
    }

    private static void littleEndian(ByteArrayOutputStream output, int value, int bytes) {
        for (int index = 0; index < bytes; index++) output.write(value >>> (index * 8));
    }
}
