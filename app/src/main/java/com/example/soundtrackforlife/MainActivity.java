package com.example.soundtrackforlife;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import android.Manifest;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.content.ContentResolver;
import android.database.Cursor;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Menu;
import android.view.MenuInflater;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import com.example.soundtrackforlife.MusicService.MusicBinder;
import com.google.android.gms.location.ActivityRecognitionClient;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;

import android.widget.MediaController.MediaPlayerControl;

import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements MediaPlayerControl {
    
    public static final int LIKE = 1;
    public static final int DISLIKE = 0;

    private ArrayList<Song> songList;
    private ListView songView;
    private MusicService musicSrv;
    private Intent playIntent;
    private boolean musicBound=false;
    private MusicController controller;
    private boolean paused=false, playbackPaused=false;
    final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 0;
    private ActivityRecognitionClient activityRecognitionClient;
    long DETECTION_INTERVAL_IN_MILLISECONDS = 5 * 1000;
    private FeatureExtractor fex;
    private ArrayList<Song> songPlaylist;
    private ListView songPlaylistView;
    private ArrayList<Song> songSearchList;
    private ListView songSearchListView;
    private TextInputEditText searchInput;
    private SongAdapter songSearchAdapter;
    String selected_playlist;
    String activityStr;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        songView = findViewById(R.id.song_list);
        songList = new ArrayList<>();
        fex = new FeatureExtractor(this.getAssets(), this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        getSongList();

        Collections.sort(songList, new Comparator<Song>(){
            public int compare(Song a, Song b){
                return a.getTitle().compareTo(b.getTitle());
            }
        });

        SongAdapter songAdt = new SongAdapter(this, songList);
        songView.setAdapter(songAdt);
        setController();

        activityRecognitionClient = new ActivityRecognitionClient(this);
        startActivityRecognition();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if(playIntent==null){
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    //connect to the service
    private ServiceConnection musicConnection = new ServiceConnection(){

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicBinder binder = (MusicBinder)service;
            //get service
            musicSrv = binder.getService();
            //pass list
            musicSrv.setList(songList);
            musicBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };

    @Override
    protected void onPause(){
        super.onPause();
        paused=true;
    }

    @Override
    protected void onResume(){
        super.onResume();
        if(paused){
            setController();
            paused=false;
        }
    }

    @Override
    protected void onStop() {
        controller.hide();
        super.onStop();
    }

    public void getSongList() {
        //retrieve song info
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

        if(musicCursor!=null && musicCursor.moveToFirst()){
            //get columns
            int titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int relativePath = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            int idColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);
            //add songs to list
            do {
                long thisId = musicCursor.getLong(idColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);
                String thisRelativePath = musicCursor.getString(relativePath);
                songList.add(new Song(thisId, thisTitle, thisArtist, thisRelativePath));
            }
            while (musicCursor.moveToNext());
        }
    }

    public void songPicked(View view){

        if (view.getParent() == findViewById(R.id.song_search)) {
            Set<String> set = new HashSet<String> (getSharedPreferences("prefs", Context.MODE_PRIVATE).
                    getStringSet(selected_playlist, new HashSet<String>()));
            if (!set.contains(((TextView)((LinearLayout) view).getChildAt(0)).getText() + "|" +
                    ((TextView)((LinearLayout) view).getChildAt(1)).getText())) {
                set.add(((TextView)((LinearLayout) view).getChildAt(0)).getText() + "|" +
                        ((TextView)((LinearLayout) view).getChildAt(1)).getText());
                getSharedPreferences("prefs", Context.MODE_PRIVATE).
                        edit().
                        putStringSet(selected_playlist, set).
                        apply();
            }
            setupPlaylistView();
        }
        else {

            int songPos = Integer.parseInt(view.getTag().toString());
            if (view.getParent() == findViewById(R.id.song_playlist)) {
                songPos = musicSrv.getSongPosn(
                        ((TextView)((LinearLayout) view).getChildAt(0)).getText().toString(),
                        ((TextView)((LinearLayout) view).getChildAt(1)).getText().toString());
            }

            musicSrv.setSong(songPos);
            musicSrv.playSong();
            if (playbackPaused) {
                setController();
                playbackPaused = false;
            }
            controller.show(0);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_shuffle:
                boolean shuffle = musicSrv.setShuffle();
                displayMessage("Shuffle " + (shuffle ? "On" : "Off"));
                break;
            case R.id.action_end:
                stopService(playIntent);
                musicSrv=null;
                System.exit(0);
                break;
                // TODO: handle implicit feedback
            case R.id.action_dislike:
                addRecord(musicSrv.getCurrentSongData(), DISLIKE, getCurrentActivity());
                displayMessage("You didn't like " + musicSrv.getCurrentSongData().getTitle());
                break;
            case R.id.action_like:
                // TODO: how to get features asynchronously and write them in the db once processing is done
//                addRecord(musicSrv.getCurrentSongData(), LIKE, getCurrentActivity());
                double[][] features = fex.extractFeatures(musicSrv.getCurrentSongData());
                displayMessage("You liked " + musicSrv.getCurrentSongData().getTitle());
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    public void displayMessage(String mess) {
        Snackbar.make(findViewById(R.id.coordinatorLayout), mess, Snackbar.LENGTH_SHORT).show();
    }

    private void addRecord(Song song, int feedback, int activityType) {
        // TODO: add location to saved data and time
        FeedbackDBreader dbHelper = new FeedbackDBreader(getApplicationContext());
        SQLiteDatabase db = dbHelper.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("songtitle", song.getTitle());
        //TODO: check if this is proper activity info
        values.put("activity", String.valueOf(activityType));
        values.put("value", feedback);

        long newRowId = db.insert("feedback", null, values);
    }

    @Override
    protected void onDestroy() {
        stopService(playIntent);
        musicSrv=null;
        super.onDestroy();
    }

    @Override
    public void pause() {
        playbackPaused=true;
        musicSrv.pausePlayer();
    }

    @Override
    public void seekTo(int pos) {
        musicSrv.seek(pos);
    }

    @Override
    public void start() {
        musicSrv.go();
    }

    @Override
    public int getDuration() {
        if(musicSrv!=null && musicBound && musicSrv.isPng()) {
            return musicSrv.getDur();
        }
        else {
            return 0;
        }
    }

    @Override
    public int getCurrentPosition() {
        if(musicSrv!=null && musicBound && musicSrv.isPng()){
            return musicSrv.getPosn();
        }
        else {
            return 0;
        }
    }


    @Override
    public boolean isPlaying() {
        if(musicSrv!=null && musicBound){
            return musicSrv.isPng();
        }
        return false;
    }

    @Override
    public int getBufferPercentage() {
        return 0;
    }

    @Override
    public boolean canPause() {
        return true;
    }

    @Override
    public boolean canSeekBackward() {
        return true;
    }

    @Override
    public boolean canSeekForward() {
        return true;
    }

    @Override
    public int getAudioSessionId() {
        return 0;
    }

    private void setController(){
        controller = new MusicController(this);
        controller.setPrevNextListeners(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playNext();
            }
        }, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                playPrev();
            }
        });
        controller.setMediaPlayer(this);
        controller.setAnchorView(findViewById(R.id.song_view));
        controller.setEnabled(true);
        controller.setActivated(true);
    }

    private void playNext(){
        musicSrv.playNext();
        if(playbackPaused){
            setController();
            playbackPaused=false;
        }
        controller.show(0);
    }

    private void playPrev(){
        musicSrv.playPrev();
        if(playbackPaused){
            setController();
            playbackPaused=false;
        }
        controller.show(0);
    }

    void startActivityRecognition() {
        Task<Void> task = activityRecognitionClient.requestActivityUpdates(
                DETECTION_INTERVAL_IN_MILLISECONDS,
                getActivityDetectionPendingIntent());

        task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void result) {
                Log.i("startActRecognition", "starting activity recognition");
            }
        });

        task.addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.w("startActRecognition", e);
            }
        });
    }

    private PendingIntent getActivityDetectionPendingIntent() {
        Intent intent = new Intent(this, DetectedActivitiesIntentService.class);

        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private int getCurrentActivity() {
        int defaultValue = -1;

        return getSharedPreferences("prefs", Context.MODE_PRIVATE).getInt("activity", defaultValue);
    }

    public void goToPlaylistMaker(View view) {
        setContentView(R.layout.playlist_maker);
    }

    public void goToPlaylist(View view) {
        switch (view.getId()) {
            case R.id.in_vehicle:
                selected_playlist = "in_vehicle";
                activityStr = "add song to 'on a bus/in a car'";
                break;
            case R.id.on_bicycle:
                selected_playlist = "on_bicycle";
                activityStr = "add song to 'riding a bike'";
                break;
            case R.id.walking:
                selected_playlist = "walking";
                activityStr = "add song to 'walking'";
                break;
            case R.id.running:
                selected_playlist = "running";
                activityStr = "add song to 'running'";
                break;
            case R.id.still:
                selected_playlist = "still";
                activityStr = "add song to 'sitting/standing'";
                break;
        }

        setupPlaylistView();
    }

    void setupPlaylistView() {
        setContentView(R.layout.playlist);
        Button btn = findViewById(R.id.add_song_button);
        btn.setText(activityStr);
        songPlaylistView = findViewById(R.id.song_playlist);
        songPlaylist = new ArrayList<>();

        Set<String> set = getSharedPreferences("prefs", Context.MODE_PRIVATE).
                getStringSet(selected_playlist, new HashSet<String>());

        for (String song : set) {
            songPlaylist.add(new Song(
                    musicSrv.getSongID(song.substring(0, song.indexOf('|')), song.substring(song.indexOf('|') + 1)),
                    song.substring(0, song.indexOf('|')),
                    song.substring(song.indexOf('|') + 1)));
        }

        Collections.sort(songPlaylist, new Comparator<Song>(){
            public int compare(Song a, Song b){
                return a.getTitle().compareTo(b.getTitle());
            }
        });

        SongAdapter songAdt = new SongAdapter(this, songPlaylist);
        songPlaylistView.setAdapter(songAdt);
    }

    public void goToSongFinder(View view) {
        setContentView(R.layout.song_finder);
        songSearchListView = findViewById(R.id.song_search);
        songSearchList = getRefreshedSearchList("");
        songSearchAdapter = new SongAdapter(this, songSearchList);
        songSearchListView.setAdapter(songSearchAdapter);

        searchInput = findViewById(R.id.search_input);
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                songSearchList = getRefreshedSearchList(s.toString());
                songSearchAdapter.setSongs(songSearchList);
                TextView txt = findViewById(R.id.no_songs_found_text);
                Button btn = findViewById(R.id.add_search_song);
                if (songSearchList.size() > 0) {
                    txt.setVisibility(TextView.GONE);
                    btn.setVisibility(TextView.GONE);
                }
                else {
                    txt.setVisibility(TextView.VISIBLE);
                    btn.setText("add " + s.toString());
                    btn.setVisibility(TextView.VISIBLE);
                }
                findViewById(R.id.song_search).invalidate();
            }
        });
    }

    public ArrayList getRefreshedSearchList(String searchStr) {
        ArrayList<Song> arrayList = new ArrayList<>();

        //retrieve song info
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

        if(musicCursor!=null && musicCursor.moveToFirst()){
            //get columns
            int titleColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media.TITLE);
            int idColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media._ID);
            int artistColumn = musicCursor.getColumnIndex
                    (android.provider.MediaStore.Audio.Media.ARTIST);

            Set<String> set = new HashSet<String> (getSharedPreferences("prefs", Context.MODE_PRIVATE).
                    getStringSet(selected_playlist, new HashSet<String>()));

            //add songs to list
            do {
                long thisId = musicCursor.getLong(idColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);

                if (!set.contains(thisTitle + "|" + thisArtist)) {
                    if (searchStr.length() == 0) {
                        arrayList.add(new Song(thisId, thisTitle, thisArtist));
                        continue;
                    }

                    if (searchStr.length() > 2) {
                        if (thisTitle.toLowerCase().contains(searchStr.toLowerCase())) {
                            arrayList.add(new Song(thisId, thisTitle, thisArtist));
                            continue;
                        }
                    }

                    if (thisTitle.toLowerCase().startsWith(searchStr.toLowerCase())) {
                        arrayList.add(new Song(thisId, thisTitle, thisArtist));
                    }
                }
            }
            while (musicCursor.moveToNext());
        }

        Collections.sort(arrayList, new Comparator<Song>(){
            public int compare(Song a, Song b){
                return a.getTitle().compareTo(b.getTitle());
            }
        });

        return arrayList;
    }

    public void saveSongAsString(View view) {
        String songStr = ((Button) view).getText().toString().substring(3);
        Set<String> set = new HashSet<String> (getSharedPreferences("prefs", Context.MODE_PRIVATE).
                getStringSet(selected_playlist + "_manual", new HashSet<String>()));
        if (!set.contains(songStr)) {
            set.add(songStr);
            getSharedPreferences("prefs", Context.MODE_PRIVATE).
                    edit().
                    putStringSet(selected_playlist + "_manual", set).
                    apply();
        }
        setupPlaylistView();
    }
}
