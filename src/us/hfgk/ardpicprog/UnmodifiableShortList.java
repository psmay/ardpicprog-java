package us.hfgk.ardpicprog;

import java.util.List;

public class UnmodifiableShortList implements ReadableShortList {

	private final ReadableShortList source;

	public UnmodifiableShortList(ReadableShortList source) {
		this.source = source;
	}

	@Override
	public List<AddressRange> extents() {
		return source.extents();
	}
	
	@Override
	public List<AddressRange> extentsWithin(AddressRange range) {
		return source.extentsWithin(range);
	}

	@Override
	public Short get(int index) {
		return source.get(index);
	}

	@Override
	public short get(int index, short defaultValue) {
		return source.get(index, defaultValue);
	}

	@Override
	public short[] get(AddressRange range) {
		return source.get(range);
	}
}
