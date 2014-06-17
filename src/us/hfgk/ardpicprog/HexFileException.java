package us.hfgk.ardpicprog;

import java.io.IOException;

public final class HexFileException extends IOException {

	private static final long serialVersionUID = 1L;

	HexFileException() {
		super();
	}

	HexFileException(String message, Throwable cause) {
		super(message, cause);
	}

	HexFileException(String message) {
		super(message);
	}

	HexFileException(Throwable cause) {
		super(cause);
	}

}