package us.hfgk.ardpicprog;

import java.util.List;

interface ReadableShortList {

	/**
	 * Returns a list of index ranges in which this list is known to be
	 * populated.
	 */
	List<AddressRange> extents();

	/**
	 * Returns the intersection, if any, of each element in extents() with the
	 * given range.
	 * 
	 * If an extent is entirely outside this range, there is no corresponding
	 * element here.
	 * 
	 * If an extent is partly outside this range, the element will reflect only
	 * the portion inside the range.
	 */
	List<AddressRange> extentsWithin(AddressRange range);

	/**
	 * Returns the value at the given index of this list, or
	 * {@code defaultValue} if the index contains no value.
	 */
	short get(int index, short defaultValue);

	/**
	 * Returns the values of indices in the given range of this list, if the
	 * range is within one of the defined extents. The behavior of retrieving
	 * one or more unset indices is not defined.
	 */
	short[] get(AddressRange range);

	/**
	 * Returns the value at the given index of this list, or {@code null} if the
	 * index contains no value.
	 */
	Short get(int index);
}
