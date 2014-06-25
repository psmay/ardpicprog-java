package us.hfgk.ardpicprog;

import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import us.hfgk.ardpicprog.pylike.Po;
import us.hfgk.ardpicprog.pylike.Str;
import us.hfgk.ardpicprog.pylike.Tuple2;

public class Programmer implements Closeable {

	private static final Str COMMAND_ARG_NOPRESERVE = Str.val("NOPRESERVE");

	private static final Str COMMAND_READBIN = Str.val("READBIN");

	private static final Str COMMAND_SETDEVICE = Str.val("SETDEVICE");

	private static final Str COMMAND_WRITEBIN = Str.val("WRITEBIN");

	private static final Str COMMAND_ARG_FORCE = Str.val("FORCE");

	private static final Str COMMAND_WRITE = Str.val("WRITE");

	private static final Str DOT = Str.val(".");

	private static final Str RESPONSE_UNKNOWN_VERSION = Str.val("ProgramPIC ");

	private static final Str RESPONSE_VERSION_1 = Str.val("ProgramPIC 1.");

	private static final Str COMMAND_PROGRAM_PIC_VERSION = Str.val("PROGRAM_PIC_VERSION");

	private static final Str DETAIL_DEVICE_ID = Str.val("DeviceID");

	private static final Str SPACE = Str.val(" ");

	private static final Str COMMAND_PWROFF = Str.val("PWROFF");

	private static final Str COMMAND_ERASE = Str.val("ERASE");

	private static final Str COMMAND_DEVICES = Str.val("DEVICES");

	private static final Str COMMAND_DEVICE = Str.val("DEVICE");

	private static final Str RESPONSE_PENDING = Str.val("PENDING");

	private static final Str RESPONSE_OK = Str.val("OK");

	private static final Str DEVICE_NAME_AUTO = Str.val("auto");

	private static final Str DEVICE_ID_0000 = Str.val("0000");

	private static final Logger log = Logger.getLogger(Programmer.class.getName());

	private static final int BINARY_WORD_TRANSFER_MAX = 32;

	private static final Str TERM_PACKET = Str.val(new byte[] { 0x00 }, 0, 1);

	private static final Str DETAIL_DEVICE_NAME = Str.val("DeviceName");

	private int bufferPos = 0;
	private Str buffer = Str.EMPTY;

	private int readProgrammerByte() throws IOException {
		if (bufferPos >= Po.len(buffer)) {
			if (!fillBuffer())
				return -1;
		}
		return Po.getitem(buffer, bufferPos++) & 0xFF;
	}

	private boolean fillBuffer() throws IOException {
		this.buffer = com.read(1024);
		this.bufferPos = 0;
		return !buffer.equals(Str.EMPTY);
	}

	private ProgrammerCommPort com = null;

	Programmer(ProgrammerCommPort com) throws IOException {
		this.com = com;
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
			putln(COMMAND_PROGRAM_PIC_VERSION);
			log.finest("Waiting for programmer version");
			Str response = readProgrammerLine();
			if (!response.equals(Str.EMPTY)) {
				log.fine("Non-empty response: '" + response + "'");
				if (response.startswith(RESPONSE_VERSION_1)) {
					// We've found a version 1 sketch, which we can talk to.
					log.fine("Found recognized programmer version");
					return true;
				} else if (response.startswith(RESPONSE_UNKNOWN_VERSION)) {
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

	private static boolean deviceNameMatch(Str name1, Str name2) {
		return name1.lower().equals(name2.lower());
	}

	Map<Str, Str> initDevice(Str deviceName) throws IOException {
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
		Map<Str, Str> details = readDeviceInfo();

		Str detailsDeviceName = details.get(DETAIL_DEVICE_NAME);
		Str detailsDeviceID = details.get(DETAIL_DEVICE_ID);

		if (detailsDeviceName != null) {

			if (Common.strEmpty(deviceName) || deviceName.equals(DEVICE_NAME_AUTO)
					|| deviceNameMatch(deviceName, detailsDeviceName))
				return details;

			if (detailsDeviceID == null || !detailsDeviceID.equals(DEVICE_ID_0000)) {
				throw new DeviceException("Expecting " + deviceName + " but found "
						+ ((detailsDeviceID == null) ? "an unrecognized device" : detailsDeviceID)
						+ " in the programmer");
			}
		}

		// If the DeviceID is not "0000", then the device in the socket reports
		// a device identifier, but it is not supported by the programmer.
		if (detailsDeviceID != null && !detailsDeviceID.equals(DEVICE_ID_0000)) {
			throw new DeviceException("Unsupported device in programmer, ID = " + detailsDeviceID);
		}

		// If the user wanted to auto-detect the device type, then fail now
		// because we don't know what we have in the socket.
		if (deviceName.equals(Str.EMPTY) || deviceName.equals(DEVICE_NAME_AUTO)) {
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

	private static final Str NEWL = Str.val("\n");

	private void putln(Str cmd) throws IOException {
		com.write(cmd.pYappend(NEWL));
	}

	// Sends a command to the sketch. Returns true if the response is "OK".
	// Throws if the response is "ERROR" or a timeout occurred.
	private void commandAll(Str cmd) throws IOException {
		log.fine("Command " + cmd + ": issuing");
		putln(cmd);

		Str response;

		do {
			log.finest("Command " + cmd + ": Reading result line");
			response = readProgrammerLine();
			log.finest("Command " + cmd + ": Read line '" + response + "'");
		} while (response.equals(RESPONSE_PENDING)); // int-running operation:
														// sketch
		// has asked for a inter
		// timeout.
		if (!response.equals(RESPONSE_OK)) {
			throw new CommandException("Response to command '" + cmd + "' not OK: '" + response + "'");
		}
		log.fine("Command " + cmd + ": Got OK response");
	}

	// Returns a list of the available devices.
	Str devices() throws IOException {
		commandDevices();
		return readMultiLineResponse();
	}

	private Str read(int length) throws IOException {
		Str data = Str.EMPTY;

		while (length > 0) {
			int ch = readProgrammerByte();
			if (ch == -1)
				throw new EOFException();
			data = data.pYappend((byte) ch);
			--length;
		}

		return data;
	}

	private Str readProgrammerLine() throws IOException {
		Str line = Str.EMPTY;
		int ch;

		boolean timedOut = false;

		while ((ch = readProgrammerByte()) != -1) {
			if (ch == 0x0A)
				return line;
			else if (ch != 0x0D && ch != 0x00)
				line = line.pYappend((byte) ch);
		}

		if (line.equals(Str.EMPTY) && timedOut)
			throw new ProgrammerException("Timed out");

		return line;
	}

	// Reads a multi-line response, terminated by ".", from the sketch.
	private Str readMultiLineResponse() throws IOException {
		Str response = Str.EMPTY;
		Str line;
		for (;;) {
			line = readProgrammerLine();
			if (line == null || line.equals(DOT))
				break;
			response = response.pYappend(line).pYappend((byte) '\n');
		}
		return response;
	}

	// Reads device information from a multi-line response and returns it as a
	// map.
	private Map<Str, Str> readDeviceInfo() throws IOException {
		log.finest("Reading device info");
		Map<Str, Str> response = new HashMap<Str, Str>();
		Str line;

		for (;;) {
			line = readProgrammerLine();
			log.finest("Line is: '" + line + "'");
			if (line == null || line.equals(DOT))
				break;
			int index = line.find((byte) ':');
			if (index >= 0) {
				Str key = line.pYslice(0, index).strip();
				Str value = line.pYslice(index + 1).strip();
				log.finest("Mapping " + key + " -> " + value);
				response.put(key, value);
			} else {
				log.finest("No ':' found");
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

	private Str packetWordsToBytes(short[] data, int srcOffset, int wordCount) throws IOException {
		Str str = Str.EMPTY;
		int outputLength = (byte) (wordCount << 1);
		str = str.pYappend((byte) outputLength);
		for (int i = 0; i < wordCount; ++i) {
			short word = data[srcOffset + i];
			str = str.pYappend((byte) word).pYappend((byte) (word >> 8));
		}
		return str;
	}

	private void writePacket(Str s) throws IOException, PacketResponseException {
		log.finest("Writing " + Po.len(s) + " byte(s) as packet");
		com.write(s);
		Str response = this.readProgrammerLine();
		if (!response.equals(RESPONSE_OK))
			throw new PacketResponseException("Packet response was '" + response + "'; expected 'OK'");
	}

	private void commandDevice() throws IOException {
		command(COMMAND_DEVICE);
	}

	private void commandDevices() throws IOException {
		command(COMMAND_DEVICES);
	}

	void commandErase(boolean force) throws IOException {
		ArrayList<Str> strs = new ArrayList<Str>();
		strs.add(COMMAND_ERASE);
		addNoPreserveArg(strs, force);
		command(strs);
	}

	private void addNoPreserveArg(Collection<Str> strs, boolean force) {
		if (force) {
			strs.add(COMMAND_ARG_NOPRESERVE);
		}
	}

	private void command(List<Str> strs) throws IOException {
		commandAll(SPACE.join(strs));
	}

	private void command(Str... values) throws IOException {
		command(Arrays.asList(values));
	}

	private void commandPwroff() throws IOException {
		command(COMMAND_PWROFF);
	}

	private void commandReadBin(AddressRange range) throws IOException {
		command(COMMAND_READBIN, range.hexInclusive());
	}

	private Map<Str, Str> commandSetDevice(Str deviceName) throws IOException {
		command(COMMAND_SETDEVICE, deviceName);
		return readDeviceInfo();
	}

	private void commandWriteBin(int start, boolean force) throws IOException {
		ArrayList<Str> strs = new ArrayList<Str>();
		strs.add(COMMAND_WRITEBIN);
		addForceArg(strs, force);
		strs.add(Common.toX4Str(start));

		command(strs);
	}

	private void commandWrite(int start, boolean force, short... values) throws IOException {
		ArrayList<Str> strs = new ArrayList<Str>();
		strs.add(COMMAND_WRITE);
		addForceArg(strs, force);
		strs.addAll(Common.toX4(asNumberList(start, values)));

		command(strs);
	}

	private void addForceArg(Collection<Str> strs, boolean force) {
		if (force) {
			strs.add(COMMAND_ARG_FORCE);
		}
	}

	private static List<Number> asNumberList(int start, short... values) {
		ArrayList<Number> numbers = new ArrayList<Number>(values.length + 1);
		numbers.add(start);
		addShortValues(numbers, values);
		return numbers;
	}

	private static void addShortValues(Collection<Number> numbers, short... values) {
		for (short value : values) {
			numbers.add(value);
		}
	}

	private void writeFrom(boolean forceCalibration, AddressRange range, short[] data) throws IOException,
			PacketResponseException {

		if (range.size() != data.length)
			throw new IllegalArgumentException("Data array must be the same size as the range");

		int offset = 0;

		int rangeSize = range.size();

		if (rangeSize == 5) {
			// Cannot use "WRITEBIN" for exactly 5 words (10 bytes), so use
			// "WRITE" instead.
			this.commandWrite(range.start(), forceCalibration, data);
		} else {
			this.commandWriteBin(range.start(), forceCalibration);

			while (offset < rangeSize) {
				int transferSize = min(Programmer.BINARY_WORD_TRANSFER_MAX, rangeSize - offset);
				this.writePacket(this.packetWordsToBytes(data, offset, transferSize));
				offset += transferSize;
			}

			// Terminating packet.
			this.writePacket(TERM_PACKET);
		}
	}

	public short[] readCopy(AddressRange range) throws IOException {
		int rangePost = range.post();
		ArrayList<Short> data = new ArrayList<Short>(range.size());

		int current = range.start();

		this.commandReadBin(range);

		while (current < rangePost) {
			int pktlen = this.readProgrammerByte();

			if (pktlen < 0) {
				throw new EOFException();
			} else if (pktlen > 0) {
				Str bytes = this.read(pktlen);
				int numWords = min(rangePost - current, pktlen / 2);
				addLittleWords(data, bytes, numWords);
				current += numWords;
			} else {
				// pktlen == 0
				break;
			}
		}
		if (current < rangePost) {
			throw new ProgrammerException("Could not fill entire buffer");
		}
		return toPrimitiveArray(data);
	}

	private static final short[] toPrimitiveArray(List<Short> data) {
		short[] output = new short[data.size()];
		int actualCount = 0;

		for (short z : data) {
			++actualCount;
			if (actualCount > output.length) {
				output = Arrays.copyOf(output, actualCount);
			}
			output[actualCount - 1] = z;
		}

		if (actualCount > output.length) {
			output = Arrays.copyOf(output, actualCount);
		}

		return output;
	}

	private static void addLittleWords(List<Short> dest, Str src, int wordCount) {
		for (int byteIndex : Po.xrange(0, 2 * wordCount, 2)) {
			dest.add(getLittleWord(src, byteIndex));
		}
	}

	private static final short getLittleWord(Str str, int index) {
		return bytesToWord(Po.getitem(str, index + 1), Po.getitem(str, index));
	}

	private static final short bytesToWord(byte hiByte, byte loByte) {
		int lo = (loByte & 0xFF);
		int hi = (hiByte & 0xFF) << 8;
		return (short) (lo | hi);
	}

	private static final int min(int a, int b) {
		return (a < b) ? a : b;
	}

	private boolean forceCalibration = false;

	public void writeFrom(AddressRange range, short[] srcArray) throws IOException {
		writeFrom(forceCalibration, range, srcArray);
	}

	public void setForceCalibration(boolean value) {
		forceCalibration = value;
	}

	public boolean getForceCalibration() {
		return forceCalibration;
	}

	void write(HexFile hexFile, boolean forceCalibration) throws IOException {
		setForceCalibration(forceCalibration);
		write(hexFile);
	}

	void write(HexFile hexFile) throws IOException {
		write(hexFile.getMetadata().getDevice(), hexFile.getWords());
	}

	void write(DeviceDetails device, ReadableShortList data) throws IOException {
		AddressRange programRangeForWrite = (this.getForceCalibration() || device.reservedRange.isEmpty()) ? device.programRange
				: device.programStartToReservedStart();

		// Write the contents of program memory.

		this.writeArea(data, "program memory", programRangeForWrite, device.programRange.isEmpty());

		// Write data memory before config memory in case the configuration
		// word turns on data protection and thus hinders data verification.
		this.writeArea(data, "data memory", device.dataRange, device.dataRange.isEmpty());

		// Write the contents of config memory.
		this.writeArea(data, "id words and fuses", device.configRange, device.configRange.isEmpty());

		log.info("done.");
	}

	private void writeArea(ReadableShortList source, String desc, AddressRange range, boolean skip) throws IOException {
		if (skip)
			log.info("Skipped burning " + desc + ",");
		else {
			log.info("Burning " + desc + ",");
			int writeLen = source.writeTo(this, range);
			Programmer.reportCount(writeLen);
		}
	}

	ReadableShortList readAreas(List<Tuple2<String, AddressRange>> areas) throws IOException {
		ShortList dest = Common.getBlankShortList();

		for (Tuple2<String, AddressRange> rd : areas) {
			this.readArea(dest, rd);
		}

		log.info("done.");
		return dest;
	}

	private void readArea(MutableShortList dest, Tuple2<String, AddressRange> area) throws IOException {
		String areaDesc = area._1;
		AddressRange range = area._2;
		if (!range.isEmpty()) {
			log.info("Reading " + areaDesc + ",");
			dest.readFrom(this, range);
		} else {
			log.info("Skipped reading " + areaDesc + ",");
		}
	}

	boolean blankCheckAll(HexFileMetadata metadata) throws IOException {
		for (Tuple2<String, AddressRange> area : metadata.getAreas()) {
			if (!this.blankCheckArea(metadata, area))
				return false;
		}
		return true;
	}

	private boolean blankCheckRange(HexFileMetadata metadata, AddressRange range) throws IOException {
		final short[] buf = this.readCopy(range);

		int i = range.start();
		for (short word : buf) {
			if (!metadata.wouldBeAllOnes(i, word)) {
				return false;
			}
			++i;
		}

		return true;
	}

	private boolean blankCheckArea(HexFileMetadata metadata, Tuple2<String, AddressRange> area) throws IOException {
		String areaDesc = area._1;
		AddressRange range = area._2;
		if (!range.isEmpty()) {
			log.info("Blank checking " + areaDesc + ",");
			if (this.blankCheckRange(metadata, range)) {
				log.info("Looks blank");
				return true;
			} else {
				log.info("Looks non-blank");
				return false;
			}
		} else {
			log.info("Skipped blank checking " + areaDesc + ",");
			return true;
		}
	}

	private static void reportCount(int count) {
		log.info((count == 1) ? " 1 location," : " " + count + " locations,");
	}

}
