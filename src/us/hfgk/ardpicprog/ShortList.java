package us.hfgk.ardpicprog;

import java.io.IOException;
import java.util.List;

interface ShortList {

	void clear();

	List<IntRange> extents();

	short get(int index, short defaultValue);

	Short get(int index);

	void set(int address, short word);

	void readFrom(ShortSource source, IntRange range) throws IOException;

	int writeTo(ShortSink destination, IntRange range) throws IOException;

}