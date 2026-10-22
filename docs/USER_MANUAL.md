# Kullanım Kılavuzu

## 1. Başlatma

```
java -jar target/battleship.jar
```

İsteğe bağlı ilk argüman veritabanı adresidir. Aynı bilgisayarda iki pencere açarken her birinin kendi geçmişini tutması için farklı dosya verebilirsiniz:

```
java -jar target/battleship.jar jdbc:sqlite:data/client.db
```

## 2. Ekran

| Bölge | İçerik |
|---|---|
| Sol | **Your ships**: kendi tahtanız (gemiler gri) |
| Orta | **Your enemy**: rakip tahtası (gemiler gizli) |
| Sağ üst | **Ships allocation**: yerleşim |
| Sağ orta | **Connection Configuration**: sunucu / istemci |
| Sağ alt | **Chat History**: sohbet |
| Alt | **Output**: zaman damgalı olay günlüğü, en altta durum çubuğu |

Renkler: gri = gemi, açık mavi + koyu nokta = ıska (MISS), kırmızı + beyaz nokta = isabet (HIT), koyu kırmızı = batan gemi (SUNK).

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

## 5. Gemileri yerleştirme

* **Automatic allocation** (varsayılan): açılışta rastgele bir filo hazırdır. `New allocation` yeni bir rastgele dizilim üretir.
* **Manual allocation**: `Clear allocation` tüm gemileri kaldırır ve manuel moda geçer.
  * Listeden gemiyi seçin (`Carrier (5)`, `Battleship (4)`, `Cruiser (3)`, `Submarine (3)`, `Destroyer (2)`).
  * `R` tuşu veya `Rotate` yönü değiştirir (yatay / dikey).
  * Fareyle tahtanın üzerinde gezinin: yeşil önizleme = yerleştirilebilir, kırmızı = yerleştirilemez.
  * Sol tık yerleştirir, yerleşmiş gemiye sağ tık onu kaldırır.
* Beş gemi yerleşince ve rakip bağlıyken `Ready` etkinleşir.

## 6. Oyun

1. İki taraf da `Ready` deyince oyun başlar. **Sunucu ilk atışı yapar.**
2. Sıra sizdeyken durum çubuğu `Your turn` gösterir ve rakip tahtası canlıdır; bir hücreye tıklayın.
3. Sıra rakipteyken rakip tahtası soluk görünür ve tıklamalar yok sayılır.
4. Sıra her atıştan sonra karşı tarafa geçer (isabet olsa da).
5. 17 gemi hücresinin tamamını ilk vuran kazanır: `Game over: you won` / `Game over: you lost`.

## 7. Sohbet

Bağlıyken alttaki kutuya yazıp `Enter` veya `Send`. En fazla 500 karakter. Sizin satırlarınız mavi (`Me:`), rakibinkiler kırmızı (rakibin adı).

## 8. Menü ve kısayollar

| Menü | Kısayol | İşlev |
|---|---|---|
| Game → New Game | Ctrl+N | Aynı rakiple yeni oyun (önceki bittiyse). Oyun sürerken onay sorar ve bağlantıyı keser |
| Game → History | Ctrl+H | Geçmiş oyunlar, istatistik; bir satıra çift tık o oyunun hamlelerini gösterir |
| Game → Exit | Ctrl+Q | Çıkış (oyun sürüyorsa onay sorar) |
| Help → About | | Sürüm bilgisi |

## 9. Bağlantıyı kesme

* Sunucu: `Stop server`. Oyun sürüyorsa `A game is in progress. Abort it?` sorusu gelir. Oyun `ABORTED` olarak kaydedilir.
* İstemci: `Disconnect`.
* Rakip ayrılırsa `Opponent has left the game.` mesajı çıkar; sunucu yeni bir istemci için dinlemeye devam eder.

## 10. Sorun giderme

| Belirti | Çözüm |
|---|---|
| `Port is in use` | Başka bir port seçin ya da o portu kullanan programı kapatın |
| İstemci bağlanamıyor (LAN) | Sunucu bilgisayarın güvenlik duvarında 9090/TCP'ye izin verin; IP adresini kontrol edin |
| `My IP Address` yanlış adres gösteriyor | Birden fazla ağ kartı varsa doğru adresi `ipconfig` ile bulup rakibe onu verin |
| `Cannot open database` | `data/` klasörüne yazma izni olduğundan emin olun |
