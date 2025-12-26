package com.example.locationtracker;

import android.app.Application;
import android.content.Context;
import android.location.Geocoder;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;

import java.util.Locale;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

public class LocationApplication extends Application {

    @Getter
    private Container container;

    @Override
    public void onCreate() {
        super.onCreate();
        final var context = this.getApplicationContext();
        final var fusedLocationClient = LocationServices.getFusedLocationProviderClient(context);
        final var geocoder = new Geocoder(context, Locale.getDefault());
        this.container = new Container(
                context,
                new LocationTrackerManager(context, fusedLocationClient, LocationConfig.builder().build()),
                new LocationTrackerRepository(geocoder),
                new LocationTrackerNotification(context),
                new LocationTrackerBroadcaster(context));
    }

    @Getter
    @AllArgsConstructor
    public class Container {
        private final Context applicationContext;
        private final LocationTrackerManager manager;
        private final LocationTrackerRepository repository;
        private final LocationTrackerNotification notifier;
        private final LocationTrackerBroadcaster broadcaster;
    }

    @Getter
    @Builder
    public static class LocationConfig {
        @Builder.Default
        private final int priority = Priority.PRIORITY_HIGH_ACCURACY; // GPS pur
        @Builder.Default
        private final long updateInterval = 10 * 1000; // callback souhaité toutes les 10s
        @Builder.Default
        private final long minUpdateInterval = 5 * 1000; // pas plus d’une fois toutes les 5s
        @Builder.Default
        private final float minUpdateDistanceMeters = 50; // déclenche seulement si mouvement >50m
        @Builder.Default
        private final boolean waitForAccurateLocation = true; // attend fix GPS précis
        @Builder.Default
        private final float maxAccuracy = 10; // ignore positions >10 m
    }
}
