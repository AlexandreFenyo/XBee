package net.fenyo.xbee.serial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang3.ArrayUtils;

public class SerialReader extends ThreadHandler {
	private InputStream stream;
	
	private List<Byte> input_buffer = new ArrayList<Byte>();
	private final StringBuffer outside_frame = new StringBuffer();
	private final List<XBeeFrame> frames = new ArrayList<XBeeFrame>();
	private long frame_start_time = 0;

	public String getData() {
		synchronized (outside_frame) {
			final String retval = outside_frame.toString();
			outside_frame.setLength(0);
			return retval;
		}
	}

	public XBeeFrame getFrameWithId(final int id) throws InterruptedException {
		return getFrameWithId(id, 10000);
	}
	
	public XBeeFrame getFrameWithId(final int id, final long timeout) throws InterruptedException {
		final long now = System.currentTimeMillis();
		synchronized (frames) {
			do {
				for (final XBeeFrame frame : frames) if (frame.getId() == id) {
					frames.remove(frame);
					return frame;
				}
				final long delay = now + timeout - System.currentTimeMillis();
				if (delay > 0) frames.wait(delay);
			} while (now + timeout > System.currentTimeMillis());
		}
		return null;
	}

	public XBeeFrame getFrameWithAddress(final String address, final long timeout) throws InterruptedException, UnsupportedEncodingException {
		final long now = System.currentTimeMillis();
		synchronized (frames) {
			do {
				for (final XBeeFrame frame : frames) if (frame.getAddress() != null && frame.getAddress().equals(address)) {
					frames.remove(frame);
					return frame;
				}
				final long delay = now + timeout - System.currentTimeMillis();
				if (delay > 0) frames.wait(delay);
			} while (now + timeout > System.currentTimeMillis());
		}
		return null;
	}

	public void removeOldFramesWithId(final int id) {
		boolean removed;
		do {
			removed = false;
			synchronized (frames) {
				for (final XBeeFrame frame : frames)
					if (frame.getId() == id) {
						frames.remove(frame);
						removed = true;
						log.error("removed frame with old id " + id);
						break;
					}
			}
		} while (removed == true);
	}
	
	// never call waitForData from 2 separate threads at the same time (SerialPortHandler, the only caller to waitForData(), MUST follow this rule)
	public String waitForData(final String data, long timeout) throws InterruptedException {
		final long now = System.currentTimeMillis();

		synchronized (outside_frame) {
			while (true) {
				int idx;
				if ((idx = outside_frame.indexOf(data)) == -1) {
					long delay = now + timeout - System.currentTimeMillis();
					if (delay > 0) outside_frame.wait(delay);
					else break;
				} else {
					final String retval = outside_frame.substring(0, idx);
					outside_frame.delete(0, idx + data.length());
					return retval;
				}
			}
		}
		
		return null;
	}

	private void handleBytes() {
		// remove XON-XOFF
		int pos;
		while ((pos = input_buffer.indexOf(0x11)) != -1) {
			log.error("XON at position " + pos);
			input_buffer.remove(pos);
		}
		while ((pos = input_buffer.indexOf(0x13)) != -1) {
			log.error("XOFF at position " + pos);
			input_buffer.remove(pos);
		}

		while (!input_buffer.isEmpty()) {
			if (input_buffer.get(0) != 0x7e) {
				final byte b = input_buffer.get(0);
				input_buffer.remove(0);
				try {
					synchronized (outside_frame) {
						outside_frame.append(new String(new byte [] { b }, "ISO8859-1").charAt(0));
						outside_frame.notifyAll();
					}
				} catch (final UnsupportedEncodingException e) {
					log.error("invalid byte read " + b, e);
				}

			} else {

				boolean full_frame = false;

				int loc = 1;
				final List<Byte> unescaped_bytes = new ArrayList<Byte>(); // w/o escaped bytes
				handle_frame: {
					unescaped_bytes.add((byte) 0x7e);

					int msb = -1;
					int lsb = -1;
					int size;

					// get msb
					if (loc >= input_buffer.size()) break handle_frame;
					if (input_buffer.get(loc) != 0x7d) msb = 0xff & input_buffer.get(loc++);
					else if (++loc >= input_buffer.size()) break handle_frame;
					else msb = (0xff & input_buffer.get(loc++)) ^ 0x20;

					// get lsb
					if (loc >= input_buffer.size()) break handle_frame;
					if (input_buffer.get(loc) != 0x7d) lsb = 0xff & input_buffer.get(loc++);
					else if (++loc >= input_buffer.size()) break handle_frame;
					else lsb = (0xff & input_buffer.get(loc++)) ^ 0x20;

					unescaped_bytes.add((byte) msb);
					unescaped_bytes.add((byte) lsb);
						
					// compute unescaped frame size including start delimiter and checksum
					size = (msb << 8) + lsb + 4;

					while (loc < input_buffer.size() && unescaped_bytes.size() < size)
						if (input_buffer.get(loc) == 0x7d) {
							if (loc + 1 >= input_buffer.size()) break handle_frame;
							unescaped_bytes.add((byte) ((0xff & input_buffer.get(loc + 1)) ^ 0x20));
							loc += 2;
						} else unescaped_bytes.add(input_buffer.get(loc++));

					if (unescaped_bytes.size() == size) full_frame = true;
				}
				
				if (!full_frame) {
					// partial frame
					if (System.currentTimeMillis() - frame_start_time > 5000) {
						// partial frame delim received more than 5 sec ago
						log.error("removing partial frame: (timeout: " + (System.currentTimeMillis() - frame_start_time) + " - frame start time: " + frame_start_time + ")");
						for (final byte b : input_buffer) log.error("byte: " + Integer.toHexString(b & 0xff) + "h" + " " + (b & 0xff) + "d ('" + (char) b + "')");
						input_buffer.clear();
						frame_start_time = 0;
						// the method will return since input_buffer is now cleared (see 'while' loop condition)
					} return; // return since need more bytes to have a full frame
				} else {
					// at least one full frame in input_buffer
					input_buffer.subList(0, loc).clear();
					// log.debug("received 1 full frame");
					
					// check checksum
					int cksum = 0;
					for (int i = 3; i < unescaped_bytes.size() - 1; i++) cksum += unescaped_bytes.get(i);
					cksum = 0xff - (cksum & 0xff);
					if (cksum != (0xff & unescaped_bytes.get(unescaped_bytes.size() - 1))) {
						log.error("invalid checksum");
						return;
					}

					// store new received frame
					if (unescaped_bytes.size() < 5) {
						log.error("short frame");
						return;
					}
					final int api_id = 0xff & unescaped_bytes.get(3);
					
					int frame_id = -1;
					// initialized array must be sorted to make a binarySearch
					if (Arrays.binarySearch(new int [] { 0x00, 0x01, 0x08, 0x09, 0x17, 0x88, 0x89, 0x97 }, api_id) >= 0) {
						// frame has a frame id
						if (unescaped_bytes.size() < 6) {
							log.error("short frame");
							return;
						}
						frame_id = 0xff & unescaped_bytes.get(4);
					}

					final XBeeFrame frame = new XBeeFrame(api_id, frame_id, ArrayUtils.toPrimitive(unescaped_bytes.subList(frame_id == -1 ? 4 : 5, unescaped_bytes.size() - 1).toArray(new Byte [] {})));
log.debug("frame id: " + Integer.toHexString(api_id));

					if (api_id == 0x8a) {
						// modem status
						try {
							final int status = 0xff & frame.getRawContent()[4];
							switch (status) {
							case 0:
								log.warn("status frame: HARDWARE RESET");
								break;

							case 1:
								log.warn("status frame: WATCHDOG TIMER RESET");
								break;

							case 2:
								log.warn("status frame: ASSOCIATED");
								break;

							case 3:
								log.warn("status frame: DISASSOCIATED");
								break;

							case 4:
								log.warn("status frame: SYNCHRONIZATION LOST");
								break;

							case 5:
								log.warn("status frame: COORDINATOR REALIGNMENT");
								break;

							case 6:
								log.warn("status frame: COORDINATOR STARTED");
								break;

							default:
								log.warn("status frame: invalid status " + status);
								break;
							}
						} catch (final UnsupportedEncodingException e) {
							log.error("invalid status frame");
						}
					} else synchronized (frames) {
						frames.add(frame);
						// afficher les frames reçues
//						log.debug(frame);
						frames.notifyAll();
					}
					// WARNING : Integer a = 0x1; Byte b = 0x1; a.equals(b) is FALSE => input_buffer.contains((byte) 0x7e) != input_buffer.contains(0x7e)
					frame_start_time = input_buffer.contains((byte) 0x7e) ? System.currentTimeMillis() : 0;
				}
			}
		}
	}

	@Override
	public void run() {
		final byte [] tmp_bytes = new byte [1024];

		try {
			stream = getSocket().getInputStream();
		} catch (final IOException e) {
			log.warn(e);
			errorOccured();
			return;
		}

		while (true) {
			if (getError()) return;

			final int nbytes;
			try {
				nbytes = stream.read(tmp_bytes);
			} catch (final IOException ex) {
				log.warn(ex);
				errorOccured();
				return;
			}
			if (nbytes == -1) {
				// EOF
				errorOccured();
				return;
			}

			log.debug("read " + nbytes + " nbytes");
			for (int i = 0; i < nbytes; i++) {
				try {
					log.debug("read byte[" + i + "] = " + Integer.toHexString(0xff & tmp_bytes[i]) + " - char='" + new String(new byte [] { tmp_bytes[i] }, "ASCII") + "'");
				} catch (final UnsupportedEncodingException e) {
					log.error(e);
				}
				input_buffer.add(tmp_bytes[i]);
				if (tmp_bytes[i] == 0x7e && frame_start_time == 0) frame_start_time = System.currentTimeMillis();
			}

			handleBytes();
		}
	}

	public SerialReader() {
		super();
	}
}
