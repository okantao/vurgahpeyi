package com.amiral;

import com.amiral.controller.GameController;
import com.amiral.controller.SocketGateway;
import com.amiral.db.Database;
import com.amiral.ui.MainFrame;
import com.amiral.util.AppLogger;
import java.sql.SQLException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Application entry point.
 * Optional first argument: JDBC URL of the database (default {@code jdbc:sqlite:data/amiral.db}).
 */
public final class App {

    public static final String TITLE = MainFrame.TITLE;
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

        ExecutorService io = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "io");
            t.setDaemon(true);
            return t;
        });
        GameController controller = new GameController(db, new SocketGateway(), SwingUtilities::invokeLater, io);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                db.close();
            } catch (SQLException e) {
                LOG.fine("Close failed: " + e);
            }
        }));
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (ReflectiveOperationException | javax.swing.UnsupportedLookAndFeelException e) {
                LOG.fine("Default look and feel kept: " + e);
            }
            new MainFrame(controller, defaultNickname()).setVisible(true);
        });
    }

    /** OS user name, cut to the 20 characters the database allows. */
    static String defaultNickname() {
        String name = System.getProperty("user.name", "Player").trim();
        if (name.isEmpty()) {
            name = "Player";
        }
        return name.length() > 20 ? name.substring(0, 20) : name;
    }
}
