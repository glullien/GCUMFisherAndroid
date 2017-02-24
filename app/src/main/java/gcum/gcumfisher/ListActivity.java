package gcum.gcumfisher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.ViewGroup;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.List;

import gcum.gcumfisher.connection.GetLogin;
import gcum.gcumfisher.connection.Point;
import gcum.gcumfisher.connection.ServerPhoto;
import gcum.gcumfisher.util.AsyncTaskE;

public class ListActivity extends Activity {
    static final String TYPE = "TYPE";
    static final int FOR_ONE_POINT = 1;
    static final String LATITUDE = "LATITUDE";
    static final String LONGITUDE = "LONGITUDE";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list);

        Intent intent = getIntent();
        if (intent != null) switch (intent.getIntExtra(TYPE, -1)) {
            case FOR_ONE_POINT:
                Point point = new Point(intent.getLongExtra(LATITUDE, 0), intent.getLongExtra(LONGITUDE, 0));
                new GetPointInfo().execute(point);
        }
    }

    @NonNull
    private ImageView getImageView(@NonNull ServerPhoto photo) throws Exception {
        final int thumbnailSize = findViewById(R.id.imagesScroll).getWidth() - 10;
        final Bitmap bitmap = BitmapFactory.decodeStream(GetLogin.getPhotoInputStream(photo.getId(), thumbnailSize));
        final ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        return imageView;
    }

    private List<ServerPhoto> photos;
    private int loaded = 0;

    private void showPhotos(List<ServerPhoto> photos) {
        this.photos = photos;
        loaded = 0;
        final int thumbnailSize = findViewById(R.id.imagesScroll).getWidth() - 10;
        ((ViewGroup) findViewById(R.id.images)).removeAllViews();
        new GetPhotos(thumbnailSize).execute(photos.toArray(new ServerPhoto[photos.size()]));
        ((TextView) findViewById(R.id.info)).setText("Loaded "+loaded+"/"+photos.size());
    }

    @NonNull
    private RelativeLayout.LayoutParams getCenterLayoutParams() {
        final RelativeLayout.LayoutParams res = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        res.addRule(RelativeLayout.CENTER_HORIZONTAL);
        res.addRule(RelativeLayout.CENTER_VERTICAL);
        return res;
    }

    class GetPhotos extends AsyncTaskE<ServerPhoto, Bitmap, Boolean> {
        private final int thumbnailSize;

        GetPhotos(int thumbnailSize) {
            this.thumbnailSize = thumbnailSize;
        }

        @Override
        protected void onPostExecuteError(Exception error) {
            displayError("Internal error: " + error);
        }

        @Override
        protected void onProgressUpdate(Bitmap... bitmaps) {
            final ViewGroup images = (ViewGroup) findViewById(R.id.images);
            for (Bitmap bitmap : bitmaps) {
                final RelativeLayout view = new RelativeLayout(ListActivity.this);
                final ImageView imageView = new ImageView(ListActivity.this);
                imageView.setImageBitmap(bitmap);
                view.addView(imageView, getCenterLayoutParams());
                final GridLayout.LayoutParams lp = new GridLayout.LayoutParams(GridLayout.spec(loaded), GridLayout.spec(0));
                lp.setMargins(5, 0, 5, 0);
                images.addView(view, lp);
                ((TextView) findViewById(R.id.info)).setText("Loaded "+(++loaded)+"/"+photos.size());
            }
        }

        @Override
        protected Boolean doInBackgroundOrCrash(ServerPhoto[] photos) throws Exception {
            for (ServerPhoto photo : photos) {
                final Bitmap bitmap = BitmapFactory.decodeStream(GetLogin.getPhotoInputStream(photo.getId(), thumbnailSize));
                publishProgress(bitmap);
            }
            return true;
        }
    }

    class GetPointInfo extends AsyncTaskE<Point, Boolean, List<ServerPhoto>> {
        @Override
        protected void onPostExecuteSuccess(List<ServerPhoto> photos) {
            if (photos != null) showPhotos(photos);
        }

        @Override
        protected void onPostExecuteError(Exception error) {
            displayError("Internal error: " + error);
        }

        @Override
        protected List<ServerPhoto> doInBackgroundOrCrash(Point[] points) throws Exception {
            return GetLogin.getPointInfo(points[0]);
        }
    }

    public void displayError(@NonNull CharSequence message) {
        new AlertDialog.Builder(this).setTitle(R.string.error).setMessage(message).setIcon(android.R.drawable.ic_dialog_alert).show();
    }
}
