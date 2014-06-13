package us.hfgk.ardpicprog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.logging.Logger;

public class HexFile {
	private static final int READ_RECORD_OK = 0x7FFFFFFF;

	private static final Logger log = Logger.getLogger(HexFile.class.getName());

	public static final int FORMAT_AUTO = -1;
	public static final int FORMAT_IHX8M = 0;
	public static final int FORMAT_IHX16 = 1;
	public static final int FORMAT_IHX32 = 2;

	public String deviceName() {
		return _deviceName;
	}

	public void setFormat(int format) {
		_format = format;
	}

	private int dataBits() {
		return _dataBits;
	}

	int programSizeWords() {
		return _programRange.size();
	}

	public int dataSizeBytes() {
		return _dataRange.size() * dataBits() / 8;
	}

	private String _deviceName;
	private IntRange _programRange = IntRange.getEnd(0, 0x07FF);
	private IntRange _configRange = IntRange.getEnd(0x2000, 0x2007);
	private IntRange _dataRange = IntRange.getEnd(0x2100, 0x217F);
	private IntRange _reservedRange = IntRange.getEnd(0x0800, 0x07FF);
	private int _programBits = 14;
	private int _dataBits = 8;
	private int _format = FORMAT_AUTO;

	private int count = 0;

	private SparseShortList words = new SparseShortList();

	private static String fetchMap(Map<String, String> details, String key, String defValue) {
		String value = details.get(key);
		return (value == null) ? defValue : value;
	}

	static IntRange parseRange(String value) throws HexFileException {
		int index = value.indexOf('-');
		if (index == -1)
			throw new HexFileException("Invalid range '" + value + "' (missing '-')");
		Integer start, end;
		start = Common.parseHex(value.substring(0, index));
		if (start == null)
			throw new HexFileException("Invalid range '" + value + "' (start not a number)");
		end = Common.parseHex(value.substring(index + 1));
		if (end == null)
			throw new HexFileException("Invalid range '" + value + "' (end not a number)");
		return IntRange.getEnd(start, end);
	}

	public void setDeviceDetails(Map<String, String> details) throws HexFileException {
		String value = details.get("DeviceName");
		_deviceName = (value != null) ? value : "";
		_programRange = parseRangeUnlessEmpty(details.get("ProgramRange"), 0x0001);
		_programBits = Common.parseInt(fetchMap(details, "ProgramBits", "14"), 0);
		_configRange = parseRangeUnlessEmpty(details.get("ConfigRange"), 0x2000);
		_dataRange = parseRangeUnlessEmpty(details.get("DataRange"), 0x2100);
		_dataBits = Common.parseInt(fetchMap(details, "DataBits", "8"), 0);
		_reservedRange = parseRangeUnlessEmpty(details.get("ReservedRange"), _programRange.post());

		if (_programBits < 1)
			throw new HexFileException("Invalid program word width " + _programBits);
		if (_dataBits < 1)
			throw new HexFileException("Invalid data word width " + _programBits);
	}

	private IntRange parseRangeUnlessEmpty(String value, int emptyStartAddress) throws HexFileException {
		return Common.stringEmpty(value) ? IntRange.empty(emptyStartAddress) : parseRange(value);
	}

	private short word(int address) {
		return words.get(address, fullWord(address));
	}

	private short fullWord(int address) {
		if (_dataRange.containsValue(address))
			return (short) ((1 << _dataBits) - 1);
		else
			return (short) ((1 << _programBits) - 1);
	}

	private boolean wouldBeAllOnes(int address, short wordValue) {
		return wordValue == fullWord(address);
	}

	private boolean isAllOnes(int address) {
		return wouldBeAllOnes(address, word(address));
	}

	public boolean canForceCalibration() {
		if (_reservedRange.isEmpty())
			return true; // No reserved words, so force is trivially ok.
		for (int address = _reservedRange.start(); address < _reservedRange.post(); ++address) {
			if (!isAllOnes(address))
				return true;
		}
		return false;
	}

	private void readPart(ProgrammerPort port, String areaDesc, IntRange range) throws IOException {
		if (!range.isEmpty()) {
			log.info("Reading " + areaDesc + ",");
			readBlock(port, range);
		} else {
			log.info("Skipped reading " + areaDesc + ",");
		}
	}

	private boolean blankCheckPart(ProgrammerPort port, String areaDesc, IntRange range) throws IOException {
		if (!range.isEmpty()) {
			log.info("Blank checking " + areaDesc + ",");
			if (blankCheckBlock(port, range)) {
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

	public boolean blankCheckRead(ProgrammerPort port) throws IOException {
		return blankCheckPart(port, "program memory", _programRange) && blankCheckPart(port, "data memory", _dataRange)
				&& blankCheckPart(port, "id words and fuses", _configRange);
	}

	public void read(ProgrammerPort port) throws IOException {
		words.clear();

		readPart(port, "program memory", _programRange);
		readPart(port, "data memory", _dataRange);
		readPart(port, "id words and fuses", _configRange);

		log.info("done.");
	}

	private void readBlock(ProgrammerPort port, IntRange range) throws IOException {
		words.readBlock(port.getBlockReader(), range);
	}

	private boolean blankCheckBlock(ProgrammerPort port, IntRange range) throws IOException {
		final short[] buf = new short[range.size()];
		port.getBlockReader().doRead(range, buf, 0);

		int i = range.start();
		for (short word : buf) {
			if (!wouldBeAllOnes(i, word)) {
				return false;
			}
			++i;
		}

		return true;
	}

	private void flush() {
		System.out.flush();
	}

	private void reportCount() {
		if (count == 1)
			log.info(" 1 location,");
		else
			log.info(" " + count + " locations,");
		count = 0;
	}

	private static short shortFromBytes(byte high, byte low) {
		return (short) (((high & 0xFF) << 8) | (low & 0xFF));
	}

	// Read a big-endian word value from a buffer.
	private static short readBigWord(ArrayList<Byte> line, int index) {
		return shortFromBytes(line.get(index), line.get(index + 1));
	}

	// Read a little-endian word value from a buffer.
	private static short readLittleWord(ArrayList<Byte> buf, int index) {
		return shortFromBytes(buf.get(index + 1), buf.get(index));
	}

	public void load(InputStream file) throws IOException {
		boolean startLine = true;
		ArrayList<Byte> line = new ArrayList<Byte>();
		int ch, digit;
		int nibble = -1;

		int baseAddress = 0;

		while ((ch = file.read()) >= 0) {

			if (ch == ' ' || ch == '\t')
				continue;

			if (ch == '\r' || ch == '\n') {
				if (nibble != -1) {
					// Half a byte at the end of the line.
					throw new HexFileException("Half byte at end of line");
				}

				if (!startLine) {
					validateSize(line);
					validateChecksum(line);

					baseAddress = readRecord(line, baseAddress);
					if (baseAddress == READ_RECORD_OK) {
						return; // ok
					}
				}
				line.clear();
				startLine = true;
				continue;
			}

			digit = examineDigit(ch);

			if (digit == DIGIT_COLON) {
				if (!startLine) {
					// ':' did not appear at the start of a line.
					throw new HexFileException("':' must not appear after the beginning of a line");
				} else {
					startLine = false;
					continue;
				}
			} else if (digit < 0) {
				// Invalid character in hex file.
				throw new HexFileException("Invalid hex character '" + ch + "'");
			} else {

				if (startLine) {
					// Hex digit at the start of a line.
					throw new HexFileException("Hex digit must not appear at the beginning of a line");
				}
				if (nibble == -1) {
					nibble = digit;
				} else {
					line.add(((byte) ((nibble << 4) | digit)));
					nibble = -1;
				}
			}
		}

		throw new HexFileException("Unexpected end of input");

	}

	private void validateSize(ArrayList<Byte> line) throws HexFileException {
		if (line.size() < 5) {
			// Not enough bytes to form a valid line.
			throw new HexFileException("Line too short");
		}

		if ((line.get(0) & 0xFF) != line.size() - 5) {
			// Size value is incorrect.
			throw new HexFileException("Line size is not correct");
		}
	}

	private void validateChecksum(ArrayList<Byte> line) throws HexFileException {
		int checksum;
		checksum = 0;

		// This omits the last byte from the checksum.
		byte previous = (byte) 0;
		for (byte b : line) {
			checksum += (previous & 0xFF);
			previous = b;
		}

		checksum = (((checksum & 0xFF) ^ 0xFF) + 1) & 0xFF;

		if (checksum != (line.get(line.size() - 1) & 0xFF)) {
			// Checksum for this line is incorrect.
			throw new HexFileException("Line checksum is not correct");
		}
	}

	private int readRecord(ArrayList<Byte> line, int baseAddress) throws HexFileException {
		if (line.get(3) == 0x00) {
			// Data record.
			if ((line.get(0) & 0x01) != 0)
				throw new HexFileException("Line length must be even");

			int address = baseAddress + readBigWord(line, 1);
			if ((address & 0x0001) != 0)
				throw new HexFileException("Address must be even");

			address >>= 1; // Convert byte address into word address.\

			for (int index2 = 0; index2 < (line.size() - 5); index2 += 2) {
				short word = readLittleWord(line, index2 + 4);
				words.set(address + index2 / 2, word);
			}
		} else if (line.get(3) == 0x01) {
			// Stop processing at the End Of File Record.
			if (line.get(0) != 0x00)
				throw new HexFileException("Invalid end of file record");
			baseAddress = READ_RECORD_OK; // fake OK
		} else if (line.get(3) == 0x02) {
			// Extended Segment Address Record.
			if (line.get(0) != 0x02)
				throw new HexFileException("Invalid segment address record");
			baseAddress = (readBigWord(line, 4)) << 4;
		} else if (line.get(3) == 0x04) {
			// Extended Linear Address Record.
			if (line.get(0) != 0x02)
				throw new HexFileException("Invalid address record");
			baseAddress = (readBigWord(line, 4)) << 16;
		} else if (line.get(3) != 0x03 && line.get(3) != 0x05) {
			// Invalid record type.
			throw new HexFileException("Invalid record type");
		}
		return baseAddress;
	}

	private static int DIGIT_COLON = 0x10;

	private int examineDigit(int ch) {
		if (ch == ':') {
			return DIGIT_COLON;
		} else if (ch >= '0' && ch <= '9') {
			return ch - '0';
		} else if (ch >= 'A' && ch <= 'F') {
			return ch - 'A' + 10;
		} else if (ch >= 'a' && ch <= 'f') {
			return ch - 'a' + 10;
		} else {
			// Invalid character in hex file.
			return -1;
		}
	}

	public boolean save(String filename, boolean skipOnes) throws IOException {
		OutputStream file;
		try {
			file = Common.openForWrite(filename);
		} catch (IOException e) {
			log.severe("Could not open " + filename + ": " + e.getMessage());
			return false;
		}

		saveRange(file, _programRange, skipOnes);
		if (!_configRange.isEmpty()) {
			if ((_configRange.size()) >= 8) {
				saveRange(file, IntRange.getSize(_configRange.start(), 6), skipOnes);
				// Don't bother saving the device ID word at _configRange.start
				// + 6.
				saveRange(file, IntRange.getPost(_configRange.start() + 7, _configRange.post()), skipOnes);
			} else {
				saveRange(file, _configRange, skipOnes);
			}
		}
		saveRange(file, _dataRange, skipOnes);
		writeString(file, ":00000001FF\n");
		file.close();
		return true;
	}

	private void saveRange(OutputStream file, IntRange range, boolean skipOnes) throws IOException {
		int current = range.start();
		if (skipOnes) {
			while (current < range.post()) {
				while (current < range.post() && isAllOnes(current))
					++current;
				if (current >= range.post())
					break;
				int limit = current + 1;
				while (limit < range.post() && !isAllOnes(limit))
					++limit;
				saveRange(file, IntRange.getPost(current, limit));
				current = limit;
			}
		} else {
			saveRange(file, IntRange.getPost(current, range.post()));
		}
	}

	private void saveRange(OutputStream file, IntRange range) throws IOException {
		int current = range.start();
		int currentSegment = ~0;
		boolean needsSegments = (rangeIsNotShort(_programRange) || rangeIsNotShort(_configRange) || rangeIsNotShort(_dataRange));
		int format;
		if (_format == FORMAT_AUTO && _programBits == 16)
			format = FORMAT_IHX32;
		else
			format = _format;
		if (format == FORMAT_IHX8M)
			needsSegments = false;
		byte[] buffer = new byte[64];
		while (current < range.post()) {
			int byteAddress = current * 2;
			int segment = byteAddress >> 16;
			if (needsSegments && segment != currentSegment) {
				if (segment < 16 && _format != FORMAT_IHX32) {
					// Over 64K boundary: output an Extended Segment Address
					// Record.
					currentSegment = segment;
					segment <<= 12;
					buffer[0] = (byte) 0x02;
					buffer[1] = (byte) 0x00;
					buffer[2] = (byte) 0x00;
					buffer[3] = (byte) 0x02;
					buffer[4] = (byte) (segment >> 8);
					buffer[5] = (byte) segment;
					writeLine(file, buffer, 6);
				} else {
					// Over 1M boundary: output an Extended Linear Address
					// Record.
					currentSegment = segment;
					buffer[0] = (byte) 0x02;
					buffer[1] = (byte) 0x00;
					buffer[2] = (byte) 0x00;
					buffer[3] = (byte) 0x04;
					buffer[4] = (byte) (segment >> 8);
					buffer[5] = (byte) segment;
					writeLine(file, buffer, 6);
				}
			}
			if ((current + 7) < range.post())
				buffer[0] = (byte) 0x10;
			else
				buffer[0] = (byte) ((range.post() - current) * 2);
			buffer[1] = (byte) (byteAddress >> 8);
			buffer[2] = (byte) byteAddress;
			buffer[3] = (byte) 0x00;
			int len = 4;
			while (current < range.post() && len < (4 + 16)) {
				short value = word(current);
				buffer[len++] = (byte) value;
				buffer[len++] = (byte) (value >> 8);
				++current;
			}
			writeLine(file, buffer, len);
		}
	}

	private boolean rangeIsNotShort(IntRange range) {
		return 0x10000 < range.post();
	}

	private static void writeLine(OutputStream file, byte[] buffer, int len) throws IOException {
		int checksum = calculateChecksum(buffer, len);
		writeString(file, ":");
		putBytesAsHex(file, buffer, len);
		putByteAsHex(file, checksum);
		writeString(file, "\n");
	}

	private static void putBytesAsHex(OutputStream file, byte[] buffer, int len) throws IOException {
		writeString(file, Common.toX2(buffer, 0, len));
	}

	private static int calculateChecksum(byte[] buffer, int len) {
		int index;
		int checksum = 0;
		for (index = 0; index < len; ++index)
			checksum += (buffer[index] & 0xFF);
		checksum = (((checksum & 0xFF) ^ 0xFF) + 1) & 0xFF;
		return checksum;
	}

	private static void putByteAsHex(OutputStream file, int z) throws IOException {
		writeString(file, Common.toX2(z));
	}

	private static void writeString(OutputStream s, String data) throws IOException {
		s.write(Common.getBytes(data));
	}

	public void saveCC(String filename, boolean skipOnes) throws IOException {
		OutputStream file = Common.openForWrite(filename);

		for (IntRange extent : words.extents()) {
			saveRange(file, extent, skipOnes);
		}
		writeString(file, ":00000001FF\n");
		file.close();
	}

	public void write(ProgrammerPort port, boolean forceCalibration) throws IOException {
		// Write the contents of program memory.
		count = 0;
		writeProgramSubrange(port, forceCalibration, _programRange, "program memory");

		// Write data memory before config memory in case the configuration
		// word turns on data protection and thus hinders data verification.
		writeSubrange(port, forceCalibration, _dataRange, "data memory");

		// Write the contents of config memory.
		writeSubrange(port, forceCalibration, _configRange, "id words and fuses");

		log.info("done.");
	}

	private void writeProgramSubrange(ProgrammerPort port, boolean forceCalibration, IntRange range, String desc)
			throws IOException {
		if (!range.isEmpty()) {
			log.info("Burning " + desc + ",");
			flush();
			if (forceCalibration || _reservedRange.isEmpty()) {
				// Calibration forced or no reserved words to worry about.
				writeBlock(port, range, forceCalibration);
			} else {
				// Assumes: reserved words are always at the end of program
				// memory.
				writeBlock(port, IntRange.empty(range.start()), forceCalibration);
			}
			reportCount();
		} else {
			log.info("Skipped burning " + desc + ",");
		}
	}

	private void writeSubrange(ProgrammerPort port, boolean forceCalibration, IntRange range, String desc)
			throws IOException {
		if (!range.isEmpty()) {
			log.info("burning " + desc + ",");
			flush();
			writeBlock(port, range, forceCalibration);
			reportCount();
		} else {
			log.info("skipped burning " + desc + ",");
		}
	}

	private void writeBlock(ProgrammerPort port, IntRange range, boolean forceCalibration) throws IOException {
		count += words.writeBlock(port.getBlockWriter(forceCalibration), range);
	}

	public static final class HexFileException extends IOException {

		private static final long serialVersionUID = 1L;

		private HexFileException() {
			super();
		}

		private HexFileException(String message, Throwable cause) {
			super(message, cause);
		}

		private HexFileException(String message) {
			super(message);
		}

		private HexFileException(Throwable cause) {
			super(cause);
		}

	}

}
