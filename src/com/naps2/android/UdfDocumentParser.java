package com.naps2.android;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class UdfDocumentParser {

    public static final int PAGE_WIDTH_PX = 1240;  // A4 @ 150 DPI
    public static final int PAGE_HEIGHT_PX = 1754; // A4 @ 150 DPI
    public static final int MARGIN_PX = 90;        // ~15 mm

    public static String extractContentXml(byte[] udfBytes) throws Exception {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(udfBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("content.xml".equalsIgnoreCase(entry.getName())) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = zis.read(buf)) != -1) {
                        baos.write(buf, 0, n);
                    }
                    return new String(baos.toByteArray(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new Exception("UDF arşivi içinde content.xml bulunamadı!");
    }

    public static List<String> extractParagraphs(String xml) {
        List<String> paragraphs = new ArrayList<>();
        // Basit ve hızlı XML paragraf ayıklayıcı
        int pos = 0;
        while ((pos = xml.indexOf("<content>", pos)) != -1) {
            int end = xml.indexOf("</content>", pos);
            if (end == -1) break;
            String text = xml.substring(pos + 9, end);
            text = text.replace("&quot;", "\"")
                       .replace("&apos;", "'")
                       .replace("&lt;", "<")
                       .replace("&gt;", ">")
                       .replace("&amp;", "&");
            if (!text.trim().isEmpty()) {
                paragraphs.add(text.trim());
            }
            pos = end + 10;
        }

        if (paragraphs.isEmpty()) {
            // Yedek: XML etiketlerini temizleyip satırlara böl
            String clean = xml.replaceAll("<[^>]+>", "\n");
            for (String line : clean.split("\n")) {
                line = line.trim();
                if (!line.isEmpty()) {
                    paragraphs.add(line);
                }
            }
        }
        return paragraphs;
    }

    public static List<Bitmap> renderUdfToA4Pages(byte[] udfBytes) throws Exception {
        String xml = extractContentXml(udfBytes);
        List<String> paragraphs = extractParagraphs(xml);

        List<Bitmap> pages = new ArrayList<>();
        int printableW = PAGE_WIDTH_PX - (2 * MARGIN_PX);
        int printableH = PAGE_HEIGHT_PX - (2 * MARGIN_PX);

        TextPaint textPaint = new TextPaint();
        textPaint.setColor(Color.BLACK);
        textPaint.setTextSize(26.0f);
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        textPaint.setAntiAlias(true);

        TextPaint headerPaint = new TextPaint();
        headerPaint.setColor(Color.DKGRAY);
        headerPaint.setTextSize(20.0f);
        headerPaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
        headerPaint.setAntiAlias(true);

        Bitmap currentBmp = Bitmap.createBitmap(PAGE_WIDTH_PX, PAGE_HEIGHT_PX, Bitmap.Config.ARGB_8888);
        Canvas currentCanvas = new Canvas(currentBmp);
        currentCanvas.drawColor(Color.WHITE);

        int currentY = MARGIN_PX;
        int pageNumber = 1;

        // Başlık çiz
        currentCanvas.drawText("UYAP RESMİ BELGESİ", MARGIN_PX, MARGIN_PX - 30, headerPaint);
        currentCanvas.drawLine(MARGIN_PX, MARGIN_PX - 20, PAGE_WIDTH_PX - MARGIN_PX, MARGIN_PX - 20, headerPaint);

        for (String para : paragraphs) {
            Layout.Alignment align = Layout.Alignment.ALIGN_NORMAL;
            if (para.contains("MAHKEMESİ") || para.contains("CUMHURİYET BAŞSAVCILIĞI") || para.startsWith("-")) {
                align = Layout.Alignment.ALIGN_CENTER;
            }

            StaticLayout layout = new StaticLayout(
                    para,
                    textPaint,
                    printableW,
                    align,
                    1.2f,
                    0.0f,
                    false
            );

            int layoutH = layout.getHeight() + 20;

            // Sayfa sonuna geldiyse yeni sayfa aç
            if (currentY + layoutH > PAGE_HEIGHT_PX - MARGIN_PX) {
                // Sayfa numarası çiz
                currentCanvas.drawText("Sayfa " + pageNumber, PAGE_WIDTH_PX / 2.0f - 40, PAGE_HEIGHT_PX - 40, headerPaint);
                pages.add(currentBmp);

                pageNumber++;
                currentBmp = Bitmap.createBitmap(PAGE_WIDTH_PX, PAGE_HEIGHT_PX, Bitmap.Config.ARGB_8888);
                currentCanvas = new Canvas(currentBmp);
                currentCanvas.drawColor(Color.WHITE);
                currentY = MARGIN_PX;

                currentCanvas.drawText("UYAP RESMİ BELGESİ", MARGIN_PX, MARGIN_PX - 30, headerPaint);
                currentCanvas.drawLine(MARGIN_PX, MARGIN_PX - 20, PAGE_WIDTH_PX - MARGIN_PX, MARGIN_PX - 20, headerPaint);
            }

            currentCanvas.save();
            currentCanvas.translate(MARGIN_PX, currentY);
            layout.draw(currentCanvas);
            currentCanvas.restore();

            currentY += layoutH;
        }

        // Son sayfa numarasını çiz ve ekle
        currentCanvas.drawText("Sayfa " + pageNumber, PAGE_WIDTH_PX / 2.0f - 40, PAGE_HEIGHT_PX - 40, headerPaint);
        pages.add(currentBmp);

        return pages;
    }
}
