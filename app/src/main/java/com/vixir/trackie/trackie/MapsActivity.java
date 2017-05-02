package com.vixir.trackie.trackie;

import android.Manifest;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.graphics.Color;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.common.api.PendingResult;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.common.api.Status;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.location.LocationListener;

import android.location.Location;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
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

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import io.realm.Realm;
import io.realm.RealmChangeListener;
import io.realm.RealmResults;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback, GoogleApiClient.OnConnectionFailedListener, GoogleApiClient.ConnectionCallbacks, LocationListener {

    private static final String TAG = MapsActivity.class.getSimpleName();

    private static final int MY_PERMISSIONS_REQUEST_ACCESS_FINE_LOCATION = 200;
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 201;
    private final static int PLAY_SERVICES_RESOLUTION_REQUEST = 1000;
    private static final String BROADCAST = "com.vixir.trackie.android.action.broadcast";
    public static final String START_LOC = "shift-start-location";
    private GoogleMap mMap;
    private GoogleApiClient mGoogleApiClient;
    private Polyline line;
    private LocationRequest mLocationRequest;
    private boolean mRequestingLocationUpdates = false;
    private Location mCurrentLocation;
    private boolean mLocationUpdateState = false;
    private BroadcastReceiver mReceiver;
    private RealmChangeListener mRealmChangeListener;
    private Realm mRealm = Realm.getDefaultInstance();
    @BindView(R.id.start_stop_toggle)
    protected Button mToggleButton;
    boolean loading = true;

    @BindView(R.id.animation_view)
    LottieAnimationView mAnimationView;

    @BindView(R.id.shift_end_message)
    protected TextView shiftEndMessage;

    //TODO update table as false.
    //TODO let user look at past trips

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        ButterKnife.bind(this);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        if (isMyServiceRunning(LocationUpdateService.class)) {
            Log.d(TAG, "service already running.");
            mRequestingLocationUpdates = true;
            mToggleButton.setText("STOP");
        } else {
            mToggleButton.setText("START");
        }
        mRealmChangeListener = new RealmChangeListener() {
            @Override
            public void onChange(Object element) {
                Log.d(TAG, "callback received");
                RealmResults<LatLngList> realmResults = (RealmResults<LatLngList>) element;
                if (realmResults.size() == 0) {
                    return;
                }
                MapsActivity.this.redrawLine(DBUtils.latLngFromLatLngPOJO(realmResults.last()));
            }
        };
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ArrayList<LatLng> latLngs = intent.getParcelableArrayListExtra(LocationUpdateService.LOC_MESSAGE);
                if (null != mCurrentLocation && mRequestingLocationUpdates == true) {
                    MapsActivity.this.redrawLine(latLngs);
                }
                Log.d(TAG, "size location data :- " + latLngs.size());
            }
        };
        LocalBroadcastManager.getInstance(this).registerReceiver((mReceiver), new IntentFilter(LocationUpdateService.LOC_RESULT));

        if (LocationUtils.checkPlayServices(this)) {
            buildGoogleApiClient();
        } else {
            finish();
        }
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

        if (null != mGoogleApiClient) {
            mGoogleApiClient.connect();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (null != mGoogleApiClient && mGoogleApiClient.isConnected()) {
            LocationServices.FusedLocationApi.removeLocationUpdates(mGoogleApiClient, this);
        }
        stopLocationUpdates();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(mReceiver);
        if (null != mGoogleApiClient && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
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
            mToggleButton.setText("STOP");
            mRequestingLocationUpdates = true;
            hideShiftCompleted();
            mRealm.executeTransaction(new Realm.Transaction() {
                @Override
                public void execute(Realm realm) {
                    LatLngList latLngList = realm.createObject(LatLngList.class, System.currentTimeMillis());
                    Log.d(TAG, "New LatLng List Object Created");
                }
            });
            placeMarkerOnMap(LocationUtils.locationToLatLng(mCurrentLocation));
            startLocationUpdates();
            if (!isMyServiceRunning(LocationUpdateService.class)) {
                Intent intent = new Intent(this, LocationUpdateIntentService.class);
                Bundle extras = new Bundle();
                extras.putParcelable(START_LOC, LocationUtils.locationToLatLng(mCurrentLocation));
                intent.putExtras(extras);
                startService(intent);
            }
            Log.d(TAG, "Periodic location updates started!");
        } else {
            mToggleButton.setText("START");
            mRequestingLocationUpdates = false;
            showShiftCompleted();
            Intent intent = new Intent(this, LocationUpdateService.class);
            Bundle extras = new Bundle();
            extras.putBoolean("stopservice", true);
            intent.putExtras(extras);
            stopService(intent);
            Log.d(TAG, "Periodic location updates stopped!");
            stopLocationUpdates();
        }
    }

    private void hideShiftCompleted() {
        shiftEndMessage.setVisibility(View.GONE);
    }

    private void showShiftCompleted() {
        RealmResults<LatLngList> realmQuery = mRealm.where(LatLngList.class).findAll();
        if (realmQuery != null && realmQuery.size() > 0) {
            LatLngList currentLatLngList = realmQuery.last();
            long startShift = currentLatLngList.getId();
            long stopShift = System.currentTimeMillis();
            long diff = stopShift - startShift;
            long diffSeconds = diff / 1000 % 60;
            long diffMinutes = diff / (60 * 1000) % 60;
            long diffHours = diff / (60 * 60 * 1000) % 24;
            long diffDays = diff / (24 * 60 * 60 * 1000);
            String days = (diffDays == 0) ? "" : diffDays + "d";
            String hours = (diffHours == 0) ? "" : diffHours + "h";
            String min = (diffMinutes == 0) ? "" : diffMinutes + "m";
            String sec = (diffSeconds == 0) ? "" : diffSeconds + "s";
            shiftEndMessage.setText("Shift Duration : " + days + " " + hours + "" + min + " " + sec + " ");
            shiftEndMessage.setVisibility(View.VISIBLE);
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
        if (mRequestingLocationUpdates) {
            updateMapFromDB();
        }
        if (null != locationAvailability && locationAvailability.isLocationAvailable()) {
            mCurrentLocation = LocationServices.FusedLocationApi.getLastLocation(mGoogleApiClient);
            if (mCurrentLocation != null) {
                LatLng currentLatLng = LocationUtils.locationToLatLng(mCurrentLocation);
                if (mMap != null && !mRequestingLocationUpdates) {
                    Log.d(TAG, "inside start Location Updates");
                    placeMarkerOnMap(currentLatLng);
                    mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 12));
                }
            }
        }
    }

    private void updateMapFromDB() {
        RealmResults<LatLngList> realmQuery = mRealm.where(LatLngList.class).findAll();
        Log.d(TAG, "Query size()" + realmQuery.size());
        if (realmQuery != null && realmQuery.size() > 0) {
            LatLngList currentLatLngList = realmQuery.last();
            ArrayList<LatLng> latLngs = DBUtils.latLngFromLatLngPOJO(currentLatLngList);
            redrawLine(latLngs);
        }
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        LatLng latLong;

        mMap.setOnMapLoadedCallback(new GoogleMap.OnMapLoadedCallback() {
            @Override
            public void onMapLoaded() {
                RealmResults<LatLngList> latLngLists = mRealm.where(LatLngList.class).findAll();
                latLngLists.addChangeListener(mRealmChangeListener);
            }
        });
        latLong = new LatLng(-34, 151);
        if (null != mCurrentLocation) {
            double latitude = mCurrentLocation.getLatitude();
            double longitude = mCurrentLocation.getLongitude();
            LatLng latLng = new LatLng(latitude, longitude);
            Log.d(TAG, "inside on map ready");
            placeMarkerOnMap(latLng);
        } else {
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(latLong, 12));
        }
    }

    @Override
    public void onLocationChanged(Location location) {
        if (null != location && !mRequestingLocationUpdates) {
            mCurrentLocation = location;
            LatLng latLng = LocationUtils.locationToLatLng(location);
            Log.d(TAG, "inside on location changed");
            placeMarkerOnMap(latLng);
        }
    }

    private void addMarkers(LatLng startlatLng, LatLng stopLatLng) {
        if (mMap == null) {
            return;
        }
        if (mAnimationView.isAnimating()) {
            mAnimationView.cancelAnimation();
            mAnimationView.setVisibility(View.GONE);
            mToggleButton.setVisibility(View.VISIBLE);
        }
        MarkerOptions options = new MarkerOptions();
        options.icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_start)).flat(true).anchor(0.5f, 0.5f);
        mMap.addMarker(options.position(startlatLng).title("start"));
        //noinspection MissingPermission
        mMap.setMyLocationEnabled(false);
        if (null != stopLatLng) {
            options.icon(BitmapDescriptorFactory.fromResource(R.mipmap.ic_tracker));
            mMap.addMarker(options.position(stopLatLng).title("current"));
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(stopLatLng, 12));
        }
        Log.d(TAG, "Marker added.............................");
        Log.d(TAG, "Zoom done.............................");
    }

    private void redrawLine(ArrayList<LatLng> points) {
        Log.d(TAG, "redraw");
        if (null == mMap) {
            return;
        }

        final PolylineOptions options = new PolylineOptions().width(7).color(Color.BLUE);
        for (int i = 0; i < points.size(); i++) {
            LatLng point = points.get(i);
            options.add(point);
        }
        mMap.clear();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                line = mMap.addPolyline(options);
            }
        });
        if (points.size() > 0) {
            addMarkers(points.get(0), points.get(points.size() - 1));
            fixZoom(points);
        } else {
            addMarkers(LocationUtils.locationToLatLng(mCurrentLocation), null);
        }
    }

    protected void createLocationRequest() {
        mLocationRequest = LocationUtils.getLocationRequest();
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
        //noinspection MissingPermission
        mMap.setMyLocationEnabled(true);
        mMap.addMarker(markerOptions);
        mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(location, 12), new GoogleMap.CancelableCallback() {
            @Override
            public void onFinish() {
                mAnimationView.cancelAnimation();
                mAnimationView.setVisibility(View.GONE);
                mToggleButton.setVisibility(View.VISIBLE);
            }

            @Override
            public void onCancel() {
            }
        });
    }

    private void fixZoom(List<LatLng> points) {
        LatLngBounds.Builder bc = new LatLngBounds.Builder();
        for (LatLng item : points) {
            bc.include(item);
        }
        mMap.moveCamera(CameraUpdateFactory.newLatLngBounds(bc.build(), 13));
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.i(TAG, "Connection failed: ConnectionResult.getErrorCode() = " + connectionResult.getErrorCode());
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG, "connected");
        setUpMap();
        if (mLocationUpdateState) {
            startLocationUpdates();
        }
    }


    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "connection suspended");
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
