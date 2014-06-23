package us.hfgk.ardpicprog.pylike;

import java.util.Arrays;

public final class Str {
	private final byte[] value;

	public static final Str EMPTY = new Str(new byte[0]);

	public static Str val(String str) {
		if (str.length() == 0)
			return EMPTY;

		byte[] buffer = new byte[str.length()];

		for (int i = 0; i < buffer.length; ++i) {
			buffer[i] = (byte) str.charAt(i);
		}

		return new Str(buffer);
	}

	private Str(byte[] captive) {
		this.value = captive;
	}

	private Str(byte[] noncaptive, int off, int len) {
		this.value = Arrays.copyOfRange(noncaptive, off, off + len);
	}
	
	public static Str val(byte[] buffer, int off, int len) {
		return new Str(buffer, off, len);
	}

	public boolean equals(Str s) {
		return (s.length() == this.length()) ? startswith(s) : false;
	}

	@Deprecated
	public boolean equals(String s) {
		return equals(val(s));
	}

	public int find(byte b) {
		for (int i = 0; i < value.length; ++i) {
			if (value[i] == b)
				return i;
		}
		return -1;
	}

	@Override
	public int hashCode() {
		return this.toString().hashCode();
	}

	public byte[] getJavaByteArray() {
		return Arrays.copyOf(value, value.length);
	}

	public Str join(Str... parts) {
		return joinUsing(this, parts);
	}

	public Str lower() {
		return val(toString().toLowerCase());
	}

	public Str pYappend(byte b) {
		byte[] buffer = Arrays.copyOf(value, value.length + 1);
		buffer[value.length] = b;
		return new Str(buffer);
	}

	public Str pYappend(Str other) {
		return join(this, other);
	}

	public boolean pYin(Str str) {
		return str.contains(this);
	}

	public Str pYslice(int startIndex, int endIndex) {
		startIndex = adjustIndex(startIndex);
		endIndex = adjustIndex(endIndex);
		if (endIndex < startIndex)
			endIndex = startIndex;
		return (startIndex == endIndex) ? EMPTY : new Str(value, startIndex, endIndex);
	}

	public Str slice(int startIndex) {
		return pYslice(startIndex, length());
	}

	public boolean startswith(Str prefix) {
		return hasAtIndex(prefix, 0);
	}

	public Str strip() {
		Str str = this;
		int first = 0;
		int last = str.length();
		while (first < last) {
			byte b = value[first];
			if (!isStrippable(b))
				break;
			++first;
		}
		while (first < last) {
			byte b = value[last - 1];
			if (!isStrippable(b))
				break;
			--last;
		}
		return str.pYslice(first, last);
	}

	private static int joinLengthUsing(Str sep, Str... parts) {
		int len = 0;
		int prelen = 0;
		int seplen = sep.length();

		for (Str part : parts) {
			len += (prelen + part.value.length);
			prelen = seplen;
		}
		return len;
	}

	private static Str joinUsing(Str sep, Str... parts) {
		int len = joinLengthUsing(sep, parts);

		if (len == 0)
			return EMPTY;

		byte[] buffer = new byte[len];
		int p = 0;
		byte[] prevalue = EMPTY.value;
		byte[] sepvalue = sep.value;

		for (Str part : parts) {
			System.arraycopy(prevalue, 0, buffer, p, prevalue.length);
			p += prevalue.length;

			byte[] partvalue = part.value;
			System.arraycopy(partvalue, 0, buffer, p, partvalue.length);
			p += partvalue.length;

			prevalue = sepvalue;
		}

		return new Str(buffer);
	}

	private int adjustIndex(int index) {
		if (index < 0) {
			index += value.length;
			if (index < 0) {
				index = 0;
			}
		} else if (index > value.length) {
			index = value.length;
		}
		return index;
	}

	private boolean contains(Str str) {
		return indexOf(str) >= 0;
	}

	private boolean hasAtIndex(Str str, int index) {
		int strLength = str.length();
		int thisLength = this.length() - index;
		if (thisLength < strLength)
			return false;

		for (int i = 0; i < strLength; ++i) {
			if (value[index + i] != str.value[i])
				return false;
		}
		return true;
	}

	private int indexOf(Str str) {
		int tooFar = this.length() - str.length();

		for (int i = 0; i < tooFar; ++i) {
			if (hasAtIndex(str, i))
				return i;
		}
		return -1;
	}

	private boolean isStrippable(byte b) {
		switch (b) {
		case 9:
		case 10:
		case 11:
		case 12:
		case 13:
		case 32:
			return true;
		default:
			return false;
		}
	}

	private int length() {
		return value.length;
	}

	public int oPlen() {
		return length();
	}


}
