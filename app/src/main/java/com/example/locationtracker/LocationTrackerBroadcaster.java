package com.example.locationtracker;

import android.content.Context;
import android.content.Intent;
import android.location.Location;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class LocationTrackerBroadcaster {

    public static final String ACTION_LOCATION_UPDATE = "com.example.monapp.LOCATION_UPDATE";
    public static final String EXTRA_LATITUDE = "latitude";
    public static final String EXTRA_LONGITUDE = "longitude";
    public static final String EXTRA_ACCURACY = "accuracy";
    public static final String EXTRA_SPEED = "speed";
    public static final String EXTRA_ALTITUDE = "altitude";
    public static final String EXTRA_TIMESTAMP = "timestamp";

    @NonNull
    private final Context context;

    public void broadcastLocationUpdate(Location location) {
        final var intent = new Intent(ACTION_LOCATION_UPDATE);
        intent.putExtra(EXTRA_LATITUDE, location.getLatitude());
        intent.putExtra(EXTRA_LONGITUDE, location.getLongitude());
        intent.putExtra(EXTRA_ACCURACY, location.getAccuracy());
        intent.putExtra(EXTRA_SPEED, location.getSpeed());
        intent.putExtra(EXTRA_ALTITUDE, location.getAltitude());
        intent.putExtra(EXTRA_TIMESTAMP, location.getTime());

        context.sendBroadcast(intent);
    }

    public static Location extractLocationFromIntent(final Intent intent) {
        if (intent == null) {
            return null;
        }

        final var location = new Location("broadcast");
        location.setLatitude(intent.getDoubleExtra(EXTRA_LATITUDE, 0.0));
        location.setLongitude(intent.getDoubleExtra(EXTRA_LONGITUDE, 0.0));
        location.setAccuracy(intent.getFloatExtra(EXTRA_ACCURACY, 0f));
        location.setSpeed(intent.getFloatExtra(EXTRA_SPEED, 0f));
        location.setAltitude(intent.getDoubleExtra(EXTRA_ALTITUDE, 0.0));
        location.setTime(intent.getLongExtra(EXTRA_TIMESTAMP, 0L));

        return location;
    }
}
