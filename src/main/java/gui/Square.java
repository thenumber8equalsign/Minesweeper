package gui;

import javax.swing.JButton;

public class Square extends JButton {

	private final int NUMBER; // The number on the square, -1 if it is a bomb

	private boolean revealed;

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
		this.revealed = false;
	}

	/**
	 * Sets revealed to true
	 */
	public void reveal() {
		this.revealed = true;
	}
}
