# Veritabanı

DBMS: **SQLite** (`org.xerial:sqlite-jdbc`). Dosya: `data/amiral.db` (ilk açılışta oluşturulur, repoya girmez).
Şema: `src/main/resources/schema.sql`, açılışta `Database.initSchema()` çalıştırır. Tüm DDL `IF NOT EXISTS` olduğu için tekrar çalıştırmak güvenlidir.
Bağlantı açılırken `PRAGMA foreign_keys = ON` ve `PRAGMA busy_timeout = 5000` ayarlanır.

Erişim yalnızca `com.amiral.db` paketindeki repository sınıfları üzerinden, `PreparedStatement` ile yapılır (string birleştirme ile SQL yok).

## 1. ER diyagramı

```
 player 1 ──< game_player >── 1 game 1 ──< ship_placement
                                  │ 1
                                  ├──< move
                                  ├──< ability_use    (radar / torpedo, limit trigger)
                                  ├──< chat_message   (game_id NULL olabilir)
                                  └──< app_log        (game_id NULL olabilir, ON DELETE SET NULL)
```

PlantUML kaynağı: `docs/diagrams/er.puml`.

## 2. Tablolar

| Tablo | Amaç | Anahtarlar ve kısıtlar |
|---|---|---|
| `player` | Oyuncu takma adları | PK `id`; `nickname` **UNIQUE**, **CHECK** uzunluk 1–20 |
| `game` | Her oyun (yerel oyuncunun bakış açısından) | PK `id`; **CHECK** `mode IN ('SERVER','CLIENT','AI')`, `status IN ('ACTIVE','FINISHED','ABORTED')`, `winner_role IN ('LOCAL','REMOTE')`, `port BETWEEN 1024 AND 65535` |
| `game_player` | Oyun ↔ oyuncu (rol ile) | PK `(game_id, role)`; **FK** `game(id)` ON DELETE CASCADE, **FK** `player(id)` |
| `ship_placement` | Yerel oyuncunun filosu | **FK** `game(id)` CASCADE; **UNIQUE** `(game_id, ship_type)`; **CHECK** gemi tipi, `orientation IN ('H','V')`, `start_coord` biçimi |
| `move` | Her atış (torpidonun her hücresi ayrı satır) | **FK** `game(id)` CASCADE; **UNIQUE** `(game_id, shooter, coord)`; **CHECK** `turn_no >= 1`, `shooter`, `result IN ('MISS','HIT','SUNK')`, `coord` biçimi, `weapon IN ('SHOT','TORPEDO')` |
| `ability_use` | Radar ve torpido kullanımları | **FK** `game(id)` CASCADE; **CHECK** `user_role`, `ability IN ('RADAR','TORPEDO')`, `coord` biçimi, `found >= 0`; **TRIGGER** `trg_ability_limit` |
| `chat_message` | Sohbet satırları | **FK** `game(id)` CASCADE; **CHECK** `length(body) BETWEEN 1 AND 500` |
| `app_log` | Output paneline yazılan her olay | **FK** `game(id)` ON DELETE SET NULL; **CHECK** `level IN ('INFO','WARN','ERROR')` |

İndeksler: `idx_move_game (game_id, turn_no)`, `idx_chat_game (game_id, sent_at)`, `idx_game_status (status)`, `idx_ability_game (game_id, turn_no)`.

### Tetikleyici: radar / torpido hakkı

Oyun kuralı (oyuncu başına 3 radar, 2 torpido) veritabanı düzeyinde de korunur:

```sql
CREATE TRIGGER IF NOT EXISTS trg_ability_limit
BEFORE INSERT ON ability_use
WHEN (SELECT COUNT(*) FROM ability_use
      WHERE game_id = NEW.game_id AND user_role = NEW.user_role AND ability = NEW.ability)
     >= CASE NEW.ability WHEN 'RADAR' THEN 3 ELSE 2 END
BEGIN
  SELECT RAISE(ABORT, 'ability limit reached');
END;
```

`Database.splitStatements` tetikleyici gövdesindeki `;` karakterlerini bölmeden çalıştırır.

### Sürüm yükseltme (v1.0 → v1.1)

v1.0 ile oluşturulmuş bir `amiral.db` açılışta kendiliğinden yükseltilir (`Database.migrate`):

1. `game.mode` CHECK kısıtına `'AI'` eklemek için `game` tablosu yeniden oluşturulur (yabancı anahtarlar geçici olarak kapatılır, satırlar kopyalanır, tablo yeniden adlandırılır, indeksler yeniden kurulur).
2. `move` tablosuna `weapon TEXT NOT NULL DEFAULT 'SHOT'` sütunu eklenir.
3. `ability_use` tablosu ve tetikleyici `IF NOT EXISTS` ile oluşturulur.

Eski kayıtlar korunur (`SchemaUpgradeTest#upgradesVersionOneDatabase`).

`start_coord` ve `coord` için CHECK: `GLOB '[A-J][1-9]' OR GLOB '[A-J]10'` (ROADMAP Bölüm 4'teki not uyarınca düzeltildi). Aynı kural uygulamada `Coordinate.parse` ile de uygulanır.

Zaman damgaları DB'de UTC (`datetime('now')`), arayüzde yerel saat ve `MM/dd/yyyy HH:mm:ss` biçimindedir (`GameController.toLocal`).

## 3. Ne zaman ne yazılır

| Olay | Yazılan kayıt |
|---|---|
| Rakip bağlandı / bilgisayar oyunu başladı | `game` (ACTIVE, mode SERVER / CLIENT / AI), `player` ×2 (upsert; bilgisayar `Computer (Hard)` gibi), `game_player` ×2 |
| Ready | `ship_placement` ×5 (tek transaction) |
| Her atış (iki yön) | `move` (`shooter` = LOCAL / REMOTE, `weapon` = SHOT) |
| Torpido | `move` × vurulan hücre sayısı (`weapon` = TORPEDO) + `ability_use` (`found` = isabet sayısı) |
| Radar | `ability_use` (`found` = bulunan gemi hücresi sayısı) |
| Sohbet | `chat_message` |
| Oyun bitti | `game.status = FINISHED`, `winner_role`, `ended_at` |
| Bağlantı koptu / Stop | `game.status = ABORTED`, `ended_at` |
| Output paneline her satır | `app_log` |

## 4. Örnek sorgular (`StatsRepository`)

Galibiyet/mağlubiyet özeti:

```sql
SELECT COUNT(*) AS games,
       SUM(CASE WHEN status='FINISHED' AND winner_role='LOCAL'  THEN 1 ELSE 0 END) AS wins,
       SUM(CASE WHEN status='FINISHED' AND winner_role='REMOTE' THEN 1 ELSE 0 END) AS losses,
       SUM(CASE WHEN status='ABORTED' THEN 1 ELSE 0 END) AS aborted
FROM game WHERE status <> 'ACTIVE';
```

Role göre galibiyet:

```sql
SELECT winner_role, COUNT(*) FROM game WHERE status='FINISHED' GROUP BY winner_role;
```

Son oyunlar (geçmiş penceresi), oyuncu adlarıyla:

```sql
SELECT g.id, g.mode, g.started_at, g.ended_at, g.remote_ip,
       CASE WHEN g.status='FINISHED' AND g.winner_role='LOCAL' THEN 'WIN'
            WHEN g.status='FINISHED' THEN 'LOSS' ELSE g.status END AS result,
       pl.nickname AS local_nick, pr.nickname AS remote_nick
FROM game g
LEFT JOIN game_player gl ON gl.game_id = g.id AND gl.role = 'LOCAL'
LEFT JOIN player pl      ON pl.id = gl.player_id
LEFT JOIN game_player gr ON gr.game_id = g.id AND gr.role = 'REMOTE'
LEFT JOIN player pr      ON pr.id = gr.player_id
ORDER BY g.started_at DESC, g.id DESC
LIMIT ?;
```

Bir oyunda kim kaç radar / torpido kullandı:

```sql
SELECT user_role, ability, COUNT(*) AS uses, SUM(found) AS found
FROM ability_use WHERE game_id = ?
GROUP BY user_role, ability;
```

Bilgisayara karşı galibiyet oranı:

```sql
SELECT COUNT(*) AS games,
       SUM(winner_role = 'LOCAL') AS wins
FROM game WHERE mode = 'AI' AND status = 'FINISHED';
```

Bir oyundaki isabet sayısı:

```sql
SELECT shooter, COUNT(*) FROM move
WHERE game_id = ? AND result IN ('HIT','SUNK')
GROUP BY shooter;
```

## 5. Elle inceleme

```
sqlite3 data/amiral.db
.tables
SELECT id, mode, status, winner_role FROM game ORDER BY id DESC LIMIT 5;
SELECT turn_no, shooter, coord, result FROM move WHERE game_id = 1;
```
