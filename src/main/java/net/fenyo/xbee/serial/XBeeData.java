package net.fenyo.xbee.serial;

import java.io.*;
import org.apache.commons.logging.*;

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
