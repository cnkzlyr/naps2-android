package com.naps2.android;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class EsclScannerClient {

    public static class ScanRequest {
        public String ip;
        public int port = 8080; // Varsayılan eSCL portu (veya 80)
        public int resolution = 300; // 150, 300, 600
        public String colorMode = "RGB24"; // RGB24, Grayscale8, BlackAndWhite1
        public String inputSource = "Platen"; // Platen (Cam), Feeder (ADF)
    }

    public static String getCapabilities(String ip, int port) throws Exception {
        URL url = new URL("http://" + ip + ":" + port + "/eSCL/ScannerCapabilities");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(4000);
        conn.setReadTimeout(6000);
        conn.setRequestMethod("GET");

        if (conn.getResponseCode() != 200) {
            throw new Exception("Tarayıcıya ulaşılamadı. HTTP: " + conn.getResponseCode());
        }

        try (InputStream in = conn.getInputStream(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) {
                baos.write(buf, 0, n);
            }
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    public static Bitmap scanDocument(ScanRequest req) throws Exception {
        URL jobUrl = new URL("http://" + req.ip + ":" + req.port + "/eSCL/ScanJobs");
        HttpURLConnection conn = (HttpURLConnection) jobUrl.openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(60000); // Tarama süresi için geniş zaman
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "text/xml");

        String xmlPayload = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<scan:ScanSettings xmlns:scan=\"http://schemas.hp.com/imaging/escl/2011/05/03\" xmlns:pwg=\"http://www.pwg.org/schemas/2010/12/sm\">\n" +
                "  <pwg:Version>2.0</pwg:Version>\n" +
                "  <scan:Intent>Document</scan:Intent>\n" +
                "  <scan:DocumentFormat>image/jpeg</scan:DocumentFormat>\n" +
                "  <scan:XResolution>" + req.resolution + "</scan:XResolution>\n" +
                "  <scan:YResolution>" + req.resolution + "</scan:YResolution>\n" +
                "  <scan:ColorMode>" + req.colorMode + "</scan:ColorMode>\n" +
                "  <scan:InputSource>" + req.inputSource + "</scan:InputSource>\n" +
                "</scan:ScanSettings>";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(xmlPayload.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code != 201 && code != 200) {
            throw new Exception("Tarama işi başlatılamadı. HTTP Kod: " + code);
        }

        String location = conn.getHeaderField("Location");
        if (location == null || location.isEmpty()) {
            location = "http://" + req.ip + ":" + req.port + "/eSCL/ScanJobs/NextDocument";
        } else if (!location.endsWith("NextDocument")) {
            location = location + "/NextDocument";
        }

        // Taranan belgeyi indir
        URL docUrl = new URL(location);
        HttpURLConnection docConn = (HttpURLConnection) docUrl.openConnection();
        docConn.setConnectTimeout(10000);
        docConn.setReadTimeout(90000);
        docConn.setRequestMethod("GET");

        if (docConn.getResponseCode() != 200) {
            throw new Exception("Taranan görsel alınamadı. HTTP: " + docConn.getResponseCode());
        }

        try (InputStream in = docConn.getInputStream()) {
            Bitmap bmp = BitmapFactory.decodeStream(in);
            if (bmp == null) {
                throw new Exception("Taranan görsel çözümlenemedi!");
            }
            return bmp;
        }
    }
}
