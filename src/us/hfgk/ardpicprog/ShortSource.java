package us.hfgk.ardpicprog;

import java.io.IOException;

interface ShortSource {
	public short[] readCopy(AddressRange range) throws IOException;
}