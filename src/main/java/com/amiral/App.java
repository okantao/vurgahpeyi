package com.amiral;

import com.amiral.db.Database;
import com.amiral.util.AppLogger;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Application entry point. Optional first argument: JDBC URL of the database. */
public final class App {

    public static final String TITLE = "BattleShip v1.0";
    private static final Logger LOG = AppLogger.get(App.class);

    private App() {
    }

    public static void main(String[] args) {
        String url = args.length > 0 ? args[0] : Database.DEFAULT_URL;
        Database db;
        try {
            db = Database.open(url);
            db.initSchema();
        } catch (SQLException e) {
            LOG.log(Level.SEVERE, "Cannot open database " + url, e);
            JOptionPane.showMessageDialog(null, "Cannot open database:\n" + e.getMessage(), TITLE,
                    JOptionPane.ERROR_MESSAGE);
            System.exit(1);
            return;
        }
        LOG.info("Database ready: " + url);
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame(TITLE);
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(1100, 700);
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }
}
