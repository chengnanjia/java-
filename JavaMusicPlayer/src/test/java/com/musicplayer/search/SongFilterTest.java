package com.musicplayer.search;

import com.musicplayer.model.Music;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SongFilterTest {
    private final Music sunny = new Music(
            "晴天 Remix", "周杰伦", "叶惠美", "/music/sunny.mp3", null);
    private final Music gem = new Music(
            "唯一", "G.E.M. 邓紫棋", "Ｔ.Ｉ.Ｍ.Ｅ.",
            "/music/unique.mp3", null);

    @Test
    void matchesChineseAndMixedChineseEnglishText() {
        assertTrue(SongFilter.matches(sunny, "晴天"));
        assertTrue(SongFilter.matches(sunny, "晴天 remix"));
        assertTrue(SongFilter.matches(sunny, "remix"));
    }

    @Test
    void ignoresCasePunctuationAndFullWidthDifferences() {
        assertTrue(SongFilter.matches(gem, "gem"));
        assertTrue(SongFilter.matches(gem, "time"));
        assertTrue(SongFilter.matches(gem, "ＴＩＭＥ"));
    }

    @Test
    void everyWhitespaceSeparatedTermMustMatchSomeSongField() {
        assertTrue(SongFilter.matches(gem, "唯一 邓紫棋"));
        assertTrue(SongFilter.matches(gem, "gem time"));
        assertFalse(SongFilter.matches(gem, "唯一 周杰伦"));
    }

    @Test
    void searchesAlbumAndReturnsAllSongsForBlankQuery() {
        assertTrue(SongFilter.matches(sunny, "叶惠美"));
        assertEquals(List.of(sunny, gem), SongFilter.filter("   ", List.of(sunny, gem)));
        assertTrue(SongFilter.filter(null, List.of(sunny, gem)).containsAll(List.of(sunny, gem)));
    }

    @Test
    void returnsNoResultsForUnknownTerms() {
        assertFalse(SongFilter.matches(sunny, "不存在的歌曲"));
        assertTrue(SongFilter.filter("not found", List.of(sunny, gem)).isEmpty());
    }

    @Test
    void supportsFullPinyinAndInitials() {
        assertTrue(SongFilter.matches(sunny, "qingtian"));
        assertTrue(SongFilter.matches(sunny, "qt"));
        assertTrue(SongFilter.matches(sunny, "zhou jie lun"));
    }
}
