package com.example.locationtracker;

import android.location.Geocoder;
import android.location.Location;
import android.os.Build;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.functions.FirebaseFunctions;
import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LocationTrackerRepository {

    private static final String FUNCTION_NAME = "onCallCreateOne";
    private static final String COLLECTION_NAME = "locations";

    @NonNull
    private final Geocoder geocoder;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();

    public Task<String> createOne(@NonNull final Location location) {
        // Utilisation de TaskCompletionSource car Tasks.call est déprécié
        final var tcs = new TaskCompletionSource<>();

        executorService.execute(() -> {
            try {
                final var payload = mapLocationToData(location);
                tcs.setResult(payload);
            } catch (Exception e) {
                tcs.setException(e);
            }
        });

        // Exécuter le mapping (et donc le géocodage) sur un thread d'arrière-plan
        return tcs.getTask()
                .continueWithTask(task -> {
                    if (!task.isSuccessful() || task.getResult() == null) {
                        return Tasks.forException(task.getException() != null ? task.getException() : new Exception("Mapping failed"));
                    }
                    return FirebaseFunctions.getInstance()
                            .getHttpsCallable(FUNCTION_NAME)
                            .call(task.getResult());
                })
                .continueWith(task -> (task.isSuccessful() && task.getResult() != null) ? (String) task.getResult().getData() : "");
    }

    private String getCompleteAddressString(@NonNull final Location location) {
        if (!Geocoder.isPresent()) return "";
        try {
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
            // Log erreur ou ignorer silencieusement pour ne pas bloquer l'envoi
            return "";
        }
    }

    private LocationPayload mapLocationToData(@NonNull final Location location) {
        final var document = LocationPayload.LocationDocument.builder()
                .lat(location.getLatitude())
                .lng(location.getLongitude())
                .time(location.getTime())
                .provider(location.getProvider() != null ? location.getProvider() : "unknown")
                .accuracy(location.getAccuracy())
                .speed(location.hasSpeed() ? location.getSpeed() : null)
                .altitude(location.hasAltitude() ? location.getAltitude() : null)
                .bearing(location.hasBearing() ? location.getBearing() : null)
                .user(Build.MANUFACTURER + "-" + Build.DEVICE)
                .address(getCompleteAddressString(location))
                .date(Timestamp.now())
                .build();

        return LocationPayload.builder()
                .collection(COLLECTION_NAME)
                .document(document)
                .build();
    }

    @Data
    @Builder
    private static class LocationPayload {

        private String collection;
        private LocationDocument document;

        @Data
        @Builder
        private static class LocationDocument {

            private double lat;
            private double lng;
            private long time;
            private String provider;
            private float accuracy;

            private Float speed;
            private Double altitude;
            private Float bearing;

            private String user;
            private Timestamp date;
            private String address;
        }
    }
}