package gcum.gcumfisher;


import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import java.util.List;

public class LoginActivity extends Activity {

    public static final int LOGIN = 1;
    public static final int CANCELED = 2;
    private static final String PREFERENCES = "gcum.gcumfisher.LOGIN_PREFERENCES";
    public static final String KEY_USERNAME = "username";
    public static final String KEY_PASSWORD = "password";

    class Testing extends AsyncTask<String, Boolean, Boolean> {
        private final Credentials credentials;

        Testing(Credentials credentials) {
            this.credentials = credentials;
        }

        @Override
        protected void onPostExecute(Boolean res) {
            if (res) {
                SharedPreferences sharedPref = getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = sharedPref.edit();
                editor.putString(KEY_USERNAME, credentials.username);
                editor.putString(KEY_PASSWORD, credentials.password);
                editor.commit();
                setResult(LOGIN);
                finish();
            } else {
                setStatus(getString(R.string.error), R.color.error);
                findViewById(R.id.login).setEnabled(true);
            }
        }

        @Override
        protected Boolean doInBackground(String... params) {
            try {
                WebDavAccess access = new WebDavAccess(getResources(), credentials);
                List<String> files = access.dir(getString(R.string.webdav_dir));
                return !files.isEmpty();
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }
    }

    static class Credentials {
        final String username;
        final String password;

        Credentials(String username, String password) {
            this.username = username;
            this.password = password;
        }
    }

    static void disconnect(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPref.edit();
        editor.remove(KEY_USERNAME);
        editor.remove(KEY_PASSWORD);
        editor.commit();
    }

    @Nullable
    static Credentials getCredentials(@NonNull Context context) {
        SharedPreferences sharedPref = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String username = sharedPref.getString(KEY_USERNAME, null);
        String password = sharedPref.getString(KEY_PASSWORD, null);
        return ((username == null) ||(password==null))?null:new Credentials(username, password);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login);
    }

    public void login(View view) {
        String username = ((EditText) findViewById(R.id.usernameInput)).getText().toString();
        String password = ((EditText) findViewById(R.id.passwordInput)).getText().toString();
        setStatus(getString(R.string.testing, username), R.color.progress);
        findViewById(R.id.login).setEnabled(false);
        new Testing(new Credentials(username, password)).execute();
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

}
