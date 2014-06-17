package us.hfgk.ardpicprog;

import java.io.IOException;

public class ProgrammerException extends IOException {
	private static final long serialVersionUID = 1L;

	ProgrammerException() {
		super();
	}

	ProgrammerException(String message, Throwable cause) {
		super(message, cause);
	}

	ProgrammerException(String message) {
		super(message);
	}

	ProgrammerException(Throwable cause) {
		super(cause);
	}

}