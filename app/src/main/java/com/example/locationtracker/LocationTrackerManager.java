package com.example.locationtracker;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;

import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;

import lombok.Data;
import lombok.NonNull;
import lombok.Setter;

@Data
public class LocationTrackerManager {

    @NonNull
    private final Context context;
    @NonNull
    private final FusedLocationProviderClient fusedLocationClient;
    @NonNull
    private final LocationApplication.LocationConfig config;
    private LocationCallback locationCallback;
    @Setter
    private LocationListener listener;

    @SuppressLint("MissingPermission")
    public void startLocationUpdates() {
        if (!this.hasLocationPermission()) {
            if (listener != null)
                listener.onPermissionDenied();

            return;
        }

        this.locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull final LocationResult locationResult) {
                for (final var location : locationResult.getLocations()) {

                    // Empêcher les positions « approximatives »
                    if (location.getAccuracy() > config.getMaxAccuracy()) return;

                    if (listener != null)
                        listener.onLocationChanged(location);
                }
            }
        };

        final var locationRequest = new LocationRequest.Builder(config.getPriority(), config.getUpdateInterval())
                .setMinUpdateIntervalMillis(config.getMinUpdateInterval())
                .setMinUpdateDistanceMeters(config.getMinUpdateDistanceMeters())
                .setWaitForAccurateLocation(config.isWaitForAccurateLocation())
                .build();

        fusedLocationClient.requestLocationUpdates(locationRequest, this.locationCallback, Looper.getMainLooper());
    }

    public void stopLocationUpdates() {
        if (locationCallback != null) {
            fusedLocationClient.removeLocationUpdates(locationCallback);
        }
    }

    private boolean hasLocationPermission() {
        return ActivityCompat.checkSelfPermission(context,
                android.Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public interface LocationListener {
        void onLocationChanged(Location location);
        void onPermissionDenied();
    }
}