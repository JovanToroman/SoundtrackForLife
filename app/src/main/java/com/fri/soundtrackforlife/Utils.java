package com.fri.soundtrackforlife;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.material.snackbar.Snackbar;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;

class Utils {

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };
    private static final int REQUEST_ACTIVITY = 2;
    private static String[] PERMISSIONS_ACTIVITY = {Manifest.permission.ACTIVITY_RECOGNITION};

    static Object fromString(String base64) throws IOException, ClassNotFoundException {
        byte[] data = MyBase64.decode(base64);
        ObjectInputStream ois = new ObjectInputStream(
                new ByteArrayInputStream(data));
        Object o = ois.readObject();
        ois.close();
        return o;
    }

    /**
     * Checks if the app has permission to write to device storage. If the app does not have
     * permission then the user will be prompted to grant permissions.
     * @param activity injected activity
     */
    static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    static void displayMessage(String mess, AppCompatActivity activity) {
        Snackbar.make(activity.findViewById(R.id.coordinatorLayout), mess, Snackbar.LENGTH_LONG)
                .show();
    }

    /**
     * Checks if the app has permission to determine user's activity. If the app does not have
     * permission then the user will be prompted to grant permissions.
     * @param activity injected activity
     */
    static void verifyActivityPermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity,
                Manifest.permission.ACTIVITY_RECOGNITION);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_ACTIVITY,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }
}
