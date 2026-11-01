# NAPS2 Android Edition (Not Another PDF Scanner 2 for Android)

NAPS2 (Not Another PDF Scanner 2) masaüstü uygulamasının tüm gelişmiş belge tarama, A4 PDF motoru, görüntü temizleme (siyah-beyaz eşikleme) ve çok sayfalı yönetim özelliklerinin modern Android ekosistemine eksiksiz uyarlanmış (port edilmiş) mobil versiyonudur.

---

## 🌟 Öne Çıkan Özellikler

### 1. 📷 Çoklu Belge Tarama ve İçe Aktarma
- **Kamera ile Belge Tarama:** Android cihazınızın kamerası ile anında tekli veya çok sayfalı evrak tarama.
- **Wi-Fi Ağ Tarayıcısı (eSCL / AirPrint / Mopria):** HP, Canon, Epson, Brother, Xerox veya Samsung Wi-Fi tarayıcınızın IP adresini girerek sürücüsüz doğrudan ağdan tarama.
- **Evrak ve Görsel İçe Aktarma:** PDF, TIFF (çok sayfalı), UDF (UYAP), JPG, PNG, WEBP ve BMP dosyalarını içe aktarma.
- **Doğrudan UDF (UYAP) Desteği:** UDF dosyalarını açar, XML içerik ve paragraflarını A4 sayfalarına basarak taranmış belge tuvaline aktarır.

### 2. 🔲 NAPS2 Görüntü Temizleme ve Filtre Motoru
- **Siyah-Beyaz Eşikleme (B&W Threshold):** NAPS2'nin efsanevi filtresi! Arka plandaki sararmaları, gölgeleri ve lekeleri yok ederek metinleri keskin siyah, kağıdı bembeyaz yapar.
- **Gri Tonlama (Grayscale):** 8-bit temiz arşivlik görünüm.
- **Canlı Parlaklık ve Kontrast:** Canlı kaydırıcılar ile anında önizleme.
- **Döndürme ve Aynalama:** 90° Sağa/Sola döndürme, 180° ters çevirme ve yatay/dikey aynalama.

### 3. 📄 NAPS2 A4 PDF Standardizasyon Motoru
- **Otomatik Yönlendirme:** Genişlik > Yükseklik ise Yatay A4, aksi halde Dikey A4 kağıt standardı.
- **Yazıcı Güvenli Alanı:** 5 mm kenar boşluğu ile baskıya hazır A4 üretimi.
- **Tek PDF'de Birleştirme (Merge) veya Ayrı Ayrı PDF Kaydetme.**
- **Donanım Hızlandırmalı Vektör Çıktı:** Android `PdfDocument` ve Skia grafik motoru ile yüksek çözünürlüklü, optimize edilmiş PDF çıktısı.

### 4. 📤 Paylaşım ve Entegrasyon
- **Tek Dokunuşla Paylaşım:** Taranan ve birleştirilen belgeleri doğrudan WhatsApp, UYAP, Gmail, Google Drive veya Bluetooth ile gönderme.
- **Galeri ve İndirilenler Entegrasyonu:** Üretilen PDF'ler anında `/sdcard/Download` klasörüne kaydedilir ve medya tarayıcısına kaydedilir.

---

## 📱 Kurulum ve Çalıştırma

### Hazır APK'yı Yükleme
Derlenen APK dosyası doğrudan cihazınızın **İndirilenler** klasöründedir:
- Konum: `/sdcard/Download/naps2-android.apk`

Termux içerisinden yüklemek için:
```bash
cd ~/naps2-android
./install-apk.sh
```
Ekrana gelen Android Paket Yükleyici penceresinden **Yükle (Install)** butonuna dokunmanız yeterlidir.

---

## 🛠️ Sıfırdan Derleme (Build from Source)

Termux veya Linux üzerinde tamamen bağımsız olarak derlemek için:
```bash
cd ~/naps2-android
./build.sh
```

Derleme adımları:
1. `aapt2 compile`: XML kaynakları ve layoutlar derlenir.
2. `aapt2 link`: Varlıklar ve `R.java` bağlanır.
3. `javac`: Java kaynak kodları (`NapsPdfEngine`, `EsclScannerClient`, `UdfDocumentParser`, `NapsNativeBridge`, `MainActivity`) derlenir.
4. `d8`: Java bytecode'u Android Dalvik Executable (`classes.dex`) formatına çevrilir.
5. `zipalign`: 4-byte bellek hizalaması yapılır.
6. `apksigner`: APK imzalanır ve doğrulanır.
