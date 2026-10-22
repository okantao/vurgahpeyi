PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS player (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  nickname    TEXT    NOT NULL UNIQUE CHECK (length(nickname) BETWEEN 1 AND 20),
  created_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS game (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  mode        TEXT    NOT NULL CHECK (mode IN ('SERVER','CLIENT')),
  status      TEXT    NOT NULL CHECK (status IN ('ACTIVE','FINISHED','ABORTED')),
  started_at  TEXT    NOT NULL DEFAULT (datetime('now')),
  ended_at    TEXT,
  winner_role TEXT    CHECK (winner_role IN ('LOCAL','REMOTE')),
  remote_ip   TEXT,
  port        INTEGER CHECK (port BETWEEN 1024 AND 65535)
);

CREATE TABLE IF NOT EXISTS game_player (
  game_id    INTEGER NOT NULL REFERENCES game(id) ON DELETE CASCADE,
  player_id  INTEGER NOT NULL REFERENCES player(id),
  role       TEXT    NOT NULL CHECK (role IN ('LOCAL','REMOTE')),
  PRIMARY KEY (game_id, role)
);

CREATE TABLE IF NOT EXISTS ship_placement (
  id           INTEGER PRIMARY KEY AUTOINCREMENT,
  game_id      INTEGER NOT NULL REFERENCES game(id) ON DELETE CASCADE,
  ship_type    TEXT    NOT NULL CHECK (ship_type IN ('CARRIER','BATTLESHIP','CRUISER','SUBMARINE','DESTROYER')),
  start_coord  TEXT    NOT NULL CHECK (start_coord GLOB '[A-J][1-9]' OR start_coord GLOB '[A-J]10'),
  orientation  TEXT    NOT NULL CHECK (orientation IN ('H','V')),
  UNIQUE (game_id, ship_type)
);

CREATE TABLE IF NOT EXISTS move (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  game_id     INTEGER NOT NULL REFERENCES game(id) ON DELETE CASCADE,
  turn_no     INTEGER NOT NULL CHECK (turn_no >= 1),
  shooter     TEXT    NOT NULL CHECK (shooter IN ('LOCAL','REMOTE')),
  coord       TEXT    NOT NULL CHECK (coord GLOB '[A-J][1-9]' OR coord GLOB '[A-J]10'),
  result      TEXT    NOT NULL CHECK (result IN ('MISS','HIT','SUNK')),
  created_at  TEXT    NOT NULL DEFAULT (datetime('now')),
  UNIQUE (game_id, shooter, coord)
);

CREATE TABLE IF NOT EXISTS chat_message (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  game_id     INTEGER REFERENCES game(id) ON DELETE CASCADE,
  sender      TEXT    NOT NULL CHECK (sender IN ('LOCAL','REMOTE')),
  body        TEXT    NOT NULL CHECK (length(body) BETWEEN 1 AND 500),
  sent_at     TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE TABLE IF NOT EXISTS app_log (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  game_id     INTEGER REFERENCES game(id) ON DELETE SET NULL,
  level       TEXT    NOT NULL CHECK (level IN ('INFO','WARN','ERROR')),
  message     TEXT    NOT NULL,
  created_at  TEXT    NOT NULL DEFAULT (datetime('now'))
);

CREATE INDEX IF NOT EXISTS idx_move_game      ON move(game_id, turn_no);
CREATE INDEX IF NOT EXISTS idx_chat_game      ON chat_message(game_id, sent_at);
CREATE INDEX IF NOT EXISTS idx_game_status    ON game(status);
