package gcum.gcumfisher.connection;

import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import gcum.gcumfisher.R;

public class ServerPhoto {

    public static class Address {
        @NonNull
        private final String street;
        private final int district;
        @NonNull
        private final String city;

        Address(@NonNull String street, int district, @NonNull String city) {
            this.street = street;
            this.district = district;
            this.city = city;
        }

        @NonNull
        public String getStreet() {
            return street;
        }

        public int getDistrict() {
            return district;
        }

        @Override
        public String toString() {
            return "Address{street=" + street + ", district=" + district + ", city=" + city + "}";
        }

        public String getAddress(Resources resources) {
            return resources.getString(R.string.address_format, street, district);
        }
    }

    public static class Coordinates {
        @NonNull
        private final Point point;
        @NonNull
        private final CoordinatesSource source;

        Coordinates(@NonNull Point point, @NonNull CoordinatesSource source) {
            this.point = point;
            this.source = source;
        }

        @NonNull
        public Point getPoint() {
            return point;
        }

        @NonNull
        public CoordinatesSource getSource() {
            return source;
        }
    }

    public static class Location {
        @NonNull
        private final Address address;
        @NonNull
        private final Coordinates coordinates;

        Location(@NonNull Address address, @NonNull Coordinates coordinates) {
            this.address = address;
            this.coordinates = coordinates;
        }

        @NonNull
        public Address getAddress() {
            return address;
        }

        @NonNull
        public Coordinates getCoordinates() {
            return coordinates;
        }
    }

    public enum CoordinatesSource {Street, Device}

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

    @NonNull
    public String getDate() {
        return date;
    }

    @Nullable
    public String getTime() {
        return time;
    }

    public String getAddress(Resources resources) {
        return location.address.getAddress(resources);
    }

    @Override
    public String toString() {
        return "ServerPhoto{id=" + id + ", date=" + date + ", time=" + time + ", location=" + location + ", username=" + username + ", likesCount=" + likesCount + ", isLiked=" + isLiked + "}";
    }
}

