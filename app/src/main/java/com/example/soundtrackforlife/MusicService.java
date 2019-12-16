package com.example.soundtrackforlife;

import android.app.Service;
import android.content.Intent;
import android.media.MediaPlayer;
import android.os.AsyncTask;
import android.os.Build;
import android.os.IBinder;
import java.util.ArrayList;
import java.util.Random;

import android.content.ContentUris;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Binder;
import android.os.PowerManager;
import android.util.Log;
import android.app.Notification;
import android.app.PendingIntent;


public class MusicService extends Service implements
        MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener,
        MediaPlayer.OnCompletionListener {

    //media player
    private MediaPlayer player;
    private MainActivity mainActivity;
    //song list
    private ArrayList<Song> songs;
    private String songTitle="";
    private static final int NOTIFY_ID=1;

    //current position
    private int songPosn;
    private int prevSongPosn;
    private final IBinder musicBind = new MusicBinder();

    private boolean shuffle=true;
    private Random rand;

    @Override
    public IBinder onBind(Intent arg0) {
        return musicBind;
    }

    @Override
    public boolean onUnbind(Intent intent){
        player.stop();
        player.release();
        return false;
    }

    public void onCreate(){
        //create the service
        super.onCreate();
        //initialize position
        songPosn=0;
        prevSongPosn = 0; // songs.size() - 1 produces error
        //create player
        player = new MediaPlayer();
        rand=new Random();

        initMusicPlayer();
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        //start playback
        mp.start();
        Intent notIntent = new Intent(this, MainActivity.class);
        notIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pendInt = PendingIntent.getActivity(this, 0,
                notIntent, PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.Builder builder = null;
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            builder = new Notification.Builder(this);
            builder.setContentIntent(pendInt)
                    .setSmallIcon(R.drawable.play)
                    .setTicker(songTitle)
                    .setOngoing(true)
                    .setContentTitle("Playing")
            .setContentText(songTitle);
            Notification not = builder.build();

            startForeground(NOTIFY_ID, not);
        }

    }

    public void initMusicPlayer(){
        player.setWakeMode(getApplicationContext(),
                PowerManager.PARTIAL_WAKE_LOCK);
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        player.setOnPreparedListener(this);
        player.setOnCompletionListener(this);
        player.setOnErrorListener(this);
    }

    public void setList(ArrayList<Song> theSongs){
        songs=theSongs;
    }

    public void playSong(){
        player.reset();

        //get song
        Song playSong = songs.get(songPosn);
        songTitle=playSong.getTitle();
        //get id
        long currSong = playSong.getID();
        //set uri
        Uri trackUri = ContentUris.withAppendedId(
                android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                currSong);
        try{
            player.setDataSource(getApplicationContext(), trackUri);
        }
        catch(Exception e){
            Log.e("MUSIC SERVICE", "Error setting data source", e);
        }
        try {
            player.prepareAsync();
        }
        catch (Exception e) {
            Log.e("MUSIC SERVICE", "prepareAsync() crashed");
        }
    }

    @Override
    public void onCompletion(MediaPlayer mp) {
            prevSongPosn = songPosn;
            try {
                AsyncTask.execute(() -> mainActivity.addRecordWithFeatures(getCurrentSongData(), MainActivity.LIKE));
            } catch (Exception e) {
                Log.d("implicit_feedback", "Exception while implicit feedback");
            }
            mp.reset();
            playNext();
    }

    @Override
    public boolean onError(MediaPlayer mp, int what, int extra) {
        mp.reset();
        return false;
    }

    @Override
    public void onDestroy() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ECLAIR) {
            stopForeground(true);
        }
    }

    public class MusicBinder extends Binder {
        MusicService getService() {
            return MusicService.this;
        }
    }

    public boolean setShuffle(){
        if(shuffle) {
            shuffle=false;
        }
        else {
            shuffle=true;
        }
        return shuffle;
    }

    public int getPosn() {
        return player.getCurrentPosition();
    }

    public void setSong(int songIndex){
        songPosn=songIndex;
    }

    public Song getCurrentSongData() {
        return songs.get(songPosn);
    }

    public int getDur(){
        return player.getDuration();
    }

    public boolean isPng(){
        return player.isPlaying();
    }

    public void pausePlayer(){
        player.pause();
    }

    public void seek(int posn){
        player.seekTo(posn);
    }

    public void go(){
        player.start();
    }

    public void playPrev(){
        songPosn = prevSongPosn;
        playSong();
    }

    //add implicit feedback for songs which are interrupted. Give feedback based on if it is over half of the song or not
    public void playNext(){
        prevSongPosn = songPosn;
        songPosn = resolveNextSong(shuffle);
        playSong();
    }

    private int resolveNextSong(boolean random){
        int activityId = mainActivity.getCurrentActivity();

        int minCount = Integer.MAX_VALUE;
        for (Song s : songs) {
            if (s.getCounts().get(activityId) < minCount) {
                minCount = s.getCounts().get(activityId);
            }
        }
        int newSong = songPosn;

        if (random) {
            while (newSong == songPosn || songs.get(newSong).getCounts().get(activityId) != minCount) {
                newSong = rand.nextInt(songs.size());
            }
        } else {
            newSong++;
            if(newSong >= songs.size()) {
                newSong = 0;
            }
        }
        return newSong;
    }

    public int getSongPosn(String title, String artist) {
        for (Song song : songs) {
            if (song.getTitle().equals(title) && song.getArtist().equals(artist)) {
                return songs.indexOf(song);
            }
        }
        return 0;
    }

    public long getSongID(String title, String artist) {
        for (Song song : songs) {
            if (song.getTitle().equals(title) && song.getArtist().equals(artist)) {
                return song.getID();
            }
        }
        return 0;
    }

    // returning empty string might be problematic
    public String getSongPath(String title, String artist) {
        for (Song song : songs) {
            if (song.getTitle().equals(title) && song.getArtist().equals(artist)) {
                return song.getRelativePath();
            }
        }
        return "";
    }

    public Song getSong(String title, String artist) {
        for (Song song : songs) {
            if (song.getTitle().equals(title) && song.getArtist().equals(artist)) {
                return song;
            }
        }
        return new Song(-1, "", "", "");
    }

    public Song getSongByTitle(String title) {
        for (Song song : songs) {
            if (song.getTitle() != null && song.getTitle().equals(title)) {
                return song;
            }
        }
        return new Song(-1, "", "", "");
    }

    public MainActivity getMainActivity() {
        return mainActivity;
    }

    public void setMainActivity(MainActivity mainActivity) {
        this.mainActivity = mainActivity;
    }
}
