package com.fri.soundtrackforlife;

import android.Manifest;
import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.MediaStore;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.MediaController.MediaPlayerControl;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.fri.soundtrackforlife.MusicService.MusicBinder;
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
import com.google.android.material.textfield.TextInputEditText;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static com.fri.soundtrackforlife.Utils.displayMessage;

public class MainActivity extends AppCompatActivity implements MediaPlayerControl {

    public static final int LIKE = 1;
    public static final int DISLIKE = 0;
    final int MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE = 0;
    final int MY_REQUEST_PERMISSIONS_REQUEST_CODE = 2;
    long UPDATE_INTERVAL_IN_MILLISECONDS = 360 * 1000;
    long DETECTION_INTERVAL_IN_MILLISECONDS = 5 * 1000;
    String selected_playlist;
    String activityStr;
    FirebaseDatabase firebaseDatabase;
    DatabaseReference databaseReference;
    SongClassifier songClassifier;
    Song lastFeedbackSong;
    private Map<String, Integer> playlistActivityCodes;
    private ArrayList<Song> songList;
    private ListView songView;
    private MusicService musicSrv;
    private Intent playIntent;
    private boolean musicBound = false;
    private MusicController controller;
    private ActivityRecognitionClient activityRecognitionClient;
    private FeatureExtractor fex;
    private ArrayList<Song> songPlaylist;
    private ListView songPlaylistView;
    private ArrayList<Song> songSearchList;
    private ListView songSearchListView;
    private TextInputEditText searchInput;
    private SongAdapter songSearchAdapter;
    private FusedLocationProviderClient fusedLocationClient;
    private LocationCallback locationCallback;
    private LocationRequest locationRequest;
    private FeedbackDBreader feedbackDBreader;
    private MenuItem backButton;
    private String currentScreen;
    private ArrayList<Song> recommendedSongList;
    private ImageView splashScreen;
    private TextView splashScreenText;
    private String displayedActivity;
    private Song displayedNextSong;
    //connect to the service
    private ServiceConnection musicConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            MusicBinder binder = (MusicBinder) service;
            //get service
            musicSrv = binder.getService();
            //pass list
            musicSrv.setList(songList);
            musicSrv.setSongPlaylist(generatePlaylists());
            musicBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            musicBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        songView = findViewById(R.id.song_list);
        splashScreen = findViewById(R.id.splash_screen);
        splashScreenText = findViewById(R.id.splash_screen_text);
        songList = new ArrayList<>();
        fex = new FeatureExtractor(this.getAssets(), this);

        checkPermissions();

        feedbackDBreader = new FeedbackDBreader(this);

        try {
            songClassifier = new SongClassifier(this);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
            if (songList.size() == 0) {
                songListSetup();
                AsyncTask.execute(() -> calculateSongFeatures());
            }
        }
        setController();

        activityRecognitionClient = new ActivityRecognitionClient(this);
        startActivityRecognition();

        firebaseDatabase = FirebaseDatabase.getInstance();
        databaseReference = firebaseDatabase.getReference("songs");

        setupLocationSensing();

        currentScreen = "main";

        initializeActivityMap();

        setupMediaPlayerRefreshing();
    }

    private void setupMediaPlayerRefreshing() {
        int interval = 1000 * 5; // 5 Second
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                try {
                    Thread.sleep(interval);
                    String activity = getActivityString(getCurrentActivity());
                    if (!activity.equals(displayedActivity)) {
                        displayedNextSong = songList.get(musicSrv.resolveNextSong());
                        displayedActivity = activity;
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                controller.refresh(songList, activity, displayedNextSong.getTitle(),
                                        musicSrv.getCurrentSongData().getTitle());
                            }
                        });

                    } else {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                controller.refresh(songList, activity, displayedNextSong.getTitle(),
                                        musicSrv.getCurrentSongData().getTitle());
                            }
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, 1000 * 15, interval);
    }

    private void songListSetup() {
        getSongList();
        Collections.sort(songList, new Comparator<Song>() {
            public int compare(Song a, Song b) {
                return a.getTitle().compareTo(b.getTitle());
            }
        });
        SongAdapter songAdt = new SongAdapter(this, songList);
        songView.setAdapter(songAdt);
    }

    private void initializeActivityMap() {
        playlistActivityCodes = new HashMap<>();
        playlistActivityCodes.put("in_vehicle", DetectedActivity.IN_VEHICLE);
        playlistActivityCodes.put("on_bicycle", DetectedActivity.ON_BICYCLE);
        playlistActivityCodes.put("walking", DetectedActivity.WALKING);
        playlistActivityCodes.put("running", DetectedActivity.RUNNING);
        playlistActivityCodes.put("still", DetectedActivity.STILL);
    }

    private void setupLocationSensing() {
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

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, MY_REQUEST_PERMISSIONS_REQUEST_CODE);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.INTERNET) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.INTERNET}, 3);
        }
        Utils.verifyActivityPermissions(this);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_READ_EXTERNAL_STORAGE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    if (songList.size() == 0) {
                        songListSetup();
                        AsyncTask.execute(() -> calculateSongFeatures());
                    }
                }
            }
        }
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        backButton = menu.findItem(R.id.action_back);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (playIntent == null) {
            playIntent = new Intent(this, MusicService.class);
            bindService(playIntent, musicConnection, Context.BIND_AUTO_CREATE);
            startService(playIntent);
        }
    }

    private Map<Integer, List<Song>> generatePlaylists() {
        Map<Integer, List<Song>> ret = new HashMap<>();
        for (String activity : SongClassifier.activities.keySet()) {
            List<Song> good = new ArrayList<>();
            int detectedActivity = SongClassifier.activities.get(activity).intValue();
            for (Song s : songList) {
                if (songClassifier.predictSongLiking(detectedActivity, s.getFeatures())) {
                    good.add(s);
                }
            }
            ret.put(detectedActivity, good);
        }
        return ret;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (controller.isSongPlaying()) {
            setControllerAnchor();
            controller.show(0);
        }
    }

    @Override
    protected void onStop() {
        controller.hideCustom();
        super.onStop();
    }

    public void getSongList() {
        //retrieve song info
        ContentResolver musicResolver = getContentResolver();
        Uri musicUri = android.provider.MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
        Cursor musicCursor = musicResolver.query(musicUri, null, null, null, null);

        if (musicCursor != null && musicCursor.moveToFirst()) {
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

    public void songPicked(View view) {

        if (musicSrv.getMainActivity() == null) {
            musicSrv.setMainActivity(this);
        }

        updateCounts();
        int songPos = Integer.parseInt(view.getTag().toString());

        musicSrv.setSong(songPos);
        musicSrv.playSong();
        setControllerAnchor();
        controller.setSongIsPlaying(true);
        controller.show(0);
        incrementCounts();

        initPlayerValues();
    }

    private void initPlayerValues() {
        displayedNextSong = songList.get(musicSrv.resolveNextSong());
        displayedActivity = getActivityString(getCurrentActivity());
        controller.refresh(songList, getActivityString(getCurrentActivity()),
                displayedNextSong.getTitle(),
                musicSrv.getCurrentSongData().getTitle());
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
        countsCursor.close();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        // preventing npe on implicit feedback when feedback is given before any song is played
        if (musicSrv.getMainActivity() == null) {
            musicSrv.setMainActivity(this);
        }
        if (item.getItemId() == R.id.action_end) { // allow exiting even if music library is empty
            stopService(playIntent);
            musicSrv = null;
            System.exit(0);
        }
        if (songLibraryEmpty()) { // don't allow for any other actions if library empty
            return super.onOptionsItemSelected(item);
        }
        if (item.getItemId() == R.id.action_dislike) {
            Song currentSong = musicSrv.getCurrentSongData();
            AsyncTask.execute(() -> addRecordWithFeatures(currentSong, DISLIKE));
            displayMessage("You didn't like " + currentSong.getTitle(), this);
            playNext();
        }
        if (item.getItemId() == R.id.action_like) {
            AsyncTask.execute(() -> addRecordWithFeatures(musicSrv.getCurrentSongData(), LIKE));
            displayMessage("You liked " + musicSrv.getCurrentSongData().getTitle(), this);
        }
        return super.onOptionsItemSelected(item);
    }

    void addRecordWithFeatures(Song song, int feedback) {
        if (checkLastFeedbackSong(song)) {
            setLastFeedbackSong(song);
            double[][] features = fex.extractFeatures(song);
            int activity = getCurrentActivity();
            long id = addRecord(song, feedback, activity, features);
            addRecordToFirebase(song, feedback, activity, features, id);
            songClassifier.addEntryToJSON(features, feedback, song.getTitle(), song.getArtist(), getActivityString(activity));
        }
    }

    private long addRecord(Song song, int feedback, int activityType, double[][] features) {
        SQLiteDatabase db = feedbackDBreader.getWritableDatabase();

        ContentValues values = new ContentValues();
        values.put("songtitle", song.getTitle());
        values.put("activity", String.valueOf(activityType));
        values.put("value", feedback);
        values.put("location", getCurrentLocationLatitude() + "," + getCurrentLocationLongitude());

        if (features != null && features[0] != null && features[0].length > 0) {
            for (int i = 0; i < 8; i++) {
                values.put("feature" + (i + 1), features[i][0]);
            }
        }

        long newRowId = db.insert("feedback", null, values);
        return newRowId;
    }

//    public void displayMessage(String mess) {
//        Snackbar.make(findViewById(R.id.coordinatorLayout), mess, Snackbar.LENGTH_LONG).show();
//    }

    @Override
    protected void onDestroy() {
        stopService(playIntent);
        musicSrv = null;
        super.onDestroy();
    }

    @Override
    public void pause() {
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
        if (musicSrv != null && musicBound && musicSrv.isPng()) {
            return musicSrv.getDur();
        } else {
            return 0;
        }
    }

    @Override
    public int getCurrentPosition() {
        if (musicSrv != null && musicBound && musicSrv.isPng()) {
            return musicSrv.getPosn();
        } else {
            return 0;
        }
    }


    @Override
    public boolean isPlaying() {
        if (musicSrv != null && musicBound) {
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

    private void setController() {
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
        setControllerAnchor();
        controller.setEnabled(true);
        controller.setActivated(true);
    }

    private void setControllerAnchor() {
        controller.setAnchorView(findViewById(R.id.song_view));
    }

    private void playNext() {
        try {
            Song currentSong = musicSrv.getCurrentSongData();
            int current = musicSrv.getPlayer().getCurrentPosition();
            int duration = musicSrv.getPlayer().getDuration();
            AsyncTask.execute(() -> addRecordWithFeatures(currentSong,
                    current < (duration / 2) ? MainActivity.DISLIKE : MainActivity.LIKE));
        } catch (Exception e) {
            Log.d("implicit_feedback", "Exception while implicit feedback");
        }
        if (displayedNextSong != null) {
            musicSrv.playNext(displayedNextSong);
        } else {
            musicSrv.playNext();
        }
        controller.show(0);

        incrementCounts();

        initPlayerValues();
    }

    private void playPrev() {
        musicSrv.playPrev();
        controller.show(0);

        incrementCounts();

        initPlayerValues();
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

    String getHour() {
        return Integer.toString(Calendar.getInstance().get(Calendar.HOUR_OF_DAY));
    }

    String getDay() {
        return Calendar.getInstance().getTime().toString().substring(0, 3);
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

        List<Double> feats = new ArrayList<>();

        String activityString = selected_playlist.toUpperCase();
        String feedbackString = "manual";

        List<Double> location = new ArrayList<>();

        List<String> time = new ArrayList<>();

        databaseReferenceManual.push().setValue(new SongFirebaseEntry(
                title, "", feedbackString, activityString, location, time, feats
        ));
    }

    void addRecordToFireBasePlaylist(String playlist,
                                     double[][] features, Song song) {
        DatabaseReference databaseReferencePlaylist = databaseReference.child("playlist");

        List<Double> feats = new ArrayList<>();
        if (features != null && features[0] != null && features[0].length > 0) {
            for (int i = 0; i < 8; i++) {
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
        if (features != null && features[0] != null && features[0].length > 0) {
            for (int i = 0; i < 8; i++) {
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
        } else {
            feedbackString = "like";
        }

        databaseReferenceFeedback.push().setValue(new SongFirebaseEntry(
                song.getTitle(), song.getArtist(), feedbackString, getActivityString(activityType),
                location, time, feats, id, getUserId()
        ));
    }

    public FeedbackDBreader getFeedbackDBreader() {
        return feedbackDBreader;
    }

    private void calculateSongFeatures() {
        System.out.println("Feature calculation started.");
        SQLiteDatabase db = feedbackDBreader.getWritableDatabase();
        for (Song s : songList) {
            try {
                Cursor c = feedbackDBreader.retrieveExistingFeatures(s);
                if (c.moveToNext()) {
                    System.out.println(s.getTitle() + " already in database.");
                    c.close();
                    continue;
                }
                c.close();
            } catch (Exception e) {
                e.printStackTrace();
            }

            double[][] features = songClassifier.getExistingFeatures(s.getTitle());
            if (features[0][0] == 0.0) {
                System.out.println(s.getTitle() + " no entry in unique_db.json!");
                features = fex.extractFeatures(s);
            } else {
                System.out.println(s.getTitle() + " successfully got features from unique_db.json!");
            }

            ContentValues values = new ContentValues();
            values.put("songtitle", s.getTitle());
            if (features != null && features[0] != null && features[0].length > 0) {
                for (int i = 0; i < 8; i++) {
                    values.put("feature" + (i + 1), features[i][0]);
                }
                db.insert("features", null, values);
                System.out.println(s.getTitle() + " features calculated.");
            } else {
                System.out.println(s.getTitle() + " features null!");
            }
            s.setFeatures(features);
        }
        db.close();
        System.out.println("Feature calculation completed.");

        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                splashScreen.setVisibility(View.GONE);
                splashScreenText.setVisibility(View.GONE);
            }
        });
    }

    private ArrayList<Song> getRecommendedSongList(double[] features) {
        recommendedSongList = new ArrayList<>();
        Cursor c = feedbackDBreader.getSongsWithFeatures();
        double score;
        Song song;

        while (c.moveToNext()) {
            score = 0;
            for (int i = 0; i < 8; i++) {
                score += Math.abs(features[i] - c.getDouble(i + 2)) / features[i];
            }
            // TODO: if score higher than some threshold, do not add to list
            song = musicSrv.getSongByTitle(c.getString(1));
            song.setScore(score);
            recommendedSongList.add(song);
        }

        c.close();
        Collections.sort(recommendedSongList, new Comparator<Song>() {
            public int compare(Song a, Song b) {
                return Double.compare(a.getScore(), b.getScore());
            }
        });

        return recommendedSongList;
    }

    /**
     * This is the main method which is invoked when "PLAY!" button is clicked.
     * @param view injected view
     */
    public void recommendSong(View view) {
        if (songLibraryEmpty()) {
            return;
        }
        if (musicSrv.getMainActivity() == null) {
            musicSrv.setMainActivity(this); // set a reference to this activity on music player
        }
        if (displayedNextSong != null) {
            musicSrv.playNext(displayedNextSong); // play predetermined song
        } else {
            musicSrv.playNext(); // play song which is to be determined
        }
        controller.show(0);
        incrementCounts();
        initPlayerValues();
    }

    /**
     * Handles cases when there are no songs in user's music library to avoid
     * {@link IndexOutOfBoundsException}.
     * @return true if list is not empty and false if it is empty.
     */
    private boolean songLibraryEmpty() {
        if (songList.isEmpty()) {
            displayMessage("Please add some songs to your music library", this);
            return true;
        }
        return false;
    }

    public void setLastFeedbackSong(Song song) {
        lastFeedbackSong = song;
    }

    public boolean checkLastFeedbackSong(Song song) {
        return lastFeedbackSong == null || lastFeedbackSong != song;
    }

    private String getUserId() {

        String userId = getSharedPreferences("prefs", Context.MODE_PRIVATE).getString("userid", "none");
        if (userId.equals("none")) {
            userId = "user-" + new Date().getTime();
            getSharedPreferences("prefs", Context.MODE_PRIVATE).
                    edit().
                    putString("userid", userId).
                    apply();
        }

        return userId;
    }
}
