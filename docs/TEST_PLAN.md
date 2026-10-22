# Test Planı

## 1. Otomatik testler

`./mvnw -B verify` (Windows: `mvnw.cmd -B verify`) birim testlerini (Surefire) ve entegrasyon testlerini (Failsafe, `*IT`) çalıştırır, ardından JaCoCo kapsam raporunu `target/site/jacoco/index.html` altına üretir.

| Paket | Test sınıfları | Satır kapsamı | Hedef (T-24) |
|---|---|---|---|
| `model` | `CoordinateTest`, `ShipTest`, `BoardTest`, `RandomFleetGeneratorTest`, `GameEngineTest`, `GameEngineWeaponsTest`, `WeaponTest`, `ComputerPlayerTest` | %99 | %85+ |
| `net` | `ProtocolCodecTest`, `MessageTest`, `ConnectionTest`, `GameLoopbackIT` | %89 | %70+ |
| `db` | `DatabaseTest`, `RepositoryTest`, `StatsRepositoryTest`, `SchemaUpgradeTest` | %95 | %80+ |
| `controller` | `GameControllerTest`, `WeaponFlowTest` (sahte ağ), `ComputerGameTest` (bilgisayar rakip), `SocketGameIT` (gerçek TCP) | %88 | %70+ |
| `ui` | `UiComponentsTest` (headless çalışabilen bileşenler) | %39 | – |

Toplam 203 test (195 birim + 8 entegrasyon). `ui` kapsamı düştü çünkü çizim ve animasyon kodu (`BoardPanel.paint*`, `ShipPainter`) ekran gerektirir; bunlar ekran görüntüleriyle manuel doğrulandı.

`MainFrame` ve `HistoryDialog` pencere açtığı için headless CI ortamında test edilemez; bu kısımlar aşağıdaki manuel senaryolarla doğrulanır.

## 2. Manuel senaryolar

Durum sütunu: **PASS** = doğrulandı, **BEKLİYOR** = henüz çalıştırılmadı.
"Kanıt" sütunu senaryoyu kapsayan otomatik testi ya da manuel doğrulamayı gösterir.
Manuel GUI doğrulamaları 22.10.2026 tarihinde aynı makinede iki pencereyle (sunucu + istemci) yapıldı.

| ID | Senaryo | Beklenen | Kanıt | Durum |
|---|---|---|---|---|
| TP-01 | Uygulama ilk açılış | `data/amiral.db` oluşur, pencere açılır | `DatabaseTest#createsFileAndParentDirectory`, manuel | PASS |
| TP-02 | Otomatik yerleşim | 5 gemi, çakışma yok, `Ready` aktif | `UiComponentsTest#automaticAllocationAtStart`, `RandomFleetGeneratorTest` | PASS |
| TP-03 | Manuel yerleşim, geçersiz konum | Kırmızı önizleme, yerleşmez | `UiComponentsTest#invalidManualPlacementIsIgnored` (yerleşmeme). Kırmızı önizleme rengi gözle kontrol edilmeli | PASS (mantık) |
| TP-04 | Manuel yerleşim, döndürme (R) | Yön değişir | `UiComponentsTest#manualPlacementRotateAndRemove` | PASS |
| TP-05 | Sağ tık ile gemi kaldırma | Gemi listeye geri döner | `UiComponentsTest#manualPlacementRotateAndRemove` | PASS |
| TP-06 | Port 9090 ile sunucu başlatma | Log `Server is opened!`, IP görünür | `GameControllerTest#serverStartsAndClientJoins`, manuel | PASS |
| TP-07 | Port 80 girişi | Doğrulama hatası | `UiComponentsTest#portValidation` | PASS |
| TP-08 | Aynı makinede istemci bağlantısı (ikinci instance) | `WELCOME`, log güncellenir | `GameLoopbackIT#helloWelcomeHandshake`, manuel | PASS |
| TP-09 | Üçüncü bağlantı denemesi | `REJECT` | `GameLoopbackIT#secondClientIsRejected` | PASS |
| TP-10 | İki taraf da Ready | Oyun başlar, sıra sunucuda | `GameControllerTest#bothReadyStartsGameWithServerFirst`, manuel | PASS |
| TP-11 | Sıra dışı tıklama | Hamle yapılmaz | `GameControllerTest#outOfTurnClickIsIgnored` | PASS |
| TP-12 | Atış: MISS, HIT, SUNK | Renkler doğru, log doğru | `GameControllerTest#fullGameServerWins`, manuel (MISS). HIT/SUNK renkleri gözle kontrol edilmeli | PASS (mantık) |
| TP-13 | Aynı hücreye ikinci atış | `ALREADY_SHOT`, sıra değişmez | `GameControllerTest#repeatedShotIsRefusedAndTurnStays`, `GameEngineTest` | PASS |
| TP-14 | Sohbet gönderme (Enter ile) | Karşı tarafta görünür | `UiComponentsTest#chatSendsTrimmedTextAndLimitsLength`, `SocketGameIT` | PASS |
| TP-15 | Türkçe karakterli sohbet (ş, ğ, ı, ö, ü, ç) | Doğru görünür | `ProtocolCodecTest#turkishChatSurvivesBase64`, `GameControllerTest#chatNeedsConnectionAndValidText` | PASS |
| TP-16 | Tüm filoyu batırma | `GAME_OVER`, durum çubuğu, DB `FINISHED` | `GameControllerTest#fullGameServerWins`, `SocketGameIT#clientWinsOverTcp` | PASS |
| TP-17 | Oyun sırasında istemci kapatma | Sunucu `ABORTED`, log, UI pasif | `GameControllerTest#opponentLeavingAbortsTheGame`, `#lostConnectionAbortsTheGame` | PASS |
| TP-18 | Oyun sırasında Stop Server | Onay sorusu, sonra kapanır | `GameControllerTest#stopServerDuringGame`, manuel (onay diyaloğu ve istemcide "Opponent has left the game.") | PASS |
| TP-19 | Geçmiş penceresi | Oyunlar ve istatistik doğru | `StatsRepositoryTest`, manuel (Ctrl+H) | PASS |
| TP-20 | Uygulamayı yeniden açma | Önceki kayıtlar geçmişte | Dosya tabanlı DB; manuel adım çalıştırılmadı | BEKLİYOR |
| TP-21 | İki bilgisayar arası ağ (LAN) | Aynı oyun akışı | İki makine gerekir | BEKLİYOR |
| TP-22 | Bilgisayara karşı oyun başlatma (Normal) | `vs Computer (Normal)`, ilk hamle oyuncuda | TC-02, `ComputerGameTest#computerJoinsAndIsReady`, manuel | PASS |
| TP-23 | Bilgisayarı yenme | `VICTORY!`, DB `mode = AI`, `FINISHED` | `ComputerGameTest#playerCanBeatTheComputer` | PASS |
| TP-24 | Bilgisayarın radar / torpido kullanması | Kendi tahtamızda animasyon ve sonuç | Manuel (bilgisayar torpidoyla Destroyer'ı batırdı), `ComputerGameTest#hardComputerUsesRadarAndTorpedoAndCanWin` | PASS |
| TP-25 | Zorluk seviyeleri farklı oynar | Hard < Normal < Easy ortalama hamle | `ComputerPlayerTest#harderLevelsNeedFewerTurns` (62,1 / 27,5 / 20,9) | PASS |
| TP-26 | Radar kullanma (4×4) | Oracle Lens animasyonu, bulunan hücrelerde elmas, sıra geçer | TC-03, manuel (E5 → 3 hücre), `WeaponFlowTest#radarRevealsShipsOnBothSides` | PASS |
| TP-27 | Torpido kullanma (3×3) | Torpido animasyonu, 9 hücrede sonuç, batan gemi enkaz | TC-03, manuel (G5 → 3 isabet), `WeaponFlowTest#torpedoHitsAreaAndSinks` | PASS |
| TP-28 | 4. radar / 3. torpido | Düğme pasif; rakipten gelirse `ERROR NO_ABILITY_LEFT` | `WeaponFlowTest#limitsAreEnforcedLocally`, `#opponentOverLimitIsRefused` | PASS |
| TP-29 | Sıra dışında radar / torpido | Düğmeler pasif; rakipten gelirse `ERROR NOT_YOUR_TURN` | `WeaponFlowTest#actionOutOfTurnIsRefused`, manuel | PASS |
| TP-30 | v1.0 veritabanıyla açılış | Eski kayıtlar korunur, yeni tablolar eklenir | `SchemaUpgradeTest#upgradesVersionOneDatabase` | PASS |
| TP-31 | Radar / torpido ağ üzerinden (iki pencere) | TP-26 / TP-27 ile aynı, iki tarafta | Sahte ağla otomatik test edildi; gerçek iki pencereyle manuel çalıştırılmadı | BEKLİYOR |

Ayrıntılı test case dokümanları: [TC-01](test-cases/TC-01-ag-uzerinden-oyun.md), [TC-02](test-cases/TC-02-bilgisayara-karsi-oyun.md), [TC-03](test-cases/TC-03-radar-ve-torpido.md).

### Manuel adımlar (BEKLİYOR olanlar)

**TP-20:** Bir oyun oynayın, uygulamayı kapatın, yeniden açın, `Game → History` (Ctrl+H). Önceki oyun listede görünmelidir.

**TP-21:** Bilgisayar A'da `Play as server` → `Start server`; görünen `My IP Address` değerini not edin. Bilgisayar B'de `Play as client`, `Server IP` alanına bu adresi yazın → `Connect`. Güvenlik duvarının 9090/TCP portuna izin verdiğinden emin olun. Sonra TP-10…TP-16 adımlarını tekrarlayın.

## 3. Hata senaryoları (T-23)

| Senaryo | Davranış | Test |
|---|---|---|
| Karşı taraf bağlantıyı keser | Oyun `ABORTED`, log, durum çubuğu `Disconnected`, bilgi mesajı | `GameControllerTest#lostConnectionAbortsTheGame` |
| Geçersiz koordinat | `ERROR INVALID_COORD`, sıra değişmez | `GameControllerTest#invalidCoordinateIsRefused` |
| Sırası olmayan atış | `ERROR NOT_YOUR_TURN` | `GameControllerTest#shootOutOfTurnIsRefused` |
| Aynı hücreye ikinci atış | `ERROR ALREADY_SHOT` | `GameControllerTest#repeatedShotIsRefusedAndTurnStays` |
| Oyun başlamadan atış | `ERROR GAME_NOT_STARTED` | `GameControllerTest#shotBeforeStartIsRefused` |
| Geçersiz filo | `ERROR INVALID_FLEET` | `GameControllerTest#invalidFleetIsRefused` |
| Port kullanımda | `Port is in use` hata diyaloğu | `GameControllerTest#portInUseIsReported`, `GameLoopbackIT#portInUseFails` |
| Sunucuya bağlanılamıyor | Hata diyaloğu, durum `Idle` | `GameControllerTest#connectFailureIsReported` |
| Kalp atışı zaman aşımı (15 sn) | Bağlantı kopmuş sayılır | `ConnectionTest#answersPingAndTimesOutWithoutTraffic` |
