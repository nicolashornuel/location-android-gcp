package com.example.locationtracker;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import androidx.annotation.Nullable;
import lombok.Getter;

/**
 * Service de géolocalisation en foreground
 * Utilise le Container de LocationApplication pour l'injection de dépendances
 */
public class LocationForegroundService extends Service {

    private static final String TAG = "LocationForegroundService";

    private LocationApplication.Container container;
    private final IBinder binder = new LocalBinder();

    @Getter
    private boolean running = false;

    /**
     * Binder local pour la communication avec MainActivity
     */
    public class LocalBinder extends Binder {
        public LocationForegroundService getService() {
            return LocationForegroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service créé");

        // 🆕 Marquer comme running APRÈS l'initialisation réussie
        initializeService();
    }

    /**
     * 🆕 Initialiser le service et ses dépendances
     */
    private void initializeService() {
        try {
            // Obtenir le container depuis l'Application
            container = ((LocationApplication) getApplication()).getContainer();

            if (container == null) {
                Log.e(TAG, "Container est null, impossible de démarrer le service");
                stopSelf();
                return;
            }

            // Configurer le listener de localisation
            setupLocationListener();

            running = true;
            Log.d(TAG, "Service initialisé avec succès");

        } catch (ClassCastException e) {
            Log.e(TAG, "Erreur de cast de l'Application", e);
            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "Erreur d'initialisation du service", e);
            stopSelf();
        }
    }

    /**
     * 🆕 Configurer le listener pour recevoir les mises à jour de localisation
     */
    private void setupLocationListener() {
        container.getManager().setListener(new LocationTrackerManager.LocationListener() {
            @Override
            public void onLocationChanged(final Location location) {
                handleLocationUpdate(location);
            }

            @Override
            public void onPermissionDenied() {
                handlePermissionDenied();
            }
        });
    }

    /**
     * 🆕 Traiter une nouvelle mise à jour de localisation
     */
    private void handleLocationUpdate(Location location) {
        Log.d(TAG, String.format("Nouvelle position: %.6f, %.6f (±%.0fm)",
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy()));

        // Mettre à jour la notification
        updateNotificationSafely(location);

        // Diffuser l'événement
        broadcastLocationSafely(location);

        // Sauvegarder dans Firebase
        saveLocationToFirebase(location);
    }

    /**
     * 🆕 Mettre à jour la notification de manière sécurisée
     */
    private void updateNotificationSafely(Location location) {
        try {
            if (container != null && container.getNotifier() != null) {
                container.getNotifier().updateNotification(location);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur mise à jour notification", e);
        }
    }

    /**
     * 🆕 Diffuser la localisation de manière sécurisée
     */
    private void broadcastLocationSafely(Location location) {
        try {
            if (container != null && container.getBroadcaster() != null) {
                container.getBroadcaster().broadcastLocationUpdate(location);
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur broadcast location", e);
        }
    }

    /**
     * 🆕 Sauvegarder la localisation dans Firebase
     */
    private void saveLocationToFirebase(Location location) {
        try {
            if (container != null && container.getRepository() != null) {
                container.getRepository().createOne(location)
                        .addOnSuccessListener(documentId -> {
                            Log.d(TAG, "Position sauvegardée avec ID: " + documentId);
                        })
                        .addOnFailureListener(e -> {
                            Log.e(TAG, "Erreur de sauvegarde Firebase: " + e.getMessage(), e);
                        });
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors de la tentative de sauvegarde", e);
        }
    }

    /**
     * 🆕 Gérer le refus de permission
     */
    private void handlePermissionDenied() {
        Log.e(TAG, "Permission de localisation refusée - arrêt du service");
        stopSelf();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service démarré - startId: " + startId);

        // 🆕 Vérifier que le service est bien initialisé
        if (!running || container == null) {
            Log.w(TAG, "Service non initialisé, réinitialisation...");
            initializeService();
        }

        // Démarrer en foreground avec notification
        startForegroundWithNotification();

        // Marquer le service comme actif
        markServiceAsRunning();

        // Démarrer les mises à jour de localisation
        startLocationTracking();

        return START_STICKY;
    }

    /**
     * 🆕 Démarrer le service en foreground avec notification
     */
    private void startForegroundWithNotification() {
        try {
            if (container != null && container.getNotifier() != null) {
                final var notification = container.getNotifier().createInitialNotification();
                final var notificationId = container.getNotifier().getNotificationId();
                startForeground(notificationId, notification);
                Log.d(TAG, "Service en mode foreground");
            } else {
                Log.e(TAG, "Impossible de créer la notification");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur démarrage foreground", e);
            stopSelf();
        }
    }

    /**
     * 🆕 Marquer le service comme actif dans les préférences
     */
    private void markServiceAsRunning() {
        try {
            if (container != null && container.getServicePreferences() != null) {
                container.getServicePreferences().setServiceRunning(true);
                Log.d(TAG, "Service marqué comme actif");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur sauvegarde état service", e);
        }
    }

    /**
     * 🆕 Démarrer le suivi de localisation
     */
    private void startLocationTracking() {
        try {
            if (container != null && container.getManager() != null) {
                container.getManager().startLocationUpdates();
                Log.d(TAG, "Suivi GPS démarré");
            } else {
                Log.e(TAG, "LocationManager non disponible");
                stopSelf();
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Permission manquante pour démarrer le GPS", e);
            stopSelf();
        } catch (Exception e) {
            Log.e(TAG, "Erreur démarrage GPS", e);
            stopSelf();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "Service en cours de destruction");

        running = false;

        // Arrêter les mises à jour GPS
        stopLocationTracking();

        // Nettoyer le listener
        cleanupListener();

        // Marquer le service comme inactif
        markServiceAsNotRunning();

        Log.d(TAG, "Service détruit");
    }

    /**
     * 🆕 Arrêter le suivi de localisation
     */
    private void stopLocationTracking() {
        try {
            if (container != null && container.getManager() != null) {
                container.getManager().stopLocationUpdates();
                Log.d(TAG, "Suivi GPS arrêté");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur arrêt GPS", e);
        }
    }

    /**
     * 🆕 Nettoyer le listener
     */
    private void cleanupListener() {
        try {
            if (container != null && container.getManager() != null) {
                container.getManager().setListener(null);
                Log.d(TAG, "Listener nettoyé");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur nettoyage listener", e);
        }
    }

    /**
     * 🆕 Marquer le service comme inactif
     */
    private void markServiceAsNotRunning() {
        try {
            if (container != null && container.getServicePreferences() != null) {
                container.getServicePreferences().setServiceRunning(false);
                Log.d(TAG, "Service marqué comme inactif");
            }
        } catch (Exception e) {
            Log.e(TAG, "Erreur sauvegarde état service", e);
        }
    }

    public Location getLastKnownLocation() {
        return container.getManager().getLastLocation();
    }

    public String getTrackingStats() {
        return container.getManager().getTrackingStats();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "Service bound");
        return binder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "Service unbound");
        return super.onUnbind(intent);
    }

    @Override
    public void onRebind(Intent intent) {
        super.onRebind(intent);
        Log.d(TAG, "Service rebound");
    }
}