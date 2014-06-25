package us.hfgk.ardpicprog;

import java.io.IOException;
import java.util.List;

interface ReadableShortList {

	/**
	 * Returns a list of index ranges in which this list is known to be
	 * populated.
	 */
	List<AddressRange> extents();

	/**
	 * Returns the value at the given index of this list, or
	 * {@code defaultValue} if the index contains no value.
	 */
	short get(int index, short defaultValue);

	/**
	 * Returns the value at the given index of this list, or {@code null} if the
	 * index contains no value.
	 */
	Short get(int index);

	/**
	 * Copies values from the indices of this list specified by {@code range}
	 * into {@code sink}.
	 */
	int writeTo(Programmer sink, AddressRange range) throws IOException;
}
