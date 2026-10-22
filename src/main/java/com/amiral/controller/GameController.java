package com.amiral.controller;

import com.amiral.db.AbilityRepository;
import com.amiral.db.ChatRepository;
import com.amiral.db.Database;
import com.amiral.db.GameRepository;
import com.amiral.db.LogRepository;
import com.amiral.db.MoveRepository;
import com.amiral.db.PlayerRepository;
import com.amiral.db.ShipPlacementRepository;
import com.amiral.db.StatsRepository;
import com.amiral.model.Board;
import com.amiral.model.ComputerPlayer;
import com.amiral.model.Coordinate;
import com.amiral.model.GameEngine;
import com.amiral.model.GameRuleException;
import com.amiral.model.GameState;
import com.amiral.model.RuleViolation;
import com.amiral.model.Ship;
import com.amiral.model.ShipType;
import com.amiral.model.ShotOutcome;
import com.amiral.model.ShotResult;
import com.amiral.model.Weapon;
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
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.logging.Logger;

/**
 * Bridge between the game engine, the network (or the computer opponent), the database and the UI.
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
    private static final Set<RuleViolation> ACTION_ERRORS = EnumSet.of(RuleViolation.NOT_YOUR_TURN,
            RuleViolation.ALREADY_SHOT, RuleViolation.INVALID_COORD, RuleViolation.GAME_NOT_STARTED,
            RuleViolation.NO_ABILITY_LEFT);
    private static final Logger LOG = AppLogger.get(GameController.class);

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    private final Object lock = new Object();
    private final NetworkGateway networkGateway;
    private final Executor uiExecutor;
    private final Executor ioExecutor;
    private final PlayerRepository players;
    private final GameRepository games;
    private final ShipPlacementRepository placements;
    private final MoveRepository moves;
    private final AbilityRepository abilities;
    private final ChatRepository chats;
    private final LogRepository logs;
    private final StatsRepository stats;
    private volatile Consumer<AppEvent> view = e -> { };
    private Function<ComputerPlayer.Difficulty, NetworkGateway> computerFactory = ComputerGateway::new;

    private NetworkGateway gateway;
    private ConnectionState state = ConnectionState.IDLE;
    private GameMode mode = GameMode.SERVER;
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
        this.networkGateway = gateway;
        this.gateway = gateway;
        this.uiExecutor = uiExecutor;
        this.ioExecutor = ioExecutor;
        this.players = new PlayerRepository(db);
        this.games = new GameRepository(db);
        this.placements = new ShipPlacementRepository(db);
        this.moves = new MoveRepository(db);
        this.abilities = new AbilityRepository(db);
        this.chats = new ChatRepository(db);
        this.logs = new LogRepository(db);
        this.stats = new StatsRepository(db);
    }

    public void setView(Consumer<AppEvent> view) {
        this.view = view;
    }

    /** Replaces how the computer opponent is created (tests use a deterministic one). */
    public void setComputerFactory(Function<ComputerPlayer.Difficulty, NetworkGateway> factory) {
        synchronized (lock) {
            this.computerFactory = factory;
        }
    }

    // ---------------------------------------------------------------- UI actions

    /** Opens the server socket. A busy port is reported as "Port is in use". */
    public void startServer(int listenPort, String nickname) {
        synchronized (lock) {
            if (state != ConnectionState.IDLE) {
                return;
            }
            gateway = networkGateway;
            mode = GameMode.SERVER;
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
            gateway = networkGateway;
            mode = GameMode.CLIENT;
            serverMode = false;
            localNick = nickname;
            port = serverPort;
            closingLocally = false;
            setState(ConnectionState.CONNECTING);
            log("Connecting to " + host + ":" + serverPort + "...");
            status("Connecting...");
        }
        NetworkGateway g = networkGateway;
        ioExecutor.execute(() -> {
            try {
                g.connect(host, serverPort, nickname, this);
            } catch (IOException | RuntimeException e) {
                synchronized (lock) {
                    setState(ConnectionState.IDLE);
                    status(STATUS_IDLE);
                    error("Cannot connect to " + host + ":" + serverPort + " - " + e.getMessage(), true);
                }
            }
        });
    }

    /** Starts a game against the computer. We move first, like a server. */
    public void playComputer(String nickname, ComputerPlayer.Difficulty difficulty) {
        synchronized (lock) {
            if (state != ConnectionState.IDLE) {
                return;
            }
            mode = GameMode.AI;
            serverMode = true;
            localNick = nickname;
            port = 0;
            closingLocally = false;
            gateway = computerFactory.apply(difficulty);
            log("Starting a game against the computer (" + difficulty.label() + ").");
            try {
                gateway.connect("computer", 0, nickname, this);
            } catch (IOException | RuntimeException e) {
                gateway = networkGateway;
                error("Cannot start the computer player: " + e.getMessage(), true);
            }
        }
    }

    /** "Stop server", "Disconnect" or "End game". A running game is aborted. */
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
            log(switch (mode) {
                case SERVER -> "Server is closed.";
                case CLIENT -> "Disconnected from server.";
                case AI -> "Game against the computer closed.";
            });
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

    /** A normal shot at the enemy board. Ignored when it is not our turn. */
    public void fire(Coordinate c) {
        act(Weapon.SHOT, c);
    }

    /** Uses a weapon on our turn: shot, radar (4x4) or torpedo (3x3). */
    public void act(Weapon weapon, Coordinate c) {
        synchronized (lock) {
            if (engine == null || engine.state() != GameState.PLAYING) {
                return;
            }
            if (!engine.isLocalTurn()) {
                log("It is not your turn.");
                return;
            }
            if (weapon == Weapon.SHOT && engine.hasShotAt(c)) {
                log("You already shot at " + c + ".");
                return;
            }
            try {
                engine.use(weapon, c);
            } catch (GameRuleException e) {
                log(e.getMessage() + ".");
                emit(new AppEvent.Error(e.getMessage(), false));
                return;
            }
            gateway.send(switch (weapon) {
                case SHOT -> Message.shoot(c.toString());
                case RADAR -> Message.radar(c.toString());
                case TORPEDO -> Message.torpedo(c.toString());
            });
            if (weapon != Weapon.SHOT) {
                emitAbilities();
            }
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

    public GameMode mode() {
        synchronized (lock) {
            return mode;
        }
    }

    public String localIp() {
        return networkGateway.localIp();
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

    /** Uses left for our weapon (Integer.MAX_VALUE for shots, 0 without a game). */
    public int usesLeft(Weapon w) {
        synchronized (lock) {
            return engine == null ? 0 : engine.usesLeft(w);
        }
    }

    // ---------------------------------------------------------------- network callbacks

    @Override
    public void onConnected(String remoteNickname, String address) {
        synchronized (lock) {
            remoteNick = remoteNickname == null || remoteNickname.isBlank() ? "Enemy" : remoteNickname;
            remoteAddress = mode == GameMode.AI ? null : address;
            opponentQuit = false;
            closingLocally = false;
            startRound();
            setState(ConnectionState.CONNECTED);
            switch (mode) {
                case SERVER -> log(remoteNick + " wants to join (" + address + ")");
                case CLIENT -> log("Connected to " + remoteNick + " at " + address + ":" + port);
                case AI -> log("You are playing against " + remoteNick + ".");
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
                    case RADAR -> remoteRadar(m.field(0));
                    case RADAR_RESULT -> remoteRadarResult(m.field(0), m.field(1));
                    case TORPEDO -> remoteTorpedo(m.field(0));
                    case TORPEDO_RESULT -> remoteTorpedoResult(m.field(0), m.field(1));
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
        Map<ShipType, Ship> fleet = ProtocolCodec.decodeFleet(fleetText);
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
        emitAbilities();
        log("Game started! " + (yourTurn ? "Your turn." : remoteNick + " starts."));
        status(yourTurn ? STATUS_YOUR_TURN : STATUS_ENEMY_TURN);
    }

    /** Parses a target cell sent by the opponent and checks that the game is running; null if refused. */
    private Coordinate incomingTarget(String coordText) {
        if (engine == null || engine.state() != GameState.PLAYING) {
            sendError(RuleViolation.GAME_NOT_STARTED, "The game is not running");
            return null;
        }
        try {
            return Coordinate.parse(coordText);
        } catch (IllegalArgumentException e) {
            sendError(RuleViolation.INVALID_COORD, "Invalid cell: " + coordText);
            return null;
        }
    }

    private void remoteShoot(String coordText) {
        Coordinate c = incomingTarget(coordText);
        if (c == null) {
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
        saveMove(nextTurn(), GameRepository.REMOTE, c, outcome.result(), Weapon.SHOT);
        emit(new AppEvent.ShotReceived(c, outcome.result(), outcome.sunkType(), outcome.sunkCells()));
        log(remoteNick + " shot at " + c + ": " + describe(outcome.result(), outcome.sunkType()));
        afterEnemyAction();
    }

    private void remoteRadar(String coordText) {
        Coordinate c = incomingTarget(coordText);
        if (c == null) {
            return;
        }
        List<Coordinate> found;
        try {
            found = engine.receiveEnemyRadar(c);
        } catch (GameRuleException e) {
            sendError(e.violation(), e.getMessage());
            return;
        }
        gateway.send(Message.radarResult(c.toString(), ProtocolCodec.encodeCells(found)));
        saveAbility(nextTurn(), GameRepository.REMOTE, Weapon.RADAR, c, found.size());
        emit(new AppEvent.RadarScan(c, Weapon.RADAR.area(c), found, false));
        emitAbilities();
        log(remoteNick + " used a radar around " + c + " and found " + found.size() + " of your ship cells.");
        afterEnemyAction();
    }

    private void remoteTorpedo(String coordText) {
        Coordinate c = incomingTarget(coordText);
        if (c == null) {
            return;
        }
        List<ShotOutcome> outcomes;
        try {
            outcomes = engine.receiveEnemyTorpedo(c);
        } catch (GameRuleException e) {
            sendError(e.violation(), e.getMessage());
            return;
        }
        gateway.send(Message.torpedoResult(c.toString(), ProtocolCodec.encodeOutcomes(outcomes)));
        int turn = nextTurn();
        outcomes.forEach(o -> saveMove(turn, GameRepository.REMOTE, o.coordinate(), o.result(), Weapon.TORPEDO));
        saveAbility(turn, GameRepository.REMOTE, Weapon.TORPEDO, c, hits(outcomes));
        emit(new AppEvent.TorpedoStrike(c, Weapon.TORPEDO.area(c), outcomes, false));
        emitAbilities();
        log(remoteNick + " fired a torpedo at " + c + ": " + describeTorpedo(outcomes));
        afterEnemyAction();
    }

    private void afterEnemyAction() {
        if (engine.state() == GameState.FINISHED) {
            gateway.send(Message.gameOver(true));
            gameOver(false);
        } else if (engine.isLocalTurn()) {
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
        saveMove(nextTurn(), GameRepository.LOCAL, c, r, Weapon.SHOT);
        emit(new AppEvent.ShotFired(c, r, sunk, enemyShipCells(r, sunk)));
        log("You shot at " + c + ": " + describe(r, sunk));
        afterOwnAction();
    }

    private void remoteRadarResult(String coordText, String cellsText) throws ProtocolException {
        if (engine == null || engine.state() != GameState.PLAYING) {
            return;
        }
        Coordinate c = Coordinate.parse(coordText);
        List<Coordinate> found = ProtocolCodec.decodeCells(cellsText);
        try {
            engine.applyRadarResult(c, new HashSet<>(found));
        } catch (GameRuleException e) {
            warn("Unexpected radar result: " + e.getMessage());
            return;
        }
        saveAbility(nextTurn(), GameRepository.LOCAL, Weapon.RADAR, c, found.size());
        emit(new AppEvent.RadarScan(c, Weapon.RADAR.area(c), found, true));
        log("Radar around " + c + ": " + (found.isEmpty() ? "no ships." : found.size() + " ship cells found!"));
        afterOwnAction();
    }

    private void remoteTorpedoResult(String coordText, String outcomesText) throws ProtocolException {
        if (engine == null || engine.state() != GameState.PLAYING) {
            return;
        }
        Coordinate c = Coordinate.parse(coordText);
        List<ShotOutcome> outcomes = ProtocolCodec.decodeOutcomes(outcomesText);
        try {
            engine.applyTorpedoResult(c, outcomes);
        } catch (GameRuleException e) {
            warn("Unexpected torpedo result: " + e.getMessage());
            return;
        }
        int turn = nextTurn();
        List<ShotOutcome> withCells = new ArrayList<>();
        for (ShotOutcome o : outcomes) {
            saveMove(turn, GameRepository.LOCAL, o.coordinate(), o.result(), Weapon.TORPEDO);
            withCells.add(new ShotOutcome(o.coordinate(), o.result(), o.sunkType(),
                    enemyShipCells(o.result(), o.sunkType())));
        }
        saveAbility(turn, GameRepository.LOCAL, Weapon.TORPEDO, c, hits(outcomes));
        emit(new AppEvent.TorpedoStrike(c, Weapon.TORPEDO.area(c), withCells, true));
        log("Torpedo at " + c + ": " + describeTorpedo(outcomes));
        afterOwnAction();
    }

    private void afterOwnAction() {
        if (engine.state() == GameState.FINISHED) {
            gameOver(true);
        } else if (engine.isLocalTurn()) {
            // A hit keeps the turn.
            emit(new AppEvent.TurnChanged(true));
            status(STATUS_YOUR_TURN);
        } else {
            status(STATUS_ENEMY_TURN);
        }
    }

    /** Cells of a sunk enemy ship (known from the fleet in its READY message). */
    private List<Coordinate> enemyShipCells(ShotResult r, ShipType sunk) {
        if (r == ShotResult.SUNK && sunk != null && remoteFleet != null) {
            return remoteFleet.shipOf(sunk).map(Ship::cells).orElse(List.of());
        }
        return List.of();
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
        if (engine != null && ACTION_ERRORS.contains(v)) {
            engine.cancelPendingShot();
            emit(new AppEvent.Error(message, false));
            emitAbilities();
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
        String modeName = mode.name();
        String ip = remoteAddress;
        Integer p = port >= 1024 && port <= 65535 ? port : null;
        gameId = null;
        db(() -> {
            long id = games.createGame(modeName, ip, p);
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

    private int nextTurn() {
        return ++turnNo;
    }

    private void saveMove(int turn, String shooter, Coordinate c, ShotResult r, Weapon weapon) {
        Long id = gameId;
        if (id != null) {
            db(() -> moves.save(id, turn, shooter, c, r, weapon.name()));
        }
    }

    private void saveAbility(int turn, String user, Weapon weapon, Coordinate c, int found) {
        Long id = gameId;
        if (id != null) {
            db(() -> abilities.save(id, turn, user, weapon.name(), c.toString(), found));
        }
    }

    private void emitAbilities() {
        if (engine != null) {
            emit(new AppEvent.AbilitiesChanged(engine.usesLeft(Weapon.RADAR), engine.usesLeft(Weapon.TORPEDO),
                    engine.enemyUsesLeft(Weapon.RADAR), engine.enemyUsesLeft(Weapon.TORPEDO)));
        }
    }

    private void sendError(RuleViolation v, String message) {
        gateway.send(Message.error(v.name(), message));
        warn("Refused opponent's action: " + v);
    }

    private static int hits(List<ShotOutcome> outcomes) {
        return (int) outcomes.stream().filter(o -> o.result().isHit()).count();
    }

    private static String describe(ShotResult r, ShipType sunk) {
        return switch (r) {
            case MISS -> "miss";
            case HIT -> "hit!";
            case SUNK -> (sunk == null ? "ship" : sunk.displayName()) + " sunk!";
            case ALREADY_SHOT -> "already shot";
        };
    }

    private static String describeTorpedo(List<ShotOutcome> outcomes) {
        int hits = hits(outcomes);
        StringBuilder text = new StringBuilder(hits == 0 ? "no hits" : hits + " hit" + (hits > 1 ? "s" : ""));
        for (ShotOutcome o : outcomes) {
            if (o.result() == ShotResult.SUNK && o.sunkType() != null) {
                text.append(", ").append(o.sunkType().displayName()).append(" sunk!");
            }
        }
        return text.toString();
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

    /** Shots, torpedo cells and radar scans of a game, in turn order. */
    @Override
    public List<MoveRow> moves(long id) {
        synchronized (lock) {
            List<MoveRow> rows = new ArrayList<>();
            try {
                for (MoveRepository.Move m : moves.findByGame(id)) {
                    rows.add(new MoveRow(m.turnNo(), m.shooter(), m.weapon(), m.coord(), m.result(),
                            toLocal(m.createdAt())));
                }
                for (AbilityRepository.AbilityUse a : abilities.findByGame(id)) {
                    if (Weapon.RADAR.name().equals(a.ability())) {
                        rows.add(new MoveRow(a.turnNo(), a.userRole(), a.ability(), a.coord(),
                                a.found() + " found", toLocal(a.createdAt())));
                    }
                }
            } catch (SQLException e) {
                LOG.warning("Cannot read moves: " + e.getMessage());
            }
            rows.sort(Comparator.comparingInt(MoveRow::turn));
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
