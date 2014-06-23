package us.hfgk.ardpicprog.pylike;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;

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

}
