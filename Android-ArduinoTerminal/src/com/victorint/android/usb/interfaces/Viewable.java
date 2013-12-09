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
package com.victorint.android.usb.interfaces;

import android.app.Activity;

/**
 * This interface represents a separate visual object within an Activity.
 * Activity can manipulate internal state of Viewable by passing it data
 * via thread-safe "signalToUi" method.
 */
public interface Viewable {
	
	public static final int DISABLE_CONTROLS_TYPE 			= 0;
	public static final int ENABLE_CONTROLS_TYPE 			= 1;
	public static final int CLEAR_UI_TYPE 					= 2;
	public static final int CHANGE_TITLE_TYPE 				= 3;
	public static final int SET_VIEW_FROM_PREFERENCES_TYPE 	= 4;
	public static final int SHOW_LATEST_MESSAGES 			= 5;
	public static final int CHAR_SEQUENCE_TYPE 				= 10;
	public static final int BYTE_SEQUENCE_TYPE 				= 11;
	public static final int INFO_MESSAGE_TYPE 				= 22;
	public static final int DEBUG_MESSAGE_TYPE 				= 24;
	public static final int CONNECTION_ACTION 				= 100;
	public static final int EXIT_ACTION 					= 101;
	
	/**
	 * Thread-safe method to control Viewable and pass data to it.
	 * @param type int specifying type of signal
	 * @param data optional data, can be null. 
	 */
	void signalToUi(int type, Object data);
	
	/**
	 * Saves internal state of Viewable to application's preferences
	 */
	void saveState();
	
	/**
	 * Reads application's preferences to set internal state of Viewable  
	 */
	void readState();
	
	/**
	 * Sets Activity to display this Viewable
	 * @param activity Activity that this Viewable
	 */
	void setActivity(Activity activity);

    /**
     * Closes all appropriate resources and prepares for exit.
     * Viewable object can not be re-used after this method is called.
     */
	void close();
    
}
