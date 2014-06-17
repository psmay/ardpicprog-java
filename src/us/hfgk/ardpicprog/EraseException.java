package us.hfgk.ardpicprog;

public class EraseException extends CommandException {
	private static final long serialVersionUID = 1L;

	EraseException() {
		super();
	}

	EraseException(String message, Throwable cause) {
		super(message, cause);
	}

	EraseException(String message) {
		super(message);
	}

	EraseException(Throwable cause) {
		super(cause);
	}
}