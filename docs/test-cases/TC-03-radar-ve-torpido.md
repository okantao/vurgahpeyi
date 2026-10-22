# Test Case TC-03: Radar ve Torpido Hakları

| Alan | Değer |
|---|---|
| Test Case ID | TC-03 |
| Başlık | 3 radar (4×4 tarama) ve 2 torpido (3×3 saldırı) hakkının sırası gelen oyuncu tarafından kullanılması |
| İlgili ister | "Oyunda kullanıcıların; 3 Radar (4 * 4 bir alanı tarasın), 2 torpido (3x3 alan) hakkı hamle sırası kendilerinde kullanacakları şekilde olsun." |
| Öncelik | Yüksek |
| Tür | Fonksiyonel, kural, sınır değer |
| Hazırlayan / Tarih | 22.10.2026 |
| Sürüm | BattleShip v1.1 |

## 1. Amaç

Radar ve torpidonun yalnızca oyuncunun kendi sırasında kullanılabildiğini, alanların doğru boyutta olduğunu, kullanım haklarının (3 / 2) aşılamadığını, radarın sırayı rakibe geçirdiğini, isabetli torpidonun ise sırayı oyuncuda bıraktığını, sonuçların iki tarafta da doğru gösterildiğini ve veritabanına yazıldığını doğrulamak.

## 2. Kurallar (tasarım kararları)

| Kural | Değer |
|---|---|
| Radar alanı | 4×4 = 16 hücre. Tıklanan hücre alanın merkezindeki 4 hücrenin sol üstüdür (E5 → D4…G7). Kenarda alan tahtanın içine kaydırılır (J10 → G7…J10) |
| Radar sonucu | Alandaki gemi hücreleri gösterilir; **vurulmaz** |
| Torpido alanı | 3×3 = 9 hücre, tıklanan hücre merkez (E5 → D4…F6); kenarda içeri kaydırılır |
| Torpido sonucu | Alandaki daha önce vurulmamış her hücreye atış; birden fazla gemi batabilir |
| Hak | Oyuncu başına oyun başına 3 radar, 2 torpido |
| Sıra | Radar hamle sayılır ve sıra rakibe geçer; torpido en az bir hücreyi vurursa sıra oyuncuda kalır, hiçbirini vuramazsa rakibe geçer |
| Sınır | Haklar bitince ilgili düğme pasifleşir; rakipten gelen fazla kullanım `ERROR NO_ABILITY_LEFT` ile reddedilir |

## 3. Ön koşullar

* Bir oyun `PLAYING` durumunda (bilgisayara karşı veya ağ üzerinden). Örnek: TC-02 adım 1–4.

## 4. Adımlar

| # | Eylem | Beklenen sonuç | Gerçek sonuç | Durum |
|---|---|---|---|---|
| 1 | Oyun başında silah çubuğuna bak | `Shot ∞`, `Radar 3`, `Torpedo 2`; `Enemy: radar 3 · torpedo 2` | Beklendiği gibi | PASS |
| 2 | Sıra rakipteyken `Radar`'a tıkla | Düğmeler pasif, seçilemez | Beklendiği gibi | PASS |
| 3 | Sıra sendeyken `Radar`'ı seç (veya `2` tuşu) ve fareyi düşman tahtasında gezdir | Kırmızı, kesikli çerçeveli 4×4 önizleme | Beklendiği gibi | PASS |
| 4 | E5'e tıkla | Oracle Lens tarzı animasyon: merkezden genişleyen kırmızı halka, dönen tarama kolu; dalga ulaştıkça bulunan gemi hücrelerinde nabız gibi atan kırmızı elmas; alan hafif kırmızı kalır. Output: `Radar around E5: 3 ship cells found!` | Manuel çalıştırmada G4, G5, G6 bulundu, animasyon görüldü | PASS |
| 5 | Radar sonrası | `Radar 2`; banner `ENEMY'S TURN`; seçim `Shot`a döner | Beklendiği gibi | PASS |
| 6 | Rakip tarafında | Kendi tahtasında aynı radar animasyonu ve taranan alan; `Enemy: radar 2 · torpedo 2` | `WeaponFlowTest#radarRevealsShipsOnBothSides` | PASS |
| 7 | Sıra gelince `Torpedo` (veya `3`), G5'e tıkla | 3×3 turuncu önizleme; torpido soldan gelip patlar, sarsıntı, 9 hücrede sonuç. Output: `Torpedo at G5: 3 hits` | Manuel çalıştırmada 3 isabet, 6 ıska | PASS |
| 8 | Torpido bir gemiyi tamamen kapsıyorsa | Gemi batar, enkaz çizilir, alttaki filo şeridinde üstü çizilir | Bilgisayarın torpidosu bizim Destroyer'ı batırdı: enkaz + `4/5 afloat` | PASS |
| 9 | Tüm hücreleri daha önce vurulmuş bir alana torpido | `Already shot at …` uyarısı, hak gitmez, sıra sende kalır | `GameEngineWeaponsTest#torpedoNeedsAnUnshotCell` | PASS |
| 10 | 3 radar kullandıktan sonra 4. radar | `Radar 0`, düğme pasif; zorlanırsa `No Radar left`, mesaj gönderilmez | `WeaponFlowTest#limitsAreEnforcedLocally` | PASS |
| 11 | Hile yapan rakip 4. radar mesajı gönderir | `ERROR NO_ABILITY_LEFT` | `WeaponFlowTest#opponentOverLimitIsRefused` | PASS |
| 12 | Rakip sırası değilken `TORPEDO` gönderir | `ERROR NOT_YOUR_TURN` | `WeaponFlowTest#actionOutOfTurnIsRefused` | PASS |
| 13 | Kenar hücrelerde alan | A1'e radar → A1…D4; J10'a torpido → H8…J10 | `WeaponTest#areasShiftInsideTheBoard` | PASS |
| 14 | Son gemi hücreleri torpidoyla vurulur | Oyun biter, torpidoyu atan kazanır | `GameEngineWeaponsTest#enemyTorpedoCanSinkTheLastShip` | PASS |

## 5. Veritabanı kontrolü

```sql
SELECT turn_no, user_role, ability, coord, found FROM ability_use WHERE game_id = <id> ORDER BY turn_no;
SELECT coord, result FROM move WHERE game_id = <id> AND weapon = 'TORPEDO';
-- Tetikleyici: 4. radar kaydı reddedilir
INSERT INTO ability_use(game_id, turn_no, user_role, ability, coord) VALUES (<id>, 99, 'LOCAL', 'RADAR', 'A1');
-- Error: ability limit reached
```

| Kontrol | Kanıt | Durum |
|---|---|---|
| Radar `ability_use`'a `found` sayısıyla yazılır | `WeaponFlowTest#radarRevealsShipsOnBothSides` | PASS |
| Torpido hücreleri `move`'a `weapon = 'TORPEDO'` ile yazılır | `WeaponFlowTest#torpedoHitsAreaAndSinks` | PASS |
| Tetikleyici 4. radarı / 3. torpidoyu reddeder | `SchemaUpgradeTest#triggerLimitsRadarsAndTorpedoes` | PASS |
| Geçmiş penceresinde `Weapon` sütunu (SHOT / RADAR / TORPEDO) | `GameController#moves` + manuel | PASS |

## 6. Sonuç

**PASS.** Bilgisayara karşı oyunda arayüzde manuel olarak (radar ve torpido animasyonları ekran görüntüleriyle) ve 12 motor testi, 7 controller testi ve 5 veritabanı testiyle doğrulandı.
