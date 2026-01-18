package main;

public class Main {
	static void main(String[] args) {
		System.setProperty("awt.useSystemAAFontSettings", "on"); // anti-aliased text
		new gui.Board(10, 10, 10);
	}
}
