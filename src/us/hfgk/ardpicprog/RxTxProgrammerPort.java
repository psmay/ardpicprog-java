package us.hfgk.ardpicprog;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class RxTxProgrammerPort extends ProgrammerPort {
	private SerialPort serialPort = null;
	private int timeoutValue = 5000;
	private OutputStream out;
	private InputStream in;

	public static String getDefaultPicPort() {
		return "/dev/ttyACM0";
	}

	public RxTxProgrammerPort() {
		super();
	}

	public RxTxProgrammerPort(int timeoutSecs) {
		super(timeoutSecs);
	}

	@Override
	protected void init() {
	}

	@Override
	protected void write(byte[] data, int offset, int length) throws IOException {
		out.write(data, offset, length);
	}

	@Override
	protected boolean fillBuffer() throws IOException {
		int bytesRead;
		buflen = 0;
		bufposn = 0;

		bytesRead = in.read(buffer);
		buflen = bytesRead;

		return (bytesRead > 0);
	}

	@Override
	public void open(String port, int speed) throws IOException {
		if (serialPort != null)
			throw new PortSetupException("Programmer port already open");

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

			// Wait for programmer to recover from a reset
			serialPort.enableReceiveTimeout(1000);
			boolean versionCompatible = pollVersion(0);
			if (!versionCompatible)
				throw new PortSetupException(port + ": Programmer did not respond with a compatible version string");

			serialPort.enableReceiveTimeout(timeoutSecs * 1000);
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

	@Override
	protected boolean deviceStillOpen() {
		return serialPort != null;
	}

	@Override
	protected void closeDevice() throws IOException {
		if (serialPort != null) {
			serialPort.close();
			serialPort = null;
			out = null;
			in = null;
		}
	}

}
