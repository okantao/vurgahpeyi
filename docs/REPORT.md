# BattleShip v1.0 (Amiral Battı) — Proje Raporu

## 1. Giriş

Bu proje, iki oyuncunun ağ üzerinden (TCP) oynadığı klasik Amiral Battı oyunudur. Ödevin ana isteri, uygulamanın altyapısında **veritabanı yaklaşımı** bulunmasıdır: oyunlar, hamleler, gemi yerleşimleri, sohbet ve olay kayıtları SQLite veritabanında kalıcı olarak tutulur, geçmiş ve istatistikler arayüzde sorgulanarak gösterilir. Arayüz, referans ekran görüntüsündeki "BattleShip v1.0" penceresini temel alır.

## 2. Gereksinimler ve karşılanma durumu

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

Tasarım kararı: oyun kaydı (`game`) sunucu açıldığında değil, rakip bağlandığında oluşturulur. Böylece rakip gelmeden kapatılan sunucular geçmişte "ABORTED" oyun olarak görünmez.

## 6. Ağ protokolü

UTF-8, satır başına bir mesaj, alanlar `|` ile ayrılır, serbest metin Base64 ile kodlanır. Mesajlar: `HELLO`, `WELCOME`, `REJECT`, `READY`, `START`, `SHOOT`, `RESULT`, `SHOT` (ayrılmış), `CHAT`, `GAME_OVER`, `QUIT`, `PING`/`PONG`, `ERROR`. Her 5 saniyede `PING`; 15 saniye trafik yoksa bağlantı kopmuş sayılır. Ayrıntı ve örnek oturum: `docs/PROTOCOL.md`; sekans diyagramı: `docs/diagrams/sequence.png`.

Her taraf kendi tahtasının sahibidir: `SHOOT` alan taraf sırayı, koordinatı ve tekrar atışı kontrol eder, `RESULT` veya `ERROR` döner. İkinci bir istemci `REJECT|ALREADY_CONNECTED` alır.

## 7. Oyun motoru

`GameEngine` tek bir oyuncunun bakış açısını tutar: kendi tahtası, rakibe yapılan atışlar ve sonuçları, sıra ve durum (`SETUP → WAITING → PLAYING → FINISHED`, herhangi bir durumdan `ABORTED`). Rakip tahtası bilinmediği için kendi atışımız önce "bekleyen atış" olarak kaydedilir; sonuç `RESULT` ile gelince uygulanır. Rakip atışı reddederse (`ERROR`) bekleyen atış geri alınır ve sıra bizde kalır. Sıra her atışta karşı tarafa geçer; 17 isabet oyunu bitirir. Sunucu ilk hamleyi yapar.

## 8. Arayüz

Referans ekran düzeni: solda "Your ships", ortada "Your enemy", sağda yerleşim, bağlantı ve sohbet kutuları, altta Output ve durum çubuğu. Tahtalar `paintComponent` ile çizilir ve kare kalır. Eklenenler: Game/Help menüsü ve kısayollar, manuel yerleşimde gemi listesi, `R` ile döndürme, yeşil/kırmızı önizleme, sağ tıkla kaldırma, `Ready` düğmesi, port doğrulaması, takma ad alanı, sıra rakipteyken soluk düşman tahtası, geçmiş penceresi, onay ve hata diyalogları. Ekran görüntüsü: `docs/screenshot.png`.

## 9. Test sonuçları

`./mvnw -B verify`: 137 birim testi ve 8 entegrasyon testi, hepsi yeşil. Satır kapsamı: model %100, db %95, controller %89, net %87, ui %54 (pencere açan sınıflar headless ortamda test edilemez).

Öne çıkan testler:

* `GameControllerTest` — iki controller'ı sahte ağla bağlayıp tam bir oyunu, hata kodlarını, kopmaları ve DB kayıtlarını doğrular.
* `SocketGameIT` — aynı tam oyunu gerçek TCP soketleri üzerinden oynar.
* `GameLoopbackIT` — el sıkışma, `REJECT`, kullanımda port, kopma.
* `RepositoryTest` — CHECK, UNIQUE, FK ve CASCADE kısıtlarının gerçekten çalıştığı.

Manuel test planı (TP-01…TP-21) `docs/TEST_PLAN.md`'dedir: 19 senaryo PASS; TP-20 (yeniden açılışta geçmiş) ve TP-21 (iki bilgisayar arası LAN) henüz çalıştırılmadı.

## 10. Sonuç ve geliştirme önerileri

Oyun, ROADMAP'teki tüm fazlarıyla (model, veritabanı, ağ, arayüz, controller, test, doküman) tamamlandı. Geliştirme önerileri:

* `READY` mesajı filoyu rakibe gönderir (protokol gereği); hile önlemek için filo yerine yalnızca bir özet (hash) gönderilip oyun sonunda doğrulanabilir.
* Gemilerin bitişik olmasını yasaklayan isteğe bağlı kural.
* Bilgisayara karşı tek kişilik mod.
* Tekrar bağlanınca yarıda kalan oyuna devam edebilme.
* Arayüz için Türkçe dil seçeneği.
