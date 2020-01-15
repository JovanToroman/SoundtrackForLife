package com.fri.soundtrackforlife;

import android.content.Context;
import android.content.res.AssetManager;

import com.google.android.gms.location.DetectedActivity;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import de.daslaboratorium.machinelearning.classifier.Classifier;
import de.daslaboratorium.machinelearning.classifier.bayes.BayesClassifier;

class SongClassifier {

    private static Map<String, Double> activities;
    private Classifier<Double, Boolean> bayes;
    JSONObject dbUnique;


    SongClassifier(Context c) throws IOException, JSONException {

        dbUnique = new JSONObject(readJsonString(c, "unique_db.json"));

        initializeActivityMap();

        String jsonString = readJsonString(c, "model_data.json");
        JSONObject jsonObject = new JSONObject(jsonString);
        Iterator<String> feedbackKeys = jsonObject.getJSONObject("feedback").keys();
        Iterator<String> playlistKeys = jsonObject.getJSONObject("playlist").keys();

        List<List<Double>> dataLike = new ArrayList<>();
        List<List<Double>> dataDislike = new ArrayList<>();
        addValues(feedbackKeys, jsonObject, dataLike, dataDislike, "feedback");
        addValues(playlistKeys, jsonObject, dataLike, dataDislike, "playlist");

        bayes = new BayesClassifier<>();

        for (List<Double> l : dataLike){
            bayes.learn(true, l);
        }
        for (List<Double> d : dataDislike){
            bayes.learn(false, d);
        }


        // to evaluate model. Accuracy approximately 64%
//        System.out.println(evaluateModel(dataLike, dataDislike, bayes));

        bayes.setMemoryCapacity(700);
    }

    /**
     * The method for predicting if a user will like a certain song for a specific activity.
     * @param detectedActivity Google activity api enum
     * @param features two-dim double array containing values of eight reference features
     * @return a Boolean describing user's liking (true='like', false='dislike')
     */
    Boolean predictSongLiking(Integer detectedActivity, double[][] features) {
        List<Double> predictionFeatures = new ArrayList<>();
        if(features != null && features[0] != null && features[0].length > 0) {
            for(int i = 0; i < 8; i++) {
                predictionFeatures.add(features[i][0]);
            }
        }
        predictionFeatures.add((double)detectedActivity);
        return bayes.classify(predictionFeatures).getCategory();
    }

    private String readJsonString(Context c, String fileName) throws IOException {
        AssetManager am = c.getApplicationContext().getAssets();
        InputStream inputStream = am.open(fileName);
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line = reader.readLine();
        StringBuilder sb = new StringBuilder();
        while(line != null) {
            sb.append(line);
            line = reader.readLine();
        }
        return sb.toString();
    }

    private void addValues(Iterator<String> feedbackKeys, JSONObject jsonObject, List<List<Double>> dataLike,
                           List<List<Double>> dataDislike, String source) throws JSONException {
        while (feedbackKeys.hasNext()) {
            List<Double> vals = new ArrayList<>();
            String key = feedbackKeys.next();
            JSONArray features = jsonObject.getJSONObject(source).getJSONObject(key).optJSONArray("features");
            if (features == null || features.length() == 0) {
                continue;
            }
            vals.add(features.getDouble(0));
            vals.add(features.getDouble(1));
            vals.add(features.getDouble(2));
            vals.add(features.getDouble(3));
            vals.add(features.getDouble(4));
            vals.add(features.getDouble(5));
            vals.add(features.getDouble(6));
            vals.add(features.getDouble(7));
            vals.add(activities.get(jsonObject.getJSONObject(source).getJSONObject(key).getString("activity")));
            if ("dislike".equals(jsonObject.getJSONObject(source).getJSONObject(key).getString("feedback"))) {
                dataDislike.add(vals);
            } else {
                dataLike.add(vals);
            }
        }
    }

    private double evaluateModel(List<List<Double>> dataLike, List<List<Double>> dataDislike, Classifier<Double, Boolean> bayes) {
        List<List<Double>> dataLikeTrain = new ArrayList<>(dataLike.subList(0, (int)(dataLike.size() * 0.8)));
        List<List<Double>> dataLikeTest = new ArrayList<>(dataLike.subList((int)(dataLike.size() * 0.8), dataLike.size()));

        List<List<Double>> dataDislikeTrain = new ArrayList<>(dataDislike.subList(0, (int)(dataDislike.size() * 0.8)));
        List<List<Double>> dataDislikeTest = new ArrayList<>(dataDislike.subList((int)(dataDislike.size() * 0.8), dataDislike.size()));

        for (List<Double> l : dataLikeTrain){
            bayes.learn(true, l);
        }
        for (List<Double> d : dataDislikeTrain){
            bayes.learn(false, d);
        }


        int correct = 0;
        int total = 0;

        for (List<Double> s: dataLikeTest
        ) {
            Boolean result = true;
            if (result) {
                correct++;
            }
            total++;
        }

        for (List<Double> s: dataDislikeTest
        ) {
            Boolean result = true;
            if (!result) {
                correct++;
            }
            total++;
        }
        return (double)correct/total;
    }

    private void initializeActivityMap() {
        activities = new HashMap<>();
        activities.put("IN_VEHICLE", (double)DetectedActivity.IN_VEHICLE);
        activities.put("ON_BICYCLE", (double)DetectedActivity.ON_BICYCLE);
        activities.put("WALKING", (double)DetectedActivity.WALKING);
        activities.put("RUNNING", (double)DetectedActivity.RUNNING);
        activities.put("ON_FOOT", (double) DetectedActivity.ON_FOOT);
        activities.put("UNKNOWN", (double) DetectedActivity.UNKNOWN);
        activities.put("TILTING", (double) DetectedActivity.TILTING);
        activities.put("STILL", (double) DetectedActivity.STILL);
    }

    public double[][] getExistingFeatures(String title) {
        double[][] feats = new double[8][1];
        try {
            if (dbUnique.has(title)) {
                String[] vals = dbUnique.get(title).toString().replace("[", "").replace("]", "").split(",");
                for (int i = 0; i < 8; i++) {
                    feats[i][0] = Double.valueOf(vals[i]);
                }
            }
        }
        catch (Exception e) {
            e.printStackTrace();
        }

        return feats;
    }
}