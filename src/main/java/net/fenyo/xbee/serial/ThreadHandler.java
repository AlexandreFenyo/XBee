package net.fenyo.xbee.serial;

import java.net.*;
import java.util.*;
import org.apache.commons.logging.*;

public abstract class ThreadHandler implements Runnable {
    protected final Log log = LogFactory.getLog(getClass());

    protected final List<XBeeData> data = new ArrayList<XBeeData>();

    private Socket socket = null;
    private Thread thread = null;
    private boolean error = false;

    public boolean getError() {
        return error;
    }

    protected void errorOccured() {
        error = true;
    }

    protected Socket getSocket() {
        return socket;
    }

    public void start(final Socket socket) {
        this.socket = socket;
        thread = new Thread(this, getClass().toString());
        thread.start();
    }

    protected boolean isInterrupted() {
        return thread.isInterrupted();
    }

    public void close() throws InterruptedException {
        errorOccured();
        thread.interrupt();
        thread.join();
    }
}
