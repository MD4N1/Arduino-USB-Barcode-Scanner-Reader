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

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;

import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;
import com.victorint.android.usb.interfaces.Viewable;

import android.app.IntentService;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;
import android.widget.Toast;

public class ArduinoUsbService extends IntentService {

	private String TAG = "ArduinoUsbService";
	private static final int NOTIFICATION_ID = 123; 
    static final int MSG_REGISTER_CLIENT = 1;
    static final int MSG_UNREGISTER_CLIENT = 2;
    static final int MSG_SEND_ASCII_TO_CLIENT = 3;
    static final int MSG_SEND_BYTES_TO_CLIENT = 4;
    static final int MSG_SEND_ASCII_TO_SERVER = 5;
    static final int MSG_SEND_BYTES_TO_SERVER = 6;
    static final int MSG_SEND_ECHO_TO_SERVER = 7;
    static final int MSG_SEND_EXIT_TO_CLIENT  = 20;
    static final String MSG_KEY = "msg";
	
	ArrayList<Messenger> mClients = new ArrayList<Messenger>();
	
	private boolean accessoryDetached_ = false;
	private boolean canRun_ = false;

	private Thread connectingThread_;
	private String deviceName_;

	private UsbManager mUsbManager_;
	private UsbAccessory accessory_;
	private ParcelFileDescriptor descriptor_;
	private FileInputStream inputStream_;
	private FileOutputStream outputStream_;
	
	private boolean startApplication_ = true;
	private boolean stopApplication_ = true;
	private Class applicationClass_ = com.victorint.android.usb.ArduinoTerminalActivity.class;
	private Intent startApplicationIntent_;
	private boolean debug_ = false;

	public static String getAccessoryName(UsbAccessory accessory) {
		if (accessory == null) {
			return null;
		} 
		String tmpString = accessory.getDescription();
		if (tmpString != null && tmpString.length() > 0) {
			return tmpString;
		} else {
			return accessory.getModel() + " : " + accessory.getSerial();
		}
	}

    /**
     * Handler of incoming messages from clients.
     */
    class IncomingHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
        	try {
        	Log.d(TAG, "Service handleMessage: " + msg.what);
        	switch (msg.what) {
        	case MSG_REGISTER_CLIENT:
        		Log.d(TAG, "Service handleMessage: MSG_REGISTER_CLIENT " + msg.replyTo);
        		//if (!mClients.contains(msg.replyTo)) {
        		mClients.add(msg.replyTo);
        		if (debug_) {
        			Bundle cb = msg.getData();
        			CharSequence connectMessage = null;
        			if (deviceName_ != null) {
        				connectMessage = getResources().getString(R.string.connected_to_usb_device_message) +
        						": " + deviceName_ + "\n";
        			} else {
        				connectMessage = getResources().getString(R.string.no_usb_devices_attached_message) +
        						"\n";
        			}
        			cb.putCharSequence(ArduinoUsbService.MSG_KEY, connectMessage);
        			try {
        				Message msgToClient = Message.obtain(null,
        						ArduinoUsbService.MSG_SEND_ASCII_TO_CLIENT);
        				msgToClient.setData(cb);
        				msg.replyTo.send(msgToClient);

        				// Debug messages
        				/*
        				//String message1 = "123456789\rABCD\nabcdefghijk\n";
        				//sendMessageToClients(message1);
        				//String message2 = "1234\r5678";
        				//sendMessageToClients(message2);

        				byte[] msg2 = new byte[10];
					      msg2[0] = 'A';
					      msg2[1] = 'B';
					      msg2[2] = 'C';
					      msg2[3] = 'D';
					      msg2[4] = 'E';
					      msg2[5] = 'F';
					      msg2[6] = '\r';
					      msg2[7] = '1';
					      msg2[8] = '2';
					      msg2[9] = '3';
        				//msg2[10] = '3';
	        				sendBytesToClients(msg2);
	        				sendBytesToClients(msg2);
	        				sendBytesToClients(msg2);
	        				sendBytesToClients(msg2);
         				*/
       			} catch (RemoteException e) {
        				Log.d(TAG, "MSG_REGISTER_CLIENT: " + getExceptionStack(e, true));
        			}

        		}
        		break;
        	case MSG_UNREGISTER_CLIENT:
        		mClients.remove(msg.replyTo);
        		Log.d(TAG, "Service handleMessage: MSG_UNREGISTER_CLIENT " + msg.replyTo);
        		break;
        	case MSG_SEND_EXIT_TO_CLIENT:
        		Log.d(TAG, "Service handleMessage: MSG_SEND_EXIT_TO_CLIENT " + mClients.size());
        		for (int i=mClients.size()-1; i>=0; i--) {
        			try {
        				Message msgToClient = Message.obtain(null,
        						ArduinoUsbService.MSG_SEND_EXIT_TO_CLIENT);
        				mClients.get(i).send(msgToClient);
        			} catch (RemoteException e) {
        				// The client is dead.  Remove it from the list;
        				// we are going through the list from back to front
        				// so this is safe to do inside the loop.
        				mClients.remove(i);
        				Log.d(TAG, "MSG_SEND_EXIT_TO_CLIENT: " + getExceptionStack(e, true));
        			}
        		}
        		break;
        	case MSG_SEND_ASCII_TO_CLIENT:
        		Bundle b = msg.getData();
        		CharSequence asciiMessage = b.getCharSequence(ArduinoUsbService.MSG_KEY);
                	Log.d(TAG, "Service handleMessage: MSG_SEND_ASCII_TO_CLIENT " + asciiMessage);
                    for (int i=mClients.size()-1; i>=0; i--) {
                        try {
                			Message msgToClient = Message.obtain(null,
                					ArduinoUsbService.MSG_SEND_ASCII_TO_CLIENT, asciiMessage);
                			msgToClient.setData(b);

                            mClients.get(i).send(msgToClient);
                        } catch (RemoteException e) {
                            // The client is dead.  Remove it from the list;
                            // we are going through the list from back to front
                            // so this is safe to do inside the loop.
                            mClients.remove(i);
                            Log.d(TAG, "MSG_SEND_ASCII_TO_CLIENT: " + getExceptionStack(e, true));
                        }
                    }
                    break;
                case MSG_SEND_BYTES_TO_CLIENT:
                	Bundle bb = msg.getData();
                	byte[] data = bb.getByteArray(ArduinoUsbService.MSG_KEY);
                	int length = (data == null) ? -1 : data.length;
                	Log.d(TAG, "Service handleMessage: MSG_SEND_BYTES_TO_CLIENT " + length);
                    for (int i=mClients.size()-1; i>=0; i--) {
                        try {
                			Message msgToClient = Message.obtain(null,
                					ArduinoUsbService.MSG_SEND_BYTES_TO_CLIENT);
                			Bundle bc = new Bundle();
                            bc.putByteArray(ArduinoUsbService.MSG_KEY, data);
                            msgToClient.setData(bc);
                            mClients.get(i).send(msgToClient);
                        } catch (RemoteException e) {
                            // The client is dead.  Remove it from the list;
                            // we are going through the list from back to front
                            // so this is safe to do inside the loop.
                            mClients.remove(i);
                            Log.d(TAG, "MSG_SEND_BYTES_TO_CLIENT: " + getExceptionStack(e, true));
                        }
                    }
                    break;
                case MSG_SEND_ASCII_TO_SERVER:
                	Bundle sb = msg.getData();
                	CharSequence asciiServerMessage = sb.getCharSequence(ArduinoUsbService.MSG_KEY);
                	Log.d(TAG, "Service handleMessage: MSG_SEND_ASCII_TO_SERVER " + asciiServerMessage);

                	if (deviceName_ == null) {
                		String noconnectMessage = getResources().getString(R.string.no_usb_devices_attached_message) +
                		"\n";
                		sendMessageToClients(noconnectMessage);
                		/*
        				byte[] msg2 = new byte[10];
        				msg2[0] = 'A';
        				msg2[1] = 'B';
        				msg2[2] = 'C';
        				msg2[3] = 'D';
        				msg2[4] = 'E';
        				msg2[5] = 'F';
        				msg2[6] = '\r';
        				msg2[7] = '0';
        				msg2[8] = '1';
        				msg2[9] = '2';
        				//msg2[10] = '3';
        				sendBytesToClients(msg2);
						*/
                		//sendMessageToClients("0123456789"+ '\r' + "ABCD" + '\n' + "abcdefghijklmnop" + '\n');
                	} else {
                		byte[] buffer = asciiServerMessage.toString().getBytes();
                		try {
                			if (outputStream_ != null && buffer[1] != -1) {
                				try {
                					outputStream_.write(buffer);
                				} catch (IOException e) {
                					Log.d(TAG, "Send Data to USB fails: " + getExceptionStack(e, true));
                					sendMessageToClients("Send Data to USB fails: "+e.getMessage());
                					disconnect();
                				}
                			}

                		} catch (Exception e) {
                			Log.d(TAG, "MSG_SEND_ASCII_TO_SERVER: " + getExceptionStack(e, true));
                			sendMessageToClients("Exception " + e.getMessage());
                		}
                	}
                	break;
                case MSG_SEND_BYTES_TO_SERVER:
                	Bundle sbb = msg.getData();
                	byte[] sData = sbb.getByteArray(ArduinoUsbService.MSG_KEY);
                	int sLength = (sData == null) ? -1 : sData.length;
                	Log.d(TAG, "Service handleMessage: MSG_SEND_BYTES_TO_SERVER " + sLength);
                	if (deviceName_ == null) {
                		String noconnectMessage = getResources().getString(R.string.no_usb_devices_attached_message) +
                		"\n";
                		sendMessageToClients(noconnectMessage);
                	} else {
                		try {
                			if (outputStream_ != null && sData[1] != -1) {
                				try {
                					outputStream_.write(sData);
                				} catch (IOException e) {
                					Log.d(TAG, "Send Data to USB fails: " + getExceptionStack(e, true));
                					sendMessageToClients("Send Data to USB fails: "+e.getMessage());
                					disconnect();
                				}
                			}

                		} catch (Exception e) {
                			Log.d(TAG, "MSG_SEND_ASCII_TO_SERVER: " + getExceptionStack(e, true));
                			sendMessageToClients("Exception " + e.getMessage());
                		}
                	}
                	break;
                case MSG_SEND_ECHO_TO_SERVER:
                	Bundle eb = msg.getData();
                	byte[] eData = eb.getByteArray(ArduinoUsbService.MSG_KEY);
                	int eLength = (eData == null) ? -1 : eData.length;
                	Log.d(TAG, "Service handleMessage: MSG_SEND_ECHO_TO_SERVER " + eLength);
                	sendBytesToClients(eData);
                	break;
                default:
                	super.handleMessage(msg);
            }
       } catch (Exception ee) {
    		if (debug_) {
    			Log.e(TAG, "Server handleMessage Exception: "+ Utils.getExceptionStack(ee, true));
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
     * When binding to the service, we return an interface to our messenger
     * for sending messages to the service.
     */
    @Override
    public IBinder onBind(Intent intent) {
        return mMessenger_.getBinder();
    }

	// We use this to catch the USB accessory detached message
	private BroadcastReceiver mUsbReceiver_ = new BroadcastReceiver() {
		public void onReceive(Context context, Intent intent) {
			final String TAG = "mUsbReceiver";

			String action = intent.getAction(); 

			Log.d(TAG,"onReceive entered: " + action);

			if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = UsbManager.getAccessory(intent);

				Log.d(TAG,"Accessory detached");	        	

				// TODO: Check it's us here?

				accessoryDetached_ = true;

				unregisterReceiver(mUsbReceiver_);

				if (accessory != null) {
					// TODO: call method to clean up and close communication with the accessory?
				}
			}

			Log.d(TAG,"onReceive exited");
		}
	};


	public ArduinoUsbService() {
		super("ArduinoUsbService");
	}

	Notification getNotification() {

		if (mUsbManager_ == null) {
			mUsbManager_ = UsbManager.getInstance(this);
		}
		int perm = 0;
		UsbAccessory[] accessories = mUsbManager_.getAccessoryList();
		accessory_ = (accessories == null ? null : accessories[0]);
		if (accessory_ != null) {
			if (mUsbManager_.hasPermission(accessory_)) {
				perm = 10;
			} else {
				perm = 1;
			}
		} else {
			perm = -1;
		}
		Log.d(TAG, "getNotification before notification: perm="+perm+", mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);

		Notification notification = new Notification(R.drawable.notification,
				getResources().getString(R.string.usb_device_attached), System.currentTimeMillis());

		Context context = getApplicationContext();
		CharSequence contentTitle = getResources().getString(R.string.usb_device_attached) + " " + perm;
		CharSequence contentText = getAccessoryName(accessory_);

		// This can be changed if we want to launch an activity when notification clicked
		Intent notificationIntent = new Intent();

		PendingIntent contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0);

		notification.setLatestEventInfo(context, contentTitle, contentText, contentIntent);		

		return notification;
	}

	@Override
	protected void onHandleIntent(Intent arg0) {

		Log.d(TAG, "onHandleIntent entered: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);

		startForeground(NOTIFICATION_ID, getNotification());

		// Register to receive detached messages
		IntentFilter filter = new IntentFilter(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver_, filter);

		//if (mUsbManager_ == null) {
			mUsbManager_ = UsbManager.getInstance(this);
		//}
		//if (accessory_ == null) {
			UsbAccessory[] accessories = mUsbManager_.getAccessoryList();
			accessory_ = (accessories == null ? null : accessories[0]);
		//}
		int perm = 0;
		deviceName_ = getAccessoryName(accessory_);

		if (accessory_ == null) {
			accessoryDetached_ = true;
			sendMessageToClients(getResources().getString(R.string.no_usb_devices_attached_message) + "\n");
		} else if (!mUsbManager_.hasPermission(accessory_)) {
			accessoryDetached_ = true;
			sendMessageToClients(getResources().getString(R.string.no_permissions_for_this_usb_device_message) + ": " + deviceName_ + "\n");
		} else {
			accessoryDetached_ = false;
			String message = getResources().getString(R.string.connected_to_usb_device_message) + ": " + deviceName_ + "\n";
			sendMessageToClients(message);
			connect();
		}


		int count = 0;
		while(!accessoryDetached_) {
			// Wait until the accessory detachment is flagged
			if (accessoryDetached_) {
				break;
			}

			// In reality we'd do stuff here.

			SystemClock.sleep(300);
			/*
			count++;
			try {
				if (count == 10) {
					String message = "Ping :: " + System.currentTimeMillis();
					String message2 = "Ping2 :: " + System.currentTimeMillis();
					Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
					Log.d(TAG, message);
					sendMessageToClients(message);
					sendBytesToClients(message2.getBytes());
					count = 0;
				}
			} catch (Exception e) {
				Toast.makeText(this, "Exception in sleep: " + e.getMessage(), Toast.LENGTH_SHORT).show();
			}
			*/
		}

		//unregisterReceiver(mUsbReceiver_);
		mUsbReceiver_ = null;

		String message = "\n" + getResources().getString(R.string.usb_device_detached) + ": " + deviceName_ + "\n";
		sendMessageToClients(message);

		disconnect();
		stopForeground(true);

		Log.d(TAG, "onHandleIntent exited: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);
		stopSelf();

	}
	

	private void connect() {
		Log.d(TAG, "connect entered: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);
		disconnect(false);
		
		accessoryDetached_ = false;
		
		try {
			mUsbManager_ = UsbManager.getInstance(this);
			UsbAccessory[] accessories = mUsbManager_.getAccessoryList();
			accessory_ = (accessories == null ? null : accessories[0]);
			deviceName_ = getAccessoryName(accessory_);
			
			//Toast.makeText(this, "Connecting to: " + deviceName_, Toast.LENGTH_SHORT).show();
			//sendMessageToClients("Connecting to: " + deviceName_);
			if (accessory_ == null) {
				accessoryDetached_ = true;
				return;
			}
			
			
			descriptor_ = mUsbManager_.openAccessory(accessory_);
			FileDescriptor fd = descriptor_.getFileDescriptor();
			inputStream_  = new FileInputStream(fd);  
			outputStream_ = new FileOutputStream(fd); 

			connectingThread_ = new Thread(new Runnable() {

				@Override
				public void run() {
					try {
						doWork();
					} catch (Exception ee) {
						Log.d(TAG, "doWork fail: " + getExceptionStack(ee, true));
						//Toast.makeText(ArduinoUsbService.this, "doWork fail: " + ee.getMessage(), Toast.LENGTH_SHORT).show();
						sendMessageToClients("doWork fail: " + ee.getMessage() + "\n");
					}
				}
			});
			canRun_ = true;
			connectingThread_.start();
			

			if (startApplication_) {
				startApplicationIntent_ = new Intent(this, applicationClass_);
				startApplicationIntent_.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
					| Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);

				startActivity(startApplicationIntent_);
			}
		} catch (Exception e) {
			Log.d(TAG, "Connect fail: " + getExceptionStack(e, true));
			sendMessageToClients("Connect fail: " + e.getMessage() + "\n");
		}
		Log.d(TAG, "connect exited: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);
	}

	public void disconnect() {
		disconnect(stopApplication_);
	}
		
	public void disconnect(boolean stopApp) {
		Log.d(TAG, "disconnect entered: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);
		if (debug_) sendMessageToClients(getResources().getString(R.string.disconnected_from_usb_device_message)
				+ ": " + deviceName_ + "\n");

		canRun_ = false;
		try {
			try {
				if (connectingThread_ != null) {
					connectingThread_.stop();
					connectingThread_ = null;
				}
			} catch (Exception e) {
				Log.d(TAG, "Stop Thread fail: " + getExceptionStack(e, true));
				sendMessageToClients("Stop Thread fail: "+e.getMessage());
			}

			if (descriptor_ != null) {
				descriptor_.close();
			}
		} catch (IOException e) {
			Log.d(TAG, "Disconnect Exception: " + getExceptionStack(e, true));
			sendMessageToClients("Disconnect Exception: "+e.getMessage());

		} finally {
			mUsbManager_	= null;
			descriptor_ 	= null;
			accessory_  	= null;
			deviceName_ 	= null;
			outputStream_ 	= null;
			inputStream_ 	= null;
		}

		if (stopApp) {
			try {
				sendExitToClients();
			} catch (Exception e) {
				Log.d(TAG, "Stop Application fail: " + getExceptionStack(e, true));
				sendMessageToClients("Stop Application fail: " + getExceptionStack(e, true));
			}

		}
		Log.d(TAG, "disconnect exited");
	}

	public void doWork() {
		Log.d(TAG, "doWork entered: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);
		int ret = 0;
		byte[] buffer = new byte[16384];
		int i;

		while (ret >= 0 & canRun_) {
			try {
				ret = inputStream_.read(buffer);
			} catch (IOException e) {
				break;
			}
			byte[] data = Arrays.copyOf(buffer, ret);
			
			/*
    		StringBuilder buffer2 = new StringBuilder();
    		for (int ii=0; ii<ret; ii++) {
    			buffer2.append(Integer.toHexString(buffer[ii]) + " " );
    		}
    		String tmpData2 = buffer2.toString() +"\n";

			sendMessageToClients(tmpData2);
			
    		StringBuilder buffer3 = new StringBuilder();
    		for (int ii=0; ii<data.length; ii++) {
    			buffer3.append(Integer.toHexString(data[ii]) + " " );
    		}
    		String tmpData3 = buffer3.toString() +"\n";

			sendMessageToClients(tmpData3);
			*/
			sendBytesToClients(data);
			/*
			byte[] msg2 = new byte[10];
			msg2[0] = 'A';
			msg2[1] = 'B';
			msg2[2] = 'C';
			msg2[3] = 'D';
			msg2[4] = 'E';
			msg2[5] = 'F';
			msg2[6] = '\r';
			msg2[7] = '1';
			msg2[8] = '2';
			msg2[9] = '3';
			sendBytesToClients(msg2);
			*/
		}
		Log.d(TAG, "doWork exited: mUsbManager="+mUsbManager_+", accessory="+accessory_+", descriptor="+descriptor_+", inputStream="+inputStream_+", outputStream="+outputStream_);
	}


	public void sendMessageToClients(String message) {
		try {
			Message msg = Message.obtain(null,
					ArduinoUsbService.MSG_SEND_ASCII_TO_CLIENT, message);
			Bundle b = new Bundle();
            b.putCharSequence(ArduinoUsbService.MSG_KEY, message);
            msg.setData(b);
			msg.replyTo = mMessenger_;
			mMessenger_.send(msg);
		} catch (RemoteException e) {
			Log.d(TAG, "sendMessageToClients Exception: " + getExceptionStack(e, true));
		}
	}

	public void sendBytesToClients(byte[] data) {
		try {
			Message msg = Message.obtain(null,
					ArduinoUsbService.MSG_SEND_BYTES_TO_CLIENT);
			Bundle b = new Bundle();
            b.putByteArray(ArduinoUsbService.MSG_KEY, data);
            msg.setData(b);
			msg.replyTo = mMessenger_;
			mMessenger_.send(msg);
		} catch (RemoteException e) {
			Log.d(TAG, "sendBytesToClients Exception: " + getExceptionStack(e, true));
		}
	}

	public void sendExitToClients() {
		try {
			Message msg = Message.obtain(null,
					ArduinoUsbService.MSG_SEND_EXIT_TO_CLIENT);
			msg.replyTo = mMessenger_;
			mMessenger_.send(msg);
		} catch (RemoteException e) {
		}
	}

	public static String getExceptionStack(Exception e, boolean getMessage) {
		if (e == null) return null;
		String tmp = null;
		
		try {
		StringWriter w = new StringWriter();
		e.printStackTrace(new PrintWriter(w));
		if (!getMessage) {
			return w.toString();
		} else {
			return e.getMessage() + "\n" + w.toString();
		}
		} catch (Exception ee) {
			return " Exception in getExceptionStack: " + ee.getMessage();
		}
		/*
		StringBuilder buffer = new StringBuilder();
		buffer.append(e.getMessage());
		buffer.append("\n");
		
		StackTraceElement[] elements = e.getStackTrace();
		if (elements != null && elements.length > 0) {
			for (int i=0; i<elements.length; i++) {
				if (elements[i] != null) {
					buffer.append(elements[i].toString());
					buffer.append("\n");
				}
			}
		}
		return buffer.toString();
		*/
	}
}
