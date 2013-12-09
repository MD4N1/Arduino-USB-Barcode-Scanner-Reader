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

import java.io.PrintWriter;
import java.io.StringWriter;

import com.android.future.usb.UsbManager;
import com.victorint.android.usb.interfaces.Connectable;
import com.victorint.android.usb.interfaces.Viewable;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Toast;

/**
 * Abstract Client Activity class that uses Viewable to show data
 * and knows how to connect to the ArduinoUsbService
 */
public abstract class UsbActivity extends Activity implements Connectable {
	
	private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
	private static final String TAG = "UsbActivity";
	private static final int scrollDelay = 300;
	static final int NO_USB_DEVICES_DIALOG = 1;
	static final int SELECT_USB_DEVICE_DIALOG = 2;
	
	/** Messenger for communicating with service. */
	Messenger mService_ = null;
	/** Flag indicating whether we have called bind on the service. */
	boolean mIsBound;

	protected Viewable currentViewable_;
	protected Resources resources_;
	protected boolean exitOnDetach_ = true;
	boolean debug_ = false;
	
	/**
	 * Handler of incoming messages from service.
	 */
	class IncomingHandler extends Handler {
	    @Override
	    public void handleMessage(Message msg) {
	    	try {
	    	Log.d(TAG, "USBActivity handleMessage: " + msg.what);
	        switch (msg.what) {
            case ArduinoUsbService.MSG_SEND_ASCII_TO_CLIENT:
            	Bundle b = msg.getData();
            	CharSequence asciiMessage = b.getCharSequence(ArduinoUsbService.MSG_KEY);
            	logMessage("USBActivity handleMessage: TO_CLIENT " + asciiMessage);
            	showMessage(asciiMessage);
            	break;
            case ArduinoUsbService.MSG_SEND_BYTES_TO_CLIENT:
            	Bundle bb = msg.getData();
            	byte[] data = bb.getByteArray(ArduinoUsbService.MSG_KEY);
            	signalToUi(Viewable.BYTE_SEQUENCE_TYPE, data);
            	break;
            case ArduinoUsbService.MSG_SEND_ASCII_TO_SERVER:
            	Bundle sb = msg.getData();
            	CharSequence sAsciiMessage = sb.getCharSequence(ArduinoUsbService.MSG_KEY);
            	Log.d(TAG, "USBActivity handleMessage: TO_SERVER " + sAsciiMessage);
            	showMessage(sAsciiMessage);
            	break;
            case ArduinoUsbService.MSG_SEND_EXIT_TO_CLIENT:
            	UsbActivity.this.runOnUiThread(new Runnable() {
					@Override
					public void run() {
			 	        try {
			 	        	if (debug_) showMessage("on Exit Signal\n");
			 	        	if (exitOnDetach_) close();
			 	        } catch (Exception e) {
			 	        	if (debug_) showMessage("Close App: " +e.getMessage() + "\n");
			 	        }
			 	       if (exitOnDetach_) finish();
					}
            	});
            	break;
            default:
            	super.handleMessage(msg);
	        }
	    	} catch (Exception ee) {
	    		if (debug_) {
	    			Log.e(TAG, "Client handleMessage Exception: "+ Utils.getExceptionStack(ee, true));
	    			//showMessage("handleMessage: " +ee.getMessage() + "\n");
	    		}
	    	}
	    }
	}

	/**
	 * Target we publish for clients to send messages to IncomingHandler.
	 */
	final Messenger mMessenger_ = new Messenger(new IncomingHandler());

	/**
	 * Class for interacting with the main interface of the service.
	 */
	private ServiceConnection mConnection = new ServiceConnection() {
	    public void onServiceConnected(ComponentName className,
	            IBinder service) {
	        // This is called when the connection with the service has been
	        // established, giving us the service object we can use to
	        // interact with the service.  We are communicating with our
	        // service through an IDL interface, so get a client-side
	        // representation of that from the raw service object.
	        mService_ = new Messenger(service);
	        logMessage("Attached.");

	        // We want to monitor the service for as long as we are
	        // connected to it.
	        try {
	            Message msg = Message.obtain(null,
	            		ArduinoUsbService.MSG_REGISTER_CLIENT);
	            msg.replyTo = mMessenger_;
	            mService_.send(msg);

	        } catch (RemoteException e) {
	        	logMessage("Problem connecting to Server: " + e.getMessage());
	        }

	        // As part of the sample, tell the user what happened.
	        //Toast.makeText(UsbActivity.this, R.string.remote_service_connected,
	        //        Toast.LENGTH_SHORT).show();
	        logMessage("Server Connected");
	    }

	    public void onServiceDisconnected(ComponentName className) {
	        // This is called when the connection with the service has been
	        // unexpectedly disconnected -- that is, its process crashed.
	        mService_ = null;
	        logMessage("Server Disconnected");

	        // As part of the sample, tell the user what happened.
	        //Toast.makeText(UsbActivity.this, R.string.remote_service_disconnected,
	        //        Toast.LENGTH_SHORT).show();
	    }
	};

	void doBindService() {
	    // Establish a connection with the service.  We use an explicit
	    // class name because there is no reason to be able to let other
	    // applications replace our component.
	    bindService(new Intent(UsbActivity.this, 
	    		ArduinoUsbService.class), mConnection, Context.BIND_AUTO_CREATE);
	    mIsBound = true;
	    logMessage("Bound.");
	}

	void doUnbindService() {
	    if (mIsBound) {
	        // If we have received the service, and hence registered with
	        // it, then now is the time to unregister.
	        if (mService_ != null) {
	            try {
	                Message msg = Message.obtain(null,
	                		ArduinoUsbService.MSG_UNREGISTER_CLIENT);
	                msg.replyTo = mMessenger_;
	                mService_.send(msg);
	            } catch (RemoteException e) {
	                // There is nothing special we need to do if the service
	                // has crashed.
	            }
	        }

	        // Detach our existing connection.
	        unbindService(mConnection);
	        mIsBound = false;
	        logMessage("Unbound.");
	    }
	}	
	
	protected void showMessage(CharSequence message) {
		signalToUi(Viewable.CHAR_SEQUENCE_TYPE, message);
	}
	
	private final BroadcastReceiver usbReceiver_ = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (resources_ == null) {
				resources_ = getResources();
			}
			if (ACTION_USB_PERMISSION.equals(action)) {
				logMessage("Got ACTION_USB_PERMISSION");
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				logMessage(resources_.getString(R.string.usb_device_detached));
			} else if (UsbManager.ACTION_USB_ACCESSORY_ATTACHED.equals(action)) {
				logMessage(resources_.getString(R.string.usb_device_attached));
			}
		}
	};
	
	
   /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_ATTACHED);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);

		if (resources_ == null) {
			resources_ = getResources();
		}

        createAndSetViews();
        logMessage("before register receiver");
		//registerReceiver(usbReceiver_, filter);
		
		doBindService();

    }
    
    /**
     * This method is implemented in the ArduinoTerminalActivity class.
     */
    protected abstract void createAndSetViews();
    
	@Override
	public void onDestroy() {
		logMessage("onDestroy");
		close();
		//unregisterReceiver(usbReceiver_);
		doUnbindService();
		super.onDestroy();
	}

	@Override
	public void onResume() {
		logMessage("onResume");
		super.onResume();
		signalToUi(Viewable.SET_VIEW_FROM_PREFERENCES_TYPE, null);
		signalToUi(Viewable.SHOW_LATEST_MESSAGES, scrollDelay);
	}
	
 	@Override
	public void onPause() {
 		logMessage("onPause");
		super.onPause();
		//disconnect();
	}

 	@Override
	public void onStop() {
 		logMessage("onStop");
 		doUnbindService();
		super.onStop();
	}

 	@Override
	public void onStart() {
 		logMessage("onStart");
		doBindService();
		super.onStart();
	}

 	@Override
	public void onRestart() {
 		logMessage("onRestart");
		super.onRestart();
		signalToUi(Viewable.SHOW_LATEST_MESSAGES, null);
	}

 	@Override
 	public void onConfigurationChanged(Configuration newConfig) {
 	    super.onConfigurationChanged(newConfig);
 	    
 	    // Scroll down when orientation has been changed
 	   signalToUi(Viewable.SHOW_LATEST_MESSAGES, new Integer(scrollDelay));

 	    // Checks the orientation of the screen
 	    /*
 	    if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
 	        Toast.makeText(this, "landscape", Toast.LENGTH_SHORT).show();
 	    } else if (newConfig.orientation == Configuration.ORIENTATION_PORTRAIT){
 	        Toast.makeText(this, "portrait", Toast.LENGTH_SHORT).show();
 	    }
 	    */
 	  }
 	
 	@Override
 	public boolean onCreateOptionsMenu(Menu menu) {
 		logMessage("onCreateOptionsMenu");
 	    MenuInflater inflater = getMenuInflater();
 	    inflater.inflate(R.menu.main_menu, menu);
 	    return true;
 	}
 	
 	@Override
 	public boolean onOptionsItemSelected(MenuItem item) {
 		logMessage("onOptionsItemSelected");
 	    // Handle item selection
 	    switch (item.getItemId()) {
 	    case R.id.mainMenuConnect:
 	        onConnectMenu();
 	       return true;
 	    case R.id.mainMenuSettings:
 	    	startActivity(new Intent(this, Preferences.class));
 	        return true;
 	    case R.id.mainMenuExit:
 	        try {
 	        	logMessage("on Exit menu");
 	        	close();
 	        } catch (Exception e) {
 	        	logMessage("Close menu fail: " +e.getMessage());
 	        }
 	        finish();
 	        return true;
 	    default:
 	        return super.onOptionsItemSelected(item);
 	    }
 	} 	
 	
 	public void onConnectMenu() {
 		logMessage("On Connect Menu");
 		doUnbindService();
 		
    	Intent stopServiceIntent = new Intent(this, com.victorint.android.usb.ArduinoUsbService.class);
    	this.stopService(stopServiceIntent);

    	SystemClock.sleep(1500);
    	
    	Intent startServiceIntent = new Intent(this, com.victorint.android.usb.ArduinoUsbService.class);
    	startService(startServiceIntent);

    	SystemClock.sleep(1500);
		doBindService();
 	}
 	
 	public boolean isConnected() {
    	return false;
    }
    
 	public void connect() {
 		logMessage("Connect");
    }
    
    public void disconnect() {
    	logMessage("Disconnect");
		//unregisterReceiver(usbReceiver_);
		doUnbindService();

    }
    
    public void close() {
    	logMessage("close");
		//if (isConnected()) {
			disconnect();
		//}
		
		if (currentViewable_ != null) {
			currentViewable_.saveState();
			currentViewable_.close();
			currentViewable_ = null;
		}
    }
    
    public void sendEcho(byte[] data) {
		try {
            Log.d(TAG, "USBActivity sendEcho: TO_SERVER " + data + ", mService: " + mService_);
			Message msg = Message.obtain(null,
					ArduinoUsbService.MSG_SEND_ECHO_TO_SERVER, data);
			msg.replyTo = mMessenger_;
			Bundle b = new Bundle();
            if (mService_ != null) {
                b.putByteArray(ArduinoUsbService.MSG_KEY, data);
                msg.setData(b);
            	mService_.send(msg);
            } else if (mMessenger_ != null)  {
                b.putCharSequence(ArduinoUsbService.MSG_KEY, "Server not Available :: " + data);
                msg.setData(b);
                mMessenger_.send(msg);
            }
		} catch (RemoteException e) {
			logMessage("Problem sending echo to Server: " + e.getMessage());
		}
    	
    }
        
    public void sendClearUI() {
        signalToUi(Viewable.CLEAR_UI_TYPE, null);
    }
        
    public void sendData(CharSequence data) {
		try {
            Log.d(TAG, "USBActivity sendData: TO_SERVER " + data + ", mService: " + mService_);
			Message msg = Message.obtain(null,
					ArduinoUsbService.MSG_SEND_ASCII_TO_SERVER, data);
			msg.replyTo = mMessenger_;
			Bundle b = new Bundle();
            if (mService_ != null) {
                b.putCharSequence(ArduinoUsbService.MSG_KEY, data);
                msg.setData(b);
            	mService_.send(msg);
            } else if (mMessenger_ != null)  {
                b.putCharSequence(ArduinoUsbService.MSG_KEY, "Server not Available :: " + data);
                msg.setData(b);
                mMessenger_.send(msg);
            }
		} catch (RemoteException e) {
			logMessage("Problem sending message to Server: " + e.getMessage());
		}
    	
    }
        
    /*
    public void sendData(CharSequence data) {
    	if (data == null || data.length() == 0) {
    		return;
    	}
    	int length = (data.length() > 3) ? data.length() : 3;
    	byte[] buffer = new byte[length];
    	int val = Character.getNumericValue(data.charAt(0));
		if (val > 255) {
			val = 255;
		}
		buffer[0] = (byte) val;
    	if (data.length() > 1) {
    		val = Character.getNumericValue(data.charAt(1));
    		if (val > 255) {
    			val = 255;
    		}
    		buffer[1] = (byte) val;
    	}
    	if (data.length() > 2) {
    		val = Integer.parseInt(data.toString().substring(2), 16);
    		if (val > 255) {
    			val = 255;
    		}
    		buffer[2] = (byte) val;
    	}

    	sendData(Viewable.BYTE_SEQUENCE_TYPE, buffer);
    }
    */
    
    public void sendData(int type, byte[] data) {
		byte[] buffer = new byte[3];
		byte command = data[0];
		byte target  = data[1];
		byte value   = data[2];

		buffer[0] = command;
		buffer[1] = target;
		buffer[2] = (byte) value;
		/*
		if (outputStream_ != null && buffer[1] != -1) {
			try {
				outputStream_.write(buffer);
			} catch (IOException e) {
				showMessage("sendData fails: "+e.getMessage());
			}
		}
		*/
    }
    
    public void signalToUi(int type, Object data) {
		if (currentViewable_ != null) {
			currentViewable_.signalToUi(type, data);
		}
	}
    
    public void setExitOnDetach(boolean exitOnDetach) {
    	exitOnDetach_ = exitOnDetach;
    }
    
    public boolean getExitOnDetach() {
    	return exitOnDetach_;
    }
    
    public void logMessage(String msg) {
    	logMessage(msg, null);
    }
        
    public void logMessage(String msg, Exception e) {
    	if (debug_) {
    		Log.d(TAG, msg + "\n" + Utils.getExceptionStack(e, true));
    	}
    }
    
}