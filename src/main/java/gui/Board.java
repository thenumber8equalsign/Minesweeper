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
	public static final int MENU_BAR_HEIGHT;
	public static final int MAX_SAVE_SLOTS = 4;

	static {
		try {
			NOTO_MONO = Font.createFont(Font.TRUETYPE_FONT,
					Board.class.getClassLoader().getResourceAsStream("fonts/notoMono.ttf")).deriveFont(20f);
			NOTO_MONO_METRICS = new Canvas().getFontMetrics(NOTO_MONO);
			MENU_BAR_HEIGHT = NOTO_MONO_METRICS.getHeight() + 6;

			NOTO_MONO_BOLD = Font.createFont(Font.TRUETYPE_FONT,
					Board.class.getClassLoader().getResourceAsStream("fonts/notoMonoBold.ttf")).deriveFont(24f);
		} catch (FontFormatException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static final Random RANDOM = new Random();

	private static final int DEFAULT_SQUARE_LENGTH = 54;

	private int numRows;
	private int numCols;
	private int numBombs;
	private int numFlags = 0;

	private Square[][] squares;
	private JMenuBar menuBar;
	private JPanel field;

	private JLabel flagsPlacedLabel;

	private boolean gameOver = false;
	private boolean wonGame = false;
	private boolean firstClick = true;
	private boolean hasX = false; // This boolean will store if our board has an "X" on it to mark which square the user should click first

	public Board(int rows, int cols, int bombs) {
		if (!(rows > 0 && cols > 0 && bombs >= 0 && bombs <= rows * cols)) {
			throw new IllegalArgumentException();
		}
		this.numRows = rows;
		this.numCols = cols;
		this.numBombs = bombs;

		// Set the content pane's preferred size because then it will automatically account for the title bar and whatnot
		this.getContentPane().setPreferredSize(new Dimension(numCols * DEFAULT_SQUARE_LENGTH, DEFAULT_SQUARE_LENGTH * numRows + MENU_BAR_HEIGHT));
		this.pack(); // resize the frame to fit the components (the content pane)

		this.setTitle("Minesweeper");
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLayout(null);
		this.setLocationRelativeTo(null); // Center the window


		menuBar = new JMenuBar();
		menuBar.setFont(NOTO_MONO);
		menuBar.setBounds(0, 0, this.getContentPane().getWidth(), MENU_BAR_HEIGHT);

		// Menu for saving game, loading game, and new game
		JMenu fileOptions = new JMenu("File");
		fileOptions.setFont(NOTO_MONO);

		JMenuItem newGameItem = new JMenuItem("New");
		newGameItem.setFont(NOTO_MONO);
		newGameItem.addActionListener(this);

		fileOptions.add(newGameItem);
		fileOptions.addSeparator();

		int[] availableSaveSlots = getAvailableSaveSlots();
		JMenu submenu = new JMenu("Save");
		submenu.setFont(NOTO_MONO);
		for (int i = 0; i < MAX_SAVE_SLOTS; ++i) {
			JMenuItem item = new JMenuItem();
			item.setFont(NOTO_MONO);

			boolean available = false;
			for (int j = 0; j < availableSaveSlots.length; ++j) {
				if (availableSaveSlots[j] == i) {
					available = true;
					break;
				}
			}

			final int FINAL_I = i;
			final boolean FINAL_AVAILABLE = available;
			item.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent actionEvent) {
					if (!FINAL_AVAILABLE) {
						int result = JOptionPane.showConfirmDialog(null, "Overwrite save slot?", "Ovewrite Save Slot", JOptionPane.YES_NO_OPTION);
						if (result != JOptionPane.YES_OPTION) return;
					}

					saveGame(FINAL_I);
				}
			});

			String text = "Slot " + i + ": ";
			item.setText("Slot " + i + ": " + ((available) ? " Available" : " In Use"));

			submenu.add(item);
		}
		fileOptions.add(submenu);

		submenu = new JMenu("Load");
		submenu.setFont(NOTO_MONO);
		for (int i = 0; i < MAX_SAVE_SLOTS; ++i) {
			JMenuItem item = new JMenuItem();
			item.setFont(NOTO_MONO);

			boolean empty = true;
			for (int j = 0; j < availableSaveSlots.length; ++j) {
				if (availableSaveSlots[j] == i) {
					empty = false;
					break;
				}
			}

			final int FINAL_I = i;
			final boolean FINAL_EMPTY = empty;
			item.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent actionEvent) {
					if (!FINAL_EMPTY) {
						JOptionPane.showMessageDialog(null, "The slot is empty", "Empty Slot", JOptionPane.PLAIN_MESSAGE);
						return;
					}

					try {
						loadGame(FINAL_I);
					} catch (ClassNotFoundException e) {
						// This should never happen
					}
				}
			});

			String text = "Slot " + i + ": ";
			item.setText("Slot " + i + ": " + ((empty) ? " Has Game" : " Empty"));

			submenu.add(item);
		}
		fileOptions.add(submenu);

		menuBar.add(fileOptions);

		// Options to change game options, like field dimensions
		JMenu gameOptions = new JMenu("Game");
		gameOptions.setFont(NOTO_MONO);

		JMenuItem changeDimensions = new JMenuItem("Change Board Size");
		changeDimensions.setFont(NOTO_MONO);
		changeDimensions.addActionListener(this);

		gameOptions.add(changeDimensions);
		menuBar.add(gameOptions);

		// Flags placed
		flagsPlacedLabel = new JLabel("" + (numBombs - numFlags));
		flagsPlacedLabel.setFont(NOTO_MONO);
		menuBar.add(flagsPlacedLabel);

		// Set up the field
		field = new JPanel();
		field.setBounds(0, menuBar.getHeight(), this.getContentPane().getWidth(), this.getContentPane().getHeight() - menuBar.getHeight());
		field.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		generateField();

		this.add(menuBar);
		this.add(field);
		this.setVisible(true);

		this.getRootPane().addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				// Resize the menuBar and fieldPanel
				menuBar.setSize(getContentPane().getWidth(), menuBar.getHeight());
				field.setSize(getContentPane().getWidth(), getContentPane().getHeight() - menuBar.getHeight());

				// Resize all the icons on the squares
				for (int i = 0; i < numRows; ++i) {
					for (int j = 0; j < numCols; ++j) {
						Square s = squares[i][j];
						if (s.getIsFlagged()) {
							try {
								setSquareIcon(s, "icons/flag.png");
							} catch (Exception ex) {
								System.err.println(ex);
							}
						} else if (s.isBomb() && !wonGame && gameOver) {
							try {
								setSquareIcon(s, "icons/bomb.png");
							} catch (Exception ex) {
								System.err.println(ex);
							}
						}
					}
				}
			}
		});
	}

	private void setSquareIcon(Square s, String path) throws IOException {
		BufferedImage icon = ImageIO.read(Board.class.getClassLoader().getResource(path));
		int width = icon.getWidth(), height = icon.getHeight();
		int newWidth, newHeight;
		// Scale the new width to be proportional to the height
		newHeight = s.getHeight();
		newWidth = width * newHeight / height; // Given w1/h1 = w2/h2, w2 = h2w1/h1

		int newWidthForWidth, newHeightForWidth;
		// Scale the new width to be proportional to the height
		newWidthForWidth = s.getWidth();
		newHeightForWidth = height * newWidthForWidth / width;

		// now, set the newWidth and newHeight variables to contain whichever pair has the smaller width
		if (newWidth > newWidthForWidth) {
			newWidth = newWidthForWidth;
			newHeight = newHeightForWidth;
		}

		// Add a slight pad
		newWidth -= 5;
		newHeight -= 5;

		Image newIcon = icon.getScaledInstance(newWidth, newHeight, BufferedImage.SCALE_DEFAULT);
		s.setIcon(new ImageIcon(newIcon));
	}

	private void generateField() {
		field.removeAll(); // Clear the buttons
		field.repaint();
		field.revalidate(); // I have no idea why we need to do this, we just do
		field.setLayout(new GridLayout(numRows, numCols)); // Reset the layout

		flagsPlacedLabel.setText("" + (numBombs - numFlags));

		Point[] bombLocations = new Point[numBombs];
		for (int i = 0; i < numBombs; ++i) {
			int x, y;
			x = RANDOM.nextInt(numCols);
			y = RANDOM.nextInt(numRows);

			bombLocations[i] = new Point(x, y);
			;

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
			squares[bombLocations[i].y][bombLocations[i].x].setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
		}

		// Iterate through every square and determine its number
		hasX = false;
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
					squares[i][j].setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

					// put an x on the first 0 we find, that way the user doesn't have to guess on the first click
					if (squares[i][j].getNUMBER() == 0 && !hasX) {
						hasX = true;
						squares[i][j].setForeground(Color.DARK_GRAY);
						squares[i][j].setText("<html>&times;</html>");
					}
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
				// Also we need to create final copies of i and j if we wish to use them in the anonymous class
				final int FINAL_I = i;
				final int FINAL_J = j;
				squares[i][j].addMouseListener(new MouseAdapter() {
					private final Color SQUARE_COLOR = squares[FINAL_I][FINAL_J].getBackground();

					@Override
					public void mouseClicked(MouseEvent e) {
						if (SwingUtilities.isRightMouseButton(e)) {
							Square s = (Square) e.getSource();
							if (s.getIsRevealed()) return; // Don't flag a revealed square

							if (!s.getIsFlagged() && numBombs - numFlags > 0) {
								try {
									setSquareIcon(s, "icons/flag.png");

									s.setIsFlagged(true);
									++numFlags;
									flagsPlacedLabel.setText("" + (numBombs - numFlags));
								} catch (Exception ex) {
									System.err.println(ex);
								}
							} else if (s.getIsFlagged()) {
								s.setIcon(null);
								s.setIsFlagged(false);
								--numFlags;
								flagsPlacedLabel.setText("" + (numBombs - numFlags));
							}
						}
					}

					@Override
					public void mouseEntered(MouseEvent mouseEvent) {
						if (mouseEvent.getSource() instanceof Square s) {
							s.setBackground(new Color(0xC9C9C9));
						}
					}

					@Override
					public void mouseExited(MouseEvent mouseEvent) {
						if (mouseEvent.getSource() instanceof Square s) {
							if (!s.getIsRevealed()) {
								s.setBackground(SQUARE_COLOR);
							} else {
								s.setBackground(new Color(0xFFBC5B)); // Show the revealed color if the square is revealed
							}
						}
					}
				});

				squares[i][j].setFont(NOTO_MONO_BOLD);
				squares[i][j].setFocusPainted(false); // Do not outline the text when it is focused
				field.add(squares[i][j]);
			}
		}
	}

	private void endGame(boolean won) {
		this.gameOver = true;
		this.wonGame = won;

		if (!won) {
			// Reveal all the bombs if we lost
			for (int i = 0; i < numRows; ++i) {
				for (int j = 0; j < numCols; ++j) {
					if (squares[i][j].isBomb()) {
						try {
							setSquareIcon(squares[i][j], "icons/bomb.png");
						} catch (Exception ex) {

						}
					} else {
						try {
							squares[i][j].reveal();
						} catch (BombException ex) {
							// This should never happen
						}
					}
				}
			}
		}

		// Show a popup window
		JFrame popup = new JFrame();
		popup.setTitle("You " + ((won) ? "Won" : "Lost"));
		popup.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);

		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());


		JLabel wonLabel = new JLabel(popup.getTitle() + "!");
		wonLabel.setFont(NOTO_MONO);
		wonLabel.setHorizontalAlignment(JLabel.CENTER);

		GridBagConstraints c = new GridBagConstraints();
		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 0;
		c.insets = new Insets(10, 10, 5, 10); // the bottom edge is the only one that does not face the end, so it is set to 5 instead of 10
		c.gridwidth = 2;
		panel.add(wonLabel, c);

		JButton closeButton = new JButton("Close");
		closeButton.setFont(NOTO_MONO);
		closeButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				popup.dispose();
			}
		});

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 0;
		c.gridy = 1;
		c.gridwidth = 1; // The previous gridwidth was 2, so we need to set it back to 1
		c.insets = new Insets(0, 10, 10, 5); // right is 5 because right faces a different button, the top faces the label, which already has the insets
		panel.add(closeButton, c);

		JButton newGameButton = new JButton("New Game");
		newGameButton.setFont(NOTO_MONO);
		newGameButton.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent e) {
				newGame();
				popup.dispose();
			}
		});

		c.fill = GridBagConstraints.HORIZONTAL;
		c.gridx = 1;
		c.gridy = 1;
		c.insets = new Insets(0, 0, 10, 10); // top and left already have their insets set by the other components
		panel.add(newGameButton, c);

		closeButton.setPreferredSize(newGameButton.getPreferredSize()); // Set a uniform width for the two buttons

		popup.add(panel);
		popup.pack(); // Resize the JFrame to fit its children
		popup.setLocationRelativeTo(this); // Center it in this window
		popup.setResizable(false);
		popup.setVisible(true);

		popup.requestFocus();
	}

	private void revealZeros() {
		// Keep looping to reveal every neighboring square
		for (; ; ) {
			boolean revealed = false;
			for (int i = 0; i < numRows; ++i) {
				for (int j = 0; j < numCols; ++j) {
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
						// Get the square of the current neighbor
						try {
							Square neighbor = squares[neighbors[k].y][neighbors[k].x];
							if (neighbor.getIsRevealed() && neighbor.getNUMBER() == 0
									&& !squares[i][j].getIsFlagged()
									&& !squares[i][j].getIsRevealed()) {
								// If the neighbor is a revealed 0, reveal the current square if it is not flagged
								squares[i][j].reveal();
								revealed = true;
								break; // We've already revealed this square, we can exit this loop
							}
						} catch (IndexOutOfBoundsException ex) {
							// Ignore it, keep going
						} catch (BombException ex) {
							// This should never happen
						}
					}
				}
			}

			if (!revealed) {
				break; // break if we've had a run where we never revealed anything
			}
		}
	}

	private void newGame() {
		this.firstClick = true;
		this.hasX = false;
		this.wonGame = false;
		this.gameOver = false;
		this.numFlags = 0;
		generateField();
	}

	// TODO: Make these read/write to files in a directory "saves"
	private void saveGame(int slot) {
		System.out.println("Saving game");
	}

	private static int[] getAvailableSaveSlots() {
		System.out.println("Getting games");
		return new int[]{0,3};
	}

	private void loadGame(int slot) throws ClassNotFoundException {
		System.out.println("Loading game");
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
					JFrame boardSizeFrame = new JFrame("Board Size");
					boardSizeFrame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);

					JPanel panel = new JPanel();
					panel.setLayout(new GridBagLayout());

					GridBagConstraints c = new GridBagConstraints();

					JLabel rowLabel = new JLabel("Rows:");
					rowLabel.setFont(NOTO_MONO);
					c.fill = GridBagConstraints.HORIZONTAL;
					c.gridy = 0;
					c.gridx = 0;
					c.insets = new Insets(10, 10, 5, 5);
					panel.add(rowLabel, c);

					JTextField rowField = new JTextField(8);
					rowField.setFont(NOTO_MONO);
					c.fill = GridBagConstraints.HORIZONTAL;
					c.gridy = 0;
					c.gridx = 1;
					c.insets = new Insets(10, 5, 5, 10);
					panel.add(rowField, c);

					JLabel colLabel = new JLabel("Columns:");
					colLabel.setFont(NOTO_MONO);
					c.fill = GridBagConstraints.HORIZONTAL;
					c.gridy = 1;
					c.gridx = 0;
					c.insets = new Insets(5, 10, 5, 5);
					panel.add(colLabel, c);

					JTextField colField = new JTextField(8);
					colField.setFont(NOTO_MONO);
					c.fill = GridBagConstraints.HORIZONTAL;
					c.gridy = 1;
					c.gridx = 1;
					c.insets = new Insets(5, 5, 5, 10);
					panel.add(colField, c);

					JLabel bombLabel = new JLabel("Bombs:");
					bombLabel.setFont(NOTO_MONO);
					c.fill = GridBagConstraints.HORIZONTAL;
					c.gridy = 2;
					c.gridx = 0;
					c.insets = new Insets(5, 10, 5, 5);
					panel.add(bombLabel, c);

					JTextField bombField = new JTextField(8);
					bombField.setFont(NOTO_MONO);
					c.fill = GridBagConstraints.HORIZONTAL;
					c.gridy = 2;
					c.gridx = 1;
					c.insets = new Insets(5, 5, 5, 10);
					panel.add(bombField, c);

					JButton okButton = new JButton("OK");
					okButton.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent actionEvent) {
							try {
								int rows, cols, bombs;
								rows = Integer.parseInt(rowField.getText());
								cols = Integer.parseInt(colField.getText());
								bombs = Integer.parseInt(bombField.getText());

								if (rows < 0 || cols < 0 || bombs < 0) {
									throw new Exception("A positive integer is required");
								}

								if (bombs > numRows * numCols) {
									throw new Exception("The number of bombs can not be greater than the number of cells");
								}

								numBombs = bombs;
								numRows = rows;
								numCols = cols;

								boardSizeFrame.dispose();

								getContentPane().setPreferredSize(new Dimension(numCols * DEFAULT_SQUARE_LENGTH, DEFAULT_SQUARE_LENGTH * numRows + MENU_BAR_HEIGHT));
								pack();
								newGame();

								setLocationRelativeTo(null);
							} catch (NumberFormatException ex) {
								JOptionPane.showMessageDialog(null, "A positive integer is required", "Error", JOptionPane.ERROR_MESSAGE);
							} catch (Exception ex) {
								JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
							}
						}
					});
					okButton.setFont(NOTO_MONO);
					c.fill = GridBagConstraints.HORIZONTAL;
					c.gridy = 3;
					c.gridx = 0;
					c.insets = new Insets(5, 10, 10, 5);
					panel.add(okButton, c);

					JButton cancelButton = new JButton("Cancel");
					cancelButton.addActionListener(new ActionListener() {
						@Override
						public void actionPerformed(ActionEvent actionEvent) {
							boardSizeFrame.dispose();
						}
					});
					cancelButton.setFont(NOTO_MONO);
					c.fill = GridBagConstraints.HORIZONTAL;
					c.gridy = 3;
					c.gridx = 1;
					c.insets = new Insets(5, 5, 10, 10);
					panel.add(cancelButton, c);


					boardSizeFrame.add(panel);
					boardSizeFrame.pack();
					boardSizeFrame.setLocationRelativeTo(this);
					boardSizeFrame.setResizable(false);
					boardSizeFrame.setVisible(true);

					boardSizeFrame.requestFocus();
				}
			} else if (menuText.equals("File")) {
				if (menuItemText.equals("New")) {
					newGame();
				}
			}
		} else if (e.getSource() instanceof Square s && !gameOver) {
			// If this is the first click, ensure the user only clicks on the "X", assuming that there is an X
			// Since there may be no 0s, we can only enable this limitation if there is an x on the board
			if (firstClick && hasX && !s.getText().equals("<html>&times;</html>")) {
				return;
			}

			boolean revealedSquare = false;
			firstClick = false;
			if (!s.getIsFlagged() && !s.getIsRevealed()) {
				try {
					s.reveal();
					revealedSquare = true;
				} catch (BombException ex) {
					endGame(false);
					return;
				}
			}

			// Reveal all connected zeros when a zero is clicked
			if (s.getNUMBER() == 0 && s.getIsRevealed()) {
				revealZeros();
			} else if (s.getIsRevealed() && !revealedSquare) {
				// If we click on a revealed square that was not a zero, and this square was not revealed this turn,
				// check if the square is satisfied, if it is, then reveal all the non-flagged neighbors
				int num = s.getNUMBER();
				// There can be a maximum of 8 neighbors, test them all
				// (i-1,j-1), (i-1, j ), (i-1,j+1)
				// ( i ,j-1), ( i , j ), ( i ,j+1)
				// (i+1,j-1), (i+1, j ), (i+1,j+1)

				// Find our i and j values for the current square
				int i, j = 0;
				outer:
				for (i = 0; i < numRows; ++i) {
					for (j = 0; j < numCols; ++j) {
						if (squares[i][j] == s) {
							break outer;
						}
					}
				}

				Point[] neighbors = new Point[]{
						new Point(j - 1, i - 1), new Point(j, i - 1), new Point(j + 1, i - 1),
						new Point(j - 1, i), new Point(j, i), new Point(j + 1, i),
						new Point(j - 1, i + 1), new Point(j, i + 1), new Point(j + 1, i + 1)
				};

				for (int k = 0; k < neighbors.length; ++k) {
					try {
						Square square = squares[neighbors[k].y][neighbors[k].x];
						if (square.getIsFlagged()) {
							--num;
						}
					} catch (IndexOutOfBoundsException ex) {
						// Ignore it, keep going
					}
				}

				if (num == 0) {
					// The square is satisfied
					for (int k = 0; k < neighbors.length; ++k) {
						try {
							Square square = squares[neighbors[k].y][neighbors[k].x];
							if (!square.getIsFlagged()) {
								square.reveal();
							}
						} catch (IndexOutOfBoundsException ex) {
							// Ignore it, keep going
						} catch (BombException ex) {
							endGame(false);
							return;
						}
					}
				}

				// Reveal zeros in case we revealed some zeros, we need to reveal all the zeros connected to the revealed zeros
				revealZeros();
			}


			// Now we need to determine if we won the game by checking if all the non-bombs have been clicked
			int numSquares = numRows * numCols - numBombs;
			for (int i = 0; i < numRows; ++i) {
				for (int j = 0; j < numCols; ++j) {
					if (squares[i][j].getIsRevealed()) {
						--numSquares;
					}
				}
			}
			if (numSquares == 0) {
				endGame(true);
			}
		}
	}
}
