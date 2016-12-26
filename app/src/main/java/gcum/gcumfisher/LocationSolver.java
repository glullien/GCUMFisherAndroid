package gcum.gcumfisher;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * Track the location using GPS and use Geocoder to translate the position into spots
 */
class LocationSolver {

    interface Listener {
        void displayError(@NonNull CharSequence message);

        void setLocationProgressMessage(@NonNull CharSequence text);

        void setLocationResults(@NonNull List<Spot> addresses);
    }

    private final Listener listener;
    private final Activity activity;
    private final int maxResults;

    private LocationSolver(Activity activity, Listener listener, int maxResults) {
        this.activity = activity;
        this.listener = listener;
        this.maxResults = maxResults;
    }

    /**
     * Background task pour interroger le serveur
     */
    private class QueryAddress extends AsyncTask<Location, Spot.FilteredResults, Integer> {

        private final Geocoder geocoder;

        QueryAddress(@NonNull Context context) {
            geocoder = new Geocoder(context, Locale.getDefault());
        }

        /**
         * Appelé quand une adresse possible est trouvée
         */
        @Override
        protected void onProgressUpdate(Spot.FilteredResults... spots) {
            if ((spots != null) && (spots.length == 1)) update(spots[0]);
            else listener.displayError(activity.getString(R.string.error));
        }

        private void update(Spot.FilteredResults spots) {
            if (!spots.isEmpty()) listener.setLocationResults(spots.spots);
            else if (spots.outOfParis != null)
                listener.displayError(activity.getString(R.string.not_in_paris, spots.outOfParis));
            else listener.displayError(activity.getString(R.string.error));
        }

        /**
         * Commence la recherche avec le texte entré par le user
         */
        @Override
        protected Integer doInBackground(Location... params) {
            if (params != null) callGeocoder(params[0]);
            return 0;
        }

        private void callGeocoder(@NonNull Location location) {
            try {
                // Interroge le serveur (appel synchrone) sur les rue dans Paris (coordonnées GPS)
                final List<Address> addresses = geocoder.getFromLocation(location.getLatitude(), location.getLongitude(), maxResults);
                if (isCancelled()) return;
                if (addresses != null) {
                    // Vérifie que l'adresse est parisienne avant de la proposer au user
                    final Spot.FilteredResults spots = Spot.filterParisSpots(addresses);
                    publishProgress(spots);
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }


    private LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            listener.setLocationProgressMessage(activity.getText(R.string.street_lookup));
            new QueryAddress(activity).execute(location);
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
            listener.displayError(activity.getString(R.string.location_provider_disabled, provider));
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
