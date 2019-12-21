package com.fri.soundtrackforlife;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;


import jAudioFeatureExtractor.ACE.DataTypes.Batch;
import jAudioFeatureExtractor.Aggregators.Mean;
import jAudioFeatureExtractor.Aggregators.MultipleFeatureHistogram;
import jAudioFeatureExtractor.Aggregators.StandardDeviation;
import jAudioFeatureExtractor.AudioFeatures.AreaMoments;
import jAudioFeatureExtractor.AudioFeatures.AreaMomentsBeatHistogram;
import jAudioFeatureExtractor.AudioFeatures.AreaMomentsLogConstantQ;
import jAudioFeatureExtractor.AudioFeatures.AreaMomentsMFCC;
import jAudioFeatureExtractor.AudioFeatures.AreaPolynomialApproximationConstantQMFCC;
import jAudioFeatureExtractor.AudioFeatures.AreaPolynomialApproximationLogConstantQ;
import jAudioFeatureExtractor.AudioFeatures.BeatHistogram;
import jAudioFeatureExtractor.AudioFeatures.BeatHistogramLabels;
import jAudioFeatureExtractor.AudioFeatures.BeatSum;
import jAudioFeatureExtractor.AudioFeatures.Chroma;
import jAudioFeatureExtractor.AudioFeatures.Compactness;
import jAudioFeatureExtractor.AudioFeatures.ConstantQ;
import jAudioFeatureExtractor.AudioFeatures.ConstantQMFCC;
import jAudioFeatureExtractor.AudioFeatures.FFTBinFrequencies;
import jAudioFeatureExtractor.AudioFeatures.FractionOfLowEnergyWindows;
import jAudioFeatureExtractor.AudioFeatures.HarmonicSpectralCentroid;
import jAudioFeatureExtractor.AudioFeatures.HarmonicSpectralFlux;
import jAudioFeatureExtractor.AudioFeatures.HarmonicSpectralSmoothness;
import jAudioFeatureExtractor.AudioFeatures.LPC;
import jAudioFeatureExtractor.AudioFeatures.LogConstantQ;
import jAudioFeatureExtractor.AudioFeatures.MFCC;
import jAudioFeatureExtractor.AudioFeatures.MagnitudeSpectrum;
import jAudioFeatureExtractor.AudioFeatures.PeakFinder;
import jAudioFeatureExtractor.AudioFeatures.PowerSpectrum;
import jAudioFeatureExtractor.AudioFeatures.RMS;
import jAudioFeatureExtractor.AudioFeatures.RelativeDifferenceFunction;
import jAudioFeatureExtractor.AudioFeatures.SpectralCentroid;
import jAudioFeatureExtractor.AudioFeatures.SpectralFlux;
import jAudioFeatureExtractor.AudioFeatures.SpectralRolloffPoint;
import jAudioFeatureExtractor.AudioFeatures.SpectralVariability;
import jAudioFeatureExtractor.AudioFeatures.StrengthOfStrongestBeat;
import jAudioFeatureExtractor.AudioFeatures.StrongestBeat;
import jAudioFeatureExtractor.AudioFeatures.StrongestFrequencyViaFFTMax;
import jAudioFeatureExtractor.AudioFeatures.StrongestFrequencyViaSpectralCentroid;
import jAudioFeatureExtractor.AudioFeatures.StrongestFrequencyViaZeroCrossings;
import jAudioFeatureExtractor.AudioFeatures.TraditionalAreaMoments;
import jAudioFeatureExtractor.AudioFeatures.ZernikeMoments;
import jAudioFeatureExtractor.AudioFeatures.ZernikeMomentsBeatHistogram;
import jAudioFeatureExtractor.AudioFeatures.ZeroCrossings;
import jAudioFeatureExtractor.DataModel;
import javazoom.jl.converter.Converter;
import javazoom.jl.decoder.JavaLayerException;

public class FeatureExtractor {

    public static final String STORAGE_EMULATED_0_DOWNLOAD = "/storage/emulated/0/Download/";
    private AssetManager am;
    private MainActivity ma;

    public FeatureExtractor (AssetManager am, MainActivity ma) {
        this.am = am;
        this.ma = ma;
    }

    /**
     * This method is used to extract music features from songs. It gets the path of a song from its
     * relativePath attribute. It works for MP3 format ONLY.
     * @param s
     */
    public double[][] extractFeatures(Song s) {

        Utils.verifyStoragePermissions(ma);

        Batch batch = null;

        try {

            double[][] existingFeedback = reuseExistingFeedback(s);
            if (existingFeedback != null) {
                return existingFeedback;
            }

            batch = createBatch();

            setupDataModel(batch);

            File inputFile = conjureInputFile(s.getRelativePath());

            batch.setRecordings(new File[]{inputFile});
            batch.execute();

            inputFile.delete();

        } catch (Exception e) {
            e.printStackTrace();
        }

        //get results
        double[][] results = extractResults(batch);
        System.out.println("done");

        return results;
    }

    private double[][] reuseExistingFeedback(Song s) {
        double[][] ret = new double[8][1];
        Cursor c = ma.getFeedbackDBreader().retrieveExistingFeedback(s);
        if(c.moveToNext()) {
            for (int i = 0; i < 8; i++) {
                ret[i][0] = c.getDouble(i + 5);
            }
            return ret;
        } else {
            return null;
        }
    }

    private double[][] extractResults(Batch batch) {
        // if batch null due to exception or container null due to exception in extracting feats,
        // return empty multi-dim. array to not break app
        return batch == null || batch.getDataModel().container == null
                ? new double[][]{{}}
                : batch.getDataModel().container.getResults();
    }

    private File conjureInputFile(String relativePath) throws JavaLayerException, IOException {
        // take only part of original song because phone can't handle whole song and store it in
        // a temporary file
        File f = new File(relativePath);
        InputStream fis = new FileInputStream(f);
        OutputStream o = new FileOutputStream(STORAGE_EMULATED_0_DOWNLOAD + "temp_out");
        int size = fis.available();
        for(int i = size / 2; i < 6 * size / 10; i++) {
            o.write(fis.read());
        }

        // convert small piece of a song to .wav format whcih we can process (we can also process
        // .aiff, but decided to implement on .wav since it is detected by out music player
        Converter c = new Converter();
        c.convert(STORAGE_EMULATED_0_DOWNLOAD + "temp_out", STORAGE_EMULATED_0_DOWNLOAD + "temp_proc.wav");
        File check = new File(STORAGE_EMULATED_0_DOWNLOAD + "temp_proc.wav");

        // housekeeping
        fis.close();
        o.close();
        File temp = new File(STORAGE_EMULATED_0_DOWNLOAD + "temp_out");
        temp.delete();

        return check;
    }


    private Batch createBatch() throws Exception {
        Object[] features = new Object[3];
        List l1 = new LinkedList();
        l1.add(new MagnitudeSpectrum());
        l1.add(new PowerSpectrum());
        l1.add(new FFTBinFrequencies());
        l1.add(new SpectralCentroid());
        l1.add(new SpectralRolloffPoint());
        l1.add(new ZernikeMoments());
        l1.add(new ZernikeMomentsBeatHistogram());
        l1.add(new SpectralFlux());
        l1.add(new Compactness());
        l1.add(new SpectralVariability());
        l1.add(new RMS());
        l1.add(new FractionOfLowEnergyWindows());
        l1.add(new ZeroCrossings());
        l1.add(new BeatHistogram());
        l1.add(new BeatHistogramLabels());
        l1.add(new StrongestBeat());
        l1.add(new BeatSum());
        l1.add(new StrengthOfStrongestBeat());
        l1.add(new StrongestFrequencyViaZeroCrossings());
        l1.add(new StrongestFrequencyViaSpectralCentroid());
        l1.add(new StrongestFrequencyViaFFTMax());
        l1.add(new MFCC());
        l1.add(new ConstantQ());
        l1.add(new LPC());
        l1.add(new PeakFinder());
        l1.add(new HarmonicSpectralCentroid());
        l1.add(new HarmonicSpectralFlux());
        l1.add(new HarmonicSpectralSmoothness());
        l1.add(new RelativeDifferenceFunction());
        l1.add(new AreaMoments());
        l1.add(new AreaMomentsMFCC());
        l1.add(new AreaMomentsLogConstantQ());
        l1.add(new AreaMomentsBeatHistogram());
        l1.add(new ConstantQ());
        l1.add(new LogConstantQ());
        l1.add(new ConstantQMFCC());
        l1.add(new Chroma());
        l1.add(new AreaPolynomialApproximationConstantQMFCC());
        l1.add(new AreaPolynomialApproximationLogConstantQ());
        l1.add(new TraditionalAreaMoments());
        l1.add(new MagnitudeSpectrum());

        List l2 = new LinkedList();
        for (int i = 0; i<41; i++) {
            if(i == 34)
                l2.add(true);
            else l2.add(false);
        }
        List l3 = new LinkedList();
        l3.add(new Mean());
        l3.add(new StandardDeviation());
        l3.add(new jAudioFeatureExtractor.Aggregators.AreaMoments());
        l3.add(new jAudioFeatureExtractor.Aggregators.MFCC());
        l3.add(new MultipleFeatureHistogram());
        l3.add(new jAudioFeatureExtractor.Aggregators.AreaPolynomialApproximation());
        l3.add(new jAudioFeatureExtractor.Aggregators.ZernikeMoments());

        features[0] = l1;
        features[1] = l2;
        features[2] = l3;

        String settingsEncoded = "rO0ABXVyABNbTGphdmEubGFuZy5PYmplY3Q7kM5YnxBzKWwCAAB4cAAAAAx0AAM1MTJ0AAMwLjBzcgAQamF2YS5sYW5nLkRvdWJsZYCzwkopa/sEAgABRAAFdmFsdWV4cgAQamF2YS5sYW5nLk51bWJlcoaslR0LlOCLAgAAeHBAz0AAAAAAAHNyABFqYXZhLmxhbmcuQm9vbGVhbs0gcoDVnPruAgABWgAFdmFsdWV4cABzcQB+AAcAc3EAfgAHAXQAA0FDRXNyABFqYXZhLnV0aWwuSGFzaE1hcAUH2sHDFmDRAwACRgAKbG9hZEZhY3RvckkACXRocmVzaG9sZHhwP0AAAAAAAMB3CAAAAQAAAACcdAA0RGVyaXZhdGl2ZSBvZiBTdGFuZGFyZCBEZXZpYXRpb24gb2YgUm9vdCBNZWFuIFNxdWFyZXNxAH4ABwB0ABhSdW5uaW5nIE1lYW4gb2YgQmVhdCBTdW1xAH4AD3QALlJ1bm5pbmcgTWVhbiBvZiBQZWFrIEJhc2VkIFNwZWN0cmFsIFNtb290aG5lc3NxAH4AD3QAEURlcml2YXRpdmUgb2YgTFBDcQB+AA90ACxTdGFuZGFyZCBEZXZpYXRpb24gb2YgQXJlYSBNZXRob2Qgb2YgTW9tZW50c3EAfgAPdAAeUnVubmluZyBNZWFuIG9mIFN0cm9uZ2VzdCBCZWF0cQB+AA90ACZTdHJvbmdlc3QgRnJlcXVlbmN5IFZpYSBaZXJvIENyb3NzaW5nc3EAfgAPdAAsUnVubmluZyBNZWFuIG9mIFJlbGF0aXZlIERpZmZlcmVuY2UgRnVuY3Rpb25xAH4AD3QAEVNwZWN0cmFsIENlbnRyb2lkc3EAfgAHAXQAH0FyZWEgTWV0aG9kIG9mIE1vbWVudHMgb2YgTUZDQ3NxAH4AD3QAIkRlcml2YXRpdmUgb2YgU3BlY3RyYWwgVmFyaWFiaWxpdHlxAH4AD3QAJlN0YW5kYXJkIERldmlhdGlvbiBvZiBSb290IE1lYW4gU3F1YXJlcQB+AA90ABtEZXJpdmF0aXZlIG9mIFNwZWN0cmFsIEZsdXhxAH4AD3QAFkFyZWEgTWV0aG9kIG9mIE1vbWVudHNxAH4AD3QAR0Rlcml2YXRpdmUgb2YgU3RhbmRhcmQgRGV2aWF0aW9uIG9mICBUcmFkaXRpb25hbCBBcmVhIE1ldGhvZCBvZiBNb21lbnRzcQB+AA90AC9EZXJpdmF0aXZlIG9mIFJ1bm5pbmcgTWVhbiBvZiBTcGVjdHJhbCBDZW50cm9pZHEAfgAPdAAaU3RhbmRhcmQgRGV2aWF0aW9uIG9mIE1GQ0NxAH4AD3QAPkRlcml2YXRpdmUgb2YgU3RhbmRhcmQgRGV2aWF0aW9uIG9mIFN0cmVuZ3RoIE9mIFN0cm9uZ2VzdCBCZWF0cQB+AA90ADREZXJpdmF0aXZlIG9mIFN0cm9uZ2VzdCBGcmVxdWVuY3kgVmlhIFplcm8gQ3Jvc3NpbmdzcQB+AA90AEFEZXJpdmF0aXZlIG9mIFJ1bm5pbmcgTWVhbiBvZiBTdHJvbmdlc3QgRnJlcXVlbmN5IFZpYSBGRlQgTWF4aW11bXEAfgAPdAA8RGVyaXZhdGl2ZSBvZiBSdW5uaW5nIE1lYW4gb2YgUGVhayBCYXNlZCBTcGVjdHJhbCBTbW9vdGhuZXNzcQB+AA90ACVBcmVhIE1ldGhvZCBvZiBNb21lbnRzIEJlYXQgSGlzdG9ncmFtcQB+AA90ACRTdGFuZGFyZCBEZXZpYXRpb24gb2YgU3Ryb25nZXN0IEJlYXRxAH4AD3QALlJ1bm5pbmcgTWVhbiBvZiBGcmFjdGlvbiBPZiBMb3cgRW5lcmd5IFdpbmRvd3NxAH4AD3QAOkRlcml2YXRpdmUgb2YgU3RhbmRhcmQgRGV2aWF0aW9uIG9mIFNwZWN0cmFsIFJvbGxvZmYgUG9pbnRxAH4AD3QANFN0YW5kYXJkIERldmlhdGlvbiBvZiBGcmFjdGlvbiBPZiBMb3cgRW5lcmd5IFdpbmRvd3NxAH4AD3QALkRlcml2YXRpdmUgb2YgUnVubmluZyBNZWFuIG9mIFJvb3QgTWVhbiBTcXVhcmVxAH4AD3QADlBvd2VyIFNwZWN0cnVtcQB+AA90AEdEZXJpdmF0aXZlIG9mIFN0YW5kYXJkIERldmlhdGlvbiBvZiBTdHJvbmdlc3QgRnJlcXVlbmN5IFZpYSBGRlQgTWF4aW11bXEAfgAPdAAWUnVubmluZyBNZWFuIG9mIENocm9tYXEAfgAPdAAqRGVyaXZhdGl2ZSBvZiBTdGFuZGFyZCBEZXZpYXRpb24gb2YgQ2hyb21hcQB+AA90AEREZXJpdmF0aXZlIG9mIFJ1bm5pbmcgTWVhbiBvZiBTdHJvbmdlc3QgRnJlcXVlbmN5IFZpYSBaZXJvIENyb3NzaW5nc3EAfgAPdAAnRGVyaXZhdGl2ZSBvZiBTdGFuZGFyZCBEZXZpYXRpb24gb2YgTFBDcQB+AA90ABBMb2cgb2YgQ29uc3RhbnRRcQB+AA90ABpTdHJlbmd0aCBPZiBTdHJvbmdlc3QgQmVhdHEAfgAYdAA5UnVubmluZyBNZWFuIG9mIFN0cm9uZ2VzdCBGcmVxdWVuY3kgVmlhIFNwZWN0cmFsIENlbnRyb2lkcQB+AA90ADxEZXJpdmF0aXZlIG9mIFJ1bm5pbmcgTWVhbiBvZiBGcmFjdGlvbiBPZiBMb3cgRW5lcmd5IFdpbmRvd3NxAH4AD3QASkRlcml2YXRpdmUgb2YgU3RhbmRhcmQgRGV2aWF0aW9uIG9mIFN0cm9uZ2VzdCBGcmVxdWVuY3kgVmlhIFplcm8gQ3Jvc3NpbmdzcQB+AA90AAhCZWF0IFN1bXEAfgAPdABBRGVyaXZhdGl2ZSBvZiBSdW5uaW5nIE1lYW4gb2YgIFRyYWRpdGlvbmFsIEFyZWEgTWV0aG9kIG9mIE1vbWVudHNxAH4AD3QAOERlcml2YXRpdmUgb2YgU3RhbmRhcmQgRGV2aWF0aW9uIG9mIFNwZWN0cmFsIFZhcmlhYmlsaXR5cQB+AA90ADJEZXJpdmF0aXZlIG9mIFN0YW5kYXJkIERldmlhdGlvbiBvZiBaZXJvIENyb3NzaW5nc3EAfgAPdAAOQmVhdCBIaXN0b2dyYW1xAH4AD3QAA0xQQ3EAfgAPdAAmRGVyaXZhdGl2ZSBvZiBSdW5uaW5nIE1lYW4gb2YgQmVhdCBTdW1xAH4AD3QAEkRlcml2YXRpdmUgb2YgTUZDQ3EAfgAPdAAmUnVubmluZyBNZWFuIG9mIFNwZWN0cmFsIFJvbGxvZmYgUG9pbnRxAH4AD3QACUNvbnN0YW50UXEAfgAPdAAfRGVyaXZhdGl2ZSBvZiBTcGVjdHJhbCBDZW50cm9pZHEAfgAPdAAqRGVyaXZhdGl2ZSBvZiBSZWxhdGl2ZSBEaWZmZXJlbmNlIEZ1bmN0aW9ucQB+AA90ADVEZXJpdmF0aXZlIG9mIFN0YW5kYXJkIERldmlhdGlvbiBvZiBTcGVjdHJhbCBDZW50cm9pZHEAfgAPdAAsRGVyaXZhdGl2ZSBvZiBGcmFjdGlvbiBPZiBMb3cgRW5lcmd5IFdpbmRvd3NxAH4AD3QAI1N0YW5kYXJkIERldmlhdGlvbiBvZiBTcGVjdHJhbCBGbHV4cQB+AA90AChEZXJpdmF0aXZlIG9mIFN0YW5kYXJkIERldmlhdGlvbiBvZiBNRkNDcQB+AA90ACZBcmVhIE1ldGhvZCBvZiBNb21lbnRzIENvbnN0YW50LVEgTUZDQ3EAfgAPdABDRGVyaXZhdGl2ZSBvZiBTdGFuZGFyZCBEZXZpYXRpb24gb2YgUGFydGlhbCBCYXNlZCBTcGVjdHJhbCBDZW50cm9pZHEAfgAPdAAsU3RhbmRhcmQgRGV2aWF0aW9uIG9mIFNwZWN0cmFsIFJvbGxvZmYgUG9pbnRxAH4AD3QAIVN0YW5kYXJkIERldmlhdGlvbiBvZiBDb21wYWN0bmVzc3EAfgAPdAAOU3Ryb25nZXN0IEJlYXRxAH4AD3QAMURlcml2YXRpdmUgb2YgIFRyYWRpdGlvbmFsIEFyZWEgTWV0aG9kIG9mIE1vbWVudHNxAH4AD3QAD1plcm5pa2UgTW9tZW50c3EAfgAPdAAQUm9vdCBNZWFuIFNxdWFyZXEAfgAYdAATUnVubmluZyBNZWFuIG9mIExQQ3EAfgAPdAAsRGVyaXZhdGl2ZSBvZiBSdW5uaW5nIE1lYW4gb2YgU3Ryb25nZXN0IEJlYXRxAH4AD3QAKlJ1bm5pbmcgTWVhbiBvZiBTdHJlbmd0aCBPZiBTdHJvbmdlc3QgQmVhdHEAfgAPdAAzUnVubmluZyBNZWFuIG9mICBUcmFkaXRpb25hbCBBcmVhIE1ldGhvZCBvZiBNb21lbnRzcQB+AA90AEJEZXJpdmF0aXZlIG9mIFN0YW5kYXJkIERldmlhdGlvbiBvZiBQZWFrIEJhc2VkIFNwZWN0cmFsIFNtb290aG5lc3NxAH4AD3QAKlN0YW5kYXJkIERldmlhdGlvbiBvZiBTcGVjdHJhbCBWYXJpYWJpbGl0eXEAfgAPdAAWRGVyaXZhdGl2ZSBvZiBCZWF0IFN1bXEAfgAPdAA/RGVyaXZhdGl2ZSBvZiBTdGFuZGFyZCBEZXZpYXRpb24gb2YgUGFydGlhbCBCYXNlZCBTcGVjdHJhbCBGbHV4cQB+AA90ACREZXJpdmF0aXZlIG9mIEFyZWEgTWV0aG9kIG9mIE1vbWVudHNxAH4AD3QAN0Rlcml2YXRpdmUgb2YgU3Ryb25nZXN0IEZyZXF1ZW5jeSBWaWEgU3BlY3RyYWwgQ2VudHJvaWRxAH4AD3QALURlcml2YXRpdmUgb2YgUGFydGlhbCBCYXNlZCBTcGVjdHJhbCBDZW50cm9pZHEAfgAPdABNRGVyaXZhdGl2ZSBvZiBTdGFuZGFyZCBEZXZpYXRpb24gb2YgU3Ryb25nZXN0IEZyZXF1ZW5jeSBWaWEgU3BlY3RyYWwgQ2VudHJvaWRxAH4AD3QAHVJ1bm5pbmcgTWVhbiBvZiBTcGVjdHJhbCBGbHV4cQB+AA90ADlTdGFuZGFyZCBEZXZpYXRpb24gb2YgU3Ryb25nZXN0IEZyZXF1ZW5jeSBWaWEgRkZUIE1heGltdW1xAH4AD3QAEk1hZ25pdHVkZSBTcGVjdHJ1bXEAfgAPdAAvRGVyaXZhdGl2ZSBvZiBTdGFuZGFyZCBEZXZpYXRpb24gb2YgQ29tcGFjdG5lc3NxAH4AD3QADlplcm8gQ3Jvc3NpbmdzcQB+ABh0ADVTdGFuZGFyZCBEZXZpYXRpb24gb2YgUGFydGlhbCBCYXNlZCBTcGVjdHJhbCBDZW50cm9pZHEAfgAPdAAeRGVyaXZhdGl2ZSBvZiBSb290IE1lYW4gU3F1YXJlcQB+AA90ACZSdW5uaW5nIE1lYW4gb2YgQXJlYSBNZXRob2Qgb2YgTW9tZW50c3EAfgAPdAAhRGVyaXZhdGl2ZSBvZiBSdW5uaW5nIE1lYW4gb2YgTFBDcQB+AA90ACFSdW5uaW5nIE1lYW4gb2YgU3BlY3RyYWwgQ2VudHJvaWRxAH4AD3QAMURlcml2YXRpdmUgb2YgU3RhbmRhcmQgRGV2aWF0aW9uIG9mIFNwZWN0cmFsIEZsdXhxAH4AD3QAK0Rlcml2YXRpdmUgb2YgUnVubmluZyBNZWFuIG9mIFNwZWN0cmFsIEZsdXhxAH4AD3QAKERlcml2YXRpdmUgb2YgU3RyZW5ndGggT2YgU3Ryb25nZXN0IEJlYXRxAH4AD3QAFFNwZWN0cmFsIFZhcmlhYmlsaXR5cQB+ABh0ABtSdW5uaW5nIE1lYW4gb2YgQ29tcGFjdG5lc3NxAH4AD3QAHkZyYWN0aW9uIE9mIExvdyBFbmVyZ3kgV2luZG93c3EAfgAPdAAZU3RhbmRhcmQgRGV2aWF0aW9uIG9mIExQQ3EAfgAPdAAvUnVubmluZyBNZWFuIG9mIFBhcnRpYWwgQmFzZWQgU3BlY3RyYWwgQ2VudHJvaWRxAH4AD3QAH1BhcnRpYWwgQmFzZWQgU3BlY3RyYWwgQ2VudHJvaWRxAH4AD3QAJ1N0YW5kYXJkIERldmlhdGlvbiBvZiBTcGVjdHJhbCBDZW50cm9pZHEAfgAPdAAyRGVyaXZhdGl2ZSBvZiBSdW5uaW5nIE1lYW4gb2YgU3BlY3RyYWwgVmFyaWFiaWxpdHlxAH4AD3QAMkRlcml2YXRpdmUgb2YgU3RhbmRhcmQgRGV2aWF0aW9uIG9mIFN0cm9uZ2VzdCBCZWF0cQB+AA90AClEZXJpdmF0aXZlIG9mIFJ1bm5pbmcgTWVhbiBvZiBDb21wYWN0bmVzc3EAfgAPdAAwU3RhbmRhcmQgRGV2aWF0aW9uIG9mIFN0cmVuZ3RoIE9mIFN0cm9uZ2VzdCBCZWF0cQB+AA90ADpEZXJpdmF0aXZlIG9mIFJ1bm5pbmcgTWVhbiBvZiBSZWxhdGl2ZSBEaWZmZXJlbmNlIEZ1bmN0aW9ucQB+AA90AARNRkNDcQB+AA90ADREZXJpdmF0aXZlIG9mIFJ1bm5pbmcgTWVhbiBvZiBBcmVhIE1ldGhvZCBvZiBNb21lbnRzcQB+AA90ABlEZXJpdmF0aXZlIG9mIENvbXBhY3RuZXNzcQB+AA90ACJEZXJpdmF0aXZlIG9mIFJ1bm5pbmcgTWVhbiBvZiBNRkNDcQB+AA90AD1EZXJpdmF0aXZlIG9mIFJ1bm5pbmcgTWVhbiBvZiBQYXJ0aWFsIEJhc2VkIFNwZWN0cmFsIENlbnRyb2lkcQB+AA90ABtQYXJ0aWFsIEJhc2VkIFNwZWN0cmFsIEZsdXhxAH4AD3QAIyBUcmFkaXRpb25hbCBBcmVhIE1ldGhvZCBvZiBNb21lbnRzcQB+AA90AA1TcGVjdHJhbCBGbHV4cQB+ABh0ADFTdGFuZGFyZCBEZXZpYXRpb24gb2YgUGFydGlhbCBCYXNlZCBTcGVjdHJhbCBGbHV4cQB+AA90ADNSdW5uaW5nIE1lYW4gb2YgU3Ryb25nZXN0IEZyZXF1ZW5jeSBWaWEgRkZUIE1heGltdW1xAH4AD3QALERlcml2YXRpdmUgb2YgUnVubmluZyBNZWFuIG9mIFplcm8gQ3Jvc3NpbmdzcQB+AA90AClTdHJvbmdlc3QgRnJlcXVlbmN5IFZpYSBTcGVjdHJhbCBDZW50cm9pZHEAfgAPdAAeUnVubmluZyBNZWFuIG9mIFplcm8gQ3Jvc3NpbmdzcQB+AA90ADZSdW5uaW5nIE1lYW4gb2YgU3Ryb25nZXN0IEZyZXF1ZW5jeSBWaWEgWmVybyBDcm9zc2luZ3NxAH4AD3QAPFN0YW5kYXJkIERldmlhdGlvbiBvZiBTdHJvbmdlc3QgRnJlcXVlbmN5IFZpYSBaZXJvIENyb3NzaW5nc3EAfgAPdAAcUmVsYXRpdmUgRGlmZmVyZW5jZSBGdW5jdGlvbnEAfgAPdAAsRGVyaXZhdGl2ZSBvZiBTdGFuZGFyZCBEZXZpYXRpb24gb2YgQmVhdCBTdW1xAH4AD3QALERlcml2YXRpdmUgb2YgUGVhayBCYXNlZCBTcGVjdHJhbCBTbW9vdGhuZXNzcQB+AA90AClEZXJpdmF0aXZlIG9mIFBhcnRpYWwgQmFzZWQgU3BlY3RyYWwgRmx1eHEAfgAPdAAbMkQgUG9seW5vbWlhbCBBcHByb3hpbWF0aW9ucQB+AA90ACBSdW5uaW5nIE1lYW4gb2YgUm9vdCBNZWFuIFNxdWFyZXEAfgAPdAAZQmVhdCBIaXN0b2dyYW0gQmluIExhYmVsc3EAfgAPdAAeUGVhayBCYXNlZCBTcGVjdHJhbCBTbW9vdGhuZXNzcQB+AA90ADREZXJpdmF0aXZlIG9mIFJ1bm5pbmcgTWVhbiBvZiBTcGVjdHJhbCBSb2xsb2ZmIFBvaW50cQB+AA90AAtDb21wYWN0bmVzc3EAfgAYdAAeU3RhbmRhcmQgRGV2aWF0aW9uIG9mIEJlYXQgU3VtcQB+AA90ADlTdGFuZGFyZCBEZXZpYXRpb24gb2YgIFRyYWRpdGlvbmFsIEFyZWEgTWV0aG9kIG9mIE1vbWVudHNxAH4AD3QAQERlcml2YXRpdmUgb2YgU3RhbmRhcmQgRGV2aWF0aW9uIG9mIFJlbGF0aXZlIERpZmZlcmVuY2UgRnVuY3Rpb25xAH4AD3QAOURlcml2YXRpdmUgb2YgUnVubmluZyBNZWFuIG9mIFBhcnRpYWwgQmFzZWQgU3BlY3RyYWwgRmx1eHEAfgAPdAAkRGVyaXZhdGl2ZSBvZiBTcGVjdHJhbCBSb2xsb2ZmIFBvaW50cQB+AA90ADhEZXJpdmF0aXZlIG9mIFJ1bm5pbmcgTWVhbiBvZiBTdHJlbmd0aCBPZiBTdHJvbmdlc3QgQmVhdHEAfgAPdAAGQ2hyb21hcQB+AA90ADJTdGFuZGFyZCBEZXZpYXRpb24gb2YgUmVsYXRpdmUgRGlmZmVyZW5jZSBGdW5jdGlvbnEAfgAPdAAkUnVubmluZyBNZWFuIG9mIFNwZWN0cmFsIFZhcmlhYmlsaXR5cQB+AA90ABxEZXJpdmF0aXZlIG9mIFplcm8gQ3Jvc3NpbmdzcQB+AA90ACoyRCBQb2x5bm9taWFsIEFwcHJveGltYXRpb24gQ29uc3RhbnRRIE1GQ0NxAH4AD3QAI1N0cm9uZ2VzdCBGcmVxdWVuY3kgVmlhIEZGVCBNYXhpbXVtcQB+AA90ACtSdW5uaW5nIE1lYW4gb2YgUGFydGlhbCBCYXNlZCBTcGVjdHJhbCBGbHV4cQB+AA90ABdDb25zdGFudFEgZGVyaXZlZCBNRkNDc3EAfgAPdAAYRkZUIEJpbiBGcmVxdWVuY3kgTGFiZWxzcQB+AA90ABRSdW5uaW5nIE1lYW4gb2YgTUZDQ3EAfgAPdAAlQXJlYSBNZXRob2Qgb2YgTW9tZW50cyBMb2cgQ29uc3RhbnQgUXEAfgAPdAA6RGVyaXZhdGl2ZSBvZiBTdGFuZGFyZCBEZXZpYXRpb24gb2YgQXJlYSBNZXRob2Qgb2YgTW9tZW50c3EAfgAPdAAURGVyaXZhdGl2ZSBvZiBDaHJvbWFxAH4AD3QADlBlYWsgRGV0ZWN0aW9ucQB+AA90AEdEZXJpdmF0aXZlIG9mIFJ1bm5pbmcgTWVhbiBvZiBTdHJvbmdlc3QgRnJlcXVlbmN5IFZpYSBTcGVjdHJhbCBDZW50cm9pZHEAfgAPdABCRGVyaXZhdGl2ZSBvZiBTdGFuZGFyZCBEZXZpYXRpb24gb2YgRnJhY3Rpb24gT2YgTG93IEVuZXJneSBXaW5kb3dzcQB+AA90ADFEZXJpdmF0aXZlIG9mIFN0cm9u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";
        Object[] settings = (Object[]) Utils.fromString(settingsEncoded);


        //configure batch
        Batch batch = new Batch(features);
        batch.setSettings(settings);
        return batch;
    }

    private void setupDataModel(Batch batch) throws FileNotFoundException {
        // configure outputs
        DataModel dm = batch.getDataModel();
        OutputStream valsavepath = ma.openFileOutput("fv.xml", Context.MODE_PRIVATE | Context.MODE_APPEND);
        OutputStream defsavepath = ma.openFileOutput("fk.xml", Context.MODE_PRIVATE | Context.MODE_APPEND);
        dm.featureKey = defsavepath;
        dm.featureValue = valsavepath;
    }


}
