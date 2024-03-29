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

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.support.annotation.NonNull;
import android.support.v7.widget.AppCompatButton;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import net.tpky.mc.concurrent.Async;
import net.tpky.mc.concurrent.Promise;
import net.tpky.mc.model.webview.CachedKeyInformation;
import net.tpky.mc.utils.Func1;

import java.text.DateFormat;
import java.util.Date;

public class KeyItemAdapter extends ArrayAdapter<CachedKeyInformation> {

    private final static String TAG = KeyItemAdapter.class.getSimpleName();

    public interface KeyItemAdapterHandler {
        boolean isLockNearby(String physicalLockId);
        Promise<Boolean> accessLock(String physicalLockId);
    }

    private final KeyItemAdapterHandler handler;

    public KeyItemAdapter(Context context, KeyItemAdapterHandler keyItemAdapterHandler) {
        super(context, R.layout.key_item);
        this.handler = keyItemAdapterHandler;
    }

    @NonNull
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        final CachedKeyInformation item = getItem(position);

        LayoutInflater mInflater = (LayoutInflater) getContext().getSystemService(Activity.LAYOUT_INFLATER_SERVICE);
        final View viewToUse = (convertView == null) ? mInflater.inflate(R.layout.key_item, null) : convertView;

        TextView lockNameTextView = (TextView) viewToUse.findViewById(R.id.key_item__lock_name);
        TextView ownerNameTextView = (TextView) viewToUse.findViewById(R.id.key_item__owner);
        TextView validFromTextView = (TextView) viewToUse.findViewById(R.id.key_item__valid_from);
        TextView validBeforeTextView = (TextView) viewToUse.findViewById(R.id.key_item__valid_before);
        View validFromWrapper = viewToUse.findViewById(R.id.key_item__valid_from_wrapper);
        final AppCompatButton triggerButton = (AppCompatButton) viewToUse.findViewById(R.id.key_item__trigger_button);

        lockNameTextView.setText(getTitle(item));
        ownerNameTextView.setText(getOwnerName(item));

        Date validFrom = (item != null && item.getGrant() != null) ? item.getGrant().getValidFrom() : null;
        Date validBefore = (item != null && item.getGrant() != null) ? item.getGrant().getValidBefore() : null;

        if (validFrom == null || validFrom.before(new Date())) {
            validFromWrapper.setVisibility(View.GONE);
        } else {
            validFromTextView.setText(formatDate(validFrom));
            validFromWrapper.setVisibility(View.VISIBLE);
        }

        if (validBefore == null) {
            validBeforeTextView.setText(R.string.key_item__unrestricted_access);
        } else {
            validBeforeTextView.setText(formatDate(validBefore));
        }

        final String physicalLockId = ((item != null) && (item.getGrant() != null) && (item.getGrant().getBoundLock() != null)) ? item.getGrant().getBoundLock().getPhysicalLockId() : null;


        /*if (!handler.isLockNearby(physicalLockId)) {
            //triggerButton.setVisibility(View.GONE);
            //triggerButton.setOnClickListener(null);
        } else {*/

            triggerButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {

                    triggerButton.setEnabled(false);

                    // asynchronously trigger a command of the locking device
                    handler.accessLock(physicalLockId)

                            // Catch errors and return false to indicate failure
                            .catchOnUi(new Func1<Exception, Boolean, Exception>() {
                                @Override
                                public Boolean invoke(Exception e) throws Exception {
                                    Log.e(TAG, "Access lock failed", e);
                                    return false;
                                }
                            })

                            // when done, continue on the UI thread
                            .continueOnUi(new Func1<Boolean, Void, Exception>() {
                                @Override
                                public Void invoke(Boolean success) throws Exception {

                                    // when trigger lock was successfully set background color to green
                                    // otherwise set background color to red
                                    viewToUse.setBackgroundColor((success) ? Color.GREEN : Color.RED);

                                    // enable button to allow another trigger
                                    triggerButton.setEnabled(true);

                                    // reset background after a delay
                                    Async.delayAsync(3000).continueOnUi(new Func1<Void, Void, Exception>() {
                                        @Override
                                        public Void invoke(Void aVoid) throws Exception {
                                            viewToUse.setBackgroundColor(Color.TRANSPARENT);
                                            return null;
                                        }
                                    });

                                    return null;
                                }
                            })

                            // make sure, we don't miss any exceptions.
                            .conclude();
                }
            });

            triggerButton.setVisibility(View.VISIBLE);
        //}

        return viewToUse;
    }

    private String getTitle(CachedKeyInformation cachedKeyInformation) {
        if(cachedKeyInformation == null || cachedKeyInformation.getGrant() == null || cachedKeyInformation.getGrant().getBoundLock() == null || cachedKeyInformation.getGrant().getBoundLock().getTitle() == null || cachedKeyInformation.getGrant().getBoundLock().getTitle().isEmpty()){
            return getContext().getString(R.string.key_item__unknown_lock);
        }
        return cachedKeyInformation.getGrant().getBoundLock().getTitle();
    }

    private String getOwnerName(CachedKeyInformation cachedKeyInformation){
        if(cachedKeyInformation == null || cachedKeyInformation.getGrant() == null || cachedKeyInformation.getGrant().getOwner() == null || cachedKeyInformation.getGrant().getOwner().getName() == null || cachedKeyInformation.getGrant().getOwner().getName().isEmpty()){
            return getContext().getString(R.string.key_item__unknown_owner);
        }
        return cachedKeyInformation.getGrant().getOwner().getName();
    }

    private String formatDate(Date date){
        DateFormat dateFormat = android.text.format.DateFormat.getDateFormat(getContext());
        DateFormat timeFormat = android.text.format.DateFormat.getTimeFormat(getContext());
        return dateFormat.format(date) + ", " + timeFormat.format(date);
    }
}
