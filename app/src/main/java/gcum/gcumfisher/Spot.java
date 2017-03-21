package gcum.gcumfisher;

import android.content.res.Resources;
import android.support.annotation.NonNull;

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

    @Override
    public String toString() {
        return "Spot{street=" + street + ", district=" + district + "}";
    }

    @NonNull
    String toString(@NonNull Resources resources) {
        return resources.getString(R.string.address_format, street, district);
    }
}
