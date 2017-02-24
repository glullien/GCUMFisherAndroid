package gcum.gcumfisher.util;

import android.os.AsyncTask;

public abstract class AsyncTaskE<Params, Process, Result> extends AsyncTask<Params, Process, AsyncResult<Result>> {

    @Override
    protected final void onPostExecute(AsyncResult<Result> result) {
        if (result.getError() != null) onPostExecuteError(result.getError());
        else onPostExecuteSuccess(result.getResult());
    }

    protected void onPostExecuteSuccess(Result result) {
    }

    protected abstract void onPostExecuteError(Exception error);

    @SafeVarargs
    @Override
    protected final AsyncResult<Result> doInBackground(Params... params) {
        try {
            return new AsyncResult<>(doInBackgroundOrCrash(params));
        } catch (Exception e) {
            return new AsyncResult<>(e);
        }
    }

    protected abstract Result doInBackgroundOrCrash(Params[] params) throws Exception;
}
