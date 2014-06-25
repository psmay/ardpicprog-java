package us.hfgk.ardpicprog;

import java.io.IOException;
import java.util.logging.Logger;

import us.hfgk.ardpicprog.pylike.Po;
import us.hfgk.ardpicprog.pylike.PylikeReadable;
import us.hfgk.ardpicprog.pylike.Re;
import us.hfgk.ardpicprog.pylike.Str;

class HexFileParser {
	@SuppressWarnings("unused")
	private static final Logger log = Logger.getLogger(HexFileParser.class.getName());

	private static final int LINE_NONDATA_SIZE = 5;
	// 0: Byte count n
	// 1: Address H
	// 2: Address L
	// 3: Record type
	// 4 .. n+4: Data (n bytes)
	// n+5: Checksum

	// Dummy base address to express that reading EOF record was successful
	private static final int BASEADDRESS_DONE = -1;

	// Record types
	public static final byte RECORD_DATA = 0x00;
	public static final byte RECORD_EOF = 0x01;
	public static final byte RECORD_EXTENDED_SEGMENT_ADDRESS = 0x02;
	public static final byte RECORD_START_SEGMENT_ADDRESS = 0x03;
	public static final byte RECORD_EXTENDED_LINEAR_ADDRESS = 0x04;
	public static final byte RECORD_START_LINEAR_ADDRESS = 0x05;

	private static short[] convertDataLineToWords(Str dataLine) {
		Str actualData = dataLine.pYslice(4, -1);
		short[] shortData = new short[Po.len(actualData) / 2];
		for (int wordIndex : Po.xrange(shortData.length)) {
			int byteIndex = wordIndex * 2;
			shortData[wordIndex] = readLittleWord(actualData, byteIndex);
		}
		return shortData;
	}

	private static Str hexDigitsToBytes(Str digitsOnly) throws HexFileException {
		Str result = Str.EMPTY;
		while (Po.len(digitsOnly) >= 2) {
			Str these = digitsOnly.pYslice(0, 2);
			digitsOnly = digitsOnly.pYslice(2);
			try {
				int b = Po.int_(these, 16);
				result = result.pYappend((byte) b);
			} catch (NumberFormatException e) {
				throw new HexFileException("Invalid hex digits '" + these + "'", e);
			}
		}
		if (Po.len(digitsOnly) > 0) {
			throw new HexFileException("Hex file line contained odd number of digits");
		}
		return result;
	}

	private static Str hexFileLineToData(Str textLine) throws HexFileException {
		Str withoutSpaces = Re.sub(Str.val("[ \\t\\r\\n\\x00]+"), Str.EMPTY, textLine);
		if (!withoutSpaces.startswith(Str.val(":")))
			throw new HexFileException("Hex file line must begin with ':'");
		Str digitsOnly = withoutSpaces.pYslice(1);
		Str result = hexDigitsToBytes(digitsOnly);
		return result;
	}

	public static HexFile load(HexFileMetadata details, PylikeReadable file) throws IOException {
		if (details == null)
			throw new IllegalArgumentException();
		ShortList words = Common.getBlankShortList();
		loadIntoShortList(words, file);
		return new HexFile(details, words);
	}

	private static void loadIntoShortList(ShortList words, PylikeReadable file) throws IOException {
		int baseAddress = 0;

		for (Str textLine : file.readlines()) {
			Str data = hexFileLineToData(textLine);
			validateSize(data);
			validateChecksum(data);

			baseAddress = readRecord(words, data, baseAddress);
			if (baseAddress == BASEADDRESS_DONE) {
				return; // ok
			}
		}
	}

	// Read a big-endian word value from a buffer.
	private static short readBigWord(Str bytes, int index) {
		byte a = Po.getitem(bytes, index);
		byte b = Po.getitem(bytes, index + 1);
		return shortFromBytes(a, b);
	}

	private static int readDataRecord(ShortList words, Str line, int baseAddress, byte dataByteCount)
			throws HexFileException {
		// Data record.
		if ((dataByteCount & 1) != 0)
			throw new HexFileException("Line length must be even");

		int address = baseAddress + readBigWord(line, 1);
		if ((address & 1) != 0)
			throw new HexFileException("Address must be even");

		int wordAddress = address >> 1;

		words.set(wordAddress, convertDataLineToWords(line));

		return baseAddress;
	}

	private static int readEOFRecord(byte dataByteCount) throws HexFileException {
		// Stop processing at the End Of File Record.
		if (dataByteCount != 0)
			throw new HexFileException("Invalid end of file record");
		return BASEADDRESS_DONE; // fake OK
	}

	private static int readExtendedLinearAddressRecord(Str line, byte dataByteCount) throws HexFileException {
		// Extended Linear Address Record.
		if (dataByteCount != 2)
			throw new HexFileException("Invalid linear address record");
		return readBigWord(line, 4) << 16;
	}

	private static int readExtendedSegmentAddressRecord(Str line, byte dataByteCount) throws HexFileException {
		// Extended Segment Address Record.
		if (dataByteCount != 2)
			throw new HexFileException("Invalid segment address record");
		return readBigWord(line, 4) << 4;
	}

	// Read a little-endian word value from a buffer.
	private static short readLittleWord(Str bytes, int index) {
		byte a = Po.getitem(bytes, index);
		byte b = Po.getitem(bytes, index + 1);
		return shortFromBytes(b, a);
	}

	private static int readRecord(ShortList words, Str line, int baseAddress) throws HexFileException {
		byte recordType = Po.getitem(line, 3);
		byte dataByteCount = Po.getitem(line, 0);

		switch (recordType) {
		case RECORD_DATA:
			return readDataRecord(words, line, baseAddress, dataByteCount);
		case RECORD_EOF:
			return readEOFRecord(dataByteCount);
		case RECORD_EXTENDED_SEGMENT_ADDRESS:
			return readExtendedSegmentAddressRecord(line, dataByteCount);
		case RECORD_EXTENDED_LINEAR_ADDRESS:
			return readExtendedLinearAddressRecord(line, dataByteCount);
		case RECORD_START_SEGMENT_ADDRESS:
		case RECORD_START_LINEAR_ADDRESS:
			// do nothing
			return baseAddress;
		default:
			throw new HexFileException("Invalid record type");
		}
	}

	private static short shortFromBytes(byte high, byte low) {
		return (short) (((high & 0xFF) << 8) | (low & 0xFF));
	}

	private static void validateChecksum(Str dataLine) throws HexFileException {
		Str allButLastByte = dataLine.pYslice(0, -1);

		byte computedChecksum = computeChecksum(allButLastByte);
		byte readChecksum = (byte) Po.ord(dataLine.pYslice(-1));

		if (computedChecksum != readChecksum) {
			throw new HexFileException("Line checksum is not correct");
		}
	}

	public static byte computeChecksum(Str dataWithoutChecksumByte) {
		int sum = 0;
		for (Str ch : dataWithoutChecksumByte.charsIn()) {
			sum += Po.ord(ch);
		}
		return (byte) -sum;
	}

	private static void validateSize(Str dataLine) throws HexFileException {
		int lineLength = Po.len(dataLine);

		if (lineLength < LINE_NONDATA_SIZE) {
			throw new HexFileException("Not enough bytes to form a valid line");
		}

		int dataByteCount = Po.getitem(dataLine, 0) & 0xFF; // NB python will
															// need ord()

		if (dataByteCount != lineLength - LINE_NONDATA_SIZE) {
			throw new HexFileException("Line size is not correct");
		}
	}

}
