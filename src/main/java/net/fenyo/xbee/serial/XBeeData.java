package net.fenyo.xbee.serial;

import java.io.UnsupportedEncodingException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public abstract class XBeeData {
    protected final Log log = LogFactory.getLog(getClass());

    public abstract byte[] getRawContent() throws UnsupportedEncodingException;

    public long waitBeforeWrite() {
        return 0;
    }

    public long waitAfterWrite() {
        return 0;
    }
}
