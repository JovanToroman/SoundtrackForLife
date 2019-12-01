package com.example.soundtrackforlife;

public class Song {
    private long id;
    private String title;
    private String artist;
    private String relativePath;

    public Song(long songID, String songTitle, String songArtist, String songRelativePath) {
        id=songID;
        title=songTitle;
        artist=songArtist;
        relativePath = songRelativePath;
    }

    public long getID(){return id;}
    public String getTitle(){return title;}
    public String getArtist(){return artist;}
    public String getRelativePath() {return relativePath;}
}
