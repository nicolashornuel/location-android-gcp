package com.example.locationtracker;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {

    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            return;
        }

        final var action = intent.getAction();
        Log.i("BootReceiver reçu: {}", action);

        // Vérifier les différents événements de démarrage
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) ||
                Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(action) ||
                "android.intent.action.QUICKBOOT_POWERON".equals(action)) {

            // Vérifier si le service était actif avant le redémarrage
            final var preferences = new ServicePreferences(context);
            if (preferences.wasServiceRunning()) {
                Log.i(TAG, "Le service était actif, redémarrage...");
                startLocationService(context);
            } else {
                Log.i(TAG, "Le service n'était pas actif, pas de redémarrage");
            }
        }
    }

    /**
     * Démarrer le service de localisation
     */
    private void startLocationService(Context context) {
        try {
            Intent serviceIntent = new Intent(context, LocationForegroundService.class);

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent);
            } else {
                context.startService(serviceIntent);
            }

            Log.i(TAG, "Service de localisation démarré avec succès");
        } catch (Exception e) {
            Log.e(TAG, "Erreur lors du démarrage du service", e);
        }
    }
}