/* /////////////////////////////////////////////////////////////////////////////////////////////////
//                          Copyright (c) Tapkey GmbH
//
//         All rights are reserved. Reproduction in whole or in part is
//        prohibited without the written consent of the copyright owner.
//    Tapkey reserves the right to make changes without notice at any time.
//   Tapkey makes no warranty, expressed, implied or statutory, including but
//   not limited to any implied warranty of merchantability or fitness for any
//  particular purpose, or that the use will not infringe any third party patent,
//   copyright or trademark. Tapkey must not be liable for any loss or damage
//                            arising from its use.
///////////////////////////////////////////////////////////////////////////////////////////////// */

package net.tpky.demoapp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;

import com.google.gson.Gson;

import net.tpky.mc.concurrent.CancellationTokens;
import net.tpky.mc.manager.UserManager;
import net.tpky.mc.model.Identity;
import net.tpky.mc.model.User;
import net.tpky.mc.utils.Action;
import net.tpky.mc.utils.Func;
import net.tpky.mc.utils.Func1;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * A login screen that offers login via authentication token.
 */
public class LoginActivity extends AppCompatActivity {

    private static final String TAG = LoginActivity.class.getSimpleName();

    private UserManager userManager;
    private WmaIdentityProvider wmaIdentityProvider;

    private boolean signinInProgress;

    private View mProgressView;
    private View mLoginFormView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        App app = (App) getApplication();
        userManager = app.getTapkeyServiceFactory().getUserManager();
        wmaIdentityProvider = app.getWmaIdentityProvider();

        /*
         * Hide the email and password fields because they are not needed now
         */
        AutoCompleteTextView mEmailView = findViewById(R.id.email);
        mEmailView.setVisibility(View.GONE);
        EditText mPasswordView = findViewById(R.id.password);
        mPasswordView.setVisibility(View.GONE);

        /*
         * Obtain the WITTE identifier of the current user and sign in
         */
        Button mEmailSignInButton = findViewById(R.id.email_sign_in_button);
        mEmailSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                // As the sign in process is asynchronous, we track the info, that the process is running
                // (will be started) now, to avoid concurrency issues.
                if (signinInProgress) {
                    return;
                }
                showProgress(true);
                signinInProgress = true;

                // obtain authentication token
                getAuthenticationToken(App.MyCustomerId, App.MyUserId, App.MySdkKey, App.MySubKey, new Func1<String, Void, Exception>() {
                    @Override
                    public Void invoke(String s) throws Exception {

                        // sign in to Tapkey using the authentication token
                        attemptLogin(App.MyUserId);
                        return null;
                    }
                });
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
    }

    /*
     * Attempt to sign in.
     * If there are authentication errors, no actual login attempt is made.
     */
    private void attemptLogin(int userId) {
        // Authenticate the user with the current token against Tapkey.
        try {

            // check if a user already is logged in to Tapkey
            if (userManager.hasUsers()) {
                Log.w(TAG, "User already signed in");
                return;
            }

            // attempt authentication with the current WMA authentication token
            userManager.authenticateAsync(new Identity(wmaIdentityProvider.getIpId(), App.wmaAuthToken), CancellationTokens.None)

                    // when done, continue on the UI thread
                    .continueOnUi(new Func1<User, Void, Exception>() {
                        @Override
                        public Void invoke(User user) throws Exception {
                            Log.d(TAG, "Login successful. User ID: " + user.getId() + ". User name: " + user.getIpUserName());

                            // redirect to main activity
                            Intent intent = new Intent(LoginActivity.this, MainActivity.class);
                            startActivity(intent);
                            finish();
                            return null;
                        }
                    })

                    // handle any exceptions on the UI thread
                    .catchOnUi(new Func1<Exception, Void, Exception>() {
                        @Override
                        public Void invoke(Exception e) throws Exception {
                            Log.e(TAG, "Login failed", e);
                            return null;
                        }
                    })

                    // finally
                    .finallyOnUi(new Action<Exception>() {
                        @Override
                        public void invoke() throws Exception {
                            signinInProgress = false;
                            showProgress(false);
                        }
                    })

                    // make sure, not to miss any exceptions
                    .conclude();
        }
        catch (Exception ex) {
            Log.e(TAG, "Login failed", ex);
        }
    }

    /*
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {

        /*
         * On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
         * for very easy animations. If available, use these APIs to fade-in
         * the progress spinner.
         */
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    /*
     * Obtain an authentication token for the current end user.
     * Note that the authentication token expires after 1h so you need to call this method if required.
     * The customer ID is the WITTE identifier of the customer that
     * the user is assigned to which is usually your own customer.
     * The user ID is the WITTE identifier of the current user.
     * The SDK key is the not used now but will be included later. Pass any value here.
     * The subscription key is the key of your APIM subscription that allows you to access the WITTE SDK.
     */
    private void getAuthenticationToken(
            final int customerId,
            final int userId,
            final String sdkKey,
            final String subKey,
            final Func1<String, Void, Exception> onSuccess) {

        // object containing the parameters for the HTTP request body
        final Map parameters = new HashMap(3);
        parameters.put("CustomerId", customerId);
        parameters.put("UserId", userId);
        parameters.put("SdkKey", sdkKey);

        // WITTE SDK url
        String uriString = App.UriGetOAuthToken;

        // HTTP post task
        @SuppressLint("StaticFieldLeak")
        class PostTask extends AsyncTask<String, Void, String> {

            protected String doInBackground(String... uriString) {
                HttpURLConnection conn = null;
                try {

                    // set up the HTTP request
                    URL url = new URL(uriString[0]);
                    conn = (HttpURLConnection) url.openConnection();
                    conn.setRequestMethod("POST");
                    conn.setRequestProperty("Content-Type","application/json");
                    conn.setRequestProperty("Ocp-Apim-Subscription-Key", subKey);
                    conn.setReadTimeout(30000);
                    conn.setConnectTimeout(30000);
                    conn.setDoInput(true);
                    conn.setDoOutput(true);

                    // write data to the request stream that shall be posted
                    OutputStream os = conn.getOutputStream();
                    os.write(new Gson().toJson(parameters).getBytes("UTF-8"));
                    os.close();

                    // read data from the response stream
                    InputStream in = conn.getInputStream();
                    BufferedReader br = new BufferedReader(new InputStreamReader(in));
                    String line;
                    String responseAsString = "";
                    while ((line = br.readLine()) != null) {
                        responseAsString += line;
                    }
                    in.close();

                    // extract the authentication from the response
                    Map response = new Gson().fromJson(responseAsString, Map.class);
                    String token =  new Gson().fromJson(response.get("Data").toString(), String.class);
                    App.wmaAuthToken = token;

                    return token;
                }
                catch (Exception ex) {
                    Log.e(TAG, "Obtaining authentication token failed: ", ex);
                    return null;
                }
                finally {
                    conn.disconnect();
                }
            }

            protected void onPostExecute(String token) {
                Log.d(TAG, "Obtained authentication token: " + token);
                try {
                    onSuccess.invoke(token);
                }
                catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        };

        // run request
        try {
            new PostTask().execute(uriString);
        }
        catch (Exception ex) {
            Log.e(TAG, "Executing HTTP Post task failed: ", ex);
        }
    }
}

