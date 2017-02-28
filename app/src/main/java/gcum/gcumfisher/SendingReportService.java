package gcum.gcumfisher;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.List;

import gcum.gcumfisher.connection.AutoLogin;
import gcum.gcumfisher.connection.Server;

/**
 * Background service pour l'envoi des fichiers vers le cloud
 */
public class SendingReportService extends IntentService {

    public static final String RECEIVER = "gcum.gcumfisher.SendingReportService.RECEIVER";
    public static final String STREET = "gcum.gcumfisher.SendingReportService.STREET";
    public static final String DISTRICT = "gcum.gcumfisher.SendingReportService.DISTRICT";
    public static final String IMAGE_SIZE = "gcum.gcumfisher.SendingReportService.IMAGE_SIZE";
    public static final String IMAGE_QUALITY = "gcum.gcumfisher.SendingReportService.IMAGE_QUALITY";
    public static final String IMAGES = "gcum.gcumfisher.SendingReportService.IMAGES";
    public static final String PROGRESS_MESSAGE = "gcum.gcumfisher.SendingReportService.PROGRESS_MESSAGE";
    public static final String ERROR_MESSAGE = "gcum.gcumfisher.SendingReportService.ERROR_MESSAGE";
    public static final int RESULT_CODE_SUCCESS = 0;
    public static final int RESULT_CODE_PROGRESS = 1;
    public static final int RESULT_CODE_ERROR = 2;
    private Server server;

    public SendingReportService() {
        super("SendingReportService");
    }

    @Override
    public void onCreate() {
        super.onCreate();
        server = new Server(getResources());
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        final ResultReceiver receiver = intent.getParcelableExtra(RECEIVER);
        try {
            final AutoLogin autoLogin = LoginActivity.getAutoLogin(getApplicationContext());
            if (autoLogin == null) deliverError(receiver, "Aucun login");
            else {
                final String street = intent.getStringExtra(STREET);
                final int district = intent.getIntExtra(DISTRICT, Integer.MIN_VALUE);
                final int quality = intent.getIntExtra(IMAGE_QUALITY, 95);
                final PreferencesActivity.ImageSize imageSize = PreferencesActivity.ImageSize.valueOf(intent.getStringExtra(IMAGE_SIZE));
                final List<Photo> photos = Photo.fromArrayList(intent.getStringArrayListExtra(IMAGES));
                for (int i = 0; i < photos.size(); i++) {
                    deliverProgress(receiver, getString(R.string.sending_images, i + 1, photos.size()));
                    final Photo photo = photos.get(i);
                    byte[] resized = imageSize.resize(photo, quality, getApplicationContext());
                    File tmpFile = File.createTempFile("GcumReport", photo.extension());
                    FileOutputStream o = new FileOutputStream(tmpFile);
                    o.write(resized);
                    o.close();
                    server.uploadAndReport(autoLogin, street, district, photo.date, photo.point, tmpFile.getPath());
                    final boolean deleted = tmpFile.delete();
                    if (!deleted) throw new Exception("Cannot delete " + tmpFile);
                }
                deliverSuccess(receiver);
            }
        } catch (Exception e) {
            e.printStackTrace();
            deliverError(receiver, "Err: " + e);
        }
    }

    private void deliverSuccess(@NonNull ResultReceiver receiver) {
        receiver.send(RESULT_CODE_SUCCESS, new Bundle());
    }

    private void deliverError(@NonNull ResultReceiver receiver, @NonNull String message) {
        Bundle bundle = new Bundle();
        bundle.putString(ERROR_MESSAGE, message);
        receiver.send(RESULT_CODE_ERROR, bundle);
    }

    private void deliverProgress(@NonNull ResultReceiver receiver, @NonNull String message) {
        Bundle bundle = new Bundle();
        bundle.putString(PROGRESS_MESSAGE, message);
        receiver.send(RESULT_CODE_PROGRESS, bundle);
    }

}
