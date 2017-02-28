package gcum.gcumfisher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;

import java.util.List;

import gcum.gcumfisher.connection.Point;
import gcum.gcumfisher.connection.Server;
import gcum.gcumfisher.util.AsyncTaskE;

public class MapActivity extends Activity implements OnMapReadyCallback, GoogleMap.OnMarkerClickListener {

    private static final int SHOW_LIST = 1;
    private Server server;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.map);

        MapFragment mapFragment = (MapFragment) getFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        server = new Server(getResources());
    }

    @Override
    public void onMapReady(GoogleMap map) {
        LatLngBounds bounds = new LatLngBounds(new LatLng(48.819117, 2.248415), new LatLng(48.902543, 2.439249));
        map.moveCamera(CameraUpdateFactory.newLatLngBounds(bounds, 0));
        map.setOnMarkerClickListener(this);
        new GetPoints(map).execute();
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

    class GetPoints extends AsyncTaskE<Boolean, Boolean, List<Point>> {
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
            displayError("Internal error: " + error);
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
