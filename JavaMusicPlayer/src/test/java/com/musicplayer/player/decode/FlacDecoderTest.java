package com.musicplayer.player.decode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FlacDecoderTest {
    @TempDir Path tempDir;

    @Test
    void decodesDurationAndPcmForMonoAndStereoAtDifferentRatesAndDepths() throws Exception {
        assertFixture("generated-16bit-stereo.flac", 44100, 2);
        assertFixture("generated-24bit-mono.flac", 32000, 1);
    }

    @Test
    void seekingWithoutSeekTableMatchesContinuousDecode() throws Exception {
        assertSeekMatches(resource("generated-24bit-mono.flac"), false);
    }

    @Test
    void seekingWithSeekTableMatchesContinuousDecode() throws Exception {
        Path source = resource("generated-16bit-stereo.flac");
        Path withSeekTable = tempDir.resolve("with-seek-table.flac");
        addSeekTable(source, withSeekTable);
        assertSeekMatches(withSeekTable, true);
    }

    @Test
    void convertsEightAndTwentyFourBitSamplesToSignedLittleEndianPcm() {
        byte[] eightBit = FlacDecoder.convertTo16BitPcm(
                new byte[]{0, (byte) 128, (byte) 255}, 3, 8, 1, 0, 3);
        assertArrayEquals(new byte[]{0, (byte) 128, 0, 0, 0, 127}, eightBit);

        byte[] twentyFourBit = FlacDecoder.convertTo16BitPcm(
                new byte[]{0x55, 0x34, 0x12}, 3, 24, 1, 0, 1);
        assertArrayEquals(new byte[]{0x34, 0x12}, twentyFourBit);
    }

    private void assertFixture(String name, int sampleRate, int channels) throws Exception {
        FlacDecoder decoder = new FlacDecoder();
        decoder.open(resource(name));
        assertEquals(sampleRate, decoder.sampleRate());
        assertEquals(channels, decoder.channels());
        assertEquals(250, decoder.durationMillis());

        byte[] pcm = readAll(decoder);
        assertEquals(sampleRate / 4 * channels * 2, pcm.length);
        assertEquals(-1, decoder.read(new byte[64]));
        decoder.close();
    }

    private void assertSeekMatches(Path source, boolean seekTable) throws Exception {
        FlacDecoder full = new FlacDecoder();
        full.open(source);
        byte[] completePcm = readAll(full);
        full.close();

        FlacDecoder seeked = new FlacDecoder();
        seeked.open(source);
        seeked.seekTo(125);
        byte[] afterSeek = new byte[512];
        assertEquals(afterSeek.length, seeked.read(afterSeek));
        int frameSize = seeked.channels() * 2;
        int offset = 125 * seeked.sampleRate() / 1000 * frameSize;
        for (int index = 0; index < afterSeek.length; index++) {
            assertEquals(completePcm[offset + index], afterSeek[index],
                    "PCM mismatch at offset " + index + " (SeekTable=" + seekTable + ")");
        }
        seeked.close();
    }

    private byte[] readAll(FlacDecoder decoder) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[2048];
        int count;
        while ((count = decoder.read(buffer)) >= 0) output.write(buffer, 0, count);
        return output.toByteArray();
    }

    private Path resource(String name) throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/audio/" + name)) {
            if (input == null) throw new IllegalStateException("缺少 FLAC 测试夹具：" + name);
            Path file = tempDir.resolve(name);
            Files.copy(input, file);
            return file;
        }
    }

    private void addSeekTable(Path source, Path destination) throws Exception {
        byte[] flac = Files.readAllBytes(source);
        int position = 4;
        int finalHeaderPosition;
        while (true) {
            int header = flac[position] & 0xff;
            int blockLength = ((flac[position + 1] & 0xff) << 16)
                    | ((flac[position + 2] & 0xff) << 8)
                    | (flac[position + 3] & 0xff);
            finalHeaderPosition = position;
            position += 4 + blockLength;
            if ((header & 0x80) != 0) break;
        }

        flac[finalHeaderPosition] &= 0x7f;
        ByteArrayOutputStream output = new ByteArrayOutputStream(flac.length + 22);
        output.write(flac, 0, position);
        output.write(0x83);
        output.write(0);
        output.write(0);
        output.write(18);
        writeBigEndian(output, 0, 8);
        writeBigEndian(output, 0, 8);
        writeBigEndian(output, 0, 2);
        output.write(flac, position, flac.length - position);
        Files.write(destination, output.toByteArray());
    }

    private static void writeBigEndian(ByteArrayOutputStream output, long value, int bytes) {
        for (int index = bytes - 1; index >= 0; index--) {
            output.write((int) (value >>> (index * 8)) & 0xff);
        }
    }
}
