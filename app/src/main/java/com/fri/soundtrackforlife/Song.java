package com.fri.soundtrackforlife;

import java.util.HashMap;
import java.util.Map;

public class Song {
    private long id;
    private String title;
    private String artist;
    private String relativePath;
    private Map<Integer, Integer> counts;
    private double score;

    public Song(long songID, String songTitle, String songArtist, String songRelativePath) {
        id=songID;
        title=songTitle;
        artist=songArtist;
        relativePath = songRelativePath;
        counts = initCounts();
        score = 0;
    }

    private Map<Integer, Integer> initCounts() {
        Map<Integer, Integer> ret = new HashMap<>();
        for (int i = 0; i < 9; i++) {
            ret.put(i, 0);
        }
        ret.remove(6);
        ret.put(-1, 0);
        return ret;
    }

    public long getID(){return id;}
    public String getTitle(){return title;}
    public String getArtist(){return artist;}
    public String getRelativePath() {return relativePath;}
    Map<Integer, Integer> getCounts() {
        return counts;
    }

    public void setScore(double s) {
        score = s;
    }

    public double getScore() {
        return score;
    }
}
