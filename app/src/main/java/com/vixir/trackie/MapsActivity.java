package com.vixir.trackie;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;

import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationListener;

import android.location.Location;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResult;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;

import java.util.ArrayList;
import java.util.List;

import butterknife.ButterKnife;
import butterknife.OnClick;
import butterknife.internal.Utils;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, LocationListener {

    private static final String TAG = MapsActivity.class.getSimpleName();
    private static final int INTERVAL = 10000; // 10 sec
    private static final int FASTEST_INTERVAL = 5000;
    private static final int SMALLEST_DISPLACEMENT = 10;
    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 200;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 201;
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;
    private static final String BROADCAST = "com.vixir.trackie.android.action.broadcast";
    public static final String START_LOC = "shift-start-location";
    //    boolean mBound = false;
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Polyline line;
    private LocationRequest mLocationRequest;
    private boolean mRequestingLocationUpdates = false;
    private Location mCurrentLocation;
    private boolean mLocationUpdateState = false;
    private BroadcastReceiver mReceiver;
/*    private ServiceConnection mConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            LocationUpdateService.LocationUpdateBinder binder = (LocationUpdateService.LocationUpdateBinder) service;
            mService = binder.getService();
            mBound = true;
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            mBound = false;
        }
    };*/

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        ButterKnife.bind(this);
        if (isMyServiceRunning(LocationUpdateService.class)) {
            mRequestingLocationUpdates = true;
        }
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ArrayList<LatLng> latLngs = intent.getParcelableArrayListExtra(LocationUpdateService.LOC_MESSAGE);
                if (null != mCurrentLocation && mRequestingLocationUpdates == true) {
                    redrawLine(latLngs);
                }
                Log.e(TAG, "" + latLngs.size());
            }
        };
        if (LocationUtils.checkPlayServices(this)) {
            buildGoogleApiClient();
        } else {
            finish();
        }
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        createLocationRequest();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mGoogleApiClient.isConnected() && mLocationUpdateState) {
            startLocationUpdates();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
//        Intent intent = new Intent(this, LocationUpdateService.class);
//        bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
        if (null != mGoogleApiClient) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (null != mGoogleApiClient && mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        stopLocationUpdates();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        if (null != mGoogleApiClient && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
       /* if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }*/
    }

    protected synchronized void buildGoogleApiClient() {
        mGoogleApiClient = new GoogleApiClient.Builder(this).addConnectionCallbacks(this).addOnConnectionFailedListener(this).addApi(LocationServices.API).build();
        mGoogleApiClient.connect();
    }

    @OnClick(R.id.start_stop_toggle)
    protected void onToggle() {
        if (null == mCurrentLocation) {
            return;
        }
        if (!mRequestingLocationUpdates) {
            mRequestingLocationUpdates = true;
            startLocationUpdates();
            Intent intent = new Intent(this, LocationUpdateIntentService.class);
            Bundle extras = new Bundle();
            extras.putParcelable(START_LOC, LocationUtils.locationToLatLng(mCurrentLocation));
            startService(intent);
            LocalBroadcastManager.getInstance(this).registerReceiver((mReceiver), new IntentFilter(LocationUpdateService.LOC_RESULT));
            Log.d(TAG, "Periodic location updates started!");
        } else {
            mRequestingLocationUpdates = false;
            Intent intent = new Intent(this, LocationUpdateService.class);
            stopService(intent);
            Log.d(TAG, "Periodic location updates stopped!");
            placeMarkerOnMap(LocationUtils.locationToLatLng(mCurrentLocation));
        }
    }


    protected void stopLocationUpdates() {
        if (null != mGoogleApiClient && mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
    }

    private void startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.ACCESS_FINE_LOCATION},
                    LOCATION_PERMISSION_REQUEST_CODE);
            return;
        }
        LocationServices.FusedLocationApi.requestLocationUpdates(mGoogleApiClient, mLocationRequest, this);
        LocationAvailability locationAvailability = LocationServices.FusedLocationApi.getLocationAvailability(mGoogleApiClient);

        if (null != locationAvailability && locationAvailability.isLocationAvailable()) {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (mCurrentLocation != null) {
                LatLng currentLatLng = LocationUtils.locationToLatLng(mCurrentLocation);
                if (mMap != null) {
                    placeMarkerOnMap(currentLatLng);
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12));
                }
            }
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        LatLng latLong;
        latLong = new LatLng(-34, 151);
        if (null != mCurrentLocation) {
            double latitude = mCurrentLocation.getLatitude();
            double longitude = mCurrentLocation.getLongitude();
            LatLng latLng = new LatLng(latitude, longitude);
            placeMarkerOnMap(latLng);
        } else {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLong, 12));
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (null != location) {
            LatLng latLng = LocationUtils.locationToLatLng(location);
            placeMarkerOnMap(latLng);
        }
    }

    private void addMarkers(LatLng startlatLng, LatLng stopLatLng) {
        MarkerOptions options = new MarkerOptions();
        options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)).alpha(0.8f);
        mMap.addMarker(options.position(startlatLng).title("start"));
        mMap.setMyLocationEnabled(false);
        if (null != stopLatLng) {
            options.icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_MAGENTA)).alpha(0.8f);
            mMap.addMarker(options.position(stopLatLng).title("current"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(stopLatLng, 12));
        }
        Log.d(TAG, "Marker added.............................");
        Log.d(TAG, "Zoom done.............................");
    }

    private void redrawLine(ArrayList<LatLng> points) {
        Log.d(TAG, "redraw");

        final PolylineOptions options = new PolylineOptions().width(7).color(Color.BLUE);
        for (int i = 0; i < points.size(); i++) {
            LatLng point = points.get(i);
            options.add(point);
        }
        if (null != mMap) {
            mMap.clear();
            runOnUiThread(new Runnable() {

                @Override
                public void run()
                {
                    line = mMap.addPolyline(options);    //do your loop adding polyline
                }
            });
        }
        if (points.size() > 0) {
            addMarkers(points.get(0), points.get(points.size() - 1));
            fixZoom(points);
        } else {
            addMarkers(LocationUtils.locationToLatLng(mCurrentLocation), null);
        }
    }

    protected void createLocationRequest() {
        mLocationRequest = new LocationRequest();
        mLocationRequest.setInterval(INTERVAL);
        mLocationRequest.setFastestInterval(FASTEST_INTERVAL);
        mLocationRequest.setSmallestDisplacement(SMALLEST_DISPLACEMENT); //added
        mLocationRequest.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder().addLocationRequest(mLocationRequest);
        PendingResult<LocationSettingsResult> result = LocationServices.SettingsApi.checkLocationSettings(mGoogleApiClient, builder.build());
        result.setResultCallback(
                new ResultCallback<LocationSettingsResult>() {
                    @Override
                    public void onResult(@NonNull LocationSettingsResult locationSettingsResult) {
                        final Status status = locationSettingsResult.getStatus();
                        switch (status.getStatusCode()) {
                            case LocationSettingsStatusCodes.SUCCESS: {
                                mLocationUpdateState = true;
                                startLocationUpdates();
                                break;
                            }
                            case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                                try {
                                    status.startResolutionForResult(MapsActivity.this, PLAY_SERVICES_RESOLUTION_REQUEST);
                                } catch (IntentSender.SendIntentException e) {
                                }
                                break;
                            case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                                break;
                        }
                    }
                });
    }


    private void setUpMap() {
        boolean isPermissionGranted = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        if (!isPermissionGranted) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION);
            return;
        }
        if (null != mMap) {
            mMap.setMyLocationEnabled(true);
        }
    }

    protected void placeMarkerOnMap(LatLng location) {
        MarkerOptions markerOptions = new MarkerOptions().position(location).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)).alpha(0.8f);
        mMap.clear();
        mMap.setMyLocationEnabled(true);
        mMap.addMarker(markerOptions);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 12));
    }

    private void fixZoom(List<LatLng> points) {
        LatLngBounds.Builder bc = new LatLngBounds.Builder();
        for (LatLng item : points) {
            bc.include(item);
        }
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bc.build(), 50));
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.e(TAG, "connected");
        setUpMap();
        if (mLocationUpdateState) {
            startLocationUpdates();
        }
    }

    @Override
    public void onConnectionSuspended(int i) {
        mGoogleApiClient.connect();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {

        switch (requestCode) {
            case MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(MapsActivity.this, "Permission Granted, :)", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(MapsActivity.this, "Permission Denied,  :(", Toast.LENGTH_LONG).show();
                }
                return;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PLAY_SERVICES_RESOLUTION_REQUEST) {
            if (resultCode == RESULT_OK) {
                mLocationUpdateState = true;
                startLocationUpdates();
            }
        }
    }

    private boolean isMyServiceRunning(Class<?> serviceClass) {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (serviceClass.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

}
