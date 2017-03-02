package gcum.gcumfisher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;

import java.io.ByteArrayInputStream;

import gcum.gcumfisher.connection.ImageLoader;
import gcum.gcumfisher.connection.PhotoDetails;
import gcum.gcumfisher.connection.Server;
import gcum.gcumfisher.util.AsyncTaskE;

public class PhotoActivity extends Activity implements View.OnTouchListener {
    public final static String PHOTO_ID = "gcum.gcumfisher.PhotoActivity.PHOTO_ID";
    private Server server;
    private String photoId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.photo);

        server = new Server(getResources());

        final Intent intent = getIntent();
        if (intent != null) {
            photoId = intent.getStringExtra(PHOTO_ID);
            if (photoId != null) new GetPhoto().execute(photoId);
        }

        ImageView imageView = (ImageView) findViewById(R.id.photo);
        imageView.setOnTouchListener(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.photo, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.details:
                if (photoId != null) {
                    final ProgressBar progress = (ProgressBar) findViewById(R.id.progress);
                    progress.setVisibility(View.VISIBLE);
                    progress.setIndeterminate(true);
                    new ShowDetail().execute(photoId);
                }
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private final Matrix matrix = new Matrix();
    private final Matrix savedMatrix = new Matrix();

    private enum Mode {NONE, DRAG, ZOOM}

    private Mode mode = Mode.NONE;

    private final PointF start = new PointF();
    private final PointF mid = new PointF();
    private float oldDist = 1f;

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        ImageView view = (ImageView) v;
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                savedMatrix.set(matrix);
                start.set(event.getX(), event.getY());
                mode = Mode.DRAG;
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                oldDist = spacing(event);
                if (oldDist > 10f) {
                    savedMatrix.set(matrix);
                    midPoint(mid, event);
                    mode = Mode.ZOOM;
                }
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                mode = Mode.NONE;
                break;
            case MotionEvent.ACTION_MOVE:
                switch (mode) {
                    case DRAG:
                        matrix.set(savedMatrix);
                        matrix.postTranslate(event.getX() - start.x, event.getY() - start.y);
                        break;
                    case ZOOM:
                        float newDist = spacing(event);
                        if (newDist > 10f) {
                            matrix.set(savedMatrix);
                            float scale = newDist / oldDist;
                            matrix.postScale(scale, scale, mid.x, mid.y);
                        }
                        break;
                }
                break;
        }

        view.setImageMatrix(matrix);
        return true;
    }

    private float spacing(MotionEvent event) {
        float x = event.getX(0) - event.getX(1);
        float y = event.getY(0) - event.getY(1);
        return (float) Math.sqrt(x * x + y * y);
    }

    private void midPoint(PointF point, MotionEvent event) {
        float x = event.getX(0) + event.getX(1);
        float y = event.getY(0) + event.getY(1);
        point.set(x / 2, y / 2);
    }

    class ShowDetail extends AsyncTaskE<String, Boolean, PhotoDetails> {
        @Override
        protected void onPostExecuteError(Exception error) {
            error.printStackTrace();
            findViewById(R.id.progress).setVisibility(View.GONE);
        }

        @Override
        protected void onPostExecuteSuccess(PhotoDetails details) {
            findViewById(R.id.progress).setVisibility(View.GONE);
            final StringBuilder message = new StringBuilder();
            if (details.getUsername() != null)
                message.append("Prise par ").append(details.getUsername()).append("\n");
            message.append("Le ").append(details.getDate());
            if (details.getTime() != null) message.append("Le").append(details.getTime());
            message.append("\n");
            message.append(details.getLocation().getAddress().getAddress(getResources())).append("\n");
            message.append("Taille ").append(details.getWidth()).append(" x ").append(details.getHeight()).append("\n");
            message.append("Aimé par ").append(details.getLikes().size()).append(" personnes\n");
            if (details.isLiked()) message.append("Aimé par moi\n");
            new AlertDialog.Builder(PhotoActivity.this).setTitle("Info").setMessage(message).setIcon(android.R.drawable.ic_dialog_info).show();

        }

        @Override
        protected PhotoDetails doInBackgroundOrCrash(String[] params) throws Exception {
            return server.getPhotoInfo(params[0]);
        }
    }

    class GetPhoto extends AsyncTaskE<String, Integer, Bitmap> {
        @Override
        protected void onProgressUpdate(Integer... values) {
            ((ProgressBar) findViewById(R.id.progress)).setProgress(values[0]);
        }

        @Override
        protected void onPostExecuteError(Exception error) {
            error.printStackTrace();
            //displayError("Internal error: " + error);
            findViewById(R.id.progress).setVisibility(View.GONE);
        }

        @Override
        protected void onPostExecuteSuccess(Bitmap bitmap) {
            final ImageView view = (ImageView) findViewById(R.id.photo);
            final RectF src = new RectF(0, 0, bitmap.getWidth(), bitmap.getHeight());
            final RectF dst = new RectF(0, 0, view.getWidth(), view.getHeight());
            matrix.setRectToRect(src, dst, Matrix.ScaleToFit.CENTER);
            view.setImageMatrix(matrix);
            view.setImageBitmap(bitmap);
            findViewById(R.id.progress).setVisibility(View.GONE);
        }

        @Override
        protected Bitmap doInBackgroundOrCrash(String[] id) throws Exception {
            final byte[] bytes = ImageLoader.load(server.getPhoto(id[0]), new ImageLoader.Progress() {
                @Override
                public void on(int value) {
                    publishProgress(value);
                }
            });
            return BitmapFactory.decodeStream(new ByteArrayInputStream(bytes));
        }
    }

}
