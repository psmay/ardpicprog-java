package us.hfgk.ardpicprog;

import java.io.IOException;

interface ShortSink {
	public void writeFrom(AddressRange range, short[] srcArray) throws IOException;
}