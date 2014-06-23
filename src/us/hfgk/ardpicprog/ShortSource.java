package us.hfgk.ardpicprog;

import java.io.IOException;

interface ShortSource {
	public void readTo(AddressRange range, short[] destArray, int offset) throws IOException;
}