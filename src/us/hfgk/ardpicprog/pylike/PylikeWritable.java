package us.hfgk.ardpicprog.pylike;

import java.io.Closeable;
import java.io.IOException;

public interface PylikeWritable extends Closeable {
	/** Write this entire string. **/
	void write(Str data) throws IOException;
}
