package gui;

import javax.swing.JButton;

public class Square extends JButton {
	private final int NUMBER; // The number on the square, -1 if it is a bomb

	private boolean isRevealed;
	private boolean isFlagged;

	/**
	 * Constructor
	 * @param number the number shown on the square,
	 *      if you don't know what this is in the context of minesweeper, you should not be reading this
	 * @throws IllegalArgumentException if number is out of the range [-1]U[0,8]
	 */
	public Square(int number) {
		if (number > 8 || (number < 0 && number != -1)) {
			// 8 is the max
			throw new IllegalArgumentException("number out of range [-1]U[0,8]");
		}

		this.NUMBER = number;
		this.isRevealed = false;
		this.isFlagged = false;
	}

	/**
	 * Sets this.revealed to true
	 * @throws exceptions.BombException if this square is a bomb
	 */
	public void reveal() throws exceptions.BombException {
		if (NUMBER == -1) {
			throw new exceptions.BombException();
		}
		this.isRevealed = true;
	}

	/**
	 * Self-explanatory
	 * @return true if this.NUMBER == -1, otherwise false
	 */
	public boolean isBomb() {
		return this.NUMBER == -1;
	}

	/**
	 * Self-explanatory
	 * @return this.NUMBER
	 */
	public int getNUMBER() {
		return this.NUMBER;
	}

	/**
	 * Self-explanatory
	 * @return this.isRevealed
	 */
	public boolean getIsRevealed() {
		return this.isRevealed;
	}

	/**
	 * Self-explanatory
	 * @return this.isFlagged
	 */
	public boolean getIsFlagged() {
		return this.isFlagged;
	}

	/**
	 * Self-explanatory
	 * @param isFlagged the new value for this.isFlagged
	 */
	public void setIsFlagged(boolean isFlagged) {
		this.isFlagged = isFlagged;
	}
}
