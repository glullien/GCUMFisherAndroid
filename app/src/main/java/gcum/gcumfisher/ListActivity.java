package gcum.gcumfisher;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
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
    private final static float imageSizeRatio = 0.7f;
    private Server server;
    private Point here;
    private Server.Sort sort = Server.Sort.date;
    private String latest;
    private String author = "<all>";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.list);
        server = new Server(getResources());

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
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.list, menu);
        menu.findItem(R.id.closest).setVisible(here != null);
        return true;
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
            case R.id.filter:
                openFilterDialog();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void setFilter(String author) {
        this.author = author;
        refreshList();
    }

    public void openFilterDialog() {
        try {
            final Dialog dialog = new Dialog(this);
            dialog.setContentView(R.layout.list_filter);
            dialog.setTitle(R.string.filter);
            dialog.findViewById(R.id.ok).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    int selectedId = ((RadioGroup) dialog.findViewById(R.id.authors)).getCheckedRadioButtonId();
                    String username = ((EditText) dialog.findViewById(R.id.authorUsername)).getText().toString();
                    switch (selectedId) {
                        case R.id.authorAll:
                            setFilter("<all>");
                            dialog.dismiss();
                            break;
                        case R.id.authorMyself:
                            setFilter("<myself>");
                            dialog.dismiss();
                            break;
                        case R.id.authorOther:
                            if (username.length() > 0) {
                                setFilter(username);
                                dialog.dismiss();
                            }
                            break;
                    }
                }
            });
            ((EditText) dialog.findViewById(R.id.authorUsername)).addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    ((RadioButton) dialog.findViewById(R.id.authorOther)).setChecked(true);
                }

                @Override
                public void afterTextChanged(Editable s) {
                }
            });
            dialog.findViewById(R.id.cancel).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    dialog.dismiss();
                }
            });
            switch (author) {
                case "<all>":
                    ((RadioButton) dialog.findViewById(R.id.authorAll)).setChecked(true);
                    break;
                case "<myself>":
                    ((RadioButton) dialog.findViewById(R.id.authorMyself)).setChecked(true);
                    break;
                default:
                    ((RadioButton) dialog.findViewById(R.id.authorOther)).setChecked(true);
                    ((EditText) dialog.findViewById(R.id.authorUsername)).setText(author);
                    break;
            }
            dialog.findViewById(R.id.authorMyself).setVisibility((LoginActivity.getAutoLogin(getApplicationContext()) != null) ? View.VISIBLE : View.GONE);
            dialog.show();
        } catch (Exception e) {
            reportInternalError("Opening filter dialog", e);
        }
    }

    void reportInternalError(@NonNull String message, @NonNull Exception e) {
        Log.e("ListActivity", message, e);
        server.startLog(null, message, e);
        displayError(getString(R.string.error_message, e.getMessage()));
    }

    public void more(View view) {
        if (latest != null) {
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

    class PhotoBitmap {
        private final Bitmap bitmap;
        private final ServerPhoto serverPhoto;

        PhotoBitmap(Bitmap bitmap, ServerPhoto serverPhoto) {
            this.bitmap = bitmap;
            this.serverPhoto = serverPhoto;
        }
    }

    private void setDrawableRight(@NonNull TextView view, @DrawableRes int drawable) {
        final Drawable d = getResources().getDrawable(drawable);
        d.setBounds(0, 0, d.getIntrinsicWidth(), d.getIntrinsicHeight());
        view.setCompoundDrawables(null, null, d, null);
    }

    private class ToggleLike extends AsyncTaskE<Boolean, Boolean, ToggleLikeResult> {
        private final AutoLogin autoLogin;
        private final String photoId;
        private final TextView likesCount;

        ToggleLike(AutoLogin autoLogin, String photoId, TextView likesCount) {
            this.autoLogin = autoLogin;
            this.photoId = photoId;
            this.likesCount = likesCount;
        }

        @Override
        protected void onPostExecuteSuccess(ToggleLikeResult toggleLikeResult) {
            likesCount.setText(Integer.toString(toggleLikeResult.getLikesCount()));
            likesCount.setTextColor(getColor(toggleLikeResult.isLiked() ? R.color.liked : R.color.black));
            setDrawableRight(likesCount, toggleLikeResult.isLiked() ? R.drawable.liked_heart : R.drawable.heart);
        }

        @Override
        protected void onPreExecute() {
            likesCount.setText("");
            setDrawableRight(likesCount, android.R.drawable.ic_menu_rotate);
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
            this.thumbnailSize = (int) (findViewById(R.id.imagesScroll).getWidth() * imageSizeRatio);
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

        @Override
        protected void onProgressUpdate(PhotoBitmap... photos) {
            for (final PhotoBitmap photo : photos) {
                addPhotoView(photo);
                loaded++;
                ((ProgressBar) findViewById(R.id.progress)).setProgress(loaded * 100 / (loaded + photoToLoad.size()));
            }
        }

        private void addPhotoView(@NonNull final PhotoBitmap photo) {
            final ServerPhoto serverPhoto = photo.serverPhoto;

            LayoutInflater inflater = getLayoutInflater();
            ViewGroup res = (ViewGroup) inflater.inflate(R.layout.list_photo, null);

            ((TextView) res.findViewById(R.id.date)).setText(serverPhoto.getDate());
            if (serverPhoto.getTime() != null)
                ((TextView) res.findViewById(R.id.time)).setText(serverPhoto.getTime());

            ((TextView) res.findViewById(R.id.author)).setText(serverPhoto.getUsername());

            ((TextView) res.findViewById(R.id.address)).setText(serverPhoto.getAddress(getResources()));

            final ServerPhoto.Coordinates coordinates = serverPhoto.getLocation().getCoordinates();
            final ServerPhoto.CoordinatesSource source = coordinates.getSource();
            final String distance;
            if (here == null) distance = null;
            else switch (source) {
                case Device:
                    distance = coordinates.getPoint().distanceToString(getResources(), here);
                    break;
                case Street:
                    distance = null;
                    break;
                default:
                    distance = null;
                    break;
            }
            final String point = coordinates.getPoint().toString(getResources());

            final TextView coordinatesView = (TextView) res.findViewById(R.id.coordinates);
            coordinatesView.setText((distance != null) ? distance : point);
            if (distance != null) coordinatesView.setOnClickListener(new View.OnClickListener() {
                boolean displayPoint = false;

                @Override
                public void onClick(View v) {
                    displayPoint = !displayPoint;
                    coordinatesView.setText(displayPoint ? point : distance);
                }
            });

            final TextView heartCounterView = (TextView) res.findViewById(R.id.heartCounter);
            heartCounterView.setText(Integer.toString(serverPhoto.getLikesCount()));

            @ColorInt
            final int color = getResources().getColor(serverPhoto.isLiked() ? R.color.liked : R.color.black);
            heartCounterView.setTextColor(color);
            setDrawableRight(heartCounterView, serverPhoto.isLiked() ? R.drawable.liked_heart : R.drawable.heart);

            res.findViewById(R.id.heartCounter).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final AutoLogin autoLogin = LoginActivity.getAutoLogin(getApplicationContext());
                    if (autoLogin == null)
                        displayError(getResources().getString(R.string.should_be_login));
                    else
                        new ToggleLike(autoLogin, serverPhoto.getId(), heartCounterView).execute();
                }
            });

            final ImageView imageView = (ImageView) res.findViewById(R.id.photo_view);
            imageView.setImageBitmap(photo.bitmap);
            imageView.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    final Intent intent = new Intent(ListActivity.this, PhotoActivity.class);
                    intent.putExtra(PhotoActivity.PHOTO_ID, photo.serverPhoto.getId());
                    startActivity(intent);
                }
            });

            final ViewGroup images = (ViewGroup) findViewById(R.id.images);
            images.addView(res);
        }

        @Override
        protected Boolean doInBackgroundOrCrash(Boolean[] photos) throws Exception {
            while ((!photoToLoad.isEmpty()) && (!isCancelled())) {
                ServerPhoto photo = photoToLoad.peek();
                final byte[] bytes = ImageLoader.load(server.getPhoto(photo.getId(), thumbnailSize));
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
                latest = list.isEmpty() ? null : list.get(list.size() - 1).getId();
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
            return server.getList(autoLogin, BATCH_SIZE, sort, here, author, latest);
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
