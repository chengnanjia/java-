package com.musicplayer.player.decode;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

public final class WavDecoder implements AudioDecoder {
    private Path path;
    private AudioInputStream input;
    private AudioFormat sourceFormat;
    private AudioFormat.Encoding encoding;
    private int sampleRate;
    private int channels;
    private int sampleBits;
    private int sourceFrameSize;
    private int outputFrameSize;
    private long frameLength;
    private long currentFrame;
    private byte[] carry = new byte[0];
    private boolean endOfStream;

    @Override
    public void open(Path path) throws IOException {
        close();
        this.path = path;
        try {
            AudioFileFormat fileFormat = AudioSystem.getAudioFileFormat(path.toFile());
            openStream();
            configure(fileFormat.getFrameLength());
        } catch (UnsupportedAudioFileException exception) {
            IOException failure = new IOException(
                    "无法识别或不支持的 WAV 文件：" + path.getFileName(), exception);
            closeAfterFailure(failure);
            throw failure;
        } catch (IOException exception) {
            closeAfterFailure(exception);
            throw exception;
        }
    }

    private void closeAfterFailure(IOException failure) {
        try {
            close();
        } catch (IOException closeException) {
            failure.addSuppressed(closeException);
        }
    }

    private void openStream() throws IOException, UnsupportedAudioFileException {
        input = AudioSystem.getAudioInputStream(path.toFile());
    }

    private void configure(long declaredFrames) throws IOException {
        sourceFormat = input.getFormat();
        encoding = sourceFormat.getEncoding();
        channels = sourceFormat.getChannels();
        sampleRate = Math.round(sourceFormat.getSampleRate());
        sampleBits = sourceFormat.getSampleSizeInBits();
        sourceFrameSize = sourceFormat.getFrameSize();
        outputFrameSize = channels * 2;
        frameLength = declaredFrames >= 0 ? declaredFrames : input.getFrameLength();
        currentFrame = 0;
        carry = new byte[0];
        endOfStream = false;
        if (sampleRate <= 0 || channels <= 0 || sourceFrameSize <= 0) {
            throw new IOException("WAV 音频格式信息无效");
        }
        boolean companded = AudioFormat.Encoding.ALAW.equals(encoding)
                || AudioFormat.Encoding.ULAW.equals(encoding);
        if (companded && sampleBits == 8) return;
        if (!AudioFormat.Encoding.PCM_SIGNED.equals(encoding)
                && !AudioFormat.Encoding.PCM_UNSIGNED.equals(encoding)) {
            throw unsupportedEncoding();
        }
        if (sampleBits != 8 && sampleBits != 16 && sampleBits != 24 && sampleBits != 32) {
            throw unsupportedEncoding();
        }
        if ((sampleBits + 7) / 8 * channels > sourceFrameSize) {
            throw new IOException("WAV 每帧数据长度小于采样格式要求");
        }
    }

    private IOException unsupportedEncoding() {
        String format = encoding == null ? "未知" : encoding.toString().toUpperCase(Locale.ROOT);
        return new IOException("不支持的 WAV 编码或位深：" + format + " / " + sampleBits
                + " 位（例如 ADPCM 编码目前不支持）");
    }

    @Override
    public long durationMillis() {
        return frameLength < 0 || sampleRate <= 0 ? 0 : frameLength * 1000 / sampleRate;
    }

    @Override public int sampleRate() { return sampleRate; }
    @Override public int channels() { return channels; }

    @Override
    public int read(byte[] buffer) throws IOException {
        requireOpen();
        if (buffer.length < outputFrameSize) {
            throw new IOException("PCM 读取缓冲区必须至少容纳一个音频帧");
        }
        int framesToRead = buffer.length / outputFrameSize;
        byte[] source = new byte[framesToRead * sourceFrameSize];
        int bytesRead = Math.min(carry.length, source.length);
        System.arraycopy(carry, 0, source, 0, bytesRead);
        while (bytesRead < source.length && !endOfStream) {
            int remaining = source.length - bytesRead;
            int alignedLength = remaining / sourceFrameSize * sourceFrameSize;
            if (alignedLength == 0) break;
            int count = input.read(source, bytesRead, alignedLength);
            if (count < 0) {
                endOfStream = true;
            } else if (count > 0) {
                bytesRead += count;
            }
        }
        int completeFrames = bytesRead / sourceFrameSize;
        int remainder = bytesRead % sourceFrameSize;
        carry = new byte[remainder];
        if (remainder > 0) {
            System.arraycopy(source, completeFrames * sourceFrameSize, carry, 0, remainder);
        }
        if (completeFrames == 0) {
            if (remainder > 0) throw new IOException("WAV 文件末尾包含不完整的音频帧");
            if (frameLength < 0) frameLength = currentFrame;
            return -1;
        }
        for (int frame = 0; frame < completeFrames; frame++) {
            int sourceOffset = frame * sourceFrameSize;
            int outputOffset = frame * outputFrameSize;
            for (int channel = 0; channel < channels; channel++) {
                int sampleOffset = sourceOffset + channel * ((sampleBits + 7) / 8);
                short sample = decodeSample(source, sampleOffset);
                buffer[outputOffset++] = (byte) sample;
                buffer[outputOffset++] = (byte) (sample >> 8);
            }
        }
        currentFrame += completeFrames;
        return completeFrames * outputFrameSize;
    }

    private short decodeSample(byte[] source, int offset) {
        if (AudioFormat.Encoding.ALAW.equals(encoding)) {
            return decodeALaw(source[offset]);
        }
        if (AudioFormat.Encoding.ULAW.equals(encoding)) {
            return decodeULaw(source[offset]);
        }
        int bytesPerSample = (sampleBits + 7) / 8;
        long raw = 0;
        for (int index = 0; index < bytesPerSample; index++) {
            int shift = sourceFormat.isBigEndian()
                    ? (bytesPerSample - index - 1) * 8 : index * 8;
            raw |= (long) (source[offset + index] & 0xff) << shift;
        }
        long value = raw;
        if (AudioFormat.Encoding.PCM_SIGNED.equals(encoding)) {
            long signBit = 1L << (sampleBits - 1);
            if ((raw & signBit) != 0) value = raw - (1L << sampleBits);
        } else {
            value -= 1L << (sampleBits - 1);
        }
        long scaled = sampleBits > 16 ? value >> (sampleBits - 16) : value << (16 - sampleBits);
        return (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, scaled));
    }

    private static short decodeULaw(byte encoded) {
        int value = ~encoded & 0xff;
        int magnitude = ((value & 0x0f) << 3) + 0x84;
        magnitude <<= (value & 0x70) >> 4;
        return (short) ((value & 0x80) != 0 ? 0x84 - magnitude : magnitude - 0x84);
    }

    private static short decodeALaw(byte encoded) {
        int value = (encoded ^ 0x55) & 0xff;
        int magnitude = (value & 0x0f) << 4;
        int segment = (value & 0x70) >> 4;
        magnitude += segment == 0 ? 8 : segment == 1 ? 0x108 : 0x108;
        if (segment > 1) magnitude <<= segment - 1;
        return (short) ((value & 0x80) != 0 ? magnitude : -magnitude);
    }

    @Override
    public void seekTo(long millis) throws IOException {
        requireOpen();
        long targetFrame = Math.max(0, millis) * sampleRate / 1000;
        if (frameLength >= 0) targetFrame = Math.min(targetFrame, frameLength);
        input.close();
        try {
            openStream();
        } catch (UnsupportedAudioFileException exception) {
            throw new IOException("无法重新打开 WAV 文件：" + path.getFileName(), exception);
        }
        long bytesRemaining = targetFrame * sourceFrameSize;
        byte[] discard = new byte[Math.max(sourceFrameSize,
                8192 / sourceFrameSize * sourceFrameSize)];
        while (bytesRemaining > 0) {
            long skipped = input.skip(bytesRemaining);
            if (skipped > 0) {
                bytesRemaining -= skipped;
                continue;
            }
            int requested = (int) Math.min(discard.length, bytesRemaining);
            int count = input.read(discard, 0, requested);
            if (count < 0) throw new IOException("WAV 文件在定位目标之前意外结束");
            if (count == 0) continue;
            bytesRemaining -= count;
        }
        currentFrame = targetFrame;
        carry = new byte[0];
        endOfStream = false;
    }

    @Override
    public void close() throws IOException {
        if (input != null) {
            input.close();
            input = null;
        }
    }

    private void requireOpen() throws IOException {
        if (input == null) throw new IOException("WAV 解码器尚未打开");
    }
}
