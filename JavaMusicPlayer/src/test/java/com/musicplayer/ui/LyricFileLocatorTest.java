package com.musicplayer.ui;

import com.musicplayer.model.Music;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LyricFileLocatorTest {
    @TempDir Path tempDir;

    @Test
    void findsTitleArtistNamedLibraryLyricWhenAudioHasTaggedTitle() throws Exception {
        Path musicDirectory = Files.createDirectories(tempDir.resolve("music"));
        Path lyricDirectory = Files.createDirectories(tempDir.resolve("lyrics"));
        Path audio = Files.createFile(musicDirectory.resolve("唯一-G.E.M.邓紫棋.mp3"));
        Path lyric = Files.writeString(lyricDirectory.resolve("唯一-G.E.M.邓紫棋.lrc"),
                "[00:00.00]唯一\n[00:02.00]歌词\n");
        Music music = new Music("唯一", "G.E.M. 邓紫棋", audio.toString());

        Path found = LyricFileLocator.findLyrics(music, lyricDirectory).orElseThrow();

        assertEquals(lyric, found);
        assertEquals(2, LrcParser.parse(found).size());
    }

    @Test
    void matchesLibraryLyricIgnoringCaseAndWhitespace() throws Exception {
        Path musicDirectory = Files.createDirectories(tempDir.resolve("music"));
        Path lyricDirectory = Files.createDirectories(tempDir.resolve("lyrics"));
        Path audio = Files.createFile(musicDirectory.resolve("New Song.mp3"));
        Path lyric = Files.createFile(lyricDirectory.resolve("new   song.LRC"));
        Music music = new Music("New Song", "Artist", audio.toString());

        assertEquals(lyric, LyricFileLocator.findLyrics(music, lyricDirectory).orElseThrow());
    }

    @Test
    void prefersSameDirectoryLyricOverLibraryLyric() throws Exception {
        Path musicDirectory = Files.createDirectories(tempDir.resolve("music"));
        Path lyricDirectory = Files.createDirectories(tempDir.resolve("lyrics"));
        Path audio = Files.createFile(musicDirectory.resolve("Sunny.mp3"));
        Path siblingLyric = Files.createFile(musicDirectory.resolve("Sunny.lrc"));
        Files.createFile(lyricDirectory.resolve("Sunny.lrc"));
        Music music = new Music("Sunny", "Artist", audio.toString());

        assertEquals(siblingLyric, LyricFileLocator.findLyrics(music, lyricDirectory).orElseThrow());
    }
}
