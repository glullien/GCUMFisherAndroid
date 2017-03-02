package gcum.gcumfisher.connection;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.List;

public class PhotoDetails {

    @NonNull
    private final String date;
    @Nullable
    private final String time;
    @NonNull
    private final ServerPhoto.Location location;
    private final int width;
    private final int height;
    @Nullable
    private final String username;
    @NonNull
    private final List<String> likes;
    private final boolean isLiked;

    PhotoDetails(@NonNull String date, @Nullable String time, @NonNull ServerPhoto.Location location, int width, int height, @Nullable String username, @NonNull List<String> likes, boolean isLiked) {
        this.date = date;
        this.time = time;
        this.location = location;
        this.width = width;
        this.height = height;
        this.username = username;
        this.likes = likes;
        this.isLiked = isLiked;
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
    public ServerPhoto.Location getLocation() {
        return location;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    @Nullable
    public String getUsername() {
        return username;
    }

    @NonNull
    public List<String> getLikes() {
        return likes;
    }

    public boolean isLiked() {
        return isLiked;
    }
}
