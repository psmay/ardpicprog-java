package us.hfgk.ardpicprog;

import java.io.IOException;
import java.io.OutputStream;

class HexFileSerializer {

	private static int calculateChecksum(byte[] buffer, int len) {
		int index;
		int checksum = 0;
		for (index = 0; index < len; ++index)
			checksum += (buffer[index] & 0xFF);
		checksum = (((checksum & 0xFF) ^ 0xFF) + 1) & 0xFF;
		return checksum;
	}

	private static int determineOutputExtendedAddress(HexFile hex, OutputStream file, int currentSegment,
			boolean needsSegments, byte[] buffer, int segment) throws IOException {
		if (needsSegments && segment != currentSegment) {
			if (segment < 16 && hex.getMetadata().getFormat() != HexFile.FORMAT_IHX32) {
				// Over 64K boundary: output an Extended Segment Address
				// Record.
				currentSegment = outputExtendedAddress(hex, file, buffer, segment, (byte) 0x02, 12);
			} else {
				// Over 1M boundary: output an Extended Linear Address
				// Record.
				currentSegment = outputExtendedAddress(hex, file, buffer, segment, (byte) 0x04, 0);
			}
		}
		return currentSegment;
	}

	private static int outputExtendedAddress(HexFile hex, OutputStream file, byte[] buffer, int segment, byte b3,
			int shift) throws IOException {
		int currentSegment = segment;
		segment <<= shift;
		buffer[0] = (byte) 0x02;
		buffer[1] = (byte) 0x00;
		buffer[2] = (byte) 0x00;
		buffer[3] = b3;
		buffer[4] = (byte) (segment >> 8);
		buffer[5] = (byte) segment;
		writeLine(file, buffer, 6);
		return currentSegment;
	}

	private static void putByteAsHex(OutputStream file, int z) throws IOException {
		writeString(file, Common.toX2(z));
	}

	private static void putBytesAsHex(OutputStream file, byte[] buffer, int len) throws IOException {
		writeString(file, Common.toX2(buffer, 0, len));
	}

	private static boolean rangeIsNotShort(IntRange range) {
		return 0x10000 < range.post();
	}

	public static void save(HexFile hex, OutputStream file, boolean skipOnes) throws IOException {
		DeviceDetails device = hex.getMetadata().getDevice();
		saveRange(hex, file, device.programRange, skipOnes);
		if (!device.configRange.isEmpty()) {
			if ((device.configRange.size()) >= 8) {
				saveRange(hex, file, IntRange.getSize(device.configRange.start(), 6), skipOnes);
				// Don't bother saving the device ID word at _configRange.start
				// + 6.
				saveRange(hex, file, IntRange.getPost(device.configRange.start() + 7, device.configRange.post()),
						skipOnes);
			} else {
				saveRange(hex, file, device.configRange, skipOnes);
			}
		}
		saveRange(hex, file, device.dataRange, skipOnes);
		writeEOFRecord(file);
	}

	public static void saveCC(HexFile hex, OutputStream file, boolean skipOnes) throws IOException {
		for (IntRange extent : hex.extents()) {
			saveRange(hex, file, extent, skipOnes);
		}
		writeEOFRecord(file);
	}

	private static void saveRange(HexFile hex, OutputStream file, IntRange range) throws IOException {
		int current = range.start();
		int currentSegment = ~0;
		DeviceDetails device = hex.getMetadata().getDevice();
		boolean needsSegments = (rangeIsNotShort(device.programRange) || rangeIsNotShort(device.configRange) || rangeIsNotShort(device.dataRange));
		int formatz = hex.getMetadata().getFormat();
		int format = (formatz == HexFile.FORMAT_AUTO && device.programBits == 16) ? HexFile.FORMAT_IHX32 : formatz;
		if (format == HexFile.FORMAT_IHX8M)
			needsSegments = false;
		byte[] buffer = new byte[64];

		while (current < range.post()) {
			int byteAddress = current * 2;
			int segment = byteAddress >> 16;
			currentSegment = determineOutputExtendedAddress(hex, file, currentSegment, needsSegments, buffer, segment);

			buffer[0] = ((current + 7) < range.post()) ? (byte) 0x10 : (byte) ((range.post() - current) * 2);
			buffer[1] = (byte) (byteAddress >> 8);
			buffer[2] = (byte) byteAddress;
			buffer[3] = (byte) 0x00;
			int len = 4;
			while (current < range.post() && len < (4 + 16)) {
				short value = hex.word(current);
				buffer[len++] = (byte) value;
				buffer[len++] = (byte) (value >> 8);
				++current;
			}
			writeLine(file, buffer, len);
		}
	}

	private static void saveRange(HexFile hex, OutputStream file, IntRange range, boolean skipOnes) throws IOException {
		int current = range.start();
		final int post = range.post();
		if (skipOnes) {
			while (current < post) {
				while (current < post && hex.isAllOnes(current))
					++current;
				if (current >= post)
					break;
				int limit = current + 1;
				while (limit < post && !hex.isAllOnes(limit))
					++limit;
				saveRange(hex, file, IntRange.getPost(current, limit));
				current = limit;
			}
		} else {
			saveRange(hex, file, IntRange.getPost(current, post));
		}
	}

	private static void writeEOFRecord(OutputStream file) throws IOException {
		writeString(file, ":00000001FF\n");
	}

	private static void writeLine(OutputStream file, byte[] buffer, int len) throws IOException {
		int checksum = calculateChecksum(buffer, len);
		writeString(file, ":");
		putBytesAsHex(file, buffer, len);
		putByteAsHex(file, checksum);
		writeString(file, "\n");
	}

	private static void writeString(OutputStream s, String data) throws IOException {
		s.write(Common.getBytes(data));
	}

}
