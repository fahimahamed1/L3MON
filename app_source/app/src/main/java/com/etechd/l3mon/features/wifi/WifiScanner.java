package com.etechd.l3mon.features.wifi;

import android.Manifest;
import android.content.Context;
import android.location.LocationManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.util.Log;

import androidx.core.content.ContextCompat;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Comparator;
import java.util.List;

public class WifiScanner {

    private static final String TAG = "WifiScanner";

    public static JSONObject scan(Context context) {
        JSONObject resultJson = new JSONObject();
        JSONArray networksArray = new JSONArray();

        try {
            WifiManager wifiManager = (WifiManager) context.getSystemService(Context.WIFI_SERVICE);
            LocationManager locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);

            if (wifiManager == null) {
                String message = "Wi-Fi service unavailable";
                Log.e(TAG, message);
                resultJson.put("error", message);
                return resultJson;
            }

            if (!wifiManager.isWifiEnabled()) {
                String message = "Wi-Fi is disabled on the device";
                Log.w(TAG, message);
                resultJson.put("error", message);
                return resultJson;
            }

            boolean locationEnabled = locationManager != null &&
                    (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER));

            boolean hasFineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;
            boolean hasCoarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED;

        resultJson.put("locationEnabled", locationEnabled);
        resultJson.put("hasFineLocation", hasFineLocation);
        resultJson.put("hasCoarseLocation", hasCoarseLocation);

            if (!locationEnabled || (!hasFineLocation && !hasCoarseLocation)) {
                String message = "Location access required for Wi-Fi scans";
                Log.w(TAG, message + " (enabled=" + locationEnabled + ", fine=" + hasFineLocation + ", coarse=" + hasCoarseLocation + ")");
                resultJson.put("error", message);
                return resultJson;
            }

            try {
                boolean scanStarted = wifiManager.startScan();
                resultJson.put("scanRequested", scanStarted);
            } catch (SecurityException se) {
                String message = "Scan blocked by platform security policy";
                Log.e(TAG, message, se);
                resultJson.put("error", message);
                return resultJson;
            }

            List<ScanResult> scanResults = wifiManager.getScanResults();
            if (scanResults != null && !scanResults.isEmpty()) {
                scanResults.sort(Comparator.comparingInt((ScanResult sr) -> sr.level).reversed());
                int limit = Math.min(scanResults.size(), 25);
                for (int i = 0; i < limit; i++) {
                    ScanResult scanResult = scanResults.get(i);
                    try {
                        JSONObject netJson = new JSONObject();
                        netJson.put("BSSID", scanResult.BSSID);
                        netJson.put("SSID", scanResult.SSID);
                        netJson.put("capabilities", scanResult.capabilities);
                        netJson.put("frequency", scanResult.frequency);
                        netJson.put("level", scanResult.level);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            netJson.put("timestamp", scanResult.timestamp);
                        }
                        networksArray.put(netJson);
                    } catch (Exception e) {
                        Log.w(TAG, "Failed to encode Wi-Fi network", e);
                    }
                }
            }

            resultJson.put("networks", networksArray);
            resultJson.put("timestamp", System.currentTimeMillis());

        } catch (Exception e) {
            Log.e(TAG, "Error scanning Wi-Fi networks", e);
            try {
                resultJson.put("error", e.getMessage() != null ? e.getMessage() : "Wi-Fi scan failed");
            } catch (Exception ignored) {
            }
        }

        return resultJson;
    }
}
