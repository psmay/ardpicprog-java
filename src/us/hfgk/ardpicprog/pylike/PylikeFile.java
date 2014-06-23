package us.hfgk.ardpicprog.pylike;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

public final class PylikeFile {
	private PylikeFile() {}
	
	private static class InputStreamReadable implements PylikeReadable {
		private final InputStream stream;
		
		public InputStreamReadable(InputStream s) {
			stream = s;
		}

		@Override
		public void close() throws IOException {
			stream.close();
		}

		@Override
		public Str read() throws IOException {
			ArrayList<Str> parts = new ArrayList<Str>();
			for(;;) {
				int requestSize = 1024;
				Str part = read(requestSize);
				if(part.equals(Str.EMPTY))
					break;
				parts.add(part);
			}
			return Str.EMPTY.join(parts.toArray(new Str[0]));
		}

		@Override
		public Str read(int size) throws IOException {
			if(size == 0)
				return Str.EMPTY;
			
			byte[] buffer = new byte[size];
			int pos = 0;
			while(pos < size) {
				int reqLen = size - pos;
				int readLen = stream.read(buffer, pos, reqLen);
				
				if(readLen <= 0)
					break;
				
				pos += readLen;
			}
			
			return Str.val(buffer, 0, pos);
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
