package gcum.gcumfisher.connection;

public class ToggleLikeResult {
    private final int likesCount;
    private final boolean isLiked;

    public ToggleLikeResult(int likesCount, boolean isLiked) {
        this.likesCount = likesCount;
        this.isLiked = isLiked;
    }

    public int getLikesCount() {
        return likesCount;
    }

    public boolean isLiked() {
        return isLiked;
    }
}
