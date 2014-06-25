package us.hfgk.ardpicprog;

import java.io.IOException;

import us.hfgk.ardpicprog.pylike.Po;
import us.hfgk.ardpicprog.pylike.PylikeWritable;
import us.hfgk.ardpicprog.pylike.Str;
import us.hfgk.ardpicprog.pylike.Tuple2;

class HexFileSerializer {

	private static boolean hasAddressesOver16Bits(AddressRange range) {
		return 0x10000 < range.post();
	}

	public static void save(HexFile hex, PylikeWritable file, boolean skipOnes) throws IOException {
		DeviceDetails device = hex.getMetadata().getDevice();
		saveRange(hex, file, device.programRange, skipOnes);
		if (!device.configRange.isEmpty()) {
			if (device.configRange.size() >= 8) {
				int start = device.configRange.start();
				saveRange(hex, file, AddressRange.getSize(start, 6), skipOnes);
				// Don't bother saving the device ID word at _configRange.start + 6.
				saveRange(hex, file, AddressRange.getPost(start + 7, device.configRange.post()), skipOnes);
			} else {
				saveRange(hex, file, device.configRange, skipOnes);
			}
		}
		saveRange(hex, file, device.dataRange, skipOnes);
		writeEOFRecord(file);
	}

	public static void saveCC(HexFile hex, PylikeWritable file, boolean skipOnes) throws IOException {
		for (AddressRange extent : hex.extents()) {
			saveRange(hex, file, extent, skipOnes);
		}
		writeEOFRecord(file);
	}

	private static void saveRange(HexFile hex, PylikeWritable file, AddressRange range) throws IOException {
		int current = range.start();
		int rangePost = range.post();

		int currentSegment = ~0;
		boolean needsSegments = needsSegments(hex.getMetadata());

		while (current < rangePost) {
			int byteAddress = current * 2;
			int segment = byteAddress >> 16;

			currentSegment = determineOutputExtendedAddress(hex, file, currentSegment, needsSegments, segment);

			Tuple2<Str, Integer> lineAndCount = generateOutputLine(hex, current, rangePost);
			Str line = lineAndCount._1;
			int wordsUsed = lineAndCount._2;

			writeRecord(file, line);
			
			current += wordsUsed;
		}
	}

	private static boolean needsSegments(HexFileMetadata metadata) {
		DeviceDetails device = metadata.getDevice();
		int format = metadata.getEffectiveFormat();
				
		boolean hasAddressesOver16Bits = hasAddressesOver16Bits(device.programRange) || hasAddressesOver16Bits(device.configRange) || hasAddressesOver16Bits(device.dataRange);
		boolean formatIs8Bit = format == HexFileMetadata.FORMAT_IHX8M;
		
		return hasAddressesOver16Bits & !formatIs8Bit;
	}

	private static Tuple2<Str, Integer> generateOutputLine(HexFile hex, int start, int post) {
		int byteAddress = start * 2;
		
		Str bufstr = Str.val(
				((start + 7) < post) ? (byte) 0x10 : (byte) ((post - start) * 2),
				(byte) (byteAddress >> 8),
				(byte) byteAddress,
				(byte) 0x00);

		int i = 0;
		
		while ((start + i) < post && Po.len(bufstr) < (4 + 16)) {
			short value = hex.word(start + i);
			bufstr = bufstr.pYappend(getLittleWordStr(value));
			++i;
		}
		
		return new Tuple2<Str,Integer>(bufstr, i);
	}

	private static Str getLittleWordStr(short value) {
		byte lo = (byte) value;
		byte hi = (byte) (value >> 8);
		return Str.val(lo, hi);
	}

	private static int determineOutputExtendedAddress(HexFile hex, PylikeWritable file, int currentSegment,
			boolean needsSegments, int segment) throws IOException {
		if (needsSegments && segment != currentSegment) {
			byte recordType;
			int segmentShift;

			if (segment < 16 && hex.getMetadata().getFormat() != HexFileMetadata.FORMAT_IHX32) {
				// Over 64K boundary: output an Extended Segment Address Record.
				recordType = HexFileParser.RECORD_EXTENDED_SEGMENT_ADDRESS;
				segmentShift = 12;
			} else {
				// Over 1M boundary: output an Extended Linear Address Record.
				recordType = HexFileParser.RECORD_EXTENDED_LINEAR_ADDRESS;
				segmentShift = 0;
			}
			currentSegment = segment;
			outputExtensionAddress(hex, file, segment, recordType, segmentShift);
		}
		return currentSegment;
	}

	private static void outputExtensionAddress(HexFile hex, PylikeWritable file, int segment, byte recordType,
			int segmentShift) throws IOException {

		int extSegment = segment << segmentShift;
		byte extSegmentHi = (byte) (extSegment >> 8);
		byte extSegmentLo = (byte) extSegment;
		
		Str line = Str.val((byte) 0x02, (byte) 0x00, (byte) 0x00, recordType, extSegmentHi, extSegmentLo);
		writeRecord(file, line);
	}

	private static void saveRange(HexFile hex, PylikeWritable file, AddressRange range, boolean skipOnes)
			throws IOException {
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
				saveRange(hex, file, AddressRange.getPost(current, limit));
				current = limit;
			}
		} else {
			saveRange(hex, file, AddressRange.getPost(current, post));
		}
	}

	private static final Str EOF_RECORD = Str.val(":00000001FF\n");

	private static void writeEOFRecord(PylikeWritable file) throws IOException {
		file.write(EOF_RECORD);
	}

	private static void writeRecord(PylikeWritable file, Str buffer) throws IOException {
		int checksum = HexFileParser.computeChecksum(buffer);
		file.write(Str.val(":"));
		file.write(Common.toX2(buffer));
		file.write(Common.toX2Str(checksum));
		file.write(Str.val("\n"));
	}

}
