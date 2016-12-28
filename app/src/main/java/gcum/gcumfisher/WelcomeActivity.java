package gcum.gcumfisher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.content.FileProvider;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

/**
 * Main page
 */
public class WelcomeActivity extends Activity {

    private static final int TAKE_PHOTO_REQUEST = 1;
    private static final int PICK_PHOTO_REQUEST = 2;
    private static final int ADJUST_LOCATION_REQUEST = 3;
    private static final int LOGIN_REQUEST = 4;

    public static final String SAVED_PHOTOS = "gcum.gcumfisher.WelcomeActivity.PHOTOS";
    public static final String SAVED_NEXT_PHOTO = "gcum.gcumfisher.WelcomeActivity.NEXT_PHOTO";

    /**
     * List of photos waiting to be sent
     */
    private final List<Photo> photos = new LinkedList<>();

    /**
     * Current photo waiting to be inserted in photos
     * Design in pretty ugly but I'm afraid I need it to keep this information while Android is taking a photo
     */
    @Nullable
    private Photo nextPhoto;

    /**
     * The current spot found by the GPS
     */
    @Nullable
    private Spot gpsAddress;

    /**
     * The spot forced by the user with the SetLocationActity
     * If non null, it's override gpsAddress
     */
    @Nullable
    private Spot forcedAddress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome);
        if (savedInstanceState != null) {
            // Restore content after orientation switched
            final List<String> savedPhotos = savedInstanceState.getStringArrayList(SAVED_PHOTOS);
            if (savedPhotos != null) {
                photos.addAll(Photo.fromArrayList(savedPhotos));
                for (final Photo photo : photos) addImageView(photo);
            }
            final String savedNextPhoto = savedInstanceState.getString(SAVED_NEXT_PHOTO);
            if (savedNextPhoto != null) nextPhoto = new Photo(savedNextPhoto);
        }
        restoreSendReportReceiver();
        updatePhotosCount();
        updateSendButton();
        LocationSolver.startTracking(this, new LocationSolver.Listener() {
            @Override
            public void displayError(@NonNull CharSequence message) {
                // WelcomeActivity.this.displayError(message);
                if (getAddress() == null) setStreetTextView(message, R.color.error);
            }

            /**
             * Display the progression message if no spot in ready
             */
            @Override
            public void setLocationProgressMessage(@NonNull CharSequence text) {
                if (getAddress() == null) setStreetTextView(text, R.color.progress);
            }

            /**
             * Called when the GPS find a spot
             */
            @Override
            public void setLocationResults(@NonNull List<Spot> addresses) {
                gpsAddress = addresses.get(0);
                updateLocation();
            }
        }, 1);

        // Small cheat to view what cloud is being used
        findViewById(R.id.title).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                displayInfo();
                return true;
            }
        });
        updateLoginButton();
    }

    private void updateLoginButton() {
        ((Button) findViewById(R.id.login)).setText(getString(isConnected() ? R.string.logout : R.string.login));
    }

    private boolean isConnected() {
        return LoginActivity.getCredentials(getApplicationContext()) != null;
    }

    /**
     * Enabled send button if we've got a spot and at least one photo
     */
    private void updateSendButton() {
        findViewById(R.id.send).setEnabled((!photos.isEmpty()) && (!isSendingPhoto()) && (getAddress() != null) && isConnected());
    }

    /**
     * Change the counter on screen
     */
    private void updatePhotosCount() {
        NumberFormat nb = DecimalFormat.getIntegerInstance(Locale.FRANCE);
        ((TextView) findViewById(R.id.photosCount)).setText(nb.format(photos.size()));
    }

    /**
     * Called by take photo button
     */
    public void takePhoto(View view) {
        final Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) == null)
            displayError(R.string.cannot_take_picture);
        else try {
            File photoFile = createImageFile(System.currentTimeMillis());
            Uri photoURI = FileProvider.getUriForFile(this, "gcum.gcumfisher.fileprovider", photoFile);
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
            startActivityForResult(takePictureIntent, TAKE_PHOTO_REQUEST);
        } catch (IOException ex) {
            displayError(R.string.cannot_save_photo);
        }
    }

    /**
     * Called by pick a photo from gallery button
     */
    public void pickPhoto(View view) {
        final Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(galleryIntent, PICK_PHOTO_REQUEST);
    }

    /**
     * Create a new file to store a photo.
     * Side effect : fill the nextPhoto field that will be used by addNextPhoto()
     */
    private File createImageFile(long date) throws IOException {
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile("GCUM", ".JPEG", storageDir);
        nextPhoto = new Photo(image.getAbsolutePath(), date);
        return image;
    }

    /**
     * Add the photo in nextPhoto in the list of images on screen
     */
    private void addNextPhoto() {
        // nextPhoto should not be null, but it's safer to check
        if (nextPhoto != null) {
            photos.add(nextPhoto);
            addImageView(nextPhoto);
            updateSendButton();
            updatePhotosCount();
        }
    }

    /**
     * Copy the photo in path into a new file that will be used by addNextPhoto()
     */
    private void saveAsNextPhoto(@NonNull String path) {
        try {
            File source = new File(path);
            File f = createImageFile(source.lastModified());
            FileOutputStream out = new FileOutputStream(f);
            InputStream in = new FileInputStream(source);
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
            in.close();
            out.close();
        } catch (Exception e) {
            displayError(R.string.cannot_save_photo);
        }
    }

    /**
     * When an activity returns to the main one
     */
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case TAKE_PHOTO_REQUEST:
                if (resultCode == RESULT_OK) addNextPhoto();
                break;
            case PICK_PHOTO_REQUEST:
                if ((resultCode == RESULT_OK) && (data != null)) {
                    Uri selectedImage = data.getData();
                    String[] filePathColumn = {MediaStore.Images.Media.DATA};
                    Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
                    if (cursor != null) {
                        cursor.moveToFirst();
                        int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                        String imgDecodableString = cursor.getString(columnIndex);
                        cursor.close();
                        saveAsNextPhoto(imgDecodableString);
                        addNextPhoto();
                    }
                }
                break;
            case ADJUST_LOCATION_REQUEST:
                switch (resultCode) {
                    case SetLocationActivity.FORCE_ADDRESS:
                        String steet = data.getStringExtra(SetLocationActivity.FORCE_ADDRESS_STREET);
                        int district = data.getIntExtra(SetLocationActivity.FORCE_ADDRESS_DISTRICT, 1);
                        forcedAddress = new Spot(steet, district);
                        updateLocation();
                        ((ImageButton) findViewById(R.id.adjustLocation)).setImageResource(android.R.drawable.ic_menu_mylocation);
                        break;
                }
                break;
            case LOGIN_REQUEST:
                updateLoginButton();
                break;
        }
    }

    /**
     * Creates a UI view for a photo
     */
    private View createPhotoView(@NonNull final Photo photo) {
        final RelativeLayout view = new RelativeLayout(this);
        final ImageView waitingIcon = new ImageView(this);
        waitingIcon.setImageResource(android.R.drawable.ic_menu_gallery);
        view.addView(waitingIcon, getCenterLayoutParams());
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                view.addView(getImageView(photo), getCenterLayoutParams());
                final int age = photo.getAge();
                // Add age on bottom in old image
                if (age >= 1) view.addView(getAgeView(age), getBottomLayoutParams());
                view.removeView(waitingIcon);
                view.setOnTouchListener(new View.OnTouchListener() {
                    GestureDetector gestureDetector = new GestureDetector(WelcomeActivity.this, new GestureDetector.SimpleOnGestureListener() {
                        int SWIPE_THRESHOLD = 100;
                        int SWIPE_VELOCITY_THRESHOLD = 100;

                        @Override
                        public boolean onDown(MotionEvent e) {
                            return true;
                        }

                        @Override
                        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                            float diffY = e2.getY() - e1.getY();
                            float diffX = e2.getX() - e1.getX();
                            if ((Math.abs(diffX) > Math.abs(diffY)) && (Math.abs(diffX) > SWIPE_THRESHOLD) && (Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD)) {
                                swipeHorizontally();
                                return true;
                            } else if ((Math.abs(diffY) > Math.abs(diffX)) && (Math.abs(diffY) > SWIPE_THRESHOLD) && (Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD)) {
                                swipeVertically();
                                return true;
                            } else return super.onFling(e1, e2, velocityX, velocityY);
                        }
                    });

                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        return gestureDetector.onTouchEvent(event);
                    }

                    private void swipeHorizontally() {
                        swipeVertically();
                    }

                    private void swipeVertically() {
                        final LinearLayout buttons = new LinearLayout(WelcomeActivity.this);
                        buttons.setOrientation(LinearLayout.HORIZONTAL);
                        final ImageButton remove = new ImageButton(WelcomeActivity.this);
                        remove.setBackgroundResource(R.color.removeBackground);
                        remove.setImageResource(android.R.drawable.ic_menu_delete);
                        remove.setPadding(20, 20, 20, 20);
                        buttons.addView(remove);
                        final ImageButton cancel = new ImageButton(WelcomeActivity.this);
                        cancel.setBackgroundResource(R.color.cancelBackground);
                        cancel.setPadding(20, 20, 20, 20);
                        cancel.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
                        buttons.addView(cancel);
                        view.addView(buttons, getCenterLayoutParams());

                        remove.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                removePhoto(photo);
                            }
                        });
                        cancel.setOnClickListener(new View.OnClickListener() {
                            @Override
                            public void onClick(View v) {
                                view.removeView(buttons);
                            }
                        });
                    }
                });
            }
        }, 10);
        return view;
    }

    private RelativeLayout.LayoutParams getCenterLayoutParams() {
        final RelativeLayout.LayoutParams res = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        res.addRule(RelativeLayout.CENTER_HORIZONTAL);
        res.addRule(RelativeLayout.CENTER_VERTICAL);
        return res;
    }

    private RelativeLayout.LayoutParams getBottomLayoutParams() {
        final RelativeLayout.LayoutParams res = new RelativeLayout.LayoutParams(RelativeLayout.LayoutParams.WRAP_CONTENT, RelativeLayout.LayoutParams.WRAP_CONTENT);
        res.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
        res.addRule(RelativeLayout.ALIGN_PARENT_START);
        res.addRule(RelativeLayout.ALIGN_PARENT_BOTTOM);
        res.setMargins(10, 10, 10, 10);
        return res;
    }

    @NonNull
    private TextView getAgeView(int age) {
        final TextView ageView = new TextView(this);
        ageView.setText(getResources().getQuantityString(R.plurals.age, age, age));
        ageView.setTextAppearance(this, R.style.OverPrint);
        ageView.setGravity(Gravity.CENTER_HORIZONTAL);
        return ageView;
    }

    @NonNull
    private ImageView getImageView(@NonNull Photo photo) {
        final int thumbnailSize = findViewById(R.id.imagesScroll).getHeight() - 20;
        final Bitmap bitmap = photo.getBitmap(thumbnailSize);
        final ImageView imageView = new ImageView(this);
        imageView.setImageBitmap(bitmap);
        return imageView;
    }

    /**
     * Add a photo in the UI scroll view
     */
    private void addImageView(@NonNull Photo photo) {
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.setMargins(5, 0, 5, 0);
        ((ViewGroup) findViewById(R.id.images)).addView(createPhotoView(photo), lp);
    }

    /**
     * Add a photo in the UI scroll view
     */
    private void removePhoto(@NonNull Photo photo) {
        int photoIndex = photos.indexOf(photo);
        if (photoIndex >= 0) {
            photos.remove(photoIndex);
            ((ViewGroup) findViewById(R.id.images)).removeViewAt(photoIndex);
            updateSendButton();
            updatePhotosCount();
        }
    }

    /**
     * Save content before orientation change
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putStringArrayList(SAVED_PHOTOS, Photo.toArrayList(photos));
        if (nextPhoto != null) outState.putString(SAVED_NEXT_PHOTO, nextPhoto.toString());
    }

    /**
     * Called when the user click on location button
     */
    public void adjustLocation(View view) {
        // Switch between forced address and automatic address provided by the GPS
        if (forcedAddress == null)
            startActivityForResult(new Intent(this, SetLocationActivity.class), ADJUST_LOCATION_REQUEST);
        else {
            forcedAddress = null;
            ((ImageButton) findViewById(R.id.adjustLocation)).setImageResource(android.R.drawable.ic_menu_edit);
            updateLocation();
        }
    }

    /**
     * Remove ALL photos
     * Called when the user click on the trash button
     */
    public void clearPhotos(View view) {
        if (!photos.isEmpty()) new AlertDialog.Builder(this).
                setTitle(R.string.required_confirmation).setMessage(R.string.clear_all_photos_confirmation).
                setIcon(android.R.drawable.ic_dialog_alert).
                setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        clearPhotos();
                    }
                }).
                setNegativeButton(android.R.string.no, null).
                show();
    }

    /**
     * Remove ALL photos (no UI confirmation)
     */
    public void clearPhotos() {
        photos.clear();
        ((ViewGroup) findViewById(R.id.images)).removeAllViews();
        updatePhotosCount();
        updateSendButton();
    }

    /**
     * After a successfull report !
     */
    private void displaySendSuccess() {
        sendReportReceiver.activity = null;
        sendReportReceiver = null;
        clearPhotos();
        new AlertDialog.Builder(this).setTitle(R.string.success).setMessage(R.string.images_sent).setIcon(android.R.drawable.ic_dialog_alert).show();
        displaySendProgress("");
    }

    private void displaySendProgress(@Nullable CharSequence message) {
        ((TextView) findViewById(R.id.progress)).setText((message != null) ? message : getString(R.string.error));
    }

    private void displaySendError(@Nullable CharSequence message) {
        sendReportReceiver.activity = null;
        sendReportReceiver = null;
        displayError((message != null) ? message : getString(R.string.error));
        updateSendButton();
        displaySendProgress("");
    }

    /**
     * Callback during the reporting procedure
     */
    private SendReportReceiver sendReportReceiver;

    static class SendReportReceiver extends ResultReceiver {
        SendReportReceiver(Handler handler, @NonNull WelcomeActivity initialActivity) {
            super(handler);
            this.activity = initialActivity;
        }

        WelcomeActivity activity;
        String lastProgressMessage;

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            switch (resultCode) {
                case SendingReportService.RESULT_CODE_SUCCESS:
                    if (activity != null) activity.displaySendSuccess();
                    break;
                case SendingReportService.RESULT_CODE_PROGRESS:
                    lastProgressMessage = resultData.getString(SendingReportService.PROGRESS_MESSAGE);
                    if (activity != null) activity.displaySendProgress(lastProgressMessage);
                    break;
                case SendingReportService.RESULT_CODE_ERROR:
                    if (activity != null)
                        activity.displaySendError(resultData.getString(SendingReportService.ERROR_MESSAGE));
                    break;
            }
        }
    }

    /**
     * In case the phone is rotated while sending photos
     */
    @Override
    public Object onRetainNonConfigurationInstance() {
        if (sendReportReceiver == null) return super.onRetainNonConfigurationInstance();
        else {
            sendReportReceiver.activity = null;
            return sendReportReceiver;
        }
    }

    /**
     * In case the phone has been rotated while sending photos
     */
    private void restoreSendReportReceiver() {
        final Object lastNonConfigurationInstance = getLastNonConfigurationInstance();
        if (lastNonConfigurationInstance != null) {
            sendReportReceiver = (SendReportReceiver) lastNonConfigurationInstance;
            sendReportReceiver.activity = this;
            displaySendProgress(sendReportReceiver.lastProgressMessage);
        }
    }

    /**
     * Called when the user click on login button
     */
    public void login(View view) {
        LoginActivity.Credentials credentials = LoginActivity.getCredentials(getApplicationContext());
        if (credentials != null) {
            LoginActivity.disconnect(getApplicationContext());
            updateLoginButton();
        } else startActivityForResult(new Intent(this, LoginActivity.class), LOGIN_REQUEST);
    }

    /**
     * Called when the user click on send button
     */
    public void send(View view) {
        Spot address = getAddress();
        LoginActivity.Credentials credentials = LoginActivity.getCredentials(getApplicationContext());
        if ((address != null) && (credentials != null) && !photos.isEmpty()) {
            sendReportReceiver = new SendReportReceiver(new Handler(), this);
            Intent intent = new Intent(this, SendingReportService.class);
            intent.putExtra(SendingReportService.RECEIVER, sendReportReceiver);
            intent.putExtra(SendingReportService.STREET, address.street);
            intent.putExtra(SendingReportService.DISTRICT, address.district);
            intent.putStringArrayListExtra(SendingReportService.IMAGES, Photo.toArrayList(photos));
            startService(intent);
            updateSendButton();
        }
    }

    boolean isSendingPhoto() {
        return sendReportReceiver != null;
    }

    /**
     * Return the current spot
     */
    @Nullable
    private Spot getAddress() {
        return (forcedAddress != null) ? forcedAddress : gpsAddress;
    }

    /**
     * Must be called when gpsAddress or forcedAddress is changed to update the UI
     */
    public void updateLocation() {
        Spot address = getAddress();
        if (address == null) setStreetTextView("...", R.color.progress);
        else setStreetTextView(address.toString(getResources()), R.color.address);
        updateSendButton();
    }

    /**
     * Set street textfield content and color
     */
    void setStreetTextView(@NonNull CharSequence text, int color) {
        final TextView streetTextView = getStreetTextView();
        streetTextView.setText(text);
        streetTextView.setTextColor(getResources().getColor(color));
    }

    @NonNull
    TextView getStreetTextView() {
        return ((TextView) findViewById(R.id.street));
    }

    public void displayError(@StringRes int messageId) {
        displayError(getString(messageId));
    }

    public void displayError(@NonNull CharSequence message) {
        new AlertDialog.Builder(this).setTitle(R.string.error).setMessage(message).setIcon(android.R.drawable.ic_dialog_alert).show();
    }

    public void displayInfo() {
        final StringBuilder message = new StringBuilder();
        message.append("Version: 0.9\n");
        message.append("Webdav");
        message.append("\nHost:\n").append(getString(R.string.webdav_host));
        message.append("\nSite:\n").append(getString(R.string.webdav_site)).append(getString(R.string.webdav_root));
        new AlertDialog.Builder(this).setTitle("Info").setMessage(message).setIcon(android.R.drawable.ic_dialog_info).show();
    }
}
