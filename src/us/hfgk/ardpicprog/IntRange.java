package us.hfgk.ardpicprog;

class IntRange {
	private final int start;
	private final int post;

	public int start() {
		return start;
	}

	public int end() {
		return post - 1;
	}

	public int post() {
		return post;
	}

	public int size() {
		return post - start;
	}

	private IntRange(int start, int post) {
		this.start = start;
		if (post <= start)
			post = start;
		this.post = post;
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

	boolean intersects(IntRange other) {
		return this.start < other.post && this.post > other.start;
	}

	boolean containsValue(int value) {
		return value >= start && value < post;
	}

	boolean isEmpty() {
		return start >= post;
	}

	// If this does not intersect that, the result is an empty range whose start
	// is the greater of this start or that start.
	IntRange intersection(IntRange that) {
		return intersection(this, that);
	}

	private static final int min(int a, int b) {
		return (a < b) ? a : b;
	}

	private static final int max(int a, int b) {
		return (a > b) ? a : b;
	}

	private static IntRange intersection(IntRange a, IntRange b) {
		if (a == null || b == null)
			return null;

		return IntRange.getPost(max(a.start(), b.start()), min(a.post(), b.post()));
	}
	
	public String toString() {
		String left = "(IntRange '";
		String right = "' size " + size() + ")";
		
		String middle = isEmpty() ?
				"empty at " + start() :
				"[" + start() + "," + post() + ")";
				
		return left + middle + right;		
	}
}
