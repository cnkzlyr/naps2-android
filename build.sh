#!/data/data/com.termux/files/usr/bin/bash
# ==============================================================================
# NAPS2 Android - Otomatik APK Derleme ve Paketleme Scripti
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

ANDROID_JAR="/data/data/com.termux/files/home/android-sdk/android-34/android.jar"
if [ ! -f "$ANDROID_JAR" ]; then
    echo "[HATA] android.jar bulunamadı: $ANDROID_JAR"
    exit 1
fi

echo "=========================================================="
echo "  NAPS2 Android APK Derleme Süreci Başlatılıyor"
echo "=========================================================="

BUILD_TMP="$SCRIPT_DIR/build_tmp"
rm -rf "$BUILD_TMP"
mkdir -p "$BUILD_TMP/obj" "$BUILD_TMP/dex" "$SCRIPT_DIR/dist"

# 1. Kaynakları derle (aapt2 compile)
echo "[1/6] Android XML kaynakları derleniyor (aapt2)..."
aapt2 compile --dir res -o "$BUILD_TMP/compiled_res.zip"

# 2. APK iskeletini ve R.java'yı oluştur (aapt2 link)
echo "[2/6] APK iskeleti ve varlıklar bağlanıyor (aapt2 link)..."
aapt2 link -o "$BUILD_TMP/base.apk" \
    -I "$ANDROID_JAR" \
    --manifest AndroidManifest.xml \
    "$BUILD_TMP/compiled_res.zip" \
    -A assets \
    --java src

# 3. Java Kodlarını Derle (javac)
echo "[3/6] Java kaynak kodları derleniyor (javac)..."
javac -cp "$ANDROID_JAR" -d "$BUILD_TMP/obj" src/com/naps2/android/*.java

# 4. Dalvik Bytecode Üret (d8)
echo "[4/6] DEX bytecode oluşturuluyor (d8)..."
d8 --output "$BUILD_TMP/dex" "$BUILD_TMP/obj/com/naps2/android/"*.class --lib "$ANDROID_JAR"

# 5. DEX'i APK'ya Ekle ve Zipalign Uygula
echo "[5/6] classes.dex APK içine ekleniyor ve zipalign uygulanıyor..."
cd "$BUILD_TMP"
zip -uj base.apk dex/classes.dex
zipalign -p -f 4 base.apk aligned.apk

# 6. APK'yı İmzala (apksigner)
echo "[6/6] APK imzalanıyor (apksigner)..."
KEYSTORE="$SCRIPT_DIR/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkey -v -keystore "$KEYSTORE" \
        -storepass android -alias androiddebugkey -keypass android \
        -keyalg RSA -keysize 2048 -validity 10000 \
        -dname "CN=NAPS2 Android,O=NAPS2,C=TR" >/dev/null 2>&1
fi

FINAL_APK="$SCRIPT_DIR/dist/naps2-android.apk"
apksigner sign --ks "$KEYSTORE" --ks-pass pass:android --key-pass pass:android --out "$FINAL_APK" aligned.apk

# İmzayı doğrula
apksigner verify "$FINAL_APK"

# /sdcard/Download konumuna kopyala
if [ -d "/sdcard/Download" ]; then
    cp -f "$FINAL_APK" "/sdcard/Download/naps2-android.apk"
    echo "[BİLGİ] APK dosyası cihazın İndirilenler klasörüne kopyalandı:"
    echo "       /sdcard/Download/naps2-android.apk"
fi

rm -rf "$BUILD_TMP"

echo ""
echo "=========================================================="
echo "  [BAŞARILI] NAPS2 Android APK başarıyla derlendi!"
echo "  APK Konumu: $FINAL_APK"
echo "  Boyut     : $(ls -lh "$FINAL_APK" | awk '{print $5}')"
echo "=========================================================="
