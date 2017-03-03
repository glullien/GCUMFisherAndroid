package gcum.gcumfisher.connection;


import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

public class ServerReturnedErrorException extends Exception {

    @Nullable
    final String code;

    ServerReturnedErrorException(@NonNull String message, @Nullable String code) {
        super(message);
        this.code = code;
    }

    @Nullable
    public String getCode() {
        return code;
    }
}
