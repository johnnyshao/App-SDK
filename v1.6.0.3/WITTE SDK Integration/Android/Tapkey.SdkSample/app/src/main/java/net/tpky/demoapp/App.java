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

import android.app.Application;

import net.tpky.mc.TapkeyAppContext;
import net.tpky.mc.TapkeyServiceFactory;
import net.tpky.mc.TapkeyServiceFactoryBuilder;
import net.tpky.mc.broadcast.PushNotificationReceiver;
import net.tpky.mc.concurrent.Async;
import net.tpky.mc.concurrent.AsyncStackTrace;
import net.tpky.mc.concurrent.CancellationToken;
import net.tpky.mc.concurrent.Promise;
import net.tpky.mc.manager.idenitity.IdentityProvider;
import net.tpky.mc.model.Identity;
import net.tpky.mc.model.User;

import java.net.CookieHandler;
import java.net.CookieManager;
import java.net.CookiePolicy;

/*
 * Tapkey expects the Application instance to implement the TapkeyAppContext interface.
 */
public class App extends Application implements TapkeyAppContext {

    static {
        /*
         * Enable generation of meaningful stack traces in async code.
         * This may have performance impact, even if no exceptions are raised but significantly
         * easens debugging. The performance overhead is usually neglictable on newer devices.
         */
        AsyncStackTrace.enableAsyncStateTrace();
    }

//    // development environment
//    public final static String UriGetOAuthToken = "https://wittedigitalapimdev.azure-api.net/v1/app/sdk/GetOAuthToken";
//    public final static String UriGetUniqueId = "https://wittedigitalapimdev.azure-api.net/v1/app/sdk/GetUniqueId";

    // production environment
    public final static String UriGetOAuthToken = "https://wittedigitalapimprod.azure-api.net/v1/app/sdk/GetOAuthToken";
    public final static String UriGetUniqueId = "https://wittedigitalapimprod.azure-api.net/v1/app/sdk/GetUniqueId";

    /*
     * Customer Id
     */
    public final static int MyCustomerId = 0; // TODO: add your customer Id here

    /*
     * User Id
     */
    public final static int MyUserId = 0; // TODO: add your user Id here

    /*
     * SDK Key
     */
    public final static String MySdkKey = ""; // TODO: add your SDK key here

    /*
     * API Subscription Key
     */
    public final static String MySubKey = ""; // TODO: add your API subscription key here

    /*
     * Service Id
     */
    public final static int MyServiceId = 0; // TODO: add your service Id here (e.g. Id for Witte Wave box)

    /*
     * Physical Lock Id
     */
    public final static String MyPhysicalLockId = ""; // TODO: add the Id of your Witte Wave box here e.g. 'C108F094'

    /*
     * The TapkeyServiceFactory holds all needed services
     */
    private TapkeyServiceFactory tapkeyServiceFactory;

    /*
     * WMA identity provider responsible for authenticating and identifying users against the Tapkey back-end
     */
    private WmaIdentityProvider wmaIdentityProvider;

    /*
     * WMA authentication token
     */
    public static String wmaAuthToken = "";

    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * Create an instance of TapkeyServiceFactory. Tapkey expects that a single instance of
         * TapkeyServiceFactory exists inside an application that can be retrieved via the
         * Application instance's getTapkeyServiceFactory() method.
         * The TapkeyServiceFactoryBuilder requires the Google Cloud Messaging Sender Id to
         * register for push notifications.
         * Please use this sender ID.
         */
        TapkeyServiceFactoryBuilder b = new TapkeyServiceFactoryBuilder("520292870727")

        /*
         * Optionally, settings like the backend URI and tenant ID can be changed.
         */
                // Change the backend URI if required.
                //.setServiceBaseUri("https://example.com")

                // The locking devices distributed by WITTE and
                // the corresponding user accounts at the Tapkey back-end live in the WMA tenant
                .setTenantId("wma")

                // Change the SSLContext if required to implement certificate pinning, etc.
                //.setSSLContext(SSLContext.getDefault())

                // The Bluetooth Uuid is fix for the WMA tenant
                .setBleServiceUuid("6e65742e-7470-6ba0-0000-060601810057");

        // Build the TapkeyServiceFactory instance.
        this.tapkeyServiceFactory = b.build(this);

        /*
         * Create an identity provider for the WMA tenant and register it with the Tapkey libraries.
         * This identity provider handles the authentication of Tapkey users in the WMA tenant.
         */
        this.wmaIdentityProvider = new WmaIdentityProvider();
        this.tapkeyServiceFactory.getIdentityProviderRegistration().registerIdentityProvider(this.wmaIdentityProvider.getIpId(), this.wmaIdentityProvider);

        /*
         * The Tapkey libs require cookies to be persisted in order to survive app/device restarts
         * because server authentication involves authentication cookies.
         * Persistence is done using the cookie store provided by the TapkeyServiceFactory instance.
         */
        CookieHandler.setDefault(new CookieManager(tapkeyServiceFactory.getCookieStore(), CookiePolicy.ACCEPT_ORIGINAL_SERVER));

        /*
         * Register PushNotificationReceiver.
         * The PushNotificationReceiver listens for google push notifications and periodically polls
         * for notifications from the Tapkey backend.
         */
        PushNotificationReceiver.register(this, tapkeyServiceFactory.getServerClock());
    }

    @Override
    public TapkeyServiceFactory getTapkeyServiceFactory() {
        return tapkeyServiceFactory;
    }

    public WmaIdentityProvider getWmaIdentityProvider() {
        return wmaIdentityProvider;
    }
}


/*
 * This class represents the WMA identity provider which is needed
 * by the Tapkey SDK to authenticate and log out users.
 */
class WmaIdentityProvider implements IdentityProvider {
    private String ipId = "wma.oauth";

    @Override
    public Promise<Void> logOutAsync(User user, CancellationToken cancellationToken) {

        // add your own code to handle the case that the Tapkey SDK has logged out the current user
        return Async.PromiseFromResult(null);
    }

    @Override
    public Promise<Identity> refreshToken(User user, CancellationToken cancellationToken) {

        // provide a valid authentication token to the Tapkey SDK
        return Async.PromiseFromResult(new Identity(this.ipId, App.wmaAuthToken));
    }

    public String getIpId() {
        return this.ipId;
    }
}
