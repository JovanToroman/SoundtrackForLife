package com.fri.soundtrackforlife;

import android.content.Context;
import android.widget.MediaController;

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

}
