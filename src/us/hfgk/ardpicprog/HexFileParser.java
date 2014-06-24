package us.hfgk.ardpicprog;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import us.hfgk.ardpicprog.pylike.Po;
import us.hfgk.ardpicprog.pylike.PylikeReadable;
import us.hfgk.ardpicprog.pylike.Str;

class HexFileParser {
	// Record types
	private static final int RECORD_DATA = 0x00;
	private static final int RECORD_EOF = 0x01;
	private static final int RECORD_EXTENDED_SEGMENT_ADDRESS = 0x02;
	private static final int RECORD_START_SEGMENT_ADDRESS = 0x03;
	private static final int RECORD_EXTENDED_LINEAR_ADDRESS = 0x04;
	private static final int RECORD_START_LINEAR_ADDRESS = 0x05;

	// Dummy base address to express that reading record was successful
	private static final int READ_RECORD_OK = 0x7FFFFFFF;

	private static int DIGIT_COLON = 0x10;

	private static void copyLineToWords(ShortList words, byte[] line, int wordAddress) {
		for (int byteIndex : Po.xrange(0, line.length - 5, 2)) {
			int wordIndex = byteIndex >> 1;
			words.set(wordAddress + wordIndex, readLittleWord(line, byteIndex + 4));
		}
	}

	private static int examineDigit(Str s) {
		if (s.equals(Str.val(":")))
			return DIGIT_COLON;
		try {
			return Po.int_(s, 16);
		} catch (NumberFormatException e) {
			return -1;
		}
	}

	public static HexFile load(HexFileMetadata details, PylikeReadable file) throws IOException {
		if (details == null)
			throw new IllegalArgumentException();
		ShortList words = Common.getBlankShortList();
		loadIntoShortList(words, file);
		return new HexFile(details, words);
	}

	private static void loadIntoShortList(ShortList words, PylikeReadable file) throws IOException, HexFileException {
		boolean startLine = true;
		ByteArrayOutputStream line = new ByteArrayOutputStream();
		int digit;
		int nibble = -1;

		int baseAddress = 0;

		Str chstr;

		while (!(chstr = file.read(1)).equals(Str.EMPTY)) {
			// ch = 0xFF & Po.getitem(chstr, 0);

			if (chstr.equals(Str.val(" ")) || chstr.equals(Str.val("\t")))
				continue;

			if (chstr.equals(Str.val("\r")) || chstr.equals(Str.val("\n"))) {
				if (nibble != -1) {
					// Half a byte at the end of the line.
					throw new HexFileException("Half byte at end of line");
				}

				if (!startLine) {
					byte[] bytes = line.toByteArray();
					validateSize(bytes);
					validateChecksum(bytes);

					baseAddress = readRecord(words, bytes, baseAddress);
					if (baseAddress == READ_RECORD_OK) {
						return; // ok
					}
				}
				line.reset();
				startLine = true;
				continue;
			}

			digit = examineDigit(chstr);

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
				throw new HexFileException("Invalid hex character '" + chstr + "'");
			} else {

				if (startLine) {
					// Hex digit at the start of a line.
					throw new HexFileException("Hex digit must not appear at the beginning of a line");
				}
				if (nibble == -1) {
					nibble = digit;
				} else {
					line.write((byte) ((nibble << 4) | digit));
					nibble = -1;
				}
			}
		}

		throw new HexFileException("Unexpected end of input");
	}

	// Read a big-endian word value from a buffer.
	private static short readBigWord(byte[] bytes, int index) {
		return shortFromBytes(bytes[index], bytes[index + 1]);
	}

	// Read a little-endian word value from a buffer.
	private static short readLittleWord(byte[] bytes, int index) {
		return shortFromBytes(bytes[index + 1], bytes[index]);
	}

	private static int readRecord(ShortList words, byte[] line, int baseAddress) throws HexFileException {

		byte byte3 = line[3];
		byte byte0 = line[0];

		switch (byte3) {
		case RECORD_DATA:
			// Data record.
			if ((byte0 & 0x01) != 0)
				throw new HexFileException("Line length must be even");

			int address = baseAddress + readBigWord(line, 1);
			if ((address & 0x0001) != 0)
				throw new HexFileException("Address must be even");

			copyLineToWords(words, line, address >> 1); // pass word address
			return baseAddress;

		case RECORD_EOF:
			// Stop processing at the End Of File Record.
			if (byte0 != 0x00)
				throw new HexFileException("Invalid end of file record");
			return READ_RECORD_OK; // fake OK

		case RECORD_EXTENDED_SEGMENT_ADDRESS:
			// Extended Segment Address Record.
			if (byte0 != 0x02)
				throw new HexFileException("Invalid segment address record");
			return (readBigWord(line, 4)) << 4;

		case RECORD_EXTENDED_LINEAR_ADDRESS:
			// Extended Linear Address Record.
			if (byte0 != 0x02)
				throw new HexFileException("Invalid address record");
			return (readBigWord(line, 4)) << 16;

		case RECORD_START_SEGMENT_ADDRESS:
		case RECORD_START_LINEAR_ADDRESS:
			// do nothing
			return baseAddress;

		default:
			// Invalid record type.
			throw new HexFileException("Invalid record type");
		}
	}

	private static short shortFromBytes(byte high, byte low) {
		return (short) (((high & 0xFF) << 8) | (low & 0xFF));
	}

	private static void validateChecksum(byte[] line) throws HexFileException {
		int checksum;
		checksum = 0;

		// This omits the last byte from the checksum.
		byte previous = (byte) 0;
		for (byte b : line) {
			checksum += (previous & 0xFF);
			previous = b;
		}

		checksum = (((checksum & 0xFF) ^ 0xFF) + 1) & 0xFF;

		if (checksum != (line[line.length - 1] & 0xFF)) {
			// Checksum for this line is incorrect.
			throw new HexFileException("Line checksum is not correct");
		}
	}

	private static void validateSize(byte[] line) throws HexFileException {
		if (line.length < 5) {
			// Not enough bytes to form a valid line.
			throw new HexFileException("Line too short");
		}

		if ((line[0] & 0xFF) != line.length - 5) {
			// Size value is incorrect.
			throw new HexFileException("Line size is not correct");
		}
	}
}
