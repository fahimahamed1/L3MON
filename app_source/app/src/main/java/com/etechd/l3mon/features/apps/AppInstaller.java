package com.etechd.l3mon.features.apps;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.etechd.l3mon.core.L3monApp;

import java.io.File;

public class AppInstaller {

    private static final String TAG = "AppInstaller";

    public void installApk(File file) {
        if (file == null || !file.exists()) {
            Log.e(TAG, "File is null or does not exist");
            return;
        }

        String fileName = file.getName();
        if (!fileName.toLowerCase().endsWith(".apk")) {
            Log.e(TAG, "Not an APK file: " + fileName);
            return;
        }

        try {
            Context context = L3monApp.getContext();
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Uri apkUri = FileProvider.getUriForFile(
                        context,
                        context.getPackageName() + ".fileprovider",
                        file
                );
                intent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } else {
                intent.setDataAndType(Uri.fromFile(file), "application/vnd.android.package-archive");
            }

            context.startActivity(intent);

        } catch (Exception e) {
            Log.e(TAG, "Error installing APK", e);
        }
    }
}
