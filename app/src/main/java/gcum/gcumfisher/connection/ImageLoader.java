package gcum.gcumfisher.connection;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;

public class ImageLoader {

    public interface Progress {
        void on(int value);
    }

    public static byte[] load(@NonNull HttpURLConnection connection) throws IOException {
        return load(connection, null);
    }

    public static byte[] load(@NonNull HttpURLConnection connection, @Nullable Progress progress) throws IOException {
        final int length = connection.getContentLength();
        final byte[] bytes;
        if (length == -1) {
            final ByteArrayOutputStream boas = new ByteArrayOutputStream();
            final InputStream i = connection.getInputStream();
            byte[] buffer = new byte[512];
            try {
                boolean eof = false;
                while (!eof) {
                    int r = i.read(buffer);
                    if (r < 0) eof = true;
                    else boas.write(buffer, 0, r);
                }
            } finally {
                i.close();
                boas.close();
            }
            bytes = boas.toByteArray();
        } else {
            bytes = new byte[length];
            final InputStream i = connection.getInputStream();
            try {
                int read = 0;
                while (read < length) {
                    int r = i.read(bytes, read, 512);
                    if (r < 0) throw new IOException("EOF reached");
                    read += r;
                    if (progress != null) progress.on(read * 100 / length);
                }
            } finally {
                i.close();
            }
        }
        return bytes;
    }
}
