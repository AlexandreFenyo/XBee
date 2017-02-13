package net.fenyo.xbee.serial;

public class Tools {
	public static String byteToBinaryString(final byte i) {
		final String s = Integer.toBinaryString(i);
		final String retval;
		if (s.length() < 8) retval = "00000000".substring(s.length()) + s;
		else retval = s.substring(s.length() - 8);
		return retval;
	}

}