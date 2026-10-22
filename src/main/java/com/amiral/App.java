package com.amiral;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Application entry point. */
public final class App {

    public static final String TITLE = "BattleShip v1.0";

    private App() {
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(TITLE);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1100, 700);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
