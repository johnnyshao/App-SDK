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


import Foundation
import UIKit
import TapkeyMobileLib

class KeyChainViewController : UITableViewController{
    
    var keyManager: TkKeyManager?;
    var userManager: TkUserManager?;
    var bleLockManager: TkBleLockManager?;
    var commandExecutionFacade: TkCommandExecutionFacade?;
    var configManager: TkConfigManager?;
    var firmwareManager: TkFirmwareManager?;
    
    var observer: TkObserver<Void>?;
    var keyObserverRegistration: TkObserverRegistration?;
    
    var bluetoothObserver: TkObserver<AnyObject>?;
    var bluetoothObserverRegistration: TkObserverRegistration?;
    
    var bluetoothStateObserver: TkObserver<NetTpkyMcModelBluetoothState>?;
    var bluetoothStateObserverRegistration: TkObserverRegistration?;
    
    var scanInProgress: Bool = false;
    var keys: [NetTpkyMcModelWebviewCachedKeyInformation] = [];
    
    var currentFwUpgradeChunkIndex : Int32 = 0;

    
    override func viewDidLoad() {
     
        let app:AppDelegate = UIApplication.shared.delegate as! AppDelegate;
        let tapkeyServiceFactory:TapkeyServiceFactory = app.getTapkeyServiceFactory();
        self.keyManager = tapkeyServiceFactory.getKeyManager();
        self.userManager = tapkeyServiceFactory.getUserManager();
        self.bleLockManager = tapkeyServiceFactory.getBleLockManager();
        self.commandExecutionFacade = tapkeyServiceFactory.getCommandExecutionFacade();
        self.configManager = tapkeyServiceFactory.getConfigManager();
        self.firmwareManager = tapkeyServiceFactory.getFirmwareManager();
        
        self.observer = TkObserver({ (aVoid:Void?) in self.reloadLocalKeys() })
        self.bluetoothObserver = TkObserver({ (any:AnyObject?) in self.refreshView(); });
        self.bluetoothStateObserver = TkObserver({ (newBluetoothState: NetTpkyMcModelBluetoothState?) in
        
            let bluetoothState:NetTpkyMcModelBluetoothState = newBluetoothState ?? NetTpkyMcModelBluetoothState.no_BLE();
            
            /*
             * When bluetooth is enabled and scan not in progress yet, start scan
             */
            if (!self.scanInProgress && bluetoothState == NetTpkyMcModelBluetoothState.bluetooth_ENABLED()){
                
                self.scanInProgress = true;
                self.bleLockManager!.startForegroundScan();
                
            /*
             * Whe bluetooth is not enabled and scan in progress, stop scan
             */
            } else if (self.scanInProgress && bluetoothState != NetTpkyMcModelBluetoothState.bluetooth_ENABLED()){
                
                self.bleLockManager!.stopForegroundScan();
                self.scanInProgress = false;
                
            }
            
            self.refreshView();
        });

        NotificationCenter.default.addObserver(self, selector: #selector(KeyChainViewController.viewInForeground), name:NSNotification.Name.UIApplicationWillEnterForeground, object: nil)
        NotificationCenter.default.addObserver(self, selector: #selector(KeyChainViewController.viewInBackground), name:NSNotification.Name.UIApplicationDidEnterBackground, object: nil)
        

    }
    
    override func viewDidAppear(_ animated: Bool) {
        self.viewInForeground();
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        self.viewInBackground();
    }
    
    @objc private func viewInForeground() {
        
        if(self.keyObserverRegistration == nil){
            self.keyObserverRegistration = self.keyManager!.getKeyUpdateObserveable().addObserver(self.observer!);
        }
        
        if(self.bluetoothObserverRegistration == nil){
            self.bluetoothObserverRegistration = self.bleLockManager!.getLocksChangedObservable().addObserver(self.bluetoothObserver!);
        }
        
        if(self.bluetoothStateObserverRegistration == nil){
            self.bluetoothStateObserverRegistration = self.configManager!.observerBluetoothState().addObserver(self.bluetoothStateObserver!);
        }

        // Start scan, if not in progress and bluetooth is enabled
        if(!self.scanInProgress){
            if(self.configManager?.getBluetoothState() == NetTpkyMcModelBluetoothState.bluetooth_ENABLED()){
                self.scanInProgress = true;
                self.bleLockManager!.startForegroundScan();
            }
        }
        
        reloadLocalKeys();
        
        // test desired functions after shortly waiting that box has been found by BLE scan
        NetTpkyMcConcurrentAsync.delay(withLong: 2000)
            .continueOnUi({ (aVoid: Void?) -> Void? in
                //self.triggerLock(physicalLockId: self.prettyLockIdToBase64LockId(pretty: "C1-08-F0-94")).conclude();
                self.triggerLock(physicalLockId: self.prettyLockIdToBase64LockId(pretty: "C1-55-1A-0B")).conclude();
                //self.queryLockState(physicalLockId: self.prettyLockIdToBase64LockId(pretty: "C1-08-F0-94")).conclude();
                //self.queryLockState(physicalLockId: self.prettyLockIdToBase64LockId(pretty: "C1-55-1A-0B")).conclude();
                //return;
            }).conclude();
    }
    
    @objc private func viewInBackground() {

        if(self.keyObserverRegistration != nil){
            keyObserverRegistration!.close();
            keyObserverRegistration = nil;
        }
        
        if(self.bluetoothObserverRegistration != nil){
            self.bluetoothObserverRegistration!.close();
            self.bluetoothObserverRegistration = nil;
        }
        
        if(self.bluetoothStateObserverRegistration != nil){
            self.bluetoothStateObserverRegistration!.close();
            self.bluetoothStateObserverRegistration = nil;
        }

        if(self.scanInProgress){
            self.bleLockManager!.stopForegroundScan();
            self.scanInProgress = false;
        }
    }
    
    
    // is invoked by the observer and adds the found keys to the key collection
    private func reloadLocalKeys() {
        
        // We only support a single user today, so the first user is the only user.
        guard let user = self.userManager!.getFirstUser() else {
            NSLog("No User is signed in")
            return;
        }
        
        self.keyManager!.queryLocalKeysAsync(user: user, forceUpdate: false, cancellationToken: TkCancellationToken_None)
            .continueOnUi { (keys: [NetTpkyMcModelWebviewCachedKeyInformation]?) -> Void in
                
                self.keys = keys ?? [];
                self.refreshView();
                
            }.catchOnUi { (e:NSException?) in
                
                NSLog("Query local keys failed: \(e?.getMessage())");
                
            }.conclude();
    }
    
    // is invoked by reloadLocalKeys and updates the key list view
    private func refreshView() {
        NSLog("Refresh view");
        self.tableView.reloadData();
    }
    
    // trigger lock
    private func triggerLock(physicalLockId: String) -> TkPromise<Bool> {
        return self.bleLockManager!.executeCommandAsync(deviceIds: [], physicalLockId: physicalLockId, commandFunc: { (tlcConnection: NetTpkyMcTlcpTlcpConnection?) -> TkPromise<Bool> in
            
            return self.commandExecutionFacade!.triggerLockAsync(tlcConnection, cancellationToken: TkCancellationToken_None)
                .continueOnUi({ (commandResult: NetTpkyMcModelCommandResult?) -> Bool in
                    
                    let code: NetTpkyMcModelCommandResult_CommandResultCode = commandResult?.getCode() ?? NetTpkyMcModelCommandResult_CommandResultCode.technicalError();
                    
                    // show response data
                    let responseData = commandResult?.getResponseData() as! IOSByteArray;
                    let responseDataAsNSData = responseData.toNSData() as NSData;
                    let bytes = responseDataAsNSData.bytes;
                    
                    for i in 0..<responseDataAsNSData.length {
                        NSLog("Response data byte: " + bytes.load(fromByteOffset: i, as: UInt8.self).description);
                    }
                    
                    // show response code
                    switch(code) {
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.ok():
                        NSLog("Trigger lock completed with OK");
                        return true;
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.wrongLockMode():
                        NSLog("Trigger lock completed with WrongLockMode");
                        return true;
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.lockVersionTooOld():
                        NSLog("Trigger lock completed with LockVersionTooOld");
                        return true;
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.lockVersionTooYoung():
                        NSLog("Trigger lock completed with LockVersionTooYoung");
                        return true;
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.lockNotFullyAssembled():
                        NSLog("Trigger lock completed with LockNotFullyAssembled");
                        return true;
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.serverCommunicationError():
                        NSLog("Trigger lock completed with ServerCommunicationError");
                        return true;
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.lockDateTimeInvalid():
                        NSLog("Trigger lock completed with LockDateTimeInvalid");
                        return true;
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.unauthorized_NotYetValid():
                        NSLog("Trigger lock completed with UnauthorizedNotYetValid");
                        return true;
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.unauthorized():
                        NSLog("Trigger lock completed with UnAuthorized");
                        return true;
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.lockCommunicationError():
                        NSLog("Trigger lock completed with LockCommunicationError");
                        return true;
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.unknownTagType():
                        NSLog("Trigger lock completed with UnknownTagType");
                        return true;
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.userSpecificError():
                        NSLog("Trigger lock completed with UserSpecificError");
                        return true;
                        
                    case NetTpkyMcModelCommandResult_CommandResultCode.technicalError():
                        NSLog("Trigger lock completed with TechnicalError");
                        return true;
                        
                    default:
                        return false;
                    }
                })
            
        }, cancellationToken: TkCancellationToken_None)
        .catchOnUi({ (e:NSException?) -> Bool in
            NSLog("Trigger lock failed: \(e?.getMessage())");
            return false;
        });
    }
    
    override func tableView(_ tableView: UITableView, numberOfRowsInSection section: Int) -> Int {
        return self.keys.count;
    }
    
    override func tableView(_ tableView: UITableView, cellForRowAt indexPath: IndexPath) -> UITableViewCell {
        
        let cell:KeyItemTableCell = tableView.dequeueReusableCell(withIdentifier:"KeyItem", for: indexPath) as! KeyItemTableCell
        
        let key = self.keys[indexPath.row]
        let physicalLockId: String = key.getGrant().getBoundLock().getPhysicalLockId()
        let isLockNearby:Bool = self.bleLockManager!.isLockNearby(physicalLockId: physicalLockId)
        
        cell.setKey(key, nearby: isLockNearby, triggerFn: { () -> TkPromise<Bool> in
            //return self.triggerLock(physicalLockId: physicalLockId);
            return self.queryLockState(physicalLockId: physicalLockId);
            //return self.upgradeFirmware(physicalLockId: physicalLockId, chunkIndex: self.currentFwUpgradeChunkIndex);
        });
        
        return cell
    }
    
    // query public lock state
    private func queryLockState(physicalLockId: String) -> TkPromise<Bool> {
        let isLockNearby:Bool = self.bleLockManager!.isLockNearby(physicalLockId: physicalLockId);
        
        if (isLockNearby) {
            return self.bleLockManager!.executeCommandAsync(deviceIds: [], physicalLockId: physicalLockId, commandFunc: { (tlcConnection: NetTpkyMcTlcpTlcpConnection?) -> TkPromise<Bool> in
                
                return self.commandExecutionFacade!.queryPublicStateAsync(tlcConnection, cancellationToken: TkCancellationToken_None)
                    .continueOnUi({ (lockState: NetTpkyMcModelPublicStateInfo?) -> Bool in
                        
                        let manufacturerId = lockState?.getPublicState().getManufacturerId().stringValue;
                        let fwType = lockState?.getPublicState().getFwType();
                        let fwVersion = lockState?.getPublicState().getFwVersionInt().stringValue;
                        NSLog("Query lock state completed");
                        NSLog("Manufacturer ID: " + manufacturerId!);
                        NSLog("FW Type: " + fwType!);
                        NSLog("FW Version: " + fwVersion!);
                        return true;
                    })
                
            }, cancellationToken: TkCancellationToken_None)
            .catchOnUi({ (e:NSException?) -> Bool in
                NSLog("Query lock state failed: \(e?.getMessage())");
                return false;
            });
        }
        else {
            NSLog("Query lock state failed: Lock not in range");
            let promiseSource = TkPromiseSource<Bool>();
            promiseSource.setResult(false);
            return promiseSource.getPromise();
        }
    }
    
    // upgrade firmware
    private func upgradeFirmware(physicalLockId: String, chunkIndex: Int32) -> TkPromise<Bool> {
        let userId = self.userManager!.getFirstUser()?.getId();
        let firmwareInfoId = "94844832-e1ed-4dca-8483-6efa547638a3"; // downgrade fw
        //let firmwareInfoId = "b5ebc2f9-1dd8-4dbc-b5b5-4b8d9722b30c"; // fw 1000003
        //let firmwareInfoId = "6cdc52ce-3ea8-49f3-90a2-a49849752efb"; // fw 1000004
        //let firmwareInfoId = "265c805d-a7ae-4f3b-bf1f-6e67d1b5afdf"; // fw 1000005
        //let firmwareInfoId = "a9f6bdef-93c3-499b-9532-4379e083d596"; // fw 1000006
        
        // download desired firmware package
        NSLog("Download firmware package started");
        return self.firmwareManager!.downloadFirmwareContentAsync(userId: userId!, physicalLockId: physicalLockId, firmwareInfoId: firmwareInfoId, cancellationToken: TkCancellationToken_None)
        .continueAsyncOnUi({ (fwPackage:NetTpkyMcModelFirmwarePackage?) -> TkPromise<Bool> in
            
            NSLog("Download firmware package completed");
            
            // upgrade desired downloaded firmware package
            return self.bleLockManager!.executeCommandAsync(deviceIds: [], physicalLockId: physicalLockId, commandFunc: { (tlcConnection: NetTpkyMcTlcpTlcpConnection?) -> TkPromise<Bool> in
                
                return self.commandExecutionFacade!.upgradeFirmware(tlcConnection, firmwarePackage: fwPackage!, firstChunkIdx: chunkIndex, progress: { (progressObj:NetTpkyMcManagerCommandExecutionFacade_FirmwareUpgradeProgress) in
                    let numChunksCompleted = progressObj.getNofChunksCompleted();
                    let progress = progressObj.getProgress();
                    NSLog("Progress: " + numChunksCompleted.description + " (" + progress!.description + ")");
                    self.currentFwUpgradeChunkIndex = numChunksCompleted;
                }, cancellationToken: TkCancellationToken_None)
                .continueOnUi({ (void:Void?) -> Bool in
                    NSLog("Upgrade firmware completed");
                    return true;
                })
                .catchOnUi({ (e:NSException?) -> Bool in
                    NSLog("Upgrade firmware failed: \(e?.getMessage())");
                    return false;
                })
            }, cancellationToken: TkCancellationToken_None)
            .catchOnUi({ (e:NSException?) -> Bool in
                NSLog("Upgrade firmware failed: Execute command failed: \(e?.getMessage())");
                return false;
            });
        })
        .catchOnUi({ (e:NSException?) -> Bool in
            NSLog("Download firmware package failed: \(e?.getMessage())");
            return false;
        });
    }
    
    // convert physical lock ID from pretty format to Base64 format
    private func prettyLockIdToBase64LockId(pretty: String) -> String {
        
        // remove all contained - symbols
        var pretty = pretty.replacingOccurrences(of: "-", with: "");
        
        // convert hex string to byte array
        let array: Array<UInt8> = stride(from: 0, to: pretty.characters.count, by: 2).flatMap{ UInt8(String(Array(pretty.characters)[$0..<$0.advanced(by: 2)]), radix: 16) };
        
        // add the 2-byte prefix indicating the length of the pretty-format number
        let num = array.count;
        let lengthArray: Array<UInt8> = [ UInt8(num), 0];
        
        // convert to Base64 string
        return NSData(bytes: (lengthArray + array), length: num + 2).base64EncodedString(options: NSData.Base64EncodingOptions(rawValue: 0));
    }
}
