package us.hfgk.ardpicprog;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import us.hfgk.ardpicprog.pylike.PylikeWritable;
import us.hfgk.ardpicprog.pylike.Str;
import us.hfgk.ardpicprog.pylike.Tuple2;

public class HexFile {
	private static final Logger log = Logger.getLogger(HexFile.class.getName());

	private final HexFileMetadata metadata;

	private final UnmodifiableShortList words;

	public ReadableShortList getWords() {
		return words;
	}

	public HexFile(Map<Str, Str> details, int format, ReadableShortList words) throws HexFileException {
		this(new DeviceDetails(details), format, words);
	}

	public HexFile(HexFileMetadata metadata, ReadableShortList words) throws HexFileException {
		if(metadata == null)
			throw new IllegalArgumentException();
		
		if(words == null)
			words = Common.getBlankReadableShortList();
		
		
		this.metadata = metadata;
		
		this.words = (words instanceof UnmodifiableShortList) ? (UnmodifiableShortList)words : new UnmodifiableShortList(words);
	}
	
	public HexFile(DeviceDetails device, int format, ReadableShortList words) throws HexFileException {
		this(new HexFileMetadata(device, format), words);
	}

	public HexFile(Map<Str, Str> details, int format) throws HexFileException {
		this(details, format, null);
	}

	// Hex file formats
	public static final int FORMAT_AUTO = -1;
	public static final int FORMAT_IHX8M = 0;
	public static final int FORMAT_IHX16 = 1;
	public static final int FORMAT_IHX32 = 2;

	public List<AddressRange> extents() {
		return words.extents();
	}

	public short word(int address) {
		return words.get(address, metadata.fullWordAtAddress(address));
	}

	private boolean wouldBeAllOnes(int address, short wordValue) {
		return wouldBeAllOnes(metadata, address, wordValue);
	}

	private static boolean wouldBeAllOnes(HexFileMetadata metadata, int address, short wordValue) {
		return wordValue == metadata.fullWordAtAddress(address);
	}

	public boolean isAllOnes(int address) {
		return wouldBeAllOnes(address, word(address));
	}

	public boolean canForceCalibration() {
		if (getMetadata().getDevice().reservedRange.isEmpty())
			return true; // No reserved words, so force is trivially ok.

		int post = getMetadata().getDevice().reservedRange.post();
		for (int address = getMetadata().getDevice().reservedRange.start(); address < post; ++address) {
			if (!isAllOnes(address))
				return true;
		}

		return false;
	}

	public static boolean blankCheckRead(HexFileMetadata metadata, Programmer source) throws IOException {
		for (Tuple2<String, AddressRange> area : metadata.getAreas()) {			
			if (!blankCheckArea(metadata, source, area))
				return false;
		}
		return true;
	}

	private static boolean blankCheckArea(HexFileMetadata metadata, Programmer shortSource, Tuple2<String, AddressRange> area)
			throws IOException {
		String areaDesc = area._1;
		AddressRange range = area._2;
		if (!range.isEmpty()) {
			log.info("Blank checking " + areaDesc + ",");			
			if (blankCheckFrom(metadata, shortSource, range)) {
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

	public static void readFrom(ShortList words, Programmer source, List<Tuple2<String, AddressRange>> areas)
			throws IOException {
		words.clear();
		// List<Tuple2<String, IntRange>> areas = getAreas();

		for (Tuple2<String, AddressRange> rd : areas) {
			readAreaFrom(words, source, rd);
		}

		log.info("done.");
	}

	private static void readAreaFrom(ShortList words, Programmer source, Tuple2<String, AddressRange> area)
			throws IOException {
		String areaDesc = area._1;
		AddressRange range = area._2;
		if (!range.isEmpty()) {
			log.info("Reading " + areaDesc + ",");
			words.readFrom(source, range);
		} else {
			log.info("Skipped reading " + areaDesc + ",");
		}
	}

	private static boolean blankCheckFrom(HexFileMetadata metadata, Programmer source, AddressRange range) throws IOException {
		final short[] buf = source.readCopy(range);

		int i = range.start();
		for (short word : buf) {			
			if (!wouldBeAllOnes(metadata, i, word)) {
				return false;
			}
			++i;
		}

		return true;
	}

	private static void reportCount(int count) {
		log.info((count == 1) ? " 1 location," : " " + count + " locations,");
	}

	public void save(PylikeWritable file, boolean skipOnes) throws IOException {
		HexFileSerializer.save(this, file, skipOnes);
	}

	public void saveCC(PylikeWritable file, boolean skipOnes) throws IOException {
		HexFileSerializer.saveCC(this, file, skipOnes);
	}

	public static void writeTo(Programmer pgm, DeviceDetails device, ReadableShortList data) throws IOException {
		// If the test is true, calibration forced or no reserved words to worry
		// about.
		// Else, assumes: reserved words are always at the end of program
		// memory.		
		
		AddressRange programRangeForWrite = (pgm.getForceCalibration() || device.reservedRange.isEmpty())
				? device.programRange : device.programStartToReservedStart();

		// Write the contents of program memory.
		
		writeArea(data, pgm, "program memory", programRangeForWrite, device.programRange.isEmpty());

		// Write data memory before config memory in case the configuration
		// word turns on data protection and thus hinders data verification.
		writeArea(data, pgm, "data memory", device.dataRange, device.dataRange.isEmpty());

		// Write the contents of config memory.
		writeArea(data, pgm, "id words and fuses", device.configRange, device.configRange.isEmpty());

		log.info("done.");
	}

	private static void writeArea(ReadableShortList words, Programmer pgm, String desc, AddressRange range, boolean skip) throws IOException {
		if (skip)
			log.info("Skipped burning " + desc + ",");
		else {
			log.info("Burning " + desc + ",");
			reportCount(words.writeTo(pgm, range));
		}
	}

	HexFileMetadata getMetadata() {
		return metadata;
	}

}
