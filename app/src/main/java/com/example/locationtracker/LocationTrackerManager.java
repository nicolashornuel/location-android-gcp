package com.example.locationtracker;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

/**
 * Gestionnaire de localisation responsable des interactions avec l'API GPS
 */
public class LocationTrackerManager {

    private static final String TAG = "LocationTrackerManager";

    @NonNull
    @Getter
    private final Context context;

    @NonNull
    @Getter
    private final FusedLocationProviderClient fusedLocationClient;

    @NonNull
    @Getter
    private final LocationApplication.LocationConfig config;

    private LocationCallback locationCallback;

    @Setter
    private LocationListener listener;

    // 🆕 Dernière position connue en cache
    @Getter
    @Nullable
    private Location lastLocation;

    // 🆕 Flag pour savoir si les updates sont actifs
    @Getter
    private boolean updatesActive = false;

    /**
     * Constructeur
     */
    public LocationTrackerManager(
            @NonNull Context context,
            @NonNull FusedLocationProviderClient fusedLocationClient,
            @NonNull LocationApplication.LocationConfig config) {
        this.context = context.getApplicationContext();
        this.fusedLocationClient = fusedLocationClient;
        this.config = config;
    }

    /**
     * Démarrer les mises à jour de localisation
     */
    @SuppressLint("MissingPermission")
    public void startLocationUpdates() {
        Log.d(TAG, "Démarrage des mises à jour de localisation");

        // 🆕 Vérifier si déjà actif
        if (updatesActive) {
            Log.w(TAG, "Les mises à jour sont déjà actives");
            return;
        }

        if (!hasLocationPermission()) {
            Log.e(TAG, "Permission de localisation manquante");
            if (listener != null) {
                listener.onPermissionDenied();
            }
            return;
        }

        createLocationCallback();
        LocationRequest locationRequest = createLocationRequest();

        try {
            fusedLocationClient.requestLocationUpdates(
                    locationRequest,
                    locationCallback,
                    Looper.getMainLooper()
            );
            updatesActive = true;
            Log.d(TAG, "Mises à jour de localisation démarrées avec succès");

            // 🆕 Obtenir la dernière position connue immédiatement
            getLastKnownLocation();

        } catch (SecurityException e) {
            Log.e(TAG, "SecurityException lors du démarrage des updates", e);
            if (listener != null) {
                listener.onPermissionDenied();
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du démarrage des updates", e);
        }
    }

    /**
     * 🆕 Créer le callback pour recevoir les positions
     */
    private void createLocationCallback() {
        locationCallback = new LocationCallback() {
            @Override
            public void onLocationResult(@NonNull LocationResult locationResult) {
                if (locationResult == null) {
                    Log.w(TAG, "LocationResult est null");
                    return;
                }

                for (Location location : locationResult.getLocations()) {
                    processLocation(location);
                }
            }
        };
    }

    /**
     * 🆕 Traiter une nouvelle position
     */
    private void processLocation(@NonNull Location location) {
        // Log détaillé de la position
        Log.d(TAG, String.format("Position reçue: %.6f, %.6f (±%.0fm) via %s",
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getProvider()));

        // Filtre de précision
        if (!isLocationAccurate(location)) {
            Log.w(TAG, String.format("Position rejetée: précision %.0fm > max %.0fm",
                    location.getAccuracy(),
                    config.getMaxAccuracy()));
            return;
        }

        // 🆕 Filtre de distance minimale (éviter les updates inutiles)
        if (lastLocation != null && !hasMovedEnough(location)) {
            Log.d(TAG, "Position ignorée: mouvement insuffisant");
            return;
        }

        // 🆕 Filtre de temps (éviter les positions trop anciennes)
        if (!isLocationRecent(location)) {
            Log.w(TAG, "Position ignorée: trop ancienne");
            return;
        }

        // Position valide, la sauvegarder et notifier
        lastLocation = location;

        if (listener != null) {
            listener.onLocationChanged(location);
        } else {
            Log.w(TAG, "Listener est null, impossible de notifier");
        }
    }

    /**
     * 🆕 Vérifier si la position est assez précise
     */
    private boolean isLocationAccurate(@NonNull Location location) {
        return location.hasAccuracy() &&
                location.getAccuracy() <= config.getMaxAccuracy();
    }

    /**
     * 🆕 Vérifier si l'utilisateur a suffisamment bougé
     */
    private boolean hasMovedEnough(@NonNull Location newLocation) {
        if (lastLocation == null) {
            return true;
        }

        float distance = lastLocation.distanceTo(newLocation);
        float minDistance = config.getMinUpdateDistanceMeters();

        Log.d(TAG, String.format("Distance parcourue: %.2fm (min: %.2fm)",
                distance, minDistance));

        return distance >= minDistance;
    }

    /**
     * 🆕 Vérifier si la position est récente (moins de 5 minutes)
     */
    private boolean isLocationRecent(@NonNull Location location) {
        long locationAge = System.currentTimeMillis() - location.getTime();
        long maxAge = 5 * 60 * 1000; // 5 minutes en millisecondes

        if (locationAge > maxAge) {
            Log.w(TAG, String.format("Position trop ancienne: %d secondes",
                    locationAge / 1000));
            return false;
        }

        return true;
    }

    /**
     * 🆕 Créer la requête de localisation
     */
    private LocationRequest createLocationRequest() {
        Log.d(TAG, String.format("Configuration GPS: interval=%dms, priority=%d, minDistance=%.0fm",
                config.getUpdateInterval(),
                config.getPriority(),
                config.getMinUpdateDistanceMeters()));

        return new LocationRequest.Builder(
                config.getPriority(),
                config.getUpdateInterval()
        )
                .setMinUpdateIntervalMillis(config.getMinUpdateInterval())
                .setMinUpdateDistanceMeters(config.getMinUpdateDistanceMeters())
                .setWaitForAccurateLocation(config.isWaitForAccurateLocation())
                .setMaxUpdateDelayMillis(config.getUpdateInterval() * 2) // 🆕 Max delay
                .build();
    }

    /**
     * 🆕 Obtenir la dernière position connue
     */
    @SuppressLint("MissingPermission")
    public void getLastKnownLocation() {
        if (!hasLocationPermission()) {
            Log.w(TAG, "Pas de permission pour obtenir la dernière position");
            return;
        }

        fusedLocationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    if (location != null) {
                        Log.d(TAG, "Dernière position connue récupérée");
                        processLocation(location);
                    } else {
                        Log.d(TAG, "Aucune dernière position disponible");
                    }
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Erreur récupération dernière position", e);
                });
    }

    /**
     * Arrêter les mises à jour de localisation
     */
    public void stopLocationUpdates() {
        Log.d(TAG, "Arrêt des mises à jour de localisation");

        if (!updatesActive) {
            Log.w(TAG, "Les mises à jour ne sont pas actives");
            return;
        }

        if (locationCallback != null) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback);
                updatesActive = false;
                Log.d(TAG, "Mises à jour arrêtées avec succès");
            } catch (Exception e) {
                Log.e(TAG, "Erreur lors de l'arrêt des updates", e);
            }
        }
    }

    /**
     * Vérifier si les permissions de localisation sont accordées
     */
    public boolean hasLocationPermission() {
        boolean hasFine = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        boolean hasCoarse = ActivityCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED;

        // 🆕 Au moins une des deux permissions doit être accordée
        return hasFine || hasCoarse;
    }

    /**
     * 🆕 Obtenir des statistiques de suivi
     */
    public String getTrackingStats() {
        if (lastLocation == null) {
            return "Aucune position disponible";
        }

        long age = (System.currentTimeMillis() - lastLocation.getTime()) / 1000;

        return String.format(
                "Dernière position: %.6f, %.6f\n" +
                        "Précision: %.0fm\n" +
                        "Provider: %s\n" +
                        "Age: %ds\n" +
                        "Updates actifs: %s",
                lastLocation.getLatitude(),
                lastLocation.getLongitude(),
                lastLocation.getAccuracy(),
                lastLocation.getProvider(),
                age,
                updatesActive ? "Oui" : "Non"
        );
    }

    /**
     * 🆕 Changer la configuration en cours d'exécution
     */
    public void updateConfig(LocationApplication.LocationConfig newConfig) {
        Log.d(TAG, "Mise à jour de la configuration");

        // Note: Pour vraiment changer la config, il faudrait restart les updates
        // avec la nouvelle config. Ici on log juste un warning.
        Log.w(TAG, "Pour appliquer la nouvelle config, redémarrez les updates");
    }

    /**
     * Interface pour recevoir les callbacks de localisation
     */
    public interface LocationListener {
        void onLocationChanged(Location location);
        void onPermissionDenied();
    }
}