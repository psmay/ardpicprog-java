package us.hfgk.ardpicprog;

import java.util.Collections;
import java.util.Map;

import us.hfgk.ardpicprog.pylike.Str;

final class DeviceDetails {
	final Str deviceName;
	final AddressRange programRange;
	final AddressRange configRange;
	final AddressRange dataRange;
	final AddressRange reservedRange;
	final int programBits;
	final int dataBits;

	DeviceDetails() throws HexFileException {
		this(null);
	}
	
	DeviceDetails(Map<Str, Str> details) throws HexFileException {
		if(details == null) {
			details = Collections.emptyMap();
		}
		deviceName = ensureDefined(details.get(Str.val("DeviceName")));
		programRange = parseRangeUnlessEmpty(details.get(Str.val("ProgramRange")), 0x0001);
		programBits = Common.parseInt(DeviceDetails.fetchMap(details, Str.val("ProgramBits"), Str.val("14")), 0);
		configRange = parseRangeUnlessEmpty(details.get(Str.val("ConfigRange")), 0x2000);
		dataRange = parseRangeUnlessEmpty(details.get(Str.val("DataRange")), 0x2100);
		dataBits = Common.parseInt(DeviceDetails.fetchMap(details, Str.val("DataBits"), Str.val("8")), 0);
		reservedRange = parseRangeUnlessEmpty(details.get(Str.val("ReservedRange")), programRange.post());

		if (programBits < 1)
			throw new HexFileException("Invalid program word width " + programBits);
		if (dataBits < 1)
			throw new HexFileException("Invalid data word width " + dataBits);
	}

	private static Str ensureDefined(Str value) {
		return (value != null) ? value : Str.EMPTY;
	}

	private static AddressRange parseRangeUnlessEmpty(Str value, int emptyStartAddress) throws HexFileException {
		return Common.strEmpty(value) ? AddressRange.empty(emptyStartAddress) : parseRange(value);
	}

	private static AddressRange parseRange(Str value) throws HexFileException {
		int index = value.find((byte)'-');
		if (index == -1)
			throw new HexFileException("Invalid range '" + value + "' (missing '-')");
		Integer start, end;
		start = Common.parseHex(value.pYslice(0, index));
		if (start == null)
			throw new HexFileException("Invalid range '" + value + "' (start not a number)");
		end = Common.parseHex(value.pYslice(index + 1));
		if (end == null)
			throw new HexFileException("Invalid range '" + value + "' (end not a number)");
		return AddressRange.getEnd(start, end);
	}

	private static Str fetchMap(Map<Str, Str> details, Str key, Str defValue) {
		Str value = details.get(key);
		return (value == null) ? defValue : value;
	}
}