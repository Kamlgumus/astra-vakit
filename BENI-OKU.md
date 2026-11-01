# ✦ Astra Vakit — Android Ezan Vakitleri Uygulaması

Gerçek (native) Android uygulaması. HTML değil, Kotlin ile yazıldı.

## Özellikler
- İlk açılışta **ismini sorar**, bir daha unutmaz
- **Konum + bildirim izni** ister (Android'in kendi izin penceresiyle)
- Konumuna göre vakitleri **cihazda hesaplar** (internet gerekmez), konum değişince otomatik güncellenir
- Büyük geri sayım halkası, güneş/ay yayı, gün uzunluğu, gece yarısı, teheccüd, kerahat vakitleri
- **Ana ekran widget'ı** (sıradaki vakit + canlı geri sayım + 6 vaktin hepsi)
- **Tam zamanında bildirim** (vakit girince, istersen 10/15/30/45 dk önce de), vakit bazında aç/kapa
- Aylık takvim, Hicri tarih, Ramazan sayacı
- **Kıble pusulası** (sensörlü, doğru yöne bakınca yeşile döner)
- Dijital tesbih (titreşimli, zikir seçmeli)
- Uzay teması: yıldızlar, nebula, halkalı gezegen, kayan yıldız
- Hesaplama yöntemi: Diyanet, MWL, Umm al-Qura, Mısır, Karaçi, ISNA + Hanefi/Şafii ikindi

---

## APK'yı nasıl alırım? (Bilgisayara hiçbir şey kurmadan — Yol A)

1. https://github.com adresinde ücretsiz hesap aç.
2. Sağ üstte **+ → New repository** → isim: `astra-vakit` → **Create repository**.
3. Açılan sayfada **"uploading an existing file"** linkine tıkla.
4. Bu zip'i bilgisayarında **çıkar**. İçindeki **tüm dosya ve klasörleri** (`.github` klasörü dahil!) sayfaya sürükle-bırak. **Commit changes** de.
   - `.github` klasörü gizli sayılıp yüklenmezse: **Add file → Create new file** de, isim kutusuna `.github/workflows/build.yml` yaz, zip'teki aynı dosyanın içeriğini yapıştır, commit et.
5. Üstteki **Actions** sekmesine gir. **APK Derle** işi otomatik başlar (3-6 dk). Yeşil ✓ olunca işe tıkla.
6. Sayfanın altındaki **Artifacts** bölümünden **Astra-Vakit-APK**'yı indir, zip'ten çıkan `app-debug.apk` dosyasını telefona at.
7. Telefonda APK'ya dokun → "Bilinmeyen kaynaklardan yüklemeye izin ver" → Yükle.

## Yol B — Android Studio ile
1. Android Studio'yu kur, **Open** ile bu klasörü aç, Gradle eşitlemesini bekle.
2. **Build → Build Bundle(s)/APK(s) → Build APK(s)**.
3. APK: `app/build/outputs/apk/debug/app-debug.apk`

## Widget nasıl eklenir?
Uygulamada **Ayarlar → Ana ekrana widget ekle**, ya da ana ekrana uzun bas → Widget'lar → **Astra Vakit**.

## Bildirimler zamanında gelmiyorsa
- Telefon Ayarlar → Uygulamalar → Astra Vakit → Pil → **Kısıtlama yok**
- Uygulama içinden **Ayarlar → Tam zamanlı alarm izni**'ni ver
- Ayarlar → **Test bildirimi gönder** ile dene

## Notlar
- Vakitler astronomik hesaptır; Diyanet takvimiyle 1-3 dk fark olabilir.
- Bildirim sesi telefonun varsayılan bildirim sesidir (ezan ses dosyası eklenmedi; telif ve boyut nedeniyle).
