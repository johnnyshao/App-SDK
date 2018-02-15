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

import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ListFragment;
import android.support.v4.content.ContextCompat;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import net.tpky.mc.TapkeyServiceFactory;
import net.tpky.mc.concurrent.Async;
import net.tpky.mc.concurrent.CancellationTokens;
import net.tpky.mc.concurrent.Promise;
import net.tpky.mc.manager.BleLockManager;
import net.tpky.mc.manager.FirmwareManager;
import net.tpky.mc.manager.KeyManager;
import net.tpky.mc.manager.CommandExecutionFacade;
import net.tpky.mc.manager.UserManager;
import net.tpky.mc.model.BleLock;
import net.tpky.mc.model.CommandResult;
import net.tpky.mc.model.CryptArtifacts;
import net.tpky.mc.model.Grant;
import net.tpky.mc.model.Lock;
import net.tpky.mc.model.Owner;
import net.tpky.mc.model.PublicStateInfo;
import net.tpky.mc.model.User;
import net.tpky.mc.model.webview.CachedKeyInformation;
import net.tpky.mc.model.FirmwarePackage;
import net.tpky.mc.tlcp.TlcpConnection;
import net.tpky.mc.utils.Action;
import net.tpky.mc.utils.Action1;
import net.tpky.mc.utils.Func1;
import net.tpky.mc.utils.ObserverRegistration;

import java.util.Date;
import java.util.List;
import java.util.Map;


public class KeyListFragment extends ListFragment {

    private final static String TAG = KeyListFragment.class.getSimpleName();
    private static final int PERMISSIONS_REQUEST__ACCESS_COARSE_LOCATION = 0;

    private KeyManager keyManager;
    private CommandExecutionFacade commandExecutionFacade;
    private UserManager userManager;
    private BleLockManager bleLockManager;
    private FirmwareManager firmwareManager;

    private ArrayAdapter<CachedKeyInformation> adapter;

    private ObserverRegistration bleObserverRegistration;
    private ObserverRegistration keyUpdateObserverRegistration;

    /*
     * The handler that connects the individual items of the key ring (i.e. individual keys) to the
     * functional components.
     */
    private final KeyItemAdapter.KeyItemAdapterHandler keyItemAdapterHandler = new KeyItemAdapter.KeyItemAdapterHandler() {

        /*
         * Return whether the lock with the given lock ID (in Base64 format) is in range.
         */
        @Override
        public boolean isLockNearby(String physicalLockId) {
            return (physicalLockId != null) && bleLockManager.isLockNearby(physicalLockId);
        }

        /*
         * Access the lock with the given lock ID (in Base64 format).
         */
        @Override
        public Promise<Boolean> accessLock(String physicalLockId) {
            //return triggerLock(physicalLockId); // open/close
            return queryLockState(physicalLockId); // query lock state
            //return upgradeFirmware(physicalLockId, "94844832-e1ed-4dca-8483-6efa547638a3");
        }
    };

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        App app = (App) getActivity().getApplication();
        TapkeyServiceFactory tapkeyServiceFactory = app.getTapkeyServiceFactory();
        keyManager = tapkeyServiceFactory.getKeyManager();
        commandExecutionFacade = tapkeyServiceFactory.getCommandExecutionFacade();
        userManager = tapkeyServiceFactory.getUserManager();
        bleLockManager = tapkeyServiceFactory.getBleLockManager();
        firmwareManager = tapkeyServiceFactory.getFirmwareManager();
        adapter = new KeyItemAdapter(getActivity(), keyItemAdapterHandler);

        setListAdapter(adapter);

        // make sure, we have the ACCESS_COARSE_LOCATION permission, which is required, to detect
        // BLE locks nearby.
        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            // Should we explain to the user, why we need this permission before actually requesting
            // it? This is the case, if the user rejected the permission before.
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                showPermissionRationale();
            } else {
                requestPermission();
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Listen for changes in the available Tapkey access keys. Changes might happen, e.g. due to push
        // notifications received from the Tapkey backend.
        if (keyUpdateObserverRegistration == null) {
            keyUpdateObserverRegistration = keyManager.getKeyUpdateObservable().addObserver(new Action1<Void, RuntimeException>() {
                @Override
                public void invoke(Void aVoid) throws RuntimeException {
                    queryLocalKeys(false);
                }
            });
        }
        keyUpdate();

        // listen for Tapkey locks coming into or leaving range.
        if (bleObserverRegistration == null) {
            bleObserverRegistration = bleLockManager.getLocksChangedObservable().addObserver(new Action1<Map<String, BleLock>, RuntimeException>() {
                @Override
                public void invoke(Map<String, BleLock> stringBleLockMap) throws RuntimeException {
                    bleLocksChanged(stringBleLockMap);
                }
            });
        }

        // If we have the COARSE_LOCATION permission, start scanning for BLE devices.
        // Otherwise, ask the user for permission.
        if (ContextCompat.checkSelfPermission(getContext(), android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            bleLockManager.startForegroundScan();
        } else {
            if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                showPermissionRationale();
            }
        }

        // test: trigger lock at the start
        bleLockManager.startForegroundScan();
        net.tpky.mc.concurrent.Async.delayAsync(3000).continueOnUi(new Func1<Void, Void, Exception>() {
            @Override
            public Void invoke(Void aVoid) throws Exception {
                triggerLock("BADBCPCU");
                return null;
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();

        // stop scanning for BLE locks nearby.
        bleLockManager.stopForegroundScan();

        // stop listening for key updates and BLE lock changes.
        if (keyUpdateObserverRegistration != null) {
            keyUpdateObserverRegistration.close();
            keyUpdateObserverRegistration = null;
        }
        if (bleObserverRegistration != null) {
            bleObserverRegistration.close();
            bleObserverRegistration = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PERMISSIONS_REQUEST__ACCESS_COARSE_LOCATION: {

                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    bleLockManager.startForegroundScan();
                } else {
                    if (shouldShowRequestPermissionRationale(android.Manifest.permission.ACCESS_COARSE_LOCATION)) {
                        showPermissionRationale();
                    } else {
                        Snackbar.make(getView(), R.string.key_item__permission_needed, Snackbar.LENGTH_INDEFINITE)
                                .setAction("ALLOW", new View.OnClickListener() {
                                    @Override
                                    public void onClick(View view) {

                                        Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", getActivity().getPackageName(), null));
                                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                                        startActivity(intent);

                                    }
                                }).show();
                    }
                }
                bleLockManager.startForegroundScan();
            }
        }
    }

    private void showPermissionRationale(){
        Snackbar.make(getView(), R.string.key_item__permission_needed, Snackbar.LENGTH_INDEFINITE)
                .setAction("ALLOW", new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        requestPermission();
                    }
                }).show();
    }

    private void requestPermission(){
        requestPermissions(new String[]{android.Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSIONS_REQUEST__ACCESS_COARSE_LOCATION);
    }

    /*
     * Display all locking devices assigned to the current user.
     */
    private void keyUpdate() {

        // To do: Obtain the locking assigned to the current user

        // Display the locking devices
        Owner o1 = new Owner();
        o1.setName("WITTE");
        Lock l1 = new Lock();
        l1.setTitle("Lock C1-08-F0-94");
        l1.setPhysicalLockId(prettyLockIdToBase64LockId("C1-08-F0-94"));
        Grant g1 = new Grant();
        g1.setOwner(o1);
        g1.setBoundLock(l1);
        g1.setValidFrom(new Date(2017 - 1900, 11, 4, 12, 0));
        g1.setValidBefore(null);
        CachedKeyInformation k1 = new CachedKeyInformation(new CryptArtifacts(), g1);
        Owner o2 = new Owner();
        o2.setName("WITTE");
        Lock l2 = new Lock();
        l2.setTitle("Lock C1-55-1A-0B");
        l2.setPhysicalLockId(prettyLockIdToBase64LockId("C1-55-1A-0B"));
        Grant g2 = new Grant();
        g2.setOwner(o2);
        g2.setBoundLock(l2);
        g2.setValidFrom(new Date(2017 - 1900, 11, 3, 12, 0));
        g2.setValidBefore(null);
        CachedKeyInformation k2 = new CachedKeyInformation(new CryptArtifacts(), g2);
        adapter.clear();
        adapter.add(k1);
        adapter.add(k2);
    }

    private void bleLocksChanged(Map<String, BleLock> stringBleLockMap){
        adapter.notifyDataSetChanged();
    }

    /*
     * Query locally stored Tapkey access keys
     */
    public void queryLocalKeys(boolean forceUpdate) {

        // We only support a single user today, so the first user is the only user.
        User firstUser = userManager.getFirstUser();
        if(firstUser == null){
            Log.e(TAG, "Query local keys failed: No user is signed in");
            return;
        }

        // query for this user's keys asynchronously
        keyManager.queryLocalKeysAsync(firstUser, forceUpdate, CancellationTokens.None)

                // when completed with success, continue on the UI thread
                .continueOnUi(new Func1<List<CachedKeyInformation>, Void, Exception>() {
                    @Override
                    public Void invoke(List<CachedKeyInformation> cachedKeyInformations) throws Exception {
                        Log.d(TAG, "Query local keys succeeded ");
                        return null;
                    }
                })

                // handle async exceptions
                .catchOnUi(new Func1<Exception, Void, Exception>() {
                    @Override
                    public Void invoke(Exception e) throws Exception {
                        Log.e(TAG, "Query local keys failed ", e);
                        return null;
                    }
                })

                // make sure, we don't miss any exceptions.
                .conclude();
    }

    /*
     * Open/close the lock
     */
    public Promise<Boolean> triggerLock(String physicalLockId) {

        // check if lock in range
        boolean inRange = bleLockManager.isLockNearby(physicalLockId);

        // let the BLE lock manager establish a connection to the BLE lock and then let the
        // CommandExecutionFacade use this connection to execute a TriggerLock command.
        return bleLockManager.executeCommandAsync(new String[0], physicalLockId, new Func1<TlcpConnection, Promise<Boolean>, RuntimeException>() {
            @Override
            public Promise<Boolean> invoke(TlcpConnection tlcpConnection) throws RuntimeException {

                // now, that we have a TlcpConnection to the lock, let the CommandExecutionFacade
                // asynchronously execute the trigger lock command.
                return commandExecutionFacade.triggerLockAsync(tlcpConnection, CancellationTokens.None).continueOnUi(new Func1<CommandResult, Boolean, Exception>() {
                    @Override
                    public Boolean invoke(CommandResult commandResult) throws Exception {

                        byte[] responseData = (byte[])commandResult.getResponseData();
                        for (byte b : responseData) {
                            Log.d(TAG, "Trigger lock response byte: " + b);
                        }

                        switch (commandResult.getCommandResultCode()) {
                            case Ok:
                                Log.d(TAG, "Trigger lock result code OK");
                                return true;
                            case WrongLockMode:
                                Log.d(TAG, "Trigger lock result code WrongLockMode");
                                return false;
                            case LockVersionTooOld:
                                Log.d(TAG, "Trigger lock result code LockVersionTooOld");
                                return false;
                            case LockVersionTooYoung:
                                Log.d(TAG, "Trigger lock result code LockVersionTooYoung");
                                return false;
                            case LockNotFullyAssembled:
                                Log.d(TAG, "Trigger lock result code LockNotFullyAssembled");
                                return false;
                            case ServerCommunicationError:
                                Log.d(TAG, "Trigger lock result code ServerCommunicationError");
                                return false;
                            case LockDateTimeInvalid:
                                Log.d(TAG, "Trigger lock result code LockDateTimeInvalid");
                                return false;
                            case Unauthorized_NotYetValid:
                                Log.d(TAG, "Trigger lock result code Unauthorized_NotYetValid");
                                return false;
                            case Unauthorized:
                                Log.d(TAG, "Trigger lock result code Unauthorized");
                                return false;
                            case LockCommunicationError:
                                Log.d(TAG, "Trigger lock result code LockCommunicationError");
                                return false;
                            case UnknownTagType:
                                Log.d(TAG, "Trigger lock result code UnknownTagType");
                                return false;
                            case UserSpecificError:
                                Log.d(TAG, "Trigger lock result code UserSpecificError");
                                return false;
                            case TechnicalError:
                                Log.d(TAG, "Trigger lock result code TechnicalError");
                                return false;
                            default:
                                Log.d(TAG, "Trigger lock result code Unkown");
                                return false;
                        }
                    }
                });
            }
        }, CancellationTokens.None).catchOnUi(new Func1<Exception, Boolean, Exception>() {
            @Override
            public Boolean invoke(Exception e) throws Exception {
                Log.e(TAG, "Couldn't execute trigger lock command.", e);

                // handle any exceptions and let the user know, something went wrong.
                Toast.makeText(getContext(), R.string.key_item__triger_failed, Toast.LENGTH_SHORT).show();
                return false;
            }
        });
    }

    /*
     * Query the current firmware state of the lock
     */
    private Promise<Boolean> queryLockState(String physicalLockId) {

        // check if lock in range
        boolean inRange = bleLockManager.isLockNearby(physicalLockId);

        // query public state
        if (inRange)
        {
            return bleLockManager.executeCommandAsync(new String[0], physicalLockId, new Func1<TlcpConnection, Promise<Boolean>, RuntimeException>() {
                @Override
                public Promise<Boolean> invoke(TlcpConnection tlcpConnection) throws RuntimeException {

                    // now, that we have a TlcpConnection to the lock, let the CommandExecutionFacade
                    // asynchronously execute the trigger lock command.
                    return commandExecutionFacade.queryPublicStateAsync(tlcpConnection, CancellationTokens.None).continueOnUi(new Func1<PublicStateInfo, Boolean, Exception>() {
                        @Override
                        public Boolean invoke(PublicStateInfo state) throws Exception {
                            if (state == null || state.getPublicState() == null) {
                                Log.e(TAG, "Querying lock state failed: State is null");
                                return false;
                            }
                            Log.d(TAG, "Lock state manufacturer ID: " + state.getPublicState().getManufacturerId());
                            Log.d(TAG, "Lock state firmware type: " + state.getPublicState().getFwType());
                            Log.d(TAG, "Lock state firmware version: " + state.getPublicState().getFwVersionInt());
                            return true;
                        }
                    });
                }
            }, CancellationTokens.None).catchOnUi(new Func1<Exception, Boolean, Exception>() {
                @Override
                public Boolean invoke(Exception e) throws Exception {
                    Log.e(TAG, "Querying lock state failed: ", e);
                    return false;
                }
            });
        }

        // return false
        else {
            Log.w(TAG, "Lock not in range");
            return Async.PromiseFromResult(false);
        }
    }

    /*
     * Upgrade the firmware on a lock
     */
    private Promise<Boolean> upgradeFirmware(final String physicalLockId, String firmwareInfoId) {

        // obtain the user ID of the currently signed in user
        final String userId = userManager.getFirstUser().getId();

        // download firmware package given by the firmware info ID
        return firmwareManager.downloadFirmwareContentAsync(userId, physicalLockId, firmwareInfoId, CancellationTokens.None)
                .continueAsyncOnUi(new Func1<FirmwarePackage, Promise<Boolean>, Exception>() {
                    @Override
                    public Promise<Boolean> invoke(final FirmwarePackage firmwarePackage) throws Exception {
                        Log.d(TAG, "Firmware upgrade: package downloaded successfully");
                        return bleLockManager.executeCommandAsync(new String[0], physicalLockId, new Func1<TlcpConnection, Promise<Boolean>, RuntimeException>() {
                            @Override
                            public Promise<Boolean> invoke(TlcpConnection tlcpConnection) throws RuntimeException {

                                // perform firmware upgrade
                                return commandExecutionFacade.upgradeFirmware(tlcpConnection, firmwarePackage, 0, new Action1<CommandExecutionFacade.FirmwareUpgradeProgress, RuntimeException>() {
                                    @Override
                                    public void invoke(CommandExecutionFacade.FirmwareUpgradeProgress firmwareUpgradeProgress) throws RuntimeException {
                                        Log.d(TAG, "Firmware upgrade progress: " + (firmwareUpgradeProgress.getProgress() * 100) + " % (" + firmwareUpgradeProgress.getNofChunksCompleted() + ")");
                                    }
                                }, CancellationTokens.None).asConst(true);
                            }
                        }, CancellationTokens.None);
                    }
                })
                .catchOnUi(new Func1<Exception, Boolean, Exception>() {
                    @Override
                    public Boolean invoke(Exception e) throws Exception {
                        Log.e(TAG, "Firmware upgrade failed: ", e);
                        return false;
                    }
                })
                .finallyOnUi(new Action<RuntimeException>() {
                    @Override
                    public void invoke() throws RuntimeException {
                        // do something after the firmware upgrade task finished (completed or failed)
                    }
                });
    }

    /*
     * Convert a lock ID from the pretty format (e.g. C1-08-F0-94) to the Base64 format (e.g. BADBCPCU).
     */
    private String prettyLockIdToBase64LockId(String pretty) {

        // remove all contained - symbols
        pretty = pretty.replace("-", "");

        // convert hex string to byte array
        byte[] array = new byte[pretty.length() / 2];
        for (int i = 0; i < array.length; i++) {
            int index = i * 2;
            int v = Integer.parseInt(pretty.substring(index, index + 2), 16);
            array[i] = (byte)v;
        }

        // add the 2-byte prefix indicating the length of the pretty-format number
        int num = array.length;
        byte[] lengthArray = {(byte)num, 0};

        // convert to Base64 string
        byte[] c = new byte[lengthArray.length + array.length];
        System.arraycopy(lengthArray, 0, c, 0, lengthArray.length);
        System.arraycopy(array, 0, c, lengthArray.length, array.length);
        return Base64.encodeToString(c, Base64.NO_WRAP);
    }
}
