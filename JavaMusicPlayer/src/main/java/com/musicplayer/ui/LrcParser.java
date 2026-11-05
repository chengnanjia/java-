package com.musicplayer.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LrcParser {
    private static final Pattern TIME_TAG = Pattern.compile("\\[(\\d{1,2}):(\\d{2})(?:\\.(\\d{1,3}))?]");
    private static final Pattern OFFSET_TAG = Pattern.compile("\\[offset:([+-]?\\d+)]", Pattern.CASE_INSENSITIVE);

    private LrcParser() {
    }

    public static List<LyricLine> parse(Path path) throws IOException {
        List<String> source = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) source.add(line);
        }

        long offset = 0;
        for (String line : source) {
            Matcher offsetMatcher = OFFSET_TAG.matcher(line);
            if (offsetMatcher.find()) {
                try {
                    offset = Long.parseLong(offsetMatcher.group(1));
                } catch (NumberFormatException ignored) {
                    offset = 0;
                }
            }
        }

        List<LyricLine> result = new ArrayList<>();
        for (String line : source) {
            Matcher matcher = TIME_TAG.matcher(line);
            List<Long> timestamps = new ArrayList<>();
            int lastTagEnd = 0;
            while (matcher.find()) {
                long minutes = Long.parseLong(matcher.group(1));
                long seconds = Long.parseLong(matcher.group(2));
                String fraction = matcher.group(3);
                long millis = fraction == null ? 0 : Long.parseLong((fraction + "00").substring(0, 3));
                timestamps.add((minutes * 60 + seconds) * 1000 + millis);
                lastTagEnd = matcher.end();
            }
            if (timestamps.isEmpty()) continue;
            String text = line.substring(lastTagEnd).trim();
            if (text.isEmpty()) continue;
            for (long timestamp : timestamps) {
                result.add(new LyricLine(Math.max(0, timestamp + offset), text));
            }
        }
        result.sort(Comparator.comparingLong(LyricLine::timeMillis));
        return result;
    }

    public record LyricLine(long timeMillis, String text) {
    }
}