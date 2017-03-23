package gcum.gcumfisher;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import gcum.gcumfisher.connection.Server;
import gcum.gcumfisher.connection.ServerReturnedErrorException;
import gcum.gcumfisher.util.AsyncTaskE;

public class ForgotEmailActivity extends Activity {

    private Server server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.forgot_email);
        server = new Server(getResources());

        ((TextView) findViewById(R.id.info)).setText(server.getBaseUrls());
    }

    public void sendPassword(View view) {
        final String email = ((EditText) findViewById(R.id.emailInput)).getText().toString();
        if (!email.matches(".*@.*"))
            setStatus(getString(R.string.invalid_email), R.color.error);
        else {
            setStatus(getString(R.string.testing, email), R.color.progress);
            findViewById(R.id.send_password).setEnabled(false);
            new ForgotEmail(email).execute();
        }
    }

    class ForgotEmail extends AsyncTaskE<Boolean, Boolean, Boolean> {
        final String email;

        ForgotEmail(String email) {
            this.email = email;
        }

        @Override
        protected void onPostExecuteSuccess(Boolean result) {
            new AlertDialog.Builder(ForgotEmailActivity.this).
                    setTitle(R.string.success).setMessage(R.string.email_sent).
                    setIcon(android.R.drawable.ic_dialog_alert).
                    setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            setResult(RESULT_OK);
                            finish();
                        }
                    }).
                    show();
        }

        @Override
        protected void onPostExecuteError(Exception error) {
            if (error instanceof ServerReturnedErrorException) {
                String code = ((ServerReturnedErrorException) error).getCode();
                if ("EMAIL_NOT_FOUND".equals(code))
                    setStatus(getResources().getString(R.string.email_not_found), R.color.error);
                else
                    setStatus(getResources().getString(R.string.error_message, error.getMessage()), R.color.error);
            } else
                setStatus(getResources().getString(R.string.error_message, error.getMessage()), R.color.error);
            findViewById(R.id.send_password).setEnabled(true);
        }

        @Override
        protected Boolean doInBackgroundOrCrash(Boolean[] params) throws Exception {
            server.sendID(email);
            return true;
        }
    }

    void setStatus(String status, int color) {
        TextView textView = ((TextView) findViewById(R.id.status));
        textView.setText(status);
        textView.setTextColor(getResources().getColor(color));
    }

    public void cancel(View view) {
        setResult(RESULT_CANCELED);
        finish();
    }

}
