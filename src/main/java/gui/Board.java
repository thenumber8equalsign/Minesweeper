package gui;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.ActionEvent;
import java.io.IOException;

public class Board extends JFrame implements ActionListener {
	public static final Font NOTO_MONO;
	public static final FontMetrics NOTO_MONO_METRICS;

	static {
		try {
			NOTO_MONO = Font.createFont(Font.TRUETYPE_FONT,
					Board.class.getClassLoader().getResourceAsStream("fonts/notoMono.ttf")).deriveFont(20f);
			NOTO_MONO_METRICS = new Canvas().getFontMetrics(NOTO_MONO);
		} catch (FontFormatException | IOException e) {
			throw new RuntimeException(e);
		}
	}

	private int numRows;
	private int numCols;
	private int numBombs;

	private Square[][] squares;
	private JMenuBar menuBar;
	private JPanel field;

	public Board(int rows, int cols, int bombs) {
		this.setBounds(0, 0, 800, 600);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLayout(null);
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setLocationRelativeTo(null); // Center the window


		menuBar = new JMenuBar();
		menuBar.setFont(NOTO_MONO);
		menuBar.setBounds(0, 0,800, NOTO_MONO_METRICS.getHeight() + 6);

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

		// Iterate over each bomb, and randomly determine coordinates for each bomb

		this.add(menuBar);
		this.add(field);
		this.setVisible(true);
	}

	@Override
	public void actionPerformed(ActionEvent e) {
		System.out.println(e.getSource().getClass());
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
			}
		}
	}
}
