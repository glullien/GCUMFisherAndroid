package gcum.gcumfisher;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import gcum.gcumfisher.connection.Point;
import gcum.gcumfisher.connection.Server;
import gcum.gcumfisher.connection.ServerPhoto;
import gcum.gcumfisher.util.AsyncTaskE;

/**
 * Page pour chercher une adresse à la main
 */
public class SetLocationActivity extends Activity {

    public static final int FORCE_ADDRESS = 1;
    public static final int CANCELED = 2;
    public static final String FORCE_ADDRESS_STREET = "gcum.gcumfisher.SetLocationActivity.FORCE_ADDRESS_STREET";
    public static final String FORCE_ADDRESS_DISTRICT = "gcum.gcumfisher.SetLocationActivity.FORCE_ADDRESS_DISTRICT";
    public static final String LATITUDE = "gcum.gcumfisher.SetLocationActivity.LATITUDE";
    public static final String LONGITUDE = "gcum.gcumfisher.SetLocationActivity.LONGITUDE";

    /**
     * Proposition d'adresses
     */
    private final List<Spot> spots = new ArrayList<>();
    private List<Spot> closestSpots = null;
    private Server server;

    private class QueryServerAddress extends AsyncTaskE<String, Boolean, List<Spot>> {
        @Override
        protected void onPreExecute() {
            ProgressBar wheel = (ProgressBar) findViewById(R.id.search_street_progress);
            wheel.setVisibility(View.VISIBLE);
            wheel.setIndeterminate(true);
        }

        @Override
        protected void onPostExecuteSuccess(List<Spot> spots) {
            if ((spots != null) && !isCancelled()) addSpots(spots);
            findViewById(R.id.search_street_progress).setVisibility(View.GONE);
        }

        @Override
        protected void onPostExecuteError(Exception error) {
            error.printStackTrace();
            findViewById(R.id.search_street_progress).setVisibility(View.GONE);
            final TextView errorView = (TextView) findViewById(R.id.error);
            errorView.setVisibility(View.VISIBLE);
            errorView.setText(getResources().getString(R.string.error_message, error.getMessage()));
        }

        @Override
        protected List<Spot> doInBackgroundOrCrash(String[] params) throws Exception {
            List<Spot> spots = new ArrayList<>(10);
            for (ServerPhoto.Address address : server.searchAddress(params[0], 10)) {
                spots.add(new Spot(address.getStreet(), address.getDistrict()));
            }
            return spots;
        }
    }

    /**
     * Ajoute des adresses possibles à la page
     */
    private void addSpots(@NonNull List<Spot> spots) {
        for (final Spot spot : spots)
            if ((spot != null) && !this.spots.contains(spot)) this.spots.add(spot);
        updatePossibleSpots();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_location);
        server = new Server(getResources());

        ((EditText) findViewById(R.id.streetInput)).addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                startLookup(s.toString());
            }
        });

        ((ListView) findViewById(R.id.streets)).setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Spot spot = spots.get(position);
                if (spot != null) finish(spot);
            }
        });

        findViewById(R.id.search_street_progress).setVisibility(View.GONE);
        findViewById(R.id.error).setVisibility(View.GONE);

        final Intent intent = getIntent();
        final double latitude = intent.getDoubleExtra(LATITUDE, Double.NaN);
        final double longitude = intent.getDoubleExtra(LONGITUDE, Double.NaN);
        if ((!Double.isNaN(latitude)) && (!Double.isNaN(longitude)))
            new QueryClosestStreets().execute(new Point(latitude, longitude));
    }

    private class QueryClosestStreets extends AsyncTaskE<Point, Boolean, List<Spot>> {
        @Override
        protected void onPostExecuteError(Exception error) {
        }

        @Override
        protected void onPostExecuteSuccess(List<Spot> spots) {
            closestSpots = spots;
            if (((EditText) findViewById(R.id.streetInput)).getText().toString().length() < 2)
                addSpots(spots);
        }

        @Override
        protected List<Spot> doInBackgroundOrCrash(Point[] params) throws Exception {
            if ((params == null) || (params.length == 0)) throw new Exception("missing location");
            final List<ServerPhoto.Address> addresses = server.searchClosest(params[0], 10);
            List<Spot> spots = new ArrayList<>(addresses.size());
            for (final ServerPhoto.Address address : addresses)
                spots.add(new Spot(address.getStreet(), address.getDistrict()));
            return spots;
        }
    }

    private QueryServerAddress currentLookup;

    /**
     * Lance une nouvelle recherche
     */
    private void startLookup(@NonNull String input) {
        // Annule la recherche en cours
        if (currentLookup != null) currentLookup.cancel(false);
        spots.clear();
        findViewById(R.id.error).setVisibility(View.GONE);
        updatePossibleSpots();
        if (input.length() > 1) {
            // Start la nouvelle recherche (appel asynchrone)
            currentLookup = new QueryServerAddress();
            currentLookup.execute(input);
        } else if (closestSpots != null) addSpots(closestSpots);
    }

    /**
     * Renvoie l'adresse choisi par le user
     */
    private void finish(Spot spot) {
        Intent intent = new Intent();
        intent.putExtra(FORCE_ADDRESS_STREET, spot.street);
        intent.putExtra(FORCE_ADDRESS_DISTRICT, spot.district);
        setResult(FORCE_ADDRESS, intent);
        finish();
    }

    /**
     * Update l'UI
     */
    public void updatePossibleSpots() {
        ArrayList<String> strings = new ArrayList<>(spots.size());
        for (Spot spot : spots) strings.add(spot.toString(getResources()));
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, strings);
        ((ListView) findViewById(R.id.streets)).setAdapter(adapter);
    }

    public void cancel(View view) {
        setResult(CANCELED);
        finish();
    }
}
