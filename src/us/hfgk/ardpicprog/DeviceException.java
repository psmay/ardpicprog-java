package us.hfgk.ardpicprog;


public class DeviceException extends ProgrammerException {
	private static final long serialVersionUID = 1L;

	DeviceException() {
		super();
	}

	DeviceException(String message, Throwable cause) {
		super(message, cause);
	}

	DeviceException(String message) {
		super(message);
	}

	DeviceException(Throwable cause) {
		super(cause);
	}

}