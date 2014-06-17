package us.hfgk.ardpicprog;

import java.io.IOException;

interface MutableShortList {

	/** Removes all elements from this list. **/
	void clear();

	/** Sets the given index of this list to {@code value}. */
	void set(int index, short value);

	/**
	 * Copies values from {@code source} to the indices of this list specified
	 * by {@code range}.
	 */
	void readFrom(ShortSource source, IntRange range) throws IOException;

}
