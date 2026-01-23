package gui;

import exceptions.BombException;

import javax.imageio.ImageIO;
import javax.security.auth.login.AccountNotFoundException;
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Random;

public class Board extends JFrame implements ActionListener {
	public static final Font NOTO_MONO;
	public static final Font NOTO_MONO_BOLD;
	public static final FontMetrics NOTO_MONO_METRICS;
	public static final int MENU_BAR_HEIGHT;
	public static final int MAX_SAVE_SLOTS = 4;

	private static final String CONFIG_DIR;
	private static final String SAVE_DIR = "minesweeperSaves";

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

		// If there is no .config dir in the user.home directory, we need to create it
		String home = System.getProperty("user.home");
		File config = new File(Paths.get(home, ".config").toString());

		String str = config.getAbsolutePath();

		if (!config.exists()) {
			if (!config.mkdir()) {
				JOptionPane.showMessageDialog(null, "Could not create directory \"" + config.getAbsolutePath() + "\"\nSaving and loading will be disabled", "Error", JOptionPane.ERROR_MESSAGE);
				str = null;
			}
		} else if (!config.isDirectory()) {
			JOptionPane.showMessageDialog(null, "Could not create directory \"" + config.getAbsolutePath() + "\" because it exists, and it is a file\nSaving and loading will be disabled", "Error", JOptionPane.ERROR_MESSAGE);
			str = null;
		}

		CONFIG_DIR = str;
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


		// Use invokeAndWait because then when we go to use the height/width of the contentPane, it will actually be the right height/width
		try {
			SwingUtilities.invokeAndWait(new Runnable() {
				@Override
				public void run() {
					// Set the content pane's preferred size because then it will automatically account for the title bar and whatnot
					getContentPane().setPreferredSize(new Dimension(numCols * DEFAULT_SQUARE_LENGTH, DEFAULT_SQUARE_LENGTH * numRows + MENU_BAR_HEIGHT));
					pack(); // resize the frame to fit the components (the content pane)

					setTitle("Minesweeper");
					setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
					setLayout(null);
					setLocationRelativeTo(null); // Center the window
				}
			});
		} catch (Exception ex) {

		}

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
						int result = JOptionPane.showConfirmDialog(null, "Overwrite save slot?", "Overwrite Save Slot", JOptionPane.YES_NO_OPTION);
						if (result != JOptionPane.YES_OPTION) return;
					}

					saveGame(FINAL_I);
				}
			});

			item.setText("Slot " + i + ": " + ((available) ? " Available" : " In Use"));

			submenu.add(item);
		}
		fileOptions.add(submenu);

		submenu = new JMenu("Load");
		submenu.setFont(NOTO_MONO);
		for (int i = 0; i < MAX_SAVE_SLOTS; ++i) {
			JMenuItem item = new JMenuItem();
			item.setFont(NOTO_MONO);

			boolean hasGame = true;
			for (int j = 0; j < availableSaveSlots.length; ++j) {
				if (availableSaveSlots[j] == i) {
					hasGame = false;
					break;
				}
			}

			final int FINAL_I = i;
			final boolean FINAL_HAS_GAME = hasGame;
			item.addActionListener(new ActionListener() {
				@Override
				public void actionPerformed(ActionEvent actionEvent) {
					if (!FINAL_HAS_GAME) {
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

			item.setText("Slot " + i + ": " + ((hasGame) ? " Has Game" : " Empty"));

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
		field.setBounds(0, MENU_BAR_HEIGHT, this.getContentPane().getWidth(), this.getContentPane().getHeight() - MENU_BAR_HEIGHT);
		field.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		generateField();

		this.add(menuBar);
		this.add(field);
		this.setVisible(true);

		this.getRootPane().addComponentListener(new ComponentAdapter() {
			@Override
			public void componentResized(ComponentEvent e) {
				// Resize the menuBar and fieldPanel
				menuBar.setSize(getContentPane().getWidth(), MENU_BAR_HEIGHT);
				field.setSize(getContentPane().getWidth(), getContentPane().getHeight() - MENU_BAR_HEIGHT);

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
						if (gameOver) return;
						Square s = (Square) e.getSource();
						if (SwingUtilities.isRightMouseButton(e) && !s.getIsRevealed()) {
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
						} else if (!SwingUtilities.isLeftMouseButton(e) && s.getIsRevealed()) {
							// If it is not the left mouse button, but the square is already revealed,
							// auto-reveal the neighbors, we can do this by just calling our actionPerformed
							// We excluded left mouse button because those events are picked up by actionPerformed
							actionPerformed(new ActionEvent(e.getSource(), 0, "command"));
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

						if (squares[i][j].getIsFlagged()) {
							// Underline squares that were flagged, but were not bombs
							squares[i][j].setIcon(null);
							squares[i][j].setText("<html><u><b>" + squares[i][j].getNUMBER() + "</b></u></html>");
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

		Window[] windows = Window.getWindows();
		for (int i = 0; i < windows.length; ++i) {
			if (windows[i] != this) windows[i].dispose();
		}

		generateField();
	}

	private String getGameAsString() {
		// Spec:
		// line 0: num rows
		// line 1: num cols
		// line 2: num bombs
		// rest: each individual square containing the number, 'r' for revealed, and 'f' for flagged, the order will be left to right, top to bottom
		// Example of save file:
		/*
		4
		4
		4
		0r
		1r
		2
		22
		0r
		2r
		-1f
		-1f
		1r
		3r
		-1f
		3
		-1
		2
		1
		1
		 */

		StringBuilder str = new StringBuilder();
		str.append(String.format("%d\n%d\n%d\n", numRows, numCols, numBombs));
		for (int i = 0; i < numRows * numCols; ++i) {
			int row = i / numCols;
			int col = i % numCols;
			Square s = squares[row][col];

			str.append(s.getNUMBER());
			if (s.getIsFlagged()) {
				str.append('f');
			} else if (s.getIsRevealed()) {
				str.append('r');
			}

			str.append('\n');
		}
		return str.toString();
	}

	// TODO: Make these read/write to files in a directory "saves"
	private void saveGame(int slot) {
		if (slot < 0 || slot > 3) return;
		if (CONFIG_DIR == null) {
			JOptionPane.showMessageDialog(null, "Saving and loading is disabled", "Saving and loading disabled", JOptionPane.WARNING_MESSAGE);
			return;
		}

		if (gameOver) {
			JOptionPane.showMessageDialog(null, "The game is over, it can not be saved", "Game over", JOptionPane.WARNING_MESSAGE);
			return;
		}

		File saveDir = new File(Paths.get(CONFIG_DIR, SAVE_DIR).toString());
		if (saveDir.exists() && !saveDir.isDirectory()) {
			// Remove it
			int res = JOptionPane.showConfirmDialog(null, "A file called \"" + SAVE_DIR + "\" exists and is not a directory, delete it?", "File Exists", JOptionPane.YES_NO_OPTION);
			if (res != JOptionPane.YES_OPTION) {
				return;
			}

			try {
				if (!saveDir.delete()) {
					JOptionPane.showMessageDialog(null, "The file was not deleted", "Error", JOptionPane.ERROR_MESSAGE);
					return;
				}
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			}
		}


		if (!saveDir.exists() && !saveDir.mkdir()) {
			JOptionPane.showMessageDialog(null, "The directory was not created", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		File save = new File(Paths.get(CONFIG_DIR, SAVE_DIR, slot + ".txt").toString());

		// Delete the file if it does exist
		if (save.exists() && !save.delete()) {
			JOptionPane.showMessageDialog(null, "The existing save file could not be deleted", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		try {
			if (!save.createNewFile()) {
				JOptionPane.showMessageDialog(null, "The save file was not created", "Error", JOptionPane.ERROR_MESSAGE);
			}
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}

		try {
			FileWriter fw = new FileWriter(save, false);
			PrintWriter pw = new PrintWriter(fw);
			pw.write(getGameAsString());

			pw.close();
			fw.close();
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
		}


		// re-create the fileOptions menu
		JMenu fileOptions = null;

		for (int i = 0; i < menuBar.getMenuCount(); ++i) {
			if (menuBar.getMenu(i).getText().equals("File")) {
				fileOptions = menuBar.getMenu(i);
				break;
			}
		}

		fileOptions.removeAll();


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
						int result = JOptionPane.showConfirmDialog(null, "Overwrite save slot?", "Overwrite Save Slot", JOptionPane.YES_NO_OPTION);
						if (result != JOptionPane.YES_OPTION) return;
					}

					saveGame(FINAL_I);
				}
			});

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

			item.setText("Slot " + i + ": " + ((empty) ? " Has Game" : " Empty"));

			submenu.add(item);
		}
		fileOptions.add(submenu);
	}

	private static int[] getAvailableSaveSlots() {
		File[] possibleSaves = new File[4];
		for (int i = 0; i < 4; ++i) {
			possibleSaves[i] = new File(Paths.get(CONFIG_DIR, SAVE_DIR, i + ".txt").toString());
		}

		ArrayList<File> nonExistingSaves = new ArrayList<>();
		for (int i = 0; i < possibleSaves.length; ++i) {
			if (!possibleSaves[i].exists()) nonExistingSaves.add(possibleSaves[i]);
		}

		int[] availableSlots = new int[nonExistingSaves.size()];
		for (int i = 0; i < availableSlots.length; ++i) {
			try {
				availableSlots[i] = Integer.parseInt(String.valueOf(nonExistingSaves.get(i).getName().charAt(0)));
			} catch (NumberFormatException ex) {

			}
		}


		return availableSlots;
	}

	private void loadGame(int slot) throws ClassNotFoundException {
		if (slot < 0 || slot > 3) return;
		if (CONFIG_DIR == null) {
			JOptionPane.showMessageDialog(null, "Saving and loading is disabled", "Warning", JOptionPane.WARNING_MESSAGE);
			return;
		}

		Window[] windows = Window.getWindows();
		for (int i = 0; i < windows.length; ++i) {
			if (windows[i] != this) windows[i].dispose();
		}

		File saveFile = new File(Paths.get(CONFIG_DIR, "minesweeperSaves", slot + ".txt").toString());

		if (!saveFile.exists()) {
			JOptionPane.showMessageDialog(null, "The file \"" + saveFile.getAbsolutePath() + "\"" + " does not exist", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		ArrayList<String> save;

		try {
			FileReader fr = new FileReader(saveFile);
			BufferedReader br = new BufferedReader(fr);

			save = new ArrayList<>(br.readAllLines());

			br.close();
			fr.close();
		} catch (IOException ex) {
			JOptionPane.showMessageDialog(null, ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		boolean isValid = true;
		Square[][] newSquares = new Square[0][0];

		int newNumRows = 0;
		int newNumCols = 0;
		int newNumBombs = 0;

		try {
			newNumRows = Integer.parseInt(save.get(0));
			newNumCols = Integer.parseInt(save.get(1));
			newNumBombs = Integer.parseInt(save.get(2));

			if (newNumRows <= 0 || newNumCols <= 0 || newNumBombs <= 0 || newNumBombs >= newNumCols * newNumRows) {
				throw new Exception();
			}

			int expectedNumLines = 3 + newNumCols * newNumRows;
			if (save.size() != expectedNumLines) {
				throw new Exception();
			}

			newSquares = new Square[newNumRows][newNumCols];
			for (int i = 3; i < expectedNumLines; ++i) {
				String line = save.get(i);
				if (line.isEmpty()) break;
				Square square;

				int squareNum;
				boolean isFlagged = false;
				boolean isRevealed = false;

				if (!isNumber(line)) {
					// if the line is not a number, it must contain either r or f
					if (!line.contains("r") && !line.contains("f")) throw new Exception();

					// Now, get the number portion
					if (line.startsWith("-")) {
						squareNum = -1;
						line = line.substring(2);
					} else {
						// Since it can only be
						squareNum = Integer.parseInt(String.valueOf(line.charAt(0)));
						line = line.substring(1);
					}

					isRevealed = line.contains("r");
					isFlagged = line.contains("f");

					if (isFlagged && isRevealed) throw new Exception(); // A square can not be both flagged and revealed
				} else {
					squareNum = Integer.parseInt(line);
				}

				square = new Square(squareNum);
				square.setIsFlagged(isFlagged);

				if (isRevealed) square.reveal(); // This will also handle the BombException


				if (squareNum < -1 || squareNum > 8) {
					throw new Exception(); // the number is not valid
				}

				newSquares[(i - 3) / newNumCols][(i - 3) % newNumCols] = square;
			}
		} catch (Exception ex) {
			isValid = false;
		}

		for (int i = 0; i < newNumRows; ++i) {
			for (int j = 0; j < newNumCols; ++j) {
				System.out.print(newSquares[i][j].getNUMBER());
				System.out.print(" ");
			}
			System.out.println();
		}

		if (isValid) {
			// Now, check to make sure for every square, the number of neighbor bombs match the number shown
			for (int i = 0; i < newNumRows; ++i) {
				for (int j = 0; j < newNumCols; ++j) {
					if (newSquares[i][j].isBomb()) continue;

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
							if (newSquares[neighbors[k].y][neighbors[k].x].isBomb()) {
								++numNeighborBombs;
							}
						} catch (Exception ex) {
							// Ignore it, keep going
						}
					}

					if (numNeighborBombs != newSquares[i][j].getNUMBER()) {
						isValid = false;

						System.out.println("no match");
						System.out.println(i + " " + j);

					}
				}
			}
		}

		if (!isValid) {
			JOptionPane.showMessageDialog(null, "Save file is invalid", "Error", JOptionPane.ERROR_MESSAGE);
			return;
		}

		getContentPane().setPreferredSize(new Dimension(newNumCols * DEFAULT_SQUARE_LENGTH, DEFAULT_SQUARE_LENGTH * newNumRows + MENU_BAR_HEIGHT));
		pack();


		final int FINAL_NEW_NUM_BOMBS = newNumBombs;
		final int FINAL_NEW_NUM_ROWS = newNumRows;
		final int FINAL_NEW_NUM_COLS = newNumCols;
		final Square[][] FINAL_NEW_SQUARES = newSquares;

		firstClick = false;
		hasX = false;
		gameOver = false;
		wonGame = false;


		SwingUtilities.invokeLater(new Runnable() {
			@Override
			public void run() {
				numBombs = FINAL_NEW_NUM_BOMBS;
				numRows = FINAL_NEW_NUM_ROWS;
				numCols = FINAL_NEW_NUM_COLS;
				squares = FINAL_NEW_SQUARES;

				// Now create the new field
				field.removeAll(); // Clear the buttons
				field.repaint();
				field.revalidate(); // I have no idea why we need to do this, we just do
				field.setLayout(new GridLayout(numRows, numCols)); // Reset the layout


				for (int i = 0; i < numRows; ++i) {
					for (int j = 0; j < numCols; ++j) {
						squares[i][j].setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));

						// checkerboard pattern
						if ((i + j) % 2 == 0 && !squares[i][j].getIsRevealed()) {
							squares[i][j].setBackground(new Color(0x1B8300)); // darker green
						} else if (!squares[i][j].getIsRevealed()) {
							squares[i][j].setBackground(new Color(0x25B500)); // lighter green
						}

						squares[i][j].addActionListener(Board.this);

						// For some stupid reason, actionPerformed doesn't get invoked when right click, so we have to manually do this
						// Also we need to create final copies of i and j if we wish to use them in the anonymous class
						final int FINAL_I = i;
						final int FINAL_J = j;
						squares[i][j].addMouseListener(new MouseAdapter() {
							private final Color SQUARE_COLOR = squares[FINAL_I][FINAL_J].getBackground();

							@Override
							public void mouseClicked(MouseEvent e) {
								if (gameOver) return;
								Square s = (Square) e.getSource();
								if (SwingUtilities.isRightMouseButton(e) && !s.getIsRevealed()) {
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
								} else if (!SwingUtilities.isLeftMouseButton(e) && s.getIsRevealed()) {
									// If it is not the left mouse button, but the square is already revealed,
									// auto-reveal the neighbors, we can do this by just calling our actionPerformed
									// We excluded left mouse button because those events are picked up by actionPerformed
									Board.this.actionPerformed(new ActionEvent(e.getSource(), 0, "command"));
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

				SwingUtilities.invokeLater(new Runnable() {
					@Override
					public void run() {
						for (int i = 0; i < numRows; ++i) {
							for (int j = 0; j < numCols; ++j) {
								if (squares[i][j].getIsFlagged()) {
									try {
										setSquareIcon(squares[i][j], "icons/flag.png");
									} catch (IOException e) {

									}
								}
							}
						}
					}
				});
			}
		});


	}

	private boolean isNumber(final String STR) {
		int i = 0;

		if (STR.startsWith("-")) i = 1;

		try {
			for (; i < STR.length(); ++i) {
				if (!Character.isDigit(STR.charAt(i))) return false;
			}
		} catch (ArrayIndexOutOfBoundsException ex) {
			// This will happen if STR is "-"
			return false;
		}
		return true;
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


								getContentPane().setPreferredSize(new Dimension(cols * DEFAULT_SQUARE_LENGTH, DEFAULT_SQUARE_LENGTH * rows + MENU_BAR_HEIGHT));
								pack();

								// invokeLater will wait for all the events to be processed before executing doRun.run()
								SwingUtilities.invokeLater(new Runnable() {
									@Override
									public void run() {

										numBombs = bombs;
										numCols = cols;
										numRows = rows;

										newGame();
										setLocationRelativeTo(null);
										boardSizeFrame.dispose();
									}
								});

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

				if (num <= 0) {
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
