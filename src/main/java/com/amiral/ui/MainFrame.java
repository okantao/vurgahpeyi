package com.amiral.ui;

import com.amiral.controller.AppEvent;
import com.amiral.controller.ConnectionState;
import com.amiral.controller.GameController;
import com.amiral.model.Coordinate;
import com.amiral.model.ShotResult;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.WindowConstants;

/** Main window: two boards, the side panel, the output log and the status bar. */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;
    public static final String TITLE = "BattleShip v1.0";
    static final String ABORT_QUESTION = "A game is in progress. Abort it?";

    private final transient GameController controller;
    private final BoardPanel ownBoard = new BoardPanel();
    private final BoardPanel enemyBoard = new BoardPanel();
    private final ShipAllocationPanel allocation;
    private final ConnectionPanel connection;
    private final ChatPanel chat = new ChatPanel();
    private final OutputPanel output = new OutputPanel();
    private final StatusBar statusBar = new StatusBar();
    private HistoryDialog historyDialog;
    private ConnectionState connectionState = ConnectionState.IDLE;
    private boolean gameRunning;

    public MainFrame(GameController controller, String defaultNickname) {
        super(TITLE);
        this.controller = controller;
        this.allocation = new ShipAllocationPanel(ownBoard);
        this.connection = new ConnectionPanel(controller.localIp(), defaultNickname);

        setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                exit();
            }
        });
        setMinimumSize(new Dimension(1000, 680));
        setSize(1100, 700);
        setLocationRelativeTo(null);
        setJMenuBar(buildMenu());
        setContentPane(buildContent());
        bindKeys();
        wire();
        enemyBoard.setInteractive(false);
        output.log("Welcome to " + TITLE + ". Choose server or client and place your ships.");
    }

    private JMenuBar buildMenu() {
        JMenuBar bar = new JMenuBar();
        JMenu game = new JMenu("Game");
        game.setMnemonic(KeyEvent.VK_G);
        game.add(menuItem("New Game", KeyEvent.VK_N, this::newGame));
        game.add(menuItem("History", KeyEvent.VK_H, this::showHistory));
        game.addSeparator();
        game.add(menuItem("Exit", KeyEvent.VK_Q, this::exit));
        JMenu help = new JMenu("Help");
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                TITLE + "\nTwo-player network Battleship (Amiral Batti)\nJava 17 · Swing · SQLite",
                "About", JOptionPane.INFORMATION_MESSAGE));
        help.add(about);
        bar.add(game);
        bar.add(help);
        return bar;
    }

    private static JMenuItem menuItem(String text, int key, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setAccelerator(KeyStroke.getKeyStroke(key, InputEvent.CTRL_DOWN_MASK));
        item.addActionListener(e -> action.run());
        return item;
    }

    private JPanel buildContent() {
        JPanel boards = new JPanel(new GridLayout(1, 2, Theme.GAP * 2, 0));
        boards.setBorder(BorderFactory.createEmptyBorder(Theme.GAP, Theme.GAP, Theme.GAP, Theme.GAP));
        boards.add(titled("Your ships", ownBoard));
        boards.add(titled("Your enemy", enemyBoard));

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.add(allocation);
        side.add(connection);
        side.add(chat);
        side.add(Box.createVerticalGlue());
        side.setPreferredSize(new Dimension(Theme.SIDE_PANEL_WIDTH, 0));
        for (JComponent c : List.<JComponent>of(allocation, connection)) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        }

        JPanel south = new JPanel(new BorderLayout());
        south.add(output, BorderLayout.CENTER);
        south.add(statusBar, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout());
        root.add(boards, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        return root;
    }

    private static JPanel titled(String title, BoardPanel board) {
        JPanel p = new JPanel(new BorderLayout());
        JLabel label = new JLabel(title, SwingConstants.CENTER);
        label.setFont(Theme.TITLE_FONT);
        p.add(label, BorderLayout.NORTH);
        p.add(board, BorderLayout.CENTER);
        return p;
    }

    private void bindKeys() {
        JComponent root = getRootPane();
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(KeyEvent.VK_R, 0), "rotate");
        root.getActionMap().put("rotate", new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                // Do not steal "r" from text fields.
                if (!(getFocusOwner() instanceof JTextField)) {
                    allocation.rotate();
                }
            }
        });
    }

    private void wire() {
        controller.setView(this::handle);
        allocation.setOnReady(controller::ready);
        enemyBoard.setCellClickListener(controller::fire);
        chat.setOnSend(controller::sendChat);
        connection.setActions(new ConnectionPanel.Actions() {
            @Override
            public void start(boolean serverMode, String host, int port, String nickname) {
                if (serverMode) {
                    controller.startServer(port, nickname);
                } else {
                    controller.connect(host, port, nickname);
                }
            }

            @Override
            public void stop(boolean serverMode) {
                if (confirmAbortIfRunning()) {
                    controller.stop();
                }
            }
        });
    }

    /** Asks before aborting a running game. Returns true if it is fine to go on. */
    private boolean confirmAbortIfRunning() {
        if (!controller.isGameInProgress()) {
            return true;
        }
        return JOptionPane.showConfirmDialog(this, ABORT_QUESTION, TITLE, JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }

    private void newGame() {
        if (controller.isGameInProgress()) {
            if (!confirmAbortIfRunning()) {
                return;
            }
            controller.stop();
        }
        controller.newGame();
    }

    private void showHistory() {
        if (historyDialog == null) {
            historyDialog = new HistoryDialog(this, controller);
        } else {
            historyDialog.reload();
        }
        historyDialog.setVisible(true);
        historyDialog.toFront();
    }

    private void exit() {
        if (!confirmAbortIfRunning()) {
            return;
        }
        controller.shutdown();
        dispose();
        System.exit(0);
    }

    // ---------------------------------------------------------------- events from the controller (EDT)

    /** Applies a controller event to the widgets. */
    public void handle(AppEvent e) {
        if (e instanceof AppEvent.Log log) {
            output.log(log.message());
        } else if (e instanceof AppEvent.Status s) {
            statusBar.setStatus(s.text());
        } else if (e instanceof AppEvent.ConnectionChanged c) {
            connectionState = c.state();
            connection.refreshButtons(c.state());
            boolean connected = c.state() == ConnectionState.CONNECTED || c.state() == ConnectionState.IN_GAME;
            chat.setChatEnabled(connected);
            refreshAllocation();
        } else if (e instanceof AppEvent.ClientConnected || e instanceof AppEvent.RoundReset) {
            resetRound();
        } else if (e instanceof AppEvent.ShipsReady) {
            allocation.setLocked(true);
        } else if (e instanceof AppEvent.GameStarted g) {
            gameRunning = true;
            allocation.setLocked(true);
            setEnemyTurn(g.yourTurn());
        } else if (e instanceof AppEvent.TurnChanged t) {
            setEnemyTurn(t.yourTurn());
        } else if (e instanceof AppEvent.ShotFired f) {
            paintShot(enemyBoard, f.coordinate(), f.result(), f.sunkCells());
        } else if (e instanceof AppEvent.ShotReceived r) {
            paintShot(ownBoard, r.coordinate(), r.result(), r.sunkCells());
        } else if (e instanceof AppEvent.ChatReceived m) {
            chat.appendMessage(m.sender(), m.body(), m.local());
        } else if (e instanceof AppEvent.GameOver over) {
            gameRunning = false;
            setEnemyTurn(false);
            enemyBoard.setDimmed(false);
            JOptionPane.showMessageDialog(this, over.won() ? "You won!" : "You lost!", TITLE,
                    JOptionPane.INFORMATION_MESSAGE);
        } else if (e instanceof AppEvent.Disconnected d) {
            gameRunning = false;
            setEnemyTurn(false);
            enemyBoard.setDimmed(false);
            allocation.setLocked(false);
            if (d.opponentLeft()) {
                JOptionPane.showMessageDialog(this, "Opponent has left the game.", TITLE,
                        JOptionPane.INFORMATION_MESSAGE);
            }
        } else if (e instanceof AppEvent.Error err) {
            if (err.dialog()) {
                JOptionPane.showMessageDialog(this, err.message(), TITLE, JOptionPane.ERROR_MESSAGE);
            }
        } else if (e instanceof AppEvent.ServerStarted s) {
            connection.ipField().setText(s.ip());
        }
    }

    private void resetRound() {
        gameRunning = false;
        enemyBoard.clear();
        enemyBoard.setDimmed(false);
        enemyBoard.setInteractive(false);
        allocation.setLocked(false);
        allocation.redrawBoard();
        refreshAllocation();
    }

    private void refreshAllocation() {
        allocation.setReadyAllowed(connectionState == ConnectionState.CONNECTED && !gameRunning);
    }

    private void setEnemyTurn(boolean yourTurn) {
        enemyBoard.setInteractive(yourTurn);
        enemyBoard.setDimmed(gameRunning && !yourTurn);
    }

    private static void paintShot(BoardPanel board, Coordinate c, ShotResult r, List<Coordinate> sunkCells) {
        switch (r) {
            case MISS -> board.setCell(c, BoardPanel.CellView.MISS);
            case HIT -> board.setCell(c, BoardPanel.CellView.HIT);
            case SUNK -> {
                board.setCell(c, BoardPanel.CellView.SUNK);
                sunkCells.forEach(cell -> board.setCell(cell, BoardPanel.CellView.SUNK));
            }
            default -> {
            }
        }
    }

    // Accessors for tests.

    BoardPanel ownBoard() {
        return ownBoard;
    }

    BoardPanel enemyBoard() {
        return enemyBoard;
    }

    ShipAllocationPanel allocation() {
        return allocation;
    }

    ConnectionPanel connection() {
        return connection;
    }

    ChatPanel chat() {
        return chat;
    }

    OutputPanel output() {
        return output;
    }

    StatusBar statusBar() {
        return statusBar;
    }
}
