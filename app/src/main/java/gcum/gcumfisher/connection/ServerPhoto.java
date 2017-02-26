package gcum.gcumfisher.connection;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class ServerPhoto {

    public static class Address {
        private final String street;
        private final int district;
        private final String city;

        Address(String street, int district, String city) {
            this.street = street;
            this.district = district;
            this.city = city;
        }

        public String getStreet() {
            return street;
        }

        public int getDistrict() {
            return district;
        }
    }

    static class Coordinates {
        private final Point point;
        private final CoordinatesSource source;

        Coordinates(Point point, CoordinatesSource source) {
            this.point = point;
            this.source = source;
        }
    }

    static class Location {
        private final Address address;
        private final Coordinates coordinates;

        Location(Address address, Coordinates coordinates) {
            this.address = address;
            this.coordinates = coordinates;
        }
    }

    enum CoordinatesSource {Street, Device}

    private final String id;
    @NonNull
    private final String date;
    @Nullable
    private final String time;
    @NonNull
    private final Location location;
    @Nullable
    private final String username;
    private final int likesCount;
    private final boolean isLiked;

    ServerPhoto(String id, @NonNull String date, @Nullable String time, @NonNull Location location, @Nullable String username, int likesCount, boolean isLiked) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.location = location;
        this.username = username;
        this.likesCount = likesCount;
        this.isLiked = isLiked;
    }

    public String getId() {
        return id;
    }

    @NonNull
    public String getDate() {
        return date;
    }

    @Nullable
    public String getTime() {
        return time;
    }

    @NonNull
    public Location getLocation() {
        return location;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    public int getLikesCount() {
        return likesCount;
    }

    public boolean isLiked() {
        return isLiked;
    }
}

