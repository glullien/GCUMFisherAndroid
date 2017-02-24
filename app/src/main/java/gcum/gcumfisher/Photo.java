package gcum.gcumfisher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.media.ExifInterface;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

import gcum.gcumfisher.connection.Point;

public class Photo {
    @NonNull
    public final String path;

    /**
     * Epoch time stamp
     */
    public final long date;

    @Nullable
    public final Point point;

    Photo(@NonNull String path, long date, @Nullable Point point) {
        this.path = path;
        this.date = date;
        this.point = point;
    }

    Photo(@NonNull String savedString) {
        int slash = savedString.indexOf('/');
        this.path = savedString.substring(slash + 1);
        String [] pars = savedString.substring(0, slash).split("&");
        this.date = Long.parseLong(pars[0]);
        if (pars.length == 1) point = null;
        else point = new Point(Long.parseLong(pars[1]), Long.parseLong(pars[2]));
    }


    @NonNull
    static ArrayList<String> toArrayList(@NonNull List<Photo> source) {
        final ArrayList<String> res = new ArrayList<>(source.size());
        for (final Photo photo : source) res.add(photo.toString());
        return res;
    }

    @NonNull
    static List<Photo> fromArrayList(@NonNull List<String> source) {
        final ArrayList<Photo> res = new ArrayList<>(source.size());
        for (final String s : source) res.add(new Photo(s));
        return res;
    }

    /**
     * Round down the date to midnight
     */
    private static long getAtMidnight(long source) {
        final Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("Europe/Paris"));
        cal.setTimeInMillis(source);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    /**
     * Returns age in days
     */
    int getAge() {
        return (int) ((getAtMidnight(System.currentTimeMillis()) - getAtMidnight(date)) / 86400000L);
    }

    private static int orientationToAngle[] = {0, 0, 0, 180, 0, 0, 90, 0, 270};

    /**
     * Get a resized (keep ratio) and correctly oriented bitmap
     * @param maxSize maximum width and height
     */
    Bitmap getBitmap(final int maxSize) {
        final Bitmap fullSize = getBitmap();
        final Bitmap resized;
        final int width = fullSize.getWidth();
        final int height = fullSize.getHeight();
        if ((width <= maxSize) && (height <= maxSize)) resized = fullSize;
        else if (width <= height)
            resized = Bitmap.createScaledBitmap(fullSize, width * maxSize / height, maxSize, true);
        else resized = Bitmap.createScaledBitmap(fullSize, maxSize, height * maxSize / width, true);
        return resized;
    }

    /**
     * Get a correctly oriented bitmap
     */
    private Bitmap getBitmap() {
        final Bitmap brut = BitmapFactory.decodeFile(path);
        final Bitmap res;
        final int orientation = getOrientation();
        if ((orientation <= 0) || (orientationToAngle.length <= orientation)) res = brut;
        else {
            Matrix matrix = new Matrix();
            matrix.postRotate(orientationToAngle[orientation]);
            res = Bitmap.createBitmap(brut, 0, 0, brut.getWidth(), brut.getHeight(), matrix, true);
        }
        return res;
    }

    private int getOrientation() {
        try {
            final ExifInterface exif = new ExifInterface(path);
            return exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);
        } catch (IOException e) {
            return 0;
        }
    }

    @Override
    @NonNull
    public String toString() {
        final StringBuilder res = new StringBuilder();
        res.append(Long.toString(date));
        if (point != null) res.append("&").append(point.getLatitude()).append("&").append(point.getLongitude());
        res.append("/").append(path);
        return res.toString();
    }
}