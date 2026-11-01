package com.naps2.android;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
import android.util.Base64;
import android.webkit.JavascriptInterface;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NapsNativeBridge {

    private final MainActivity activity;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public NapsNativeBridge(MainActivity activity) {
        this.activity = activity;
    }

    @JavascriptInterface
    public void showToast(final String msg) {
        mainHandler.post(() -> Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show());
    }

    @JavascriptInterface
    public void vibrate(int ms) {
        try {
            Vibrator v = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null && v.hasVibrator()) {
                v.vibrate(ms);
            }
        } catch (Exception ignored) {}
    }

    @JavascriptInterface
    public void openCamera() {
        activity.launchNativeCamera();
    }

    @JavascriptInterface
    public void openFileChooser(String mimeTypes) {
        activity.launchNativeFileChooser(mimeTypes);
    }

    @JavascriptInterface
    public String getDownloadPath() {
        File dl = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        return dl.getAbsolutePath();
    }

    @JavascriptInterface
    public void savePdf(final String pagesJson, final String optionsJson) {
        new Thread(() -> {
            try {
                JSONArray arr = new JSONArray(pagesJson);
                JSONObject optObj = (optionsJson != null && !optionsJson.isEmpty()) ? new JSONObject(optionsJson) : new JSONObject();

                List<Bitmap> bitmaps = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    String item = arr.getString(i);
                    Bitmap bmp = decodeItemToBitmap(item);
                    if (bmp != null) {
                        bitmaps.add(bmp);
                    }
                }

                if (bitmaps.isEmpty()) {
                    showToast("Kaydedilecek geçerli sayfa bulunamadı!");
                    return;
                }

                NapsPdfEngine.PdfOptions options = new NapsPdfEngine.PdfOptions();
                options.paperSize = optObj.optString("paperSize", "A4");
                options.orientation = optObj.optString("orientation", "AUTO");

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                String customName = optObj.optString("filename", "NAPS2_Belge_" + timestamp + ".pdf");
                if (!customName.toLowerCase().endsWith(".pdf")) customName += ".pdf";

                File dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File outFile = new File(dlDir, customName);

                NapsPdfEngine.createPdfFromBitmaps(bitmaps, outFile, options);

                MediaScannerConnection.scanFile(activity, new String[]{outFile.getAbsolutePath()}, new String[]{"application/pdf"}, null);

                showToast("PDF Kaydedildi:\n" + outFile.getName() + " (" + (outFile.length() / 1024) + " KB)");
                vibrate(100);

                activity.sendWebEvent("onPdfSaved", outFile.getAbsolutePath());
            } catch (final Exception e) {
                mainHandler.post(() -> Toast.makeText(activity, "PDF Kaydetme Hatası: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    @JavascriptInterface
    public void sharePdf(final String pagesJson, final String optionsJson) {
        new Thread(() -> {
            try {
                JSONArray arr = new JSONArray(pagesJson);
                JSONObject optObj = (optionsJson != null && !optionsJson.isEmpty()) ? new JSONObject(optionsJson) : new JSONObject();

                List<Bitmap> bitmaps = new ArrayList<>();
                for (int i = 0; i < arr.length(); i++) {
                    Bitmap bmp = decodeItemToBitmap(arr.getString(i));
                    if (bmp != null) bitmaps.add(bmp);
                }

                if (bitmaps.isEmpty()) {
                    showToast("Paylaşılacak sayfa bulunamadı!");
                    return;
                }

                NapsPdfEngine.PdfOptions options = new NapsPdfEngine.PdfOptions();
                options.paperSize = optObj.optString("paperSize", "A4");
                options.orientation = optObj.optString("orientation", "AUTO");

                String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(new Date());
                File cachePdf = new File(activity.getCacheDir(), "NAPS2_Paylasim_" + timestamp + ".pdf");

                NapsPdfEngine.createPdfFromBitmaps(bitmaps, cachePdf, options);

                Uri contentUri = NapsFileProvider.getUriForFile(activity, cachePdf);

                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("application/pdf");
                shareIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "NAPS2 Taranmış Belge");
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                activity.startActivity(Intent.createChooser(shareIntent, "Belgeyi Paylaş"));
            } catch (final Exception e) {
                mainHandler.post(() -> Toast.makeText(activity, "Paylaşım Hatası: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    @JavascriptInterface
    public void saveImage(final String base64Data, final String filename, final String mimeType) {
        new Thread(() -> {
            try {
                Bitmap bmp = decodeItemToBitmap(base64Data);
                if (bmp == null) {
                    showToast("Görsel çözümlenemedi!");
                    return;
                }

                File dlDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                File outFile = new File(dlDir, filename);

                Bitmap.CompressFormat fmt = Bitmap.CompressFormat.JPEG;
                if ("image/png".equalsIgnoreCase(mimeType) || filename.toLowerCase().endsWith(".png")) {
                    fmt = Bitmap.CompressFormat.PNG;
                }

                try (FileOutputStream fos = new FileOutputStream(outFile)) {
                    bmp.compress(fmt, 95, fos);
                }

                MediaScannerConnection.scanFile(activity, new String[]{outFile.getAbsolutePath()}, new String[]{mimeType}, null);
                showToast("Görsel Kaydedildi: " + outFile.getName());
                vibrate(60);
            } catch (final Exception e) {
                mainHandler.post(() -> Toast.makeText(activity, "Görsel Kaydetme Hatası: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    @JavascriptInterface
    public void scanFromNetwork(final String ip, final int port, final String optionsJson) {
        new Thread(() -> {
            try {
                mainHandler.post(() -> showToast("Tarayıcıya bağlanılıyor (" + ip + ")..."));
                EsclScannerClient.ScanRequest req = new EsclScannerClient.ScanRequest();
                req.ip = ip;
                req.port = port;

                if (optionsJson != null && !optionsJson.isEmpty()) {
                    JSONObject obj = new JSONObject(optionsJson);
                    req.resolution = obj.optInt("resolution", 300);
                    req.colorMode = obj.optString("colorMode", "RGB24");
                    req.inputSource = obj.optString("inputSource", "Platen");
                }

                Bitmap scannedBmp = EsclScannerClient.scanDocument(req);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                scannedBmp.compress(Bitmap.CompressFormat.JPEG, 92, baos);
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

                activity.sendWebEvent("onScannedPageReceived", "data:image/jpeg;base64," + b64);
                showToast("Tarama tamamlandı!");
                vibrate(100);
            } catch (final Exception e) {
                mainHandler.post(() -> Toast.makeText(activity, "Ağ Taraması Hatası: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    @JavascriptInterface
    public void parseUdf(final String base64Data) {
        new Thread(() -> {
            try {
                String cleanB64 = base64Data;
                if (cleanB64.contains(",")) cleanB64 = cleanB64.substring(cleanB64.indexOf(",") + 1);
                byte[] bytes = Base64.decode(cleanB64, Base64.DEFAULT);

                List<Bitmap> pages = UdfDocumentParser.renderUdfToA4Pages(bytes);
                JSONArray resultArr = new JSONArray();

                for (Bitmap bmp : pages) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bmp.compress(Bitmap.CompressFormat.JPEG, 90, baos);
                    String pageB64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                    resultArr.put("data:image/jpeg;base64," + pageB64);
                }

                activity.sendWebEvent("onUdfParsed", resultArr.toString());
                showToast("UDF Belgesi Çözümlendi (" + pages.size() + " sayfa)");
            } catch (final Exception e) {
                mainHandler.post(() -> Toast.makeText(activity, "UDF Çözümleme Hatası: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private Bitmap decodeItemToBitmap(String item) {
        try {
            if (item.startsWith("data:")) {
                String base64 = item.substring(item.indexOf(",") + 1);
                byte[] decoded = Base64.decode(base64, Base64.DEFAULT);
                return BitmapFactory.decodeByteArray(decoded, 0, decoded.length);
            } else if (item.startsWith("/") || item.startsWith("file://")) {
                String path = item.replace("file://", "");
                return BitmapFactory.decodeFile(path);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
