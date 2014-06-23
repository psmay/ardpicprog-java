package us.hfgk.ardpicprog;

import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import us.hfgk.ardpicprog.Programmer.CommBuffer;
import us.hfgk.ardpicprog.pylike.Serial;
import us.hfgk.ardpicprog.pylike.Serial.ByteSize;
import us.hfgk.ardpicprog.pylike.Serial.Parity;
import us.hfgk.ardpicprog.pylike.Serial.StopBits;
import us.hfgk.ardpicprog.pylike.Str;

public class RxTxProgrammerCommPort {
	private Serial ser = null;

	private static final int DEFAULT_TIMEOUT_MILLISECONDS = 3000;

	private int timeoutMs;

	public static String getDefaultPicPort() {
		return "/dev/ttyACM0";
	}

	public RxTxProgrammerCommPort() {
		this(DEFAULT_TIMEOUT_MILLISECONDS);
	}

	public RxTxProgrammerCommPort(int timeoutMs) {
		this.timeoutMs = timeoutMs;
	}

	public void write(byte[] data, int offset, int length) throws IOException {
		write(Str.val(data, offset, length));
	}
	
	public void write(Str data) throws IOException {
		ser.write(data);
	}

	public boolean fillBuffer(CommBuffer buff) throws IOException {
		return buff.fillFrom(ser) > 0;
	}

	public void open(String port, int speed) throws IOException {
		if (ser != null)
			throw new PortSetupException("Programmer comm port already open");

		if (Common.stringEmpty(port))
			port = getDefaultPicPort();

		boolean ok = false;

		try {
			Serial.Params params = new Serial.Params();
			params.port = port;
			params.baudrate = speed;
			params.bytesize = ByteSize.EIGHTBITS;
			params.stopbits = StopBits.STOPBITS_ONE;
			params.parity = Parity.PARITY_NONE;
			params.xonxoff = false;
			params.rtscts = false;
			params.dsrdtr = false;
			
			ser = Serial.serial(params);
			
			ser.setDTR();
			ser.setRTS();
			
			ser.timeout(1000);
			ok = true;
		} catch (NoSuchPortException e) {
			throw new PortSetupException(port + ": No such port", e);
		} catch (PortInUseException e) {
			throw new PortSetupException(port + ": Port in use", e);
		} catch (UnsupportedCommOperationException e) {
			throw new PortSetupException(port + ": Port settings not supported", e);
		} finally {
			if (!ok) {
				if (ser != null) {
					ser.close();
					ser = null;
				}
			}
		}
	}

	public void setReceiveTimeout(int milliseconds) throws PortSetupException {
		if (ser != null)
			try {
				ser.timeout(milliseconds);
			} catch (UnsupportedCommOperationException e) {
				throw new PortSetupException("Changing the timeout is not supported on this port", e);
			}
		timeoutMs = milliseconds;
	}

	public int getReceiveTimeout() {
		return (ser == null) ? timeoutMs : ser.timeout();
	}

	public boolean isStillOpen() {
		return ser != null;
	}

	public void close() throws IOException {
		if (ser != null) {
			ser.close();
			ser = null;
		}
	}

}
