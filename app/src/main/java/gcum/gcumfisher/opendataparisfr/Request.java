package gcum.gcumfisher.opendataparisfr;

import android.content.res.Resources;
import android.os.AsyncTask;
import android.support.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import gcum.gcumfisher.Chars;
import gcum.gcumfisher.R;
import gcum.gcumfisher.Spot;

/**
 * Use a list of streets for a fuzzy search.
 * List downloaded from https://opendata.paris.fr/explore/dataset/voiesactuellesparis2012/
 */
public class Request extends AsyncTask<String, Spot, Integer> {

    private static List<Spot> streets;

    @NonNull
    private static List<Spot> getStreets(@NonNull Resources resources) throws IOException {
        if (streets == null) {
            final List<Spot> res = new ArrayList<>(8000);
            final BufferedReader r = new BufferedReader(new InputStreamReader(resources.openRawResource(R.raw.streets), Charset.forName("UTF-8")));
            boolean stop = false;
            while (!stop) {
                String line = r.readLine();
                if (line == null) stop = true;
                else {
                    String[] s = line.split(";");
                    res.add(new Spot(s[0], Integer.parseInt(s[1])));
                }
            }
            r.close();
            streets = res;
        }
        return streets;
    }

    @NonNull
    private final Resources resources;

    private final int maxNumber;

    public Request(@NonNull Resources resources, int maxNumber) {
        this.resources = resources;
        this.maxNumber = maxNumber;
    }

    @Override
    protected Integer doInBackground(String... params) {
        if (params != null) for (String param : params) if (param != null) query(param);
        return 0;
    }

    private void query(String locationName) {
        try {
            final List<Spot> spots = getSpots(locationName);
            if (!spots.isEmpty()) publishProgress(spots.toArray(new Spot[spots.size()]));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private List<Spot> getSpots(@NonNull String extract) throws IOException {
        final List<Spot> res = new ArrayList<>(maxNumber);
        final List<Spot> streets = getStreets(resources);

        final int maxRslSize = maxNumber - res.size();
        List<ResultSpotLevenshtein> rsl = new ArrayList<>(maxRslSize);
        for (int i = 0; i < streets.size() && ((rsl.size() < maxNumber) || (rsl.get(rsl.size() - 1).levenshtein > 0)) && !isCancelled(); i++) {
            final Spot spot = streets.get(i);
            if (!res.contains(spot)) {
                final int lenvenshtein = levenshteinIn(extract, spot.street);
                if ((rsl.size() < maxRslSize) || rsl.get(rsl.size() - 1).levenshtein > lenvenshtein) {
                    rsl.add(new ResultSpotLevenshtein(spot, lenvenshtein));
                    Collections.sort(rsl);
                    while (rsl.size() > maxRslSize) rsl.remove(maxRslSize);
                }
            }
        }
        System.out.println("" + this + ".getSpots: cancel = " + isCancelled());
        for (final ResultSpotLevenshtein r : rsl) res.add(r.spot);
        return res;
    }

    static class ResultSpotLevenshtein implements Comparable<ResultSpotLevenshtein> {
        final Spot spot;
        final int levenshtein;

        ResultSpotLevenshtein(Spot spot, int levenshtein) {
            this.spot = spot;
            this.levenshtein = levenshtein;
        }

        @Override
        public int compareTo(@NonNull ResultSpotLevenshtein o) {
            return (levenshtein < o.levenshtein) ? -1 : (levenshtein == o.levenshtein) ? 0 : 1;
        }
    }

    private static int levenshteinIn(@NonNull CharSequence extract, @NonNull CharSequence text) {
        int res = Integer.MAX_VALUE;
        for (int i = 0; i <= text.length() - extract.length(); i++) {
            boolean bonus = (i == 0) || (text.charAt(i - 1) == ' ');
            final int distance = levenshteinSub(extract, text, i, i + extract.length());
            res = Math.min(res, distance * 2 + (bonus ? 0 : 1));
        }
        return res;
    }

    private static int levenshteinSub(@NonNull CharSequence lhs, @NonNull CharSequence rhs, int rhsStart, int rhsEnd) {
        int lhsLength = lhs.length();
        int rhsLength = rhsEnd - rhsStart;

        int[] cost = new int[lhsLength + 1];
        for (int i = 0; i < cost.length; i++) cost[i] = i;
        int[] newCost = new int[lhsLength + 1];

        for (int i = 1; i <= rhsLength; i++) {
            newCost[0] = i;

            for (int j = 1; j <= lhsLength; j++) {
                int match = (same(lhs.charAt(j - 1), rhs.charAt(rhsStart + i - 1))) ? 0 : 1;

                int costReplace = cost[j - 1] + match;
                int costInsert = cost[j] + 1;
                int costDelete = newCost[j - 1] + 1;

                newCost[j] = Math.min(Math.min(costInsert, costDelete), costReplace);
            }

            int[] swap = cost;
            cost = newCost;
            newCost = swap;
        }

        return cost[lhsLength];
    }

    private static char toStdLowerChar(char a) {
        return Character.toLowerCase(Chars.toStdChar(a));
    }

    private static boolean same(char a, char b) {
        return toStdLowerChar(a) == toStdLowerChar(b);
    }

}
