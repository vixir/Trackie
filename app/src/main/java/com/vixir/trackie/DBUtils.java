package com.vixir.trackie;

import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

public class DBUtils {

    public static LatLngPojo latLngPOJOFromLatLng(LatLng latLng) {
        LatLngPojo latLngPojo = new LatLngPojo();
        latLngPojo.setLatitude(latLng.latitude);
        latLngPojo.setLongitude(latLng.longitude);
        return latLngPojo;
    }

    public static ArrayList<LatLng> latLngFromLatLngPOJO(LatLngList element) {
        if (null == element) {
            return null;
        }
        ArrayList<LatLng> latLngList = new ArrayList();
        for (LatLngPojo latLngPojo : element.latLngList) {
            LatLng latLng = new LatLng(latLngPojo.getLatitude(), latLngPojo.getLongitude());
            latLngList.add(latLng);
        }
        return latLngList;
    }
}
