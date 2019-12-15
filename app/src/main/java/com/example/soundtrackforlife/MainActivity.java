package com.example.soundtrackforlife;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.Looper;
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
import com.google.android.gms.location.DetectedActivity;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import android.widget.MediaController.MediaPlayerControl;

import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements MediaPlayerControl {
    
    public static final int LIKE = 1;
    public static final int DISLIKE = 0;
    long UPDATE_INTERVAL_IN_MILLISECONDS = 360 * 1000;

    private ArrayList<Song> songList;
    private ListView songView;
    private MusicService musicSrv;
    private Intent playIntent;
    private boolean musicBound=false;
    private MusicController controller;
    private boolean paused=false, playbackPaused=false;
    final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 0;
    final int MY_REQUEST_PERMISSIONS_REQUEST_CODE = 2;
    private ActivityRecognitionClient activityRecognitionClient;
    long DETECTION_INTERVAL_IN_MILLISECONDS = 30 * 1000;
    private FeatureExtractor fex;
    private ArrayList<Song> songPlaylist;
    private ListView songPlaylistView;
    private ArrayList<Song> songSearchList;
    private ListView songSearchListView;
    private TextInputEditText searchInput;
    private SongAdapter songSearchAdapter;
    String selected_playlist;
    String activityStr;
    FirebaseDatabase firebaseDatabase;
    DatabaseReference databaseReference;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private FeedbackDBreader feedbackDBreader;

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
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, MY_REQUEST_PERMISSIONS_REQUEST_CODE);
        }

        feedbackDBreader = new FeedbackDBreader(this);
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

        firebaseDatabase = FirebaseDatabase.getInstance();
        databaseReference = firebaseDatabase.getReference("songs");

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(LocationResult locationResult) {
                super.onLocationResult(locationResult);
                getSharedPreferences("prefs", Context.MODE_PRIVATE).
                        edit().
                        putString("locationLat", Double.toString(locationResult.getLastLocation().getLatitude())).
                        putString("locationLon", Double.toString(locationResult.getLastLocation().getLongitude())).
                        apply();
            }
        };
        locationRequest = new LocationRequest();
        locationRequest.setInterval(UPDATE_INTERVAL_IN_MILLISECONDS);
        locationRequest.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper());
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

        if (musicSrv.getMainActivity() == null) {
            musicSrv.setMainActivity(this);
        }

        updateCounts();

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
            AsyncTask.execute(() -> addRecordToFireBasePlaylist(
                    ((TextView)((LinearLayout) view).getChildAt(0)).getText().toString(),
                    ((TextView)((LinearLayout) view).getChildAt(1)).getText().toString(),
                    selected_playlist));
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
            incrementCounts();
        }
    }

    private void updateCounts() {
        Cursor countsCursor = feedbackDBreader.retrieveSongPlayCounts();

        if (countsCursor != null && countsCursor.moveToNext()) {
            do {
                String songTitle = countsCursor.getString(FeedbackDBreader.SONG_TITLE_COLUMN_ID);
                Integer activity = countsCursor.getInt(FeedbackDBreader.ACTIVITY_COLUMN_ID);
                Integer count = countsCursor.getInt(FeedbackDBreader.COUNT_COLUMN_ID);

                Map<Integer, Integer> counts = musicSrv.getSongByTitle(songTitle).getCounts();
                counts.put(activity, count);
            } while (countsCursor.moveToNext());
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
        // preventing npe on implicit feedback when feedback is given before any song is played
        if (musicSrv.getMainActivity() == null) {
            musicSrv.setMainActivity(this);
        }
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
            case R.id.action_dislike:
                Song currentSong = musicSrv.getCurrentSongData();
                AsyncTask.execute(() -> addRecordWithFeatures(currentSong, DISLIKE));
                displayMessage("You didn't like " + currentSong.getTitle());
                playNext();
                break;
            case R.id.action_like:
                AsyncTask.execute(() -> addRecordWithFeatures(musicSrv.getCurrentSongData(), LIKE));
                displayMessage("You liked " + musicSrv.getCurrentSongData().getTitle());
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    void addRecordWithFeatures(Song song, int feedback) {
        double[][] features = fex.extractFeatures(song);
        int activity = getCurrentActivity();
        long id = addRecord(song, feedback, activity, features);
        addRecordToFirebase(song, feedback, activity, features, id);
    }

    private long addRecord(Song song, int feedback, int activityType, double[][] features) {
        SQLiteDatabase db = feedbackDBreader.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("songtitle", song.getTitle());
        values.put("activity", String.valueOf(activityType));
        values.put("value", feedback);
        values.put("location", getCurrentLocationLatitude() + "," + getCurrentLocationLongitude());

        if(features != null && features[0] != null && features[0].length > 0) {
            for(int i = 0; i < 8; i++) {
                values.put("feature" + (i + 1), features[i][0]);
            }
        }

        long newRowId = db.insert("feedback", null, values);
        return newRowId;
    }

    public void displayMessage(String mess) {
        Snackbar.make(findViewById(R.id.coordinatorLayout), mess, Snackbar.LENGTH_LONG).show();
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

        incrementCounts();
    }

    private void playPrev(){
        musicSrv.playPrev();
        if(playbackPaused){
            setController();
            playbackPaused=false;
        }
        controller.show(0);

        incrementCounts();
    }

    private void incrementCounts() {
        int activityId = getCurrentActivity();
        Song currentSong = musicSrv.getCurrentSongData();
        int currentCount = currentSong.getCounts().get(activityId);
        currentSong.getCounts().put(activityId, ++currentCount);
        feedbackDBreader.updateSongPlayCount(currentSong.getTitle(), activityId);
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

    int getCurrentActivity() {
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
                    song.substring(song.indexOf('|') + 1),
                    musicSrv.getSongPath(song.substring(0, song.indexOf('|')), song.substring(song.indexOf('|') + 1))));
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
            int titleColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.TITLE);
            int relativePath = musicCursor.getColumnIndex(MediaStore.Audio.Media.DATA);
            int idColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media._ID);
            int artistColumn = musicCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST);

            Set<String> set = new HashSet<String> (getSharedPreferences("prefs", Context.MODE_PRIVATE).
                    getStringSet(selected_playlist, new HashSet<String>()));

            //add songs to list
            do {
                long thisId = musicCursor.getLong(idColumn);
                String thisTitle = musicCursor.getString(titleColumn);
                String thisArtist = musicCursor.getString(artistColumn);
                String thisRelativePath = musicCursor.getString(relativePath);

                if (!set.contains(thisTitle + "|" + thisArtist)) {
                    if (searchStr.length() == 0) {
                        arrayList.add(new Song(thisId, thisTitle, thisArtist, thisRelativePath));
                        continue;
                    }

                    if (searchStr.length() > 2) {
                        if (thisTitle.toLowerCase().contains(searchStr.toLowerCase())) {
                            arrayList.add(new Song(thisId, thisTitle, thisArtist, thisRelativePath));
                            continue;
                        }
                    }

                    if (thisTitle.toLowerCase().startsWith(searchStr.toLowerCase())) {
                        arrayList.add(new Song(thisId, thisTitle, thisArtist, thisRelativePath));
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
        addRecordToFirebaseManual(songStr);
        setupPlaylistView();
    }

    String getHour() {
        return Integer.toString(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    String getDay() {
        return Calendar.getInstance().getTime().toString().substring(0,3);
    }

    String getCurrentLocationLatitude() {
        return getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("locationLat", "0.0");
    }

    String getCurrentLocationLongitude() {
        return getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("locationLon", "0.0");
    }

    String getActivityString(int activity) {

        String activityStr = "";

        switch (activity) {
            case DetectedActivity.IN_VEHICLE:
                activityStr = "IN_VEHICLE";
                break;
            case DetectedActivity.ON_BICYCLE:
                activityStr = "ON_BICYCLE";
                break;
            case DetectedActivity.ON_FOOT:
                activityStr = "ON_FOOT";
                break;
            case DetectedActivity.STILL:
                activityStr = "STILL";
                break;
            case DetectedActivity.UNKNOWN:
                activityStr = "UNKNOWN";
                break;
            case DetectedActivity.TILTING:
                activityStr = "TILTING";
                break;
            case DetectedActivity.WALKING:
                activityStr = "WALKING";
                break;
            case DetectedActivity.RUNNING:
                activityStr = "RUNNING";
                break;
        }

        return activityStr;
    }

    void addRecordToFirebaseManual(String title) {
        DatabaseReference databaseReferenceManual = databaseReference.child("manual");
        databaseReferenceManual.push().setValue(title);
    }

    void addRecordToFireBasePlaylist(String title, String artist, String playlist) {
        DatabaseReference databaseReferencePlaylist = databaseReference.child("playlist");

        Song song = musicSrv.getSong(title, artist);
        if (song.getID() == -1) {
            return;
        }

        double[][] features = fex.extractFeatures(song);
        List<Double> feats = new ArrayList<>();
        if(features != null && features[0] != null && features[0].length > 0) {
            for(int i = 0; i < 8; i++) {
                feats.add(features[i][0]);
            }
        }

        String activityString = playlist.toUpperCase();
        String feedbackString = "playlist";

        List<Double> location = new ArrayList<>();

        List<String> time = new ArrayList<>();

        databaseReferencePlaylist.push().setValue(new SongFirebaseEntry(
                song.getTitle(), song.getArtist(), feedbackString, activityString, location, time, feats
        ));
    }

    void addRecordToFirebase(Song song, int feedback, int activityType, double[][] features, long id) {
        DatabaseReference databaseReferenceFeedback = databaseReference.child("feedback");

        List<Double> feats = new ArrayList<>();
        if(features != null && features[0] != null && features[0].length > 0) {
            for(int i = 0; i < 8; i++) {
                feats.add(features[i][0]);
            }
        }

        List<Double> location = new ArrayList<>();
        location.add(Double.parseDouble(getCurrentLocationLatitude()));
        location.add(Double.parseDouble(getCurrentLocationLongitude()));

        List<String> time = new ArrayList<>();
        time.add(getHour());
        time.add(getDay());

        String feedbackString = "";
        if (feedback == DISLIKE) {
            feedbackString = "dislike";
        }
        else {
            feedbackString = "like";
        }

        databaseReferenceFeedback.push().setValue(new SongFirebaseEntry(
                song.getTitle(), song.getArtist(), feedbackString, getActivityString(activityType),
                location, time, feats, id
        ));
    }
}
