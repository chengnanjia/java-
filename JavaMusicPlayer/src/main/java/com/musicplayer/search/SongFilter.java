package com.musicplayer.search;

import com.musicplayer.model.Music;
import net.sourceforge.pinyin4j.PinyinHelper;
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType;
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat;
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType;
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType;
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;

public final class SongFilter {
    private static final int MAX_PINYIN_VARIANTS = 128;
    private static final HanyuPinyinOutputFormat PINYIN_FORMAT = pinyinFormat();
    private static final Map<Music, SearchIndex> INDEX = Collections.synchronizedMap(new WeakHashMap<>());

    private SongFilter() {
    }

    public static List<Music> filter(String query, List<Music> songs) {
        if (songs == null || songs.isEmpty()) return List.of();
        List<String> terms = queryTerms(query);
        if (terms.isEmpty()) return List.copyOf(songs);
        return songs.stream().filter(song -> matches(song, terms)).toList();
    }

    public static boolean matches(Music song, String query) {
        return matches(song, queryTerms(query));
    }

    public static void index(Music song) {
        if (song != null) INDEX.computeIfAbsent(song, SongFilter::createIndex);
    }

    private static boolean matches(Music song, List<String> terms) {
        if (song == null || terms.isEmpty()) return song != null;
        SearchIndex index;
        synchronized (INDEX) {
            index = INDEX.computeIfAbsent(song, SongFilter::createIndex);
        }
        return terms.stream().allMatch(term ->
                index.normalizedFields.stream().anyMatch(field -> field.contains(term))
                        || index.pinyin.stream().anyMatch(field -> field.contains(term))
                        || index.initials.stream().anyMatch(field -> field.contains(term)));
    }

    private static SearchIndex createIndex(Music song) {
        List<String> sourceFields = Arrays.asList(song.getName(), song.getArtist(), song.getAlbum());
        List<String> normalized = sourceFields.stream().map(SongFilter::normalize).toList();
        List<String> pinyin = new ArrayList<>();
        List<String> initials = new ArrayList<>();
        for (String field : sourceFields) {
            PinyinIndex fieldIndex = toPinyin(field);
            pinyin.addAll(fieldIndex.full);
            initials.addAll(fieldIndex.initials);
        }
        return new SearchIndex(normalized, List.copyOf(pinyin), List.copyOf(initials));
    }

    private static List<String> queryTerms(String query) {
        if (query == null || query.isBlank()) return List.of();
        String compatibilityQuery = Normalizer.normalize(query, Normalizer.Form.NFKC).trim();
        return Arrays.stream(compatibilityQuery.split("\\s+"))
                .map(SongFilter::normalize)
                .filter(term -> !term.isEmpty())
                .toList();
    }

    private static String normalize(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{P}\\p{S}\\s]+", "");
    }

    private static PinyinIndex toPinyin(String value) {
        List<String> fullVariants = new ArrayList<>(List.of(""));
        List<String> initialVariants = new ArrayList<>(List.of(""));
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            String[] options;
            try {
                options = PinyinHelper.toHanyuPinyinStringArray(character, PINYIN_FORMAT);
            } catch (BadHanyuPinyinOutputFormatCombination exception) {
                options = null;
            }
            List<String> syllables = options == null || options.length == 0
                    ? List.of(normalize(String.valueOf(character)))
                    : Arrays.stream(options).map(SongFilter::normalize)
                    .distinct().toList();
            fullVariants = appendVariants(fullVariants, syllables);
            List<String> initialsForChar = syllables.stream()
                    .filter(syllable -> !syllable.isEmpty())
                    .map(syllable -> syllable.substring(0, 1)).distinct().toList();
            if (initialsForChar.isEmpty()) initialsForChar = List.of("");
            initialVariants = appendVariants(initialVariants, initialsForChar);
        }
        return new PinyinIndex(fullVariants, initialVariants);
    }

    private static List<String> appendVariants(List<String> existing, List<String> options) {
        List<String> result = new ArrayList<>();
        for (String prefix : existing) {
            for (String option : options) {
                result.add(prefix + option);
                if (result.size() >= MAX_PINYIN_VARIANTS) return result;
            }
        }
        return result;
    }

    private static HanyuPinyinOutputFormat pinyinFormat() {
        HanyuPinyinOutputFormat format = new HanyuPinyinOutputFormat();
        format.setCaseType(HanyuPinyinCaseType.LOWERCASE);
        format.setToneType(HanyuPinyinToneType.WITHOUT_TONE);
        format.setVCharType(HanyuPinyinVCharType.WITH_V);
        return format;
    }

    private record SearchIndex(List<String> normalizedFields, List<String> pinyin,
                               List<String> initials) { }

    private record PinyinIndex(List<String> full, List<String> initials) { }
}
