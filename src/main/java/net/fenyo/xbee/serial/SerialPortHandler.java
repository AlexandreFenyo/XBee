package net.fenyo.xbee.serial;

import java.io.*;
import java.net.*;
import org.apache.commons.lang3.*;
import org.apache.commons.logging.*;
import org.springframework.beans.factory.*;
import org.springframework.stereotype.*;

// CT, GT et CC doivent avoir les valeurs par défaut

@Component("serialPortHandler")
public class SerialPortHandler implements InitializingBean, DisposableBean, Runnable {
    protected final Log log = LogFactory.getLog(getClass());

    private final Object connection_lock = new Object();
    private final Object at_mode_lock = new Object();
    private final Object IR_transaction_lock = new Object();

    private Thread thread = null;

    private String serialServerHostName;
    private int serialServerPortNumber;

    private SerialReader serial_reader = null;
    private SerialWriter serial_writer = null;

    private Socket socket = null;

    private int frame_id = 255; // synchronized by operation_lock

    public static String bytesArrayToString(final byte[] array) {
        if (array == null)
            return null;
        String retval = "";
        for (final byte b : array)
            retval += ((0xff & b) < 16 ? "0" : "") + Integer.toHexString(0xff & b);
        return retval;
    }

    // must be called within connection_lock synchronization
    private int getNewFrameId() {
        if (frame_id == 255)
            frame_id = 1;
        else
            frame_id++;
        serial_reader.removeOldFramesWithId(frame_id);
        return frame_id;
    }

    public String getSerialServerHostName() {
        return serialServerHostName;
    }

    public void setSerialServerHostName(String serialServerHostName) {
        this.serialServerHostName = serialServerHostName;
    }

    public int getSerialServerPortNumber() {
        return serialServerPortNumber;
    }

    public void setSerialServerPortNumber(int serialServerPortNumber) {
        this.serialServerPortNumber = serialServerPortNumber;
    }

    // threads: tomcat
    @Override
    public void destroy() throws Exception {
        synchronized (connection_lock) {
            // make the waiting read() exit, in the serial reader thread (the
            // thread.interrupt() call that occurs in serial_reader.close() is
            // not sufficient to make a read exit)
            socket.close();

            if (serial_reader != null) {
                serial_reader.close();
                serial_reader = null;
            }
            if (serial_writer != null) {
                serial_writer.close();
                serial_writer = null;
            }

            thread.interrupt();
            thread.join();
        }
    }

    // threads: SerialPortHandler
    // synchronized: connection_lock
    private void connectSerial() throws InterruptedException, IOException {
        log.debug("trying to connect to serial port");

        if (socket != null && !socket.isClosed())
            socket.close();
        if (serial_reader != null) {
            serial_reader.close();
            serial_reader = null;
        }
        if (serial_writer != null) {
            serial_writer.close();
            serial_writer = null;
        }

        try {
            socket = new Socket(serialServerHostName, serialServerPortNumber);
        } catch (final UnknownHostException ex) {
            log.error(ex);
            return;
        } catch (final IOException ex) {
            log.error(ex);
            return;
        }

        serial_reader = new SerialReader();
        serial_reader.start(socket);
        serial_writer = new SerialWriter();
        serial_writer.start(socket);
    }

    // threads: tomcat
    @Override
    public void afterPropertiesSet() throws Exception {
        thread = new Thread(this, "SerialPortHandler");
        thread.start();
    }

    // threads: tomcat
    public String checkAT() throws IOException, InterruptedException {
        log.warn("resetting XBee module");
        final String value = sendATCommand("ATFR\r", "\r", true);
        if (value == null)
            log.error("can not reset module");

        return sendATCommand("ATND\r", "\r\r\r");
    }

    // threads: tomcat
    public String checkAT2() throws IOException, InterruptedException {
        return bytesArrayToString(sendATCommandFrameSingleQuery("SH"));
    }

    // threads: tomcat
    public String checkAT3() throws IOException, InterruptedException {
        return "low power node: " + bytesArrayToString(sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409B7AA8, "SL"))
                + "\r" + "IR node: "
                + bytesArrayToString(sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409f5784, "SL")) + "\r"
                + "server node: " + bytesArrayToString(sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409F5655, "SL"))
                + "\r" + "volets node: "
                + bytesArrayToString(sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409A960C, "SL"));
    }

    // threads: tomcat
    public String checkAT4() throws IOException, InterruptedException {
        return "result: " + bytesArrayToString(sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409A960C, "D0")) + "\r";
    }

    // threads: tomcat
    public String checkAT5() throws IOException, InterruptedException {
        return "result: " + bytesArrayToString(
                sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409B7A9C, "D4" + new String(new byte[] { 0x04 })))
                + "\r";
    }

    // threads: tomcat
    public String checkAT6() throws IOException, InterruptedException {
        return "result: " + bytesArrayToString(
                sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409B7A9C, "D4" + new String(new byte[] { 0x05 })))
                + "\r";
    }

    // threads: tomcat
    public String checkAT7() throws IOException, InterruptedException {
        sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409B7A9C, "IS");

        return "ATD0: " + bytesArrayToString(sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409B7A9C, "D0")) + "\r"
                + "ATD1: " + bytesArrayToString(sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409B7A9C, "D1")) + "\r"
                + "ATD2: " + bytesArrayToString(sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409B7A9C, "D2")) + "\r"
                + "ATD3: " + bytesArrayToString(sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409B7A9C, "D3")) + "\r"
                + "ATD8: " + bytesArrayToString(sendRemoteATCommandFrameSingleQuery(0x13a200, 0x409B7A9C, "D8")) + "\r";
    }

    // threads: tomcat
    // synchronized: IR_transaction_lock
    private boolean waitForChar(final char c, final long timeout)
            throws UnsupportedEncodingException, InterruptedException {
        final long now = System.currentTimeMillis();
        while (now + timeout > System.currentTimeMillis()) {
            final long delay = now + timeout - System.currentTimeMillis();
            final XBeeFrame frame = serial_reader.getFrameWithAddress("0013a200409f5784", delay > 0 ? delay : 0);
            if (frame != null && frame.getReceivedData().indexOf(c) != -1)
                return true;
        }
        log.warn("timeout after " + timeout + "ms waiting for " + c);
        return false;
    }

    // threads: tomcat
    public boolean sendFile(final String filename, final int count) throws IOException, InterruptedException {
        synchronized (IR_transaction_lock) {
            boolean errsent = false;
            log.debug("sending file " + filename + " " + count + " times");

            log.debug("erasing memory");
            boolean done = false;
            for (int retries = 0; retries < 10; retries++) { // 10 retries
                if (retries > 0)
                    log.warn("retrying nb: " + retries);
                while (serial_reader.getFrameWithAddress("0013a200409f5784", 0) != null)
                    ; // flush previous chars from IR device
                errsent = sendDataFrame(0x13a200, 0x409f5784, "D");
                if ((done = waitForChar('>', 5000)) == true && errsent == true)
                    break;
                else
                    waitForChar('<', 5000);
            }
            if (!done) {
                log.error("cannot erase memory");
                return false;
            }

            log.debug("saving IR code");
            final BufferedReader br = new BufferedReader(
                    new InputStreamReader(new FileInputStream(new File(filename)), "ISO-8859-1"));
            try {
                String line;
                String msg = "";
                int cpt = 0;
                do {
                    log.debug("saving at pos " + cpt);
                    line = br.readLine();
                    if (line != null || msg.length() > 0) {
                        if (line != null) {
                            msg += "S";
                            msg += (char) (0xff & cpt);
                            msg += (char) (0xff & (new Integer(line).intValue() >> 8));
                            msg += (char) (0xff & new Integer(line).intValue());
                        }

                        if (line == null
                                || msg.length() >= /* 4 pour envoyer 1 à 1 */ 80) {
                            done = false;
                            for (int retries = 0; retries < 10; retries++) { // 10
                                                                             // retries
                                if (retries > 0)
                                    log.warn("retrying nb: " + retries);
                                while (serial_reader.getFrameWithAddress("0013a200409f5784", 0) != null)
                                    ; // flush previous chars from IR device
                                errsent = sendDataFrame(0x13a200, 0x409f5784, msg);
                                if ((done = waitForChar('>', 5000)) == true && errsent == true)
                                    break;
                                else
                                    waitForChar('<', 5000);
                            }
                            if (!done) {
                                log.error("cannot save IR code");
                                return false;
                            }
                            msg = "";
                        }

                    }

                    cpt++;
                } while (line != null);

            } finally {
                br.close();
            }

            for (int nsend = 0; nsend < count; nsend++) {
                if (nsend != 0) {
                    log.debug("clearing pulse counter");
                    done = false;
                    for (int retries = 0; retries < 10; retries++) { // 10
                                                                     // retries
                        if (retries > 0)
                            log.warn("retrying nb: " + retries);
                        while (serial_reader.getFrameWithAddress("0013a200409f5784", 0) != null)
                            ; // flush previous chars from IR device
                        errsent = sendDataFrame(0x13a200, 0x409f5784, "P");
                        if ((done = waitForChar('>', 5000)) == true && errsent == true)
                            break;
                        else
                            waitForChar('<', 5000);
                    }
                    if (!done) {
                        log.error("cannot clear pulse counter");
                        return false;
                    }
                    // wait between two IR command sent
                    Thread.sleep(100);
                }

                log.debug("sending IR command");
                done = false;
                for (int retries = 0; retries < 10; retries++) { // 10 retries
                    if (retries > 0)
                        log.warn("retrying nb: " + retries);
                    while (serial_reader.getFrameWithAddress("0013a200409f5784", 0) != null)
                        ; // flush previous chars from IR device
                    errsent = sendDataFrame(0x13a200, 0x409f5784, "I");
                    if ((done = waitForChar('>', 5000)) == true && errsent == true)
                        break;
                    else
                        waitForChar('<', 5000);
                }
                if (!done) {
                    log.error("cannot send IR command");
                    return false;
                }
            }

            return true;
        }
    }

    // threads: tomcat
    // synchronized: IR_transaction_lock si destinataire est le module IR
    public boolean sendDataFrame(final long address_high, final long address_low, final String msg)
            throws IOException, InterruptedException {
        if (msg.getBytes("ISO8859-1").length == 0) {
            log.error("invalid data length");
            return false;
        }

        final int frame_id;
        final XBeeFrame frame;
        synchronized (connection_lock) {
            synchronized (at_mode_lock) {
                if (serial_reader == null || serial_writer == null || serial_reader.getError()
                        || serial_writer.getError()) {
                    log.warn("not connected");
                    return false;
                }

                frame_id = getNewFrameId();
                final byte[] data = new byte[8
                        /* 64 bit dest addr */ + 1 /* transmit options */ + msg.getBytes("ISO8859-1").length];
                data[0] = (byte) ((address_high >> 24) & 0xff);
                data[1] = (byte) ((address_high >> 16) & 0xff);
                data[2] = (byte) ((address_high >> 8) & 0xff);
                data[3] = (byte) (address_high & 0xff);
                data[4] = (byte) ((address_low >> 24) & 0xff);
                data[5] = (byte) ((address_low >> 16) & 0xff);
                data[6] = (byte) ((address_low >> 8) & 0xff);
                data[7] = (byte) (address_low & 0xff);
                data[8] = 0x00;
                for (int i = 0; i < msg.getBytes("ISO8859-1").length; i++)
                    data[9 + i] = msg.getBytes("ISO8859-1")[i];
                frame = new XBeeFrame(0x00, frame_id, data);
                serial_writer.sendFrame(frame);
            }
        }

        // must NOT synchronize connection_lock or at_mode_lock (to let other
        // commands being sent while waiting for a reply to this one)
        // thus, serial_reader can be null => an exception can be thrown here
        // (and must be catch at a higher level)
        final XBeeFrame retframe = serial_reader.getFrameWithId(frame_id, 5000);
        if (retframe != null) {
            final byte[] content = retframe.getContent();
            if (content.length != 1) {
                log.error("invalid frame");
                return false;
            }
            switch (0xff & content[0]) {
            case 0:
                return true;

            case 1:
                log.warn("transmitted data: no ACK received");
                return false;

            case 2:
                log.warn("transmitted data: CCA failure");
                return false;

            case 3:
                log.warn("transmitted data: Purged");
                return false;

            default:
                log.warn("transmitted data: invalid status code");
                return false;
            }
        } else
            return false;
    }

    // threads: tomcat
    public byte[] sendATCommandFrameSingleQuery(final String command) throws IOException, InterruptedException {
        if (command.getBytes("ISO8859-1").length < 2) {
            log.error("invalid command length");
            return null;
        }

        final int frame_id;
        final XBeeFrame frame;
        synchronized (connection_lock) {
            synchronized (at_mode_lock) {
                if (serial_reader == null || serial_writer == null || serial_reader.getError()
                        || serial_writer.getError()) {
                    log.warn("not connected");
                    return null;
                }

                frame_id = getNewFrameId();
                frame = new XBeeFrame(0x08, frame_id, command.getBytes("ISO8859-1"));
                serial_writer.sendFrame(frame);
            }
        }

        // must NOT synchronize connection_lock or at_mode_lock (to let other
        // commands being sent while waiting for a reply to this one)
        // thus, serial_reader can be null => an exception can be thrown here
        // (and must be catch at a higher level)
        final XBeeFrame retframe = serial_reader.getFrameWithId(frame_id, 5000);
        if (retframe != null) {
            final byte[] content = retframe.getContent();
            if (content.length < 3 || content[0] != command.getBytes("ISO8859-1")[0]
                    || content[1] != command.getBytes("ISO8859-1")[1]) {
                log.error("invalid frame");
                return null;
            }
            switch (0xff & content[2]) {
            case 0:
                return ArrayUtils.subarray(content, 3, content.length);

            case 1:
                log.warn("AT command response frame: ERROR");
                return null;

            case 2:
                log.warn("AT command response frame: INVALID COMMAND");
                return null;

            case 3:
                log.warn("AT command response frame: INVALID PARAMETER");
                return null;

            default:
                log.warn("AT command response frame: invalid status code");
                return null;
            }
        } else
            return null;
    }

    // threads: tomcat
    public byte[] sendRemoteATCommandFrameSingleQuery(final long address_high, final long address_low,
            final String command) throws IOException, InterruptedException {
        return sendRemoteATCommandFrameSingleQueryAck(address_high, address_low, command, true);
    }

    // threads: tomcat
    public byte[] sendRemoteATCommandFrameSingleQueryAck(final long address_high, final long address_low,
            final String command, final boolean ack) throws IOException, InterruptedException {
        if (command.getBytes("ISO8859-1").length < 2) {
            log.error("invalid command length");
            return null;
        }

        final int frame_id;
        final XBeeFrame frame;
        synchronized (connection_lock) {
            synchronized (at_mode_lock) {
                if (serial_reader == null || serial_writer == null || serial_reader.getError()
                        || serial_writer.getError()) {
                    log.warn("not connected");
                    return null;
                }

                frame_id = ack ? getNewFrameId() : 0;
                final byte[] command_data = new byte[8
                        /* 64 bit dest addr */ + 2 /*
                                                    * 16 bit dest addr == 0xfffe
                                                    */ + 1 /* command options */ + command.getBytes("ISO8859-1").length];
                command_data[0] = (byte) ((address_high >> 24) & 0xff);
                command_data[1] = (byte) ((address_high >> 16) & 0xff);
                command_data[2] = (byte) ((address_high >> 8) & 0xff);
                command_data[3] = (byte) (address_high & 0xff);
                command_data[4] = (byte) ((address_low >> 24) & 0xff);
                command_data[5] = (byte) ((address_low >> 16) & 0xff);
                command_data[6] = (byte) ((address_low >> 8) & 0xff);
                command_data[7] = (byte) (address_low & 0xff);
                command_data[8] = (byte) 0xff; // 32 bit address only
                command_data[9] = (byte) 0xfe;
                command_data[10] = 0x02; // apply changes on remote immediately
                for (int i = 0; i < command.getBytes("ISO8859-1").length; i++)
                    command_data[11 + i] = command.getBytes("ISO8859-1")[i];
                frame = new XBeeFrame(0x17, frame_id, command_data);
                serial_writer.sendFrame(frame);
            }
        }

        if (ack) {
            // must NOT synchronize connection_lock or at_mode_lock (to let
            // other commands being sent while waiting for a reply to this one)
            // thus, serial_reader can be null => an exception can be thrown
            // here (and must be catch at a higher level)
            // en fait, au lieu de 15000, faudrait mettre 25000 car le timeout
            // est égale à 2,5 fois SP (à vérifier), quoi que c'est aussi 3
            // essais séparés de 200 ms => bi
            final XBeeFrame retframe = serial_reader.getFrameWithId(frame_id, 15000); // 15000
                                                                                      // since
                                                                                      // SP
                                                                                      // is
                                                                                      // set
                                                                                      // to
                                                                                      // 10000
                                                                                      // ms
            if (retframe != null) {
                final byte[] content = retframe.getContent();
                if (content.length < 8
                        /* 64 bit address */ + 2 /* 16 bit address */ + 3
                        || content[8 + 2] != command.getBytes("ISO8859-1")[0]
                        || content[8 + 2 + 1] != command.getBytes("ISO8859-1")[1]) {
                    log.error("invalid frame");
                    return null;
                }
                switch (0xff & content[8 + 2 + 2]) {
                case 0:
                    return ArrayUtils.subarray(content, 8 + 2 + 3, content.length);

                case 1:
                    log.warn("AT remote command response frame: ERROR");
                    return null;

                case 2:
                    log.warn("AT remote command response frame: INVALID COMMAND");
                    return null;

                case 3:
                    log.warn("AT remote command response frame: INVALID PARAMETER");
                    return null;

                case 4:
                    log.warn("AT remote command response frame: NO RESPONSE");
                    return null;

                default:
                    log.warn("AT remote command response frame: invalid status code");
                    return null;
                }
            } else {
                log.warn("AT remote command : timeout waiting for response frame");
                return null;
            }
        } else
            return null;
    }

    // threads: tomcat
    public String sendATCommand(final String command) throws IOException, InterruptedException {
        return sendATCommand(command, "\r");
    }

    // threads: tomcat
    public String sendATCommand(final String command, final String end_of_data)
            throws IOException, InterruptedException {
        return sendATCommand(command, end_of_data, false);
    }

    // threads: tomcat, SerialPoirtHandler
    // Synchronized: connection_lock ou rien selon l'appel
    public String sendATCommand(final String command, final String end_of_data, final boolean ignore_atcn)
            throws IOException, InterruptedException {
        synchronized (connection_lock) {
            synchronized (at_mode_lock) {
                if (serial_reader == null || serial_writer == null || serial_reader.getError()
                        || serial_writer.getError()) {
                    log.warn("not connected");
                    return null;
                }

                // flush unread data
                final String prev = serial_reader.getData();
                if (prev.length() > 0)
                    log.warn("data flushed: '" + prev + "'");

                // il semble que s'il y a des envois en cours en mode cyclique
                // qui attendent un timeout car le module destinataire n'est pas
                // dispo, on n'arrivera pas à faire fonctionner la phase
                // initiale, avec +++ et les commandes AT
                // dans ce cas débrancher et rebrancher le module de son support
                // USB
                XBeeATCommand xbee_at_command = new XBeeATCommand("+++", "OK\r");

                // waiting 1.5 times default GUARD TIME - il faut que la valeur
                // de GT préconfigurée sur le module soit celle par défaut (1
                // seconde)
                Thread.sleep(1500);

                serial_writer.sendATCommand(xbee_at_command);
                log.debug("waiting for response after +++");
                if (serial_reader.waitForData(xbee_at_command.getEndOfData(), 6000) == null) {
                    log.error("timeout waiting for OK after +++");

                    // log.error("sending ATCN to get out of AT mode");
                    // xbee_at_command = new XBeeATCommand("ATCN\r", "OK\r");
                    // serial_writer.sendATCommand(xbee_at_command);
                    // final String retval_atcn;
                    // if ((retval_atcn =
                    // serial_reader.waitForData(xbee_at_command.getEndOfData(),
                    // 6000)) == null || !retval_atcn.isEmpty())
                    // log.error("timeout waiting for OK after ATCN");

                    return null;
                }
                log.debug("after 'waiting for response after +++'");

                xbee_at_command = new XBeeATCommand(command, end_of_data);
                serial_writer.sendATCommand(xbee_at_command);
                final String retval;
                if ((retval = serial_reader.waitForData(xbee_at_command.getEndOfData(), 6000)) == null) {
                    log.error("timeout waiting for AT command result");
                    return null;
                } else {
                    if (!ignore_atcn) {
                        xbee_at_command = new XBeeATCommand("ATCN\r", "OK\r");
                        serial_writer.sendATCommand(xbee_at_command);
                        final String retval_atcn;
                        if ((retval_atcn = serial_reader.waitForData(xbee_at_command.getEndOfData(), 6000)) == null
                                || !retval_atcn.isEmpty())
                            log.error("timeout waiting for OK after ATCN");
                    }
                    ;

                    return retval;
                }
            }
        }
    }

    // threads: SerialPortHandler
    @Override
    public void run() {
        try {
            boolean should_reconnect = false;
            while (true) {
                try {
                    synchronized (connection_lock) {
                        if (should_reconnect || serial_reader == null || serial_writer == null
                                || serial_reader.getError() || serial_writer.getError()) {
                            log.debug("avant connectSerial()");
                            connectSerial();
                            log.debug("après connectSerial()");
                            should_reconnect = false;
                            String value = sendATCommand("ATAP\r");
                            if (value == null || !value.equals(/* "1" */ "2")) {
                                log.warn("invalid API mode: " + value);
                                log.warn("trying to enable API mode w/escaped control characters");
                                value = sendATCommand("ATAP2\r");
                                if (value == null || !value.equals("OK")) {
                                    log.warn("cannot set AP mode 2");
                                    should_reconnect = true;
                                } else {
                                    log.warn("trying to apply changes");
                                    final String value1 = sendATCommand("ATWR\r");
                                    final String value2 = sendATCommand("ATAC\r");
                                    if (value1 == null || value2 == null || !value1.equals("OK")
                                            || !value2.equals("OK")) {
                                        log.warn("cannot apply changes");
                                        should_reconnect = true;
                                    } else {
                                        log.warn("success: AP mode 2 is set");
                                        log.warn("resetting XBee module");
                                        value = sendATCommand("ATFR\r", "\r", true);
                                        // wait for the system to reset (prevent
                                        // sending bytes before reset is done)
                                        Thread.sleep(1000);
                                        if (!value.equals("OK")) {
                                            log.warn("cannot reset XBee module");
                                            should_reconnect = true;
                                        }
                                    }
                                }
                            }
                            if (!should_reconnect) {
                                log.info("API mode is correct");

                                byte[] bytes = sendATCommandFrameSingleQuery("ID");
                                if (bytes == null || !bytesArrayToString(bytes).equals("afae")) {
                                    if (bytes == null)
                                        log.warn("bad pan id null (should be afae)");
                                    else
                                        log.warn("bad pan id " + bytesArrayToString(bytes) + "(should be afae)");

                                    log.info("trying to set pan id to afafh");
                                    bytes = sendATCommandFrameSingleQuery(
                                            "ID" + new String(new byte[] { (byte) 0xaf, (byte) 0xae }));
                                    if (bytes == null || bytes.length > 0) {
                                        log.warn("error");
                                        should_reconnect = true;
                                        continue;
                                    }
                                    log.debug("pan id set to AFAEh");
                                }

                                bytes = sendATCommandFrameSingleQuery("CE");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                switch (bytes[0]) {
                                case 0x00:
                                    log.info("this module is an end device");
                                    break;

                                case 0x01:
                                    log.info("this module is a coordinator");
                                    break;

                                default:
                                    log.warn("error: this module is neither an end device nor a coordinator");
                                    break;
                                }

                                bytes = sendATCommandFrameSingleQuery("SH");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                final String module_sh = bytesArrayToString(bytes);
                                bytes = sendATCommandFrameSingleQuery("SL");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                final String module_sl = bytesArrayToString(bytes);
                                log.info("module serial number: " + module_sh + module_sl);

                                bytes = sendATCommandFrameSingleQuery("NI");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                log.info("node id: " + new String(bytes, "ISO8859-1"));

                                bytes = sendATCommandFrameSingleQuery("DH");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                final String module_dh = bytesArrayToString(bytes);
                                bytes = sendATCommandFrameSingleQuery("DL");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                final String module_dl = bytesArrayToString(bytes);
                                log.info("module destination address: " + module_dh + module_dl);

                                bytes = sendATCommandFrameSingleQuery("MY");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                log.info("MY address: " + bytesArrayToString(bytes));
                                if (!bytesArrayToString(bytes).equals("fffe")) {
                                    log.warn("bad MY address (should be fffe)");
                                    log.warn("trying to set MY address to fffe");
                                    bytes = sendATCommandFrameSingleQuery(
                                            "MY" + new String(new byte[] { (byte) 0xff, (byte) 0xfe }));
                                    if (bytes == null || bytes.length > 0) {
                                        log.warn("error");
                                        should_reconnect = true;
                                        continue;
                                    }
                                    log.warn("MY address set to fffe");
                                }

                                bytes = sendATCommandFrameSingleQuery("MM");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                log.info("mac mode: " + bytesArrayToString(bytes));
                                if (!bytesArrayToString(bytes).equals("00")) {
                                    log.warn("bad mac mode (should be digi mode)");
                                    log.warn("trying to set mac mode to digi");
                                    bytes = sendATCommandFrameSingleQuery("MM" + new String(new byte[] { 0x00 }));
                                    if (bytes == null || bytes.length > 0) {
                                        log.warn("error");
                                        should_reconnect = true;
                                        continue;
                                    }
                                    log.warn("mac mode set to digi");
                                }

                                bytes = sendATCommandFrameSingleQuery("RN");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                log.info("back-off exponent: " + bytesArrayToString(bytes));
                                if (!bytesArrayToString(bytes).equals("00")) {
                                    log.warn("bad back-off exponent");
                                    log.warn("trying to set back-off exponent to 0");
                                    log.warn("trying to set mac mode to digi");
                                    bytes = sendATCommandFrameSingleQuery("RN" + new String(new byte[] { 0x00 }));
                                    if (bytes == null || bytes.length > 0) {
                                        log.warn("error");
                                        should_reconnect = true;
                                        continue;
                                    }
                                    log.warn("back-off exponent set to 0");
                                }

                                bytes = sendATCommandFrameSingleQuery("RR");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                log.info("retries (in addition to the standard 3 ones): " + bytesArrayToString(bytes));
                                if (!bytesArrayToString(bytes).equals("00"))
                                    log.error("bad number of retries (should be 0 in addition to the standard 3 ones)");

                                bytes = sendATCommandFrameSingleQuery("NT");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                log.info("node discovery time: " + (0xff & bytes[0]) * 100 + " ms");
                                if ((0xff & bytes[0]) * 100 != 5000) {
                                    log.warn("trying to set node discovery time to 5 secs");
                                    bytes = sendATCommandFrameSingleQuery("NT" + new String(new byte[] { 0x32 }));
                                    if (bytes == null || bytes.length > 0) {
                                        log.warn("error");
                                        should_reconnect = true;
                                        continue;
                                    }
                                    log.warn("node discovery time set to 5 secs");
                                }

                                bytes = sendATCommandFrameSingleQuery("NO");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                if (!bytesArrayToString(bytes).equals("01")) {
                                    log.warn("node discovery option: no self response");
                                    log.warn("node discovery option: trying to activate self-response");
                                    bytes = sendATCommandFrameSingleQuery("NO" + new String(new byte[] { 0x01 }));
                                    if (bytes == null || bytes.length > 0) {
                                        log.warn("error");
                                        should_reconnect = true;
                                        continue;
                                    }
                                    log.warn("node discovery option: self-response activated");
                                } else
                                    log.info("node discovery option: self response");

                                bytes = sendATCommandFrameSingleQuery("SC");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                if (bytesArrayToString(bytes).equals("1ffe"))
                                    log.info("every XBee-PRO channels available");
                                else
                                    log.warn("some XBee-PRO channels are excluded: " + bytesArrayToString(bytes));

                                bytes = sendATCommandFrameSingleQuery("SD");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                log.info("scan duration exponent: 4 (default value)");
                                if (!bytesArrayToString(bytes).equals("04"))
                                    log.warn("warning: scan duration exponent is not set to the default value");

                                // hand set SP, ST and DP (set manual to true to
                                // activate this block)
                                boolean manual = true;
                                if (manual) {
                                    log.debug("SP=" + bytesArrayToString(sendATCommandFrameSingleQuery("SP")) + "h");
                                    bytes = sendATCommandFrameSingleQuery(
                                            "SP" + new String(new byte[] { 3, (byte) 0xe8 }, "ISO8859-1"));
                                    log.debug("SP=" + bytesArrayToString(sendATCommandFrameSingleQuery("SP")) + "h");

                                    log.debug("ST=" + bytesArrayToString(sendATCommandFrameSingleQuery("ST")) + "h");
                                    bytes = sendATCommandFrameSingleQuery(
                                            "ST" + new String(new byte[] { (byte) 0xea, (byte) 0x60 }, "ISO8859-1"));
                                    log.debug("ST=" + bytesArrayToString(sendATCommandFrameSingleQuery("ST")) + "h");

                                    log.debug("DP=" + bytesArrayToString(sendATCommandFrameSingleQuery("DP")) + "h");
                                    bytes = sendATCommandFrameSingleQuery(
                                            "DP" + new String(new byte[] { 3, (byte) 0xe8 }, "ISO8859-1"));
                                    log.debug("DP=" + bytesArrayToString(sendATCommandFrameSingleQuery("DP")) + "h");

                                }

                                log.info("trying to save configuration");
                                bytes = sendATCommandFrameSingleQuery("WR");
                                if (bytes == null) {
                                    log.warn("error");
                                    should_reconnect = true;
                                    continue;
                                }
                                log.info("configuration saved");
                            }
                        }
                    }
                } catch (final IOException ex) {
                    log.warn(ex);
                }

                // avoid overloading CPU and network
                Thread.sleep(1000);

                if (should_reconnect == false) {

                    // send beacon to let IR module awake
                    try {
                        synchronized (IR_transaction_lock) {
                            // flush previous chars from IR and other devices to
                            // avoid useless frames stored in
                            // SerialReader.frames
                            synchronized (connection_lock) {
                                while (serial_reader != null
                                        && serial_reader.getFrameWithAddress("0013a200409f5784", 0) != null)
                                    ;
                                if (serial_reader != null) {
                                    XBeeFrame frame;
                                    do {
                                        frame = serial_reader.getFrameWithAddress("0013a200409b7a9c", 0);
                                        if (frame != null) {
                                            log.debug("FRAME recue du nouveau module");
                                        }
                                    } while (frame != null);
                                }
                            }
                            // send useless char to module to let it being
                            // wake-up
                            log.debug("avant send X frame");
                            // en mode cyclique (i.e. le mode dans lequel on
                            // est) : les envois vers un module éteint attendent
                            // 25 secondes (2.5 fois 10 secondes) avant de
                            // partir en timeout par le module, qui l'annonce
                            // avec une frame 98 contenant la valeur 3
                            // dans le cas d'un module éteint, les autres envois
                            // vers d'autres modules ne semblent pas partir du
                            // module coordinateur
                            sendDataFrame(0x13a200, 0x409f5784, "X");
                            log.debug("après send X frame");

                        }
                        log.debug("avant send Y frame");
                        // sendDataFrame(0x13a200, 0x409b7a9c, "Y");
                        // module volets AF
                        sendDataFrame(0x13a200, 0x409b7abb, "Z");
                    } catch (final IOException ex) {
                        log.warn(ex);
                    }

                }

            }
        } catch (final InterruptedException ex) {
            log.debug(ex);
        }
    }
}
