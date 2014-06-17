package us.hfgk.ardpicprog;

import java.util.Collections;
import java.util.Map;

final class DeviceDetails {
	final String deviceName;
	final IntRange programRange;
	final IntRange configRange;
	final IntRange dataRange;
	final IntRange reservedRange;
	final int programBits;
	final int dataBits;

	DeviceDetails() throws HexFileException {
		this(null);
	}
	
	DeviceDetails(Map<String, String> details) throws HexFileException {
		if(details == null) {
			details = Collections.emptyMap();
		}
		deviceName = ensureDefined(details.get("DeviceName"));
		programRange = parseRangeUnlessEmpty(details.get("ProgramRange"), 0x0001);
		programBits = Common.parseInt(DeviceDetails.fetchMap(details, "ProgramBits", "14"), 0);
		configRange = parseRangeUnlessEmpty(details.get("ConfigRange"), 0x2000);
		dataRange = parseRangeUnlessEmpty(details.get("DataRange"), 0x2100);
		dataBits = Common.parseInt(DeviceDetails.fetchMap(details, "DataBits", "8"), 0);
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

	private static String fetchMap(Map<String, String> details, String key, String defValue) {
		String value = details.get(key);
		return (value == null) ? defValue : value;
	}
}