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

	private final int format;
	private DeviceDetails device;
	private ShortList words;

	public HexFile(Map<String, String> details, int format, ShortList words) throws HexFileException {
		this.format = validateFormat(format);
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

	private static int validateFormat(int format) throws HexFileException {
		switch (format) {
		case FORMAT_AUTO:
		case FORMAT_IHX8M:
		case FORMAT_IHX16:
		case FORMAT_IHX32:
			return format;
		default:
			throw new HexFileException("Unknown format");
		}
	}

	int programSizeWords() {
		return device.programRange.size();
	}

	public int dataSizeBytes() {
		return device.dataRange.size() * getDevice().dataBits / 8;
	}

	public int getFormat() {
		return format;
	}

	public DeviceDetails getDevice() {
		return device;
	}

	public List<IntRange> extents() {
		return words.extents();
	}

	public short word(int address) {
		return words.get(address, fullWord(address));
	}

	private short fullWord(int address) {
		int numberOfBits = device.dataRange.containsValue(address) ? device.dataBits : device.programBits;
		return (short) ((1 << numberOfBits) - 1);
	}

	private boolean wouldBeAllOnes(int address, short wordValue) {
		return wordValue == fullWord(address);
	}

	public boolean isAllOnes(int address) {
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

	public boolean blankCheckRead(ShortSource source) throws IOException {
		for (Tuple2<String, IntRange> area : getAreas()) {
			if (!blankCheckArea(source, area))
				return false;
		}
		return true;
	}

	private boolean blankCheckArea(ShortSource shortSource, Tuple2<String, IntRange> area) throws IOException {
		String areaDesc = area._1;
		IntRange range = area._2;
		if (!range.isEmpty()) {
			log.info("Blank checking " + areaDesc + ",");
			if (blankCheckFrom(shortSource, range)) {
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

	public void readFrom(ShortSource source) throws IOException {
		words.clear();

		for (Tuple2<String, IntRange> rd : getAreas()) {
			readAreaFrom(source, rd);
		}

		log.info("done.");
	}

	private void readAreaFrom(ShortSource source, Tuple2<String, IntRange> area) throws IOException {
		String areaDesc = area._1;
		IntRange range = area._2;
		if (!range.isEmpty()) {
			log.info("Reading " + areaDesc + ",");
			words.readFrom(source, range);
		} else {
			log.info("Skipped reading " + areaDesc + ",");
		}
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
		HexFileSerializer.save(this, file, skipOnes);
	}

	public void saveCC(OutputStream file, boolean skipOnes) throws IOException {
		HexFileSerializer.saveCC(this, file, skipOnes);
	}

	public void writeTo(ProgrammerPort port, boolean forceCalibration) throws IOException {
		writeTo(port.getShortSink(forceCalibration), forceCalibration);
	}

	public void writeTo(ShortSink sink, boolean forceCalibration) throws IOException {
		// If the test is true, calibration forced or no reserved words to worry
		// about.
		// Else, assumes: reserved words are always at the end of program
		// memory.
		IntRange programRangeForWrite = (forceCalibration || device.reservedRange.isEmpty()) ? device.programRange
				: programStartToReservedStart();

		// Write the contents of program memory.
		writeArea(sink, "program memory", programRangeForWrite, device.programRange.isEmpty());

		// Write data memory before config memory in case the configuration
		// word turns on data protection and thus hinders data verification.
		writeArea(sink, "data memory", device.dataRange, device.dataRange.isEmpty());

		// Write the contents of config memory.
		writeArea(sink, "id words and fuses", device.configRange, device.configRange.isEmpty());

		log.info("done.");
	}

	private IntRange programStartToReservedStart() {
		return IntRange.getPost(device.programRange.start(), device.reservedRange.start());
	}

	private void writeArea(ShortSink sink, String desc, IntRange range, boolean skip) throws IOException {
		if (skip)
			log.info("Skipped burning " + desc + ",");
		else {
			log.info("Burning " + desc + ",");
			reportCount(words.writeTo(sink, range));
		}
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
