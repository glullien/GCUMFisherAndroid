package gcum.gcumfisher;

import android.app.Activity;
import android.content.Context;
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

import java.util.ArrayList;
import java.util.List;

import gcum.gcumfisher.opendataparisfr.Request;

/**
 * Page pour chercher une adresse à la main
 */
public class SetLocationActivity extends Activity {

    public static final int FORCE_ADDRESS = 1;
    public static final int CANCELED = 2;
    public static final String FORCE_ADDRESS_STREET = "gcum.gcumfisher.SetLocationActivity.FORCE_ADDRESS_STREET";
    public static final String FORCE_ADDRESS_DISTRICT = "gcum.gcumfisher.SetLocationActivity.FORCE_ADDRESS_DISTRICT";

    /**
     * Proposition d'adresses
     */
    private final List<Spot> spots = new ArrayList<>();

    /**
     * Background task pour chercher une ruee
     */
    class QueryAddress extends Request {

        QueryAddress(@NonNull Context context) {
            super(context.getResources(), 10);
        }

        @Override
        protected void onPreExecute() {
            ProgressBar wheel = (ProgressBar) findViewById(R.id.search_street_progress);
            wheel.setVisibility(View.VISIBLE);
            wheel.setIndeterminate(true);
        }

        /**
         * Appelé quand une adresse possible est trouvée
         */
        @Override
        protected void onProgressUpdate(Spot... spots) {
            if ((spots != null) && !isCancelled())
                for (Spot spot : spots) if (spot != null) addSpot(spot);
        }

        @Override
        protected void onPostExecute(Integer integer) {
            findViewById(R.id.search_street_progress).setVisibility(View.GONE);
        }
    }

    /**
     * Ajoute une nouvelle adresse possible à la page
     */
    private void addSpot(@NonNull Spot spot) {
        if (!spots.contains(spot)) spots.add(spot);
        updatePossibleSpots();
    }


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.set_location);

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
    }

    private QueryAddress currentLookup;

    /**
     * Lance une nouvelle recherche
     */
    private void startLookup(@NonNull String input) {
        // Annule la recherche en cours
        if (currentLookup != null) currentLookup.cancel(false);
        spots.clear();
        updatePossibleSpots();
        if (input.length() > 0) {
            // Start la nouvelle recherche (appel asynchrone)
            currentLookup = new QueryAddress(SetLocationActivity.this);
            currentLookup.execute(input);
        }
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
