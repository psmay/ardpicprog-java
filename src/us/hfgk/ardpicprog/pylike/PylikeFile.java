package us.hfgk.ardpicprog.pylike;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.NoSuchElementException;

public final class PylikeFile {
	private PylikeFile() {
	}

	private static class InputStreamReadable implements PylikeReadable {
		// private final InputStream stream;
		private final BufferedInputStream stream;

		public InputStreamReadable(InputStream s) {
			stream = new BufferedInputStream(s);
		}

		@Override
		public void close() throws IOException {
			stream.close();
		}

		@Override
		public Str read() throws IOException {
			ArrayList<Str> parts = new ArrayList<Str>();
			for (;;) {
				int requestSize = 1024;
				Str part = read(requestSize);
				if (part.equals(Str.EMPTY))
					break;
				parts.add(part);
			}
			return Str.EMPTY.join(parts.toArray(new Str[0]));
		}

		@Override
		public Str read(int size) throws IOException {
			if (size == 0)
				return Str.EMPTY;

			byte[] buffer = new byte[size];
			int pos = 0;
			while (pos < size) {
				int reqLen = size - pos;
				int readLen = stream.read(buffer, pos, reqLen);

				if (readLen <= 0)
					break;

				pos += readLen;
			}

			return Str.val(buffer, 0, pos);
		}

		@Override
		public Iterable<Str> readlines() throws IOException {
			return new Iterable<Str>() {
				@Override
				public Iterator<Str> iterator() {
					return new LineReader(stream);
				}
			};
		}
	}

	private static final class LineReader implements Iterator<Str> {
		private static final int READ_LF = 0x0A;
		private static final int READ_CR = 0x0D;
		private final BufferedInputStream stream;
		private Str nextLine = null;

		public LineReader(BufferedInputStream stream) {
			this.stream = stream;
		}

		private static int mark2read1(BufferedInputStream stream) throws IOException {
			stream.mark(2);
			return stream.read();
		}

		private static final Str pullLine(BufferedInputStream stream) throws IOException {
			// This is not a serious implementation.
			// Hopefully the bufferedness of the stream will mitigate the
			// inefficiency.

			ByteArrayOutputStream buf = new ByteArrayOutputStream();

			int ch;

			while ((ch = mark2read1(stream)) >= 0) {
				if ((ch == READ_CR) || (ch == READ_LF)) {
					stream.reset();
					break;
				}	
				buf.write(ch);
			}

			if (ch < 0) {
				if (buf.size() == 0) {
					// EOF
					return null;
				} else {
					// Final line without newline
					return toStr(buf);
				}
			}

			boolean haveCR = false;

			while ((ch = mark2read1(stream)) >= 0) {
				if (ch == READ_CR) {
					// \r
					if (haveCR) {
						// Back out of \r\r
						stream.reset();
						break;
					} else {
						// Have \r; stay to see if we get \n also
						buf.write(ch);
						haveCR = true;
					}
				} else if (ch == READ_LF) {
					// \n
					buf.write(ch);
					break;
				} else {
					// Non-newline
					stream.reset();
				}
			}

			return toStr(buf);
		}

		private static Str toStr(ByteArrayOutputStream buf) {
			return Str.val(buf.toByteArray());
		}

		@Override
		public boolean hasNext() {
			if (nextLine == null) {
				try {
					nextLine = pullLine(stream);
				} catch (IOException e) {
					throw new RuntimeException("Error reading line from stream", e);
				}
			}
			return (nextLine != null);
		}

		@Override
		public Str next() {
			if (!hasNext())
				throw new NoSuchElementException();
			Str here = nextLine;
			nextLine = null;
			return here;
		}

		@Override
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}

	private static class OutputStreamWritable implements PylikeWritable {
		private final OutputStream stream;

		public OutputStreamWritable(OutputStream s) {
			stream = s;
		}

		@Override
		public void close() throws IOException {
			stream.close();
		}

		@Override
		public void write(Str data) throws IOException {
			stream.write(data.getJavaByteArray());
		}
	}

	public static PylikeReadable wrapJavaStream(InputStream s) {
		return new InputStreamReadable(s);
	}

	public static PylikeWritable wrapJavaStream(OutputStream s) {
		return new OutputStreamWritable(s);
	}
}
