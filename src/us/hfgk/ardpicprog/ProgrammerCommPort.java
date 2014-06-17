package us.hfgk.ardpicprog;

import java.io.Closeable;
import java.io.IOException;

import us.hfgk.ardpicprog.ProgrammerPort.CommBuffer;

public interface ProgrammerCommPort extends Closeable {
	@Override
	void close() throws IOException;

	boolean fillBuffer(CommBuffer buff) throws IOException;

	int getReceiveTimeout();

	void init();

	boolean isStillOpen();

	void open(String port, int speed) throws IOException;

	void setReceiveTimeout(int ms) throws PortSetupException;

	void write(byte[] data, int offset, int length) throws IOException;
}
