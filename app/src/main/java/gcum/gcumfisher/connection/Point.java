package gcum.gcumfisher.connection;

import com.google.android.gms.maps.model.LatLng;

public class Point {
    private final long latitude;
    private final long longitude;

    public Point(long latitude, long longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
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
