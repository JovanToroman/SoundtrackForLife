package com.fri.soundtrackforlife;

import java.util.List;

public class SongFirebaseEntry {
    private String title;
    private String artist;
    private String feedback;
    private String activity;
    private List<Double> location;
    private List<String> time;
    private List<Double> features;
    private Long id;
    private String userId;

    public SongFirebaseEntry(String songTitle, String songArtist, String songFeedback,
                             String songActivity, List<Double> songLocation, List<String> songTime,
                             List<Double> songFeatures) {
        title = songTitle;
        artist = songArtist;
        feedback = songFeedback;
        activity = songActivity;
        location = songLocation;
        time = songTime;
        features = songFeatures;
    }

    public SongFirebaseEntry(String songTitle, String songArtist, String songFeedback,
                             String songActivity, List<Double> songLocation, List<String> songTime,
                             List<Double> songFeatures, long id) {
        title = songTitle;
        artist = songArtist;
        feedback = songFeedback;
        activity = songActivity;
        location = songLocation;
        time = songTime;
        features = songFeatures;
        this.id = id;
    }

    public SongFirebaseEntry(String songTitle, String songArtist, String songFeedback,
                             String songActivity, List<Double> songLocation, List<String> songTime,
                             List<Double> songFeatures, long id, String userId) {
        title = songTitle;
        artist = songArtist;
        feedback = songFeedback;
        activity = songActivity;
        location = songLocation;
        time = songTime;
        features = songFeatures;
        this.id = id;
        this.userId = userId;
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public String getFeedback() {
        return feedback;
    }

    public String getActivity() {
        return activity;
    }

    public List<Double> getLocation() {
        return location;
    }

    public List<String> getTime() {
        return time;
    }

    public List<Double> getFeatures() {
        return features;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }
}
