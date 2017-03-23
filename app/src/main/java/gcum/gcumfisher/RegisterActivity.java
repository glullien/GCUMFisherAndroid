package gcum.gcumfisher;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import gcum.gcumfisher.connection.AutoLogin;
import gcum.gcumfisher.connection.Server;
import gcum.gcumfisher.connection.ServerReturnedErrorException;
import gcum.gcumfisher.util.AsyncTaskE;


public class RegisterActivity extends Activity {

    private Server server;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.register);
        server = new Server(getResources());

        ((TextView) findViewById(R.id.info)).setText(server.getBaseUrls());
    }

    public void register(View view) {
        final String username = ((EditText) findViewById(R.id.usernameInput)).getText().toString();
        final String password = ((EditText) findViewById(R.id.passwordInput)).getText().toString();
        final String passwordCheck = ((EditText) findViewById(R.id.passwordCheckInput)).getText().toString();
        final String email = ((EditText) findViewById(R.id.emailInput)).getText().toString();
        if (!username.matches("[a-zA-Z\\d_]{1,20}"))
            setStatus(getString(R.string.invalid_username), R.color.error);
        else if (!password.matches(".{6,20}"))
            setStatus(getString(R.string.invalid_password), R.color.error);
        else if (!password.equals(passwordCheck))
            setStatus(getString(R.string.invalid_password_check), R.color.error);
        else if ((email.length() > 0) && !email.matches(".*@.*"))
            setStatus(getString(R.string.invalid_email), R.color.error);
        else {
            setStatus(getString(R.string.testing, username), R.color.progress);
            findViewById(R.id.register).setEnabled(false);
            new Register(username, password, email).execute();
        }
    }

    class Register extends AsyncTaskE<Boolean, Boolean, AutoLogin> {
        final String username;
        final String password;
        final String email;

        Register(String username, String password, String email) {
            this.username = username;
            this.password = password;
            this.email = email;
        }

        @Override
        protected void onPostExecuteSuccess(AutoLogin result) {
            findViewById(R.id.register).setEnabled(true);
            LoginActivity.connect(getApplicationContext(), result);
            setResult(RESULT_OK);
            finish();
        }

        @Override
        protected void onPostExecuteError(Exception error) {
            if (error instanceof ServerReturnedErrorException) {
                String code = ((ServerReturnedErrorException) error).getCode();
                if ("USERNAME_ALREADY_USED".equals(code))
                    setStatus(getResources().getString(R.string.username_already_used), R.color.error);
                else
                    setStatus(getResources().getString(R.string.error_message, error.getMessage()), R.color.error);
            } else
                setStatus(getResources().getString(R.string.error_message, error.getMessage()), R.color.error);
            findViewById(R.id.register).setEnabled(true);
        }

        @Override
        protected AutoLogin doInBackgroundOrCrash(Boolean[] params) throws Exception {
            return server.getAutoLogin(username, password, email, true);
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
