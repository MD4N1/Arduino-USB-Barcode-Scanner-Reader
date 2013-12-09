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
import java.util.Arrays;

/**
 * Utility class that mainly contains static methods to deal with
 * "CR" and "LF" characters; also to create string presentation of
 * an Exception.
 */
public class Utils {

	public static final String CR_STRING = "\r";
	public static final String LF_STRING = "\n";
    public static final byte CR = 0x0D;
    public static final byte LF = 0x0A;
	
	public static boolean containsCR(CharSequence line) {
		if (line == null) return false;
		String str = line.toString();
		int cr_index = str.indexOf(CR_STRING);
		if (cr_index < 0) return false;
		return true;
	}
	
	public static boolean canProcessCR(CharSequence line) {
		if (line == null) return false;
		String str = line.toString();
		int cr_index = str.indexOf(CR_STRING);
		if (cr_index < 0) return false;
		int lf_index = str.indexOf(LF_STRING);

		if (lf_index < 0) return false;

		if (lf_index < cr_index) return true;
		else return false;
	}

	public static CharSequence processCR(CharSequence line) {
		if (!canProcessCR(line)) return line;
		
		StringBuilder buffer = new StringBuilder(line);
		int length = buffer.length();
		int cr_index = buffer.indexOf(CR_STRING);
		int lf_index0 = buffer.lastIndexOf(LF_STRING, cr_index);
		int lf_index1 = buffer.indexOf(LF_STRING, cr_index+1);
		if (lf_index1 < 0) lf_index1 = length;
		
		// completely overwrite all text between LF and CR
		if ( (lf_index1 - cr_index - 1) > (cr_index - lf_index0 - 1) ) {
			buffer.delete(lf_index0+1, cr_index+1);
		}
		// overwrite only part of text between LF and CR
		else {
			String replaceString = buffer.substring(cr_index+1, lf_index1);
			buffer.delete(cr_index, lf_index1);
			buffer.replace(lf_index0+1, lf_index0+1+replaceString.length(), replaceString);
		}
		
		return processCR(buffer.toString());
		//return buffer.toString();
	}
	
	public static int processCRBytes(StringBuilder line, byte[] data, int start) {
		if (data == null || data.length == 0) return start;
		
		if (line == null) {
			line = new StringBuilder();
			start = 0;
		}
		
		int cr_index0  = -1;
		int cr_index1  = -1;
		int lf_index0 = -1;
		int lf_index_line = -1;
		int lf_index1 = -1;
		byte[] tmpData = null;
		for (int i=0; i<data.length; i++) {
			if (data[i] == CR && cr_index0 >= 0) {
				cr_index1 = i;
			}
			if (data[i] == CR && cr_index0 < 0) {
				cr_index0 = i;
			}
			if (data[i] == LF && cr_index0 < 0) {
				lf_index0 = i;
			}
			if (data[i] == LF && cr_index0 > 0) {
				lf_index1 = i;
				break;
			}			
		}
		
		if (cr_index0 < 0) {
			// If no CR found, just insert the data
			line.replace(start, start + data.length, new String(data));
			start += data.length;
		} else {
			// Here process the first CR
			if (lf_index0 > 0) {
				tmpData = Arrays.copyOfRange(data, 0, lf_index0+1);
				line.replace(start, start + tmpData.length, new String(tmpData));
				start += tmpData.length;
				lf_index_line = start;
			} else {
				lf_index_line = 0;
			}

			// Process text between lf_index and CR
			tmpData = Arrays.copyOfRange(data, lf_index0+1, cr_index0);
			line.replace(start, start + tmpData.length, new String(tmpData));
			start += tmpData.length;
			
			// Process CR from the beginning of the line
			if (lf_index1 > 0) {
				tmpData = Arrays.copyOfRange(data, cr_index0+1, lf_index1);
				line.replace(lf_index_line, lf_index_line + tmpData.length, new String(tmpData));
				line.append((char) data[lf_index1]);
				//start = lf_index_line + tmpData.length;
				start = line.length();
				
				tmpData = Arrays.copyOfRange(data, lf_index1+1, data.length);
				start = processCRBytes(line, tmpData, start);
			} else {
				tmpData = Arrays.copyOfRange(data, cr_index0+1, data.length);
				line.replace(lf_index_line, lf_index_line + tmpData.length, new String(tmpData));
				start = lf_index_line + tmpData.length;
			}


		}
		return start;
	}
	

	public static String getExceptionStack(Exception e, boolean getMessage) {
		if (e == null) return "";
		
		StringWriter w = new StringWriter();
		e.printStackTrace(new PrintWriter(w));
		if (!getMessage) {
			return w.toString();
		} else {
			return e.getMessage() + "\n" + w.toString();
		}
	}


    public static void main(String[] args) {
    	String line = "First Line\nSecond Line\rPLUS_SECOND_LINE\nThird Line";
    	
    	CharSequence line2 = processCR(line);
    	
    	System.out.println(line);
    	System.out.println(line2);
    }
    
}
