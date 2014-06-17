package us.hfgk.ardpicprog;


class CommandException extends ProgrammerException {
	private static final long serialVersionUID = 1L;

	CommandException() {
		super();
	}

	CommandException(String message, Throwable cause) {
		super(message, cause);
	}

	CommandException(String message) {
		super(message);
	}

	CommandException(Throwable cause) {
		super(cause);
	}
}