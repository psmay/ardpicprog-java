package us.hfgk.ardpicprog.pylike;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.AbstractList;
import java.util.List;

public class Po {
	public static int len(Str value) {
		return value.oPlen();
	}

	public static PylikeReadable openrb(Str filename) throws FileNotFoundException {
		return PylikeFile.wrapJavaStream(new FileInputStream(filename.toString()));
	}

	public static PylikeWritable openwb(Str filename) throws FileNotFoundException {
		return PylikeFile.wrapJavaStream(new FileOutputStream(filename.toString()));
	}

	public static byte getitem(Str value, int i) {
		return value.oPgetitem(i);
	}

	public static int ord(Str value) {
		return value.oPord();
	}

	public static int int_(Str value, int base) {
		return intJava(value.toString(), base);
	}

	private static int intJava(String value, int base) {
		if (base == 0) {
			throw new UnsupportedOperationException("Base 0 is not allowed in this implementation");
		}
		return Integer.parseInt(value, base);
	}

	public static List<Integer> xrange(final int start, int post, final int step) {
		if (step == 0)
			throw new IllegalArgumentException("step must be nonzero");

		final int length = getXrangeLength(start, post, step);

		return new AbstractList<Integer>() {

			@Override
			public Integer get(int index) {
				if (index < 0 || index >= length)
					throw new IndexOutOfBoundsException();
				return start + (index * step);
			}

			@Override
			public int size() {
				return length;
			}

		};
	}

	private static int getXrangeLength(int start, int post, int step) {
		int adjustment = (step > 0) ? (step - 1) : (step + 1);
		int count = (post - start + adjustment) / step;
		return (count < 0) ? 0 : count;
	}

	public static List<Integer> xrange(int start, int post) {
		return xrange(start, post, 1);
	}

	public static List<Integer> xrange(int length) {
		return xrange(0, length);
	}
}
