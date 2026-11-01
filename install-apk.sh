#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# NAPS2 Android - APK Kurulum Scripti
# ==============================================================================

APK_PATH="/sdcard/Download/naps2-android.apk"

if [ ! -f "$APK_PATH" ]; then
    echo "[HATA] $APK_PATH bulunamadı. Lütfen önce ./build.sh çalıştırın."
    exit 1
fi

echo "NAPS2 Android APK paketi kuruluyor..."
if command -v termux-open >/dev/null 2>&1; then
    termux-open "$APK_PATH"
    echo "[BAŞARILI] Android Paket Yükleyici ekranı açıldı."
    echo "Lütfen ekranınızdaki 'Yükle' (Install) butonuna dokunun."
else
    echo "Lütfen dosya yöneticisinden $APK_PATH dosyasına dokunarak kurun."
fi
