package com.example.locationtracker;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.util.Log;
import android.Manifest;

import androidx.core.content.ContextCompat;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.functions.FirebaseFunctions;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LocationRepository {

    private static final String FUNCTION_NAME = "onCallCreateOne";
    private static final String COLLECTION_NAME = "locations";
    @NonNull
    private final Context context;
    @NonNull
    private final Geocoder geocoder;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public Task<String> createOne(@NonNull final Location location) {

        final var auth = FirebaseAuth.getInstance();

        if (auth.getCurrentUser() == null) {
            return auth.signInAnonymously()
                    .continueWithTask(task -> {
                        if (!task.isSuccessful()) {
                            throw task.getException();
                        }
                        return prepareAndCallFunction(location);
                    });
        }

        return prepareAndCallFunction(location);
    }

    private Task<String> prepareAndCallFunction(@NonNull final Location location) {
        final var tcs = new TaskCompletionSource<Map<String, Object>>();

        executor.execute(() -> {
            try {
                final var payload = mapLocationToData(location);
                tcs.setResult(payload);
            } catch (Exception e) {
                tcs.setException(e);
            }
        });

        return tcs.getTask()
                .continueWithTask(task -> {
                    if (!task.isSuccessful()) {
                        throw task.getException();
                    }
                    return callFunction(task.getResult());
                });
    }

    private Task<String> callFunction(@NonNull final Map<String, Object> data) {
        return FirebaseFunctions.getInstance()
                .getHttpsCallable(FUNCTION_NAME)
                .call(data)
                .continueWith(task -> {
                    if (!task.isSuccessful()) {
                        Log.e("FUNCTION", "Erreur Cloud Function", task.getException());
                        throw task.getException();
                    }

                    Object resultData = task.getResult().getData();
                    Log.d("FUNCTION", "Réponse brute: " + resultData);
                    return resultData != null ? resultData.toString() : "";
                });
    }

    private String getCompleteAddressString(@NonNull final Location location) {
        if (!Geocoder.isPresent()) return "";
        try {
            // NOTE: This blocks, so it MUST be called on a background thread.
            final var addresses = this.geocoder.getFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    1
            );
            return Optional.ofNullable(addresses)
                    .filter(list -> !list.isEmpty())
                    .map(list -> list.get(0))
                    .map(address ->
                            IntStream.rangeClosed(0, address.getMaxAddressLineIndex())
                                    .mapToObj(address::getAddressLine)
                                    .collect(Collectors.joining(", "))
                    )
                    .orElse("");
        } catch (IOException e) {
            Log.w("LocationTrackerRepository", "Geocoding failed: " + e.getMessage());
            return "";
        }
    }

    private Map<String, Object> mapLocationToData(@NonNull final Location location) {
        final var document = new HashMap<String, Object>();
        document.put("lat", location.getLatitude());
        document.put("lng", location.getLongitude());
        document.put("time", location.getTime());
        document.put("provider", location.getProvider());
        document.put("accuracy", location.getAccuracy());
        document.put("speed", location.hasSpeed() ? location.getSpeed() : null);
        document.put("altitude", location.hasAltitude() ? location.getAltitude() : null);
        document.put("bearing", location.hasBearing() ? location.getBearing() : null);
        document.put("user", Build.MANUFACTURER + "-" + Build.DEVICE);
        document.put("address", getCompleteAddressString(location));
        document.put("date", System.currentTimeMillis());
        document.put("deviceStatus", getDeviceStatus(this.context));
        final var payload = new HashMap<String, Object>();
        payload.put("collection", COLLECTION_NAME);
        payload.put("document", document);
        return payload;
    }

    private Map<String, Object> getDeviceStatus(final Context context) {

        // --- Screen ---
        final var pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        final var screenOn = pm.isInteractive();
        final var powerSave = pm.isPowerSaveMode();

        // --- Lock ---
        final var km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
        final var locked = km.isKeyguardLocked();

        // --- Battery ---
        final var filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
        final var battery = context.registerReceiver(null, filter);

        final var level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        final var scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        final var percent = (int) ((level / (float) scale) * 100);

        final var status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        final var charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                        status == BatteryManager.BATTERY_STATUS_FULL;

        // --- Network ---
        final var cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        final var caps = cm.getNetworkCapabilities(cm.getActiveNetwork());
        final var hasInternet = caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);

        String networkType = "NONE";
        if (caps != null) {
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                networkType = "WIFI";
            } else if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) {
                networkType = "MOBILE";
            }
        }

        // --- GPS ---
        final var lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        final var gpsEnabled = lm.isProviderEnabled(LocationManager.GPS_PROVIDER);

        // --- Permissions ---
        final var fineGranted =
                ContextCompat.checkSelfPermission(
                        context, Manifest.permission.ACCESS_FINE_LOCATION)
                        == PackageManager.PERMISSION_GRANTED;

        final var backgroundGranted =
                Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
                        ContextCompat.checkSelfPermission(
                                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                                == PackageManager.PERMISSION_GRANTED;

        final var document = new HashMap<String, Object>();
        document.put("manufacturer", Build.MANUFACTURER);
        document.put("model", Build.MODEL);
        document.put("androidVersion", Build.VERSION.RELEASE);
        document.put("screenOn", screenOn);
        document.put("deviceLocked", locked);
        document.put("batteryPercent", percent);
        document.put("charging", charging);
        document.put("powerSaveMode", powerSave);
        document.put("hasInternet", hasInternet);
        document.put("networkType", networkType);
        document.put("gpsEnabled", gpsEnabled);
        document.put("fineLocationGranted", fineGranted);
        document.put("backgroundLocationGranted", backgroundGranted);
        document.put("timestamp", System.currentTimeMillis());

        return document;
    }
}