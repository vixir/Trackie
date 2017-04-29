package com.vixir.trackie;

import android.Manifest;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmQuery;
import io.realm.RealmResults;

public class LocationUpdateService extends Service implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String TAG = "DRIVER";
    public static final String LOC_MESSAGE = "com.vixir.trackie.LocationUpdateService.LOC_MESSAGE";
    public static final String LOC_RESULT = "com.vixir.trackie.LocationUpdateService.LOC_RESULT";
    private double currentLat, currentLng;
    private GoogleApiClient mGoogleApiClient;
    private LocationRequest mLocationRequest;
    private ArrayList<LatLng> mPoints;
    private LocationListener locationListener;
    private LocalBroadcastManager mBroadcaster;
    private static Realm mRealm = Realm.getDefaultInstance();

    private class LocationListener implements com.google.android.gms.location.LocationListener {

        public LocationListener() {
        }

        @Override
        public void onLocationChanged(Location location) {
            Log.e(TAG, "onLocationChanged: " + location);
            currentLat = location.getLatitude();
            currentLng = location.getLongitude();
            mPoints.add(new LatLng(currentLat, currentLng));
            sendResult();
        }
    }


    @Override
    public IBinder onBind(Intent arg0) {
        Log.d(TAG, "onBind");
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        boolean stopService = false;
        Log.d(TAG, "onStartCommand");
        Log.d(TAG, "intent!=null :-" + (intent == null));

        if (intent != null) {
            stopService = intent.getBooleanExtra("stopservice", false);
            mPoints.add((LatLng) intent.getParcelableExtra(MapsActivity.START_LOC));
            //mPoints refers to the current running location points list
        }
        RealmResults<LatLngList> realmQuery = mRealm.where(LatLngList.class).findAll();
        Log.d(TAG, "Query size()" + realmQuery.size());
        if (realmQuery != null) {
            LatLngList currentLatLngList = realmQuery.last();
            mPoints = DBUtils.latLngFromLatLngPOJO(currentLatLngList);
            Log.d(TAG, "Query size()" + mPoints.size());
        }
        locationListener = new LocationListener();
        if (stopService)
            stopLocationUpdates();
        else {
            if (!mGoogleApiClient.isConnected()) {
                mGoogleApiClient.connect();
            }
        }

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        mPoints = new ArrayList<LatLng>();
        mBroadcaster = LocalBroadcastManager.getInstance(this);
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(LocationServices.API).addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this).build();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        Log.d(TAG, "onUnbind");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "onDestroy");
        super.onDestroy();
    }

    public void stopLocationUpdates() {
        LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, locationListener);
        if (mGoogleApiClient.isConnected())
            mGoogleApiClient.disconnect();
    }


    @Override
    public void onConnectionFailed(ConnectionResult arg0) {
        Log.e(TAG, "ConnectiondFailed");
    }

    @Override
    public void onConnected(Bundle arg0) {
        Log.d(TAG, "onConnected");
        mLocationRequest = LocationUtils.getLocationRequest();
        startLocationUpates();
    }

    private void startLocationUpates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, locationListener);
    }


    @Override
    public void onConnectionSuspended(int arg0) {
        Log.d(TAG, "suspended");
    }


    public void sendResult() {
        if (mPoints == null) {
            return;
        }

        //fetch current running list of location points and upadate it in the background.

        RealmResults<LatLngList> realmQuery = mRealm.where(LatLngList.class).findAll();
        if (realmQuery != null && realmQuery.size() != 0) {
            final LatLngList currentLatLngList = realmQuery.last();
            mRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    LatLngList latLngList = new LatLngList();
                    latLngList.setLatLngList(mPoints);
                    latLngList.setId(currentLatLngList.getId());
                    realm.copyToRealmOrUpdate(latLngList);
                    Log.d(TAG, "current location list updated");
                }
            });
        }
        //Update UI when App is running.
        Intent intent = new Intent(LOC_RESULT);
        intent.putParcelableArrayListExtra(LOC_MESSAGE, mPoints);
        mBroadcaster.sendBroadcast(intent);
    }
}
