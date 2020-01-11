package com.fri.soundtrackforlife;

import android.content.Context;
import android.content.res.AssetManager;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import de.daslaboratorium.machinelearning.classifier.bayes.BayesClassifier;

public class Classifier {
    // Create a new bayes classifier with string categories and string features.
    public Classifier(Context c) throws IOException, JSONException {

        AssetManager am = c.getApplicationContext().getAssets();
        InputStream inputStream = am.open("model_data.json");
        BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
        String line = reader.readLine();
        StringBuilder sb = new StringBuilder();
        while(line != null) {
            sb.append(line);
            line = reader.readLine();
        }
        String jsonString = sb.toString();
        JSONObject jsonObject = new JSONObject(jsonString);
        Iterator<String> feedbackKeys = jsonObject.getJSONObject("feedback").keys();
        Iterator<String> playlistKeys = jsonObject.getJSONObject("playlist").keys();

        List<String> dataLike = new ArrayList<>();
        List<String> dataDislike = new ArrayList<>();


        addValues(feedbackKeys, jsonObject, dataLike, dataDislike, "feedback");
        addValues(playlistKeys, jsonObject, dataLike, dataDislike, "playlist");

        List dataLikeTrain = new ArrayList<>(dataLike.subList(0, (int)(dataLike.size() * 0.8)));
        List dataLikeTest = new ArrayList<>(dataLike.subList((int)(dataLike.size() * 0.8) + 1, dataLike.size()));

        List dataDislikeTrain = new ArrayList<>(dataDislike.subList(0, (int)(dataDislike.size() * 0.8)));
        List dataDislikeTest = new ArrayList<>(dataDislike.subList((int)(dataDislike.size() * 0.8) + 1, dataDislike.size() - 1));

        de.daslaboratorium.machinelearning.classifier.Classifier<String, String> bayes = new BayesClassifier<>();

        // Two examples to learn from.
//        String[] positiveText = "I love sunny days".split("\\s");
//        String[] negativeText = "I hate rain".split("\\s");

    // Learn by classifying examples.
    // New categories can be added on the fly, when they are first used.
    // A classification consists of a category and a list of features
    // that resulted in the classification in that category.
        bayes.learn("like", dataLikeTrain);
        bayes.learn("dislike", dataDislikeTrain);


        int correct = 0;
        int total = 0;

        for (Object s: dataLikeTest
             ) {
            String result = (String)bayes.classify((List)s).getCategory();
            if ("like".equals(result)) {
                correct++;
            }
            total++;
        }

        for (Object s: dataDislikeTest
        ) {
            String result = (String)bayes.classify((List)s).getCategory();
            if ("dislike".equals(result)) {
                correct++;
            }
            total++;
        }

        Log.d("result", "Accuracy: " + correct / total);
        // Get more detailed classification result.
//        ((BayesClassifier<String, String>) bayes).classifyDetailed(
//                    Arrays.asList(unknownText1));

        // Change the memory capacity. New learned classifications (using
        // the learn method) are stored in a queue with the size given
        // here and used to classify unknown sentences.
        bayes.setMemoryCapacity(700);

    }

    private void addValues(Iterator<String> feedbackKeys, JSONObject jsonObject, List dataLike,
                           List dataDislike, String source) throws JSONException {
        while (feedbackKeys.hasNext()) {
            List<String> vals = new ArrayList();
            String key = feedbackKeys.next();
            JSONArray features = jsonObject.getJSONObject(source).getJSONObject(key).optJSONArray("features");
            if (features == null || features.length() == 0) {
                continue;
            }
            vals.add(features.getString(0));
            vals.add(features.getString(1));
            vals.add(features.getString(2));
            vals.add(features.getString(3));
            vals.add(features.getString(4));
            vals.add(features.getString(5));
            vals.add(features.getString(6));
            vals.add(features.getString(7));
            vals.add(jsonObject.getJSONObject(source).getJSONObject(key).getString("activity"));
            if ("dislike".equals(jsonObject.getJSONObject(source).getJSONObject(key).getString("feedback"))) {
                dataDislike.add(vals);
            } else {
                dataLike.add(vals);
            }
        }
    }
}
