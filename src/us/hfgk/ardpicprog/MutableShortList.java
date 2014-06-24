package us.hfgk.ardpicprog;

import java.io.IOException;

interface MutableShortList {

	/** Removes all elements from this list. **/
	void clear();

	/** Sets the given index of this list to {@code value}. */
	void set(int index, short value);

	/** Sets the indices of this list, starting with the given index, to the values in the array. */
	void set(int index, short... values);
	
	/**
	 * Copies values from {@code source} to the indices of this list specified
	 * by {@code range}.
	 */
	void readFrom(ShortSource source, AddressRange range) throws IOException;
}
