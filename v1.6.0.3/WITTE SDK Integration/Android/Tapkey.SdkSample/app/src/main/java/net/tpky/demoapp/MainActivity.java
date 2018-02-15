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

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.NavigationView;
import android.support.v4.view.GravityCompat;
import android.support.v4.widget.DrawerLayout;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import com.google.gson.Gson;

import net.tpky.mc.TapkeyServiceFactory;
import net.tpky.mc.concurrent.CancellationTokens;
import net.tpky.mc.manager.ConfigManager;
import net.tpky.mc.manager.UserManager;
import net.tpky.mc.model.User;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements NavigationView.OnNavigationItemSelectedListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    private UserManager userManager;
    private WmaIdentityProvider wmaIdentityProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * Retrieve the TapkeyServiceFactory singleton.
         */
        App app = (App) getApplication();
        TapkeyServiceFactory tapkeyServiceFactory = app.getTapkeyServiceFactory();

        /*
         * Retrieve manager instances that handles Tapkey SDK functionality.
         */
        ConfigManager configManager = tapkeyServiceFactory.getConfigManager();
        userManager = tapkeyServiceFactory.getUserManager();
        wmaIdentityProvider = app.getWmaIdentityProvider();

        /*
         * Check if a user is signed in to Tapkey. If not, redirect to login LoginActivity.
         */
        if (!userManager.hasUsers()){
            Intent intent = new Intent(this, LoginActivity.class);
            startActivity(intent);
            finish();
            return;
        }

        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawer, toolbar, R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        NavigationView navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        View headerView = navigationView.getHeaderView(0);
        TextView usernameTextView = (TextView) headerView.findViewById(R.id.nav_header__username);

        /*
         * Get the username of first user signed to Tapkey. The first user is used here,
         * because we don't support multi-user.
         */
        User firstUser = userManager.getFirstUser();
        if(firstUser != null){
            usernameTextView.setText(firstUser.getIpUserName());
        }
    }

    @Override
    public void onBackPressed() {
        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {

        // Handle navigation view item clicks here.
        int id = item.getItemId();

        switch (id){

            case R.id.nav__sign_out:
                signOut();
                break;

            case R.id.nav__about:
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                break;
        }

        DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        drawer.closeDrawer(GravityCompat.START);
        return true;
    }

    /*
     * Sign out user from Tapkey
     */
    private void signOut(){

        /*
         * To sign out user, get all users and sign them out all of them.
         */
        List<User> users = userManager.getUsers();
        for(User user : users){
            userManager.logOff(user, CancellationTokens.None);
            wmaIdentityProvider.logOutAsync(user, CancellationTokens.None);
        }

        /*
         * Redirect to LoginActivity
         */
        Intent intent = new Intent(this, LoginActivity.class);
        startActivity(intent);
        finish();
    }

    /*
     * Obtain the unique lock ID of the given WITTE service ID of the desired locking device.
     * The customer ID is the WITTE identifier of the customer that
     * the user is assigned to which is usually your own customer.
     * The service ID is the WITTE identifier of the desired locking device.
     * The SDK key is the not used now but will be included later. Pass any value here.
     * The subscription key is the key of your APIM subscription that allows you to access the WITTE SDK.
     */
    public void getLockUniqueId(final int _customerId, final int _serviceId, final String _sdkKey, final String _subKey) {

        // object containing the parameters for the HTTP request body
        final Map parameters = new HashMap(3);
        parameters.put("CustomerId", _customerId);
        parameters.put("ServiceId", _serviceId);
        parameters.put("Sdkey", _sdkKey);

        // WITTE SDK url
        String uriString = App.witteSdkUri + "/GetUniqueId";

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
                    conn.setRequestProperty("Ocp-Apim-Subscription-Key", _subKey);
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

                    // extract the lock ID from the response
                    Map response = new Gson().fromJson(responseAsString, Map.class);
                    return new Gson().fromJson(response.get("Data").toString(), String.class);
                }
                catch (Exception ex) {
                    Log.e(TAG, "Obtaining unique lock ID failed: ", ex);
                    return null;
                }
                finally {
                    conn.disconnect();
                }
            }

            protected void onPostExecute(String token) {
                Log.d(TAG, "Obtained unique lock ID: " + token);
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
