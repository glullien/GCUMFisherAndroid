package gcum.gcumfisher;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.util.List;

import gcum.gcumfisher.connection.AutoLogin;
import gcum.gcumfisher.connection.GetLogin;

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

    public SendingReportService() {
        super("SendingReportService");
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        final ResultReceiver receiver = intent.getParcelableExtra(RECEIVER);
        try {
            final AutoLogin autoLogin = LoginActivity.getAutoLogin(getApplicationContext());
            final String street = intent.getStringExtra(STREET);
            final int district = intent.getIntExtra(DISTRICT, 0);
            final List<Photo> photos = Photo.fromArrayList(intent.getStringArrayListExtra(IMAGES));
            for (int i = 0; i < photos.size(); i++) {
                deliverProgress(receiver, getString(R.string.sending_images, i + 1, photos.size()));
                GetLogin.uploadAndReport(autoLogin, street, district, photos.get(i));
            }

            deliverSuccess(receiver);
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
