package com.amiral.controller;

import com.amiral.db.ChatRepository;
import com.amiral.db.Database;
import com.amiral.db.GameRepository;
import com.amiral.db.LogRepository;
import com.amiral.db.MoveRepository;
import com.amiral.db.PlayerRepository;
import com.amiral.db.ShipPlacementRepository;
import com.amiral.db.StatsRepository;
import com.amiral.model.Board;
import com.amiral.model.Coordinate;
import com.amiral.model.GameEngine;
import com.amiral.model.GameRuleException;
import com.amiral.model.GameState;
import com.amiral.model.RuleViolation;
import com.amiral.model.ShipType;
import com.amiral.model.ShotOutcome;
import com.amiral.model.ShotResult;
import com.amiral.net.Message;
import com.amiral.net.NetworkListener;
import com.amiral.net.ProtocolCodec;
import com.amiral.net.ProtocolException;
import com.amiral.util.AppLogger;
import java.io.IOException;
import java.net.BindException;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.logging.Logger;

/**
 * Bridge between the game engine, the network, the database and the UI.
 * UI actions and network callbacks are serialised with one lock; events reach the UI through {@code uiExecutor}.
 */
public class GameController implements NetworkListener, HistoryProvider {

    public static final String STATUS_IDLE = "Idle";
    public static final String STATUS_WAITING = "Waiting for opponent...";
    public static final String STATUS_PLACING = "Placing ships";
    public static final String STATUS_YOUR_TURN = "Your turn";
    public static final String STATUS_ENEMY_TURN = "Enemy's turn";
    public static final String STATUS_WON = "Game over: you won";
    public static final String STATUS_LOST = "Game over: you lost";
    public static final String STATUS_DISCONNECTED = "Disconnected";

    static final DateTimeFormatter DB_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    static final DateTimeFormatter UI_TIME = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss");
    private static final Set<RuleViolation> SHOT_ERRORS = EnumSet.of(RuleViolation.NOT_YOUR_TURN,
            RuleViolation.ALREADY_SHOT, RuleViolation.INVALID_COORD, RuleViolation.GAME_NOT_STARTED);
    private static final Logger LOG = AppLogger.get(GameController.class);

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    private final Object lock = new Object();
    private final NetworkGateway gateway;
    private final Executor uiExecutor;
    private final Executor ioExecutor;
    private final PlayerRepository players;
    private final GameRepository games;
    private final ShipPlacementRepository placements;
    private final MoveRepository moves;
    private final ChatRepository chats;
    private final LogRepository logs;
    private final StatsRepository stats;
    private volatile Consumer<AppEvent> view = e -> { };

    private ConnectionState state = ConnectionState.IDLE;
    private boolean serverMode = true;
    private String localNick = "Player";
    private String remoteNick = "Enemy";
    private String remoteAddress;
    private int port;
    private GameEngine engine;
    private Board remoteFleet;
    private Long gameId;
    private int turnNo;
    private boolean opponentQuit;
    private boolean closingLocally;

    public GameController(Database db, NetworkGateway gateway, Executor uiExecutor, Executor ioExecutor) {
        this.gateway = gateway;
        this.uiExecutor = uiExecutor;
        this.ioExecutor = ioExecutor;
        this.players = new PlayerRepository(db);
        this.games = new GameRepository(db);
        this.placements = new ShipPlacementRepository(db);
        this.moves = new MoveRepository(db);
        this.chats = new ChatRepository(db);
        this.logs = new LogRepository(db);
        this.stats = new StatsRepository(db);
    }

    public void setView(Consumer<AppEvent> view) {
        this.view = view;
    }

    // ---------------------------------------------------------------- UI actions

    /** Opens the server socket. A busy port is reported as "Port is in use". */
    public void startServer(int listenPort, String nickname) {
        synchronized (lock) {
            if (state != ConnectionState.IDLE) {
                return;
            }
            serverMode = true;
            localNick = nickname;
            port = listenPort;
            closingLocally = false;
            try {
                gateway.startServer(listenPort, nickname, this);
            } catch (BindException e) {
                error("Port is in use: " + listenPort, true);
                return;
            } catch (IOException | RuntimeException e) {
                error("Cannot start server: " + e.getMessage(), true);
                return;
            }
            setState(ConnectionState.LISTENING);
            emit(new AppEvent.ServerStarted(gateway.localIp(), listenPort));
            log("Server is opened! (port " + listenPort + ")");
            status(STATUS_WAITING);
        }
    }

    /** Connects to a server on the I/O executor; the result arrives as events. */
    public void connect(String host, int serverPort, String nickname) {
        synchronized (lock) {
            if (state != ConnectionState.IDLE) {
                return;
            }
            serverMode = false;
            localNick = nickname;
            port = serverPort;
            closingLocally = false;
            setState(ConnectionState.CONNECTING);
            log("Connecting to " + host + ":" + serverPort + "...");
            status("Connecting...");
        }
        ioExecutor.execute(() -> {
            try {
                gateway.connect(host, serverPort, nickname, this);
            } catch (IOException | RuntimeException e) {
                synchronized (lock) {
                    setState(ConnectionState.IDLE);
                    status(STATUS_IDLE);
                    error("Cannot connect to " + host + ":" + serverPort + " - " + e.getMessage(), true);
                }
            }
        });
    }

    /** "Stop server" or "Disconnect". A running game is aborted. */
    public void stop() {
        synchronized (lock) {
            if (state == ConnectionState.IDLE) {
                return;
            }
            closingLocally = true;
            if (engine != null) {
                abortGame("Game aborted by you");
            }
            gateway.close();
            engine = null;
            setState(ConnectionState.IDLE);
            log(serverMode ? "Server is closed." : "Disconnected from server.");
            status(STATUS_IDLE);
        }
    }

    /** Sends our fleet. Needs an opponent and a complete fleet. */
    public void ready(Board fleet) {
        synchronized (lock) {
            if (engine == null) {
                error("Connect to an opponent first", false);
                return;
            }
            if (engine.state() != GameState.SETUP) {
                return;
            }
            Board copy = fleet.copyPlacement();
            try {
                engine.setLocalFleet(copy);
                engine.markReady(true);
            } catch (GameRuleException e) {
                error(e.getMessage(), false);
                return;
            }
            Long id = gameId;
            db(() -> placements.saveFleet(id, copy.fleet()));
            gateway.send(Message.ready(ProtocolCodec.encodeFleet(copy.fleet())));
            setState(ConnectionState.IN_GAME);
            emit(new AppEvent.ShipsReady());
            log("Your ships are ready.");
            if (engine.state() == GameState.PLAYING) {
                bothReady();
            } else {
                status(STATUS_WAITING);
            }
        }
    }

    /** Our shot at the enemy board. Ignored when it is not our turn. */
    public void fire(Coordinate c) {
        synchronized (lock) {
            if (engine == null || engine.state() != GameState.PLAYING) {
                return;
            }
            if (!engine.isLocalTurn()) {
                log("It is not your turn.");
                return;
            }
            if (engine.hasShotAt(c)) {
                log("You already shot at " + c + ".");
                return;
            }
            engine.fireAt(c);
            gateway.send(Message.shoot(c.toString()));
            emit(new AppEvent.TurnChanged(false));
            status(STATUS_ENEMY_TURN);
        }
    }

    public void sendChat(String text) {
        synchronized (lock) {
            String body = text == null ? "" : text.strip();
            if (body.isEmpty()) {
                return;
            }
            if (body.length() > 500) {
                error("Message is longer than 500 characters", false);
                return;
            }
            if (state != ConnectionState.CONNECTED && state != ConnectionState.IN_GAME) {
                error("Not connected", false);
                return;
            }
            gateway.send(Message.chat(serverMode ? "SERVER" : "CLIENT", body));
            Long id = gameId;
            db(() -> chats.save(id, GameRepository.LOCAL, body));
            emit(new AppEvent.ChatReceived("Me", body, true));
        }
    }

    /**
     * Starts a new round. Connected and the previous game ended: new game record with the same opponent.
     * The caller must confirm and {@link #stop()} first if a game is in progress.
     */
    public void newGame() {
        synchronized (lock) {
            if (engine != null && (engine.state() == GameState.FINISHED || engine.state() == GameState.ABORTED)) {
                startRound();
                setState(ConnectionState.CONNECTED);
                log("New game against " + remoteNick + ". Place your ships and press Ready.");
                status(STATUS_PLACING);
            }
            emit(new AppEvent.RoundReset());
        }
    }

    /** Closes the network before the application exits. */
    public void shutdown() {
        stop();
    }

    public boolean isGameInProgress() {
        synchronized (lock) {
            return engine != null && (engine.state() == GameState.WAITING || engine.state() == GameState.PLAYING);
        }
    }

    public ConnectionState connectionState() {
        synchronized (lock) {
            return state;
        }
    }

    public boolean isServerMode() {
        synchronized (lock) {
            return serverMode;
        }
    }

    public String localIp() {
        return gateway.localIp();
    }

    /** Current game id, or null before the first opponent connected. */
    public Long currentGameId() {
        synchronized (lock) {
            return gameId;
        }
    }

    public GameState gameState() {
        synchronized (lock) {
            return engine == null ? null : engine.state();
        }
    }

    // ---------------------------------------------------------------- network callbacks

    @Override
    public void onConnected(String remoteNickname, String address) {
        synchronized (lock) {
            remoteNick = remoteNickname == null || remoteNickname.isBlank() ? "Enemy" : remoteNickname;
            remoteAddress = address;
            opponentQuit = false;
            closingLocally = false;
            startRound();
            setState(ConnectionState.CONNECTED);
            if (serverMode) {
                log(remoteNick + " wants to join (" + address + ")");
            } else {
                log("Connected to " + remoteNick + " at " + address + ":" + port);
            }
            emit(new AppEvent.ClientConnected(remoteNick, address));
            log("Place your ships and press Ready.");
            status(STATUS_PLACING);
        }
    }

    @Override
    public void onMessage(Message m) {
        synchronized (lock) {
            try {
                switch (m.type()) {
                    case READY -> remoteReady(m.field(0));
                    case START -> log("Server started the game.");
                    case SHOOT -> remoteShoot(m.field(0));
                    case RESULT -> remoteResult(m.field(0), m.field(1), m.field(2));
                    case CHAT -> remoteChat(m.field(1));
                    case GAME_OVER -> remoteGameOver(m.field(0));
                    case QUIT -> {
                        opponentQuit = true;
                        log(remoteNick + " has left the game.");
                    }
                    case ERROR -> remoteError(m.field(0), m.field(1));
                    default -> LOG.fine("Ignored " + m.type());
                }
            } catch (ProtocolException | IllegalArgumentException e) {
                warn("Bad message from opponent: " + e.getMessage());
            }
        }
    }

    @Override
    public void onDisconnected(String reason) {
        synchronized (lock) {
            if (closingLocally || state == ConnectionState.IDLE) {
                return;
            }
            if (isGameInProgress() || (engine != null && engine.state() == GameState.SETUP)) {
                abortGame("Game aborted: opponent left");
            }
            engine = null;
            log(opponentQuit ? "Opponent has left the game." : "Connection lost: " + reason);
            boolean listening = serverMode && gateway.isListening();
            setState(listening ? ConnectionState.LISTENING : ConnectionState.IDLE);
            emit(new AppEvent.Disconnected(reason, true));
            status(listening ? STATUS_WAITING : STATUS_DISCONNECTED);
        }
    }

    @Override
    public void onError(Exception e) {
        synchronized (lock) {
            warn("Network: " + e.getMessage());
        }
    }

    // ---------------------------------------------------------------- message handlers (lock held)

    private void remoteReady(String fleetText) throws ProtocolException {
        if (engine == null) {
            return;
        }
        if (engine.state() == GameState.FINISHED || engine.state() == GameState.ABORTED) {
            startRound();
            setState(ConnectionState.CONNECTED);
            emit(new AppEvent.RoundReset());
            log(remoteNick + " started a new game. Place your ships and press Ready.");
            status(STATUS_PLACING);
        }
        if (engine.state() != GameState.SETUP && engine.state() != GameState.WAITING) {
            return;
        }
        Map<ShipType, com.amiral.model.Ship> fleet = ProtocolCodec.decodeFleet(fleetText);
        Board board;
        try {
            board = Board.of(fleet);
        } catch (IllegalArgumentException e) {
            board = null;
        }
        if (board == null || !board.isFleetComplete()) {
            gateway.send(Message.error(RuleViolation.INVALID_FLEET.name(), "Fleet must have 5 valid ships"));
            warn(remoteNick + " sent an invalid fleet.");
            return;
        }
        remoteFleet = board;
        engine.markReady(false);
        log(remoteNick + " is ready.");
        if (engine.state() == GameState.PLAYING) {
            setState(ConnectionState.IN_GAME);
            bothReady();
        }
    }

    private void bothReady() {
        if (serverMode) {
            gateway.send(Message.start(false));
        }
        boolean yourTurn = engine.isLocalTurn();
        emit(new AppEvent.GameStarted(yourTurn));
        log("Game started! " + (yourTurn ? "Your turn." : remoteNick + " starts."));
        status(yourTurn ? STATUS_YOUR_TURN : STATUS_ENEMY_TURN);
    }

    private void remoteShoot(String coordText) {
        if (engine == null || engine.state() != GameState.PLAYING) {
            sendError(RuleViolation.GAME_NOT_STARTED, "The game is not running");
            return;
        }
        Coordinate c;
        try {
            c = Coordinate.parse(coordText);
        } catch (IllegalArgumentException e) {
            sendError(RuleViolation.INVALID_COORD, "Invalid cell: " + coordText);
            return;
        }
        ShotOutcome outcome;
        try {
            outcome = engine.receiveEnemyShot(c);
        } catch (GameRuleException e) {
            sendError(e.violation(), e.getMessage());
            return;
        }
        if (outcome.result() == ShotResult.ALREADY_SHOT) {
            sendError(RuleViolation.ALREADY_SHOT, "Already shot at " + c);
            return;
        }
        gateway.send(Message.result(c.toString(), outcome.result().name(),
                outcome.sunkType() == null ? null : outcome.sunkType().name()));
        saveMove(GameRepository.REMOTE, c, outcome.result());
        emit(new AppEvent.ShotReceived(c, outcome.result(), outcome.sunkType(), outcome.sunkCells()));
        log(remoteNick + " shot at " + c + ": " + describe(outcome.result(), outcome.sunkType()));
        if (engine.state() == GameState.FINISHED) {
            gateway.send(Message.gameOver(true));
            gameOver(false);
        } else {
            emit(new AppEvent.TurnChanged(true));
            status(STATUS_YOUR_TURN);
        }
    }

    private void remoteResult(String coordText, String resultText, String typeText) {
        if (engine == null || engine.state() != GameState.PLAYING) {
            return;
        }
        Coordinate c = Coordinate.parse(coordText);
        ShotResult r = ShotResult.valueOf(resultText);
        ShipType sunk = typeText == null || typeText.isEmpty() ? null : ShipType.valueOf(typeText);
        try {
            engine.applyOpponentShotResult(c, r, sunk);
        } catch (GameRuleException e) {
            warn("Unexpected result for " + c + ": " + e.getMessage());
            return;
        }
        saveMove(GameRepository.LOCAL, c, r);
        List<Coordinate> sunkCells = List.of();
        if (r == ShotResult.SUNK && sunk != null && remoteFleet != null) {
            sunkCells = remoteFleet.shipOf(sunk).map(s -> s.cells()).orElse(List.of());
        }
        emit(new AppEvent.ShotFired(c, r, sunk, sunkCells));
        log("You shot at " + c + ": " + describe(r, sunk));
        if (engine.state() == GameState.FINISHED) {
            gameOver(true);
        } else {
            status(STATUS_ENEMY_TURN);
        }
    }

    private void remoteChat(String encodedBody) throws ProtocolException {
        String body = ProtocolCodec.unb64(encodedBody);
        if (body.isBlank()) {
            return;
        }
        String trimmed = body.length() > 500 ? body.substring(0, 500) : body;
        Long id = gameId;
        db(() -> chats.save(id, GameRepository.REMOTE, trimmed));
        emit(new AppEvent.ChatReceived(remoteNick, trimmed, false));
    }

    private void remoteGameOver(String text) {
        if (engine != null && engine.state() == GameState.PLAYING) {
            // Out of sync: trust the opponent's verdict.
            engine.abort();
            gameOver("WIN".equals(text));
        }
    }

    private void remoteError(String code, String message) {
        warn("Opponent refused: " + code + " - " + message);
        RuleViolation v;
        try {
            v = RuleViolation.valueOf(code);
        } catch (IllegalArgumentException e) {
            return;
        }
        if (engine != null && SHOT_ERRORS.contains(v)) {
            engine.cancelPendingShot();
            emit(new AppEvent.Error(message, false));
            boolean yourTurn = engine.isLocalTurn();
            emit(new AppEvent.TurnChanged(yourTurn));
            status(yourTurn ? STATUS_YOUR_TURN : STATUS_ENEMY_TURN);
        }
    }

    // ---------------------------------------------------------------- helpers (lock held)

    private void startRound() {
        engine = new GameEngine(serverMode);
        remoteFleet = null;
        turnNo = 0;
        String mode = serverMode ? "SERVER" : "CLIENT";
        String ip = remoteAddress;
        Integer p = port >= 1024 && port <= 65535 ? port : null;
        gameId = null;
        db(() -> {
            long id = games.createGame(mode, ip, p);
            gameId = id;
            games.linkPlayers(id, players.upsert(localNick), players.upsert(remoteNick));
        });
    }

    private void gameOver(boolean won) {
        Long id = gameId;
        if (id != null) {
            db(() -> games.finish(id, won ? GameRepository.LOCAL : GameRepository.REMOTE));
        }
        setState(ConnectionState.CONNECTED);
        emit(new AppEvent.GameOver(won));
        log(won ? "Game over: you won!" : "Game over: you lost!");
        status(won ? STATUS_WON : STATUS_LOST);
    }

    private void abortGame(String message) {
        if (engine == null || engine.state() == GameState.FINISHED || engine.state() == GameState.ABORTED) {
            return;
        }
        engine.abort();
        Long id = gameId;
        if (id != null) {
            db(() -> games.abort(id));
        }
        log(message);
    }

    private void saveMove(String shooter, Coordinate c, ShotResult r) {
        turnNo++;
        Long id = gameId;
        int turn = turnNo;
        if (id != null) {
            db(() -> moves.save(id, turn, shooter, c, r));
        }
    }

    private void sendError(RuleViolation v, String message) {
        gateway.send(Message.error(v.name(), message));
        warn("Refused opponent's shot: " + v);
    }

    private static String describe(ShotResult r, ShipType sunk) {
        return switch (r) {
            case MISS -> "miss";
            case HIT -> "hit!";
            case SUNK -> (sunk == null ? "ship" : sunk.displayName()) + " sunk!";
            case ALREADY_SHOT -> "already shot";
        };
    }

    private void setState(ConnectionState s) {
        state = s;
        emit(new AppEvent.ConnectionChanged(s));
    }

    private void status(String text) {
        emit(new AppEvent.Status(text));
    }

    private void log(String message) {
        LOG.info(message);
        Long id = gameId;
        dbQuiet(() -> logs.info(id, message));
        emit(new AppEvent.Log(message));
    }

    private void warn(String message) {
        LOG.warning(message);
        Long id = gameId;
        dbQuiet(() -> logs.warn(id, message));
        emit(new AppEvent.Log(message));
    }

    private void error(String message, boolean dialog) {
        LOG.warning(message);
        Long id = gameId;
        dbQuiet(() -> logs.error(id, message));
        emit(new AppEvent.Log(message));
        emit(new AppEvent.Error(message, dialog));
    }

    private void db(SqlAction action) {
        try {
            action.run();
        } catch (SQLException e) {
            LOG.warning("Database error: " + e.getMessage());
            emit(new AppEvent.Log("Database error: " + e.getMessage()));
        }
    }

    private void dbQuiet(SqlAction action) {
        try {
            action.run();
        } catch (SQLException e) {
            LOG.warning("Cannot write log to database: " + e.getMessage());
        }
    }

    private void emit(AppEvent e) {
        Consumer<AppEvent> v = view;
        uiExecutor.execute(() -> v.accept(e));
    }

    // ---------------------------------------------------------------- HistoryProvider

    @Override
    public Stats stats() {
        synchronized (lock) {
            try {
                StatsRepository.StatsSummary s = stats.summary();
                return new Stats(s.games(), s.wins(), s.losses(), s.aborted(), s.winRate());
            } catch (SQLException e) {
                LOG.warning("Cannot read statistics: " + e.getMessage());
                return new Stats(0, 0, 0, 0, 0);
            }
        }
    }

    @Override
    public List<GameRow> games(int limit) {
        synchronized (lock) {
            List<GameRow> rows = new ArrayList<>();
            try {
                for (StatsRepository.GameSummary g : stats.recentGames(limit)) {
                    rows.add(new GameRow(g.id(), g.mode(), toLocal(g.startedAt()), toLocal(g.endedAt()), g.remoteIp(),
                            g.result(), g.remoteNickname()));
                }
            } catch (SQLException e) {
                LOG.warning("Cannot read games: " + e.getMessage());
            }
            return rows;
        }
    }

    @Override
    public List<MoveRow> moves(long id) {
        synchronized (lock) {
            List<MoveRow> rows = new ArrayList<>();
            try {
                for (MoveRepository.Move m : moves.findByGame(id)) {
                    rows.add(new MoveRow(m.turnNo(), m.shooter(), m.coord(), m.result(), toLocal(m.createdAt())));
                }
            } catch (SQLException e) {
                LOG.warning("Cannot read moves: " + e.getMessage());
            }
            return rows;
        }
    }

    /** Converts a UTC database time to local "MM/dd/yyyy HH:mm:ss". */
    static String toLocal(String utc) {
        if (utc == null || utc.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(utc, DB_TIME).atOffset(ZoneOffset.UTC)
                    .atZoneSameInstant(ZoneId.systemDefault()).format(UI_TIME);
        } catch (DateTimeParseException e) {
            return utc;
        }
    }
}
