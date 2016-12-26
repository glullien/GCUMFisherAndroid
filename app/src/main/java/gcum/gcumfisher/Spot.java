package gcum.gcumfisher;

import android.content.res.Resources;
import android.location.Address;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

public class Spot {
    @NonNull
    public final String street;

    final int district;

    public Spot(@NonNull String street, int district) {
        this.street = street;
        this.district = district;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Spot address = (Spot) o;
        return district == address.district && street.equals(address.street);
    }

    @Override
    public int hashCode() {
        int result = street.hashCode();
        result = 31 * result + district;
        return result;
    }

    static class FilteredResults {
        @NonNull
        final List<Spot> spots;
        @Nullable
        final String outOfParis;

        FilteredResults(@NonNull List<Spot> spots, @Nullable String outOfParis) {
            this.spots = spots;
            this.outOfParis = outOfParis;
        }

        boolean isEmpty() {
            return spots.isEmpty();
        }
    }

    @NonNull
    static FilteredResults filterParisSpots(@NonNull List<Address> addresses) {
        final List<Spot> spots = new ArrayList<>(addresses.size());
        String outOfParis = null;
        for (Address address : addresses) {
            String postalCode = address.getPostalCode();
            String street = address.getThoroughfare();
            if ((postalCode != null) && (street != null) && (postalCode.matches("\\d+"))) {
                int district = Integer.parseInt(postalCode) - 75000;
                if ((district < 1) || (district > 20)) outOfParis = street+" ("+postalCode+")";
                else spots.add(new Spot(street, district));
            }
        }
        return new FilteredResults(spots, spots.isEmpty()?outOfParis:null);
    }

    @NonNull
    String toString(@NonNull Resources resources) {
        return resources.getString(R.string.address_format, street, district);
    }
}
