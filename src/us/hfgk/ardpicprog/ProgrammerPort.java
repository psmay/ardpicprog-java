package us.hfgk.ardpicprog;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import us.hfgk.ardpicprog.pylike.Po;
import us.hfgk.ardpicprog.pylike.Serial;
import us.hfgk.ardpicprog.pylike.Str;

public class ProgrammerPort implements Closeable {
	private static final Logger log = Logger.getLogger(ProgrammerPort.class.getName());

	private static final int BINARY_WORD_TRANSFER_MAX = 32;

	private static final Str TERM_PACKET = Str.val(new byte[] { 0x00 }, 0, 1);

	public static class CommBuffer {
		private int pos = 0;
		private Str buffer = Str.EMPTY;

		int fillFrom(Serial in) throws IOException {
			buffer = in.read(1024);
			pos = 0;

			if (buffer.equals(Str.EMPTY))
				return -1;
			return Po.len(buffer);
		}

		private int readProgrammerByte(ProgrammerPort src) throws IOException {
			if (pos >= Po.len(buffer)) {
				if (!src.com.fillBuffer(this))
					return -1;
			}
			return Po.getitem(buffer, pos++) & 0xFF;
		}
	}

	private ProgrammerCommPort com = null;

	private CommBuffer buff = new CommBuffer();

	ProgrammerPort(ProgrammerCommPort com) throws IOException {
		this.com = com;
		com.init();
		com.setReceiveTimeout(1000);
		boolean versionCompatible = pollVersion(0);
		if (!versionCompatible)
			throw new PortSetupException("Programmer did not respond with a compatible version string");
		com.setReceiveTimeout(3000);
	}

	private boolean pollVersion(int retry) throws IOException {
		if (retry <= 0)
			retry = 5;
		while (retry > 0) {
			log.fine("Requesting programmer version");
			writeString("PROGRAM_PIC_VERSION\n");
			log.finest("Waiting for programmer version");
			String response = readProgrammerLine();
			if (!Common.stringEmpty(response)) {
				if (response.indexOf("ProgramPIC 1.") == 0) {
					// We've found a version 1 sketch, which we can talk to.
					log.fine("Found recognized programmer version");
					return true;
				} else if (response.indexOf("ProgramPIC ") == 0) {
					// Version 2 or higher sketch - cannot talk to this.
					log.fine("Found incompatible programmer version");
					return false;
				}
			}

			log.fine("Programmer did not respond with version");

			--retry;
		}
		return (retry > 0);
	}

	private static boolean deviceNameMatch(String name1, String name2) {
		return name1.equalsIgnoreCase(name2);
	}

	Map<String, String> initDevice(String deviceName) throws IOException {
		// Try the "DEVICE" command first to auto-detect the type of
		// device that is in the programming socket.
		try {
			commandDevice();
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
		String detailsDeviceName = details.get("DeviceName");
		String detailsDeviceID = details.get("DeviceID");

		if (detailsDeviceName != null) {

			if (Common.stringEmpty(deviceName) || deviceName.equals("auto")
					|| deviceNameMatch(deviceName, detailsDeviceName))
				return details;

			if (detailsDeviceID == null || !detailsDeviceID.equals("0000")) {
				throw new DeviceException("Expecting " + deviceName + " but found "
						+ ((detailsDeviceID == null) ? "an unrecognized device" : detailsDeviceID)
						+ " in the programmer");
			}
		}

		// If the DeviceID is not "0000", then the device in the socket reports
		// a device identifier, but it is not supported by the programmer.
		if (detailsDeviceID != null && !detailsDeviceID.equals("0000")) {
			throw new DeviceException("Unsupported device in programmer, ID = " + detailsDeviceID);
		}

		// If the user wanted to auto-detect the device type, then fail now
		// because we don't know what we have in the socket.
		if (deviceName.isEmpty() || deviceName.equals("auto")) {
			throw new DeviceException("Cannot autodetect: device in programmer does not have an identifier.");
		}

		// Try using "SETDEVICE" to manually select the device.
		try {
			return commandSetDevice(deviceName);
		} catch (IOException e) {
			// The device is not supported. Print a list of all supported
			// devices.
			String msg = "Device " + deviceName + " is not supported by the programmer.";
			try {
				msg += "Supported devices:\n" + devices() + "* = autodetected";
			} catch (IOException ee) {
				msg += "Failed to list supported devices.";
			}

			throw new DeviceException(msg);
		}
	}

	// Sends a command to the sketch. Returns true if the response is "OK".
	// Throws if the response is "ERROR" or a timeout occurred.
	private void command(String cmd) throws IOException {
		String line = cmd + "\n";

		log.fine("Command " + cmd + ": issuing");
		writeString(line);

		String response;

		do {
			log.finest("Command " + cmd + ": Reading result line");
			response = readProgrammerLine();
			log.finest("Command " + cmd + ": Read line '" + response + "'");
		} while (response.equals("PENDING")); // int-running operation: sketch
												// has asked for a inter
												// timeout.
		if (!response.equals("OK")) {
			throw new CommandException("Response to command '" + cmd + "' not OK: '" + response + "'");
		}
		log.fine("Command " + cmd + ": Got OK response");
	}

	private void writeString(String str) throws IOException {
		// FIXME
		com.write(Str.val(str));
	}

	// Returns a list of the available devices.
	String devices() throws IOException {
		commandDevices();
		return readMultiLineResponse();
	}

	private void read(byte[] data, int offset, int length) throws IOException {
		while (length > 0) {
			int ch = readProgrammerByte();
			if (ch == -1)
				throw new EOFException();
			data[offset++] = (byte) (0xFF & ch);
			--length;
		}
	}

	private int readProgrammerByte() throws IOException {
		return buff.readProgrammerByte(this);
	}

	private String readProgrammerLine() throws IOException {
		String line = "";
		int ch;

		boolean timedOut = false;

		while ((ch = readProgrammerByte()) != -1) {
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
			line = readProgrammerLine();
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
			line = readProgrammerLine();
			if (line == null || line.equals("."))
				break;
			int index = line.indexOf(':');
			if (index >= 0) {
				response.put(trim(line.substring(0, index)), trim(line.substring(index + 1)));
			}
		}
		return response;
	}

	public void close() throws IOException {
		if (com.isStillOpen()) {
			commandPwroff();
			com.close();
		}
	}

	private void bufferWords(short[] data, int srcOffset, int wordCount, ByteArrayOutputStream os) throws IOException {
		int outputLength = (byte) (wordCount << 1);
		os.write((byte) outputLength);
		for (int i = 0; i < wordCount; ++i) {
			short word = data[srcOffset + i];
			os.write((byte) word);
			os.write((byte) (word >> 8));
		}
	}

	private static Str getStrFromJavaBaos(ByteArrayOutputStream os) {
		byte[] b = os.toByteArray();
		return Str.val(b, 0, b.length);
	}

	private void writePacketAndClear(ByteArrayOutputStream os) throws IOException {
		Str s = getStrFromJavaBaos(os);
		writePacket(s);
		os.reset();
	}

	private void writePacket(Str s) throws IOException, PacketResponseException {
		log.finest("Writing " + Po.len(s) + " byte(s) as packet");
		this.com.write(s);
		String response = this.readProgrammerLine();
		if (!response.equals("OK"))
			throw new PacketResponseException("Packet response was '" + response + "'; expected 'OK'");
	}

	private void commandDevice() throws IOException {
		command("DEVICE");
	}

	private void commandDevices() throws IOException {
		command("DEVICES");
	}

	void commandErase(boolean force) throws IOException {
		if (force) {
			command("ERASE NOPRESERVE");
		} else {
			command("ERASE");
		}
	}

	private void commandPwroff() throws IOException {
		command("PWROFF");
	}

	private void commandReadBin(IntRange range) throws IOException {
		command("READBIN " + Common.toX4("-", (short) range.start(), (short) range.end()));
	}

	private Map<String, String> commandSetDevice(String deviceName) throws IOException {
		command("SETDEVICE " + deviceName);
		return readDeviceInfo();
	}

	private void commandWriteBin(int start, boolean force) throws IOException {
		command("WRITEBIN " + (force ? "FORCE " : "") + Common.toX4(" ", (short) start));
	}

	private void commandWrite(int start, boolean force, short... values) throws IOException {
		command("WRITE " + (force ? "FORCE " : "") + Common.toX4(" ", (short) start) + " " + Common.toX4(" ", values));
	}

	ShortSource getShortSource() {
		return new PortBlockIO(this);
	}

	ShortSink getShortSink(boolean forceCalibration) {
		return new PortBlockIO(this, forceCalibration);
	}

	private static class PortBlockIO implements ShortSink, ShortSource {
		private ProgrammerPort port;
		private boolean forceCalibration;

		PortBlockIO(ProgrammerPort port) {
			this(port, false);
		}

		PortBlockIO(ProgrammerPort port, boolean forceCalibration) {
			this.port = port;
			this.forceCalibration = forceCalibration;
		}

		public void writeFrom(IntRange range, short[] data, int offset) throws IOException {
			ByteArrayOutputStream buffer = new ByteArrayOutputStream(ProgrammerPort.BINARY_WORD_TRANSFER_MAX * 2 + 1);
			int wordlen = (range.size());

			if (wordlen == 5) {
				// Cannot use "WRITEBIN" for exactly 10 bytes, so use "WRITE"
				// instead.

				port.commandWrite(range.start(), forceCalibration, Arrays.copyOfRange(data, 0, 5));
			}

			port.commandWriteBin(range.start(), forceCalibration);
			while (wordlen >= ProgrammerPort.BINARY_WORD_TRANSFER_MAX) {
				port.bufferWords(data, offset, ProgrammerPort.BINARY_WORD_TRANSFER_MAX, buffer);
				port.writePacketAndClear(buffer);
				offset += ProgrammerPort.BINARY_WORD_TRANSFER_MAX;
				wordlen -= ProgrammerPort.BINARY_WORD_TRANSFER_MAX;
			}
			if (wordlen > 0) {
				port.bufferWords(data, offset, wordlen, buffer);
				port.writePacketAndClear(buffer);
			}

			// Terminating packet.
			port.writePacket(TERM_PACKET);
		}

		@Override
		public void readTo(IntRange range, short[] data, int offset) throws IOException {
			int current = range.start();
			byte[] buffer = new byte[256];

			port.commandReadBin(range);

			while (current < range.post()) {
				int pktlen = port.readProgrammerByte();
				if (pktlen < 0)
					throw new EOFException();
				else if (pktlen == 0)
					break;
				port.read(buffer, 0, pktlen);
				int numWords = pktlen / 2;
				if ((numWords) > (range.post() - current))
					numWords = range.post() - current;
				for (int index = 0; index < numWords; ++index) {
					data[offset + index] = (short) ((buffer[index * 2] & 0xFF) | ((buffer[index * 2 + 1] & 0xFF) << 8));
				}
				offset += numWords;
				current += numWords;
			}
			if (current < range.post()) {
				throw new ProgrammerException("Could not fill entire buffer");
			}
		}
	}

}
