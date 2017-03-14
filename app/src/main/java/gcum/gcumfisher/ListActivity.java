package gcum.gcumfisher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.Px;
import android.support.annotation.StyleRes;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import gcum.gcumfisher.connection.AutoLogin;
import gcum.gcumfisher.connection.ImageLoader;
import gcum.gcumfisher.connection.Point;
import gcum.gcumfisher.connection.Server;
import gcum.gcumfisher.connection.ServerPhoto;
import gcum.gcumfisher.connection.ToggleLikeResult;
import gcum.gcumfisher.util.AsyncTaskE;

public class ListActivity extends Activity {
    static final String TYPE = "gcum.gcumfisher.ListActivity.TYPE";
    static final int FOR_ONE_POINT = 1;
    static final int ALL = 2;
    public static final String LATITUDE = "gcum.gcumfisher.ListActivity.LATITUDE";
    public static final String LONGITUDE = "gcum.gcumfisher.ListActivity.LONGITUDE";
    public static final String HERE_LATITUDE = "gcum.gcumfisher.ListActivity.HERE_LATITUDE";
    public static final String HERE_LONGITUDE = "gcum.gcumfisher.ListActivity.HERE_LONGITUDE";
    public static final int BATCH_SIZE = 10;
    private float imageLoadRatio = 2;
    private Server server;
    private Point here;
    private Server.Sort sort = Server.Sort.date;
    private String latest;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list);

        ((ViewGroup) findViewById(R.id.images)).removeAllViews();
        photoToLoad.clear();
        latest = null;
        Intent intent = getIntent();
        if (intent == null) here = null;
        else {
            findViewById(R.id.more).setEnabled(false);
            switch (intent.getIntExtra(TYPE, -1)) {
                case ALL:
                    new GetList().execute();
                    break;
                case FOR_ONE_POINT:
                    Point point = new Point(intent.getLongExtra(LATITUDE, 0), intent.getLongExtra(LONGITUDE, 0));
                    new GetPointInfo().execute(point);
                    break;
            }

            final double latitude = intent.getDoubleExtra(HERE_LATITUDE, Double.NaN);
            final double longitude = intent.getDoubleExtra(HERE_LONGITUDE, Double.NaN);
            here = (Double.isNaN(latitude) || Double.isNaN(longitude)) ? null : new Point(latitude, longitude);
        }
        server = new Server(getResources());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (here == null) return false;
        else {
            getMenuInflater().inflate(R.menu.list, menu);
            return true;
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.closest:
                sort = Server.Sort.closest;
                refreshList();
                return true;
            case R.id.by_date:
                sort = Server.Sort.date;
                refreshList();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void more(View view) {
        if (latest != null){
            findViewById(R.id.more).setEnabled(false);
            new GetList().execute();
        }
    }

    private void refreshList() {
        if (getPhotosTask != null) getPhotosTask.cancel(false);
        ((ViewGroup) findViewById(R.id.images)).removeAllViews();
        photoToLoad.clear();
        findViewById(R.id.more).setEnabled(false);
        latest = null;
        photoToLoad.clear();
        new GetList().execute();
    }

    private final Queue<ServerPhoto> photoToLoad = new ConcurrentLinkedQueue<>();
    private GetPhotos getPhotosTask;

    private void showPhotos(List<ServerPhoto> photos) {
        if (getPhotosTask != null) getPhotosTask.cancel(false);
        photoToLoad.addAll(photos);
        getPhotosTask = new GetPhotos();
        getPhotosTask.execute();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (getPhotosTask != null) getPhotosTask.cancel(false);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!photoToLoad.isEmpty()) {
            getPhotosTask = new GetPhotos();
            getPhotosTask.execute();
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (getPhotosTask != null) getPhotosTask.cancel(false);
    }

    @NonNull
    private RelativeLayout.LayoutParams getCenterLayoutParams() {
        final RelativeLayout.LayoutParams res = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        res.addRule(RelativeLayout.CENTER_HORIZONTAL);
        res.addRule(RelativeLayout.CENTER_VERTICAL);
        return res;
    }

    private RelativeLayout.LayoutParams getBottomLayoutParams(@Px int bottom) {
        final RelativeLayout.LayoutParams res = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        res.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        res.addRule(RelativeLayout.ALIGN_PARENT_START);
        res.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        res.setMargins(10, 10, 10, bottom);
        return res;
    }

    private RelativeLayout.LayoutParams getTopLayoutParams(@Px int top) {
        final RelativeLayout.LayoutParams res = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        res.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        res.addRule(RelativeLayout.ALIGN_PARENT_START);
        res.addRule(RelativeLayout.ALIGN_PARENT_TOP);
        res.setMargins(10, top, 10, 10);
        return res;
    }

    private class PhotoBitmap {
        private final Bitmap bitmap;
        private final ServerPhoto serverPhoto;

        PhotoBitmap(Bitmap bitmap, ServerPhoto serverPhoto) {
            this.bitmap = bitmap;
            this.serverPhoto = serverPhoto;
        }
    }

    private class ToggleLike extends AsyncTaskE<Boolean, Boolean, ToggleLikeResult> {
        private final AutoLogin autoLogin;
        private final String photoId;
        private final TextView likesCount;
        private final ImageView heart;

        ToggleLike(AutoLogin autoLogin, String photoId, TextView likesCount, ImageView heart) {
            this.autoLogin = autoLogin;
            this.photoId = photoId;
            this.likesCount = likesCount;
            this.heart = heart;
        }

        @Override
        protected void onPostExecuteSuccess(ToggleLikeResult toggleLikeResult) {
            likesCount.setText(Integer.toString(toggleLikeResult.getLikesCount()));
            @StyleRes int style = toggleLikeResult.isLiked() ? R.style.OverPrintLiked : R.style.OverPrint;
            likesCount.setTextAppearance(ListActivity.this, style);
            final int color = getResources().getColor(toggleLikeResult.isLiked() ? R.color.red : R.color.white);
            heart.setColorFilter(color, PorterDuff.Mode.MULTIPLY);

        }

        @Override
        protected void onPostExecuteError(Exception error) {
            server.startLog(autoLogin, "Switching like", error);
            error.printStackTrace();
            displayError(getResources().getString(R.string.error_message, error.getMessage()));
        }

        @Override
        protected ToggleLikeResult doInBackgroundOrCrash(Boolean[] params) throws Exception {
            return server.toggleLike(autoLogin, photoId);
        }
    }

    private class GetPhotos extends AsyncTaskE<Boolean, PhotoBitmap, Boolean> {
        private final int thumbnailSize;
        private int loaded;

        GetPhotos() {
            this.thumbnailSize = findViewById(R.id.imagesScroll).getWidth() - 10;
            loaded = 0;
        }

        @Override
        protected void onPostExecuteSuccess(Boolean b) {
            findViewById(R.id.more).setEnabled(true);
        }

        @Override
        protected void onPostExecuteError(Exception error) {
            server.startLog(null, "Downloading photos", error);
            error.printStackTrace();
            displayError(getResources().getString(R.string.error_message, error.getMessage()));
            findViewById(R.id.more).setEnabled(true);
        }

        @NonNull
        private TextView getStyled(String text, @StyleRes int style) {
            final TextView res = new TextView(ListActivity.this);
            res.setPadding(0, 0, 0, 0);
            res.setText(text);
            res.setTextAppearance(ListActivity.this, style);
            res.setGravity(Gravity.CENTER_HORIZONTAL);
            return res;
        }

        @NonNull
        private TextView getOverPrintStyle(String text) {
            return getStyled(text, R.style.OverPrint);
        }

        private View getLikeView(final String photoId, int count, boolean isLiked) {
            final LinearLayout res = new LinearLayout(ListActivity.this);
            final ImageView heart = new ImageView(ListActivity.this);
            heart.setImageResource(R.drawable.heart);
            final int color = getResources().getColor(isLiked ? R.color.red : R.color.white);
            heart.setColorFilter(color, PorterDuff.Mode.MULTIPLY);
            final TextView countView = getStyled(Integer.toString(count), isLiked ? R.style.OverPrintLiked : R.style.OverPrint);
            res.addView(countView);
            res.addView(heart);
            res.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final AutoLogin autoLogin = LoginActivity.getAutoLogin(getApplicationContext());
                    if (autoLogin == null)
                        displayError(getResources().getString(R.string.should_be_login));
                    else new ToggleLike(autoLogin, photoId, countView, heart).execute();
                }
            });
            return res;
        }

        @Override
        protected void onProgressUpdate(PhotoBitmap... photos) {
            final ViewGroup images = (ViewGroup) findViewById(R.id.images);
            for (final PhotoBitmap photo : photos) {
                final RelativeLayout view = new RelativeLayout(ListActivity.this);
                final ImageView imageView = new ImageView(ListActivity.this);
                final Bitmap viewBitmap;
                if (imageLoadRatio == 1) viewBitmap = photo.bitmap;
                else {
                    final int width = Math.round(photo.bitmap.getWidth() * imageLoadRatio);
                    final int height = Math.round(photo.bitmap.getHeight() * imageLoadRatio);
                    viewBitmap = Bitmap.createScaledBitmap(photo.bitmap, width, height, true);
                }
                imageView.setImageBitmap(viewBitmap);
                imageView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        final Intent intent = new Intent(ListActivity.this, PhotoActivity.class);
                        intent.putExtra(PhotoActivity.PHOTO_ID, photo.serverPhoto.getId());
                        startActivity(intent);
                    }
                });
                view.addView(imageView, getCenterLayoutParams());

                final ServerPhoto serverPhoto = photo.serverPhoto;

                final LinearLayout topLines = new LinearLayout(ListActivity.this);
                topLines.setOrientation(LinearLayout.VERTICAL);
                topLines.setBackgroundResource(R.color.overPrintPanel);
                topLines.addView(getOverPrintStyle(serverPhoto.getDateTime(getResources())));
                topLines.addView(getStyled(serverPhoto.getAddress(getResources()), R.style.OverPrintSmall));

                final ServerPhoto.Coordinates coordinates = serverPhoto.getLocation().getCoordinates();
                if ((coordinates.getSource() == ServerPhoto.CoordinatesSource.Device) && (here != null)) {
                    long distance = Math.round(here.distance(coordinates.getPoint()));
                    topLines.addView(getStyled(getResources().getString(R.string.distance, distance), R.style.OverPrintSmall));
                }

                view.addView(topLines, getTopLayoutParams(10));

                final LinearLayout authorLine = new LinearLayout(ListActivity.this);
                authorLine.setBackgroundResource(R.color.overPrintPanel);
                authorLine.setOrientation(LinearLayout.HORIZONTAL);
                authorLine.setDividerPadding(15);
                authorLine.addView(getLikeView(serverPhoto.getId(), serverPhoto.getLikesCount(), serverPhoto.isLiked()));
                if (serverPhoto.getUsername() != null)
                    authorLine.addView(getOverPrintStyle(serverPhoto.getUsername()));
                view.addView(authorLine, getBottomLayoutParams(20));

                final LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.setMargins(5, 5, 5, 5);
                images.addView(view, lp);

                loaded++;
                ((ProgressBar) findViewById(R.id.progress)).setProgress(loaded * 100 / (loaded + photoToLoad.size()));
            }
        }

        @Override
        protected Boolean doInBackgroundOrCrash(Boolean[] photos) throws Exception {
            while ((!photoToLoad.isEmpty()) && (!isCancelled())) {
                ServerPhoto photo = photoToLoad.peek();
                final byte[] bytes = ImageLoader.load(server.getPhoto(photo.getId(), Math.round(thumbnailSize / imageLoadRatio)));
                if (!isCancelled()) {
                    if (photo == photoToLoad.poll()) {
                        final Bitmap bitmap = BitmapFactory.decodeStream(new ByteArrayInputStream(bytes));
                        publishProgress(new PhotoBitmap(bitmap, photo));
                    }
                }
            }
            return true;
        }
    }

    private class GetList extends AsyncTaskE<Boolean, Boolean, Server.GetListResult> {
        @Override
        protected void onPostExecuteSuccess(Server.GetListResult photos) {
            if (photos != null) {
                final List<ServerPhoto> list = photos.getPhotos();
                showPhotos(list);
                latest = list.get(list.size() - 1).getId();
                ((Button) findViewById(R.id.more)).setText(getResources().getString(R.string.more_nb_after, photos.getNbAfter()));
            }
        }

        @Override
        protected void onPostExecuteError(Exception error) {
            server.startLog(null, "Downloading list", error);
            error.printStackTrace();
            displayError(getResources().getString(R.string.error_message, error.getMessage()));
        }

        @Override
        protected Server.GetListResult doInBackgroundOrCrash(Boolean[] params) throws Exception {
            final AutoLogin autoLogin = LoginActivity.getAutoLogin(getApplicationContext());
            return server.getList(autoLogin, BATCH_SIZE, sort, here, latest);
        }
    }

    private class GetPointInfo extends AsyncTaskE<Point, Boolean, List<ServerPhoto>> {
        @Override
        protected void onPostExecuteSuccess(List<ServerPhoto> photos) {
            if (photos != null) showPhotos(photos);
        }

        @Override
        protected void onPostExecuteError(Exception error) {
            server.startLog(null, "Downloading point info", error);
            error.printStackTrace();
            displayError(getResources().getString(R.string.error_message, error.getMessage()));
        }

        @Override
        protected List<ServerPhoto> doInBackgroundOrCrash(Point[] points) throws Exception {
            final AutoLogin autoLogin = LoginActivity.getAutoLogin(getApplicationContext());
            return server.getPointInfo(autoLogin, points[0]);
        }
    }

    public void displayError(@NonNull CharSequence message) {
        new AlertDialog.Builder(this).setTitle(R.string.error).setMessage(message).setIcon(android.R.drawable.ic_dialog_alert).show();
    }
}
