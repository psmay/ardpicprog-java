package us.hfgk.ardpicprog;

class IntRange {
	final int start;
	final int end;

	private IntRange(int start, int end) {
		this.start = start;
		if (end < start)
			end = start - 1;
		this.end = end;
	}

	static IntRange get(int start, int end) {
		return new IntRange(start, end);
	}

	static IntRange empty(int start) {
		return get(start, start - 1);
	}
}
