package com.musicplayer.ui;

import com.musicplayer.model.Music;

import java.util.Arrays;
import java.util.List;

final class PlaylistSearch {
    private PlaylistSearch() {
    }

    static boolean matches(Music music, String query) {
        List<String> queryTerms = query == null ? List.of()
                : Arrays.stream(query.trim().split("\\s+"))
                .map(PlaylistSearch::normalize)
                .filter(term -> !term.isEmpty())
                .toList();
        if (queryTerms.isEmpty()) return true;

        String searchableText = normalize(String.join(" ",
                music.getName(),
                music.getArtist(),
                music.getAlbum(),
                music.getPath()));
        return queryTerms.stream()
                .allMatch(searchableText::contains);
    }

    static String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }
}
