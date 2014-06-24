package us.hfgk.ardpicprog.pylike;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.AbstractList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

	private static final Pattern numPrefix = Pattern.compile("^([+-]?)(0[bBoOxX]?)?(.*)$");

	private static int guessedBase(String basePrefix) {
		if (basePrefix == null) {
			return 0;
		} else if (basePrefix.length() == 1) {
			return 8; // prefix "0"
		} else {
			switch (basePrefix.charAt(1)) {
			case 'b':
			case 'B':
				return 2;
			case 'x':
			case 'X':
				return 16;
			case 'o':
			case 'O':
				return 8;
			}
		}
		throw new AssertionError();
	}

	private static int intJava(String value, int base) {
		if ((base != 0) && (base < 2 || base > 36))
			throw new IllegalArgumentException();

		Matcher m = numPrefix.matcher(value);
		if (!m.matches()) {
			throw new NumberFormatException("Value '" + value + "' does not match pattern " + numPrefix);
		}

		String sign = m.group(1);
		String basePrefix = m.group(2);
		String digits = m.group(3);

		// This refers to just 0, not 0O or 0o.
		boolean hasOctalZeroPrefix = (basePrefix != null) && basePrefix.equals("0");

		// If zero is the only digit, stripping it may be rude.
		if (hasOctalZeroPrefix)
			digits = "0" + digits;

		int guessedBase = guessedBase(basePrefix);

		if (base == 0) {
			base = (guessedBase == 0) ? 10 : guessedBase;
		} else {
			if (!hasOctalZeroPrefix && (guessedBase != 0) && (base != guessedBase))
				throw new NumberFormatException("Base-" + guessedBase + " literal '" + value
						+ "' is not a valid literal in base " + base);
		}

		return Integer.parseInt(sign + digits, base);
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
