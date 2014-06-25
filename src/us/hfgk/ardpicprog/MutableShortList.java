package us.hfgk.ardpicprog;

interface MutableShortList {

	/** Removes all elements from this list. **/
	void clear();

	/** Sets the indices of this list, starting with the given index, to the values in the array. */
	void set(int index, short... values);
}
