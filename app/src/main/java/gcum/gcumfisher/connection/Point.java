package gcum.gcumfisher.connection;

import android.location.Location;
import android.support.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

public class Point {
    private final long latitude;
    private final long longitude;

    public Point(long latitude, long longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }
    public Point(@NonNull Location location) {
        this.latitude = Math.round(location.getLatitude()*1E5);
        this.longitude = Math.round(location.getLongitude()*1E5);
    }

    public LatLng toLatLng() {
        return new LatLng(latitude * 1E-5, longitude * 1E-5);
    }

    public long getLatitude() {
        return latitude;
    }

    public long getLongitude() {
        return longitude;
    }
}
