package us.hfgk.ardpicprog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import us.hfgk.ardpicprog.pylike.Str;
import us.hfgk.ardpicprog.pylike.Tuple2;

public class HexFileMetadata {
	private final int format;
	private final DeviceDetails device;

	// Hex file formats
	public static final int FORMAT_AUTO = -1;
	public static final int FORMAT_IHX8M = 0;
	public static final int FORMAT_IHX16 = 1;
	public static final int FORMAT_IHX32 = 2;

	public HexFileMetadata(DeviceDetails device, int format) throws HexFileException {
		if (device == null) {
			device = new DeviceDetails(Collections.<Str, Str> emptyMap());
		}
		this.device = device;
		this.format = validateFormat(format);
	}

	public int getFormat() {
		return format;
	}

	public DeviceDetails getDevice() {
		return device;
	}

	static int validateFormat(int format) throws HexFileException {
		switch (format) {
		case HexFileMetadata.FORMAT_AUTO:
		case HexFileMetadata.FORMAT_IHX8M:
		case HexFileMetadata.FORMAT_IHX16:
		case HexFileMetadata.FORMAT_IHX32:
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

	public int bitWidthAtAddress(int address) {
		return device.dataRange.containsValue(address) ? getDevice().dataBits : getDevice().programBits;
	}

	public short fullWordAtAddress(int address) {
		return (short) ((1 << bitWidthAtAddress(address)) - 1);
	}

	List<Tuple2<String, AddressRange>> getAreas() {
		List<Tuple2<String, AddressRange>> ls = new ArrayList<Tuple2<String, AddressRange>>();

		ls.add(new Tuple2<String, AddressRange>("program memory", device.programRange));
		ls.add(new Tuple2<String, AddressRange>("data memory", device.dataRange));
		ls.add(new Tuple2<String, AddressRange>("id words and fuses", device.configRange));

		return Collections.unmodifiableList(ls);
	}

	public boolean wouldBeAllOnes(int address, short wordValue) {
		return wordValue == fullWordAtAddress(address);
	}
}