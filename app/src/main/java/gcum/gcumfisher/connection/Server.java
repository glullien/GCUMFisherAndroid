package gcum.gcumfisher.connection;

import android.content.res.Resources;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.android.internal.http.multipart.FilePart;
import com.android.internal.http.multipart.MultipartEntity;
import com.android.internal.http.multipart.Part;
import com.android.internal.http.multipart.StringPart;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLPeerUnverifiedException;

import gcum.gcumfisher.BuildConfig;
import gcum.gcumfisher.R;

@SuppressWarnings("StringConcatenation")
public class Server {
    @NonNull
    private final Resources resources;
    @Nullable
    private String baseUrl;

    public Server(@NonNull Resources resources) {
        this.resources = resources;
    }

    @NonNull
    private String seekBaseUrl(@NonNull List<String> baseUrls) throws Exception {
        for (String baseUrl : baseUrls) {
            if (test(baseUrl)) return baseUrl;
        }
        Log.e("Server", "Cannot find correct url");
        throw new IOException("Cannot find correct url");
    }

    private boolean test(@NonNull String baseUrl) throws Exception {
        try {
            HttpURLConnection conn = getConnection(baseUrl + "index.jsp");
            conn.setReadTimeout(10000);
            conn.setConnectTimeout(15000);
            conn.setRequestMethod("GET");
            conn.setUseCaches(false);
            conn.setDoInput(true);
            return 1 == conn.getInputStream().read(new byte[1]);
        } catch (SSLPeerUnverifiedException e) {
            Log.e("Server", "Failed test for " + baseUrl, e);
            return false;
        }
    }

    @NonNull
    public String getBaseUrls() {
        return resources.getString(R.string.base_url);
    }

    @NonNull
    private String getBaseUrl() throws Exception {
        if (baseUrl == null) baseUrl = seekBaseUrl(Arrays.asList(getBaseUrls().split(" ")));
        return baseUrl;
    }

    private static String readStream(InputStream in) {
        BufferedReader reader = null;
        StringBuilder builder = new StringBuilder();
        try {
            reader = new BufferedReader(new InputStreamReader(in, "UTF-16"));
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return builder.toString();
    }

    private JSONObject queryJson(String servlet, Map<String, String> params) throws Exception {
        return queryJson(servlet, params, null);
    }

    private JSONObject queryJson(String servlet, Map<String, String> params, Part part) throws Exception {
        final Map<String, String> stdParams = new HashMap<>();
        stdParams.put("device", "android " + android.os.Build.VERSION.RELEASE + " app " + BuildConfig.VERSION_NAME);
        stdParams.putAll(params);
        JSONObject res = new JSONObject(readStream(query(servlet, stdParams, part)));
        if (res.getString("result").equals("success")) return res;
        else
            throw new ServerReturnedErrorException(res.getString("message"), res.optString("code", null));
    }

    private HttpURLConnection getConnection(@NonNull String url) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        if (conn instanceof HttpsURLConnection)
            ((HttpsURLConnection) conn).setSSLSocketFactory(SSL.getSSLSocketFactory(resources));
        return conn;
    }

    private InputStream query(@NonNull String servlet, Map<String, String> params, Part part) throws Exception {
        HttpURLConnection conn = getConnection(getBaseUrl() + servlet);
        conn.setReadTimeout(10000);
        conn.setConnectTimeout(15000);
        conn.setRequestMethod("POST");
        conn.setUseCaches(false);
        conn.setDoInput(true);
        conn.setDoOutput(true);

        if (part == null) {
            final OutputStream os = conn.getOutputStream();
            final PrintWriter writer = new PrintWriter(new OutputStreamWriter(os, "UTF-8"));
            boolean first = true;
            for (Map.Entry<String, String> e : params.entrySet()) {
                if (first) first = false;
                else writer.print("&");
                writer.print(URLEncoder.encode(e.getKey(), "UTF-8"));
                writer.print("=");
                writer.print(URLEncoder.encode(e.getValue(), "UTF-8"));
            }
            writer.flush();
            writer.close();
            os.close();
        } else {
            final List<Part> parts = new LinkedList<>();
            for (Map.Entry<String, String> e : params.entrySet())
                parts.add(new StringPart(e.getKey(), e.getValue(), "UTF-8"));
            parts.add(part);
            final MultipartEntity multipart = new MultipartEntity(parts.toArray(new Part[parts.size()]));
            multipart.setContentEncoding("UTF-8");

            conn.addRequestProperty("Content-length", multipart.getContentLength() + "");
            conn.addRequestProperty(multipart.getContentType().getName(), multipart.getContentType().getValue());

            conn.connect();
            OutputStream os = conn.getOutputStream();
            multipart.writeTo(conn.getOutputStream());
            os.close();
        }

        final int code = conn.getResponseCode();
        if (code == HttpURLConnection.HTTP_OK) return conn.getInputStream();

        throw new HttpErrorCodeException(code);
    }

    public AutoLogin getAutoLogin(String username, String password) throws Exception {
        return getAutoLogin(username, password, null, false);
    }

    public AutoLogin getAutoLogin(String username, String password, String email, boolean register) throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("username", username);
        params.put("password", password);
        if (email != null) params.put("email", email);
        params.put("register", Boolean.toString(register));
        final JSONObject res = queryJson("getAutoLogin", params);
        return new AutoLogin(res.getString("autoLogin"), res.getString("validTo"));
    }

    public void sendID(String email) throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("email", email);
        queryJson("sendID", params);
    }

    public void uploadAndReport(@NonNull final AutoLogin autoLogin, @NonNull final String street, final int district, final long date, @Nullable Point point, @NonNull String path) throws Exception {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
        dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        final SimpleDateFormat timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.FRANCE);
        timeFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));

        final Map<String, String> params = new HashMap<>();
        params.put("autoLogin", autoLogin.getCode());
        params.put("street", street);
        params.put("district", Integer.toString(district));
        final Date d = new Date(date);
        params.put("date", dateFormat.format(d));
        params.put("time", timeFormat.format(d));
        if (point != null) {
            params.put("latitude", Long.toString(point.getLatitude()));
            params.put("longitude", Long.toString(point.getLongitude()));
        }
        final File file = new File(path);
        final FilePart part = new FilePart(file.getName(), file, "image/jpeg", "UTF-8");
        queryJson("uploadAndReport", params, part);
    }

    public List<Point> getPoints() throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("zone", "All");
        params.put("timeFrame", "All");
        params.put("locationSources", "Street,Device");
        params.put("authors", "-All-");
        final JSONObject res = queryJson("getPoints", params);
        final JSONArray photos = res.getJSONArray("photos");
        final List<Point> list = new ArrayList<>(photos.length());
        for (int i = 0; i < photos.length(); i++) {
            JSONObject o = photos.getJSONObject(i);
            list.add(new Point(o.getLong("latitude"), o.getLong("longitude")));
        }
        return list;
    }

    public enum Sort {date, closest}

    public static class GetListResult {
        @NonNull
        private final List<ServerPhoto> photos;
        private final int nbAfter;

        GetListResult(@NonNull List<ServerPhoto> photos, int nbAfter) {
            this.photos = photos;
            this.nbAfter = nbAfter;
        }

        @NonNull
        public List<ServerPhoto> getPhotos() {
            return photos;
        }

        public int getNbAfter() {
            return nbAfter;
        }
    }

    public GetListResult getList(@Nullable AutoLogin autoLogin, int number, @NonNull Sort sort, @Nullable Point point, @Nullable String author, @Nullable String start) throws Exception {
        final Map<String, String> params = new HashMap<>();
        if (autoLogin != null) params.put("autoLogin", autoLogin.getCode());
        params.put("district", "All");
        params.put("sort", sort.toString());
        params.put("author", author);
        if (point != null) {
            params.put("latitude", Long.toString(point.getLatitude()));
            params.put("longitude", Long.toString(point.getLongitude()));
        }
        params.put("start", (start == null) ? "Latest" : start);
        params.put("number", Integer.toString(number));
        final JSONObject res = queryJson("getList", params);
        return new GetListResult(getServerPhotos(res.getJSONArray("photos")), res.getInt("nbAfter"));
    }

    public List<ServerPhoto> getPointInfo(@Nullable AutoLogin autoLogin, Point point) throws Exception {
        final Map<String, String> params = new HashMap<>();
        if (autoLogin != null) params.put("autoLogin", autoLogin.getCode());
        params.put("latitude", Long.toString(point.getLatitude()));
        params.put("longitude", Long.toString(point.getLongitude()));
        params.put("timeFrame", "All");
        params.put("locationSources", "Street,Device");
        params.put("authors", "-All-");
        final JSONObject res = queryJson("getPointInfo", params);
        return getServerPhotos(res.getJSONArray("photos"));
    }

    public PhotoDetails getPhotoInfo(String id) throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("id", id);
        final JSONObject o = queryJson("getPhotoInfo", params);
        ServerPhoto.Address address = new ServerPhoto.Address(o.getString("street"), o.getInt("district"), o.getString("city"));
        Point point = new Point(o.getLong("latitude"), o.getLong("longitude"));
        ServerPhoto.CoordinatesSource coordinatesSource = ServerPhoto.CoordinatesSource.valueOf(o.getString("locationSource"));
        ServerPhoto.Coordinates coordinates = new ServerPhoto.Coordinates(point, coordinatesSource);
        ServerPhoto.Location location = new ServerPhoto.Location(address, coordinates);
        final String time = o.getString("time");
        final JSONArray likes = o.getJSONArray("likes");
        final List<String> likesList = new ArrayList<>(likes.length());
        for (int i = 0; i < likes.length(); i++) likesList.add(likes.getString(i));
        return new PhotoDetails(
                o.getString("date"),
                "unknown".equals(time) ? null : time,
                location,
                o.getInt("width"),
                o.getInt("height"),
                o.optString("username", null),
                likesList,
                o.getBoolean("isLiked"));
    }

    @NonNull
    private static List<ServerPhoto> getServerPhotos(JSONArray photos) throws JSONException {
        final List<ServerPhoto> list = new ArrayList<>(photos.length());
        for (int i = 0; i < photos.length(); i++) list.add(getServerPhoto(photos.getJSONObject(i)));
        return list;
    }

    @NonNull
    private static ServerPhoto getServerPhoto(@NonNull JSONObject o) throws JSONException {
        ServerPhoto.Address address = new ServerPhoto.Address(o.getString("street"), o.getInt("district"), o.getString("city"));
        Point point = new Point(o.getLong("latitude"), o.getLong("longitude"));
        ServerPhoto.CoordinatesSource coordinatesSource = ServerPhoto.CoordinatesSource.valueOf(o.getString("locationSource"));
        ServerPhoto.Coordinates coordinates = new ServerPhoto.Coordinates(point, coordinatesSource);
        ServerPhoto.Location location = new ServerPhoto.Location(address, coordinates);
        final String time = o.getString("time");
        return new ServerPhoto(
                o.getString("id"),
                o.getString("date"),
                "unknown".equals(time) ? null : time,
                location,
                o.optString("username", null),
                o.getInt("likesCount"),
                o.getBoolean("isLiked"));
    }

    public HttpURLConnection getPhoto(String id, int maxSize) throws Exception {
        HttpURLConnection conn = getConnection(getBaseUrl() + "getPhoto?id=" + id + "&maxSize=" + maxSize);
        conn.setDoInput(true);
        conn.connect();
        return conn;
    }

    public HttpURLConnection getPhoto(String id) throws Exception {
        HttpURLConnection conn = getConnection(getBaseUrl() + "getPhoto?id=" + id);
        conn.setDoInput(true);
        conn.connect();
        return conn;
    }

    @NonNull
    public List<ServerPhoto.Address> searchAddress(@NonNull String pattern, int nbAnswers) throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("pattern", pattern);
        params.put("nbAnswers", Integer.toString(nbAnswers));
        final JSONObject res = queryJson("searchAddress", params);
        return getAddresses(res.getJSONArray("streets"));
    }

    @NonNull
    public List<ServerPhoto.Address> searchClosest(@NonNull Point point, int nb) throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("latitude", Long.toString(point.getLatitude()));
        params.put("longitude", Long.toString(point.getLongitude()));
        params.put("nb", Integer.toString(nb));
        final JSONObject res = queryJson("searchClosest", params);
        return getAddresses(res.getJSONArray("streets"));
    }

    @NonNull
    private static List<ServerPhoto.Address> getAddresses(@NonNull JSONArray streets) throws JSONException {
        final List<ServerPhoto.Address> list = new ArrayList<>(streets.length());
        for (int i = 0; i < streets.length(); i++) {
            final JSONObject o = streets.getJSONObject(i);
            list.add(new ServerPhoto.Address(o.getString("street"), o.getInt("district"), o.getString("city")));
        }
        return list;
    }

    public ToggleLikeResult toggleLike(@NonNull final AutoLogin autoLogin, @NonNull String photoId) throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("autoLogin", autoLogin.getCode());
        params.put("photoId", photoId);
        final JSONObject res = queryJson("toggleLike", params);
        return new ToggleLikeResult(res.getInt("likesCount"), res.getBoolean("isLiked"));
    }

    private void log(@Nullable final AutoLogin autoLogin, @NonNull final String message, @Nullable final Exception exception) throws Exception {
        final Map<String, String> params = new HashMap<>();
        if (autoLogin != null) params.put("autoLogin", autoLogin.getCode());
        params.put("message", message);
        if (exception != null) {
            final StringWriter out = new StringWriter();
            PrintWriter w = new PrintWriter(out);
            w.println("Exception " + exception.getMessage());
            exception.printStackTrace(w);
            w.close();
            final String stackTrace = out.toString();
            final StringBuilder toSent = new StringBuilder();
            for (int i = 0; i < stackTrace.length(); i++) {
                char c = stackTrace.charAt(i);
                switch (c) {
                    case '\n':
                        toSent.append('\\');
                        break;
                    case '\t':
                        toSent.append(' ');
                        break;
                    default:
                        int code = (int) c;
                        if ((code >= 32) && (code < 127)) toSent.append(c);
                        else toSent.append('?');
                }
            }
            params.put("exception", toSent.toString());
        }
        queryJson("log", params);
    }

    private static class ReportTaskParam {
        @Nullable
        private final AutoLogin autoLogin;
        @NonNull
        private final String message;
        @Nullable
        private final Exception exception;

        ReportTaskParam(@Nullable final AutoLogin autoLogin, @NonNull String message, @Nullable Exception exception) {
            this.autoLogin = autoLogin;
            this.message = message;
            this.exception = exception;
        }
    }

    private class LogTask extends AsyncTask<ReportTaskParam, Boolean, Boolean> {
        @Override
        protected Boolean doInBackground(ReportTaskParam... params) {
            try {
                log(params[0].autoLogin, params[0].message, params[0].exception);
                return true;
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    public void startLog(@Nullable final AutoLogin autoLogin, @NonNull String message) {
        startLog(autoLogin, message, null);
    }

    public void startLog(@Nullable final AutoLogin autoLogin, @NonNull String message, @Nullable final Exception e) {
        new LogTask().execute(new ReportTaskParam(autoLogin, message, e));
    }
}
