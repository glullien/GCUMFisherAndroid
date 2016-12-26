package gcum.gcumfisher;

import android.app.IntentService;
import android.content.Intent;
import android.os.Bundle;
import android.os.ResultReceiver;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.apache.jackrabbit.webdav.DavException;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * Background service pour l'envoi des fichiers vers le cloud
 */
public class SendingReportService extends IntentService {

    public static final String RECEIVER = "gcum.gcumfisher.SendingReportService.RECEIVER";
    public static final String STREET = "gcum.gcumfisher.SendingReportService.STREET";
    public static final String DISTRICT = "gcum.gcumfisher.SendingReportService.DISTRICT";
    public static final String IMAGES = "gcum.gcumfisher.SendingReportService.IMAGES";
    public static final String PROGRESS_MESSAGE = "gcum.gcumfisher.SendingReportService.PROGRESS_MESSAGE";
    public static final String ERROR_MESSAGE = "gcum.gcumfisher.SendingReportService.ERROR_MESSAGE";
    public static final int RESULT_CODE_SUCCESS = 0;
    public static final int RESULT_CODE_PROGRESS = 1;
    public static final int RESULT_CODE_ERROR = 2;

    public SendingReportService() {
        super("SendingReportService");
    }

    private final static Map<String, String> streetsDirs = new HashMap<>();

    static {
        // Petit trick pour conserver les noms actuels dans le cloud
        streetsDirs.put("quai de jemmapes", "quai_Jemmapes/");
    }

    @NonNull
    private static String replaceSpecialChars(@NonNull String source) {
        // Apparemment le Webdav n'aime pas les accents français
        // todo trouver la liste exhaustive des caractères autorisés
        return Chars.toStdChars(source.replaceAll(" ", "_").replaceAll("/", " _"));
    }

    @NonNull
    private static String firstCharToLowerCase(@NonNull String source) {
        return source.substring(0, 1).toLowerCase() + source.substring(1);
    }

    @NonNull
    private static String encodeStreet(@NonNull String street) {
        String res = streetsDirs.get(street.toLowerCase());
        if (res == null) res = firstCharToLowerCase(replaceSpecialChars(street)) + "/";
        return res;
    }

    @NonNull
    private static String encodeDistrict(int district) {
        return (district == 1) ? "1er/" : (Integer.toString(district) + "e/");
    }

    @NonNull
    private static String encodeDate(long date) {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy_MM_dd", Locale.FRANCE);
        dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        return dateFormat.format(new Date(date)) + "/";
    }

    @NonNull
    private static String encodeFileName(@NonNull String image) {
        int lastSlash = image.lastIndexOf('/');
        return image.substring(lastSlash + 1);
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if (intent == null) return;
        final ResultReceiver receiver = intent.getParcelableExtra(RECEIVER);
        try {
            WebDavAccess webDavAccess = new WebDavAccess(getResources());

            String street = encodeStreet(intent.getStringExtra(STREET));
            String district = encodeDistrict(intent.getIntExtra(DISTRICT, 0));
            List<Photo> images = Photo.fromArrayList(intent.getStringArrayListExtra(IMAGES));

            makeDirs(receiver, webDavAccess, street, district, getEncodedDates(images));
            transferFiles(receiver, webDavAccess, images, "Dossier/" + district + street);

            deliverSuccess(receiver);
        } catch (Exception e) {
            e.printStackTrace();
            deliverError(receiver, "Err: " + e);
        }
    }

    private void makeDirs(@NonNull ResultReceiver receiver, @NonNull WebDavAccess webDavAccess, @NonNull String street, @NonNull String district, @NonNull Set<String> dates) throws IOException, DavException {
        deliverProgress(receiver, getString(R.string.creating_directories));
        webDavAccess.ensureExists("Dossier/", Arrays.asList(district, street));
        for (String date : dates)
            webDavAccess.ensureExists("Dossier/" + district + street, Collections.singletonList(date));
    }

    @NonNull
    private Set<String> getEncodedDates(@NonNull List<Photo> images) {
        final Set<String> dates = new HashSet<>();
        for (Photo photo : images) dates.add(encodeDate(photo.date));
        return dates;
    }

    private void transferFiles(@NonNull ResultReceiver receiver, @NonNull WebDavAccess webDavAccess, @NonNull List<Photo> images, @NonNull String dir) throws IOException, DavException {
        for (int i = 0; i < images.size(); i++) {
            final Photo photo = images.get(i);
            deliverProgress(receiver, getString(R.string.sending_images, i + 1, images.size()));
            webDavAccess.putFile(dir + encodeDate(photo.date), encodeFileName(photo.path), photo.path);
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
