# Vurgahpeyi Network Protocol

Version 1.1 (adds radar and torpedo). Implemented in `com.amiral.net` (transport) and `com.amiral.controller.GameController` (game rules).

## 1. Transport

| Item | Value |
|---|---|
| Transport | TCP, one connection between one server and one client |
| Encoding | UTF-8 |
| Framing | One message per line, terminated by `\n` |
| Field separator | `\|` |
| Free text | URL-safe Base64 without padding (nicknames, chat bodies), so it never contains `\|` or a line break |
| Default port | `9090` (valid range `1024–65535`) |
| Threads | Each side runs one daemon reader thread per connection; `send` is `synchronized` |
| Heartbeat | Each side sends `PING` every 5 s and answers `PING` with `PONG`. No traffic for 15 s → connection is considered lost (`timeout`) |

A malformed line (empty, unknown type, too few fields) is reported to the application as an error but does **not** close the connection.

## 2. Messages

`L` = the local side, `R` = the remote side, `S` = server, `C` = client.

| Direction | Message | Format | Meaning |
|---|---|---|---|
| C→S | `HELLO` | `HELLO\|<nickname-b64>` | First message after the TCP connect |
| S→C | `WELCOME` | `WELCOME\|<nickname-b64>` | Handshake accepted, carries the server player's nickname |
| S→C | `REJECT` | `REJECT\|<reason>` | Connection refused. Reason: `ALREADY_CONNECTED`, `VERSION`. The server closes the socket |
| L↔R | `READY` | `READY\|<fleet>` | Fleet is placed. `<fleet>` = `CARRIER:A1H;BATTLESHIP:C3V;...` (type, start cell, orientation) |
| S→C | `START` | `START\|<yourTurn>` | Sent by the server when both sides are READY. `true`/`false` tells the client whether it moves first (always `false`: the server starts) |
| L→R | `SHOOT` | `SHOOT\|<coord>` | Shot at a cell, e.g. `SHOOT\|B7` |
| R→L | `RESULT` | `RESULT\|<coord>\|<MISS/HIT/SUNK>\|<shipType?>` | Result of the shooter's shot, computed by the owner of the board |
| L→R | `RADAR` | `RADAR\|<coord>` | Radar scan of the 4x4 area around `<coord>` (3 per player and game) |
| R→L | `RADAR_RESULT` | `RADAR_RESULT\|<coord>\|<cells>` | Ship cells found in the area, comma separated, e.g. `G4,G5,G6`; empty if none. Nothing is hit |
| L→R | `TORPEDO` | `TORPEDO\|<coord>` | Torpedo on the 3x3 area centred on `<coord>` (2 per player and game) |
| R→L | `TORPEDO_RESULT` | `TORPEDO_RESULT\|<coord>\|<outcomes>` | One entry per newly shot cell: `<cell>:<MISS/HIT/SUNK>:<shipType?>`, comma separated, e.g. `G4:HIT:,G5:SUNK:DESTROYER,G6:MISS:` |
| S→C | `SHOT` | `SHOT\|<shooter>\|<coord>\|<MISS/HIT/SUNK>\|<shipType?>` | Reserved (spectator / echo). Decoded but not sent by v1.0 |
| L↔R | `CHAT` | `CHAT\|<sender>\|<body-b64>` | Chat line. `<sender>` is the sender's role (`SERVER` / `CLIENT`). Body 1–500 characters |
| L↔R | `GAME_OVER` | `GAME_OVER\|<WIN/LOSE>` | Seen from the receiver: `WIN` means *you* won |
| L↔R | `QUIT` | `QUIT` | The sender leaves the game; the socket is closed right after |
| L↔R | `PING` / `PONG` | `PING`, `PONG` | Heartbeat, handled inside `Connection` |
| L↔R | `ERROR` | `ERROR\|<code>\|<message>` | A request was refused. Codes below |

`<shipType?>` is filled only for `SUNK` and left empty otherwise: `RESULT|A5|HIT|`.

### Error codes

| Code | When |
|---|---|
| `INVALID_COORD` | `SHOOT` with a cell outside `A1–J10` |
| `NOT_YOUR_TURN` | `SHOOT` while it is not the sender's turn |
| `ALREADY_SHOT` | `SHOOT` on a cell that was already shot. The turn does not change |
| `INVALID_FLEET` | `READY` with a fleet that is not 5 valid, non-overlapping ships |
| `GAME_NOT_STARTED` | `SHOOT`, `RADAR` or `TORPEDO` before `START` or after the game ended |
| `NO_ABILITY_LEFT` | A fourth `RADAR` or a third `TORPEDO` from the same player |

After an `ERROR` for a `SHOOT`, `RADAR` or `TORPEDO`, the sender keeps the turn (and the weapon use) and may act again.

## 3. Rules enforced by the receiver of a `SHOOT`

Both sides own their board, so each side validates the shots it receives:

1. The game must be `PLAYING`, otherwise `GAME_NOT_STARTED`.
2. It must be the sender's turn, otherwise `NOT_YOUR_TURN`.
3. The cell must parse (`A–J` + `1–10`), otherwise `INVALID_COORD`.
4. The cell must be new, otherwise `ALREADY_SHOT`.
5. Otherwise the board answers `RESULT` On a `MISS` the turn passes to the receiver; on a `HIT` or `SUNK` the shooter keeps the turn and shoots again. A torpedo with at least one hit also keeps the turn; a radar scan always passes it.

When the last ship cell is hit, the owner of the board sends `RESULT|...|SUNK|<type>` followed by `GAME_OVER|WIN`. The shooter also detects the win after its 17th hit. Both sides store the game as `FINISHED`.

### Radar and torpedo areas

Both sides compute the area from the anchor cell with `Weapon.area(anchor)`, so only the anchor travels:

* Radar: 4x4. The anchor is the upper-left of the four centre cells (`E5` → `D4`…`G7`).
* Torpedo: 3x3 centred on the anchor (`E5` → `D4`…`F6`).
* Near an edge the area is shifted onto the board, never cut (`J10` radar → `G7`…`J10`).

A torpedo needs at least one not yet shot cell in its area, otherwise `ERROR ALREADY_SHOT`. Radar, torpedo and shot each end the turn.

## 4. Disconnects

* `QUIT` or a closed socket during a game → both sides store the game as `ABORTED` (no winner) and show *Opponent has left the game.*
* `Stop Server` closes the client connection (the client sees a disconnect).
* The server keeps listening after a client leaves, so a new client can join.

## 5. Example session

```
C→S  HELLO|S2Fhbg
S→C  WELCOME|VGVzdFNlcnZlcg
C→S  READY|CARRIER:A1H;BATTLESHIP:C3V;CRUISER:E5H;SUBMARINE:H1H;DESTROYER:J9H
S→C  READY|CARRIER:B2V;BATTLESHIP:A9V;CRUISER:H4H;SUBMARINE:J1H;DESTROYER:E7H
S→C  START|false
S→C  SHOOT|A1
C→S  RESULT|A1|HIT|
C→S  SHOOT|B2
S→C  RESULT|B2|HIT|
S→C  SHOOT|A1
C→S  ERROR|ALREADY_SHOT|Already shot at A1
S→C  SHOOT|A2
C→S  RESULT|A2|HIT|
C→S  CHAT|CLIENT|bmljZSBzaG90IQ
...
S→C  SHOOT|J10
C→S  RESULT|J10|SUNK|DESTROYER
C→S  GAME_OVER|WIN
C→S  QUIT
```

Radar and torpedo in the same game:

```
S→C  RADAR|E5
C→S  RADAR_RESULT|E5|G4,G5,G6
C→S  TORPEDO|B2
S→C  TORPEDO_RESULT|B2|A1:MISS:,A2:HIT:,A3:HIT:,B1:MISS:,B2:MISS:,B3:MISS:,C1:MISS:,C2:MISS:,C3:MISS:
S→C  RADAR|E5
S→C  RADAR|E5
S→C  RADAR|E5
C→S  ERROR|NO_ABILITY_LEFT|No Radar left
```

## 5b. Computer opponent

`ComputerGateway` plays the client role inside the same process. It speaks exactly this protocol (it answers `SHOOT`, `RADAR` and `TORPEDO` and sends its own after a short pause), so the controller code is the same for network and computer games. No socket is opened.

## 6. Sequence diagram

```
Client                         Server
  | ---- TCP connect ----------> |
  | ---- HELLO|nick -----------> |
  | <--- WELCOME|nick ---------- |
  | ---- READY|fleet ----------> |
  | <--- READY|fleet ----------- |
  | <--- START|false ----------- |   both ready, server moves first
  | <--- SHOOT|A1 -------------- |
  | ---- RESULT|A1|MISS| ------> |
  | ---- SHOOT|C4 -------------> |
  | <--- RESULT|C4|HIT| -------- |
  |            ...               |
  | ---- RESULT|..|SUNK|TYPE --> |   last cell of the client's fleet
  | ---- GAME_OVER|WIN --------> |
```
