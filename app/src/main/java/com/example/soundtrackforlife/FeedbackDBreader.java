package com.example.soundtrackforlife;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class FeedbackDBreader extends SQLiteOpenHelper {

    public static final String DATABASE_NAME = "SoundtrackForLife";
    public static final String TABLE_NAME = "feedback";

    private Context context;

    public FeedbackDBreader(Context context) {
        super(context, DATABASE_NAME, null, 1);
        this.context = context;
        SQLiteDatabase db = context.openOrCreateDatabase(DATABASE_NAME, Context.MODE_PRIVATE, null);
        db.execSQL("create table IF NOT EXISTS "+ TABLE_NAME + " (_id integer primary key autoincrement, "
                + "value short not null,"
                + "songtitle text not null,"
                + "activity short not null,"
                + "feature1 float,"
                + "feature2 float,"
                + "feature3 float,"
                + "feature4 float,"
                + "feature5 float,"
                + "feature6 float,"
                + "feature7 float,"
                + "feature8 float"
                +");");
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }
}
