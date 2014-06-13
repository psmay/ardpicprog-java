package us.hfgk.ardpicprog;

import java.util.Arrays;

class Block {
	private int address;
	private short[] data;
	
	public Block(int address, int minCapacity, short... data) {
		this.address = address;
		if(data.length > minCapacity)
			minCapacity = data.length;
		this.data = Arrays.copyOf(data, minCapacity);
	}
	
	private void resizeLeftAligned(int size) {
		this.data = Arrays.copyOf(data, size);
	}

	private void resizeRightAligned(int newSize) {
		this.data = rightCopyOf(data, newSize);
	}

	private static short[] rightCopyOf(short[] src, final int newSize) {
		short[] dst = new short[newSize];

		final int currentSize = src.length;

		final int dstEndIndex = newSize;

		// If the copy is right-aligned, this is the ideal dst index of the
		// first src element.
		int dstIndexOfSrc0 = newSize - currentSize;

		// We can't copy negative indices, so truncate on left if necessary.
		int dstStartIndex = (dstIndexOfSrc0 < 0) ? 0 : dstIndexOfSrc0;

		// If truncation happened on dst, do it on src also.
		int srcStartIndex = dstStartIndex - dstIndexOfSrc0;

		// Now we have the number of elements that will actually be copied.
		int copyLength = dstEndIndex - dstStartIndex;

		if (copyLength > 0) {
			System.arraycopy(src, srcStartIndex, dst, dstStartIndex, copyLength);
		}

		// We would do zero-fill for any remaining elements on the left, but
		// Java did that for us when the array was created.

		return dst;
	}

	public void add(short word) {
		resizeLeftAligned(data.length + 1);
		data[data.length - 1] = word;
	}

	public void prepend(short word) {
		resizeRightAligned(data.length + 1);
		--address;
		data[0] = word;
	}

	public void ensureAtLeast(int newSize) {
		if (newSize > data.length) {
			resizeLeftAligned(newSize);
		}
	}

	int getAddress() {
		return address;
	}

	short[] getData() {
		return data;
	}

	int size() {
		return data.length;
	}
}