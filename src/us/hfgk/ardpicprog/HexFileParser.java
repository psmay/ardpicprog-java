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
	
	// Record types
	private static final int RECORD_DATA = 0x00;
	private static final int RECORD_EOF = 0x01;
	private static final int RECORD_EXTENDED_SEGMENT_ADDRESS = 0x02;
	private static final int RECORD_START_SEGMENT_ADDRESS = 0x03;
	private static final int RECORD_EXTENDED_LINEAR_ADDRESS = 0x04;
	private static final int RECORD_START_LINEAR_ADDRESS = 0x05;

	// Dummy base address to express that reading record was successful
	private static final int READ_EOF_RECORD_OK = 0x7FFFFFFF;

	private static void copyLineToWords(ShortList words, Str line, int wordAddress) {
		for (int byteIndex : Po.xrange(0, Po.len(line) - LINE_NONDATA_SIZE, 2)) {
			int wordIndex = byteIndex >> 1;
			words.set(wordAddress + wordIndex, readLittleWord(line, byteIndex + 4));
		}
	}
	
	public static HexFile load(HexFileMetadata details, PylikeReadable file) throws IOException {
		if (details == null)
			throw new IllegalArgumentException();
		ShortList words = Common.getBlankShortList();
		loadIntoShortList(words, file);
		return new HexFile(details, words);
	}
	
	private static Str hexFileLineToData(Str textLine) throws HexFileException {
		Str withoutSpaces = Re.sub(Str.val("[ \\t\\r\\n\\x00]+"), Str.EMPTY, textLine);
		if(!withoutSpaces.startswith(Str.val(":")))
			throw new HexFileException("Hex file line must begin with ':'");
		Str digitsOnly = withoutSpaces.pYslice(1);
		Str result = hexDigitsToBytes(digitsOnly);
		return result;
	}

	private static Str hexDigitsToBytes(Str digitsOnly) throws HexFileException {
		Str result = Str.EMPTY;
		while(Po.len(digitsOnly) >= 2) {
			Str these = digitsOnly.pYslice(0, 2);
			digitsOnly = digitsOnly.pYslice(2);
			try {
				int b = Po.int_(these, 16);
				result = result.pYappend((byte)b);
			}
			catch(NumberFormatException e) {
				throw new HexFileException("Invalid hex digits '" + these + "'", e);
			}
		}
		if(Po.len(digitsOnly) > 0) {
			throw new HexFileException("Hex file line contained odd number of digits");
		}
		return result;
	}
	
	private static void loadIntoShortList(ShortList words, PylikeReadable file) throws IOException {
		int baseAddress = 0;

		for(Str textLine : file.readlines()) {
			Str data = hexFileLineToData(textLine);
			validateSize(data);
			validateChecksum(data);
			
			baseAddress = readRecord(words, data, baseAddress);
			if (baseAddress == READ_EOF_RECORD_OK) {
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

	private static int readExtendedLinearAddressRecord(Str line, byte dataByteCount) throws HexFileException {
		// Extended Linear Address Record.
		if (dataByteCount != 0x02)
			throw new HexFileException("Invalid address record");
		return (readBigWord(line, 4)) << 16;
	}

	private static int readExtendedSegmentAddressRecord(Str line, byte dataByteCount) throws HexFileException {
		// Extended Segment Address Record.
		if (dataByteCount != 0x02)
			throw new HexFileException("Invalid segment address record");
		return (readBigWord(line, 4)) << 4;
	}

	private static int readEOFRecord(byte dataByteCount) throws HexFileException {
		// Stop processing at the End Of File Record.
		if (dataByteCount != 0x00)
			throw new HexFileException("Invalid end of file record");
		return READ_EOF_RECORD_OK; // fake OK
	}

	private static int readDataRecord(ShortList words, Str line, int baseAddress, byte dataByteCount)
			throws HexFileException {
		// Data record.
		if ((dataByteCount & 0x01) != 0)
			throw new HexFileException("Line length must be even");

		int address = baseAddress + readBigWord(line, 1);
		if ((address & 0x0001) != 0)
			throw new HexFileException("Address must be even");

		copyLineToWords(words, line, address >> 1); // pass word address
		return baseAddress;
	}

	private static short shortFromBytes(byte high, byte low) {
		return (short) (((high & 0xFF) << 8) | (low & 0xFF));
	}

	private static void validateChecksum(Str dataLine) throws HexFileException {
		int checksum = 0;

		// This omits the last byte from the checksum.
		int last = 0;
		for (Str b : dataLine.charsIn()) { // Python: for b in line
			checksum += (last & 0xFF);
			last = Po.ord(b);
		}

		checksum = (((checksum & 0xFF) ^ 0xFF) + 1) & 0xFF;

		if (checksum != (last & 0xFF)) {
			throw new HexFileException("Line checksum is not correct");
		}
	}

	private static void validateSize(Str dataLine) throws HexFileException {
		int lineLength = Po.len(dataLine);

		if (lineLength < LINE_NONDATA_SIZE) {
			throw new HexFileException("Not enough bytes to form a valid line");
		}

		int dataByteCount = Po.getitem(dataLine, 0) & 0xFF; // NB python will need ord()

		if (dataByteCount != lineLength - LINE_NONDATA_SIZE) {
			throw new HexFileException("Line size is not correct");
		}
	}
}
