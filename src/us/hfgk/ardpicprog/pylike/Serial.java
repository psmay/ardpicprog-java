package us.hfgk.ardpicprog.pylike;

import gnu.io.CommPortIdentifier;
import gnu.io.NoSuchPortException;
import gnu.io.PortInUseException;
import gnu.io.SerialPort;
import gnu.io.UnsupportedCommOperationException;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.logging.Logger;

public class Serial {
	private static final Logger log = Logger.getLogger(Serial.class.getName());

	private static final String OPEN_PORT_APPNAME = "ardpicprog-java";
	private static final int OPEN_PORT_TIMEOUT = 15000;

	private final SerialPort serialPort;

	// Passthroughs to the original

	public int timeout() {
		return serialPort.getReceiveTimeout();
	}

	public void timeout(int value) throws UnsupportedCommOperationException {
		serialPort.enableReceiveTimeout(value);
	}

	public void setDTR() {
		setDTR(true);
	}

	public void setDTR(boolean level) {
		serialPort.setDTR(level);
	}

	public void setRTS() {
		setRTS(true);
	}

	public void setRTS(boolean level) {
		serialPort.setRTS(level);
	}

	private Serial(SerialPort serialPort) {
		this.serialPort = serialPort;
	}

	public enum ByteSize {
		FIVEBITS(SerialPort.DATABITS_5),
		SIXBITS(SerialPort.DATABITS_6),
		SEVENBITS(SerialPort.DATABITS_7),
		EIGHTBITS(SerialPort.DATABITS_8);

		private final int commValue;

		private ByteSize(int commValue) {
			this.commValue = commValue;
		}
	}

	public enum Parity {
		PARITY_NONE(SerialPort.PARITY_NONE),
		PARITY_EVEN(SerialPort.PARITY_EVEN),
		PARITY_ODD(SerialPort.PARITY_ODD),
		PARITY_MARK(SerialPort.PARITY_MARK),
		PARITY_SPACE(SerialPort.PARITY_SPACE);

		private final int commValue;

		private Parity(int commValue) {
			this.commValue = commValue;
		}
	}

	public enum StopBits {
		STOPBITS_ONE(SerialPort.STOPBITS_1),
		STOPBITS_ONE_POINT_FIVE(SerialPort.STOPBITS_1_5),
		STOPBITS_TWO(SerialPort.STOPBITS_2);

		private final int commValue;

		private StopBits(int commValue) {
			this.commValue = commValue;
		}
	};

	public static final class Params {
		public String port = null;
		public Integer baudrate = null;
		public ByteSize bytesize = null;
		public Parity parity = null;
		public StopBits stopbits = null;
		// public Integer timeout = null;
		public Boolean xonxoff = null;
		public Boolean rtscts = null;
		public Boolean dsrdtr = null;
		// public Integer writeTimeout = null;
		// public Integer interCharTimeout = null;
	}

	public void close() {
		serialPort.close();
	}

	private static void setSerialPortParams(SerialPort sp, Integer baudrate, ByteSize byteSize, StopBits stopBits,
			Parity parity) throws UnsupportedCommOperationException {
		if (baudrate == null)
			baudrate = 9600;
		if (byteSize == null)
			byteSize = ByteSize.EIGHTBITS;
		if (stopBits == null)
			stopBits = StopBits.STOPBITS_ONE;
		if (parity == null)
			parity = Parity.PARITY_NONE;

		sp.setSerialPortParams(baudrate, byteSize.commValue, stopBits.commValue, parity.commValue);
	}

	private static void setSerialPortFlowControl(SerialPort sp, Boolean xonxoff, Boolean rtscts, Boolean dsrdtr)
			throws UnsupportedCommOperationException {
		if (xonxoff == null)
			xonxoff = false;
		if (rtscts == null)
			rtscts = false;
		if (dsrdtr == null)
			dsrdtr = false;

		int xbits = xonxoff ? (SerialPort.FLOWCONTROL_XONXOFF_IN | SerialPort.FLOWCONTROL_XONXOFF_OUT) : 0;
		int rbits = rtscts ? (SerialPort.FLOWCONTROL_RTSCTS_IN | SerialPort.FLOWCONTROL_RTSCTS_OUT) : 0;
		// PySerial would silently ignore dsrdtr on a platform that doesn't
		// support it
		int dbits = 0;

		sp.setFlowControlMode(xbits | rbits | dbits);
	}

	public static Serial serial(Params params) throws PortInUseException, NoSuchPortException,
			UnsupportedCommOperationException {
		SerialPort sp = null;
		boolean ok = false;
		try {
			CommPortIdentifier portId = CommPortIdentifier.getPortIdentifier(params.port);
			sp = (SerialPort) portId.open(OPEN_PORT_APPNAME, OPEN_PORT_TIMEOUT);
			setSerialPortParams(sp, params.baudrate, params.bytesize, params.stopbits, params.parity);
			setSerialPortFlowControl(sp, params.xonxoff, params.rtscts, params.dsrdtr);

			ok = true;

			log.fine("Serial port is: " + sp);
			return new Serial(sp);
		} finally {
			if (!ok) {
				if (sp != null) {
					sp.close();
				}
			}
		}
	}

	public void write(Str value) throws IOException {
		write(serialPort.getOutputStream(), value);
	}

	private static void write(OutputStream outputStream, Str value) throws IOException {
		outputStream.write(value.getJavaByteArray());
	}

	public Str read(int count) throws IOException {
		return read(serialPort.getInputStream(), count);
	}

	private Str read(InputStream inputStream, int count) throws IOException {
		byte[] inputBuffer = new byte[count];
		int readLen = inputStream.read(inputBuffer);
		if (readLen < 0)
			return null;
		return Str.val(inputBuffer, 0, readLen);
	}
}
