package us.hfgk.ardpicprog.pylike;

import java.io.Closeable;
import java.io.IOException;

public interface PylikeReadable extends Closeable {
	/** Read the whole file. **/
	Str read() throws IOException;
	/** Read up to this number of bytes. **/
	Str read(int size) throws IOException;
	/** Read the file as lines **/
	Iterable<Str> readlines() throws IOException;
}
