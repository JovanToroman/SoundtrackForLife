package com.example.soundtrackforlife;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class FeedbackDBreader extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "SoundtrackForLife";
    public static final String FEEDBACK_TABLE_NAME = "feedback";
    public static final String PLAY_TABLE_NAME = "play";
    public static final Integer SONG_TITLE_COLUMN_ID = 1;
    public static final Integer ACTIVITY_COLUMN_ID = 2;
    public static final Integer COUNT_COLUMN_ID = 3;

    private Context context;

    public FeedbackDBreader(Context context) {
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
                + " WHERE songtitle='" + songTitle + "' and activity=" + activityId, null);
        if (c.moveToNext()) {
            int currentCount = c.getInt(COUNT_COLUMN_ID);
            int id = c.getInt(0);
            db.execSQL("UPDATE " + PLAY_TABLE_NAME + " SET count=" + ++currentCount + " WHERE _id=" + id);
        } else {
            db.execSQL("INSERT INTO " + PLAY_TABLE_NAME + " (songtitle, activity, count) " +
                    "VALUES ('" + songTitle + "', " + activityId + ", " + 1 + ")");
        }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
