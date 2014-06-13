package us.hfgk.ardpicprog;

class IntRange {
	private final int _start;
	private final int _post;

	public int start() {
		return _start;
	}

	public int end() {
		return _post - 1;
	}

	public int post() {
		return _post;
	}

	public int size() {
		return _post - _start;
	}

	private IntRange(int start, int post) {
		this._start = start;
		if (post <= start)
			post = start;
		this._post = post;
	}

	static IntRange get(int start, int end) {
		return new IntRange(start, end + 1);
	}

	static IntRange getNatural(int start, int post) {
		return new IntRange(start, post);
	}

	static IntRange empty(int start) {
		return get(start, start - 1);
	}

	boolean containsRange(IntRange other) {
		return this._start < other._post && this._post > other._start;
	}

	boolean containsValue(int value) {
		return value >= _start && value < _post;
	}

	boolean isEmpty() {
		return _start >= _post;
	}
}
