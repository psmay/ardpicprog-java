package us.hfgk.ardpicprog;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import us.hfgk.ardpicprog.ProgrammerPort.CommBuffer;
import us.hfgk.ardpicprog.ProgrammerPort.PortSetupException;

public class RxTxProgrammerCommPort implements ProgrammerCommPort {
	private SerialPort serialPort = null;
	private int timeoutValue = 5000;
	private OutputStream out;
	private InputStream in;

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
		out.write(data, offset, length);
	}

	public boolean fillBuffer(CommBuffer buff) throws IOException {
		return buff.fillFrom(in) > 0;
	}

	public void open(String port, int speed) throws IOException {
		if (serialPort != null)
			throw new PortSetupException("Programmer comm port already open");

		if (Common.stringEmpty(port))
			port = getDefaultPicPort();

		boolean ok = false;

		try {
			CommPortIdentifier portId = CommPortIdentifier.getPortIdentifier(port);

			serialPort = (SerialPort) portId.open("ardpicprog-java", timeoutValue);
			serialPort.setSerialPortParams(speed, SerialPort.DATABITS_8, SerialPort.STOPBITS_1, SerialPort.PARITY_NONE);
			serialPort.setFlowControlMode(SerialPort.FLOWCONTROL_NONE);
			serialPort.setDTR(true);
			serialPort.setRTS(true);
			out = serialPort.getOutputStream();
			in = serialPort.getInputStream();
			serialPort.enableReceiveThreshold(1);
			serialPort.enableReceiveTimeout(1000);
			ok = true;
		} catch (NoSuchPortException e) {
			throw new PortSetupException(port + ": No such port", e);
		} catch (PortInUseException e) {
			throw new PortSetupException(port + ": Port in use", e);
		} catch (UnsupportedCommOperationException e) {
			throw new PortSetupException(port + ": Port settings not supported", e);
		} finally {
			if (!ok) {
				if (serialPort != null) {
					serialPort.close();
					serialPort = null;
				}
				out = null;
				in = null;
			}
		}
	}

	public void setReceiveTimeout(int milliseconds) throws PortSetupException {
		if (serialPort != null)
			try {
				serialPort.enableReceiveTimeout(milliseconds);
			} catch (UnsupportedCommOperationException e) {
				throw new PortSetupException("Changing the timeout is not supported on this port", e);
			}
		timeoutMs = milliseconds;
	}

	public int getReceiveTimeout() {
		return (serialPort == null) ? timeoutMs : serialPort.getReceiveTimeout();
	}

	public boolean isStillOpen() {
		return serialPort != null;
	}

	public void close() throws IOException {
		if (serialPort != null) {
			serialPort.close();
			serialPort = null;
			out = null;
			in = null;
		}
	}

	@Override
	public void init() {
	}

}
