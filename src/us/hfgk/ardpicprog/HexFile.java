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
		this._format = validateFormat(format);
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

	int _format = FORMAT_AUTO;

	private DeviceDetails device;

	public DeviceDetails getDevice() {
		return device;
	}

	private ShortList words;

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
		HexFileSerializer.save(this, file, skipOnes);
	}

	public void saveCC(OutputStream file, boolean skipOnes) throws IOException {
		HexFileSerializer.saveCC(this, file, skipOnes);
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
