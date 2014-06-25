package us.hfgk.ardpicprog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Implementation of ShortList that uses only a resizable int buffer.
 */
public class DumbShortList implements ShortList {
	//private static final Logger log = Logger.getLogger(DumbShortList.class.getName());

	private int[] buffer = new int[0];

	private void ensureCapacity(int size) {
		if (size > buffer.length) {
			int oldLength = buffer.length;

			buffer = Arrays.copyOf(buffer, size);
			Arrays.fill(buffer, oldLength, buffer.length, -1);
		}
	}

	@Override
	public List<AddressRange> extentsWithin(AddressRange overRange) {
		ArrayList<AddressRange> ranges = new ArrayList<AddressRange>();

		AddressRange range = AddressRange.empty(overRange.start());
		int post = overRange.post();

		while (!(range = firstRangeAfter(range, post)).isEmpty()) {
			ranges.add(range);
		}

		ranges.trimToSize();
		return Collections.unmodifiableList(ranges);
	}

	@Override
	public List<AddressRange> extents() {
		return extentsWithin(AddressRange.getPost(0, buffer.length));
	}

	private AddressRange firstRangeAfter(AddressRange previous, int post) {
		return firstRange(previous.post(), post);
	}

	private AddressRange firstRange(int start, int post) {
		int startRange, postRange;

		for (startRange = start; startRange < post; ++startRange) {
			if (buffer[startRange] >= 0)
				break;
		}
		for (postRange = startRange; postRange < post; ++postRange) {
			if (buffer[postRange] < 0)
				break;
		}

		return AddressRange.getPost(startRange, postRange);
	}

	@Override
	public short get(int index, short defaultValue) {
		if (isUnsetAt(index))
			return defaultValue;
		return (short) buffer[index];
	}

	private boolean isUnsetAt(int index) {
		return (index >= buffer.length) || (index < 0) || (buffer[index] < 0);
	}

	@Override
	public Short get(int index) {
		if (isUnsetAt(index))
			return null;
		return (short) buffer[index];
	}

	@Override
	public short[] get(AddressRange range) {
		return Common.copyIntsToShortArray(buffer, range.start(), range.size());
	}

	@Override
	public void clear() {
		buffer = new int[0];
	}

	private void writeAt(int index, short[] source, int offset, int length) {
		if (index < 0 || offset < 0 || length < 0 || offset + length > source.length)
			throw new IndexOutOfBoundsException();
		ensureCapacity(index + length);

		Common.copyUnsignedShortsToIntArray(source, offset, buffer, index, length);
	}

	@Override
	public void set(int index, short... values) {
		writeAt(index, values, 0, values.length);
	}

}
