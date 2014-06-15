package us.hfgk.ardpicprog;

import java.io.IOException;

interface ShortSink {
	public void writeFrom(IntRange range, short[] srcArray, int offset) throws IOException;
}