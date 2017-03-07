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
import android.view.View;
import android.view.ViewGroup;
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
    static final String TYPE = "TYPE";
    static final int FOR_ONE_POINT = 1;
    static final int ALL = 2;
    static final String LATITUDE = "LATITUDE";
    static final String LONGITUDE = "LONGITUDE";
    public static final int BATCH_SIZE = 20;
    private float imageLoadRatio = 2;
    private Server server;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list);

        Intent intent = getIntent();
        if (intent != null) switch (intent.getIntExtra(TYPE, -1)) {
            case ALL:
                new GetList().execute();
                break;
            case FOR_ONE_POINT:
                Point point = new Point(intent.getLongExtra(LATITUDE, 0), intent.getLongExtra(LONGITUDE, 0));
                new GetPointInfo().execute(point);
                break;
        }
        server = new Server(getResources());
    }

    private final Queue<ServerPhoto> photoToLoad = new ConcurrentLinkedQueue<>();
    private GetPhotos getPhotosTask;

    private void showPhotos(List<ServerPhoto> photos) {
        if (getPhotosTask != null) getPhotosTask.cancel(false);
        ((ViewGroup) findViewById(R.id.images)).removeAllViews();
        photoToLoad.clear();
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
        protected void onPostExecuteError(Exception error) {
            error.printStackTrace();
            displayError(getResources().getString(R.string.error_message, error.getMessage()));
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

        private View getLikeView(final String photoId, int count) {
            final LinearLayout res = new LinearLayout(ListActivity.this);
            final ImageView heart = new ImageView(ListActivity.this);
            heart.setImageResource(R.drawable.heart);
            final TextView countView = getOverPrintStyle(Integer.toString(count));
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
                view.addView(topLines, getTopLayoutParams(10));

                final LinearLayout authorLine = new LinearLayout(ListActivity.this);
                authorLine.setBackgroundResource(R.color.overPrintPanel);
                authorLine.setOrientation(LinearLayout.HORIZONTAL);
                authorLine.setDividerPadding(15);
                authorLine.addView(getLikeView(serverPhoto.getId(), serverPhoto.getLikesCount()));
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

    private class GetList extends AsyncTaskE<Boolean, Boolean, List<ServerPhoto>> {
        @Override
        protected void onPostExecuteSuccess(List<ServerPhoto> photos) {
            if (photos != null) showPhotos(photos);
        }

        @Override
        protected void onPostExecuteError(Exception error) {
            displayError(getResources().getString(R.string.error_message, error.getMessage()));
        }

        @Override
        protected List<ServerPhoto> doInBackgroundOrCrash(Boolean[] params) throws Exception {
            return server.getList(BATCH_SIZE, null);
        }
    }

    private class GetPointInfo extends AsyncTaskE<Point, Boolean, List<ServerPhoto>> {
        @Override
        protected void onPostExecuteSuccess(List<ServerPhoto> photos) {
            if (photos != null) showPhotos(photos);
        }

        @Override
        protected void onPostExecuteError(Exception error) {
            displayError(getResources().getString(R.string.error_message, error.getMessage()));
        }

        @Override
        protected List<ServerPhoto> doInBackgroundOrCrash(Point[] points) throws Exception {
            return server.getPointInfo(points[0]);
        }
    }

    public void displayError(@NonNull CharSequence message) {
        new AlertDialog.Builder(this).setTitle(R.string.error).setMessage(message).setIcon(android.R.drawable.ic_dialog_alert).show();
    }
}
