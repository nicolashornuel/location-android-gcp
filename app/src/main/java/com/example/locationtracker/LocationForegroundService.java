package com.example.locationtracker;

import android.app.Service;
import android.content.Intent;
import android.location.Location;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import lombok.Getter;

public class LocationForegroundService extends Service {

    private static final String TAG = "LocationForegroundService";
    private LocationApplication.Container container;
    private final IBinder binder = new LocalBinder();
    @Getter
    private boolean running = false;
    public class LocalBinder extends Binder {
        public LocationForegroundService getService() {
            return LocationForegroundService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Service créé");
        running = true;
        container = ((LocationApplication) getApplication()).getContainer();
        container.getManager().setListener(new LocationTrackerManager.LocationListener() {
            @Override
            public void onLocationChanged(final Location location) {
                Log.d(TAG, "Nouvelle position: " + location);
                container.getNotifier().updateNotification(location);
                container.getBroadcaster().broadcastLocationUpdate(location);
                container.getRepository().createOne(location)
                        .addOnSuccessListener(documentId -> Log.d(TAG, "Position sauvegardée avec ID: " + documentId))
                        .addOnFailureListener(e -> Log.e(TAG, "Erreur de sauvegarde: " + e.getMessage()));
            }
            @Override
            public void onPermissionDenied() {
                Log.e(TAG, "Permission de localisation refusée");
                stopSelf();
            }
        });
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "Service démarré");
        final var notification = this.container.getNotifier().createInitialNotification();
        final var notificationId = this.container.getNotifier().getNotificationId();
        this.startForeground(notificationId, notification);
        this.container.getManager().startLocationUpdates();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        running = false;
        Log.d(TAG, "Service détruit");
        if (this.container != null && this.container.getManager() != null) {
            this.container.getManager().stopLocationUpdates();
            this.container.getManager().setListener(null);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }
}