package com.musicplayer.ui;

import com.musicplayer.model.Music;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

final class LyricFileLocator {
    private LyricFileLocator() {
    }

    static Optional<Path> findLyrics(Music music, Path libraryLyricsDirectory)
            throws IOException {
        Path audio = Path.of(music.getPath());
        String audioStem = stem(audio.getFileName().toString());
        List<String> candidates = new ArrayList<>();
        addCandidate(candidates, music.getName());
        addCandidate(candidates, audioStem);
        addCandidate(candidates, music.getName() + " - " + music.getArtist());
        addCandidate(candidates, music.getName() + "-" + music.getArtist());

        Path siblingDirectory = audio.getParent();
        if (siblingDirectory != null) {
            Optional<Path> sibling = findCandidate(siblingDirectory, candidates);
            if (sibling.isPresent()) return sibling;
        }
        return findCandidate(libraryLyricsDirectory, candidates);
    }

    private static Optional<Path> findCandidate(Path directory, List<String> candidates)
            throws IOException {
        if (directory == null || !Files.isDirectory(directory)) return Optional.empty();
        try (Stream<Path> paths = Files.list(directory)) {
            List<Path> lyricFiles = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString()
                            .toLowerCase(Locale.ROOT).endsWith(".lrc"))
                    .toList();
            for (String candidate : candidates) {
                String normalizedCandidate = normalize(candidate);
                for (Path lyricFile : lyricFiles) {
                    if (normalize(stem(lyricFile.getFileName().toString()))
                            .equals(normalizedCandidate)) {
                        return Optional.of(lyricFile);
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static void addCandidate(List<String> candidates, String candidate) {
        if (candidate == null || candidate.isBlank()) return;
        if (candidates.stream().noneMatch(existing ->
                normalize(existing).equals(normalize(candidate)))) {
            candidates.add(candidate);
        }
    }

    private static String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot > 0 ? filename.substring(0, dot) : filename;
    }

    private static String normalize(String value) {
        return value.trim().replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
    }
}
