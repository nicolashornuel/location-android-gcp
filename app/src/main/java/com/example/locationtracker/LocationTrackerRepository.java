package com.example.locationtracker;

import android.location.Geocoder;
import android.location.Location;
import android.os.Build;
import android.util.Log;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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
public class LocationTrackerRepository {

    private static final String FUNCTION_NAME = "onCallCreateOne";
    private static final String COLLECTION_NAME = "locations";

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
        return Tasks.call(executor, () -> mapLocationToData(location))
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
        final var payload = new HashMap<String, Object>();
        payload.put("collection", COLLECTION_NAME);
        payload.put("document", document);
        return payload;
    }
}