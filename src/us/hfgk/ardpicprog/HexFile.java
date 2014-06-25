package us.hfgk.ardpicprog;

import java.util.List;
import java.util.Map;

import us.hfgk.ardpicprog.pylike.Po;
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
		AddressRange reserved = getMetadata().getDevice().reservedRange;
		
		if (reserved.isEmpty())
			return true; // No reserved words, so force is trivially ok.
		
		for(int address : Po.xrange(reserved.start(), reserved.post())) {
			if (!isAllOnes(address))
				return true;
		}
		return false;
	}

	HexFileMetadata getMetadata() {
		return metadata;
	}

}
