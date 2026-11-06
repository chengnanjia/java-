package com.musicplayer.player.decode;

import org.jflac.FLACDecoder;
import org.jflac.frame.Frame;
import org.jflac.io.RandomFileInputStream;
import org.jflac.metadata.Metadata;
import org.jflac.metadata.SeekTable;
import org.jflac.metadata.StreamInfo;
import org.jflac.util.ByteData;

import java.io.IOException;
import java.nio.file.Path;

public final class FlacDecoder implements AudioDecoder {
    private Path path;
    private RandomFileInputStream input;
    private FLACDecoder decoder;
    private StreamInfo streamInfo;
    private int bitsPerSample;
    private boolean hasSeekTable;
    private boolean atEnd;
    private long samplesToDiscard;
    private byte[] pendingPcm = new byte[0];
    private int pendingOffset;

    @Override
    public void open(Path path) throws IOException {
        close();
        this.path = path;
        try {
            initializeDecoder();
        } catch (IOException exception) {
            close();
            throw new IOException("无法打开或解码 FLAC 文件：" + exception.getMessage(), exception);
        }
    }

    private void initializeDecoder() throws IOException {
        input = new RandomFileInputStream(path.toFile());
        decoder = new FLACDecoder(input);
        streamInfo = decoder.readStreamInfo();
        if (streamInfo == null) throw new IOException("FLAC 文件缺少 STREAMINFO 元数据");
        Metadata[] metadata = decoder.readMetadata(streamInfo);
        bitsPerSample = streamInfo.getBitsPerSample();
        if (streamInfo.getChannels() > 2) {
            throw new IOException("暂不支持超过双声道的 FLAC 音频（当前 "
                    + streamInfo.getChannels() + " 声道）");
        }
        if (bitsPerSample != 8 && bitsPerSample != 16 && bitsPerSample != 24) {
            throw new IOException("暂不支持 " + bitsPerSample + " 位 FLAC 音频");
        }
        if (streamInfo.getSampleRate() <= 0) {
            throw new IOException("FLAC 文件的采样率无效");
        }
        hasSeekTable = containsSeekPoint(metadata);
        samplesToDiscard = 0;
        pendingPcm = new byte[0];
        pendingOffset = 0;
        atEnd = false;
    }

    private boolean containsSeekPoint(Metadata[] metadata) {
        for (Metadata block : metadata) {
            if (!(block instanceof SeekTable seekTable)) continue;
            for (int index = 0; index < seekTable.numberOfPoints(); index++) {
                if (seekTable.getSeekPoint(index).getSampleNumber() >= 0) return true;
            }
        }
        return false;
    }

    @Override
    public long durationMillis() {
        if (streamInfo == null || streamInfo.getSampleRate() <= 0) return 0;
        return streamInfo.getTotalSamples() * 1000L / streamInfo.getSampleRate();
    }

    @Override
    public int sampleRate() {
        return streamInfo == null ? 0 : streamInfo.getSampleRate();
    }

    @Override
    public int channels() {
        return streamInfo == null ? 0 : streamInfo.getChannels();
    }

    @Override
    public int read(byte[] buffer) throws IOException {
        if (decoder == null) throw new IOException("FLAC 解码器尚未打开");
        if (buffer.length == 0) return 0;
        if (atEnd) return -1;

        int written = 0;
        while (written < buffer.length) {
            if (pendingOffset < pendingPcm.length) {
                int count = Math.min(buffer.length - written, pendingPcm.length - pendingOffset);
                System.arraycopy(pendingPcm, pendingOffset, buffer, written, count);
                pendingOffset += count;
                written += count;
                continue;
            }
            if (!readNextFrame()) {
                atEnd = true;
                return written == 0 ? -1 : written;
            }
        }
        return written;
    }

    private boolean readNextFrame() throws IOException {
        while (true) {
            Frame frame = decoder.readNextFrame();
            if (frame == null) return false;
            ByteData decoded = decoder.decodeFrame(frame, null);
            int sourceFrameSize = channels() * ((bitsPerSample + 7) / 8);
            int samplesInFrame = decoded.getLen() / sourceFrameSize;
            int skip = (int) Math.min(samplesToDiscard, samplesInFrame);
            samplesToDiscard -= skip;
            pendingPcm = convertTo16BitPcm(decoded.getData(), decoded.getLen(),
                    bitsPerSample, channels(), skip, samplesInFrame);
            pendingOffset = 0;
            if (pendingPcm.length > 0) return true;
        }
    }

    @Override
    public void seekTo(long millis) throws IOException {
        if (decoder == null) throw new IOException("FLAC 解码器尚未打开");
        long boundedMillis = Math.max(0, millis);
        if (durationMillis() > 0) boundedMillis = Math.min(boundedMillis, durationMillis());
        long targetSample = boundedMillis > Long.MAX_VALUE / sampleRate()
                ? Long.MAX_VALUE : boundedMillis * sampleRate() / 1000;
        long totalSamples = streamInfo.getTotalSamples();
        if (totalSamples > 0) targetSample = Math.min(targetSample, totalSamples);

        pendingPcm = new byte[0];
        pendingOffset = 0;
        samplesToDiscard = 0;
        if (totalSamples > 0 && targetSample >= totalSamples) {
            atEnd = true;
            return;
        }

        atEnd = false;
        if (hasSeekTable) {
            try {
                // jFLAC.seek 会优先用 SeekTable 缩小定位范围，并回到目标所在帧。
                long frameStartSample = decoder.seek(targetSample);
                samplesToDiscard = Math.max(0, targetSample - frameStartSample);
                return;
            } catch (IOException | IllegalArgumentException exception) {
                throw new IOException("FLAC 定位失败：" + exception.getMessage(), exception);
            }
        }

        // 没有 SeekTable 时从头重新解码，丢弃目标位置之前的采样帧。
        try {
            input.close();
            initializeDecoder();
            samplesToDiscard = targetSample;
        } catch (IOException exception) {
            throw new IOException("FLAC 定位失败：" + exception.getMessage(), exception);
        }
    }

    static byte[] convertTo16BitPcm(byte[] source, int sourceLength, int bitsPerSample,
                                    int channels, int skipSamples, int samplesInFrame) {
        int bytesPerSample = (bitsPerSample + 7) / 8;
        if (skipSamples < 0 || skipSamples > samplesInFrame
                || sourceLength < samplesInFrame * channels * bytesPerSample) {
            throw new IllegalArgumentException("FLAC PCM 数据长度无效");
        }
        int outputSamples = (samplesInFrame - skipSamples) * channels;
        byte[] output = new byte[outputSamples * 2];
        int sourceIndex = skipSamples * channels * bytesPerSample;
        int outputIndex = 0;
        for (int sample = 0; sample < outputSamples; sample++) {
            switch (bitsPerSample) {
                case 8 -> {
                    short value = (short) (((source[sourceIndex] & 0xff) - 128) << 8);
                    output[outputIndex++] = (byte) value;
                    output[outputIndex++] = (byte) (value >> 8);
                    sourceIndex++;
                }
                case 16 -> {
                    output[outputIndex++] = source[sourceIndex++];
                    output[outputIndex++] = source[sourceIndex++];
                }
                case 24 -> {
                    // 24 位有符号 PCM 取高 16 位，舍弃最低 8 位作为简单降位处理。
                    output[outputIndex++] = source[sourceIndex + 1];
                    output[outputIndex++] = source[sourceIndex + 2];
                    sourceIndex += 3;
                }
                default -> throw new IllegalArgumentException(
                        "不支持的 FLAC 位深：" + bitsPerSample);
            }
        }
        return output;
    }

    @Override
    public void close() throws IOException {
        RandomFileInputStream activeInput = input;
        input = null;
        decoder = null;
        streamInfo = null;
        path = null;
        hasSeekTable = false;
        atEnd = true;
        pendingPcm = new byte[0];
        pendingOffset = 0;
        samplesToDiscard = 0;
        if (activeInput != null) activeInput.close();
    }
}
