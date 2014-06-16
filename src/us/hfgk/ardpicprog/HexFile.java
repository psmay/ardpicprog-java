package us.hfgk.ardpicprog;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class HexFile {
	private static final Logger log = Logger.getLogger(HexFile.class.getName());

	public HexFile(Map<String, String> details, int format, ShortList words) throws HexFileException {
		this._format = format;
		if (details == null) {
			details = Collections.<String, String> emptyMap();
		}
		this.device = new DeviceDetails(details);

		if (words == null) {
			words = new SparseShortList();
		}
		this.words = words;
	}

	public HexFile(Map<String, String> details, int format) throws HexFileException {
		this(details, format, null);
	}

	// Hex file formats
	public static final int FORMAT_AUTO = -1;
	public static final int FORMAT_IHX8M = 0;
	public static final int FORMAT_IHX16 = 1;
	public static final int FORMAT_IHX32 = 2;

	public String deviceName() {
		return device.deviceName;
	}

	private int dataBits() {
		return device.dataBits;
	}

	int programSizeWords() {
		return device.programRange.size();
	}

	public int dataSizeBytes() {
		return device.dataRange.size() * dataBits() / 8;
	}

	private static class DeviceDetails {
		private final String deviceName;
		private final IntRange programRange;
		private final IntRange configRange;
		private final IntRange dataRange;
		private final IntRange reservedRange;
		private final int programBits;
		private final int dataBits;

		DeviceDetails(Map<String, String> details) throws HexFileException {
			deviceName = ensureDefined(details.get("DeviceName"));
			programRange = parseRangeUnlessEmpty(details.get("ProgramRange"), 0x0001);
			programBits = Common.parseInt(fetchMap(details, "ProgramBits", "14"), 0);
			configRange = parseRangeUnlessEmpty(details.get("ConfigRange"), 0x2000);
			dataRange = parseRangeUnlessEmpty(details.get("DataRange"), 0x2100);
			dataBits = Common.parseInt(fetchMap(details, "DataBits", "8"), 0);
			reservedRange = parseRangeUnlessEmpty(details.get("ReservedRange"), programRange.post());

			if (programBits < 1)
				throw new HexFileException("Invalid program word width " + programBits);
			if (dataBits < 1)
				throw new HexFileException("Invalid data word width " + dataBits);
		}

		private static String ensureDefined(String value) {
			return (value != null) ? value : "";
		}

		private static IntRange parseRangeUnlessEmpty(String value, int emptyStartAddress) throws HexFileException {
			return Common.stringEmpty(value) ? IntRange.empty(emptyStartAddress) : parseRange(value);
		}

		private static IntRange parseRange(String value) throws HexFileException {
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
	}

	private int _format = FORMAT_AUTO;

	private DeviceDetails device;
	private ShortList words;

	private static String fetchMap(Map<String, String> details, String key, String defValue) {
		String value = details.get(key);
		return (value == null) ? defValue : value;
	}

	private short word(int address) {
		return words.get(address, fullWord(address));
	}

	private short fullWord(int address) {
		int numberOfBits = device.dataRange.containsValue(address) ? device.dataBits : device.programBits;
		return (short) ((1 << numberOfBits) - 1);
	}

	private boolean wouldBeAllOnes(int address, short wordValue) {
		return wordValue == fullWord(address);
	}

	private boolean isAllOnes(int address) {
		return wouldBeAllOnes(address, word(address));
	}

	public boolean canForceCalibration() {
		if (device.reservedRange.isEmpty())
			return true; // No reserved words, so force is trivially ok.
		int post = device.reservedRange.post();
		for (int address = device.reservedRange.start(); address < post; ++address) {
			if (!isAllOnes(address))
				return true;
		}
		return false;
	}

	private List<Tuple2<String, IntRange>> getAreas() {
		List<Tuple2<String, IntRange>> ls = new ArrayList<Tuple2<String, IntRange>>();

		ls.add(new Tuple2<String, IntRange>("program memory", device.programRange));
		ls.add(new Tuple2<String, IntRange>("data memory", device.dataRange));
		ls.add(new Tuple2<String, IntRange>("id words and fuses", device.configRange));

		return Collections.unmodifiableList(ls);
	}

	public boolean blankCheckRead(ProgrammerPort port) throws IOException {
		for (Tuple2<String, IntRange> rd : getAreas()) {
			if (!blankCheckArea(port, rd))
				return false;
		}
		return true;
	}

	private boolean blankCheckArea(ProgrammerPort port, Tuple2<String, IntRange> rd) throws IOException {
		String areaDesc = rd._1;
		IntRange range = rd._2;
		if (!range.isEmpty()) {
			log.info("Blank checking " + areaDesc + ",");
			if (blankCheckFrom(port.getShortSource(), range)) {
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

	public void readFrom(ProgrammerPort port) throws IOException {
		words.clear();

		for (Tuple2<String, IntRange> rd : getAreas()) {
			readAreaFrom(port, rd);
		}

		log.info("done.");
	}

	private void readAreaFrom(ProgrammerPort port, Tuple2<String, IntRange> rd) throws IOException {
		String areaDesc = rd._1;
		IntRange range = rd._2;
		if (!range.isEmpty()) {
			log.info("Reading " + areaDesc + ",");
			readFrom(port.getShortSource(), range);
		} else {
			log.info("Skipped reading " + areaDesc + ",");
		}
	}

	private void readFrom(ShortSource source, IntRange range) throws IOException {
		words.readFrom(source, range);
	}

	private boolean blankCheckFrom(ShortSource source, IntRange range) throws IOException {
		final short[] buf = new short[range.size()];

		source.readTo(range, buf, 0);

		int i = range.start();
		for (short word : buf) {
			if (!wouldBeAllOnes(i, word)) {
				return false;
			}
			++i;
		}

		return true;
	}

	private void reportCount(int count) {
		log.info((count == 1) ? " 1 location," : " " + count + " locations,");
	}

	public void save(OutputStream file, boolean skipOnes) throws IOException {
		saveRange(file, device.programRange, skipOnes);
		if (!device.configRange.isEmpty()) {
			if ((device.configRange.size()) >= 8) {
				saveRange(file, IntRange.getSize(device.configRange.start(), 6), skipOnes);
				// Don't bother saving the device ID word at _configRange.start
				// + 6.
				saveRange(file, IntRange.getPost(device.configRange.start() + 7, device.configRange.post()), skipOnes);
			} else {
				saveRange(file, device.configRange, skipOnes);
			}
		}
		saveRange(file, device.dataRange, skipOnes);
		writeString(file, ":00000001FF\n");
	}

	private void saveRange(OutputStream file, IntRange range, boolean skipOnes) throws IOException {
		int current = range.start();
		final int post = range.post();
		if (skipOnes) {
			while (current < post) {
				while (current < post && isAllOnes(current))
					++current;
				if (current >= post)
					break;
				int limit = current + 1;
				while (limit < post && !isAllOnes(limit))
					++limit;
				saveRange(file, IntRange.getPost(current, limit));
				current = limit;
			}
		} else {
			saveRange(file, IntRange.getPost(current, post));
		}
	}

	private void saveRange(OutputStream file, IntRange range) throws IOException {
		int current = range.start();
		int currentSegment = ~0;
		boolean needsSegments = (rangeIsNotShort(device.programRange) || rangeIsNotShort(device.configRange) || rangeIsNotShort(device.dataRange));
		int format = (_format == FORMAT_AUTO && device.programBits == 16) ? FORMAT_IHX32 : _format;
		if (format == FORMAT_IHX8M)
			needsSegments = false;
		byte[] buffer = new byte[64];

		while (current < range.post()) {
			int byteAddress = current * 2;
			int segment = byteAddress >> 16;
			currentSegment = determineOutputExtendedAddress(file, currentSegment, needsSegments, buffer, segment);

			buffer[0] = ((current + 7) < range.post()) ? (byte) 0x10 : (byte) ((range.post() - current) * 2);
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

	private int determineOutputExtendedAddress(OutputStream file, int currentSegment, boolean needsSegments,
			byte[] buffer, int segment) throws IOException {
		if (needsSegments && segment != currentSegment) {
			if (segment < 16 && _format != FORMAT_IHX32) {
				// Over 64K boundary: output an Extended Segment Address
				// Record.
				currentSegment = outputExtendedAddress(file, buffer, segment, (byte) 0x02, 12);
			} else {
				// Over 1M boundary: output an Extended Linear Address
				// Record.
				currentSegment = outputExtendedAddress(file, buffer, segment, (byte) 0x04, 0);
			}
		}
		return currentSegment;
	}

	private int outputExtendedAddress(OutputStream file, byte[] buffer, int segment, byte b3, int shift)
			throws IOException {
		int currentSegment = segment;
		segment <<= shift;
		buffer[0] = (byte) 0x02;
		buffer[1] = (byte) 0x00;
		buffer[2] = (byte) 0x00;
		buffer[3] = (byte) b3;
		buffer[4] = (byte) (segment >> 8);
		buffer[5] = (byte) segment;
		writeLine(file, buffer, 6);
		return currentSegment;
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

	public void saveCC(OutputStream file, boolean skipOnes) throws IOException {
		for (IntRange extent : words.extents()) {
			saveRange(file, extent, skipOnes);
		}
		writeString(file, ":00000001FF\n");
	}

	public void writeTo(ProgrammerPort port, boolean forceCalibration) throws IOException {

		// If the test is true, calibration forced or no reserved words to worry
		// about.
		// Else, assumes: reserved words are always at the end of program
		// memory.
		IntRange programRangeForWrite = (forceCalibration || device.reservedRange.isEmpty()) ? device.programRange
				: IntRange.empty(device.programRange.start());

		// Write the contents of program memory.
		writeArea(port, forceCalibration, "program memory", programRangeForWrite, device.programRange.isEmpty());

		// Write data memory before config memory in case the configuration
		// word turns on data protection and thus hinders data verification.
		writeArea(port, forceCalibration, "data memory", device.dataRange, device.dataRange.isEmpty());

		// Write the contents of config memory.
		writeArea(port, forceCalibration, "id words and fuses", device.configRange, device.configRange.isEmpty());

		log.info("done.");
	}

	private void writeArea(ProgrammerPort port, boolean forceCalibration, String desc, IntRange range, boolean skip)
			throws IOException {
		if (skip)
			log.info("Skipped burning " + desc + ",");
		else {
			log.info("Burning " + desc + ",");
			reportCount(writeBlockTo(port, range, forceCalibration));
		}
	}

	private int writeBlockTo(ProgrammerPort port, IntRange range, boolean forceCalibration) throws IOException {
		return words.writeTo(port.getShortSink(forceCalibration), range);
	}

	public static final class HexFileException extends IOException {

		private static final long serialVersionUID = 1L;

		HexFileException() {
			super();
		}

		HexFileException(String message, Throwable cause) {
			super(message, cause);
		}

		HexFileException(String message) {
			super(message);
		}

		HexFileException(Throwable cause) {
			super(cause);
		}

	}

}
