package com.naps2.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.pdf.PdfDocument;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class NapsPdfEngine {

    // 72 DPI Point Ölçüleri
    public static final int A4_WIDTH = 595;
    public static final int A4_HEIGHT = 842;
    public static final int LETTER_WIDTH = 612;
    public static final int LETTER_HEIGHT = 792;
    public static final float DEFAULT_MARGIN = 14.17f; // ~5 mm

    public static class PdfOptions {
        public String paperSize = "A4"; // A4, LETTER, FIT
        public String orientation = "AUTO"; // AUTO, PORTRAIT, LANDSCAPE
        public float margin = DEFAULT_MARGIN;
        public int jpegQuality = 90; // 0-100
        public String title = "NAPS2 Tarama";
        public String author = "NAPS2 Android";
    }

    public static File createPdfFromBitmaps(List<Bitmap> pages, File outFile, PdfOptions options) throws IOException {
        if (pages == null || pages.isEmpty()) {
            throw new IllegalArgumentException("Dönüştürülecek sayfa bulunamadı!");
        }

        if (options == null) options = new PdfOptions();

        PdfDocument document = new PdfDocument();
        Paint paint = new Paint();
        paint.setAntiAlias(true);
        paint.setFilterBitmap(true);
        paint.setDither(true);

        try {
            for (int i = 0; i < pages.size(); i++) {
                Bitmap bmp = pages.get(i);
                if (bmp == null || bmp.isRecycled()) continue;

                int bmpW = bmp.getWidth();
                int bmpH = bmp.getHeight();

                int targetPageW = A4_WIDTH;
                int targetPageH = A4_HEIGHT;

                if ("LETTER".equalsIgnoreCase(options.paperSize)) {
                    targetPageW = LETTER_WIDTH;
                    targetPageH = LETTER_HEIGHT;
                }

                // NAPS2 Yönlendirme Kuralı
                if ("AUTO".equalsIgnoreCase(options.orientation)) {
                    if (bmpW > bmpH) {
                        int tmp = targetPageW;
                        targetPageW = targetPageH;
                        targetPageH = tmp;
                    }
                } else if ("LANDSCAPE".equalsIgnoreCase(options.orientation)) {
                    if (targetPageW < targetPageH) {
                        int tmp = targetPageW;
                        targetPageW = targetPageH;
                        targetPageH = tmp;
                    }
                }

                if ("FIT".equalsIgnoreCase(options.paperSize)) {
                    targetPageW = bmpW;
                    targetPageH = bmpH;
                }

                PdfDocument.PageInfo pageInfo = new PdfDocument.PageInfo.Builder(targetPageW, targetPageH, i + 1).create();
                PdfDocument.Page page = document.startPage(pageInfo);
                Canvas canvas = page.getCanvas();

                if ("FIT".equalsIgnoreCase(options.paperSize)) {
                    canvas.drawBitmap(bmp, 0, 0, paint);
                } else {
                    float availW = targetPageW - (2 * options.margin);
                    float availH = targetPageH - (2 * options.margin);

                    float scale = Math.min(availW / bmpW, availH / bmpH);
                    float drawW = bmpW * scale;
                    float drawH = bmpH * scale;

                    float posX = options.margin + (availW - drawW) / 2.0f;
                    float posY = options.margin + (availH - drawH) / 2.0f;

                    Rect srcRect = new Rect(0, 0, bmpW, bmpH);
                    RectF dstRect = new RectF(posX, posY, posX + drawW, posY + drawH);

                    canvas.drawBitmap(bmp, srcRect, dstRect, paint);
                }

                document.finishPage(page);
            }

            File parent = outFile.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }

            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                document.writeTo(fos);
            }

            return outFile;
        } finally {
            document.close();
        }
    }
}
