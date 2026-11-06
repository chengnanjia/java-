package com.musicplayer.ui;

import com.musicplayer.model.Music;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaylistSearchTest {
    private final Music sunny = new Music(
            "晴天", "周杰伦", "叶惠美", "/music/晴天.mp3", null);
    private final Music track = new Music(
            "唯一", "G.E.M. 邓紫棋", "T.I.M.E.",
            "/music/唯一-G.E.M.邓紫棋.mp3", null);

    @Test
    void searchesTitleArtistAlbumAndFilenameWithoutCaseSensitivity() {
        assertTrue(PlaylistSearch.matches(track, "唯一"));
        assertTrue(PlaylistSearch.matches(track, "邓紫棋"));
        assertTrue(PlaylistSearch.matches(track, "t.i.m.e."));
        assertTrue(PlaylistSearch.matches(track, "G.E.M"));
    }

    @Test
    void allQueryWordsMustMatchSearchableTrackInformation() {
        assertTrue(PlaylistSearch.matches(track, "唯一 邓紫棋"));
        assertTrue(PlaylistSearch.matches(track, "gem time"));
        assertFalse(PlaylistSearch.matches(track, "唯一 周杰伦"));
    }

    @Test
    void searchesChineseNamesByPinyinWithOrWithoutSpaces() {
        assertTrue(PlaylistSearch.matches(sunny, "qing tian"));
        assertTrue(PlaylistSearch.matches(sunny, "qingtian"));
        assertTrue(PlaylistSearch.matches(sunny, "zhou jie lun"));
        assertTrue(PlaylistSearch.matches(sunny, "ye hui mei"));
        assertFalse(PlaylistSearch.matches(sunny, "qing xue"));
    }

    @Test
    void blankQueryMatchesEveryTrackAndNullTextIsSafe() {
        assertTrue(PlaylistSearch.matches(track, "   "));
        assertTrue(PlaylistSearch.matches(track, null));
    }
}
