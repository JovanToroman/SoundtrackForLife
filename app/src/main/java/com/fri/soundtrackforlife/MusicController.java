package com.fri.soundtrackforlife;

import android.content.Context;
import android.view.View;
import android.widget.MediaController;
import android.widget.TextView;

import java.util.List;

public class MusicController extends MediaController {

    private boolean songIsPlaying = false;

    public MusicController(Context c){
        super(c);
    }

    public void hide(){}

    public boolean isSongPlaying() {
        return songIsPlaying;
    }

    public void setSongIsPlaying(boolean isPlaying) {
        songIsPlaying = isPlaying;
    }

    public void hideCustom() {
        super.hide();
    }

    @Override
    public void setAnchorView(View view) {
        super.setAnchorView(view);
        View songInfo = View.inflate(getContext(),R.layout.song_info, null);
        TextView type = songInfo.findViewById(R.id.song_info_type);
        type.setText("Listening to");
        addView(songInfo);

        View activityInfo = View.inflate(getContext(),R.layout.activity_info, null);
        TextView activityLabel = activityInfo.findViewById(R.id.activity_label);
        activityLabel.setText("Current activity");
        addView(activityInfo);

        View songInfoNext = View.inflate(getContext(),R.layout.next_song_info, null);
        TextView typeNext = songInfoNext.findViewById(R.id.next_song_info_type);
        typeNext.setText("Coming up next");
        addView(songInfoNext);
    }

    public void refresh(List<Song> songList, String activity, String nextSong, String currentSong) {
        TextView activityValue = findViewById(R.id.activity_label);
        if (activityValue != null) {
            activityValue.setText("Current activity: " + activity);
        }
        TextView nextSongValue = findViewById(R.id.next_song_info_type);
        if (nextSongValue != null) {
            nextSongValue.setText("Coming up next: " + nextSong);
        }
        TextView songValue = findViewById(R.id.song_info_type);
        if (songValue != null) {
            songValue.setText("Listening to: " + currentSong);
        }
    }

}
