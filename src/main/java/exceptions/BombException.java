package exceptions;

public class BombException extends Exception {
	public BombException(String message) {
		super(message);
	}

	public BombException() {
		super();
	}
}
