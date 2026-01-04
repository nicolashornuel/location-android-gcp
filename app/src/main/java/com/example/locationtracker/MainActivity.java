package com.example.locationtracker;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.Button;
import android.widget.CheckBox;
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
    private static final int BACKGROUND_LOCATION_REQUEST_CODE = 101;
    private static final String TAG = "MainActivity";

    private TextView tvLocation;
    private Button btnStart, btnStop;
    private CheckBox chkAutoStart;
    private boolean serviceRunning = false;
    private boolean isBound = false;  // 🆕 Track binding state
    private ServicePreferences servicePreferences;
    private LocationForegroundService locationService;

    private final BroadcastReceiver locationReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final Location location = LocationTrackerBroadcaster.extractLocationFromIntent(intent);
            if (location != null) {
                displayLocation(location);
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate");

        servicePreferences = new ServicePreferences(this);
        initializeViews();
        setupClickListeners();

        // Restaurer l'état du service
        serviceRunning = servicePreferences.wasServiceRunning();
        updateButtonStates();
    }

    private void initializeViews() {
        tvLocation = findViewById(R.id.tvLocation);
        btnStart = findViewById(R.id.btnStart);
        btnStop = findViewById(R.id.btnStop);
        chkAutoStart = findViewById(R.id.chkAutoStart);

        // Charger l'état du démarrage automatique
        chkAutoStart.setChecked(servicePreferences.isAutoStartEnabled());
        updateButtonStates();

        Button btnStats = findViewById(R.id.btnStats);
        btnStats.setOnClickListener(v -> {
            if (locationService != null) {
                String stats = locationService.getTrackingStats();
                new AlertDialog.Builder(this)
                        .setTitle(R.string.dialog_stats_title)
                        .setMessage(stats)
                        .show();
            }
        });
    }

    private void setupClickListeners() {
        btnStart.setOnClickListener(v -> {
            if (checkAndRequestPermissions()) {
                startLocationService();
            }
        });

        btnStop.setOnClickListener(v -> stopLocationService());

        chkAutoStart.setOnCheckedChangeListener((buttonView, isChecked) -> {
            servicePreferences.setAutoStartEnabled(isChecked);
            String message = isChecked ?
                    "Démarrage automatique activé" :
                    "Démarrage automatique désactivé";
            showToast(message);
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart");

        // 🆕 Bind au service s'il tourne déjà
        if (serviceRunning && !isBound) {
            bindToService();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        // Vérifier l'état réel du service
        serviceRunning = servicePreferences.wasServiceRunning();
        updateButtonStates();

        registerLocationReceiver();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");

        // 🆕 Safe unregister
        try {
            unregisterReceiver(locationReceiver);
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver not registered", e);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop");
        // Ne pas unbind ici car le service doit continuer
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");

        // 🆕 Unbind seulement si on est bound
        if (isBound) {
            try {
                unbindService(connection);
                isBound = false;
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Service not bound", e);
            }
        }
    }

    /**
     * 🆕 Enregistrer le receiver pour les mises à jour de localisation
     */
    private void registerLocationReceiver() {
        try {
            final IntentFilter filter = new IntentFilter(LocationTrackerBroadcaster.ACTION_LOCATION_UPDATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(locationReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(locationReceiver, filter);
            }
        } catch (IllegalArgumentException e) {
            Log.w(TAG, "Receiver already registered", e);
        }
    }

    /**
     * Vérifier et demander les permissions nécessaires
     */
    private boolean checkAndRequestPermissions() {
        if (hasAllPermissions()) {
            // Si toutes les permissions de base sont accordées,
            // vérifier la permission en arrière-plan
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission()) {
                requestBackgroundLocationPermission();
                return false;
            }
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

    private boolean hasBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return checkPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
        return true;
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

    /**
     * Demander la permission de localisation en arrière-plan (Android 10+)
     */
    private void requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            new android.app.AlertDialog.Builder(this)
                    .setTitle("Permission de localisation en arrière-plan")
                    .setMessage("Pour continuer à suivre votre position lorsque l'application " +
                            "est en arrière-plan ou fermée, vous devez autoriser l'accès " +
                            "à la localisation 'Toujours'.\n\n" +
                            "Cette permission est nécessaire pour le fonctionnement du service GPS.")
                    .setPositiveButton("Continuer", (dialog, which) -> {
                        ActivityCompat.requestPermissions(
                                MainActivity.this,
                                new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                                BACKGROUND_LOCATION_REQUEST_CODE
                        );
                    })
                    .setNegativeButton("Annuler", (dialog, which) -> {
                        showToast("La localisation en arrière-plan est nécessaire pour le service");
                    })
                    .show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (areAllPermissionsGranted(grantResults)) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && !hasBackgroundLocationPermission()) {
                    requestBackgroundLocationPermission();
                } else {
                    startLocationService();
                }
            } else {
                showPermissionDeniedMessage();
            }
        } else if (requestCode == BACKGROUND_LOCATION_REQUEST_CODE) {
            if (areAllPermissionsGranted(grantResults)) {
                showToast("Permission en arrière-plan accordée");
                startLocationService();
            } else {
                showBackgroundLocationDeniedDialog();
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

    private void showBackgroundLocationDeniedDialog() {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Avertissement")
                .setMessage("Sans la permission de localisation en arrière-plan, " +
                        "le service GPS ne pourra fonctionner que lorsque l'application " +
                        "est ouverte.\n\n" +
                        "Voulez-vous démarrer quand même ?")
                .setPositiveButton("Oui, démarrer", (dialog, which) -> startLocationService())
                .setNegativeButton("Non", null)
                .show();
    }

    /**
     * Démarrer le service de localisation
     */
    private void startLocationService() {
        if (serviceRunning) {
            Log.d(TAG, "Service déjà en cours d'exécution");
            return;
        }

        Log.d(TAG, "Démarrage du service de localisation");

        final Intent serviceIntent = new Intent(this, LocationForegroundService.class);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        bindToService();

        serviceRunning = true;
        updateButtonStates();
        tvLocation.setText(R.string.status_searching);
        showToast("Service de localisation démarré");
    }

    /**
     * 🆕 Bind au service
     */
    private void bindToService() {
        if (!isBound) {
            final Intent serviceIntent = new Intent(this, LocationForegroundService.class);
            bindService(serviceIntent, connection, Context.BIND_AUTO_CREATE);
            isBound = true;
            Log.d(TAG, "Service bound");
        }
    }

    /**
     * Arrêter le service de localisation
     */
    private void stopLocationService() {
        if (!serviceRunning) {
            Log.d(TAG, "Service n'est pas en cours d'exécution");
            return;
        }

        Log.d(TAG, "Arrêt du service de localisation");

        // 🆕 Unbind d'abord
        if (isBound) {
            try {
                unbindService(connection);
                isBound = false;
                Log.d(TAG, "Service unbound");
            } catch (IllegalArgumentException e) {
                Log.w(TAG, "Service not bound", e);
            }
        }

        // Puis stop
        final Intent serviceIntent = new Intent(this, LocationForegroundService.class);
        stopService(serviceIntent);

        serviceRunning = false;
        locationService = null;
        updateButtonStates();
        tvLocation.setText(R.string.status_stopped);
        showToast("Service de localisation arrêté");
    }

    /**
     * Afficher les données de localisation à l'écran
     */
    private void displayLocation(Location location) {
        final String displayText = String.format(
                "Latitude: %.6f\nLongitude: %.6f\nPrécision: %.1f m\nVitesse: %.2f m/s",
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy(),
                location.getSpeed()
        );

        tvLocation.setText(displayText);
    }

    /**
     * ServiceConnection pour communiquer avec le service
     */
    private final ServiceConnection connection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            Log.d(TAG, "onServiceConnected");
            final LocationForegroundService.LocalBinder localBinder =
                    (LocationForegroundService.LocalBinder) binder;
            locationService = localBinder.getService();
            isBound = true;

            // 🆕 Mettre à jour l'UI avec l'état réel du service
            serviceRunning = locationService.isRunning();
            updateButtonStates();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "onServiceDisconnected");
            locationService = null;
            isBound = false;
        }
    };

    private void updateButtonStates() {
        btnStart.setEnabled(!serviceRunning);
        btnStop.setEnabled(serviceRunning);
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }
}