package gcum.gcumfisher;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import gcum.gcumfisher.connection.AutoLogin;
import gcum.gcumfisher.connection.Server;

public class LoginActivity extends Activity {

    public static final int LOGIN = 1;
    public static final int CANCELED = 2;
    private static final String PREFERENCES = "gcum.gcumfisher.LOGIN_PREFERENCES";
    public static final String KEY_CODE = "autoLoginCode";
    public static final String KEY_VALID_TO = "autoLoginValidTo";

    private static final int REGISTER_REQUEST = 1;
    private static final int FORGOT_IDS_REQUEST = 2;

    private Server server;

    static class TestingResult {
        private final AutoLogin autoLogin;
        private final String error;

        TestingResult(AutoLogin autoLogin, String error) {
            this.autoLogin = autoLogin;
            this.error = error;
        }
    }

    class Testing extends AsyncTask<String, Boolean, TestingResult> {
        private final Credentials credentials;

        Testing(Credentials credentials) {
            this.credentials = credentials;
        }

        @Override
        protected void onPostExecute(TestingResult res) {
            if (res.autoLogin != null) {
                connect(getApplicationContext(), res.autoLogin);
                setResult(LOGIN);
                finish();
            } else {
                setStatus(res.error, R.color.error);
                findViewById(R.id.login).setEnabled(true);
            }
        }

        @Override
        protected TestingResult doInBackground(String... params) {
            try {
                return new TestingResult(server.getAutoLogin(credentials.username, credentials.password), null);
            } catch (Exception e) {
                e.printStackTrace();
                return new TestingResult(null, e.getMessage());
            }
        }
    }

    private static class Credentials {
        final String username;
        final String password;

        Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    static void connect(@NonNull Context context, @NonNull AutoLogin autoLogin) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.putString(KEY_CODE, autoLogin.getCode());
        editor.putString(KEY_VALID_TO, autoLogin.getValidTo());
        editor.commit();
    }

    static void disconnect(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(KEY_CODE);
        editor.remove(KEY_VALID_TO);
        editor.commit();
    }

    @Nullable
    static AutoLogin getAutoLogin(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String code = sharedPref.getString(KEY_CODE, null);
        String validTo = sharedPref.getString(KEY_VALID_TO, null);
        return ((code == null) || (validTo == null)) ? null : new AutoLogin(code, validTo);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);
        server = new Server(getResources());

        ((TextView) findViewById(R.id.info)).setText(server.getBaseUrls());
    }

    public void login(View view) {
        final String username = ((EditText) findViewById(R.id.usernameInput)).getText().toString();
        final String password = ((EditText) findViewById(R.id.passwordInput)).getText().toString();
        if (!username.matches("[a-zA-Z\\d_]{1,20}"))
            setStatus(getString(R.string.invalid_username), R.color.error);
        else if (!password.matches(".{6,20}"))
            setStatus(getString(R.string.invalid_password), R.color.error);
        else {
            setStatus(getString(R.string.testing, username), R.color.progress);
            findViewById(R.id.login).setEnabled(false);
            new Testing(new Credentials(username, password)).execute();
        }
    }

    void setStatus(String status, int color) {
        TextView textView = ((TextView) findViewById(R.id.status));
        textView.setText(status);
        textView.setTextColor(getResources().getColor(color));
    }

    public void cancel(View view) {
        setResult(CANCELED);
        finish();
    }

    public void register(View view) {
        startActivityForResult(new Intent(this, RegisterActivity.class), REGISTER_REQUEST);
    }

    public void forgot_email(View view) {
        startActivityForResult(new Intent(this, ForgotEmailActivity.class), FORGOT_IDS_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REGISTER_REQUEST:
                if (resultCode == RESULT_OK) {
                    setResult(LOGIN);
                    finish();
                }
                break;
            case FORGOT_IDS_REQUEST:
                break;
        }
    }

}
