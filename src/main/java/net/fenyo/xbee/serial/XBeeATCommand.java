package net.fenyo.xbee.serial;

import java.io.*;

public class XBeeATCommand extends XBeeData {
    private final String command;
    private final String end_of_data;

    public XBeeATCommand(final String command, final String end_of_data) {
        super();
        this.command = command;
        this.end_of_data = end_of_data;
    }

    public long waitBeforeWrite() {
        if (command.equals("+++"))
            return 1200;
        else
            return 0;
    }

    public long waitAfterWrite() {
        if (command.equals("+++"))
            return 1200;
        else
            return 0;
    }

    public byte[] getRawContent() throws UnsupportedEncodingException {
        return command.getBytes("ISO8859-1");
    }

    public String getEndOfData() {
        return end_of_data;
    }
}
