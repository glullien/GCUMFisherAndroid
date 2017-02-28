package gcum.gcumfisher.connection;

import android.content.res.Resources;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

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
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import gcum.gcumfisher.R;

public class Server {
    @NonNull
    private final String baseUrl;

    public Server(Resources resources) {
        baseUrl = resources.getString(R.string.base_url);
    }

    @NonNull
    public String getBaseUrl() {
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

    private JSONObject queryJson(String servlet, Map<String, String> params) throws IOException, JSONException {
        return queryJson(servlet, params, null);
    }

    private JSONObject queryJson(String servlet, Map<String, String> params, Part part) throws IOException, JSONException {
        return new JSONObject(readStream(query(servlet, params, part)));
    }

    private InputStream query(@NonNull String servlet, Map<String, String> params, Part part) throws IOException, JSONException {
        URL url = new URL(baseUrl + servlet);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
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
                parts.add(new StringPart(e.getKey(), e.getValue()));
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

        throw new IOException("Http error " + code);
    }

    public AutoLogin getAutoLogin(String username, String password) throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("username", username);
        params.put("password", password);
        final JSONObject res = queryJson("getAutoLogin", params);
        if (res.getString("result").equals("success"))
            return new AutoLogin(res.getString("autoLogin"), res.getString("validTo"));
        else throw new Exception(res.getString("message"));
    }

    public void uploadAndReport(@NonNull final AutoLogin autoLogin, @NonNull final String street, final int district, @NonNull final long date, @Nullable Point point, @NonNull String path) throws Exception {
        final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE);
        dateFormat.setTimeZone(TimeZone.getTimeZone("Europe/Paris"));
        final Map<String, String> params = new HashMap<>();
        params.put("autoLogin", autoLogin.getCode());
        params.put("street", street);
        params.put("district", Integer.toString(district));
        params.put("date", dateFormat.format(new Date(date)));
        if (point != null) {
            params.put("latitude", Long.toString(point.getLatitude()));
            params.put("longitude", Long.toString(point.getLongitude()));
        }
        final File file = new File(path);
        final FilePart part = new FilePart(file.getName(), file, "image/jpeg", "UTF-8");
        JSONObject res = queryJson("uploadAndReport", params, part);
        if (!res.getString("result").equals("success"))
            throw new Exception(res.getString("message"));
    }

    public List<Point> getPoints() throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("zone", "All");
        params.put("timeFrame", "All");
        params.put("locationSources", "Street,Device");
        params.put("authors", "-All-");
        final JSONObject res = queryJson("getPoints", params);
        if (!res.getString("result").equals("success"))
            throw new Exception(res.getString("message"));
        final JSONArray photos = res.getJSONArray("photos");
        final List<Point> list = new ArrayList<>(photos.length());
        for (int i = 0; i < photos.length(); i++) {
            JSONObject o = photos.getJSONObject(i);
            list.add(new Point(o.getLong("latitude"), o.getLong("longitude")));
        }
        return list;
    }

    public List<ServerPhoto> getList(int number, @Nullable String start) throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("district", "All");
        params.put("start", (start == null) ? "Latest" : start);
        params.put("number", Integer.toString(number));
        final JSONObject res = queryJson("getList", params);
        if (!res.getString("result").equals("success"))
            throw new Exception(res.getString("message"));
        return getServerPhotos(res.getJSONArray("photos"));
    }

    public List<ServerPhoto> getPointInfo(Point point) throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("latitude", Long.toString(point.getLatitude()));
        params.put("longitude", Long.toString(point.getLongitude()));
        params.put("timeFrame", "All");
        params.put("locationSources", "Street,Device");
        params.put("authors", "-All-");
        final JSONObject res = queryJson("getPointInfo", params);
        if (!res.getString("result").equals("success"))
            throw new Exception(res.getString("message"));
        return getServerPhotos(res.getJSONArray("photos"));
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

    public InputStream getPhotoInputStream(String id, int maxSize) throws Exception {
        URL url = new URL(baseUrl + "getPhoto?id=" + id + "&maxSize=" + maxSize);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setDoInput(true);
        conn.connect();
        return conn.getInputStream();
    }

    @NonNull
    public List<ServerPhoto.Address> searchAddress(@NonNull String pattern, int nbAnswers) throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("pattern", pattern);
        params.put("nbAnswers", Integer.toString(nbAnswers));
        final JSONObject res = queryJson("searchAddress", params);
        if (!res.getString("result").equals("success"))
            throw new Exception(res.getString("message"));
        return getAddresses(res.getJSONArray("streets"));
    }

    @NonNull
    public List<ServerPhoto.Address> searchClosest(@NonNull Point point, int nb) throws Exception {
        final Map<String, String> params = new HashMap<>();
        params.put("latitude", Long.toString(point.getLatitude()));
        params.put("longitude", Long.toString(point.getLongitude()));
        params.put("nb", Integer.toString(nb));
        final JSONObject res = queryJson("searchClosest", params);
        if (!res.getString("result").equals("success"))
            throw new Exception(res.getString("message"));
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
        if (!res.getString("result").equals("success"))
            throw new Exception(res.getString("message"));
        return new ToggleLikeResult(res.getInt("likesCount"), res.getBoolean("isLiked"));
    }
}
