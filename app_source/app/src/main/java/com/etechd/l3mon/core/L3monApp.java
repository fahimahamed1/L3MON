package com.etechd.l3mon.core;

import android.app.Application;
import android.content.Context;

/**
 * Central application entry-point that exposes a process-wide application context.
 */
public class L3monApp extends Application {

    private static L3monApp instance;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
    }

    public static Context getContext() {
        if (instance == null) {
            throw new IllegalStateException("L3monApp has not been initialized");
        }
        return instance.getApplicationContext();
    }
}
