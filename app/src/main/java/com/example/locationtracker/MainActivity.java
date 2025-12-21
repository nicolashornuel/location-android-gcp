package com.example.locationtracker;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * Activité principale pour contrôler le service de localisation
 */
public class MainActivity extends AppCompatActivity {

    private static final int PERMISSION_REQUEST_CODE = 100;

    private TextView tvLocation;
    private Button btnStart, btnStop;
    private boolean serviceRunning = false;

    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
                Location location = LocationTrackerBroadcaster.extractLocationFromIntent(intent);
            if (location != null) {
                displayLocation(location);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        setupClickListeners();
    }

    private void initializeViews() {
        tvLocation = findViewById(R.id.tvLocation);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        updateButtonStates();
    }

    private void setupClickListeners() {
        btnStart.setOnClickListener(v -> {
            if (checkAndRequestPermissions()) {
                startLocationService();
            }
        });

        btnStop.setOnClickListener(v -> stopLocationService());
    }

    @Override
    protected void onResume() {
        super.onResume();
        registerLocationReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(locationReceiver);
    }

    private void registerLocationReceiver() {
        final var filter = new IntentFilter(LocationTrackerBroadcaster.ACTION_LOCATION_UPDATE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(locationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(locationReceiver, filter);
        }
    }

    private boolean checkAndRequestPermissions() {
        if (hasAllPermissions()) {
            return true;
        }

        requestRequiredPermissions();
        return false;
    }

    private boolean hasAllPermissions() {
        boolean fineLocation = checkPermission(Manifest.permission.ACCESS_FINE_LOCATION);
        boolean coarseLocation = checkPermission(Manifest.permission.ACCESS_COARSE_LOCATION);
        boolean notification = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkPermission(Manifest.permission.POST_NOTIFICATIONS);

        return fineLocation && coarseLocation && notification;
    }

    private boolean checkPermission(String permission) {
        return ContextCompat.checkSelfPermission(this, permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestRequiredPermissions() {
        String[] permissions;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (areAllPermissionsGranted(grantResults)) {
                startLocationService();
            } else {
                showPermissionDeniedMessage();
            }
        }
    }

    private boolean areAllPermissionsGranted(int[] grantResults) {
        if (grantResults.length == 0) {
            return false;
        }

        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void showPermissionDeniedMessage() {
        Toast.makeText(this,
                "Permissions requises pour la géolocalisation",
                Toast.LENGTH_LONG).show();
    }

    private void startLocationService() {
        if (serviceRunning) {
            return;
        }

        Intent serviceIntent = new Intent(this, LocationForegroundService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        serviceRunning = true;
        updateButtonStates();
        showToast("Service de localisation démarré");
    }

    private void stopLocationService() {
        if (!serviceRunning) {
            return;
        }

        Intent serviceIntent = new Intent(this, LocationForegroundService.class);
        stopService(serviceIntent);

        serviceRunning = false;
        updateButtonStates();
        tvLocation.setText("Service arrêté");
        showToast("Service de localisation arrêté");
    }

    private void displayLocation(Location location) {
        String displayText = String.format(
                "Latitude: %.6f\nLongitude: %.6f\nPrécision: %.1f m\nVitesse: %.2f m/s",
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getSpeed()
        );

        tvLocation.setText(displayText);
    }

    private void updateButtonStates() {
        btnStart.setEnabled(!serviceRunning);
        btnStop.setEnabled(serviceRunning);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}