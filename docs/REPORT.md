# BattleShip v1.0 (Amiral Battı) — Proje Raporu

## 1. Giriş

Bu proje, iki oyuncunun ağ üzerinden (TCP) veya bir oyuncunun bilgisayara karşı oynadığı Amiral Battı oyunudur. Klasik kurallara ek olarak her oyuncunun 3 radar (4×4 tarama) ve 2 torpido (3×3 saldırı) hakkı vardır. Ödevin ana isteri, uygulamanın altyapısında **veritabanı yaklaşımı** bulunmasıdır: oyunlar, hamleler, gemi yerleşimleri, sohbet ve olay kayıtları SQLite veritabanında kalıcı olarak tutulur, geçmiş ve istatistikler arayüzde sorgulanarak gösterilir. Arayüz, referans ekran görüntüsündeki "BattleShip v1.0" penceresini temel alır.

## 2. Gereksinimler ve karşılanma durumu

### 2.1 Ödev isterleri (docs/odev-isterleri.png)

| İster | Puan | Karşılayan |
|---|---|---|
| UI kısmı | 1 | Koyu deniz temalı Swing arayüz (FlatLaf): çizilmiş gemiler, animasyonlu deniz, atış/torpido efektleri, Oracle Lens tarzı radar, sıra bandı, filo şeritleri, silah çubuğu (Bölüm 8) |
| Bilgisayara karşı oynama | 2 | `ComputerPlayer` (Easy / Normal / Hard) + `ComputerGateway` (Bölüm 7.1) |
| Network ile insana karşı oynama | 1 | `GameServer` / `GameClient`, TCP protokolü (Bölüm 6) |
| 3 radar (4×4), 2 torpido (3×3), kendi sırasında | – | `Weapon`, `GameEngine.use`, `RADAR` / `TORPEDO` mesajları, `ability_use` tablosu ve tetikleyicisi (Bölüm 7.2) |
| 3 test case dokümanı | – | `docs/test-cases/TC-01…TC-03` |

### 2.2 ROADMAP isterleri

| İster (ROADMAP 0.1) | Karşılayan |
|---|---|
| Veritabanı yaklaşımı | SQLite, `com.amiral.db` paketi |
| Oyun, hamle, yerleştirme, sohbet ve loglar kalıcı | `game`, `move`, `ship_placement`, `chat_message`, `app_log` tabloları |
| SQL ile erişim, Repository katmanı | 7 repository sınıfı, yalnızca `PreparedStatement` |
| Şemanın otomatik oluşturulması | `schema.sql`, açılışta `Database.initSchema()` |
| Geçmiş ve kazanma istatistikleri arayüzde | `StatsRepository` → `HistoryDialog` (Ctrl+H) |
| Yabancı anahtar, CHECK ve UNIQUE kısıtları | Tüm tablolarda; testleri `RepositoryTest` |
| Referans arayüz | `MainFrame` (iki tahta, Ships allocation, Connection Configuration, Chat History, Output) |

## 3. Teknoloji seçimi

| Bileşen | Seçim | Gerekçe |
|---|---|---|
| Dil | Java 17 | Referans arayüz Swing'e benziyor; LTS sürüm |
| Arayüz | Swing | Ek kurulum gerektirmez |
| Veritabanı | SQLite (`sqlite-jdbc`) | Sunucu gerektirmez, tek dosya; FK/CHECK/UNIQUE destekler |
| Ağ | TCP, satır tabanlı metin protokolü | Basit, hata ayıklaması kolay |
| Build / test | Maven (wrapper ile), JUnit 5, JaCoCo | Standart; Maven kurulumu gerekmez |
| CI | GitHub Actions (`mvn -B verify`) | Her push'ta derleme ve test |

## 4. Mimari

Beş paket, tek yönlü bağımlılıklarla (ROADMAP 2.1):

* `model` — saf Java oyun kuralları (`Coordinate`, `Ship`, `Board`, `GameEngine`, `RandomFleetGenerator`). Swing, JDBC veya soket bilmez.
* `net` — protokol ve soketler (`ProtocolCodec`, `Connection`, `GameServer`, `GameClient`). Oyun kuralı içermez.
* `db` — JDBC ve SQL (`Database`, repository'ler). Oyun kuralı içermez.
* `controller` — `GameController` bu üçünü birleştirir; UI'ye `AppEvent` olayları gönderir.
* `ui` — Swing bileşenleri; yalnızca `controller` ve `model` tiplerini kullanır.

İş parçacıkları: her bağlantıda bir okuma thread'i ve bir kalp atışı zamanlayıcısı; istemci bağlantısı ayrı bir I/O thread'inde kurulur. Controller tüm olayları tek bir kilitle sıralar ve UI'ye `SwingUtilities.invokeLater` ile iletir, böylece arayüz donmaz.

Sınıf diyagramı: `docs/diagrams/class.png`, use case: `docs/diagrams/usecase.png`.

## 5. Veritabanı tasarımı

Yedi tablo: `player`, `game`, `game_player`, `ship_placement`, `move`, `chat_message`, `app_log`. ER diyagramı `docs/diagrams/er.png`, DDL `src/main/resources/schema.sql`, açıklama ve örnek sorgular `docs/DATABASE.md`.

Öne çıkan kısıtlar:

* `move` üzerinde `UNIQUE (game_id, shooter, coord)` aynı hücreye ikinci atışı veritabanı düzeyinde de engeller.
* `ship_placement` üzerinde `UNIQUE (game_id, ship_type)` her gemi tipinin bir kez kaydedilmesini sağlar; filo tek transaction ile yazılır.
* `game` silindiğinde yerleşim, hamle ve sohbet `ON DELETE CASCADE` ile silinir, loglar `ON DELETE SET NULL` ile korunur.

İstatistik sorgusu (geçmiş penceresinin üst satırı):

```sql
SELECT COUNT(*),
       SUM(CASE WHEN status='FINISHED' AND winner_role='LOCAL'  THEN 1 ELSE 0 END),
       SUM(CASE WHEN status='FINISHED' AND winner_role='REMOTE' THEN 1 ELSE 0 END)
FROM game WHERE status <> 'ACTIVE';
```

v1.1 ile eklenenler: `game.mode` için `'AI'` değeri, `move.weapon` sütunu (`SHOT` / `TORPEDO`) ve `ability_use` tablosu. Radar/torpido hakkı (3 / 2) bir `BEFORE INSERT` tetikleyicisiyle veritabanı düzeyinde de korunur. v1.0 ile oluşturulmuş veritabanları açılışta kendiliğinden yükseltilir (`Database.migrate`; `game` tablosu yeniden kurulur, eski kayıtlar korunur).

Tasarım kararı: oyun kaydı (`game`) sunucu açıldığında değil, rakip bağlandığında oluşturulur. Böylece rakip gelmeden kapatılan sunucular geçmişte "ABORTED" oyun olarak görünmez.

## 6. Ağ protokolü

UTF-8, satır başına bir mesaj, alanlar `|` ile ayrılır, serbest metin Base64 ile kodlanır. Mesajlar: `HELLO`, `WELCOME`, `REJECT`, `READY`, `START`, `SHOOT`, `RESULT`, `RADAR`, `RADAR_RESULT`, `TORPEDO`, `TORPEDO_RESULT`, `SHOT` (ayrılmış), `CHAT`, `GAME_OVER`, `QUIT`, `PING`/`PONG`, `ERROR`. Her 5 saniyede `PING`; 15 saniye trafik yoksa bağlantı kopmuş sayılır. Ayrıntı ve örnek oturum: `docs/PROTOCOL.md`; sekans diyagramı: `docs/diagrams/sequence.png`.

Her taraf kendi tahtasının sahibidir: `SHOOT` alan taraf sırayı, koordinatı ve tekrar atışı kontrol eder, `RESULT` veya `ERROR` döner. İkinci bir istemci `REJECT|ALREADY_CONNECTED` alır.

## 7. Oyun motoru

`GameEngine` tek bir oyuncunun bakış açısını tutar: kendi tahtası, rakibe yapılan atışlar ve sonuçları, sıra ve durum (`SETUP → WAITING → PLAYING → FINISHED`, herhangi bir durumdan `ABORTED`). Rakip tahtası bilinmediği için kendi atışımız önce "bekleyen atış" olarak kaydedilir; sonuç `RESULT` ile gelince uygulanır. Rakip atışı reddederse (`ERROR`) bekleyen atış geri alınır ve sıra bizde kalır. İsabet eden oyuncu (atış veya en az bir isabetli torpido) ıskalayana kadar oynamaya devam eder; ıska veya radar sırayı karşı tarafa geçirir. 17 isabet oyunu bitirir. Sunucu ilk hamleyi yapar.

### 7.1 Bilgisayar rakip

`ComputerGateway`, ağ katmanının yerine geçen bir `NetworkGateway` uygulamasıdır: kendi `GameEngine`'i ve rastgele filosu vardır, aynı protokol mesajlarını üretir ve yanıtlar. Böylece controller ağ oyunu ile bilgisayar oyununu aynı kodla yönetir; bilgisayar oyuncunun gerçek filosuna hiç bakmaz (yalnızca kendi atış sonuçlarını bilir). Her hamleden önce yaklaşık 1 saniye bekler.

`ComputerPlayer` üç seviye sunar:

| Seviye | Strateji | Ortalama hamle (200 simülasyon) |
|---|---|---|
| Easy | Rastgele atış; ara sıra rastgele radar / torpido | 62,1 |
| Normal | Önce radar bulgularına ateş; isabeti takip (aynı doğrultudaki hücreler önce); dama tahtası deseniyle arama; radar ve torpidoyu ara sıra | 27,5 |
| Hard | Kalan gemilerin tüm olası yerleşimlerinden olasılık haritası (ıska, radarla görülen su ve batmış gemi hücreleri dışlanır, isabetler ağırlıklandırılır); radarı en olası 4×4 alana, torpidoyu kazancı tek atıştan belirgin yüksek olduğunda kullanır | 20,9 |

Batan bir geminin hangi isabet hücrelerine ait olduğunu bilgisayar bilmez; `SUNK` gelen hücreden geçen, gemi boyunda ardışık isabet dizisini bularak çıkarır.

### 7.2 Radar ve torpido

`Weapon` enum'u alanı hesaplar (radar 4×4, torpido 3×3; kenarda alan tahtanın içine kaydırılır), iki taraf da aynı fonksiyonu kullandığı için ağdan yalnızca hedef hücre gider. `GameEngine.use` sırayı, hakkı ve (torpido için) en az bir yeni hücre olmasını kontrol eder; her kullanım sırayı rakibe geçirir. Reddedilen kullanımda hak geri verilir. Radar gemi hücrelerini gösterir ama vurmaz; torpido alandaki her yeni hücreye atış yapar. Hile yapan bir rakibin fazladan kullanımı `ERROR NO_ABILITY_LEFT` ile reddedilir.

## 8. Arayüz

Referans ekran düzeni korunmuştur: solda "Your ships", ortada "Your enemy", sağda yerleşim, bağlantı ve sohbet kutuları, altta Output ve durum çubuğu. v1.1'de arayüz koyu deniz temasıyla yeniden tasarlandı (FlatLaf). Tahtalar `paintComponent` ile çizilir, kare kalır ve ~30 fps animasyonludur:

* **Gemiler:** `ShipPainter` her gemiyi yukarıdan görünen bir gövde olarak çizer (yuvarlak kıç, sivri pruva, gölge); uçak gemisinde uçuş güvertesi, savaş gemisinde 3, kruvazörde 2, muhripte 1 top tareti, denizaltıda kule. Batan gemiler çatlaklı kırmızı enkaz ve duman olarak görünür. Manuel yerleşimde yarı saydam yeşil/kırmızı "hayalet" gemi.
* **Deniz:** degrade ve hareket eden dalga çizgileri.
* **Atış:** hedefe daralan nişangâh, ardından patlama (isabet) veya su halkaları (ıska); isabetler titreşen ateş olarak kalır.
* **Radar:** League of Legends'taki Oracle Lens'ten esinlenen animasyon: alanın merkezinde parlama, dışa doğru yayılan parlak kenarlı kırmızı halka ve yankı halkası, dönen tarama kolu ve izi, kesikli alan çerçevesi. Bulunan her gemi hücresi, dalga ona ulaştığı anda büyüyerek belirir ve nabız gibi atan kırmızı elmas olarak kalır (gecikme hücrenin merkeze uzaklığından hesaplanır).
* **Torpido:** torpido köpük iziyle tahtanın kenarından hedef satıra gelir, 3×3 alan merkezden dışa doğru sırayla patlar, şok dalgası ve kısa ekran sarsıntısı.
* **Sıra bandı** (YOUR TURN / ENEMY'S TURN / VICTORY! / DEFEAT), sıra bizdeyken düşman tahtasının yeşil parlayan çerçevesi, sıra rakipteyken kararan tahta.
* **Filo şeritleri** (kalan / batan gemiler) ve **silah çubuğu** (Shot / Radar / Torpedo, kalan haklar, rakibin hakları; `1` `2` `3` tuşları; seçili silahın alanı fareyle önizlenir).

Ekran görüntüleri: `docs/screenshot.png`, `docs/radar-animation.png`, `docs/torpedo-animation.png`. Eklenenler: Game/Help menüsü ve kısayollar, manuel yerleşimde gemi listesi, `R` ile döndürme, yeşil/kırmızı önizleme, sağ tıkla kaldırma, `Ready` düğmesi, port doğrulaması, takma ad alanı, sıra rakipteyken soluk düşman tahtası, geçmiş penceresi, onay ve hata diyalogları. Ekran görüntüsü: `docs/screenshot.png`.

## 9. Test sonuçları

`./mvnw -B verify`: 195 birim testi ve 8 entegrasyon testi, hepsi yeşil. Satır kapsamı: model %99, db %95, net %89, controller %88, ui %39 (çizim, animasyon ve pencere kodu ekran gerektirdiği için headless ortamda test edilemez; ekran görüntüleriyle doğrulandı).

Öne çıkan testler:

* `GameControllerTest` — iki controller'ı sahte ağla bağlayıp tam bir oyunu, hata kodlarını, kopmaları ve DB kayıtlarını doğrular.
* `SocketGameIT` — aynı tam oyunu gerçek TCP soketleri üzerinden oynar.
* `GameLoopbackIT` — el sıkışma, `REJECT`, kullanımda port, kopma.
* `RepositoryTest` — CHECK, UNIQUE, FK ve CASCADE kısıtlarının gerçekten çalıştığı.
* `GameEngineWeaponsTest`, `WeaponFlowTest` — radar ve torpido kuralları, haklar, iki tarafta sonuçlar ve DB kayıtları.
* `ComputerPlayerTest` — her seviyenin yalnızca kurala uygun hamle yaptığı ve Hard < Normal < Easy sıralaması.
* `ComputerGameTest` — controller üzerinden bilgisayara karşı tam oyunlar.
* `SchemaUpgradeTest` — tetikleyici ve v1.0 veritabanının yükseltilmesi.

Ödevin istediği 3 test case dokümanı: `docs/test-cases/TC-01-ag-uzerinden-oyun.md`, `TC-02-bilgisayara-karsi-oyun.md`, `TC-03-radar-ve-torpido.md`.

Manuel test planı (TP-01…TP-31) `docs/TEST_PLAN.md`'dedir: 28 senaryo PASS; TP-20 (yeniden açılışta geçmiş), TP-21 (iki bilgisayar arası LAN) ve TP-31 (radar/torpido gerçek iki pencereyle ağ üzerinden) henüz manuel çalıştırılmadı.

## 10. Sonuç ve geliştirme önerileri

Oyun, ROADMAP'teki tüm fazlarıyla (model, veritabanı, ağ, arayüz, controller, test, doküman) ve ödev isterlerindeki ek özelliklerle (bilgisayar rakip, radar, torpido, test case dokümanları) tamamlandı. Geliştirme önerileri:

* `READY` mesajı filoyu rakibe gönderir (protokol gereği); hile önlemek için filo yerine yalnızca bir özet (hash) gönderilip oyun sonunda doğrulanabilir.
* Gemilerin bitişik olmasını yasaklayan isteğe bağlı kural.
* Tekrar bağlanınca yarıda kalan oyuna devam edebilme.
* Arayüz için Türkçe dil seçeneği.
