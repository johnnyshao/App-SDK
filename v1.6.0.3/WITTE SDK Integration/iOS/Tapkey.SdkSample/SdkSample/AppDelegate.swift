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


import UIKit
import TapkeyMobileLib

@UIApplicationMain
class AppDelegate: UIResponder, UIApplicationDelegate, TapkeyAppDelegate {

    var window: UIWindow?
    
    // current WMA authentication token
    public var wmaToken: String = "";

    fileprivate var tapkeyServiceFactory: TapkeyServiceFactory?;
    fileprivate var wmaIdentityProvider: WmaIdentityProvider?;

    func application(_ application: UIApplication,
                     willFinishLaunchingWithOptions launchOptions: [UIApplicationLaunchOptionsKey : Any]? = nil) -> Bool {
        
        // Create a Google Cloud Messaging Adapter to allow Tapkey to register and
        // receive push notification
        //
        // To receive push notification in development builds, the sandbox flag must be set to true.
        //
        let gcmAdapter = TkGcmAdapterImpl(sandbox: true);
        
        // Build service factory singleton instance and change settings
        let tapkeyServiceFactoryBuilder = TapkeyServiceFactoryBuilder()
            .setTenantId(tenantId: "wma")
            .setServiceBaseUri(serviceBaseUri: "https://my.tapkey.com")
            .setBleServiceUuid(bleServiceUuid: "6e65742e-7470-6ba0-0000-060601810057");
        let tapkeyServiceFactory: TapkeyServiceFactory = tapkeyServiceFactoryBuilder.build(application: application, gcmAdapter: gcmAdapter);
        self.tapkeyServiceFactory = tapkeyServiceFactory;

        // Register the singleton instance. This will set up cookie handling, so Tapkey can psersist received
        // authentication cookies.
        registerTapkeyServiceFactory(tapkeyServiceFactory);
        
        //
        // Create and register IdentityProviders
        //
        self.wmaIdentityProvider = WmaIdentityProvider();
        _ = tapkeyServiceFactory.getIdentityProviderRegistration().registerIdentityProvider(ipId: (wmaIdentityProvider?.getIpId())!, identityProvider: wmaIdentityProvider!);
        
        // Find out, whether a user is logged in. If not, go to SignIn View
        let userManager = tapkeyServiceFactory.getUserManager();
        if(!userManager.hasUsers()){
            self.window = UIWindow(frame: UIScreen.main.bounds);
            let mainStoryboard: UIStoryboard = UIStoryboard(name: "Main", bundle: nil);
            let signInController = mainStoryboard.instantiateViewController(withIdentifier: "SignInViewController") as! SignInViewController;
            self.window?.rootViewController = signInController;
            self.window?.makeKeyAndVisible();
        }
        
        // obtain an authentication of the current user for authenticating against Tapkey
        self.getAuthenticationToken(customerId: 3, userId: 112, sdkKey: "", subKey: "036f3656d89c49eaaa8c4dc8cf52aa29");
        
        // obtain the unique ID of some box service, e.g. "C1-55-1A-0B"
        self.getLockUniqueId(customerId: 3, serviceId: 13, sdkKey: "", subKey: "036f3656d89c49eaaa8c4dc8cf52aa29");
        
        return true;
    }
    
    func application(_ application: UIApplication, didFinishLaunchingWithOptions launchOptions: [UIApplicationLaunchOptionsKey: Any]?) -> Bool {
        // Override point for customization after application launch.
        
        return true
    }
    
    
    // Forward APN registration callbacks to Tapkey. This is required for push notifications function properly.
    func application( _ application: UIApplication, didRegisterForRemoteNotificationsWithDeviceToken
        deviceToken: Data ) {

        guard let cloudMessageManager = self.tapkeyServiceFactory?.getCloudMessagingManager() else {
            return;
        }
                
        cloudMessageManager.didRegisterForRemoteNotificationsWithDeviceToken(deviceToken);
    }
    
    // Forward APN registration callbacks to Tapkey. This is required for push notifications function properly.
    func application(_ application: UIApplication, didFailToRegisterForRemoteNotificationsWithError error: Error) {
        
        guard let cloudMessageManager = self.tapkeyServiceFactory?.getCloudMessagingManager() else {
            return;
        }
        
        cloudMessageManager.didFailToRegisterForRemoteNotificationsWithError(error);
    }
    
    // Forward APN push notifications to Tapkey. This is required for push notifications function properly.
    func application( _ application: UIApplication,
                      didReceiveRemoteNotification userInfo: [AnyHashable: Any],
                      fetchCompletionHandler handler: @escaping (UIBackgroundFetchResult) -> Void) {
        
        
        if let cloudMessageManager = self.tapkeyServiceFactory?.getCloudMessagingManager() {
            
            cloudMessageManager.didReceiveRemoteNotification(userInfo as [AnyHashable: Any], fetchCompletionHandler: handler, cancellationToken: TkCancellationToken_None);
            
        }else{
            handler(UIBackgroundFetchResult.failed);
        }
        
    }
    
    // Backgroung fetch
    func application(_ application: UIApplication, performFetchWithCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void) {
        
        // Let Tapkey poll for notifications. This is redundant to push notifications and is implemented as safety
        // measrue for the case, push notifications get lost.
        // Run the code via runAsyncInBackground to prevent app from sleeping while fetching is in progress.
        runAsyncInBackground(application, promise: self.tapkeyServiceFactory!.getPollingManager().poll(with:JavaUtilHashSet(), with: NetTpkyMcConcurrentCancellationTokens_None)
            .finallyOnUi({
                completionHandler(UIBackgroundFetchResult.newData);
            })
        );
        
    }

    func applicationWillResignActive(_ application: UIApplication) {
        // Sent when the application is about to move from active to inactive state. This can occur for certain types of temporary interruptions (such as an incoming phone call or SMS message) or when the user quits the application and it begins the transition to the background state.
        // Use this method to pause ongoing tasks, disable timers, and invalidate graphics rendering callbacks. Games should use this method to pause the game.
    }

    func applicationDidEnterBackground(_ application: UIApplication) {
        // Use this method to release shared resources, save user data, invalidate timers, and store enough application state information to restore your application to its current state in case it is terminated later.
        // If your application supports background execution, this method is called instead of applicationWillTerminate: when the user quits.
    }

    func applicationWillEnterForeground(_ application: UIApplication) {
        // Called as part of the transition from the background to the active state; here you can undo many of the changes made on entering the background.
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        // Restart any tasks that were paused (or not yet started) while the application was inactive. If the application was previously in the background, optionally refresh the user interface.
    }

    func applicationWillTerminate(_ application: UIApplication) {
        // Called when the application is about to terminate. Save data if appropriate. See also applicationDidEnterBackground:.
    }
    
    // obtain an authentication token for the current end user
    public func getAuthenticationToken(customerId: Int, userId: Int, sdkKey: String, subKey: String) {
        let parameters: NSDictionary = [
            "customerId": customerId,
            "userId": userId,
            "sdkKey": sdkKey
        ];
        let url = URL(string: "https://wittedigitalapimdev.azure-api.net/v1/app/sdk/GetOAuthToken");
        var request = URLRequest(url: url!);
        request.httpMethod = "POST";
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: parameters, options: JSONSerialization.WritingOptions.prettyPrinted);
        } catch let error {
            NSLog("Serializing as JSON failed: " + error.localizedDescription);
        }
        request.addValue("application/json", forHTTPHeaderField: "Content-Type");
        request.addValue(subKey, forHTTPHeaderField: "Ocp-Apim-Subscription-Key");
        NSURLConnection.sendAsynchronousRequest(request, queue: OperationQueue.main) { (response: URLResponse?, data: Data?, error: Error?) in
            let responseString = NSString(bytes: NSData(data: data!).bytes, length: (data?.count)!, encoding: String.Encoding.utf8.rawValue);
            NSLog("Response string: " + (responseString?.description)!);
            do {
                let responseDict = try JSONSerialization.jsonObject(with: data!, options: JSONSerialization.ReadingOptions.allowFragments) as! NSDictionary;
                let responseDataField = responseDict.object(forKey: "Data") as! NSString;
                let token = try JSONSerialization.jsonObject(with: responseDataField.data(using: String.Encoding.utf8.rawValue)!, options: JSONSerialization.ReadingOptions.allowFragments) as! NSString;
                NSLog("Obtained token: " + (token as String));
                self.wmaToken = token as String;
            } catch let error {
                NSLog("Deserializing from JSON failed: " + error.localizedDescription);
            }
        };
    }
    
    // obtain the unique ID of the given lock device service ID
    public func getLockUniqueId(customerId: Int, serviceId: Int, sdkKey: String, subKey: String) {
        let parameters: NSDictionary = [
            "customerId": customerId,
            "serviceId": serviceId,
            "sdkKey": sdkKey
        ];
        let url = URL(string: "https://wittedigitalapimdev.azure-api.net/v1/app/sdk/GetUniqueId");
        var request = URLRequest(url: url!);
        request.httpMethod = "POST";
        do {
            request.httpBody = try JSONSerialization.data(withJSONObject: parameters, options: JSONSerialization.WritingOptions.prettyPrinted);
        } catch let error {
            NSLog("Serializing as JSON failed: " + error.localizedDescription);
        }
        request.addValue("application/json", forHTTPHeaderField: "Content-Type");
        request.addValue(subKey, forHTTPHeaderField: "Ocp-Apim-Subscription-Key");
        NSURLConnection.sendAsynchronousRequest(request, queue: OperationQueue.main) { (response: URLResponse?, data: Data?, error: Error?) in
            let responseString = NSString(bytes: NSData(data: data!).bytes, length: (data?.count)!, encoding: String.Encoding.utf8.rawValue);
            NSLog("Response string: " + (responseString?.description)!);
            do {
                let responseDict = try JSONSerialization.jsonObject(with: data!, options: JSONSerialization.ReadingOptions.allowFragments) as! NSDictionary;
                let responseDataField = responseDict.object(forKey: "Data") as! NSString;
                let token = try JSONSerialization.jsonObject(with: responseDataField.data(using: String.Encoding.utf8.rawValue)!, options: JSONSerialization.ReadingOptions.allowFragments) as! NSString;
                NSLog("Obtained unique ID: " + (token as String));
                self.wmaToken = token as String;
            } catch let error {
                NSLog("Deserializing from JSON failed: " + error.localizedDescription);
            }
        };
    }

    public func getTapkeyServiceFactory() -> TapkeyServiceFactory {
        return self.tapkeyServiceFactory!;
    }
    
    public func getWmaIdentityProvider() -> WmaIdentityProvider {
        return self.wmaIdentityProvider!;
    }
}

// WMA identity provider class for WMA authentication
class WmaIdentityProvider: TkIdenitityProvider {
    
    var ipId: String = "wma.oauth";
    let app: AppDelegate = UIApplication.shared.delegate as! AppDelegate;
    
    // override from TkIdenitityProvider
    func refreshToken(user: NetTpkyMcModelUser, cancellationToken: TkCancellationToken) -> TkPromise<NetTpkyMcModelIdentity> {
        let identity = TkModelIdentity(nsString: ipId, with: app.wmaToken);
        return NetTpkyMcConcurrentAsync_PromiseFromResultWithId_(identity) as! TkPromise<NetTpkyMcModelIdentity>;
    }
    
    // override from TkIdenitityProvider
    func logOut(user: NetTpkyMcModelUser, cancellationToken: TkCancellationToken) -> TkPromise<Void> {
        return NetTpkyMcConcurrentAsync_PromiseFromResultWithId_(nil) as! TkPromise<Void>;
    }
    
    // return IP ID of this identity provider instance
    public func getIpId() -> String {
        return ipId;
    }
}
