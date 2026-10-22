# Amiral Battı: Tek Kişilik Uygulama Roadmap'i

Bu dosya tek kişi için hazırlanmıştır. Claude Code bu dosyayı okuyup Faz 0'dan başlayarak adım adım uygulayabilecek düzeyde yazılmıştır. Her görevin dosyası, sınıfı, kabul kriteri ve testi bellidir.

Süre: 8 hafta. Haftalık tahmini çalışma: 12–15 saat. Toplam: yaklaşık 100–120 saat.

---

## 0. Kapsam ve Varsayımlar

### 0.1 Ödev isterleri
- Proje, programlama dilinden bağımsız olarak altyapısında **veritabanı yaklaşımı** olan bir uygulamadır.
- Kullanılacak DBMS'e göre istenen gereksinimler karşılanmalıdır. Bu proje **SQLite** kullanır ve aşağıdaki DB gereksinimlerini karşılar:
  - Oyun, hamle, yerleştirme, sohbet ve log kayıtları kalıcı olarak saklanır.
  - Veriye erişim SQL ile yapılır ve bir katman (Repository) arkasında toplanır.
  - Şema otomatik oluşturulur (`schema.sql`).
  - Oyun geçmişi ve kazanma istatistikleri sorgulanıp arayüzde gösterilir.
  - Yabancı anahtar, CHECK ve UNIQUE kısıtları kullanılır.
- Ekran görüntüsündeki arayüz (BattleShip v1.0) referans alınır.

### 0.2 Varsayımlar (değiştirilecekse Faz 0'da ROADMAP'i güncelleyin)
| Konu | Varsayım | Gerekçe |
|---|---|---|
| Dil | Java 17 | Ekran görüntüsü Swing'e benziyor |
| Arayüz | Java Swing | Ek kurulum gerektirmez |
| Ağ | TCP socket, satır tabanlı metin protokolü | Basit, hata ayıklaması kolay |
| DBMS | SQLite (`sqlite-jdbc`) | Sunucu gerektirmez, tek dosya |
| Build | Maven | Bağımlılıklar `pom.xml` ile yönetilir |
| Test | JUnit 5 | Standart |
| Arayüz metinleri | Ekran görüntüsündeki gibi İngilizce | Referans arayüz İngilizce |
| Kod yorumları ve commit mesajları | İngilizce | Standart |
| Filo | 5, 4, 3, 3, 2 (toplam 17 hücre) | Klasik amiral battı |
| Gemi komşuluğu | Gemiler birbirine bitişik olabilir | Klasik kural. Kısıt istenirse Faz 2'de değiştirilir |
| İlk hamle | Sunucu başlar | Basit ve deterministik |
| Oyuncu sayısı | Bir sunucu, bir istemci | 2 kişilik oyun |
| Kayıt | Yalnızca yerel oyuncunun yerleşimi kaydedilir | Rakibin yerleşimi görünmez |

---

## 1. Teknoloji Yığını

| Bileşen | Sürüm | Not |
|---|---|---|
| JDK | 17 veya üzeri (LTS) | `maven.compiler.release=17` |
| Maven | 3.9+ | |
| `org.xerial:sqlite-jdbc` | 3.x (güncel) | Tek bağımlılık (DB) |
| `org.junit.jupiter:junit-jupiter` | 5.10.x | Test için |
| `maven-surefire-plugin` | 3.x | Testleri çalıştırır |
| `maven-shade-plugin` | 3.x | Tek jar çıktısı (isteğe bağlı, Faz 7) |
| GitHub Actions | `ubuntu-latest` | Her push'ta `mvn verify` (Faz 0) |

---

## 2. Proje Yapısı

Paket kökü: `com.amiral`

```
amiral-battisi/
├── pom.xml
├── README.md
├── ROADMAP.md
├── .gitignore                      (data/, target/, *.db, .idea/, *.iml)
├── .github/workflows/ci.yml
├── docs/
│   ├── PROTOCOL.md                 (Faz 3'te yazılır)
│   ├── DATABASE.md                 (Faz 2'de yazılır)
│   ├── TEST_PLAN.md                (Faz 6'da yazılır)
│   ├── USER_MANUAL.md              (Faz 6'da yazılır)
│   └── diagrams/                   (Faz 7'de: sınıf, ER, use case, sekans)
├── src/main/resources/
│   └── schema.sql
├── src/main/java/com/amiral/
│   ├── App.java                    (main, UI'yi başlatır)
│   ├── model/
│   │   ├── Coordinate.java
│   │   ├── Orientation.java        (H, V)
│   │   ├── ShipType.java           (CARRIER 5, BATTLESHIP 4, CRUISER 3, SUBMARINE 3, DESTROYER 2)
│   │   ├── Ship.java
│   │   ├── Board.java
│   │   ├── ShotResult.java         (MISS, HIT, SUNK, ALREADY_SHOT)
│   │   ├── GameState.java          (SETUP, WAITING, PLAYING, FINISHED, ABORTED)
│   │   ├── GameEngine.java         (kurallar, sıra, kazanma)
│   │   └── RandomFleetGenerator.java
│   ├── net/
│   │   ├── MessageType.java
│   │   ├── Message.java
│   │   ├── ProtocolCodec.java      (encode/decode, Base64 yardımcıları)
│   │   ├── NetworkListener.java    (arayüzü: onMessage, onConnected, onDisconnected, onError)
│   │   ├── GameServer.java
│   │   └── GameClient.java
│   ├── db/
│   │   ├── Database.java           (JDBC bağlantısı, şema kurulumu)
│   │   ├── PlayerRepository.java
│   │   ├── GameRepository.java
│   │   ├── MoveRepository.java
│   │   ├── ShipPlacementRepository.java
│   │   ├── ChatRepository.java
│   │   ├── LogRepository.java
│   │   └── StatsRepository.java
│   ├── controller/
│   │   ├── GameController.java     (model + ağ + DB + UI arasında köprü)
│   │   └── AppEvent.java
│   └── ui/
│       ├── MainFrame.java
│       ├── Theme.java              (renkler, fontlar, boyutlar)
│       ├── BoardPanel.java
│       ├── ShipAllocationPanel.java
│       ├── ConnectionPanel.java
│       ├── ChatPanel.java
│       ├── OutputPanel.java
│       ├── HistoryDialog.java
│       └── StatusBar.java
└── src/test/java/com/amiral/
    ├── model/ (CoordinateTest, BoardTest, GameEngineTest, RandomFleetGeneratorTest)
    ├── net/   (ProtocolCodecTest, MessageTest, GameLoopbackIT)
    ├── db/    (DatabaseTest, *RepositoryTest, StatsRepositoryTest)
    └── controller/ (GameControllerTest)
```

### 2.1 Katman kuralları (ihlali kabul edilmez)
- `model` paketi `java.awt`, `javax.swing`, `java.sql`, `java.net` import etmez. Saf Java'dır.
- `ui` paketi yalnızca `controller` ve `model` içindeki tipleri kullanır. `db` ve `net` paketlerini doğrudan çağırmaz.
- `controller` tüm iş akışını yönetir. UI thread dışından gelen olaylar `SwingUtilities.invokeLater` ile UI'ye iletilir.
- `db` paketi yalnızca SQL ve JDBC bilir. Oyun kuralı içermez.
- `net` paketi yalnızca protokolü ve soketleri bilir. Oyun kuralı içermez.

---

## 3. Geliştirme Kuralları

1. Her görev bir commit ile kapanır (veya birkaç küçük commit).
2. Commit mesajı formatı: `tip(kapsam): kısa açıklama`. Tipler: `feat`, `fix`, `test`, `docs`, `refactor`, `chore`.
   Örnek: `feat(model): add Board.placeShip with overlap check`.
3. Her faz sonunda etiket atılır: `v0.1` (Faz 0), `v0.2` (Faz 1), … `v1.0` (Faz 7).
4. Her faz sonunda `mvn verify` yeşil olmadan bir sonraki faza geçilmez.
5. Kodda `System.out.println` yerine `java.util.logging` kullanılır (Faz 0'da `Logger` yardımcısı eklenir).
6. Her yeni public sınıf için en az bir test yazılır. Model katmanında kapsam hedefi %85+.
7. Veritabanı dosyası (`data/amiral.db`) repoya girmez.
8. Test için bellek içi SQLite kullanılır: `jdbc:sqlite::memory:`.
9. Zaman damgaları: DB'de UTC, arayüzde yerel saat, format `MM/dd/yyyy HH:mm:ss` (ekran görüntüsündeki gibi).

---

## 4. Veritabanı Şeması

Dosya: `src/main/resources/schema.sql`. Uygulama açılışında `Database.init()` bunu çalıştırır. Tüm DDL `IF NOT EXISTS` ile yazılır.

```sql
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
  start_coord  TEXT    NOT NULL CHECK (start_coord GLOB '[A-J][1-9]' OR start_coord = 'J10' OR start_coord GLOB 'A10'),
  orientation  TEXT    NOT NULL CHECK (orientation IN ('H','V')),
  UNIQUE (game_id, ship_type)
);

CREATE TABLE IF NOT EXISTS move (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  game_id     INTEGER NOT NULL REFERENCES game(id) ON DELETE CASCADE,
  turn_no     INTEGER NOT NULL CHECK (turn_no >= 1),
  shooter     TEXT    NOT NULL CHECK (shooter IN ('LOCAL','REMOTE')),
  coord       TEXT    NOT NULL,
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
```

Not: `start_coord` CHECK ifadesini Faz 2'de düzeltin. Tek kural: `^[A-J](10|[1-9])$`. SQLite'ta regex olmadığı için kontrol uygulama tarafında (`Coordinate.parse`) da yapılır. CHECK yalnızca temel uzunluk ve harf kontrolü yapar.

### 4.1 Sorgular (StatsRepository)
- `winsByRole(): Map<String,Integer>`: `SELECT winner_role, COUNT(*) FROM game WHERE status='FINISHED' GROUP BY winner_role`
- `recentGames(limit): List<GameSummary>`: `game` ve `game_player` join'i ile son oyunlar, ters kronolojik.
- `hitCountByShooter(gameId)`: `move` tablosunda `result IN ('HIT','SUNK')` filtresi ve `GROUP BY shooter`.

---

## 5. Ağ Protokolü (özet, ayrıntı `docs/PROTOCOL.md`)

- Transport: TCP, UTF-8, her mesaj tek satır, `\n` ile biter.
- Alan ayırıcı: `|`. Serbest metin alanları (sohbet) Base64 ile kodlanır. Böylece `|` ve satır sonu sorunu olmaz.
- Port varsayılanı: `9090`. Geçerli aralık: `1024–65535`.
- Her iki tarafta da bir okuma iş parçacığı (`Thread`, daemon) çalışır.
- Kalp atışı: her 5 saniyede `PING`, 15 saniye yanıt yoksa bağlantı kopmuş sayılır.

### 5.1 Mesaj listesi

| Yön | Mesaj | Biçim | Açıklama |
|---|---|---|---|
| C→S | `HELLO` | `HELLO\|<nickname-b64>` | Bağlantı sonrası ilk mesaj |
| S→C | `WELCOME` | `WELCOME\|<nickname-b64>` | Kabul |
| S→C | `REJECT` | `REJECT\|<reason>` | Reddetme. Sebep: `ALREADY_CONNECTED`, `VERSION` |
| L↔R | `READY` | `READY\|<fleet>` | Filo hazır. `<fleet>` = `CARRIER:A1H;BATTLESHIP:C3V;...` |
| S→C ve C→S | `START` | `START\|<yourTurn:true/false>` | İki taraf da READY gönderince sunucu yayınlar |
| L→R | `SHOOT` | `SHOOT\|<coord>` | Atış |
| S→C | `SHOT` | `SHOT\|<shooter>\|<coord>\|<MISS/HIT/SUNK>\|<shipType?>` | Rakibin atışını bildirir |
| S→C | `RESULT` | `RESULT\|<coord>\|<MISS/HIT/SUNK>\|<shipType?>` | Kendi atışının sonucu |
| L↔R | `CHAT` | `CHAT\|<sender>\|<body-b64>` | Sohbet, sunucu iletir |
| L↔R | `GAME_OVER` | `GAME_OVER\|<WIN/LOSE>` | Oyun sonu |
| L↔R | `QUIT` | `QUIT` | Oyundan çıkış |
| L↔R | `PING` / `PONG` | `PING`, `PONG` | Kalp atışı |
| S→C | `ERROR` | `ERROR\|<code>\|<message>` | Kod: `INVALID_COORD`, `NOT_YOUR_TURN`, `ALREADY_SHOT`, `INVALID_FLEET`, `GAME_NOT_STARTED` |

Kurallar:
- `<shipType?>` alanı yalnızca `SUNK` durumunda dolu olur. Diğer durumlarda boş bırakılır (`RESULT|A5|HIT|`).
- Sunucu, istemciden gelen her `SHOOT` için sıra ve geçerlilik kontrolü yapar. Hatalıysa `ERROR` döner, sıra değişmez.
- Bağlantı kopması sırasında devam eden oyun `ABORTED` olarak kaydedilir ve rakibe `QUIT` ulaşmadıysa karşı taraf `OPPONENT_LEFT` görür.

---

## 6. Oyun Kuralları

1. Tahta 10×10. Satırlar `A`–`J`, sütunlar `1`–`10`. Koordinat örneği: `A1`, `J10`.
2. Filo: CARRIER(5), BATTLESHIP(4), CRUISER(3), SUBMARINE(3), DESTROYER(2). Toplam 17 hücre.
3. Her gemi düz bir çizgi üzerindedir (yatay `H` veya dikey `V`). Tahta dışına taşamaz, başka gemiyle hücre paylaşamaz.
4. Gemilerin birbirine bitişik olması serbesttir.
5. Yerleşim tamamlanmadan `READY` gönderilemez.
6. İki taraf da READY gönderince oyun başlar. Sunucu ilk hamleyi yapar.
7. Atış sonucu:
   - `MISS`: boş hücre.
   - `HIT`: gemi vuruldu, batmadı.
   - `SUNK`: bu atışla gemi tamamen battı. Gemi tipi bildirilir.
   - Aynı hücreye ikinci kez atış: `ERROR ALREADY_SHOT`, sıra değişmez.
8. Her `HIT` veya `SUNK` sonrası sıra aynı oyuncuda kalmaz. Oyun klasik kuralla devam eder: sıra her atışta karşı tarafa geçer.
9. 17 hücrenin tamamı vurulduğunda oyun biter. Vuran taraf kazanır. `GAME_OVER` iki tarafa gider. Kayıt `FINISHED`.
10. Oyun sırasında bağlantı kopması veya "Stop Server" → kayıt `ABORTED`, kazanan yok.

---

## 7. Arayüz Spesifikasyonu

Ekran görüntüsündeki yapı temel alınır. Eklenen öğeler **[ek]** ile işaretlidir.

### 7.1 Pencere
- Başlık: `BattleShip v1.0`
- Minimum boyut: 1000×680. Açılış boyutu: 1100×700. Pencere ortalanır.
- Menü çubuğu:
  - `Game` → `New Game` (Ctrl+N), `History` (Ctrl+H), `Exit` (Ctrl+Q). **[ek]**
  - `Help` → `About`. **[ek]**
- Düzen (BorderLayout):
  - CENTER: iki tahta yan yana (her biri eşit genişlik). Üstlerinde başlık etiketi: `Your ships`, `Your enemy`.
  - EAST: sağ panel, genişlik 240px. Dört grup kutusu dikey sıralanır: `Ships allocation`, `Connection Configuration`, `Chat History`.
  - SOUTH: `Output` paneli, yükseklik 150px. Log satırları.
  - En alt: durum çubuğu (`StatusBar`), tek satır. **[ek]**

### 7.2 Tahta (BoardPanel)
- `JPanel` alt sınıfı, `paintComponent` ile çizilir. Swing bileşeni yerine özel çizim.
- Izgara: 11×11 (üst satır ve sol sütun etiketleri için bir ekstra). Hücre boyutu panel boyutuna göre hesaplanır, tahta kare kalır.
- Etiketler: sütunlar `1`–`10` üstte, satırlar `A`–`J` solda. Kalın, 14pt.
- Hücre durumları ve renkler (`Theme.java`):

| Durum | Renk (hex) | Not |
|---|---|---|
| Su (boş, bilinmiyor) | `#F4F6F7` | Hücre çizgisi `#D5D8DC` |
| Gemi (yalnızca kendi tahtasında) | `#7F8C8D` | |
| Vuruş (HIT) | `#E74C3C` | Ortada beyaz nokta |
| Batan gemi (SUNK) | `#922B21` | Tüm gemi hücreleri koyu kırmızı |
| Iskalama (MISS) | `#AED6F1` | Ortada koyu mavi nokta |
| Fare üstünde (geçerli) | `#ABEBC6` | Yalnızca yerleşim modunda |
| Fare üstünde (geçersiz) | `#F5B7B1` | Yalnızca yerleşim modunda |

- Düşman tahtasında gemiler **gizlidir**. Yalnızca HIT, MISS, SUNK gösterilir.
- Tıklama olayı: `Consumer<Coordinate>` callback'i. Rakip tahtasında sıra bizde değilse tıklama yok sayılır, imleç `not-allowed` benzeri (`Cursor.DEFAULT_CURSOR`) kalır.
- Sıra bizde değilken tahta hafif soluk görünür (`AlphaComposite` 0.6). **[ek]**

### 7.3 Ships Allocation (ShipAllocationPanel)
- Radyo grubu: `Manual allocation` / `Automatic allocation`. Varsayılan: `Automatic allocation`.
- Düğmeler: `Clear allocation`, `New allocation`. **[ek]** `Ready` düğmesi (yerleşim tamamlanınca etkin).
- Manual modda:
  - Sol tahtaya (`Your ships`) tıklanarak seçili gemi yerleştirilir.
  - Yerleştirilecek gemi seçimi: `JComboBox` veya liste. Metinler: `Carrier (5)`, `Battleship (4)`, `Cruiser (3)`, `Submarine (3)`, `Destroyer (2)`. Yerleştirilen gemi listeden düşer. **[ek]**
  - `R` tuşu veya `Rotate` düğmesi yönü değiştirir. **[ek]**
  - Fare üstünde önizleme: geçerli yerse yeşil, değilse kırmızı.
  - Yerleştirilmiş gemiye sağ tık: geminin tamamı kaldırılır.
- Automatic modda:
  - `New allocation` düğmesi `RandomFleetGenerator` ile rastgele yerleşim üretir.
  - Oyun başlarken yerleşim yoksa otomatik üretilir.
- `Clear allocation` tüm gemileri kaldırır ve yerleşim modunu manuele alır.
- Yerleşim tamamlanmadan `Ready` pasif kalır.

### 7.4 Connection Configuration (ConnectionPanel)
- Radyo grubu: `Play as server` (varsayılan) / `Play as client`.
- `My IP Address:` etiketi, salt okunur metin alanı. Sunucu modunda yerel IPv4 adresi gösterilir (`InetAddress` ile bulunur, bulunamazsa `127.0.0.1`).
  - İstemci modunda etiket `Server IP:` olur ve alan düzenlenebilir. **[ek]**
- `Port:` metin alanı, varsayılan `9090`. Doğrulama: `1024–65535`, tam sayı. Hatalıysa alan kırmızı çerçeveli ve hata mesajı. **[ek]**
- Düğmeler:

| Mod | Durum | Start/Connect | Stop/Disconnect |
|---|---|---|---|
| Server | Bağlantı yok | Aktif | Pasif |
| Server | Dinliyor | Pasif | Aktif |
| Server | Oyunda | Pasif | Aktif (onay sorar) |
| Client | Bağlantı yok | Aktif (`Connect`) | Pasif |
| Client | Bağlı | Pasif | Aktif (`Disconnect`) |

### 7.5 Chat History (ChatPanel)
- Salt okunur `JTextArea` (kaydırmalı, otomatik en alta iner).
- Satır biçimi: `Me: <metin>` (mavi, `#2E86C1`) veya `Enemy: <metin>` (kırmızı, `#C0392B`). Ekran görüntüsündeki gibi `TestServer` gibi takma adlar gösterilebilir. **[ek]**
- Alt kısımda giriş alanı ve `Send` düğmesi. `Enter` tuşu da gönderir. **[ek]**
- Maksimum 500 karakter. Boş mesaj gönderilmez.
- Sohbet bağlantı yokken pasiftir.

### 7.6 Output (OutputPanel)
- Salt okunur, kaydırmalı `JTextArea`, yazı tipi monospace.
- Satır formatı: `MM/dd/yyyy HH:mm:ss - <mesaj>`. Örnek: `12/27/2015 17:43:48 - Server is opened!`
- Her olay bu panele yazılır ve `app_log` tablosuna kaydedilir.
- Panel en fazla son 500 satırı tutar.

### 7.7 Durum çubuğu (StatusBar) **[ek]**
- Metinler: `Idle`, `Waiting for opponent...`, `Placing ships`, `Your turn`, `Enemy's turn`, `Game over: you won`, `Game over: you lost`, `Disconnected`.

### 7.8 Geçmiş penceresi (HistoryDialog) **[ek]**
- Modal olmayan `JDialog`. Menüden açılır.
- Üst kısım: istatistik satırı. `Games: X · Wins: Y · Losses: Z · Win rate: %W`.
- Alt kısım: `JTable`, sütunlar: `ID`, `Mode`, `Started`, `Ended`, `Remote IP`, `Result`. Ters kronolojik.
- Satıra çift tık: oyunun hamleleri listelenir (ikinci bir tablo). Bu özellik isteğe bağlıdır, Faz 5'te yapılmazsa Faz 7'ye bırakılır.

### 7.9 Hata ve onay diyalogları **[ek]**
- Bağlantı hataları: `JOptionPane.showMessageDialog` (ERROR).
- `Stop Server` veya `Exit` sırasında oyun aktifse onay: `A game is in progress. Abort it?`.
- Rakip çıkarsa: `Opponent has left the game.` bilgi mesajı.

### 7.10 Tuş kısayolları **[ek]**
- `R`: yerleştirmede yön değiştir.
- `Enter`: sohbet gönder.
- `Ctrl+N`, `Ctrl+H`, `Ctrl+Q`: menü kısayolları.

---

## 8. Haftalık Plan

Her hafta bir fazdır. Görevler sırasıyla yapılır. Her görev için **kabul kriteri** sağlanmadan bir sonrakine geçilmez.

### Hafta 1: Faz 0 (Kurulum) ve Faz 1 Başlangıcı (Model)

#### T-00 Repo ve Maven iskeleti
- Yap: `pom.xml` (Java 17, JUnit 5, sqlite-jdbc), dizin yapısı (Bölüm 2), `.gitignore`, `README.md` (kısa), `ROADMAP.md`.
- `App.java`: `main` boş pencere açar, başlık `BattleShip v1.0`.
- `Logger` yardımcı sınıfı: `com.amiral.util.AppLogger` (java.util.logging, konsol).
- GitHub Actions `ci.yml`: `actions/checkout`, `actions/setup-java` (temurin 17), `mvn -B verify`.
- Kabul:
  - `mvn -B verify` yerelde ve CI'da yeşil.
  - `mvn -q exec:java -Dexec.mainClass=com.amiral.App` pencere açar.
  - `git tag v0.1`.

#### T-01 Coordinate
- Dosya: `model/Coordinate.java` (record).
- Alanlar: `char row` (A–J), `int col` (1–10).
- Metotlar:
  - `static Coordinate parse(String s)`: büyük/küçük harf duyarsız, `"A1"`, `"j10"` geçerli. Geçersizde `IllegalArgumentException`.
  - `String toString()`: `A1` biçimi.
  - `static Coordinate of(int rowIndex, int col)`: 0–9 satır indeksi.
  - `Optional<Coordinate> offset(int dRow, int dCol)`: sınır dışıysa boş.
- Test (`CoordinateTest`): geçerli ve geçersiz girdiler, sınırlar (`A1`, `J10`, `K1`, `A11`, `A0`, boş metin, `null`).
- Kabul: tüm testler yeşil, kapsam %100.

#### T-02 Orientation, ShipType, Ship
- `Orientation`: `H`, `V`.
- `ShipType`: enum, alanlar `displayName` ve `length`. Değerler: `CARRIER("Carrier", 5)`, `BATTLESHIP("Battleship", 4)`, `CRUISER("Cruiser", 3)`, `SUBMARINE("Submarine", 3)`, `DESTROYER("Destroyer", 2)`.
- `Ship`: alanlar `ShipType type`, `Coordinate start`, `Orientation orientation`. Metot `List<Coordinate> cells()` (sınır dışına taşarsa `IllegalStateException`), `boolean occupies(Coordinate)`, `boolean isSunk(Set<Coordinate> hits)`.
- Test: yatay ve dikey hücre listesi, `J10` başlangıçta H ile taşma, `isSunk` mantığı.
- Kabul: testler yeşil.

#### T-03 ShotResult ve GameState
- `ShotResult`: `MISS`, `HIT`, `SUNK`, `ALREADY_SHOT`.
- `GameState`: `SETUP`, `WAITING`, `PLAYING`, `FINISHED`, `ABORTED`.
- Kabul: derlenir, kullanılır (Faz 1'de).

### Hafta 2: Faz 1 (Oyun Motoru)

#### T-04 Board
- Alanlar: `Map<Coordinate, Ship>` (hücre → gemi), `Set<Coordinate> shots` (gelen atışlar), `List<Ship> ships`.
- Metotlar:
  - `boolean canPlace(Ship s)`: sınır, çakışma kontrolü.
  - `void placeShip(Ship s)`: `canPlace` yoksa `IllegalArgumentException`.
  - `boolean isFleetComplete()`: beş gemi ve 17 hücre.
  - `ShotResult receiveShot(Coordinate c)`: daha önce atıldıysa `ALREADY_SHOT`. Boşsa `MISS`. Gemi varsa: hücre vurulur. Gemi tamamen vurulduysa `SUNK`, değilse `HIT`. Sonuç `ShotResult` ile birlikte batan gemi tipini de döndürmek için `ShotOutcome(ShotResult result, ShipType sunkType)` record'u kullanılır.
  - `boolean allSunk()`: 17 hücre vuruldu.
  - `Optional<ShipType> sunkShipAt(Coordinate c)`.
- Test (`BoardTest`):
  - Yatay ve dikey yerleşim.
  - Çakışma ve sınır hatası.
  - `MISS`, `HIT`, `SUNK` sırası.
  - `ALREADY_SHOT`.
  - Tüm filo batınca `allSunk() == true`.
  - Kapsam %90+.

#### T-05 RandomFleetGenerator
- `static Board generate(Random rnd)`: her gemi için rastgele başlangıç ve yön. Çakışma veya taşma olursa aynı gemi için yeniden dener. 1000 denemede yerleşemezse `IllegalStateException` (pratikte olmaz, ama yazılır).
- `Random` parametre olarak alınır, testte `new Random(42)` ile deterministik.
- Test: 1000 üretimde her biri geçerli filo (17 hücre, çakışma yok, sınırlar içinde). Tekrar üretimde aynı seed aynı sonuç.

#### T-06 GameEngine
- Sorumluluk: iki tahta, sıra, durum geçişleri, kazanan.
- Alanlar: `Board local`, `Board remote` (karşı tarafın yerleşimi biz bilmiyoruz, bu yüzden her iki tarafın hamle sonuçlarını ayrı izleriz). Bunun yerine motor yalnızca tek tahta tutar (kendi tahtamız) ve karşı tahtanın hamle geçmişini `Set<Coordinate> myShots` ve sonuçlarını `Map<Coordinate, ShotResult> myShotResults` ile tutar.
- Metotlar:
  - `void setLocalFleet(Board b)`: yalnızca `SETUP` durumunda. Tam filo değilse hata.
  - `void markReady(boolean local)`: iki taraf da hazırsa `PLAYING`. İlk sıra sunucu (`localIsServer` parametresi ile).
  - `ShotOutcome fireAt(Coordinate c)`: yerel oyuncunun atışını kaydeder (karşı tahtaya), `myShots`'a ekler, sıra değişir. Sonuç rakipten gelir, bu yüzden motor sonucu dışarıdan alır: `void applyOpponentShotResult(Coordinate c, ShotResult r, ShipType sunk)`.
  - `ShotOutcome receiveEnemyShot(Coordinate c)`: rakip atışı, kendi tahtamızda `receiveShot` çağrılır, sonuç döner.
  - `Optional<Boolean> winner()`: `Optional.of(true)` yerel kazandı, `Optional.of(false)` rakip kazandı, boş ise oyun devam.
- Durum geçişleri:
  - `SETUP → WAITING` (READY gönderildi, rakip hazır değil).
  - `SETUP → PLAYING` veya `WAITING → PLAYING` (iki taraf hazır).
  - `PLAYING → FINISHED` (kazanan var).
  - Herhangi → `ABORTED` (bağlantı koptu).
- Test (`GameEngineTest`):
  - Geçersiz durum geçişleri hata fırlatır.
  - Sıra değişimi.
  - Yanlış sıra ile atış hata döner.
  - Kazanma koşulu.
  - `ABORTED` sonrası atış kabul edilmez.

#### T-07 Message ve ProtocolCodec (model değil, net paketinde ama saf Java, bu hafta yazılır)
- `MessageType` enum (Bölüm 5.1'deki tipler).
- `Message` record: `MessageType type`, `List<String> fields`.
- `ProtocolCodec`:
  - `String encode(Message m)`: alanları `|` ile birleştirir. Alan içinde `|` veya `\n` varsa `IllegalArgumentException`.
  - `Message decode(String line)`: boş satır, bilinmeyen tip, eksik alan için `ProtocolException`.
  - `String b64(String plain)` ve `String unb64(String encoded)`: `Base64.getUrlEncoder().withoutPadding()`.
  - Fleet kodlama: `encodeFleet(Map<ShipType, Ship>)` ve `decodeFleet(String)`. Format: `CARRIER:A1H;BATTLESHIP:C3V;...`.
- Test (`ProtocolCodecTest`):
  - Her mesaj tipi için encode-decode turu.
  - Bozuk satırlar (boş, bilinmeyen tip, eksik alan).
  - Türkçe karakter içeren sohbet metni Base64 ile doğru gider.
  - Alan içinde `|` olduğunda hata.
- Kabul: testler yeşil. `git tag v0.2` (Faz 1 + T-07 bitince).

### Hafta 3: Faz 2 (Veritabanı)

#### T-08 Database ve şema kurulumu
- `db/Database.java`:
  - `static Database open(String jdbcUrl)`: bağlantı açar, `PRAGMA foreign_keys = ON`.
  - `void initSchema()`: `schema.sql` dosyasını classpath'ten okuyup `;` ile ayırarak çalıştırır.
  - Varsayılan dosya: `data/amiral.db`. `data/` yoksa oluşturulur.
  - `close()`.
- Test (`DatabaseTest`): bellek içi DB, şema oluşturma, tüm tabloların varlığı (`sqlite_master` sorgusu), iki kez `initSchema` çağrıldığında hata olmaması (idempotent).
- Kabul: testler yeşil.

#### T-09 Repository sınıfları
- `PlayerRepository`: `long upsert(String nickname)`, `Optional<Player> findByNickname(String)`.
- `GameRepository`:
  - `long createGame(String mode, String remoteIp, Integer port)` → `ACTIVE`.
  - `void finish(long gameId, String winnerRole)` → `FINISHED`, `ended_at` set.
  - `void abort(long gameId)` → `ABORTED`, `ended_at` set.
  - `void linkPlayers(long gameId, long localId, long remoteId)` → `game_player` kayıtları.
  - `Optional<GameRecord> findById(long)`.
- `ShipPlacementRepository`: `saveFleet(long gameId, Map<ShipType, Ship>)`, `List<ShipPlacement> findByGame(long)`.
- `MoveRepository`: `void save(long gameId, int turnNo, String shooter, Coordinate c, ShotResult r)`, `List<Move> findByGame(long)`.
- `ChatRepository`: `void save(long gameId, String sender, String body)`, `List<ChatMessage> findByGame(long)`.
- `LogRepository`: `void info|warn|error(Long gameId, String message)`.
- Hepsi `PreparedStatement` kullanır. String birleştirme ile SQL yazılmaz.
- Test: her repository için CRUD testi, `UNIQUE` ihlali (aynı hücreye iki atış) `SQLException` fırlatır, `CHECK` ihlali (`orientation='X'`) fırlatır, `ON DELETE CASCADE` çalışır.
- Kabul: testler yeşil.

#### T-10 StatsRepository
- `StatsSummary summary()`: toplam oyun, galibiyet, mağlubiyet, galibiyet oranı.
- `List<GameSummary> recentGames(int limit)`: `game` ve `game_player` join.
- `Map<String,Long> shotsByShooter(long gameId)`.
- Test: önceden kurulmuş veriyle beklenen sayılar.

#### T-11 DB'yi uygulamaya bağlama
- `App.java`: başlangıçta `Database.open("jdbc:sqlite:data/amiral.db")` ve `initSchema()`. Bağlantı hatası kullanıcıya `JOptionPane` ile bildirilir ve uygulama çıkar.
- Kabul: uygulama ilk açılışta `data/amiral.db` dosyasını oluşturur. Tablolar vardır (`sqlite3` CLI ile kontrol).
- `git tag v0.3`.

### Hafta 4: Faz 3 (Ağ)

#### T-12 NetworkListener ve soket altyapısı
- `NetworkListener` arayüzü: `onConnected(String remote)`, `onMessage(Message m)`, `onDisconnected(String reason)`, `onError(Exception e)`.
- `Connection` sınıfı (ortak): `Socket`, `BufferedReader`, `PrintWriter` (autoFlush), okuma thread'i (daemon), `send(Message)` (synchronized), `close()`.
- Okuma thread'i: satır okur, `ProtocolCodec.decode` çağırır, bozuk satır `onError` olarak bildirilir ama bağlantıyı kesmez.
- Heartbeat: `ScheduledExecutorService` ile 5 saniyede `PING`. 15 saniye içinde `PONG` yoksa `onDisconnected("timeout")`.
- Test: loopback'te iki `Connection` ile mesaj gidiş-dönüşü.

#### T-13 GameServer
- Alanlar: `ServerSocket`, `Connection client`, `NetworkListener listener`.
- Metotlar:
  - `void start(int port)`: `ServerSocket` açar, port 0 ise sistem seçer (test için). Ayrı thread'de `accept()`.
  - `void stop()`: soketleri kapatır, `onDisconnected` çağırır.
  - İlk bağlanan istemci kabul edilir. İkinci bağlantı `REJECT|ALREADY_CONNECTED` alır ve kapatılır.
  - `HELLO` alınınca `WELCOME` gönderilir.
  - Istemci `READY` gönderdiğinde iki taraf hazırsa `START` yayınlanır.
- Test: `GameLoopbackIT`'te port 0 ile başlat, istemci bağlansın, HELLO/WELCOME ve REJECT senaryoları.

#### T-14 GameClient
- `connect(String host, int port, String nickname)`: bağlanır, `HELLO` gönderir, `WELCOME` bekler (zaman aşımı 5 saniye).
- `disconnect()`: `QUIT` gönderir, soketi kapatır.
- `send(...)`: `SHOOT`, `READY`, `CHAT` için yardımcı metotlar.
- Test: loopback'te server ↔ client tam el sıkışma.

#### T-15 Oyun protokolü uçtan uca (ağ katmanında)
- Server ve client arasında `READY`, `START`, `SHOOT`, `SHOT`, `RESULT`, `CHAT`, `GAME_OVER` akışı.
- Oyun kuralı kontrolü YAPILMAZ, bu `controller` katmanında olacaktır. Ağ katmanı sadece mesajları iletir.
- Test: iki tarafın mesaj sırasını doğrulayan bir senaryo (`GameLoopbackIT#fullMessageFlow`).
- `docs/PROTOCOL.md` yazılır: Bölüm 5'in tam hali, örnek mesaj dizisi (sekans diyagramı için).
- Kabul: loopback testi yeşil. `git tag v0.4`.

### Hafta 5: Faz 4 (Arayüz, sahte veriyle)

Bu haftada arayüz `controller` olmadan, sahte (mock) veriyle geliştirilir. Amaç: tüm ekranın görsel olarak çalışmasıdır.

#### T-16 Theme ve BoardPanel
- `Theme.java`: Bölüm 7.2'deki renkler, yazı tipleri (`Font.SANS_SERIF`, 13pt; etiketler 14pt bold), boşluk sabitleri.
- `BoardPanel`:
  - `enum CellView { WATER, SHIP, HIT, SUNK, MISS, PREVIEW_OK, PREVIEW_BAD }`.
  - `void setCell(Coordinate c, CellView v)`, `void setInteractive(boolean)`, `void setCellClickListener(Consumer<Coordinate>)`.
  - Hover takibi: `MouseMotionListener` ile geçerli hücre vurgulanır.
  - Kare kalır: `getPreferredSize` 360×360, `paintComponent` oranı korur.
- Kabul: 10×10 örnek tahta çizilir, hover ve tıklama çalışır (sahte veriyle, `MainFrame` içinde bir demo modu).

#### T-17 ShipAllocationPanel
- Bölüm 7.3'teki tüm öğeler.
- Manuel yerleşim durumu makinesi: `selectedShip`, `orientation`, `placedShips`. Yerleşim değişince `onFleetChanged(boolean complete)` callback'i.
- Rastgele yerleşim için `RandomFleetGenerator` çağrısı (`New allocation`).
- Kabul: manuel yerleştirme, döndürme (R), sağ tık ile kaldırma, `Ready` düğmesinin doğru etkinleşmesi (demo modunda).

#### T-18 ConnectionPanel
- Bölüm 7.4'teki tablo birebir uygulanır. Düğme etkinlik kuralları tek bir metotta (`refreshButtons(ConnectionState s)`) toplanır.
- Port doğrulama: `InputVerifier` veya değişiklik olayı ile.
- IP tespiti: `NetworkInterface` üzerinden IPv4, döner `String`.
- Kabul: her mod ve durum için düğme durumları testte kontrol edilir (`ConnectionPanelTest` ile `isEnabled` kontrolü, headless-uyumlu ise).

#### T-19 ChatPanel, OutputPanel, StatusBar
- `ChatPanel`: `appendMessage(String sender, String body)`, `Enter` ile gönder (`ActionMap`), 500 karakter sınırı.
- `OutputPanel`: `log(String message)` zaman damgalı ekler, 500 satır sınırı, otomatik kaydırma.
- `StatusBar`: `setStatus(String)`.
- Kabul: demo modunda sohbet ve log görünür, `Enter` gönderir.

#### T-20 MainFrame ve HistoryDialog
- `MainFrame`: BorderLayout düzeni (Bölüm 7.1), menü çubuğu, kısayollar.
- `HistoryDialog`: Bölüm 7.8. Veri `StatsRepository`'den gelmez, bu aşamada arayüz bir `HistoryProvider` arayüzü üzerinden çalışır (sahte uygulama ile).
- Kabul: tüm pencere açılır, boyutlandırılır, menü çalışır, geçmiş diyaloğu sahte veriyle listeler.

### Hafta 6: Faz 5 (Entegrasyon ve Controller)

#### T-21 GameController
- Sorumluluk: `GameEngine`, `GameServer`/`GameClient`, repository'ler ve `MainFrame` arasında köprü.
- Olaylar `AppEvent` sealed interface ile tanımlanır (`ServerStarted`, `ClientConnected`, `ShipsReady`, `GameStarted`, `ShotFired`, `ShotReceived`, `ChatReceived`, `GameOver`, `Disconnected`, `Error`).
- Controller her ağ olayını işler, `GameEngine`'i günceller, DB'ye yazar, UI'ye `SwingUtilities.invokeLater` ile iletir.
- Kritik akış (sunucu tarafı):
  1. `startServer(port)` → `GameRepository.createGame("SERVER", ip, port)`, log `Server is opened!`.
  2. İstemci bağlanır → log `<nick> wants to join`, `WELCOME`.
  3. Her iki taraf `READY` → engine `PLAYING`, `START` gönderilir, log `Your turn` veya `Enemy's turn`.
  4. Atış: `fireAt` → `SHOOT` → rakip `SHOT` döner (kendi tahtasında sonucu hesaplar ve `RESULT` gönderir). Her iki taraf da hamleyi DB'ye yazar.
  5. Oyun biter → `GAME_OVER` iki taraf, `GameRepository.finish`.
- Test (`GameControllerTest`): Sahte ağ ve sahte repository ile tam bir oyun akışı, sıra ve kazanma doğrulaması.
- Kabul: testler yeşil.

#### T-22 Yerleşim kaydı ve hamle kaydı
- `READY` anında `ShipPlacementRepository.saveFleet`.
- Her atışta `MoveRepository.save`. Her iki taraf da kendi hamlesini kaydeder (`shooter` alanı yerel ya da uzak).
- Sohbet: her mesaj `ChatRepository.save`.
- Kabul: oyun sonunda DB'de yerleşim (5 gemi), hamleler (en az 1), sohbet kayıtları vardır. `sqlite3` ile kontrol.

#### T-23 Hata senaryoları
- Karşı taraf bağlantıyı keserse: `ABORTED`, log, durum çubuğu, UI pasif.
- Geçersiz koordinat: `ERROR INVALID_COORD` gösterilir, sıra değişmez.
- Sırası olmayan atış: `ERROR NOT_YOUR_TURN`.
- Aynı hücreye ikinci atış: `ERROR ALREADY_SHOT`.
- Port kullanımda: `BindException` yakalanır, kullanıcıya `Port is in use` mesajı.
- Kabul: her senaryo için otomatik test veya manuel doğrulama (Faz 6'da tabloya bağlanacak).
- `git tag v0.5`.

### Hafta 7: Faz 6 (Test, Düzeltme, Dokümantasyon)

#### T-24 Otomatik test kapsamı
- `mvn verify` içinde `jacoco-maven-plugin` ile kapsam raporu (isteğe bağlı, ama önerilir).
- Hedef: `model` %85+, `net` %70+, `db` %80+, `controller` %70+.

#### T-25 Manuel test planı (`docs/TEST_PLAN.md`)
Aşağıdaki senaryolar sırayla çalıştırılır ve her biri `PASS/FAIL` işaretlenir:

| ID | Senaryo | Beklenen |
|---|---|---|
| TP-01 | Uygulama ilk açılış | `data/amiral.db` oluşur, pencere açılır |
| TP-02 | Otomatik yerleşim | 5 gemi, çakışma yok, `Ready` aktif |
| TP-03 | Manuel yerleşim, geçersiz konum | Kırmızı önizleme, yerleşmez |
| TP-04 | Manuel yerleşim, döndürme (R) | Yön değişir |
| TP-05 | Sağ tık ile gemi kaldırma | Gemi listeye geri döner |
| TP-06 | Port 9090 ile sunucu başlatma | Log `Server is opened!`, IP görünür |
| TP-07 | Port 80 girişi | Doğrulama hatası |
| TP-08 | Aynı makinede istemci bağlantısı (ikinci instance) | `WELCOME`, log güncellenir |
| TP-09 | Üçüncü bağlantı denemesi | `REJECT` |
| TP-10 | İki taraf da Ready | Oyun başlar, sıra sunucuda |
| TP-11 | Sıra dışı tıklama | Hamle yapılmaz |
| TP-12 | Atış: MISS, HIT, SUNK | Renkler doğru, log doğru |
| TP-13 | Aynı hücreye ikinci atış | `ALREADY_SHOT`, sıra değişmez |
| TP-14 | Sohbet gönderme (Enter ile) | Karşı tarafta görünür |
| TP-15 | Türkçe karakterli sohbet (ş, ğ, ı, ö, ü, ç) | Doğru görünür |
| TP-16 | Tüm filoyu batırma | `GAME_OVER`, durum çubuğu, DB `FINISHED` |
| TP-17 | Oyun sırasında istemci kapatma | Sunucu `ABORTED`, log, UI pasif |
| TP-18 | Oyun sırasında Stop Server | Onay sorusu, sonra kapanır |
| TP-19 | Geçmiş penceresi | Oyunlar ve istatistik doğru |
| TP-20 | Uygulamayı yeniden açma | Önceki kayıtlar geçmişte |
| TP-21 | İki bilgisayar arası ağ (LAN) | Aynı oyun akışı |

#### T-26 Hata düzeltme ve UX cilası
- TEST_PLAN'deki `FAIL`ler düzeltilir.
- UX kontrol listesi:
  - Tüm düğmelerin tooltip metni var.
  - Hata mesajları Türkçe veya İngilizce tutarlı (Bölüm 0.2'deki karar).
  - Sohbet ve log otomatik en alta kayar.
  - Pencere küçültüldüğünde tahtalar kare kalır, metinler kesilmez.
  - Tüm arayüz donmadan çalışır (ağ işlemleri EDT dışında).

#### T-27 Dokümantasyon
- `README.md`: proje tanımı, gereksinimler (JDK 17, Maven), çalıştırma komutları, ekran görüntüsü.
- `docs/USER_MANUAL.md`: adım adım kullanım (sunucu, istemci, yerleşim, oyun).
- `docs/DATABASE.md`: ER tablosu, şema açıklaması, örnek sorgular.
- `docs/PROTOCOL.md`: Bölüm 5'in tam hali.
- Kabul: README'deki komutlar temiz bir klonda çalışır.
- `git tag v0.6`.

### Hafta 8: Faz 7 (Teslim, Rapor, Sunum, Tampon)

#### T-28 UML diyagramları (`docs/diagrams/`)
- Sınıf diyagramı (model, controller, net, db paketleri ayrı).
- ER diyagramı (Bölüm 4'teki tablolar).
- Use case diyagramı: Oyuncu (sunucu rolü), Oyuncu (istemci rolü), Sistem.
- Sekans diyagramı: bağlantı, yerleşim, bir atış, oyun sonu.
- Araç: PlantUML (metin kaynağı olarak repoda tutulur) veya draw.io (`.drawio` dosyası).

#### T-29 Proje raporu
- Yapı: Giriş, gereksinimler, teknoloji seçimi ve gerekçeler, veritabanı tasarımı (ER, DDL, sorgular), ağ protokolü, oyun motoru tasarımı, arayüz, test sonuçları (TEST_PLAN tablosu), sonuç ve geliştirme önerileri.
- Her bölümün kaynağı repodaki dokümanlardır, rapor bunları tekrar eder ve özetler.

#### T-30 Sunum ve demo
- 10–12 slayt: problem, mimari, DB tasarımı, protokol, demo, test sonuçları, sonuç.
- Canlı demo senaryosu: iki pencere (sunucu ve istemci), Otomatik yerleşim, 5 hamle, sohbet, tam oyun, geçmiş penceresi.
- Yedek plan: demo kaydı (ekran videosu, kısa) hazır olsun.

#### T-31 Son sürüm
- `mvn -B verify` yeşil.
- `git tag v1.0`, GitHub Release notları.
- Tampon: Hafta 1–7'de gecikme varsa buradan telafi edilir. Tampon yoksa en son yapılacak iş: `T-29` ve `T-30`'un tekrar gözden geçirilmesi.

---

## 9. Claude Code için Çalıştırma Promptları

Her faz için aşağıdaki prompt, Claude Code'a tek seferde verilebilir. Önce ana kuralları yükleyin.

### 9.1 Ana kural (her oturumun başında)
```
ROADMAP.md dosyasını baştan sona oku. Kurallara (Bölüm 3) ve katman kurallarına (Bölüm 2.1) uy.
Sırayla ilerle. Bir görevi bitirmeden sonrakine geçme.
Her görev için: önce testi yaz, sonra kodu, sonra `mvn -B verify` çalıştır.
Test kırmızıysa düzeltmeden devam etme.
Her görev sonunda Bölüm 3'teki formatta commit at.
Faz sonunda ilgili git tag'i at ve bana kısa bir özet ver: tamamlanan görevler, kalan sorunlar.
Belirsiz bir karar çıkarsa ROADMAP.md'nin Bölüm 0.2'sindeki varsayımı kullan ve bunu commit mesajında belirt.
```

### 9.2 Faz 0–1 (Hafta 1–2)
```
Faz 0 ve Faz 1'i uygula: T-00'dan T-07'ye kadar. Model paketi saf Java olmalı.
Her model sınıfı için JUnit 5 testi yaz. T-07'de ProtocolCodec bitince v0.2 tag'ini at.
```

### 9.3 Faz 2 (Hafta 3)
```
T-08'den T-11'e kadar veritabanı katmanını uygula.
Şema için src/main/resources/schema.sql kullan. Testler bellek içi SQLite ile çalışsın.
Uygulama açılışta data/amiral.db oluşturmalı. v0.3 tag'ini at.
```

### 9.4 Faz 3 (Hafta 4)
```
T-12'den T-15'e kadar ağ katmanını uygula. Testler loopback üzerinden port 0 ile çalışsın.
Oyun kuralı ağ paketine girmesin. docs/PROTOCOL.md'yi yaz. v0.4 tag'ini at.
```

### 9.5 Faz 4 (Hafta 5)
```
T-16'dan T-20'ye kadar arayüzü uygula. Bu aşamada controller yok, bir demo veri
sağlayıcısı ile çalışsın. Tüm renkler ve düzen Bölüm 7'ye birebir uysun.
Headless ortamda test edilebilir kısımları JUnit ile test et.
```

### 9.6 Faz 5 (Hafta 6)
```
T-21'den T-23'e kadar GameController'ı yaz, arayüzü ve ağı bağla, DB kayıtlarını ekle.
GameControllerTest ile sahte ağ ve sahte repository kullanarak tam oyun akışını test et.
Bölüm 7.9'daki hata diyaloglarını ekle. v0.5 tag'ini at.
```

### 9.7 Faz 6 (Hafta 7)
```
T-24'ten T-27'ye kadar test kapsamını, TEST_PLAN.md'yi, UX cilasını ve dokümantasyonu yap.
TEST_PLAN.md'deki her senaryo için otomatik test varsa yaz, yoksa manuel adım olarak bırak.
v0.6 tag'ini at.
```

### 9.8 Faz 7 (Hafta 8)
```
T-28'i uygula: PlantUML kaynaklarını docs/diagrams/ altına yaz.
T-29 için rapor iskeletini (docs/REPORT.md) doldur, kaynakları repodaki dokümanlardan al.
Son olarak mvn -B verify çalıştır ve v1.0 tag'ini at.
```

---

## 10. Riskler ve Önlemler

| Risk | Olasılık | Etki | Önlem |
|---|---|---|---|
| Ağ kodunda zamanlama hataları (race condition) | Yüksek | Yüksek | Tüm `send` çağrıları `synchronized`. UI güncellemesi yalnızca EDT'de. Protokol Hafta 4'te kilitlenir. |
| Swing EDT donması | Orta | Orta | Ağ ve DB işlemleri EDT dışında. `SwingWorker` kullanımı. |
| Arayüz ve oyun mantığı karışması | Orta | Yüksek | Katman kuralları (Bölüm 2.1). `model` paketi `javax.swing` import edemez (ArchUnit testi isteğe bağlı). |
| Hafta 6'da entegrasyon bitmiyor | Orta | Yüksek | Hafta 7 ve 8 tamponu. Eksikse ek özellikler (geçmiş hamle listesi) atlanır. |
| SQLite dosyası kilitlenmesi | Düşük | Orta | Tek bağlantı, kısa transaction. Yazmalar sıralı. |
| Yerel IP tespiti yanlış adres döndürür | Orta | Düşük | Birden fazla arayüz varsa kullanıcı IP'yi elle yazabilir. Alan düzenlenebilir bırakılır. |
| Tek kişi olmanın yorgunluğu | Yüksek | Orta | Her hafta bir faz. Hafta sonu ek iş yok. Tampon haftası zorunlu. |

---

## 10b. Faz 8: Ödev İsterlerindeki Ek Özellikler (v1.1)

Ödev isterleri (`docs/odev-isterleri.png`) ROADMAP'te eksik olan şu maddeleri içeriyordu; v1.1'de eklendi:

| Görev | İçerik | Kabul |
|---|---|---|
| T-32 | `model/Weapon.java`: SHOT, RADAR (4×4, 3 hak), TORPEDO (3×3, 2 hak); alan kenarda tahtanın içine kaydırılır | `WeaponTest` |
| T-33 | `GameEngine.use` / `receiveEnemyRadar` / `receiveEnemyTorpedo`; her kullanım sırayı geçirir; hak ve sıra kontrolü | `GameEngineWeaponsTest` |
| T-34 | Protokol: `RADAR`, `RADAR_RESULT`, `TORPEDO`, `TORPEDO_RESULT`, hata kodu `NO_ABILITY_LEFT` | `ProtocolCodecTest`, `docs/PROTOCOL.md` |
| T-35 | DB: `game.mode` için `'AI'`, `move.weapon`, `ability_use` tablosu ve hak tetikleyicisi; v1.0 veritabanının yükseltilmesi | `SchemaUpgradeTest` |
| T-36 | `model/ComputerPlayer.java` (Easy / Normal / Hard), `controller/ComputerGateway.java` (aynı protokolle konuşan yerel rakip) | `ComputerPlayerTest`, `ComputerGameTest` |
| T-37 | UI: FlatLaf koyu tema, `ShipPainter` (çizilmiş gemiler), animasyonlu `BoardPanel` (deniz, atış, torpido, Oracle Lens tarzı radar), `WeaponPanel`, `FleetStatusPanel`, `TurnBanner`, `Play vs computer` + zorluk | `UiComponentsTest`, ekran görüntüleri |
| T-38 | 3 test case dokümanı: `docs/test-cases/TC-01…TC-03` | Dokümanlar |

Varsayımlar (Bölüm 0.2'ye ek): radar ve torpido hamle sayılır ve sırayı geçirir; radar gemi hücrelerini gösterir ama vurmaz; torpido alandaki yeni hücrelerin hepsine ateş eder; bilgisayara karşı ilk hamle oyuncunundur.

Not: Ödev isterlerine göre ekip en az 2 kişi olmalıdır; tek kişi yapanlardan 20 puan düşülür.

## 11. Teslim Kontrol Listesi

- [ ] Ödev isterleri (Bölüm 0.1) karşılandı ve her biri için bir kanıt var (kod, test, ekran görüntüsü).
- [ ] Veritabanı: şema, kısıtlar, sorgular, kalıcılık, geçmiş penceresi çalışıyor.
- [ ] Arayüz, referans ekran görüntüsüyle karşılaştırıldı, eksik öğe yok.
- [ ] İki farklı bilgisayarda (LAN) oyun oynandı.
- [ ] `TEST_PLAN.md` tüm senaryolar `PASS`.
- [ ] `mvn -B verify` yeşil, CI rozeti README'de.
- [ ] README, USER_MANUAL, PROTOCOL, DATABASE dokümanları güncel.
- [ ] UML diyagramları (sınıf, ER, use case, sekans) repoda.
- [ ] Rapor ve sunum hazır. Demo yedek videosu var.
- [ ] `v1.0` tag'i atıldı.
- [ ] Bilgisayara karşı oyun, radar ve torpido çalışıyor; `v1.1` tag'i atıldı.
- [ ] 3 test case dokümanı (`docs/test-cases/`) hazır.
