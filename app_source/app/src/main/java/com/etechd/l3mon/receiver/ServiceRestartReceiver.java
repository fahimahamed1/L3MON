package com.etechd.l3mon.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.etechd.l3mon.service.MainService;

public class ServiceRestartReceiver extends BroadcastReceiver {

    private static final String TAG = "ServiceRestartReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        try {
            if (intent != null) {
                String action = intent.getAction();
                if ("respawnService".equals(action) || Intent.ACTION_BOOT_COMPLETED.equals(action)) {
                    startMainService(context);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "onReceive error", e);
        }
    }

    private void startMainService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, MainService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start MainService", e);
        }
    }
}
