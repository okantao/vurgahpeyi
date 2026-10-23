package com.amiral.ui;

import com.amiral.controller.AppEvent;
import com.amiral.controller.ConnectionState;
import com.amiral.controller.GameController;
import com.amiral.controller.GameMode;
import com.amiral.model.ComputerPlayer;
import com.amiral.model.Coordinate;
import com.amiral.model.ShipType;
import com.amiral.model.ShotOutcome;
import com.amiral.model.ShotResult;
import com.amiral.model.Weapon;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
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
import javax.swing.Timer;
import javax.swing.WindowConstants;

/** Main window: header with the turn banner, two boards, the side panel, the output log and the status bar. */
public class MainFrame extends JFrame {

    private static final long serialVersionUID = 1L;
    public static final String TITLE = "Vurgahpeyi v1.0";
    static final String ABORT_QUESTION = "A game is in progress. Abort it?";

    private final transient GameController controller;
    private final BoardPanel ownBoard = new BoardPanel();
    private final BoardPanel enemyBoard = new BoardPanel();
    private final ShipAllocationPanel allocation;
    private final ConnectionPanel connection;
    private final ChatPanel chat = new ChatPanel();
    private final OutputPanel output = new OutputPanel();
    private final StatusBar statusBar = new StatusBar();
    private final TurnBanner banner = new TurnBanner();
    private final WeaponPanel weapons = new WeaponPanel();
    private final FleetStatusPanel ownFleet = new FleetStatusPanel();
    private final FleetStatusPanel enemyFleet = new FleetStatusPanel();
    private final JLabel opponentLabel = new JLabel(" ", SwingConstants.RIGHT);
    private HistoryDialog historyDialog;
    private ConnectionState connectionState = ConnectionState.IDLE;
    private boolean gameRunning;
    private boolean yourTurn;

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
        setMinimumSize(new Dimension(1120, 720));
        setSize(1320, 820);
        setLocationRelativeTo(null);
        setJMenuBar(buildMenu());
        setContentPane(buildContent());
        bindKeys();
        wire();
        enemyBoard.setInteractive(false);
        weapons.setActive(false);
        output.log("Welcome to " + TITLE + ". Choose server, client or computer, then place your ships.");
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
        JMenuItem rules = new JMenuItem("How to play");
        rules.addActionListener(e -> JOptionPane.showMessageDialog(this, """
                Place 5 ships, press Ready. The server (or you, against the computer) moves first.
                On your turn choose a weapon under "Your enemy" and click a cell:
                  1  Shot     - one cell, unlimited
                  2  Radar    - shows enemy ship cells in a 4x4 area (3 per game)
                  3  Torpedo  - hits every cell of a 3x3 area (2 per game)
                A hit lets you move again; a miss or a radar scan ends your turn.
                Sink all 17 enemy ship cells to win.
                R rotates a ship while placing; right click removes it.""", "How to play",
                JOptionPane.INFORMATION_MESSAGE));
        JMenuItem about = new JMenuItem("About");
        about.addActionListener(e -> JOptionPane.showMessageDialog(this,
                TITLE + "\nTwo-player network Battleship (Amiral Batti)\nwith radar, torpedoes and a computer opponent"
                        + "\nJava 17 · Swing · SQLite",
                "About", JOptionPane.INFORMATION_MESSAGE));
        help.add(rules);
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
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(Theme.HEADER);
        header.setBorder(BorderFactory.createEmptyBorder(6, 14, 6, 14));
        JLabel logo = new JLabel("VURGAHPEYI");
        logo.setFont(Theme.LOGO_FONT);
        logo.setForeground(Theme.ACCENT);
        logo.setPreferredSize(new Dimension(220, 36));
        opponentLabel.setFont(Theme.LABEL_FONT);
        opponentLabel.setForeground(Theme.TEXT_DIM);
        opponentLabel.setPreferredSize(new Dimension(220, 36));
        header.add(logo, BorderLayout.WEST);
        header.add(banner, BorderLayout.CENTER);
        header.add(opponentLabel, BorderLayout.EAST);

        JPanel enemyBottom = new JPanel();
        enemyBottom.setLayout(new BoxLayout(enemyBottom, BoxLayout.Y_AXIS));
        weapons.setAlignmentX(CENTER_ALIGNMENT);
        enemyFleet.setAlignmentX(CENTER_ALIGNMENT);
        enemyBottom.add(enemyFleet);
        enemyBottom.add(weapons);
        Component[] own = {boardTitle("Your ships"), ownBoard, ownFleet};
        Component[] enemy = {boardTitle("Your enemy"), enemyBoard, enemyBottom};
        JPanel boards = new JPanel(new BoardsLayout(own, enemy));
        for (Component c : own) {
            boards.add(c);
        }
        for (Component c : enemy) {
            boards.add(c);
        }

        JPanel side = new JPanel();
        side.setLayout(new BoxLayout(side, BoxLayout.Y_AXIS));
        side.setBorder(BorderFactory.createEmptyBorder(Theme.GAP, 0, 0, Theme.GAP));
        side.add(allocation);
        side.add(connection);
        side.add(chat);
        side.setPreferredSize(new Dimension(Theme.SIDE_PANEL_WIDTH, 0));
        for (JComponent c : List.<JComponent>of(allocation, connection)) {
            c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height));
        }

        JPanel south = new JPanel(new BorderLayout());
        south.add(output, BorderLayout.CENTER);
        south.add(statusBar, BorderLayout.SOUTH);

        JPanel root = new JPanel(new BorderLayout());
        root.add(header, BorderLayout.NORTH);
        root.add(boards, BorderLayout.CENTER);
        root.add(side, BorderLayout.EAST);
        root.add(south, BorderLayout.SOUTH);
        return root;
    }

    private static JLabel boardTitle(String title) {
        JLabel label = new JLabel(title, SwingConstants.CENTER);
        label.setFont(Theme.TITLE_FONT);
        label.setForeground(Theme.TEXT);
        return label;
    }

    private void bindKeys() {
        JComponent root = getRootPane();
        bindKey(root, KeyEvent.VK_R, "rotate", allocation::rotate);
        bindKey(root, KeyEvent.VK_1, "weapon-shot", () -> weapons.select(Weapon.SHOT));
        bindKey(root, KeyEvent.VK_2, "weapon-radar", () -> weapons.select(Weapon.RADAR));
        bindKey(root, KeyEvent.VK_3, "weapon-torpedo", () -> weapons.select(Weapon.TORPEDO));
    }

    private void bindKey(JComponent root, int key, String name, Runnable action) {
        root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke(key, 0), name);
        root.getActionMap().put(name, new AbstractAction() {
            private static final long serialVersionUID = 1L;

            @Override
            public void actionPerformed(ActionEvent e) {
                // Do not steal keys from text fields.
                if (!(getFocusOwner() instanceof JTextField)) {
                    action.run();
                }
            }
        });
    }

    private void wire() {
        controller.setView(this::handle);
        allocation.setOnReady(controller::ready);
        enemyBoard.setCellClickListener(this::fireSelectedWeapon);
        enemyBoard.setHoverListener(c -> showWeaponPreview());
        weapons.setOnChange(w -> showWeaponPreview());
        chat.setOnSend(controller::sendChat);
        connection.setActions(new ConnectionPanel.Actions() {
            @Override
            public void start(GameMode mode, String host, int port, String nickname,
                    ComputerPlayer.Difficulty difficulty) {
                switch (mode) {
                    case SERVER -> controller.startServer(port, nickname);
                    case CLIENT -> controller.connect(host, port, nickname);
                    case AI -> controller.playComputer(nickname, difficulty);
                }
            }

            @Override
            public void stop(GameMode mode) {
                if (confirmAbortIfRunning()) {
                    controller.stop();
                }
            }
        });
    }

    private void fireSelectedWeapon(Coordinate c) {
        Weapon w = weapons.selected();
        controller.act(w, c);
        if (w != Weapon.SHOT) {
            weapons.select(Weapon.SHOT);
        }
    }

    private void showWeaponPreview() {
        Coordinate c = enemyBoard.hoverCell();
        Weapon w = weapons.selected();
        if (c == null || !enemyBoard.isInteractive() || w == Weapon.SHOT) {
            enemyBoard.setPreview(List.of(), true, Weapon.SHOT);
        } else {
            enemyBoard.setPreview(w.area(c), true, w);
        }
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
            banner.setMode(bannerMode(s.text()));
        } else if (e instanceof AppEvent.ConnectionChanged c) {
            connectionState = c.state();
            connection.refreshButtons(c.state());
            boolean connected = c.state() == ConnectionState.CONNECTED || c.state() == ConnectionState.IN_GAME;
            chat.setChatEnabled(connected);
            if (c.state() == ConnectionState.IDLE) {
                opponentLabel.setText(" ");
            }
            refreshAllocation();
        } else if (e instanceof AppEvent.ClientConnected cc) {
            opponentLabel.setText("vs " + cc.nickname());
            resetRound();
        } else if (e instanceof AppEvent.RoundReset) {
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
            showShot(enemyBoard, enemyFleet, f.coordinate(), f.result(), f.sunkType(), f.sunkCells(), true);
        } else if (e instanceof AppEvent.ShotReceived r) {
            showShot(ownBoard, ownFleet, r.coordinate(), r.result(), r.sunkType(), r.sunkCells(), false);
        } else if (e instanceof AppEvent.RadarScan scan) {
            BoardPanel board = scan.mine() ? enemyBoard : ownBoard;
            Map<Coordinate, Long> delays = board.playRadar(scan.area(), scan.shipCells());
            board.markScanned(scan.area());
            if (scan.mine()) {
                board.markDetected(delays);
            }
        } else if (e instanceof AppEvent.TorpedoStrike strike) {
            showTorpedo(strike);
        } else if (e instanceof AppEvent.AbilitiesChanged a) {
            weapons.setCounts(a.myRadars(), a.myTorpedoes(), a.enemyRadars(), a.enemyTorpedoes());
        } else if (e instanceof AppEvent.ChatReceived m) {
            chat.appendMessage(m.sender(), m.body(), m.local());
        } else if (e instanceof AppEvent.GameOver over) {
            gameRunning = false;
            setEnemyTurn(false);
            enemyBoard.setDimmed(false);
            later(1400, () -> JOptionPane.showMessageDialog(this, over.won() ? "Victory! You won!" : "Defeat. You lost!",
                    TITLE, JOptionPane.INFORMATION_MESSAGE));
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

    private static TurnBanner.Mode bannerMode(String status) {
        return switch (status) {
            case GameController.STATUS_YOUR_TURN -> TurnBanner.Mode.YOUR_TURN;
            case GameController.STATUS_ENEMY_TURN -> TurnBanner.Mode.ENEMY_TURN;
            case GameController.STATUS_WON -> TurnBanner.Mode.WON;
            case GameController.STATUS_LOST -> TurnBanner.Mode.LOST;
            case GameController.STATUS_PLACING -> TurnBanner.Mode.PLACING;
            case GameController.STATUS_WAITING -> TurnBanner.Mode.WAITING;
            default -> TurnBanner.Mode.IDLE;
        };
    }

    private static void later(int millis, Runnable r) {
        Timer t = new Timer(millis, ev -> r.run());
        t.setRepeats(false);
        t.start();
    }

    private void resetRound() {
        gameRunning = false;
        enemyBoard.clear();
        enemyBoard.setDimmed(false);
        enemyBoard.setInteractive(false);
        enemyBoard.setHighlight(false);
        ownFleet.reset();
        enemyFleet.reset();
        weapons.reset();
        weapons.setActive(false);
        allocation.setLocked(false);
        allocation.redrawBoard();
        refreshAllocation();
    }

    private void refreshAllocation() {
        allocation.setReadyAllowed(connectionState == ConnectionState.CONNECTED && !gameRunning);
    }

    private void setEnemyTurn(boolean turn) {
        this.yourTurn = turn && gameRunning;
        enemyBoard.setInteractive(yourTurn);
        enemyBoard.setDimmed(gameRunning && !yourTurn);
        enemyBoard.setHighlight(yourTurn);
        weapons.setActive(yourTurn);
        showWeaponPreview();
    }

    private static BoardPanel.CellView view(ShotResult r) {
        return switch (r) {
            case HIT -> BoardPanel.CellView.HIT;
            case SUNK -> BoardPanel.CellView.SUNK;
            default -> BoardPanel.CellView.MISS;
        };
    }

    private static void showShot(BoardPanel board, FleetStatusPanel fleet, Coordinate c, ShotResult r,
            ShipType sunkType, List<Coordinate> sunkCells, boolean enemyBoard) {
        long delay = BoardPanel.shotImpactDelay();
        board.playShot(c, r.isHit());
        board.setCell(c, view(r), delay);
        if (r == ShotResult.SUNK) {
            sunkCells.forEach(cell -> board.setCell(cell, BoardPanel.CellView.SUNK, delay));
            if (enemyBoard && sunkType != null) {
                board.addShip(sunkType, sunkCells);
            }
            if (sunkType != null) {
                fleet.setSunk(sunkType);
            }
        }
    }

    private void showTorpedo(AppEvent.TorpedoStrike strike) {
        BoardPanel board = strike.mine() ? enemyBoard : ownBoard;
        FleetStatusPanel fleet = strike.mine() ? enemyFleet : ownFleet;
        board.playTorpedo(strike.area(), strike.anchor());
        long delay = BoardPanel.torpedoImpactDelay() + 120;
        for (ShotOutcome o : strike.outcomes()) {
            board.setCell(o.coordinate(), view(o.result()), delay);
        }
        for (ShotOutcome o : strike.outcomes()) {
            if (o.result() == ShotResult.SUNK && o.sunkType() != null) {
                o.sunkCells().forEach(cell -> board.setCell(cell, BoardPanel.CellView.SUNK, delay));
                if (strike.mine()) {
                    board.addShip(o.sunkType(), o.sunkCells());
                }
                fleet.setSunk(o.sunkType());
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

    WeaponPanel weapons() {
        return weapons;
    }
}
