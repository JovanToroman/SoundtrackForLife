package com.example.soundtrackforlife;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class FeedbackDBreader extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "SoundtrackForLife.db";

    public FeedbackDBreader(Context context) {
        super(context, DATABASE_NAME, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {

    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
