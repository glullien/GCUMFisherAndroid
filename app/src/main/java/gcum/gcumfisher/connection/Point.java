package gcum.gcumfisher.connection;

import android.content.res.Resources;
import android.location.Location;
import android.support.annotation.NonNull;

import com.google.android.gms.maps.model.LatLng;

import gcum.gcumfisher.R;

public class Point {
    private final long latitude;
    private final long longitude;

    public Point(long latitude, long longitude) {
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public Point(double latitude, double longitude) {
        this.latitude = Math.round(latitude * 1E5);
        this.longitude = Math.round(longitude * 1E5);
    }

    public Point(@NonNull Location location) {
        this(location.getLatitude(), location.getLongitude());
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

    private double toRadian(long degree) {
        return (Math.PI * degree * 1E-5) / (180);
    }

    public long distance(@NonNull Point other) {
        final double R = 6378000;
        final double latA = toRadian(latitude);
        final double lonA = toRadian(longitude);
        final double latB = toRadian(other.latitude);
        final double lonB = toRadian(other.longitude);
        return Math.round(R * (Math.PI / 2 - Math.asin(Math.sin(latB) * Math.sin(latA) + Math.cos(lonB - lonA) * Math.cos(latB) * Math.cos(latA))));
    }

    public String distanceToString(@NonNull Resources resources, @NonNull Point other) {
        final int distance = Math.round(distance(other));
        if (distance < 1000) return resources.getString(R.string.distance_meters, distance);
        else
            return resources.getString(R.string.distance_kilometers, (distance / 100) * 1E-1);
    }

    public String toString(@NonNull Resources resources) {
        return resources.getString(R.string.coordinates, latitude * 1E-5, longitude * 1E-5);
    }
}
