package us.hfgk.ardpicprog;

import java.io.IOException;
import java.util.List;

interface ShortList {

	/** Removes all elements from this list. **/
	void clear();

	/** Returns a list of index ranges in which this list is known to be populated. */
	List<IntRange> extents();

	/** Returns the value at the given index of this list, or {@code defaultValue} if the index contains no value. */
	short get(int index, short defaultValue);

	/** Returns the value at the given index of this list, or {@code null} if the index contains no value. */
	Short get(int index);

	/** Sets the given index of this list to {@code value}. */
	void set(int index, short value);

	/** Copies values from {@code source} to the indices of this list specified by {@code range}. */
	void readFrom(ShortSource source, IntRange range) throws IOException;

	/** Copies values from the indices of this list specified by {@code range} into {@code sink}. */
	int writeTo(ShortSink sink, IntRange range) throws IOException;

}