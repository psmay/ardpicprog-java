package us.hfgk.ardpicprog;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import us.hfgk.ardpicprog.pylike.PylikeWritable;
import us.hfgk.ardpicprog.pylike.Str;

public class HexFile {
	//private static final Logger log = Logger.getLogger(HexFile.class.getName());

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

	public boolean isAllOnes(int address) {
		return metadata.wouldBeAllOnes(address, word(address));
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

	public void save(PylikeWritable file, boolean skipOnes) throws IOException {
		HexFileSerializer.save(this, file, skipOnes);
	}

	public void saveCC(PylikeWritable file, boolean skipOnes) throws IOException {
		HexFileSerializer.saveCC(this, file, skipOnes);
	}

	HexFileMetadata getMetadata() {
		return metadata;
	}

}
