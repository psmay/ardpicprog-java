package us.hfgk.ardpicprog;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public abstract class ProgrammerPort {
	private static final Logger log = Logger.getLogger(ProgrammerPort.class.getName());

	protected static final int BINARY_TRANSFER_MAX = 64;
	protected int buflen;
	protected int bufposn;
	protected int timeoutSecs;
	protected byte[] buffer = new byte[1024];

	protected ProgrammerPort(int buflen, int bufposn, int timeoutSecs) {
		this.buflen = buflen;
		this.bufposn = bufposn;
		this.timeoutSecs = timeoutSecs;
		log.info("Constructed with buflen=" + buflen + ", bufpos=" + bufposn + ", timeoutSecs=" + timeoutSecs);
		init();
	}

	protected ProgrammerPort() {
		this(0, 0, 3);
	}

	private static boolean deviceNameMatch(String name1, String name2) {
		return name1.equalsIgnoreCase(name2);
	}

	public Map<String, String> initDevice(String deviceName) throws IOException {
		// Try the "DEVICE" command first to auto-detect the type of
		// device that is in the programming socket.
		try {
			command("DEVICE");
		} catch (IOException e) {
			throw new DeviceException(
					"No device in programmer or programming voltage not available: " + e.getMessage(), e);
		}

		// Fetch the device details. If we have a DeviceName and it matches,
		// then we are ready to go. If the DeviceName does not match, then we
		// know the type of device in the socket, but it isn't what we wanted.
		// If the DeviceID is "0000" but we have a DeviceName, then the device
		// is an EEPROM that needs a manual override to change the default.
		Map<String, String> details = readDeviceInfo();
		// Map<String,String>::const_iterator it = details.find("DeviceName");
		String it = details.get("DeviceName");

		if (it != null) {
			if (Common.stringEmpty(deviceName) || deviceName.equals("auto"))
				return details; // Use auto-detected device in the socket.
			if (deviceNameMatch(deviceName, it))
				return details;

			it = details.get("DeviceID");
			if (it == null || !it.equals("0000")) {
				throw new DeviceException("Expecting " + deviceName + " but found " + it + " in the programmer");
			}
		}

		// If the DeviceID is not "0000", then the device in the socket reports
		// a device identifier, but it is not supported by the programmer.
		it = details.get("DeviceID");
		if (it != null && !it.equals("0000")) {
			throw new DeviceException("Unsupported device in programmer, ID = " + it);
		}

		// If the user wanted to auto-detect the device type, then fail now
		// because we don't know what we have in the socket.
		if (deviceName.isEmpty() || deviceName.equals("auto")) {
			throw new DeviceException("Cannot autodetect: device in programmer does not have an identifier.");
		}

		// Try using "SETDEVICE" to manually select the device.
		String cmd = "SETDEVICE " + deviceName;
		try {
			command(cmd);
			return readDeviceInfo();
		} catch (IOException e) {
			// The device is not supported. Print a list of all supported
			// devices.
			String msg = "Device " + deviceName + " is not supported by the programmer.";
			try {
				command("DEVICES");
				String devices = readMultiLineResponse();
				msg += "Supported devices:\n" + devices + "* = autodetected";
			} catch (IOException ee) {
			}

			throw new DeviceException(msg);
		}
	}

	private static byte[] asBytes(String str) {
		return str.getBytes(Common.UTF8);
	}

	// Sends a command to the sketch. Returns true if the response is "OK".
	// Throws if the response is "ERROR" or a timeout occurred.
	public void command(String cmd) throws IOException {
		String line = cmd;
		line += '\n';
		byte[] lineBytes = asBytes(line);
		write(lineBytes, lineBytes.length);
		String response = readLine();
		log.info("Line read by command: " + response);
		while (response.equals("PENDING")) {
			// int-running operation: sketch has asked for a inter timeout.
			response = readLine();
			log.info("Line read by command: " + response);
		}
		if (!response.equals("OK")) {
			throw new CommandException("Response not OK: " + response);
		}
	}

	// Returns a list of the available devices.
	public String devices() throws IOException {
		try {
			command("DEVICES");
			return readMultiLineResponse();
		} catch (CommandException e) {
			return "";
		}
	}

	public void readData(int start, int end, List<Short> data) throws IOException {
		readData(start, end, data, 0);
	}

	// Reads a large block of data using "READBIN".
	public void readData(int start, int end, List<Short> data, int offset) throws IOException {
		byte[] buffer = new byte[256];

		String strbuffer = "READBIN " + Common.toX4(start) + "-" + Common.toX4(end);

		command(strbuffer);

		while (start <= end) {
			int pktlen = readChar();
			if (pktlen < 0)
				throw new EOFException();
			else if (pktlen == 0)
				break;
			read(buffer, pktlen);
			int numWords = pktlen / 2;
			if ((numWords) > (end - start + 1))
				numWords = end - start + 1;
			for (int index = 0; index < numWords; ++index) {
				data.set(offset + index, (short) ((buffer[index * 2] & 0xFF) | ((buffer[index * 2 + 1] & 0xFF) << 8)));
			}
			offset += numWords;
			start += numWords;
		}
		if (start <= end) {
			throw new ProgrammerException("Could not fill entire buffer");
		}
	}

	private void read(byte[] data, int len, int offset) throws IOException {
		while (len > 0) {
			int ch = readChar();
			if (ch == -1)
				throw new EOFException();
			data[offset++] = (byte) (0xFF & ch);
			--len;
		}
	}

	private void read(byte[] data, int len) throws IOException {
		read(data, len, 0);
	}

	private int readChar() throws IOException {
		if (bufposn >= buflen) {
			if (!fillBuffer())
				return -1;
		}
		return buffer[bufposn++] & 0xFF;
	}

	private String readLine() throws IOException {
		String line = "";
		int ch;

		boolean timedOut = false;

		while ((ch = readChar()) != -1) {
			if (ch == 0x0A)
				return line;
			else if (ch != 0x0D && ch != 0x00)
				line += (char) ch;
		}

		if (line.isEmpty() && timedOut)
			throw new ProgrammerException("Timed out");

		return line;
	}

	// Reads a multi-line response, terminated by ".", from the sketch.
	private String readMultiLineResponse() throws IOException {
		String response = "";
		String line;
		for (;;) {
			line = readLine();
			if (line == null || line.equals("."))
				break;
			response += line;
			response += '\n';
		}
		return response;
	}

	private static String trim(String str) {
		int first = 0;
		int last = str.length();
		while (first < last) {
			char ch = str.charAt(first);
			if (ch != ' ' && ch != '\t')
				break;
			++first;
		}
		while (first < last) {
			char ch = str.charAt(last - 1);
			if (ch != ' ' && ch != '\t')
				break;
			--last;
		}
		return str.substring(first, last);
	}

	// Reads device information from a multi-line response and returns it as a
	// map.
	private Map<String, String> readDeviceInfo() throws IOException {
		Map<String, String> response = new HashMap<String, String>();
		String line;

		for (;;) {
			line = readLine();
			if (line == null || line.equals("."))
				break;
			int index = line.indexOf(':');
			if (index >= 0) {
				response.put(trim(line.substring(0, index)), trim(line.substring(index + 1)));
			}
		}
		return response;
	}

	protected void init() {
	}

	protected abstract void write(byte[] packet, int len) throws IOException;

	protected abstract boolean fillBuffer() throws IOException;

	public abstract void open(String port, int speed) throws IOException;

	public void close() throws IOException {
		if (deviceStillOpen()) {
			command("PWROFF");
			closeDevice();
		}
	}

	protected abstract boolean deviceStillOpen();

	protected abstract void closeDevice() throws IOException;

	public void writeData(int start, int end, ArrayList<Short> data, int offset, boolean force) throws IOException {
		byte[] buffer = new byte[BINARY_TRANSFER_MAX + 1];
		int len = (end - start + 1) * 2;
		int index;
		short word;
		if (len == 10) {
			// Cannot use "WRITEBIN" for exactly 10 bytes, so use "WRITE"
			// instead.
			command("WRITE " + (force ? "FORCE " : "")
					+ Common.toX4(start, data.get(0), data.get(1), data.get(2), data.get(3), data.get(4)));
		}

		command("WRITEBIN " + (force ? "FORCE " : "") + Common.toX4(start));
		while (len >= BINARY_TRANSFER_MAX) {
			buffer[0] = (byte) BINARY_TRANSFER_MAX;
			for (index = 0; index < BINARY_TRANSFER_MAX; index += 2) {
				word = data.get(offset + index / 2);
				buffer[offset + index + 1] = (byte) word;
				buffer[offset + index + 2] = (byte) (word >> 8);
			}
			writePacket(buffer, BINARY_TRANSFER_MAX + 1);
			offset += BINARY_TRANSFER_MAX / 2;
			len -= BINARY_TRANSFER_MAX;
		}
		if (len > 0) {
			buffer[0] = (byte) len;
			for (index = 0; index < len; index += 2) {
				word = data.get(offset + index / 2);
				buffer[index + 1] = (byte) word;
				buffer[index + 2] = (byte) (word >> 8);
			}
			writePacket(buffer, len + 1);
		}
		buffer[0] = (byte) 0x00; // Terminating packet.
		writePacket(buffer, 1);

	}

	private void writePacket(byte[] packet, int len) throws IOException {
		write(packet, len);
		String response = readLine();
		if (!response.equals("OK"))
			throw new PacketResponseException();
	}

	public static class ProgrammerException extends IOException {
		private static final long serialVersionUID = 1L;

		public ProgrammerException() {
			super();
		}

		public ProgrammerException(String message, Throwable cause) {
			super(message, cause);
		}

		public ProgrammerException(String message) {
			super(message);
		}

		public ProgrammerException(Throwable cause) {
			super(cause);
		}

	}

	public static class CommandException extends ProgrammerException {
		private static final long serialVersionUID = 1L;

		public CommandException() {
			super();
		}

		public CommandException(String message, Throwable cause) {
			super(message, cause);
		}

		public CommandException(String message) {
			super(message);
		}

		public CommandException(Throwable cause) {
			super(cause);
		}
	}

	public static class EOFException extends ProgrammerException {

		private static final long serialVersionUID = 1L;

		public EOFException() {
			super();

		}

		public EOFException(String message, Throwable cause) {
			super(message, cause);

		}

		public EOFException(String message) {
			super(message);

		}

		public EOFException(Throwable cause) {
			super(cause);

		}

	}

	public static class PacketResponseException extends ProgrammerException {

		private static final long serialVersionUID = 1L;

		public PacketResponseException() {
			super();

		}

		public PacketResponseException(String message, Throwable cause) {
			super(message, cause);

		}

		public PacketResponseException(String message) {
			super(message);

		}

		public PacketResponseException(Throwable cause) {
			super(cause);

		}

	}

	public static class DeviceException extends ProgrammerException {
		private static final long serialVersionUID = 1L;

		public DeviceException() {
			super();
		}

		public DeviceException(String message, Throwable cause) {
			super(message, cause);
		}

		public DeviceException(String message) {
			super(message);
		}

		public DeviceException(Throwable cause) {
			super(cause);
		}

	}
}
