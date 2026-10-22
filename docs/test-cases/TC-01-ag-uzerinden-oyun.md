# Test Case TC-01: Ağ Üzerinden İnsan–İnsan Oyunu

| Alan | Değer |
|---|---|
| Test Case ID | TC-01 |
| Başlık | İki oyuncunun TCP ağı üzerinden bağlanıp bir oyunu sonuna kadar oynaması |
| İlgili ister | "Oyunda Network kullanarak insan ile oynama özelliği olsun" (1 puan) |
| Öncelik | Yüksek |
| Tür | Fonksiyonel, entegrasyon (ağ + veritabanı + arayüz) |
| Hazırlayan / Tarih | 22.10.2026 |
| Sürüm | BattleShip v1.1 |

## 1. Amaç

Bir oyuncunun sunucu, diğerinin istemci olarak bağlanabildiğini; gemi yerleşimi, sıra değişimi, atış sonuçları, sohbet ve oyun sonunun her iki tarafta da doğru göründüğünü ve veritabanına kaydedildiğini doğrulamak.

## 2. Ön koşullar

* JDK 17+ kurulu, `target/battleship.jar` derlenmiş (`mvnw.cmd -B package -DskipTests`).
* Aynı bilgisayarda iki pencere veya aynı ağda iki bilgisayar.
* 9090/TCP portu boş; iki bilgisayarlı testte güvenlik duvarı bu porta izin veriyor.

## 3. Test verisi

| Veri | Değer |
|---|---|
| Port | 9090 |
| Sunucu takma adı | `Kaptan` |
| İstemci takma adı | `Amiral` |
| Sunucu IP (aynı bilgisayar) | `127.0.0.1` |
| Sohbet metni | `Merhaba, iyi şanslar! ğüşıöç` |

## 4. Adımlar

| # | Eylem | Beklenen sonuç | Gerçek sonuç | Durum |
|---|---|---|---|---|
| 1 | Pencere A: `java -jar target\battleship.jar` | Pencere açılır, `data/amiral.db` oluşur, banner `READY TO PLAY` | Beklendiği gibi | PASS |
| 2 | A: `Play as server`, Nickname `Kaptan`, `Start server` | Output: `Server is opened! (port 9090)`, banner `WAITING FOR OPPONENT`, `My IP Address` dolu | Beklendiği gibi | PASS |
| 3 | Pencere B: `java -jar target\battleship.jar jdbc:sqlite:data/client.db` | İkinci pencere açılır | Beklendiği gibi | PASS |
| 4 | B: `Play as client`, Server IP `127.0.0.1`, Nickname `Amiral`, `Connect` | A: `Amiral wants to join`; B: `Connected to Kaptan`; iki pencerede banner `PLACE YOUR SHIPS`, başlıkta `vs …` | Beklendiği gibi | PASS |
| 5 | Üçüncü bir pencereden aynı sunucuya bağlanmayı dene | Hata: `Rejected by server: ALREADY_CONNECTED` | Otomatik testte doğrulandı (`GameLoopbackIT#secondClientIsRejected`) | PASS |
| 6 | İki pencerede `Ready` | A: `YOUR TURN` (yeşil, düşman tahtası parlar); B: `ENEMY'S TURN` (düşman tahtası soluk) | Beklendiği gibi | PASS |
| 7 | B sırası değilken düşman tahtasına tıklar | Hiçbir şey gönderilmez | Beklendiği gibi | PASS |
| 8 | A düşman tahtasında bir hücreye tıklar | Mermi animasyonu; ıska ise mavi halkalar, isabet ise ateş; sıra B'ye geçer | Beklendiği gibi | PASS |
| 9 | B aynı şekilde atış yapar | A'nın kendi tahtasında sonuç görünür, sıra A'ya geçer | Beklendiği gibi | PASS |
| 10 | B sohbete test metnini yazıp `Enter` | A'da `Amiral: Merhaba, iyi şanslar! ğüşıöç` kırmızı adla görünür | Otomatik testte doğrulandı (`GameControllerTest#chatNeedsConnectionAndValidText`) | PASS |
| 11 | Bir taraf rakibin 17 gemi hücresinin tamamını vurur | Kazanan: `VICTORY!` ve `Victory! You won!`; kaybeden: `DEFEAT` | Otomatik testte doğrulandı (`SocketGameIT#clientWinsOverTcp`, gerçek TCP) | PASS |
| 12 | `Game → History` (Ctrl+H) | Son oyun `SERVER` / `CLIENT` modunda, sonuç `WIN`/`LOSS`; çift tıkla hamleler listelenir | Geçmiş penceresi doğrulandı; hamle listesi otomatik testte | PASS |

## 5. Veritabanı kontrolü

```sql
SELECT id, mode, status, winner_role FROM game ORDER BY id DESC LIMIT 1;  -- FINISHED, LOCAL veya REMOTE
SELECT COUNT(*) FROM ship_placement WHERE game_id = <id>;                 -- 5
SELECT COUNT(*) FROM move WHERE game_id = <id>;                           -- >= 17
SELECT body FROM chat_message WHERE game_id = <id>;                       -- Türkçe karakterler bozulmadan
```

## 6. Olumsuz durumlar

| # | Eylem | Beklenen sonuç | Kanıt | Durum |
|---|---|---|---|---|
| N1 | Port alanına `80` yaz | Kırmızı çerçeve, `Port must be a number 1024-65535` | `UiComponentsTest#portValidation` | PASS |
| N2 | Oyun sürerken A `Stop server` | `A game is in progress. Abort it?` onayı; B'de `Opponent has left the game.`; iki DB'de `ABORTED` | Manuel (v1.0) + `GameControllerTest#stopServerDuringGame` | PASS |
| N3 | Aynı portla ikinci sunucu | `Port is in use: 9090` hata penceresi | `GameControllerTest#portInUseIsReported` | PASS |

## 7. Sonuç

**PASS.** Aynı bilgisayarda iki pencereyle manuel olarak ve gerçek TCP soketleri üzerinden otomatik testle doğrulandı. İki ayrı bilgisayarla (LAN) test henüz yapılmadı (TEST_PLAN TP-21).
