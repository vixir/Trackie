package com.vixir.trackie;


import com.google.android.gms.maps.model.LatLng;

import java.util.ArrayList;

import io.realm.RealmList;
import io.realm.RealmObject;
import io.realm.annotations.PrimaryKey;

public class LatLngList extends RealmObject {
    @PrimaryKey
    private long id;

    private boolean isTripFinished;

    private long stopTimeStamp;

    public RealmList<LatLngPojo> latLngList;

    public void setLatLngList(ArrayList<LatLng> latLngArrayList) {
        latLngList = new RealmList<>();
        for (LatLng latLng : latLngArrayList) {
            latLngList.add(DBUtils.latLngPOJOFromLatLng(latLng));
        }
    }

    public void setId(long id) {
        this.id = id;
    }

    public long getId() {
        return id;
    }

    public boolean isTripFinished() {
        return isTripFinished;
    }

    public void setTripFinished(boolean tripFinished) {
        isTripFinished = tripFinished;
    }

    public long getStopTimeStamp() {
        return stopTimeStamp;
    }

    public void setStopTimeStamp(long stopTimeStamp) {
        this.stopTimeStamp = stopTimeStamp;
    }
}
