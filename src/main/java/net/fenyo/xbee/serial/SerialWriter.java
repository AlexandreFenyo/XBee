package net.fenyo.xbee.serial;

import java.io.*;

public class SerialWriter extends ThreadHandler {
    private OutputStream stream;

    public void sendATCommand(final XBeeATCommand command) throws IOException, InterruptedException {
        synchronized (data) {
            data.add(command);
            data.notify();
        }
    }

    public void sendFrame(final XBeeFrame frame) throws IOException, InterruptedException {
        synchronized (data) {
            data.add(frame);
            data.notify();
        }
    }

    @Override
    public void run() {
        try {
            stream = getSocket().getOutputStream();

            while (true) {
                if (getError())
                    return;
                XBeeData xbee_data = null;
                synchronized (data) {
                    if (data.isEmpty())
                        data.wait();
                    if (!data.isEmpty()) {
                        xbee_data = data.get(0);
                        data.remove(0);
                    }
                }

                if (xbee_data != null) {
                    if (xbee_data.waitBeforeWrite() != 0)
                        Thread.sleep(xbee_data.waitBeforeWrite());

                    log.debug("write " + xbee_data.getRawContent().length + " bytes");
                    for (int i = 0; i < xbee_data.getRawContent().length; i++)
                        log.debug("write byte: " + Integer.toHexString(0xff & xbee_data.getRawContent()[i])
                                + " - char='" + new String(new byte[] { xbee_data.getRawContent()[i] }, "ASCII") + "'");

                    stream.write(xbee_data.getRawContent());
                    if (xbee_data.waitAfterWrite() != 0)
                        Thread.sleep(xbee_data.waitAfterWrite());

                } else
                    log.warn("wake-up for data but data is null");
            }
        } catch (final IOException | InterruptedException e) {
            log.warn(e);
            errorOccured();
            return;
        }
    }
}
