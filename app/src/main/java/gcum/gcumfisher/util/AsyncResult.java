package gcum.gcumfisher.util;

public class AsyncResult<T> {
    private final T result;
    private final Exception error;

    public T getResult() {
        return result;
    }

    public Exception getError() {
        return error;
    }

    public AsyncResult(T result) {
        this.result = result;
        this.error = null;
    }

    public AsyncResult(Exception error) {
        this.result = null;
        this.error = error;
    }
}
