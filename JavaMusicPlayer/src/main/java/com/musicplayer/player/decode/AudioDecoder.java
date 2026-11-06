package com.musicplayer.player.decode;

import java.io.IOException;
import java.nio.file.Path;

public interface AudioDecoder extends AutoCloseable {
    void open(Path path) throws IOException;
    long durationMillis();
    int sampleRate();
    int channels();
    int read(byte[] buffer) throws IOException;
    void seekTo(long millis) throws IOException;
    @Override void close() throws IOException;
}
