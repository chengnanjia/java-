package com.musicplayer.ui;

import com.musicplayer.model.Music;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.util.Arrays;
import java.util.List;

final class PlaylistSearch {
    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = pinyinFormat();

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
                music.getPath()))
                + normalize(toPinyin(String.join(" ",
                music.getName(),
                music.getArtist(),
                music.getAlbum(),
                music.getPath())));
        return queryTerms.stream()
                .allMatch(searchableText::contains);
    }

    private static HanyuPinyinOutputFormat pinyinFormat() {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        return format;
    }

    private static String toPinyin(String value) {
        StringBuilder pinyin = new StringBuilder(value.length());
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            try {
                String[] pronunciations = PinyinHelper.toHanyuPinyinStringArray(
                        character, PINYIN_FORMAT);
                pinyin.append(pronunciations == null || pronunciations.length == 0
                        ? character : pronunciations[0]);
            } catch (BadHanyuPinyinOutputFormatCombination exception) {
                pinyin.append(character);
            }
        }
        return pinyin.toString();
    }

    static String normalize(String value) {
        return value == null
                ? ""
                : value.toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }
}
