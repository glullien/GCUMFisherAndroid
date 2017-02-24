package gcum.gcumfisher.connection;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class ServerPhoto {

    enum CoordinatesSource {Street, Device}

    private final String id;
    @NonNull
    private final String date;
    @Nullable
    private final String time;
    @NonNull
    private final CoordinatesSource locationSource;
    @NonNull
    private final Point location;
    @Nullable
    private final String username;
    private final int likesCount;
    private final boolean isLiked;

    ServerPhoto(String id, @NonNull String date, @Nullable String time, @NonNull CoordinatesSource locationSource, @NonNull Point location, @Nullable String username, int likesCount, boolean isLiked) {
        this.id = id;
        this.date = date;
        this.time = time;
        this.locationSource = locationSource;
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
    public CoordinatesSource getLocationSource() {
        return locationSource;
    }

    @NonNull
    public Point getLocation() {
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

