package gcum.gcumfisher.connection;

import java.io.IOException;

class HttpErrorCodeException extends IOException {
    HttpErrorCodeException(int code) {
        super("Http returned error code: " + code);
    }
}
