/* Copyright (C) 2011 Circuits At Home, LTD. All rights reserved.

This software may be distributed and modified under the terms of the GNU
General Public License version 2 (GPL2) as published by the Free Software
Foundation and appearing in the file GPL2.TXT included in the packaging of
this file. Please note that GPL2 Section 2[b] requires that all works based
on this software must also be made publicly available under the terms of
the GPL2 ("Copyleft").

Contact information
-------------------

Circuits At Home, LTD
Web      :  http://www.circuitsathome.com
e-mail   :  support@circuitsathome.com
*/
package com.victorint.android.usb;

import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;

public class StartServiceActivity extends Activity {
    /** Called when the activity is first created. */
    
    private String TAG = "StartServiceActivity";
    
    private boolean mPermissionRequestPending;
    private PendingIntent mPermissionIntent;
    private UsbManager mUsbManager;
    
	static final String ACTION_USB_PERMISSION = "com.victorint.android.usb.StartServiceActivity.action.USB_PERMISSION";
	static final String ACTION_STOP_SERVICE = "com.victorint.android.usb.StartServiceActivity.action.STOP_SERVICE";
	
    private Intent startServiceIntent;
  
	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = UsbManager.getAccessory(intent);
					if (intent.getBooleanExtra(
							UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						startService(startServiceIntent);
					} else {
						Log.d(TAG, "permission denied for accessory "
								+ accessory);
					}
					mPermissionRequestPending = false;
				}
				unregisterReceiver(mUsbReceiver);
				finish();
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
			}
		}
	};

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "onCreate entered");
        mUsbManager = UsbManager.getInstance(this);
		startServiceIntent = new Intent(this, com.victorint.android.usb.ArduinoUsbService.class);
		
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);
 
		
        
		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUsbManager.hasPermission(accessory)) {
				startService(startServiceIntent);
				unregisterReceiver(mUsbReceiver);
				finish();
			} else {
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						mUsbManager.requestPermission(accessory,
								mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {
			Log.d(TAG, "mAccessory is null");
		}
		
        Log.d(TAG, "onCreate exited");
        
    }
    
}