package gcum.gcumfisher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.List;

import gcum.gcumfisher.connection.Point;
import gcum.gcumfisher.connection.Server;
import gcum.gcumfisher.util.AsyncTaskE;

public class MapActivity extends Activity implements OnMapReadyCallback, GoogleMap.OnCameraIdleListener, GoogleMap.OnMarkerClickListener {


    public static final String LATITUDE = "gcum.gcumfisher.MapActivity.LATITUDE";
    public static final String LONGITUDE = "gcum.gcumfisher.MapActivity.LONGITUDE";

    private static final int SHOW_LIST = 1;
    private Server server;
    private GoogleMap map;
    private Point here;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map);

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        server = new Server(getResources());

        final Intent intent = getIntent();
        if (intent == null) here = null;
        else {
            final double latitude = intent.getDoubleExtra(LATITUDE, Double.NaN);
            final double longitude = intent.getDoubleExtra(LONGITUDE, Double.NaN);
            here = (Double.isNaN(latitude) || Double.isNaN(longitude)) ? null : new Point(latitude, longitude);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.map, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.here:
                if (here != null) zoom(here);
                return true;
            case R.id.paris:
                zoomOnParis();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onMapReady(GoogleMap map) {
        this.map = map;
        map.setOnMarkerClickListener(this);
        map.setOnCameraIdleListener(this);
        new GetPoints(map).execute();
    }

    private void zoom(Point point) {
        map.moveCamera(CameraUpdateFactory.newLatLngZoom(point.toLatLng(), 16));
    }

    private void zoomOnParis() {
        LatLngBounds bounds = new LatLngBounds(new LatLng(48.819117, 2.248415), new LatLng(48.902543, 2.439249));
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
    }

    @Override
    public void onCameraIdle() {
        if (here != null) {
            final BitmapDescriptor icon = BitmapDescriptorFactory.fromResource(android.R.drawable.ic_menu_mylocation);
            map.addMarker(new MarkerOptions().position(here.toLatLng()).icon(icon));
            zoom(here);
        } else zoomOnParis();
        map.setOnCameraIdleListener(null);
    }

    @Override
    public boolean onMarkerClick(Marker marker) {
        final Point point = (Point) marker.getTag();
        if (point != null) {
            final Intent listIntent = new Intent(this, ListActivity.class);
            listIntent.putExtra(ListActivity.TYPE, ListActivity.FOR_ONE_POINT);
            listIntent.putExtra(ListActivity.LATITUDE, point.getLatitude());
            listIntent.putExtra(ListActivity.LONGITUDE, point.getLongitude());
            startActivityForResult(listIntent, SHOW_LIST);
        }
        return false;
    }

    private class GetPoints extends AsyncTaskE<Boolean, Boolean, List<Point>> {
        private final GoogleMap map;

        GetPoints(GoogleMap map) {
            this.map = map;
        }

        @Override
        protected void onPostExecuteSuccess(List<Point> points) {
            if (points != null) for (Point point : points)
                map.addMarker(new MarkerOptions().position(point.toLatLng())).setTag(point);
        }

        @Override
        protected void onPostExecuteError(Exception error) {
            displayError(getResources().getString(R.string.error_message, error.getMessage()));
        }

        @Override
        protected List<Point> doInBackgroundOrCrash(Boolean[] params) throws Exception {
            return server.getPoints();
        }
    }

    public void displayError(@NonNull CharSequence message) {
        new AlertDialog.Builder(this).setTitle(R.string.error).setMessage(message).setIcon(android.R.drawable.ic_dialog_alert).show();
    }
}
