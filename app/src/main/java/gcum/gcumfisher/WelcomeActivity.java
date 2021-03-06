package gcum.gcumfisher;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.ResultReceiver;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import gcum.gcumfisher.connection.AutoLogin;
import gcum.gcumfisher.connection.Point;
import gcum.gcumfisher.connection.Server;

/**
 * Main page
 */
public class WelcomeActivity extends Activity {

    private static final int TAKE_PHOTO_REQUEST = 1;
    private static final int PICK_PHOTO_REQUEST = 2;
    private static final int ADJUST_LOCATION_REQUEST = 3;
    private static final int LOGIN_REQUEST = 4;
    private static final int PREFERENCES_REQUEST = 5;

    private static final int LOCATION_PERMISSION_REQUEST = 10;

    public static final String SAVED_PHOTOS = "gcum.gcumfisher.WelcomeActivity.PHOTOS";
    public static final String SAVED_NEXT_PHOTO = "gcum.gcumfisher.WelcomeActivity.NEXT_PHOTO";
    public static final int MAX_QUICK_LOCATIONS = 4;

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
     * The current spots found by the GPS
     */
    @Nullable
    private List<Spot> gpsAddress;

    /**
     * The spot forced by the user with the SetLocationActity
     * If non null, it's override gpsAddress
     */
    @Nullable
    private Spot forcedAddress;

    /**
     * Brut current location
     */
    @Nullable
    private Location location;

    @Nullable
    private LocationSolver locationSolver;

    private Server server;

    private boolean cannotDisplayPhoto;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.welcome);
        server = new Server(getResources());
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

        getStreetTextView().setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ((gpsAddress != null) && (gpsAddress.size() > 1)) {
                    gpsAddress.add(gpsAddress.remove(0));
                    updateLocation();
                }
            }
        });

        // Small cheat to view what cloud is being used
        findViewById(R.id.title).setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                displayInfo();
                return true;
            }
        });
        updateLoginButton();

        // Check and request location permission
        boolean fineLocationRefused = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        boolean coarseLocationRefused = ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED;
        if (fineLocationRefused && coarseLocationRefused) {
            boolean shouldRequestFineLocation = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION);
            boolean shouldRequestCoarseLocation = ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION);

            if (shouldRequestFineLocation && shouldRequestCoarseLocation)
                displayLocationPermissionInfo(R.string.request_both_location_permissions);
            else if (shouldRequestFineLocation)
                displayLocationPermissionInfo(R.string.request_fine_location_permissions);
            else if (shouldRequestCoarseLocation)
                displayLocationPermissionInfo(R.string.request_coarse_location_permissions);
            else requestLocationPermissions();
        } else startLocationSolver();

        server.startLog(null, "Opening welcome activity");
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.welcome, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final Location location = this.location;
        switch (item.getItemId()) {
            case R.id.preferences:
                startActivityForResult(new Intent(this, PreferencesActivity.class), PREFERENCES_REQUEST);
                return true;
            case R.id.map:
                final Intent mapIntent = new Intent(this, MapActivity.class);
                if (location != null) {
                    mapIntent.putExtra(MapActivity.LATITUDE, location.getLatitude());
                    mapIntent.putExtra(MapActivity.LONGITUDE, location.getLongitude());
                }
                startActivity(mapIntent);
                return true;
            case R.id.list:
                final Intent intent = new Intent(this, ListActivity.class);
                intent.putExtra(ListActivity.TYPE, ListActivity.ALL);
                if (location != null) {
                    intent.putExtra(ListActivity.HERE_LATITUDE, location.getLatitude());
                    intent.putExtra(ListActivity.HERE_LONGITUDE, location.getLongitude());
                }
                startActivity(intent);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void displayLocationPermissionInfo(int messageId) {
        new AlertDialog.Builder(this).setTitle(R.string.error).setMessage(messageId).setIcon(android.R.drawable.ic_dialog_alert).
                setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        requestLocationPermissions();
                    }
                }).
                setNegativeButton(android.R.string.no, null).
                show();
    }

    private void requestLocationPermissions() {
        List<String> missingPermissions = new ArrayList<>(2);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            missingPermissions.add(Manifest.permission.ACCESS_FINE_LOCATION);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            missingPermissions.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!missingPermissions.isEmpty()) {
            ActivityCompat.requestPermissions(this, missingPermissions.toArray(new String[missingPermissions.size()]), LOCATION_PERMISSION_REQUEST);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case LOCATION_PERMISSION_REQUEST:
                if ((grantResults.length > 0) && (grantResults[0] == PackageManager.PERMISSION_GRANTED))
                    startLocationSolver();
                break;
        }
    }

    private void startLocationSolver() {
        locationSolver = LocationSolver.startTracking(this, new LocationSolver.Listener() {
            @Override
            public void displayError(@NonNull CharSequence sequence) {
                final String message = sequence.toString();
                server.startLog(null, message);
                Log.e("WelcomeActivity", message);
                if (getAddress() == null) setStreetTextView(message, R.color.error);
            }

            /**
             * Display the progression message if no spot in ready
             */
            @Override
            public void setLocationProgressMessage(@NonNull CharSequence text) {
                if (getAddress() == null) setStreetTextView(text, R.color.progress);
            }

            @Override
            public void setLocation(@Nullable Location location) {
                WelcomeActivity.this.location = location;
            }

            /**
             * Called when the GPS find a spot
             */
            @Override
            public void setAddresses(@NonNull List<Spot> addresses) {
                if (addresses.isEmpty() && (gpsAddress != null)) {
                    gpsAddress = null;
                    updateLocation();
                } else if ((gpsAddress == null) || !sameAddresses(gpsAddress, addresses)) {
                    gpsAddress = addresses.isEmpty() ? null : addresses;
                    updateLocation();
                }
            }

            private boolean sameAddresses(@NonNull List<Spot> addresses1, @NonNull List<Spot> addresses2) {
                if (addresses1.size() != addresses2.size()) return false;
                for (int i = 0; i < addresses1.size(); i++)
                    if (!addresses1.get(i).equals(addresses2.get(i))) return false;
                return true;
            }
        }, MAX_QUICK_LOCATIONS);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (locationSolver != null) locationSolver.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (locationSolver != null) locationSolver.stop();
    }

    private void updateLoginButton() {
        ((Button) findViewById(R.id.login)).setText(getString(isConnected() ? R.string.logout : R.string.login));
    }

    private boolean isConnected() {
        return LoginActivity.getAutoLogin(getApplicationContext()) != null;
    }

    /**
     * Enabled send button if we've got a spot and at least one photo
     */
    private void updateSendButton() {
        findViewById(R.id.send).setEnabled((!photos.isEmpty()) && (!isSendingPhoto()) && (getAddress() != null) && isConnected() && (!cannotDisplayPhoto));
    }

    /**
     * Change the counter on screen
     */
    private void updatePhotosCount() {
        NumberFormat nb = DecimalFormat.getIntegerInstance(Locale.FRANCE);
        ((TextView) findViewById(R.id.photosCount)).setText(nb.format(photos.size()));
    }

    private static final int REQUEST_EXTERNAL_STORAGE = 1;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    /**
     * Checks if the app has permission to write to device storage
     * If the app does not has permission then the user will be prompted to grant permissions
     */
    public static void verifyStoragePermissions(Activity activity) {
        // Check if we have write permission
        int permission = ActivityCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permission != PackageManager.PERMISSION_GRANTED) {
            // We don't have permission so prompt the user
            ActivityCompat.requestPermissions(
                    activity,
                    PERMISSIONS_STORAGE,
                    REQUEST_EXTERNAL_STORAGE
            );
        }
    }

    /**
     * Called by take photo button
     */
    public void takePhoto(View view) {
        verifyStoragePermissions(this);
        try {
            final Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
            if (takePictureIntent.resolveActivity(getPackageManager()) == null)
                displayError(R.string.cannot_take_picture);
            else {
                File photoFile = createImageFile(System.currentTimeMillis(), location);
                Uri photoURI = FileProvider.getUriForFile(this, "gcum.gcumfisher.fileprovider", photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);
                startActivityForResult(takePictureIntent, TAKE_PHOTO_REQUEST);
            }
        } catch (Exception e) {
            server.startLog(null, "Failed to take photo", e);
            displayError(getString(R.string.error_message, e.getMessage()));
        }
    }

    /**
     * Called by pick a photo from gallery button
     */
    public void pickPhoto(View view) {
        verifyStoragePermissions(this);
        try {
            final Intent galleryIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(galleryIntent, PICK_PHOTO_REQUEST);
        } catch (Exception e) {
            server.startLog(null, "Failed to pick photo", e);
            displayError(getString(R.string.error_message, e.getMessage()));
        }
    }

    /**
     * Create a new file to store a photo.
     * Side effect : fill the nextPhoto field that will be used by addNextPhoto()
     */
    private File createImageFile(long date, @Nullable final Location location) throws IOException {
        final File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        final File image = File.createTempFile("GCUM", ".JPEG", storageDir);
        final Point point = (location != null) ? new Point(location) : null;
        nextPhoto = new Photo(image.getAbsolutePath(), date, point);
        return image;
    }

    /**
     * Add the photo in nextPhoto in the list of images on screen
     */
    private void addNextPhoto() {
        // nextPhoto should not be null, but it's safer to check
        if (nextPhoto != null) try {
            photos.add(nextPhoto);
            addImageView(nextPhoto);
            updateSendButton();
            updatePhotosCount();
        } catch (Exception e) {
            server.startLog(null, "Failed to add photo", e);
            displayError(getString(R.string.error_message, e.getMessage()));
        }
    }

    /**
     * Copy the photo in path into a new file that will be used by addNextPhoto()
     */
    private void saveAsNextPhoto(@NonNull final String path) throws IOException {
        final File source = new File(path);
        final File f;
        try {
            f = createImageFile(source.lastModified(), null);
        } catch (IOException e) {
            displayError(R.string.error);
            throw e;
        }
        try (FileOutputStream out = new FileOutputStream(f); InputStream in = new FileInputStream(source)) {
            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) out.write(buf, 0, len);
        } catch (Exception e) {
            displayError(R.string.cannot_save_photo);
            throw e;
        }
    }

    private void addPickedPhoto(@NonNull final Intent data) {
        try {
            final Uri selectedImage = data.getData();
            final String[] filePathColumn = {MediaStore.Images.Media.DATA};
            final Cursor cursor = getContentResolver().query(selectedImage, filePathColumn, null, null, null);
            if (cursor != null) {
                cursor.moveToFirst();
                final int columnIndex = cursor.getColumnIndex(filePathColumn[0]);
                final String imgDecodableString = cursor.getString(columnIndex);
                cursor.close();
                saveAsNextPhoto(imgDecodableString);
                addNextPhoto();
            }
        } catch (Exception e) {
            server.startLog(null, "Failed to save picked photo", e);
            displayError(getString(R.string.error_message, e.getMessage()));
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
                if ((resultCode == RESULT_OK) && (data != null)) addPickedPhoto(data);
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
        LayoutInflater inflater = getLayoutInflater();
        final ViewGroup view = (ViewGroup) inflater.inflate(R.layout.welcome_photo, null);
        view.postDelayed(new Runnable() {
            @Override
            public void run() {
                final int thumbnailSize = findViewById(R.id.imagesScroll).getHeight() - 20;
                final Bitmap bitmap = photo.getBitmap(thumbnailSize);
                final ImageView imageView = ((ImageView) view.findViewById(R.id.photo_view));
                if (bitmap != null) imageView.setImageBitmap(bitmap);
                else {
                    imageView.setImageDrawable(getResources().getDrawable(R.drawable.error, null));
                    if (!cannotDisplayPhoto) {
                        cannotDisplayPhoto = true;
                        updateSendButton();
                        displayError(R.string.cannot_display_photo);
                    }
                }

                final int age = photo.getAge();
                ((TextView) view.findViewById(R.id.age)).setText((age < 1) ? "" : getResources().getQuantityString(R.plurals.age, age, age));

                view.findViewById(R.id.trash).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        removePhoto(photo);
                    }
                });
            }
        }, 10);
        return view;
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
        if (forcedAddress == null) {
            final Intent intent = new Intent(this, SetLocationActivity.class);
            final Location location = this.location;
            if (location != null) {
                intent.putExtra(SetLocationActivity.LATITUDE, location.getLatitude());
                intent.putExtra(SetLocationActivity.LONGITUDE, location.getLongitude());
            }
            startActivityForResult(intent, ADJUST_LOCATION_REQUEST);
        } else {
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

    private static class SendReportReceiver extends ResultReceiver {
        SendReportReceiver(Handler handler, @NonNull WelcomeActivity initialActivity) {
            super(handler);
            this.activity = initialActivity;
        }

        WelcomeActivity activity;
        String lastProgressMessage;

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            try {
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
            } catch (Exception e) {
                if (activity != null) activity.reportInternalError("Sending images", e);
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
        if (isConnected()) {
            LoginActivity.disconnect(getApplicationContext());
            updateLoginButton();
        } else startActivityForResult(new Intent(this, LoginActivity.class), LOGIN_REQUEST);
    }

    /**
     * Called when the user click on send button
     */
    public void send(View view) {
        try {
            final Spot address = getAddress();
            if ((address != null) && isConnected() && (!photos.isEmpty()) && (!cannotDisplayPhoto)) {
                sendReportReceiver = new SendReportReceiver(new Handler(), this);
                Intent intent = new Intent(this, SendingReportService.class);
                intent.putExtra(SendingReportService.RECEIVER, sendReportReceiver);
                intent.putExtra(SendingReportService.STREET, address.street);
                intent.putExtra(SendingReportService.DISTRICT, address.district);
                intent.putExtra(SendingReportService.IMAGE_SIZE, PreferencesActivity.getImageSize(getApplicationContext()).name());
                intent.putExtra(SendingReportService.IMAGE_QUALITY, PreferencesActivity.getImageQuality(getApplicationContext()));
                intent.putStringArrayListExtra(SendingReportService.IMAGES, Photo.toArrayList(photos));
                startService(intent);
                updateSendButton();
            }
        } catch (Exception e) {
            reportInternalError("Sending images", e);
        }
    }

    void reportInternalError(@NonNull String message, @NonNull Exception e) {
        Log.e("WelcomeActivity", message, e);
        server.startLog(null, message, e);
        displayError(getString(R.string.error_message, e.getMessage()));
    }

    boolean isSendingPhoto() {
        return sendReportReceiver != null;
    }

    /**
     * Return the current spot
     */
    @Nullable
    private Spot getAddress() {
        return (forcedAddress != null) ? forcedAddress : (gpsAddress != null ? gpsAddress.get(0) : null);
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
        message.append("Version: ").append(BuildConfig.VERSION_NAME).append("\n");
        message.append("\nBase url:\n").append(getString(R.string.base_url)).append("\n");
        final AutoLogin autoLogin = LoginActivity.getAutoLogin(getApplicationContext());
        if (autoLogin != null)
            message.append("\nConnecté:\n").append(autoLogin.getCode()).append("\n");
        new AlertDialog.Builder(this).setTitle("Info").setMessage(message).setIcon(android.R.drawable.ic_dialog_info).show();
    }
}
