package gcum.gcumfisher;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;

import java.util.Collections;
import java.util.List;

import gcum.gcumfisher.connection.Point;
import gcum.gcumfisher.connection.Server;
import gcum.gcumfisher.connection.ServerPhoto;
import gcum.gcumfisher.util.AsyncTaskE;

/**
 * Track the location using GPS and use Geocoder to translate the position into spots
 */
class LocationSolver {

    private final Server server;

    interface Listener {
        void displayError(@NonNull CharSequence message);

        void setLocationProgressMessage(@NonNull CharSequence text);

        void setLocation(@Nullable Location location);

        void setLocationResults(@NonNull List<Spot> addresses);
    }

    private final Listener listener;
    private final Activity activity;
    private final int maxResults;

    private LocationSolver(Activity activity, Listener listener, int maxResults) {
        this.activity = activity;
        this.listener = listener;
        this.maxResults = maxResults;
        server = new Server(activity.getResources());
    }

    private class QueryStreet extends AsyncTaskE<Location, Boolean, Spot> {
        @Override
        protected void onPostExecuteError(Exception error) {
            listener.displayError(activity.getString(R.string.error));
        }

        @Override
        protected void onPostExecuteSuccess(Spot spot) {
            listener.setLocationResults(Collections.singletonList(spot));
        }

        @Override
        protected Spot doInBackgroundOrCrash(Location[] params) throws Exception {
            if ((params == null) || (params.length == 0)) throw new Exception("missing location");
            final List<ServerPhoto.Address> addresses= server.searchClosest(new Point(params[0]), 1);
            if (addresses.size() == 0) throw new Exception("missing addresses");
            return new Spot(addresses.get(0).getStreet(), addresses.get(0).getDistrict());
        }
    }

    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            listener.setLocationProgressMessage(activity.getText(R.string.street_lookup));
            listener.setLocation(location);
            new QueryStreet().execute(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
            listener.setLocationResults(Collections.<Spot>emptyList());
            listener.displayError(activity.getString(R.string.location_provider_disabled, provider));
            listener.setLocation(null);
        }
    };

    private void trackCurrentLocation() {
        LocationManager locationManager = (LocationManager) activity.getSystemService(Context.LOCATION_SERVICE);
        boolean fineLocationRefused = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        boolean coarseLocationRefused = ActivityCompat.checkSelfPermission(activity, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        if (fineLocationRefused && coarseLocationRefused) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            listener.displayError(activity.getText(R.string.gps_call_rejected));
            return;
        }
        listener.setLocationProgressMessage(activity.getText(R.string.gps_calling));
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10000, 10, locationListener);
        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 10000, 10, locationListener);
    }

    static void startTracking(Activity activity, Listener listener, int maxResults) {
        new LocationSolver(activity, listener, maxResults).trackCurrentLocation();
    }
}
