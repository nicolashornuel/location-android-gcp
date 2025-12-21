package com.example.locationtracker;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.os.Build;
import androidx.core.app.NotificationCompat;

public class LocationTrackerNotification {

    private static final String CHANNEL_ID = "LocationServiceChannel";
    private static final int NOTIFICATION_ID = 1;

    private final Context context;
    private final android.app.NotificationManager systemNotificationManager;

    public LocationTrackerNotification(Context context) {
        this.context = context;
        this.systemNotificationManager = (android.app.NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        this.createNotificationChannel();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            final var channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Service de Géolocalisation",
                    android.app.NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Suivi de votre position GPS");
            channel.setShowBadge(false);

            if (this.systemNotificationManager != null)
                this.systemNotificationManager.createNotificationChannel(channel);
        }
    }

    public Notification createInitialNotification() {
        return this.buildNotification("Géolocalisation active", "Initialisation du suivi GPS...");
    }

    public void updateNotification(final Location location) {
        final var content = String.format(
                "Position: %.6f, %.6f (±%.0fm)",
                location.getLatitude(),
                location.getLongitude(),
                location.getAccuracy()
        );
        final var notification = this.buildNotification("Géolocalisation active", content);
        if (systemNotificationManager != null) {
            systemNotificationManager.notify(NOTIFICATION_ID, notification);
        }
    }

    private Notification buildNotification(final String title, final String content) {
        final var notificationIntent = new Intent(context, MainActivity.class);
        final var pendingIntent = PendingIntent.getActivity(
                context,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(context, CHANNEL_ID)
                .setContentTitle(title)
                .setContentText(content)
                .setSmallIcon(android.R.drawable.ic_menu_mylocation)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    public int getNotificationId() {
        return NOTIFICATION_ID;
    }
}