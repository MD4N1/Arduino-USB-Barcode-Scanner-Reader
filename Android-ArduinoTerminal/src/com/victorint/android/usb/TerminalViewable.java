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

import com.victorint.android.usb.interfaces.Connectable;
import com.victorint.android.usb.interfaces.Viewable;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.InputType;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;

/**
 * Implementation of Viewable interface that emulates Terminal.
 * Delegates all Activity-specific operations to the Activity.
 */
public class TerminalViewable implements Viewable {
	
	private static final String TAG = "TerminalViewable";
	private static final int scrollDelay = 300;
	static final String CommandStoreKey 	= "TerminalViewable.CommandStoreKey";
	static final String HistoryStoreKey 	= "TerminalViewable.HistoryStoreKey";
	static final String InputStateStoreKey 	= "TerminalViewable.InputStateStoreKey";
	static final String CursorStateStoreKey = "TerminalViewable.CursorStateStoreKey";
	static String CommandStore;
	static String HistoryStore;
	static int 	  InputStateStore;
	static int 	  CursorStateStore;

	private Activity activity_;
	private Button clearButton_;
	private ImageButton toggleInputButton_;
	private Button sendButton_;
	private EditText commandText_;
	private TextView historyBuffer_;
	private ScrollView scrollView_;
    private boolean customTitleSupported_;
	private TextView customTitle_;
	private int cursorPosition_ = 0;
	
	// Preferences
	private String displayOrientation_ = null;
	private boolean localEcho_ 			= true;
	private int fontSize_ 				= 13;
	private String fontType_ 			= null;
	private String displayColors_		= null;
	private String clearAction_			= null;
	private boolean cleadDisplay_		= true;
	private boolean cleadInput_			= false;
	private boolean showKeyboardWithInput_ = false;
	private int keyboardInputType_		= 1;
	private int messageLevel_			= 22;
	
    /** Called when the activity is first created. */
    @Override
    public void setActivity(Activity activity) {
        if (activity_ == activity) {
        	return;
        }
    	
        if (activity_ != null) {
        	saveState();
        } 
        
        activity_ = activity;
        
        customTitleSupported_ = activity_.requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
    	activity_.setContentView(R.layout.terminal);
        if ( customTitleSupported_ ) {
        	activity_.getWindow().setFeatureInt(Window.FEATURE_CUSTOM_TITLE, R.layout.titlebar);
        }

        commandText_    = (EditText) activity_.findViewById(R.id.editText1);
        commandText_.setInputType(InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        commandText_.setOnEditorActionListener(new OnEditorActionListener() {

        	@Override
        	public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
        		if (actionId == EditorInfo.IME_ACTION_SEND) {
        			//InputMethodManager imm = (InputMethodManager)v.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        			//imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        			onSendAction(view);
        			return true;	
        		}
        		return false;
        	}});
        
        commandText_.setOnTouchListener(new OnTouchListener(){
        	
			@Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
				scrollDownWithDelay(scrollDelay);
				return false;                                                       
            }

        });
        
        historyBuffer_  = (TextView) activity_.findViewById(R.id.textView22);
        historyBuffer_.setBackgroundColor(Color.rgb(200, 200, 200));
        historyBuffer_.setTextColor(Color.rgb(0, 0, 0));
        
        clearButton_ = (Button) activity_.findViewById(R.id.button21);
        clearButton_.setOnClickListener(new View.OnClickListener() {
	        public void onClick(View view) {
	        	onClearAction(view);
	        }
        });
        
        sendButton_ = (Button) activity_.findViewById(R.id.button22);
        sendButton_.setOnClickListener(new View.OnClickListener() {
	        public void onClick(View view) {
	        	onSendAction(view);
	        }
        });
        
        scrollView_ = (ScrollView) activity_.findViewById(R.id.scrollView1);
        
        customTitle_ = (TextView) activity_.findViewById(R.id.customTitle);
        
        toggleInputButton_ = (ImageButton) activity_.findViewById(R.id.toggleInputButton);
        toggleInputButton_.setOnClickListener(new View.OnClickListener() {
	        public void onClick(View view) {
	        	onToggleInputAction(view);
	        }
        });
 
        setInitialView();
        
        LinearLayout inputLayout = (LinearLayout) activity_.findViewById(R.id.inputLayout);
    	inputLayout.setVisibility(View.GONE);   
    	
        enableUiControls(false);
        
    	readState();
    	setViewFromPreferences();
    }
    
    private void setInitialView() {
    	try {
			if (fontSize_ <= 12) {
				commandText_.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (float) 13);
			} else if (fontSize_ >= 18) {
				commandText_.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (float) 18);
			} else {
				commandText_.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (float) fontSize_);
			}
    		historyBuffer_.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (float) fontSize_);
    		//clearButton_.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) fontSize_);
    		//sendButton_.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) fontSize_);

    		Resources res 		= activity_.getResources();
    		String[] tmpArray 	= res.getStringArray(R.array.pref_font_typeface_entries); 
    		if (fontType_ == null) {
    			fontType_ = tmpArray[0];
    		}
    		Typeface tf = Typeface.MONOSPACE;
			if (tmpArray[1].equalsIgnoreCase(fontType_)) {
				tf = Typeface.SANS_SERIF;
			} else if (tmpArray[2].equalsIgnoreCase(fontType_)) {
				tf = Typeface.SERIF;
			}
    		commandText_.setTypeface(tf);
    		historyBuffer_.setTypeface(tf);

    		tmpArray  			= res.getStringArray(R.array.pref_display_colors_entries); 
    		if (displayColors_ == null) {
    			displayColors_ = tmpArray[0];
    		}
			int bg = Color.BLACK;
			int fg = Color.WHITE;
			if (tmpArray[0].equalsIgnoreCase(displayColors_)) {
    			// Black on White
				fg = Color.BLACK;
				bg = Color.WHITE;
			} else if (tmpArray[1].equalsIgnoreCase(displayColors_)) {
				// White on Black
				fg = Color.WHITE;
				bg = Color.BLACK;
			} else if (tmpArray[2].equalsIgnoreCase(displayColors_)) {
				// Green on Black
				fg = Color.GREEN;
				bg = Color.BLACK;
			} else if (tmpArray[3].equalsIgnoreCase(displayColors_)) {
				// White on Blue
				fg = Color.WHITE;
				bg = Color.BLUE;
			}
			commandText_.setTextColor(fg);
			commandText_.setBackgroundColor(bg);
			historyBuffer_.setTextColor(fg);
			historyBuffer_.setBackgroundColor(bg);
			LinearLayout inputLayout = (LinearLayout) activity_.findViewById(R.id.inputLayout);
			inputLayout.setBackgroundColor(fg);
    		
    		tmpArray =res.getStringArray(R.array.pref_display_orientation_entries); 
    		if (displayOrientation_ == null) {
    			displayOrientation_ = tmpArray[0];
    		}
    		int tmpInt = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    		if (tmpArray[0].equalsIgnoreCase(displayOrientation_)) {
    			tmpInt = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    		} else if (tmpArray[1].equalsIgnoreCase(displayOrientation_)) {
    			tmpInt = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    		} else if (tmpArray[2].equalsIgnoreCase(displayOrientation_)) {
    			tmpInt = ActivityInfo.SCREEN_ORIENTATION_SENSOR;
    		}
    		((Activity) activity_).setRequestedOrientation(tmpInt);

    		tmpArray =res.getStringArray(R.array.pref_clear_action_entries); 
    		if (clearAction_ == null) {
    			clearAction_ = tmpArray[0];
    		}
    		if (tmpArray[0].equalsIgnoreCase(clearAction_)) {
    			cleadDisplay_ 	= true;
    			cleadInput_ 	= false;
    		} else if (tmpArray[1].equalsIgnoreCase(clearAction_)) {
    			cleadDisplay_ 	= false;
    			cleadInput_ 	= true;
    		} else if (tmpArray[2].equalsIgnoreCase(clearAction_)) {
    			cleadDisplay_ 	= true;
    			cleadInput_ 	= true;
    		}
    		
			if (keyboardInputType_ == 0) {
				commandText_.setRawInputType(Configuration.KEYBOARD_12KEY);
			} else if (keyboardInputType_ == 1) {
				commandText_.setRawInputType(Configuration.KEYBOARD_NOKEYS);
			}

    		((UsbActivity) activity_).setExitOnDetach(true);
    		
    	} catch (Exception e) {
    		((UsbActivity) activity_).logMessage("setInitialView Exception: ", e);
    	}
    	scrollDown1();
    }

    private void setViewFromPreferences() {
    	if (activity_ == null) return;
    	try {
    		Resources res = activity_.getResources();
    		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(activity_);

    		// Set Local Echo
    		localEcho_ = prefs.getBoolean(res.getString(R.string.pref_local_echo_key), true);

    		// Set Font Size
    		String tmpString = prefs.getString(res.getString(R.string.pref_font_size_key), "13");
    		int tmpInt = Integer.parseInt(tmpString);
    		if (tmpInt != fontSize_) {
    			fontSize_ = tmpInt;
    			if (fontSize_ <= 12) {
    				commandText_.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (float) 13);
    			} else if (fontSize_ >= 18) {
    				commandText_.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (float) 18);
    			} else {
    				commandText_.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (float) fontSize_);
    			}
    			historyBuffer_.setTextSize(TypedValue.COMPLEX_UNIT_DIP, (float) fontSize_);
        		//clearButton_.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) fontSize_);
        		//sendButton_.setTextSize(TypedValue.COMPLEX_UNIT_PX, (float) fontSize_);
    		}

    		// Set Font Type, default is Monospace
    		String[] tmpArray =res.getStringArray(R.array.pref_font_typeface_entries); 
    		tmpString = prefs.getString(res.getString(R.string.pref_font_typeface_key), tmpArray[0]);
    		if (!fontType_.equalsIgnoreCase(tmpString)) {
    			fontType_ = tmpString;
    			Typeface tf = Typeface.MONOSPACE;
    			if (tmpArray[1].equalsIgnoreCase(tmpString)) {
    				tf = Typeface.SANS_SERIF;
    			} else if (tmpArray[2].equalsIgnoreCase(tmpString)) {
    				tf = Typeface.SERIF;
    			}
    			commandText_.setTypeface(tf);
    			historyBuffer_.setTypeface(tf);
    		}
    		
    		// Set Display Colors
    		tmpArray = res.getStringArray(R.array.pref_display_colors_entries); 
    		tmpString = prefs.getString(res.getString(R.string.pref_display_colors_key), tmpArray[0]);
    		if (!displayColors_.equalsIgnoreCase(tmpString)) {
    			int bg = Color.BLACK;
    			int fg = Color.WHITE;
    			displayColors_ = tmpString;
    			if (tmpArray[0].equalsIgnoreCase(tmpString)) {
        			// Black on White
    				fg = Color.BLACK;
    				bg = Color.WHITE;
    			} else if (tmpArray[1].equalsIgnoreCase(tmpString)) {
    				// White on Black
    				fg = Color.WHITE;
    				bg = Color.BLACK;
    			} else if (tmpArray[2].equalsIgnoreCase(tmpString)) {
    				// Green on Black
    				fg = Color.GREEN;
    				bg = Color.BLACK;
    			} else if (tmpArray[3].equalsIgnoreCase(tmpString)) {
    				// White on Blue
    				fg = Color.WHITE;
    				bg = Color.BLUE;
    			}
    			commandText_.setTextColor(fg);
    			commandText_.setBackgroundColor(bg);
    			historyBuffer_.setTextColor(fg);
    			historyBuffer_.setBackgroundColor(bg);
    			LinearLayout inputLayout = (LinearLayout) activity_.findViewById(R.id.inputLayout);
    			inputLayout.setBackgroundColor(fg);
    		}

    		// Set Display Orientation
    		tmpArray =res.getStringArray(R.array.pref_display_orientation_entries); 
    		tmpString = prefs.getString(res.getString(R.string.pref_display_orientation_key), tmpArray[0]);
    		tmpInt = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    		if (!displayOrientation_.equalsIgnoreCase(tmpString)) {
    			displayOrientation_ = tmpArray[0];
    			if (tmpArray[0].equalsIgnoreCase(tmpString)) {
    				displayOrientation_ = tmpArray[0];
    				tmpInt = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    			} else if (tmpArray[1].equalsIgnoreCase(tmpString)) {
    				displayOrientation_ = tmpArray[1];
    				tmpInt = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
    			} else if (tmpArray[2].equalsIgnoreCase(tmpString)) {
    				displayOrientation_ = tmpArray[2];
    				tmpInt = ActivityInfo.SCREEN_ORIENTATION_SENSOR;
    			}
    			((Activity) activity_).setRequestedOrientation(tmpInt);
    		}

    		// Set Exit on Detach
    		boolean tmpBool = prefs.getBoolean(res.getString(R.string.pref_close_on_detach_key), true);
    		((UsbActivity) activity_).setExitOnDetach(tmpBool);
    		
    		// Set clear action
    		tmpArray = res.getStringArray(R.array.pref_clear_action_entries); 
    		tmpString = prefs.getString(res.getString(R.string.pref_clear_action_key), tmpArray[0]);
    		if (!tmpString.equalsIgnoreCase(clearAction_)) {
    			clearAction_ = tmpString;
    			if (tmpArray[0].equalsIgnoreCase(clearAction_)) {
    				cleadDisplay_ 	= true;
    				cleadInput_ 	= false;
    			} else if (tmpArray[1].equalsIgnoreCase(clearAction_)) {
    				cleadDisplay_ 	= false;
    				cleadInput_ 	= true;
    			} else if (tmpArray[2].equalsIgnoreCase(clearAction_)) {
    				cleadDisplay_ 	= true;
    				cleadInput_ 	= true;
    			}
    		}

    		
    		// Set message level 
    		//tmpString = prefs.getString(res.getString(R.string.pref_show_messages_key), "22");
    		//if (tmpString != null) {
    		//	messageLevel_ = Integer.parseInt(tmpString);
    		//}
    		
    		// Set show keyboard with input
    		showKeyboardWithInput_ = prefs.getBoolean(res.getString(R.string.pref_show_keyboard_key), false);
    		
    		// Set input keyboard type
    		tmpString = prefs.getString(res.getString(R.string.pref_keyboard_type_key), "1");
    		tmpInt = Integer.parseInt(tmpString);
    		if (keyboardInputType_ != tmpInt) {
    			keyboardInputType_ = tmpInt;
    			if (keyboardInputType_ == 0) {
    				commandText_.setRawInputType(Configuration.KEYBOARD_12KEY);
    			} else if (keyboardInputType_ == 1) {
    				commandText_.setRawInputType(Configuration.KEYBOARD_NOKEYS);
    			}
    		}

    	} catch (Exception e) {
    		((UsbActivity) activity_).logMessage("setViewFromPreferences Exception: ", e);
    	}
    	
    	scrollDown1();
    }
    
    public void readState() {
        if (activity_ == null) {
        	return;
        } 
        
    	SharedPreferences settings = activity_.getPreferences(android.content.Context.MODE_PRIVATE);
    	CommandStore = settings.getString(CommandStoreKey, null);
        if (CommandStore == null || CommandStore.length() == 0) {
        	commandText_.setText("");
        } else {
        	commandText_.setText(CommandStore);
        }
        
        CursorStateStore = settings.getInt(CursorStateStoreKey, 0);
        cursorPosition_ = CursorStateStore;
        
        HistoryStore = settings.getString(HistoryStoreKey, null);
        if (HistoryStore == null || HistoryStore.length() == 0) {
        	historyBuffer_.setText("");
        } else {
        	historyBuffer_.setText(HistoryStore);
        }
        
        InputStateStore = settings.getInt(InputStateStoreKey, View.GONE);
        setInputState(InputStateStore);
        
    }
    
    public void saveState() {
        if (activity_ == null) {
        	return;
        } 
        
    	CharSequence text = commandText_.getText();
        if (text == null || text.length() == 0) {
        	text = "";
        } 
    	CommandStore = text.toString().trim();
    	
    	
    	text = historyBuffer_.getText();
    	CursorStateStore = cursorPosition_;
        if (text == null || text.length() == 0) {
        	text = "";
        } 
    	HistoryStore = text.toString().trim();
       	
    	int inputState = View.GONE;
    	if (activity_ != null) {
    		LinearLayout inputLayout = (LinearLayout) activity_.findViewById(R.id.inputLayout);
    		inputState = inputLayout.getVisibility();
    	}
    	
    	SharedPreferences settings = activity_.getPreferences(android.content.Context.MODE_PRIVATE);
       	Editor editor = settings.edit();
        editor.putString(CommandStoreKey, CommandStore);
        editor.putString(HistoryStoreKey, HistoryStore);
        editor.putInt(InputStateStoreKey, inputState);
        editor.putInt(CursorStateStoreKey, CursorStateStore);
        editor.commit();
    }
    
    public void onClearAction(View view) {
    	clearAllText();
    	//signalToUi(Viewable.CONNECTION_ACTION, null);
    }
    
    public void onToggleInputAction(View view) {
    	LinearLayout inputLayout = (LinearLayout) activity_.findViewById(R.id.inputLayout);
    	
    	if (inputLayout.getVisibility() == View.VISIBLE) {
    		setInputState(View.GONE);
    	} else {
    		setInputState(View.VISIBLE);
    	}
    }
    
    private void setInputState(int state) {
    	LinearLayout inputLayout = (LinearLayout) activity_.findViewById(R.id.inputLayout);
    	if (state == View.VISIBLE) {
    		inputLayout.setVisibility(View.VISIBLE);
    		if (showKeyboardWithInput_) {
    			InputMethodManager imm = (InputMethodManager) activity_.getSystemService(Context.INPUT_METHOD_SERVICE);
        		//imm.showSoftInputFromInputMethod(commandText_.getWindowToken(), InputMethodManager.SHOW_IMPLICIT);
        		imm.showSoftInput(commandText_, InputMethodManager.SHOW_IMPLICIT);
    		}
    	} else {
    		inputLayout.setVisibility(View.GONE);
    		if (activity_ != null) {
    			InputMethodManager imm = (InputMethodManager) activity_.getSystemService(Context.INPUT_METHOD_SERVICE);
        		imm.hideSoftInputFromWindow(commandText_.getWindowToken(), 0);
        		//imm.hideSoftInputFromWindow(commandText_.getWindowToken(), InputMethodManager.HIDE_IMPLICIT_ONLY);
                //imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
    			//activity_.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
    		}
    	}
		scrollDownWithDelay(scrollDelay);
    }
    
    private void clearAllText() {
    	if (cleadInput_) commandText_.setText("");
    	if (cleadDisplay_) historyBuffer_.setText("");
    	cursorPosition_ = 0;
    }
    
    public void onSendAction(View view) {
    	CharSequence text = commandText_.getText();

    	if (text == null || text.length() == 0) {

    	}

    	if (localEcho_) {
        	if (text.charAt(0) != '\n') {
        		CharSequence allText = historyBuffer_.getText();
        		if (allText != null && allText.length() > 0 && allText.charAt(allText.length()-1) != '\n') {
        			text = "\n" + text;
        		}
        	}
    		addToHistory(text + "\n");
    	}

    	if (activity_ instanceof Connectable) {
    		((Connectable) activity_).sendData(text + "\r\n");
    	}		
    }

    public void signalToUi(int type, Object data) {
    	Runnable runnable = null;
    	if (type == Viewable.CLEAR_UI_TYPE) {
    		runnable = new Runnable() {
    			public void run() {
    				clearAllText();
    			}
    		};
    	} else if (type == Viewable.CONNECTION_ACTION) {
    		if (activity_ instanceof UsbActivity) {
	    		runnable = new Runnable() {
	    			public void run() {
	    				((UsbActivity) activity_).onConnectMenu();
	    			}
	    		};
	    		}
    	} else if (type == Viewable.ENABLE_CONTROLS_TYPE) {
    		runnable = new Runnable() {
    			public void run() {
    				enableUiControls(true);
    			}
    		};
    	} else if (type == Viewable.SHOW_LATEST_MESSAGES) {
    		if (data == null) {
    			scrollDown1();
    		} else if (data instanceof Integer) {
    			int delay = ((Integer) data).intValue();
    			scrollDownWithDelay(delay);
    		}
       	} else if (type == Viewable.CHANGE_TITLE_TYPE) {
       		if (!customTitleSupported_) {
       			return;
       		}
       		final CharSequence tmpData = (CharSequence) data;
    		runnable = new Runnable() {
    			public void run() {
    		           if ( customTitle_ != null ) {
    		        	   customTitle_.setText((CharSequence) tmpData);
    		           }
    			}
    		};
       	} else if (type == Viewable.DISABLE_CONTROLS_TYPE) {
    		runnable = new Runnable() {
    			public void run() {
    				enableUiControls(false);
    			}
    		};
       	} else if (type == Viewable.SET_VIEW_FROM_PREFERENCES_TYPE) {
    		runnable = new Runnable() {
    			public void run() {
    				setViewFromPreferences();
    			}
    		};
    	} else if (type == Viewable.CHAR_SEQUENCE_TYPE) {
    		if (data == null || ((CharSequence) data).length() == 0) {
    			return;
    		}
    		final CharSequence tmpData = (CharSequence) data;
    		runnable = new Runnable() {
    			public void run() {
    				addToHistory(tmpData);
    			}
    		};
    	} else if ( 
    			( type == Viewable.DEBUG_MESSAGE_TYPE || type == Viewable.INFO_MESSAGE_TYPE ) &&
    			( type <= messageLevel_ )
    			) {
    		if (data == null || ((CharSequence) data).length() == 0) {
    			return;
    		}
    		final CharSequence tmpData = (CharSequence) data;
    		runnable = new Runnable() {
    			public void run() {
    				addToHistory(tmpData);
    			}
    		};
    	} else if (type == Viewable.BYTE_SEQUENCE_TYPE) {
    		if (data == null || ((byte[]) data).length == 0) {
    			return;
    		}
    		final byte[] byteArray = (byte[]) data;
    		runnable = new Runnable() {
    			public void run() {
    				addToHistory(byteArray);
    			}
    		};
    	}
    	
    	if (runnable != null) {
    		activity_.runOnUiThread(runnable);
    	}
    	
    }
    
    private void addToHistory(CharSequence text) {
    	if (text == null || text.length() == 0) return;
    	
       	if (((UsbActivity) activity_).debug_) {
    		Log.i(TAG, "addToHistoryC1: text="+text+", cursorPosition="+cursorPosition_);
    	}
     	if (text.charAt(0) != '\n') {
    		CharSequence allText = historyBuffer_.getText();
    		if (allText.length() > 0 && allText.charAt(allText.length()-1) != '\n') {
    			historyBuffer_.append("\n");
    		}
    	}
    	historyBuffer_.append(text);
    	cursorPosition_ = historyBuffer_.getText().length();

       	if (((UsbActivity) activity_).debug_) {
    		Log.i(TAG, "addToHistoryC2: cursorPosition="+cursorPosition_+", ALL_TEXT="+historyBuffer_.getText());
    	}
       	scrollDown1();
    }
        
    private void addToHistory(byte[] data) {
    	if (data == null || data.length == 0) {
    		return;
    	}

    	CharSequence allText = historyBuffer_.getText();
    	int length = allText.length();
    	int lf_index = 0;
    	int start = 0;
    	if (length == 0) {
    		cursorPosition_ = 0;
    		start = 0;
    		lf_index = 0;
    	} else {
    		lf_index = 0;

    		for (int i=length-1; i>=0; i--) {
    			if (allText.charAt(i) == '\n') {
    				lf_index = i;
    				break;
    			}
    		}
    		if (lf_index == 0) {
    			if (allText.charAt(0) == '\n') {
    				lf_index = 1;
    				if (cursorPosition_ == 0) cursorPosition_ = 1;
    			} 
    		} else {
    			lf_index++;
    		}
			start = cursorPosition_ - lf_index;
    		
    	}
    	if (((UsbActivity) activity_).debug_) {
    		Log.i(TAG, "addToHistory1: length="+length+", cursorPosition="+cursorPosition_+", lf_index="+lf_index+", ALL_TEXT="+allText);
    	}
    	
    	CharSequence prefix = allText.subSequence(lf_index, length);
    	StringBuilder line = new StringBuilder(prefix);

    	if (((UsbActivity) activity_).debug_) {
    		Log.i(TAG, "addToHistory2: start="+start+", line="+line);
    	}
    	
    	start = Utils.processCRBytes(line, data, start);

    	if (lf_index < length) ((Editable) allText).delete(lf_index, length);
    	((Editable) allText).append(line);
    	cursorPosition_ = lf_index + start;
    	
    	if (((UsbActivity) activity_).debug_) {
    		Log.i(TAG, "addToHistory3: start="+start+", cursorPosition="+cursorPosition_+
    				", newLength="+historyBuffer_.getText().length()+", line="+line+", NEW_ALL_TEXT="+historyBuffer_.getText());
    	}

    	scrollDown1();
    }

	private void enableUiControls(boolean enable) {
		enable = true;
		if (commandText_ != null) commandText_.setEnabled(enable);
		if (clearButton_ != null) clearButton_.setEnabled(enable);
		if (sendButton_  != null) {
			sendButton_.setEnabled(enable);
			sendButton_.requestFocus();
		}
	}

	void scrollDown1() {
		if (scrollView_ == null) return;
		scrollView_.post(new Runnable() {

			@Override
			public void run() {
				scrollView_.fullScroll(ScrollView.FOCUS_DOWN);
		    	//scrollView_.scrollBy(0, 20);
			}
		});
	}
	
	void scrollDownWithDelay(int delay) {
		final int delayFinal = delay;
		Thread th = new Thread(new Runnable() {

			@Override
			public void run() {
				try {
					Thread.sleep(delayFinal);
				} catch (Exception e) { }
				scrollDown1();
			}
			
		});
		
		th.start();
	}
	
	void scrollDown2() {
		if (scrollView_ == null) return;
		scrollView_.post(new Runnable() {

			@Override
			public void run() {
				scrollView_.scrollTo(0, historyBuffer_.getHeight());
			}
		});
	}
	
	public void close() {
		// Do nothing here for now
	}
	
}