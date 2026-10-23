# Kullanım Kılavuzu

## 1. Başlatma

```
java -jar target/vurgahpeyi.jar
```

İsteğe bağlı ilk argüman veritabanı adresidir. Aynı bilgisayarda iki pencere açarken her birinin kendi geçmişini tutması için farklı dosya verebilirsiniz:

```
java -jar target/vurgahpeyi.jar jdbc:sqlite:data/client.db
```

## 2. Ekran

| Bölge | İçerik |
|---|---|
| Sol | **Your ships**: kendi tahtanız (gemiler gri) |
| Orta | **Your enemy**: rakip tahtası (gemiler gizli) |
| Sağ üst | **Ships allocation**: yerleşim |
| Sağ orta | **Connection Configuration**: sunucu / istemci / bilgisayara karşı |
| Sağ alt | **Chat History**: sohbet |
| Alt | **Output**: zaman damgalı olay günlüğü, en altta durum çubuğu |

Üstte **sıra bandı** (`YOUR TURN` yeşil, `ENEMY'S TURN` kırmızı, `PLACE YOUR SHIPS`, `VICTORY!`, `DEFEAT`) ve sağda rakibin adı (`vs Computer (Hard)` gibi) görünür.
Her tahtanın altında **filo şeridi** vardır: 5 geminin küçük çizimi; batan gemiler kırmızı enkaz olarak üstü çizili gösterilir (`4/5 afloat`).
Düşman tahtasının altında **silah çubuğu** vardır (bkz. Bölüm 7).

| İşaret | Anlamı |
|---|---|
| Gri gövde (güverte, top taretleri, kule) | Gemi; her tipin kendi çizimi var (uçak gemisinde uçuş güvertesi, denizaltıda kule) |
| Mavi halkalar | Iska (MISS) |
| Titreşen ateş | İsabet (HIT) |
| Kırmızı çatlaklı enkaz ve duman | Batan gemi (SUNK) |
| Nabız gibi atan kırmızı elmas | Radarın bulduğu, henüz vurulmamış düşman gemi hücresi |
| Hafif kırmızı alan | Radarla taranmış bölge |

## 3. Sunucu olarak oynama

1. `Play as server` seçili olsun. `My IP Address` alanında bilgisayarınızın adresi görünür; rakibe bu adresi verin.
2. `Port` varsayılan `9090`. 1024–65535 dışında bir değer kırmızı çerçeveyle reddedilir.
3. `Nickname` alanına adınızı yazın (1–20 karakter).
4. `Start server`. Output: `Server is opened!`, durum: `Waiting for opponent...`.
5. Rakip bağlanınca Output'ta `<ad> wants to join` görünür.

## 4. İstemci olarak oynama

1. `Play as client` seçin. Etiket `Server IP:` olur; sunucunun adresini yazın (aynı bilgisayarda `127.0.0.1`).
2. Port sunucuyla aynı olmalı.
3. `Connect`. Bağlantı 5 saniye içinde kurulamazsa hata mesajı çıkar.

## 4b. Bilgisayara karşı oynama

1. `Play vs computer` seçin. Yanındaki kutudan zorluk seçin:
   * **Easy**: rastgele atar, radar ve torpidoyu rastgele kullanır.
   * **Normal**: radar bulgularına önce ateş eder, isabet ettiği gemiyi takip eder, dama tahtası deseniyle arar.
   * **Hard**: kalan gemilerin tüm olası yerleşimlerinden olasılık haritası çıkarır; radarı ve torpidoyu en verimli yere kullanır.
2. IP ve port gerekmez. `Start game`.
3. Bilgisayar gemilerini hemen yerleştirir. Siz `Ready` deyince oyun başlar; **ilk hamle sizindir**.
4. Bilgisayar her hamleden önce yaklaşık 1 saniye düşünür.
5. `End game` oyunu bitirir (sürüyorsa onay sorar, oyun `ABORTED` kaydedilir).

## 5. Gemileri yerleştirme

* **Automatic allocation** (varsayılan): açılışta rastgele bir filo hazırdır. `New allocation` yeni bir rastgele dizilim üretir.
* **Manual allocation**: `Clear allocation` tüm gemileri kaldırır ve manuel moda geçer.
  * Listeden gemiyi seçin (`Carrier (5)`, `Battleship (4)`, `Cruiser (3)`, `Submarine (3)`, `Destroyer (2)`).
  * `R` tuşu veya `Rotate` yönü değiştirir (yatay / dikey).
  * Fareyle tahtanın üzerinde gezinin: geminin yarı saydam çizimi yeşilse yerleştirilebilir, kırmızıysa yerleştirilemez.
  * Sol tık yerleştirir, yerleşmiş gemiye sağ tık onu kaldırır.
* Beş gemi yerleşince ve rakip bağlıyken `Ready` etkinleşir.

## 6. Oyun

1. İki taraf da `Ready` deyince oyun başlar. **Sunucu (bilgisayara karşı ise siz) ilk hamleyi yapar.**
2. Sıra sizdeyken bant `YOUR TURN` gösterir, düşman tahtasının çerçevesi yeşil parlar; bir silah seçip hücreye tıklayın.
3. Sıra rakipteyken düşman tahtası kararır ve tıklamalar yok sayılır.
4. İsabet eden oyuncu ıskalayana kadar atışa devam eder. Iska ve radar sırayı karşı tarafa geçirir; torpido en az bir hücreyi vurursa sıra sizde kalır.
5. 17 gemi hücresinin tamamını ilk vuran kazanır: `VICTORY!` / `DEFEAT`.

## 7. Silahlar: atış, radar, torpido

Düşman tahtasının altındaki çubuktan seçin (veya `1`, `2`, `3` tuşları). Kalan haklarınız düğmelerde, rakibin kalan hakları altında yazar (`Enemy: radar 2 · torpedo 1`).

| Silah | Tuş | Alan | Hak | Ne yapar |
|---|---|---|---|---|
| Shot | 1 | 1 hücre | Sınırsız | Normal atış |
| Radar | 2 | 4×4 | 3 | Alandaki düşman gemi hücrelerini gösterir, **vurmaz**. Merkezden yayılan kırmızı dalga (Oracle Lens tarzı) ve dönen tarama kolu; dalga ulaştıkça bulunan hücrelerde kırmızı elmas belirir |
| Torpedo | 3 | 3×3 | 2 | Torpido kenardan gelip patlar; alandaki daha önce vurulmamış her hücreye atış yapılır, birden fazla gemi batabilir |

* Radar veya torpido seçiliyken fareyle gezdiğinizde alan kesikli çerçeveyle gösterilir. Kenarlarda alan tahtanın içine kaydırılır.
* Kullandıktan sonra seçim kendiliğinden `Shot` olur.
* Hakkı biten silahın düğmesi pasifleşir. Tamamı daha önce vurulmuş bir alana torpido atılamaz (hak gitmez).
* Rakip radar kullandığında kendi tahtanızda aynı animasyonu ve taranan alanı görürsünüz.

## 8. Sohbet

Bağlıyken (veya bilgisayara karşı) alttaki kutuya yazıp `Enter` veya `Send`. Bilgisayar kısa cevaplar verir. En fazla 500 karakter. Sizin satırlarınız mavi (`Me:`), rakibinkiler kırmızı (rakibin adı).

## 9. Menü ve kısayollar

| Menü | Kısayol | İşlev |
|---|---|---|
| Game → New Game | Ctrl+N | Aynı rakiple yeni oyun (önceki bittiyse). Oyun sürerken onay sorar ve bağlantıyı keser |
| Game → History | Ctrl+H | Geçmiş oyunlar (mod: SERVER / CLIENT / AI), istatistik; bir satıra çift tık o oyunun hamlelerini silah sütunuyla gösterir |
| Game → Exit | Ctrl+Q | Çıkış (oyun sürüyorsa onay sorar) |
| Help → How to play | | Kurallar ve silahlar |
| Help → About | | Sürüm bilgisi |
| – | 1 / 2 / 3 | Shot / Radar / Torpedo seç |
| – | R | Yerleştirmede gemiyi döndür |

## 10. Bağlantıyı kesme

* Sunucu: `Stop server`. Oyun sürüyorsa `A game is in progress. Abort it?` sorusu gelir. Oyun `ABORTED` olarak kaydedilir.
* İstemci: `Disconnect`.
* Rakip ayrılırsa `Opponent has left the game.` mesajı çıkar; sunucu yeni bir istemci için dinlemeye devam eder.

## 11. Sorun giderme

| Belirti | Çözüm |
|---|---|
| `Port is in use` | Başka bir port seçin ya da o portu kullanan programı kapatın |
| İstemci bağlanamıyor (LAN) | Sunucu bilgisayarın güvenlik duvarında 9090/TCP'ye izin verin; IP adresini kontrol edin |
| `My IP Address` yanlış adres gösteriyor | Birden fazla ağ kartı varsa doğru adresi `ipconfig` ile bulup rakibe onu verin |
| `Cannot open database` | `data/` klasörüne yazma izni olduğundan emin olun |
