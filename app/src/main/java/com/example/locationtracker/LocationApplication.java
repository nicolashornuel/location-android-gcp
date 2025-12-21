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
        private final int priority = Priority.PRIORITY_BALANCED_POWER_ACCURACY;
        @Builder.Default
        private final long updateInterval = 5 * 60 * 1000; // 5 minutes
        @Builder.Default
        private final long minUpdateInterval = 60 * 1000; // 1 minutes
        @Builder.Default
        private final float minUpdateDistanceMeters = 100; // 100 mètres
        @Builder.Default
        private final boolean waitForAccurateLocation = false;
    }
}
