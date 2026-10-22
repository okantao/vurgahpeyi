# BattleShip v1.0 (Amiral Battı)

![CI](../../actions/workflows/ci.yml/badge.svg)

İki kişilik, ağ üzerinden (TCP) oynanan Amiral Battı oyunu. Java 17 + Swing arayüz, SQLite veritabanı.

![Ekran görüntüsü](docs/screenshot.png)

* Sunucu / istemci modu, satır tabanlı metin protokolü ([docs/PROTOCOL.md](docs/PROTOCOL.md))
* Manuel veya otomatik gemi yerleşimi, sohbet, olay günlüğü
* Oyunlar, hamleler, yerleşimler, sohbet ve loglar SQLite'ta saklanır; geçmiş ve kazanma istatistikleri arayüzde gösterilir ([docs/DATABASE.md](docs/DATABASE.md))

## Gereksinimler

* JDK 17 veya üzeri
* Maven gerekmez: repodaki Maven Wrapper (`mvnw` / `mvnw.cmd`) ilk çalıştırmada Maven 3.9.9'u indirir.

## Derleme ve test

```
./mvnw -B verify          # Linux / macOS / Git Bash
mvnw.cmd -B verify        # Windows cmd / PowerShell
```

Birim + entegrasyon testlerini çalıştırır; kapsam raporu: `target/site/jacoco/index.html`.

## Çalıştırma

```
./mvnw -B package -DskipTests
java -jar target/battleship.jar
```

veya jar üretmeden: `./mvnw -q compile exec:java`.

İsteğe bağlı argüman: veritabanı adresi (varsayılan `jdbc:sqlite:data/amiral.db`). Aynı bilgisayarda ikinci pencere için:

```
java -jar target/battleship.jar jdbc:sqlite:data/client.db
```

Hızlı deneme: birinci pencerede `Start server`, ikincide `Play as client` → `Connect` (127.0.0.1:9090), iki tarafta `Ready`.

## Proje yapısı

| Paket | Sorumluluk |
|---|---|
| `com.amiral.model` | Saf Java oyun kuralları: `Coordinate`, `Ship`, `Board`, `GameEngine`, `RandomFleetGenerator` |
| `com.amiral.net` | Protokol ve soketler: `ProtocolCodec`, `Connection` (kalp atışı), `GameServer`, `GameClient` |
| `com.amiral.db` | JDBC + SQL: `Database`, repository sınıfları, `StatsRepository` |
| `com.amiral.controller` | Model + ağ + DB + UI köprüsü: `GameController`, `AppEvent` |
| `com.amiral.ui` | Swing arayüz: `MainFrame`, `BoardPanel`, paneller, `HistoryDialog` |

## Dokümanlar

* [Kullanım kılavuzu](docs/USER_MANUAL.md)
* [Ağ protokolü](docs/PROTOCOL.md)
* [Veritabanı](docs/DATABASE.md)
* [Test planı](docs/TEST_PLAN.md)
* [Rapor](docs/REPORT.md)
* [UML diyagramları](docs/diagrams/) (PlantUML)
* [Yol haritası](ROADMAP.md)
