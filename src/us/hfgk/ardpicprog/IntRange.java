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

	static IntRange getPost(int start, int post) {
		return new IntRange(start, post);
	}
	
	static IntRange getEnd(int start, int end) {
		return getPost(start, end + 1);
	}
	
	static IntRange getSize(int start, int size) {
		return getPost(start, start + size);
	}

	static IntRange empty(int start) {
		return getPost(start, start);
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
	
	IntRange overlapWithContainedRange(IntRange inner) {
		return containsRange(inner) ? findOverlapWithContainedRange(this, inner) : null;
	}
	
	private static IntRange findOverlapWithContainedRange(IntRange outer, IntRange inner) {
		// Assumes outer.containsRange(inner)
		final int overlapStart = outer.start() > inner.start() ? outer.start() : inner.start();
		final int overlapPost = outer.post() < inner.post() ? outer.post() : inner.post();
		return IntRange.getPost(overlapStart, overlapPost);
	}
}
