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

import com.victorint.android.usb.interfaces.Viewable;

import android.os.Bundle;

/**
 * The actual Activity that does it all
 */
public class ArduinoTerminalActivity extends UsbActivity {
	private static final String TAG = "ArduinoTerminalActivity";

	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
    }
    
    protected void createAndSetViews() {
        currentViewable_ = new TerminalViewable();
        currentViewable_.setActivity(this);
        signalToUi(Viewable.DISABLE_CONTROLS_TYPE, null);
    }
    
	@Override
	public void onDestroy() {
		super.onDestroy();
	}

	@Override
	public void onResume() {
		super.onResume();
	}
	
 	@Override
	public void onPause() {
		super.onPause();
	}

 	
 	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		
		// Do extra scroll down in case of keaboard popup
		if (currentViewable_ instanceof TerminalViewable) {
			((TerminalViewable) currentViewable_).scrollDown1();
		}
	}

 	
}