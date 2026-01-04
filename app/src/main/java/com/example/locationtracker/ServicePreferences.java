package com.example.locationtracker;

import android.content.Context;
import android.content.SharedPreferences;
import lombok.NonNull;

public class ServicePreferences {

    private static final String PREFS_NAME = "ServicePreferences";
    private static final String KEY_SERVICE_RUNNING = "service_running";
    private static final String KEY_AUTO_START_ENABLED = "auto_start_enabled";

    private final SharedPreferences preferences;

    public ServicePreferences(@NonNull final Context context) {
        this.preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    /**
     * Marquer le service comme actif
     */
    public void setServiceRunning(boolean running) {
        preferences.edit()
                .putBoolean(KEY_SERVICE_RUNNING, running)
                .apply();
    }

    /**
     * Vérifier si le service était actif
     */
    public boolean wasServiceRunning() {
        return preferences.getBoolean(KEY_SERVICE_RUNNING, false);
    }

    /**
     * Activer/désactiver le démarrage automatique
     */
    public void setAutoStartEnabled(boolean enabled) {
        preferences.edit()
                .putBoolean(KEY_AUTO_START_ENABLED, enabled)
                .apply();
    }

    /**
     * Vérifier si le démarrage automatique est activé
     */
    public boolean isAutoStartEnabled() {
        return preferences.getBoolean(KEY_AUTO_START_ENABLED, false);
    }

    /**
     * Effacer toutes les préférences
     */
    public void clear() {
        preferences.edit().clear().apply();
    }
}
