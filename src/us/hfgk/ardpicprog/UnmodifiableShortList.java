package us.hfgk.ardpicprog;

import java.io.IOException;
import java.util.List;

public class UnmodifiableShortList implements ReadableShortList {

	private final ReadableShortList source;
	
	public UnmodifiableShortList(ReadableShortList source) {
		this.source = source;
	}

	public List<AddressRange> extents() {
		return source.extents();
	}

	public short get(int index, short defaultValue) {
		return source.get(index, defaultValue);
	}

	public Short get(int index) {
		return source.get(index);
	}

	public int writeTo(Programmer sink, AddressRange range) throws IOException {
		return source.writeTo(sink, range);
	}
}
