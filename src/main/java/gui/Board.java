package gui;

import exceptions.BombException;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Random;

public class Board extends JFrame implements ActionListener {
	public static final Font NOTO_MONO;
	public static final Font NOTO_MONO_BOLD;
	public static final FontMetrics NOTO_MONO_METRICS;

	static {
		try {
			NOTO_MONO = Font.createFont(Font.TRUETYPE_FONT,
					Board.class.getClassLoader().getResourceAsStream("fonts/notoMono.ttf")).deriveFont(20f);
			NOTO_MONO_METRICS = new Canvas().getFontMetrics(NOTO_MONO);

			NOTO_MONO_BOLD = Font.createFont(Font.TRUETYPE_FONT,
					Board.class.getClassLoader().getResourceAsStream("fonts/notoMonoBold.ttf")).deriveFont(24f);
		} catch (FontFormatException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static final Random RANDOM = new Random();

	private int numRows;
	private int numCols;
	private int numBombs;

	private Square[][] squares;
	private JMenuBar menuBar;
	private JPanel field;

	private boolean lostGame = false;
	private boolean wonGame = false;

	public Board(int rows, int cols, int bombs) {
		this.setBounds(0, 0, 800, 600);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLayout(null);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLocationRelativeTo(null); // Center the window


		menuBar = new JMenuBar();
		menuBar.setFont(NOTO_MONO);
		menuBar.setBounds(0, 0, 800, NOTO_MONO_METRICS.getHeight() + 6);

		// Menu for saving game, loading game, and new game
		JMenu fileOptions = new JMenu("File");
		fileOptions.setFont(NOTO_MONO);

		JMenuItem item = new JMenuItem("New");
		item.setFont(NOTO_MONO);
		item.addActionListener(this);

		fileOptions.add(item);
		fileOptions.addSeparator();

		item = new JMenuItem("Save");
		item.setFont(NOTO_MONO);
		item.addActionListener(this);
		fileOptions.add(item);

		item = new JMenuItem("Load");
		item.setFont(NOTO_MONO);
		item.addActionListener(this);
		fileOptions.add(item);

		menuBar.add(fileOptions);

		// Options to change game options, like field dimensions
		JMenu gameOptions = new JMenu("Game");
		gameOptions.setFont(NOTO_MONO);

		JMenuItem changeDimensions = new JMenuItem("Change Board Size");
		changeDimensions.setFont(NOTO_MONO);
		changeDimensions.addActionListener(this);

		gameOptions.add(changeDimensions);
		menuBar.add(gameOptions);

		// Set up the field
		field = new JPanel();
		field.setBounds(0, menuBar.getHeight(), 800, 600 - menuBar.getHeight());

		if (!(rows > 0 && cols > 0 && bombs >= 0 && bombs <= rows * cols)) {
			throw new IllegalArgumentException();
		}
		this.numRows = rows;
		this.numCols = cols;
		this.numBombs = bombs;
		generateField();

		this.add(menuBar);
		this.add(field);
		this.setVisible(true);

		this.getRootPane().addComponentListener(new ComponentAdapter() {
			public void componentResized(ComponentEvent e) {
				// This is only called when the user releases the mouse button.
				System.out.println("Resized, new width " + getWidth() + " new height " + getHeight());
				// TODO: resize the rest of the window to account for this
			}
		});
	}

	private void generateField() {
		field.removeAll(); // Clear the buttons
		field.setLayout(new GridLayout(numRows, numCols)); // Reset the layout

		Point[] bombLocations = new Point[numBombs];
		for (int i = 0; i < numBombs; ++i) {
			int x, y;
			x = RANDOM.nextInt(numCols);
			y = RANDOM.nextInt(numRows);

			bombLocations[i] = new Point(x, y);

			// Check if there is already a bomb here
			for (int j = 0; j < i; ++j) {
				if (bombLocations[j].x == x && bombLocations[j].y == y) {
					// Decrement i so when the outer loop continues, it will restart the current iteration
					--i;
					break;
				}
			}
		}

		// reset the squares array and set the bombs
		squares = new Square[numRows][numCols];
		for (int i = 0; i < numBombs; ++i) {
			squares[bombLocations[i].y][bombLocations[i].x] = new Square(-1);
		}

		// Iterate through every square and determine its number
		for (int i = 0; i < numRows; ++i) {
			for (int j = 0; j < numCols; ++j) {
				// Note: j is x
				//       i is y

				if (squares[i][j] == null) {
					// I was way too lazy to hard code the edge cases (haha, get it?), so I just did this instead
					// Yes, I do know that handling the edge cases separately is much more performant
					// I am so not funny
					int numNeighborBombs = 0;

					// There can be a maximum of 8 neighbors, test them all
					// (i-1,j-1), (i-1, j ), (i-1,j+1)
					// ( i ,j-1), ( i , j ), ( i ,j+1)
					// (i+1,j-1), (i+1, j ), (i+1,j+1)
					Point[] neighbors = new Point[]{
							new Point(j - 1, i - 1), new Point(j, i - 1), new Point(j + 1, i - 1),
							new Point(j - 1, i), new Point(j, i), new Point(j + 1, i),
							new Point(j - 1, i + 1), new Point(j, i + 1), new Point(j + 1, i + 1)
					};

					for (int k = 0; k < neighbors.length; ++k) {
						try {
							if (squares[neighbors[k].y][neighbors[k].x].isBomb()) {
								++numNeighborBombs;
							}
						} catch (Exception ex) {
							// Ignore it, keep going
						}
					}

					squares[i][j] = new Square(numNeighborBombs);
				}


				// Now, the current square is either a bomb, or its number should be correctly calculated
				// checkerboard pattern
				if ((i + j) % 2 == 0) {
					squares[i][j].setBackground(new Color(0x1B8300)); // darker green
				} else {
					squares[i][j].setBackground(new Color(0x25B500)); // lighter green
				}

				squares[i][j].addActionListener(this);
				// For some stupid reason, actionPerformed doesn't get invoked when right click, so we have to manually do this
				squares[i][j].addMouseListener(new MouseListener() {
					@Override
					public void mouseClicked(MouseEvent e) {
						if (SwingUtilities.isRightMouseButton(e)) {
							Square s = (Square) e.getSource();
							if (s.getIsRevealed()) return; // Don't flag a revealed square

							if (!s.getIsFlagged()) {
								try {
									BufferedImage flag = ImageIO.read(Board.class.getClassLoader().getResource("icons/flag.png"));
									int width = flag.getWidth(), height = flag.getHeight();
									int newWidth, newHeight;
									// Scale the new width to be proportional to the height
									newHeight = s.getHeight();
									newWidth = width * newHeight / height; // Given w1/h1 = w2/h2, w2 = h2w1/h1

									Image newFlag = flag.getScaledInstance(newWidth, newHeight, BufferedImage.SCALE_DEFAULT);
									s.setIcon(new ImageIcon(newFlag));

									s.setIsFlagged(true);
								} catch (IOException ex) {
									System.err.println(ex);
								}
							} else {
								s.setIcon(null);
								s.setIsFlagged(false);
							}
						}
					}

					@Override
					public void mousePressed(MouseEvent mouseEvent) {

					}

					@Override
					public void mouseReleased(MouseEvent mouseEvent) {

					}

					@Override
					public void mouseEntered(MouseEvent mouseEvent) {

					}

					@Override
					public void mouseExited(MouseEvent mouseEvent) {

					}
				});
				//squares[i][j].setText("" + squares[i][j].getNUMBER());
				squares[i][j].setFont(NOTO_MONO_BOLD);
				field.add(squares[i][j]);
			}
		}
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		if (e.getSource() instanceof JMenuItem mi) {
			String menuText = null;
			String menuItemText = null;

			// Go through every menu in menuBar, and go through every MenuItem in that menu, and check if it is equal to the source
			// if we find it, menuText and menuItemText will be initialized
			for (int i = 0; i < menuBar.getMenuCount(); ++i) {
				JMenu menu = menuBar.getMenu(i);

				for (int j = 0; j < menu.getMenuComponentCount(); ++j) {
					if (menu.getMenuComponent(j) == mi) {
						menuItemText = mi.getText();
						menuText = menu.getText();
						break;
					}
				}
				if (menuItemText != null) { // For the per
					break;
				}
			}
			if (menuItemText == null || menuText == null) return;

			if (menuText.equals("Game")) {
				if (menuItemText.equals("Change Board Size")) {
					// TODO: Implement changing board size via a JPopupMenu
				}
			} else if (menuText.equals("File")) {
				// TODO: Implement file options
			}
		} else if (e.getSource() instanceof Square s) {
			if (!s.getIsFlagged() && !s.getIsRevealed()) {
				try {
					s.reveal();
				} catch (BombException ex) {
					System.out.println("Uh Oh!");
					return;
				}

				s.setBackground(new Color(0xFFBC5B));
				if (s.getNUMBER() != 0) s.setText("" + s.getNUMBER());
				// TODO: implement showing bombs when there is a bomb, losing, winning, and flood fill revealing all connected zeroes, and a different color for every number
			}
		}
	}
}
