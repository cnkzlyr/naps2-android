package com.naps2.android;

import android.Manifest;
import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.View;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;

public class MainActivity extends Activity {

    public static final int REQ_CAMERA = 101;
    public static final int REQ_FILE_PICKER = 102;
    public static final int REQ_PERMISSIONS = 201;

    private WebView webView;
    private ValueCallback<Uri[]> fileUploadCallback;
    private NapsNativeBridge nativeBridge;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Tam ekran WebView oluştur
        webView = new WebView(this);
        setContentView(webView);

        initWebView();
        checkAndRequestPermissions();
        handleIncomingIntent(getIntent());
    }

    private void initWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setDatabaseEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setSupportZoom(false);

        nativeBridge = new NapsNativeBridge(this);
        webView.addJavascriptInterface(nativeBridge, "NapsNative");

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                handlePendingFileLoads();
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (fileUploadCallback != null) {
                    fileUploadCallback.onReceiveValue(null);
                }
                fileUploadCallback = filePathCallback;

                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
                startActivityForResult(Intent.createChooser(intent, "Belge veya Görsel Seçin"), REQ_FILE_PICKER);
                return true;
            }
        });

        webView.loadUrl("file:///android_asset/www/index.html");
    }

    private void checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ArrayList<String> perms = new ArrayList<>();
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.CAMERA);
            }
            if (Build.VERSION.SDK_INT <= 32 && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.READ_EXTERNAL_STORAGE);
                perms.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);
            }
            if (!perms.isEmpty()) {
                requestPermissions(perms.toArray(new String[0]), REQ_PERMISSIONS);
            }
        }
    }

    public void launchNativeCamera() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQ_CAMERA);
        } else {
            Toast.makeText(this, "Kamera uygulaması bulunamadı", Toast.LENGTH_SHORT).show();
        }
    }

    public void launchNativeFileChooser(String mimeTypes) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType((mimeTypes != null && !mimeTypes.isEmpty()) ? mimeTypes : "*/*");
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        startActivityForResult(Intent.createChooser(intent, "Dosya Seç"), REQ_FILE_PICKER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQ_CAMERA && resultCode == RESULT_OK && data != null) {
            Bundle extras = data.getExtras();
            if (extras != null && extras.get("data") instanceof Bitmap) {
                Bitmap bmp = (Bitmap) extras.get("data");
                sendBitmapToWeb(bmp);
            }
        } else if (requestCode == REQ_FILE_PICKER) {
            if (fileUploadCallback != null) {
                Uri[] results = null;
                if (resultCode == RESULT_OK && data != null) {
                    if (data.getClipData() != null) {
                        ClipData clip = data.getClipData();
                        results = new Uri[clip.getItemCount()];
                        for (int i = 0; i < clip.getItemCount(); i++) {
                            results[i] = clip.getItemAt(i).getUri();
                        }
                    } else if (data.getData() != null) {
                        results = new Uri[]{data.getData()};
                    }
                }
                fileUploadCallback.onReceiveValue(results);
                fileUploadCallback = null;
            } else if (resultCode == RESULT_OK && data != null) {
                handleUriData(data);
            }
        }
    }

    private void handleUriData(Intent data) {
        new Thread(() -> {
            try {
                if (data.getClipData() != null) {
                    ClipData clip = data.getClipData();
                    for (int i = 0; i < clip.getItemCount(); i++) {
                        processUri(clip.getItemAt(i).getUri());
                    }
                } else if (data.getData() != null) {
                    processUri(data.getData());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void processUri(Uri uri) {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            String path = uri.getPath();
            if (path != null && path.toLowerCase().endsWith(".udf")) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                nativeBridge.parseUdf(b64);
            } else {
                Bitmap bmp = BitmapFactory.decodeStream(in);
                if (bmp != null) {
                    sendBitmapToWeb(bmp);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sendBitmapToWeb(Bitmap bmp) {
        new Thread(() -> {
            try {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 92, baos);
                String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
                sendWebEvent("onPageImported", "data:image/jpeg;base64," + b64);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void sendWebEvent(final String funcName, final String jsonArg) {
        runOnUiThread(() -> {
            String script = "if (window." + funcName + ") { window." + funcName + "(" + JSONObjectEscape(jsonArg) + "); }";
            webView.evaluateJavascript(script, null);
        });
    }

    private String JSONObjectEscape(String s) {
        if (s == null) return "null";
        if (s.startsWith("{") || s.startsWith("[")) return s;
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "") + "\"";
    }

    private Uri pendingIntentUri = null;

    private void handleIncomingIntent(Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action) || Intent.ACTION_SEND.equals(action)) {
            Uri uri = intent.getData();
            if (uri == null && intent.hasExtra(Intent.EXTRA_STREAM)) {
                uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            }
            if (uri != null) {
                pendingIntentUri = uri;
            }
        }
    }

    private void handlePendingFileLoads() {
        if (pendingIntentUri != null) {
            new Thread(() -> {
                processUri(pendingIntentUri);
                pendingIntentUri = null;
            }).start();
        }
    }

    @Override
    public void onBackPressed() {
        // WebView içinde modal açık mı kontrolü için JS çağrısı
        webView.evaluateJavascript("if (window.handleAndroidBack) { window.handleAndroidBack(); } else { false; }", value -> {
            if ("\"handled\"".equals(value) || "true".equals(value)) {
                // Modal kapatıldı
            } else {
                super.onBackPressed();
            }
        });
    }
}
