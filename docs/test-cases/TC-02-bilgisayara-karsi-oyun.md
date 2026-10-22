# Test Case TC-02: Bilgisayara Karşı Oyun

| Alan | Değer |
|---|---|
| Test Case ID | TC-02 |
| Başlık | Oyuncunun bilgisayara karşı (Easy / Normal / Hard) bir oyunu başlatıp bitirmesi |
| İlgili ister | "Oyunda Bilgisayara Karşı Oynama Olsun" (2 puan) |
| Öncelik | Yüksek |
| Tür | Fonksiyonel, yapay zekâ davranışı |
| Hazırlayan / Tarih | 22.10.2026 |
| Sürüm | BattleShip v1.1 |

## 1. Amaç

Ağ bağlantısı olmadan bilgisayar rakibe karşı oynanabildiğini, bilgisayarın yalnızca kurallara uygun hamleler yaptığını, radar ve torpidoyu kullandığını, zorluk seviyelerinin gerçekten farklı oynadığını ve oyunun `AI` modunda kaydedildiğini doğrulamak.

## 2. Ön koşullar

* `target/battleship.jar` derlenmiş.
* Ağ gerekmez.

## 3. Test verisi

| Veri | Değer |
|---|---|
| Takma ad | `Kaan` |
| Zorluk | Normal (ayrıca Easy ve Hard otomatik testte) |

## 4. Adımlar

| # | Eylem | Beklenen sonuç | Gerçek sonuç | Durum |
|---|---|---|---|---|
| 1 | Uygulamayı aç | Banner `READY TO PLAY` | Beklendiği gibi | PASS |
| 2 | `Play vs computer` seç | Zorluk kutusu etkin olur; IP ve port alanları pasifleşir; düğmeler `Start game` / `End game` | Beklendiği gibi | PASS |
| 3 | Zorluk `Normal`, `Start game` | Output: `You are playing against Computer (Normal).`, `Computer (Normal) is ready.`; başlıkta `vs Computer (Normal)` | Beklendiği gibi | PASS |
| 4 | `Ready` | `Game started! Your turn.` İlk hamle oyuncunundur, banner `YOUR TURN` | Beklendiği gibi | PASS |
| 5 | Düşman tahtasına bir atış yap | Sonuç gösterilir; yaklaşık 1 saniye "düşünme" süresinden sonra bilgisayar kendi hamlesini yapar, sıra geri gelir | Beklendiği gibi | PASS |
| 6 | Bir ısabetten sonra bilgisayarın hamlelerini izle (Normal) | Bilgisayar, isabet ettiği hücrenin komşularına ateş eder (avlama/hedefleme) | Otomatik testte doğrulandı (`ComputerPlayerTest#followsAHit`) | PASS |
| 7 | Birkaç tur oyna | Bilgisayar zaman zaman radar veya torpido kullanır; Output'ta `Computer (Normal) fired a torpedo at A9…` gibi satırlar; kendi tahtamızda animasyon | Manuel çalıştırmada bilgisayar 2 torpido kullandı, biri Destroyer'ı batırdı | PASS |
| 8 | Bilgisayar asla aynı hücreye iki kez atış yapmaz, radarla boş olduğu görülmüş hücreye atış yapmaz | — | `ComputerPlayerTest#everyLevelFinishesWithLegalMoves` (3 seviye × 15 oyun) | PASS |
| 9 | Bilgisayar 3'ten fazla radar, 2'den fazla torpido kullanmaz | — | Aynı test + `ability_use` tetikleyicisi | PASS |
| 10 | Bilgisayarın 17 hücresini vur | `VICTORY!` ve `Victory! You won!` | `ComputerGameTest#playerCanBeatTheComputer` | PASS |
| 11 | Bilgisayarın kazanmasına izin ver | `DEFEAT`; bilgisayar radar veya torpido kullanmış olur | `ComputerGameTest#hardComputerUsesRadarAndTorpedoAndCanWin` | PASS |
| 12 | Oyun bittikten sonra `Game → New Game`, `Ready` | Aynı bilgisayara karşı yeni oyun başlar, yeni `game` kaydı | `ComputerGameTest#newGameAgainstTheComputer` | PASS |
| 13 | Oyun sürerken `End game` | Onay sorusu; oyun `ABORTED` kaydedilir; "Opponent has left" mesajı çıkmaz | `ComputerGameTest#endingTheGameAbortsIt` | PASS |
| 14 | Sohbete bir şey yaz | Bilgisayar kısa bir cevap verir (ör. `Nice try!`) | `ComputerGameTest#chatGetsAnAnswer` | PASS |

## 5. Zorluk seviyelerinin karşılaştırılması

Aynı rastgele tohumlarla 200'er simülasyon oyunu; bilgisayarın 17 hücreyi batırmak için harcadığı ortalama hamle sayısı (radar ve torpido dahil):

| Seviye | Strateji | Ortalama hamle |
|---|---|---|
| Easy | Rastgele atış, ara sıra rastgele radar/torpido | 62,1 |
| Normal | Radar bulgularına önce ateş, isabeti takip, dama tahtası deseniyle avlanma | 27,5 |
| Hard | Olasılık haritası (kalan gemilerin tüm olası yerleşimleri), radar ve torpidoyu en verimli yere kullanma | 20,9 |

`ComputerPlayerTest#harderLevelsNeedFewerTurns` bu sıralamanın (Hard < Normal < Easy) korunduğunu her derlemede kontrol eder.

## 6. Veritabanı kontrolü

```sql
SELECT mode, status FROM game ORDER BY id DESC LIMIT 1;                       -- AI, FINISHED
SELECT p.nickname FROM game_player gp JOIN player p ON p.id = gp.player_id
 WHERE gp.game_id = <id> AND gp.role = 'REMOTE';                             -- Computer (Normal)
SELECT ability, COUNT(*) FROM ability_use WHERE game_id = <id> AND user_role = 'REMOTE' GROUP BY ability;
```

## 7. Sonuç

**PASS.** Arayüzde manuel olarak (Normal seviye, radar ve torpido dahil) ve 7 otomatik controller testi ile 7 yapay zekâ testiyle doğrulandı.
