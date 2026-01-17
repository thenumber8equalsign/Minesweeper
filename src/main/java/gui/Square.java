package gui;

import javax.swing.JButton;
import java.awt.Color;
import java.util.Map;

public class Square extends JButton {
	public static final Map<Integer, Color> NUMBER_TO_COLOR_MAP = Map.ofEntries(
			Map.entry(1, new Color(0x227FF2)),
			Map.entry(2, new Color(0x067A09)),
			Map.entry(3, new Color(0xB90000)),
			Map.entry(4, new Color(0x124078)),
			Map.entry(5, new Color(0x850505)),
			Map.entry(6, new Color(0x148CAD)),
			Map.entry(7, new Color(0x000000)),
			Map.entry(8, new Color(0xA5A5A5))
	);

	private final int NUMBER; // The number on the square, -1 if it is a bomb

	private boolean isRevealed;
	private boolean isFlagged;

	/**
	 * Constructor
	 *
	 * @param number the number shown on the square,
	 *               if you don't know what this is in the context of minesweeper, you should not be reading this
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
	 *
	 * @throws exceptions.BombException if this square is a bomb
	 */
	public void reveal() throws exceptions.BombException {
		if (this.isFlagged) return;

		if (isBomb()) {
			throw new exceptions.BombException();
		}
		this.isRevealed = true;


		super.setBackground(new Color(0xFFBC5B));
		super.setForeground(NUMBER_TO_COLOR_MAP.get(this.NUMBER));

		if (this.NUMBER != 0) {
			super.setText("" + this.NUMBER);
		} else {
			super.setText("");
		}
	}

	/**
	 * Self-explanatory
	 *
	 * @return true if this.NUMBER == -1, otherwise false
	 */
	public boolean isBomb() {
		return this.NUMBER == -1;
	}

	/**
	 * Self-explanatory
	 *
	 * @return this.NUMBER
	 */
	public int getNUMBER() {
		return this.NUMBER;
	}

	/**
	 * Self-explanatory
	 *
	 * @return this.isRevealed
	 */
	public boolean getIsRevealed() {
		return this.isRevealed;
	}

	/**
	 * Self-explanatory
	 *
	 * @return this.isFlagged
	 */
	public boolean getIsFlagged() {
		return this.isFlagged;
	}

	/**
	 * Self-explanatory
	 *
	 * @param isFlagged the new value for this.isFlagged
	 */
	public void setIsFlagged(boolean isFlagged) {
		if (this.isRevealed) return; // Don't flag a revealed square
		this.isFlagged = isFlagged;
	}
}
