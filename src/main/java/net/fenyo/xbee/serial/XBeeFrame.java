package net.fenyo.xbee.serial;

import java.io.*;
import java.util.*;
import org.apache.commons.lang3.*;

public class XBeeFrame extends XBeeData {
    final int api_id;
    final int frame_id;
    final byte[] content;

    public byte[] getContent() {
        return content;
    }

    public String getAddress() throws UnsupportedEncodingException {
        if (api_id != 0x80)
            return null;
        String address = "";
        for (final byte b : ArrayUtils.subarray(content, 0, 8))
            address += ((b & 0xff) < 16 ? "0" : "") + Integer.toHexString(b & 0xff);
        return address;
    }

    public String getReceivedData() throws UnsupportedEncodingException {
        if (api_id != 0x80)
            return null;
        String msg = "";
        for (final byte b : ArrayUtils.subarray(content, 10, content.length))
            msg += new String(new byte[] { b }, "ISO8859-1");
        return msg;
    }

    public byte[] getRawContent() throws UnsupportedEncodingException {
        final List<Byte> raw_content = new ArrayList<Byte>();

        final int len = 1
                /* api id */ + ((frame_id == -1) ? 0 : 1) /* frame id */ + content.length;
        raw_content.add((byte) (len >> 8)); // msb
        raw_content.add((byte) (len & 0xff)); // lsb
        raw_content.add((byte) api_id);
        if (frame_id != -1)
            raw_content.add((byte) frame_id);
        raw_content.addAll(Arrays.asList(ArrayUtils.toObject(content)));

        // compute checksum
        int cksum = api_id + frame_id;
        for (final byte b : content)
            cksum += b;
        cksum = 0xff - (cksum & 0xff);
        raw_content.add((byte) cksum);

        final List<Byte> escaped_raw_content = new ArrayList<Byte>();
        for (final byte b : raw_content)
            // initialized array must be sorted to make a binarySearch
            if (Arrays.binarySearch(new byte[] { 0x11, 0x13, 0x7d, 0x7e }, b) < 0)
                escaped_raw_content.add(b);
            else {
                escaped_raw_content.add((byte) 0x7d);
                escaped_raw_content.add((byte) (b ^ 0x20));
            }
        escaped_raw_content.add(0, (byte) 0x7e); // add start delimiter

        return ArrayUtils.toPrimitive(escaped_raw_content.toArray(new Byte[] {}));
    }

    public int getId() {
        return frame_id;
    }

    public String toString() {
        String str = "FRAME: api_id=" + api_id + ((frame_id >= 0) ? (" frame_id=" + frame_id) : "") + "\n";
        for (final byte b : content)
            str += "       byte: " + Integer.toHexString(b & 0xff) + "h" + " " + (b & 0xff) + "d ('" + (char) b
                    + "')\n";
        return str;
    }

    public XBeeFrame(final int api_id, final byte[] content) {
        this.api_id = api_id;
        this.frame_id = -1;
        this.content = content;
    }

    public XBeeFrame(final int api_id, final int frame_id, final byte[] content) {
        this.api_id = api_id;
        this.frame_id = frame_id;
        this.content = content;
    }
}
