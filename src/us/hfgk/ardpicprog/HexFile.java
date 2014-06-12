package us.hfgk.ardpicprog;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.logging.Logger;

public class HexFile {
	private static final int READ_RECORD_OK = 0x7FFFFFFF;

	private static final Logger log = Logger.getLogger(HexFile.class.getName());

	public static final int FORMAT_AUTO = -1;
	public static final int FORMAT_IHX8M = 0;
	public static final int FORMAT_IHX16 = 1;
	public static final int FORMAT_IHX32 = 2;

	private static class Block {
		int address;
		short[] data = new short[0];

		private void resizeLeftAligned(int size) {
			data = Arrays.copyOf(data, size);
		}

		private void resizeRightAligned(int newSize) {
			data = rightCopyOf(data, newSize);
		}

		private static short[] rightCopyOf(short[] src, final int newSize) {
			short[] dst = new short[newSize];

			final int currentSize = src.length;

			final int dstEndIndex = newSize;

			// If the copy is right-aligned, this is the ideal dst index of the
			// first src element.
			int dstIndexOfSrc0 = newSize - currentSize;

			// We can't copy negative indices, so truncate on left if necessary.
			int dstStartIndex = (dstIndexOfSrc0 < 0) ? 0 : dstIndexOfSrc0;

			// If truncation happened on dst, do it on src also.
			int srcStartIndex = dstStartIndex - dstIndexOfSrc0;

			// Now we have the number of elements that will actually be copied.
			int copyLength = dstEndIndex - dstStartIndex;

			if (copyLength > 0) {
				System.arraycopy(src, srcStartIndex, dst, dstStartIndex, copyLength);
			}

			// We would do zero-fill for any remaining elements on the left, but
			// Java did that for us when the array was created.

			return dst;
		}

		public void add(short word) {
			resizeLeftAligned(data.length + 1);
			data[data.length - 1] = word;
		}

		public void prepend(short word) {
			resizeRightAligned(data.length + 1);
			data[0] = word;
		}

		public void ensureAtLeast(int newSize) {
			if (newSize > data.length) {
				resizeLeftAligned(newSize);
			}
		}

	};

	private static class IntPair {
		final int start;
		final int end;

		private IntPair(int start, int end) {
			this.start = start;
			if (end < start)
				end = start - 1;
			this.end = end;
		}

		private static IntPair get(int start, int end) {
			return new IntPair(start, end);
		}

		private static IntPair empty(int start) {
			return get(start, start - 1);
		}
	}

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
		return _programRange.end - _programRange.start + 1;
	}

	public int dataSizeBytes() {
		return (_dataRange.end - _dataRange.start + 1) * dataBits() / 8;
	}

	private String _deviceName;
	private IntPair _programRange = IntPair.get(0, 0x07FF);
	private IntPair _configRange = IntPair.get(0x2000, 0x2007);
	private IntPair _dataRange = IntPair.get(0x2100, 0x217F);
	private IntPair _reservedRange = IntPair.get(0x0800, 0x07FF);
	private int _programBits = 14;
	private int _dataBits = 8;
	private int _format = FORMAT_AUTO;
	private ArrayList<Block> blocks = new ArrayList<Block>();
	private int count = 0;

	private static String fetchMap(Map<String, String> details, String key, String defValue) {
		String value = details.get(key);
		return (value == null) ? defValue : value;
	}

	static IntPair parseRange(String value) throws HexFileException {
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
		return IntPair.get(start, end);
	}

	public void setDeviceDetails(Map<String, String> details) throws HexFileException {
		String value = details.get("DeviceName");
		_deviceName = (value != null) ? value : "";
		_programRange = parseRangeUnlessEmpty(details.get("ProgramRange"), 0x0001);
		_programBits = Common.parseInt(fetchMap(details, "ProgramBits", "14"), 0);
		_configRange = parseRangeUnlessEmpty(details.get("ConfigRange"), 0x2000);
		_dataRange = parseRangeUnlessEmpty(details.get("DataRange"), 0x2100);
		_dataBits = Common.parseInt(fetchMap(details, "DataBits", "8"), 0);
		_reservedRange = parseRangeUnlessEmpty(details.get("ReservedRange"), _programRange.end + 1);

		if (_programBits < 1)
			throw new HexFileException("Invalid program word width " + _programBits);
		if (_dataBits < 1)
			throw new HexFileException("Invalid data word width " + _programBits);
	}

	private IntPair parseRangeUnlessEmpty(String value, int emptyStartAddress) throws HexFileException {
		return Common.stringEmpty(value) ? IntPair.empty(emptyStartAddress) : parseRange(value);
	}

	private short word(int address) {
		// ArrayList<HexFileBlock>::_iterator it;
		for (Block it : blocks) {
			// for (it = blocks.begin(); it != blocks.end(); ++it) {
			if (address >= it.address && address < (it.address + it.data.length)) {
				return it.data[address - it.address];
			}
		}
		return fullWord(address);
	}

	private void setWord(int address, short word) {
		int nextIndex = 0;

		for (Block it : blocks) {
			int index = nextIndex;
			++nextIndex;

			Block block = it;
			if (address < block.address) {
				if (address == (block.address - 1)) {
					// Prepend to the existing block.
					block.address--;
					block.prepend(word);
				} else {
					// Create a new block before this one.
					Block newBlock = new Block();
					newBlock.address = address;
					newBlock.add(word);
					blocks.add(index, newBlock);
				}
				return;
			} else if (address < (it.address + it.data.length)) {
				// Update a word in an existing block.
				block.data[address - block.address] = word;
				return;
			} else if (address == (it.address + it.data.length)) {
				// Can we extend the current block without hitting the next
				// block?
				if (index < (blocks.size() - 1)) {
					Block next = blocks.get(index + 1);
					if (address < next.address) {
						block.add(word);
						return;
					}
				} else {
					block.add(word);
					return;
				}
			}
		}
		Block block = new Block();
		block.address = address;
		block.add(word);
		blocks.add(block);
	}

	private short fullWord(int address) {
		if (address >= _dataRange.start && address <= _dataRange.end)
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
		if (_reservedRange.start > _reservedRange.end)
			return true; // No reserved words, so force is trivially ok.
		for (int address = _reservedRange.start; address <= _reservedRange.end; ++address) {
			if (!isAllOnes(address))
				return true;
		}
		return false;
	}

	private void readPart(ProgrammerPort port, String areaDesc, IntPair range) throws IOException {
		if (range.start <= range.end) {
			log.info("Reading " + areaDesc + ",");
			readBlock(port, range.start, range.end);
		} else {
			log.info("Skipped reading " + areaDesc + ",");
		}
	}

	private boolean blankCheckPart(ProgrammerPort port, String areaDesc, IntPair range) throws IOException {
		if (range.start <= range.end) {
			log.info("Blank checking " + areaDesc + ",");
			if (blankCheckBlock(port, range.start, range.end)) {
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
		blocks.clear();

		readPart(port, "program memory", _programRange);
		readPart(port, "data memory", _dataRange);
		readPart(port, "id words and fuses", _configRange);

		log.info("done.");
	}

	private void readBlock(ProgrammerPort port, int start, int end) throws IOException {
		Block block = new Block();
		block.address = start;
		block.ensureAtLeast(end - start + 1);
		port.readData(start, end, block.data, 0);
		// ArrayList<HexFileBlock>::iterator it;
		// for (it = blocks.begin(); it != blocks.end(); ++it) {
		int nextIndex = 0;
		for (Block it : blocks) {
			int index = nextIndex++;
			if (start <= it.address) {
				blocks.add(index, block);
				return;
			}
		}
		blocks.add(block);
	}

	private boolean blankCheckBlock(ProgrammerPort port, int start, int end) throws IOException {
		Block block = new Block();
		block.address = start;
		block.ensureAtLeast(end - start + 1);
		port.readData(start, end, block.data, 0);

		int i = start;
		for (short word : block.data) {
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
				setWord(address + index2 / 2, word);
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

		saveRange(file, _programRange.start, _programRange.end, skipOnes);
		if (_configRange.start <= _configRange.end) {
			if ((_configRange.end - _configRange.start + 1) >= 8) {
				saveRange(file, _configRange.start, _configRange.start + 5, skipOnes);
				// Don't bother saving the device ID word at _configRange.start
				// + 6.
				saveRange(file, _configRange.start + 7, _configRange.end, skipOnes);
			} else {
				saveRange(file, _configRange.start, _configRange.end, skipOnes);
			}
		}
		saveRange(file, _dataRange.start, _dataRange.end, skipOnes);
		writeString(file, ":00000001FF\n");
		file.close();
		return true;
	}

	private void saveRange(OutputStream file, int start, int end, boolean skipOnes) throws IOException {
		if (skipOnes) {
			while (start <= end) {
				while (start <= end && isAllOnes(start))
					++start;
				if (start > end)
					break;
				int limit = start + 1;
				while (limit <= end && !isAllOnes(limit))
					++limit;
				saveRange(file, start, limit - 1);
				start = limit;
			}
		} else {
			saveRange(file, start, end);
		}
	}

	private void saveRange(OutputStream file, int start, int end) throws IOException {
		int current = start;
		int currentSegment = ~0;
		boolean needsSegments = (_programRange.end >= 0x10000 || _configRange.end >= 0x10000 || _dataRange.end >= 0x10000);
		int format;
		if (_format == FORMAT_AUTO && _programBits == 16)
			format = FORMAT_IHX32;
		else
			format = _format;
		if (format == FORMAT_IHX8M)
			needsSegments = false;
		byte[] buffer = new byte[64];
		while (current <= end) {
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
			if ((current + 7) <= end)
				buffer[0] = (byte) 0x10;
			else
				buffer[0] = (byte) ((end - current + 1) * 2);
			buffer[1] = (byte) (byteAddress >> 8);
			buffer[2] = (byte) byteAddress;
			buffer[3] = (byte) 0x00;
			int len = 4;
			while (current <= end && len < (4 + 16)) {
				short value = word(current);
				buffer[len++] = (byte) value;
				buffer[len++] = (byte) (value >> 8);
				++current;
			}
			writeLine(file, buffer, len);
		}
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

		for (Block it : blocks) {
			int start = it.address;
			int end = start + it.data.length - 1;
			saveRange(file, start, end, skipOnes);
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

	private void writeProgramSubrange(ProgrammerPort port, boolean forceCalibration, IntPair range, String desc)
			throws IOException {
		if (range.start <= range.end) {
			log.info("Burning " + desc + ",");
			flush();
			if (forceCalibration || _reservedRange.start > _reservedRange.end) {
				// Calibration forced or no reserved words to worry about.
				writeBlock(port, range.start, range.end, forceCalibration);
			} else {
				// Assumes: reserved words are always at the end of program
				// memory.
				writeBlock(port, range.start, range.start - 1, forceCalibration);
			}
			reportCount();
		} else {
			log.info("Skipped burning " + desc + ",");
		}
	}

	private void writeSubrange(ProgrammerPort port, boolean forceCalibration, IntPair range, String desc)
			throws IOException {
		if (range.start <= range.end) {
			log.info("burning " + desc + ",");
			flush();
			writeBlock(port, range.start, range.end, forceCalibration);
			reportCount();
		} else {
			log.info("skipped burning " + desc + ",");
		}
	}

	private void writeBlock(ProgrammerPort port, int start, int end, boolean forceCalibration) throws IOException {
		for (Block it : blocks) {
			int blockStart = it.address;
			int blockEnd = blockStart + it.data.length - 1;
			if (start <= blockEnd && end >= blockStart) {
				int offset = 0;

				int overlapStart;
				int overlapEnd;
				if (start > blockStart) {
					offset += (start - blockStart);
					overlapStart = start;
				} else {
					overlapStart = blockStart;
				}
				if (end < blockEnd)
					overlapEnd = end;
				else
					overlapEnd = blockEnd;
				port.writeData(overlapStart, overlapEnd, it.data, offset, forceCalibration);
				count += overlapEnd - overlapStart + 1;
			}
		}
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
