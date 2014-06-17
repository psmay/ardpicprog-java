package us.hfgk.ardpicprog;

class PacketResponseException extends ProgrammerException {

	private static final long serialVersionUID = 1L;

	PacketResponseException() {
		super();

	}

	PacketResponseException(String message, Throwable cause) {
		super(message, cause);

	}

	PacketResponseException(String message) {
		super(message);

	}

	PacketResponseException(Throwable cause) {
		super(cause);

	}

}