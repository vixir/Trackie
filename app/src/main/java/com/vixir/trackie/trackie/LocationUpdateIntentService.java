package com.vixir.trackie.trackie;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.util.Log;

import com.google.android.gms.maps.model.LatLng;

import static com.vixir.trackie.trackie.MapsActivity.START_LOC;


public class LocationUpdateIntentService extends IntentService {
    private static final String TAG = LocationUpdateIntentService.class.getSimpleName();

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public LocationUpdateIntentService(String name) {
        super(name);
    }

    public LocationUpdateIntentService() {
        super("LocationUpdateIntentService");
    }


    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        LatLng latlng = ((LatLng) intent.getParcelableExtra(MapsActivity.START_LOC));
        Intent service = new Intent(this, LocationUpdateService.class);
        Bundle extras = new Bundle();
        extras.putParcelable(START_LOC, latlng);
        service.putExtras(extras);
        this.startService(service);
        Log.d(TAG, "started");
    }
}
