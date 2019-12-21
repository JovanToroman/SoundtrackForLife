package com.fri.soundtrackforlife;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.google.android.material.snackbar.Snackbar;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class FeedbackDBreader extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "SoundtrackForLife";
    public static final String FEEDBACK_TABLE_NAME = "feedback";
    public static final String PLAY_TABLE_NAME = "play";
    public static final Integer SONG_TITLE_COLUMN_ID = 1;
    public static final Integer ACTIVITY_COLUMN_ID = 2;
    public static final Integer COUNT_COLUMN_ID = 3;

    private MainActivity context;

    public FeedbackDBreader(MainActivity context) {
        super(context, DATABASE_NAME, null, 1);
        this.context = context;
        SQLiteDatabase db = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
        db.execSQL("create table IF NOT EXISTS "+ FEEDBACK_TABLE_NAME + " (_id integer primary key autoincrement, "
                + "value short not null,"
                + "songtitle text not null,"
                + "activity short not null,"
                + "location text,"
                + "feature1 float,"
                + "feature2 float,"
                + "feature3 float,"
                + "feature4 float,"
                + "feature5 float,"
                + "feature6 float,"
                + "feature7 float,"
                + "feature8 float,"
                + "created DATETIME DEFAULT CURRENT_TIMESTAMP"
                +");");

        db.execSQL("create table IF NOT EXISTS "+ PLAY_TABLE_NAME + " (_id integer primary key autoincrement, "
                + "songtitle text not null,"
                + "activity short not null,"
                + "count integer not null,"
                + "created DATETIME DEFAULT CURRENT_TIMESTAMP"
                +");");
    }

    Cursor retrieveSongPlayCounts(){
        SQLiteDatabase db = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
        Cursor c = db.rawQuery("SELECT _id, songtitle, activity, count FROM " + PLAY_TABLE_NAME, null);
        return c;
    }

    void updateSongPlayCount(String songTitle, Integer activityId) {
        SQLiteDatabase db = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
        Cursor c = db.rawQuery("SELECT _id, songtitle, activity, count FROM " + PLAY_TABLE_NAME
                + " WHERE songtitle='" + sqlify(songTitle) + "' and activity= " + activityId, null);
        if (c.moveToNext()) {
            int currentCount = c.getInt(COUNT_COLUMN_ID);
            int id = c.getInt(0);
            db.execSQL("UPDATE " + PLAY_TABLE_NAME + " SET count=" + ++currentCount + " WHERE _id=" + id);
        } else {
            db.execSQL("INSERT INTO " + PLAY_TABLE_NAME + " (songtitle, activity, count) " +
                    "VALUES ('" + sqlify(songTitle) + "', " + activityId + ", " + 1 + ")");
        }
    }

    private String sqlify(String input) {
        return input.replaceAll("'", "''");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public void sendFeedback() throws IOException {
        SQLiteDatabase db = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
        Cursor c = db.rawQuery("SELECT * FROM " + FEEDBACK_TABLE_NAME, null);
        StringBuilder b = new StringBuilder();
        b.append("{\"data\":[");
        while(c.moveToNext()) {
            b.append("{");
            b.append("\"id\":\"" + c.getString(0) + "\",");
            b.append("\"value\":\"" + c.getString(1) + "\",");
            b.append("\"songtitle\":\"" + c.getString(2) + "\",");
            b.append("\"activity\":\"" + c.getString(3) + "\",");
            b.append("\"location\":\"" + c.getString(4) + "\",");
            for (int i = 1; i < 9; i++) {
                b.append("\"feature" + i + "\":\"" + c.getString(i+4) + "\",");
            }
            b.append("\"created\":\"" + c.getString(13) + "\"},");
            // delete the last comma
            b.delete(b.length() - 1, b.length());
            b.append("}");
        }
        b.delete(b.length() - 1, b.length());
        b.append("]}");
        String data = b.toString();

        Request request = new Request.Builder().url(new String(MyBase64.decode("aHR0cHM6Ly9zb2xzdGluZ2VyLmNvbS9kYl91cGxvYWQucGhw")))
                .post(RequestBody.create(MediaType.parse("JSON"), data))
                .build();

        new OkHttpClient().newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                Log.d("feedback", "failure");
                displayMessage("Could not send now. Connect to the Internet and try again.");
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                Log.d("response",response.body().string());
                Log.d("feedback","success");
                if(response.code() == 200) {
                    db.execSQL("DELETE FROM " + FEEDBACK_TABLE_NAME);
                }
                displayMessage("Successfully sent feedback.");
            }
        });
    }

    public Cursor retrieveExistingFeedback(Song s) {
        SQLiteDatabase db = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
        return db.rawQuery("SELECT * FROM " + FEEDBACK_TABLE_NAME
                + " WHERE songtitle='" + s.getTitle() + "' AND feature1 IS NOT NULL", null);
    }

    public void displayMessage(String mess) {
        Snackbar.make(context.findViewById(R.id.coordinatorLayout), mess, Snackbar.LENGTH_LONG).show();
    }
}
