package com.musicplayer.model;

import com.mpatric.mp3agic.ID3v1;
import com.mpatric.mp3agic.ID3v2;
import com.mpatric.mp3agic.Mp3File;

import java.io.File;

public class Music {
    private final String name;
    private final String path;
    private final String artist;
    private final String album;
    private final byte[] artwork;

    public Music(String name, String path) {
        this(name, "未知歌手", path);
    }

    public Music(String name, String artist, String path) {
        this(name, artist, "", path, null);
    }

    public Music(String name, String artist, String album, String path, byte[] artwork) {
        this.name = name;
        this.artist = artist == null || artist.trim().isEmpty() ? "未知歌手" : artist.trim();
        this.album = album == null ? "" : album.trim();
        this.path = path;
        this.artwork = artwork == null ? null : artwork.clone();
    }

    public static Music fromFile(File file) {
        String fileName = file.getName();
        int extension = fileName.lastIndexOf('.');
        String name = extension > 0 ? fileName.substring(0, extension) : fileName;
        String artist = "未知歌手";
        String album = "";
        byte[] artwork = null;
        String[] parts = name.split("\\s+-\\s+", 2);
        if (parts.length == 2) {
            name = parts[0].trim();
            artist = parts[1].trim();
        }
        try {
            Mp3File mp3File = new Mp3File(file);
            if (mp3File.hasId3v2Tag()) {
                ID3v2 tag = mp3File.getId3v2Tag();
                if (present(tag.getTitle())) name = tag.getTitle().trim();
                if (present(tag.getArtist())) artist = tag.getArtist().trim();
                if (present(tag.getAlbum())) album = tag.getAlbum().trim();
                artwork = tag.getAlbumImage();
            } else if (mp3File.hasId3v1Tag()) {
                ID3v1 tag = mp3File.getId3v1Tag();
                if (present(tag.getTitle())) name = tag.getTitle().trim();
                if (present(tag.getArtist())) artist = tag.getArtist().trim();
                if (present(tag.getAlbum())) album = tag.getAlbum().trim();
            }
        } catch (Exception ignored) {
            // 缺失或损坏的标签不应阻止歌曲加入曲库。
        }
        return new Music(name, artist, album, file.getAbsolutePath(), artwork);
    }

    private static boolean present(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public String getName() {
        return name;
    }

    public String getArtist() {
        return artist;
    }

    public String getPath() {
        return path;
    }

    public String getAlbum() {
        return album;
    }

    public byte[] getArtwork() {
        return artwork == null ? null : artwork.clone();
    }

    @Override
    public String toString() {
        return name + " - " + artist;
    }
}
