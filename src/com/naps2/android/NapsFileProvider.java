package com.naps2.android;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;
import java.io.FileNotFoundException;

public class NapsFileProvider extends ContentProvider {
    public static final String AUTHORITY = "com.naps2.android.fileprovider";

    @Override
    public boolean onCreate() {
        return true;
    }

    public static Uri getUriForFile(android.content.Context context, File file) {
        return Uri.parse("content://" + AUTHORITY + "/" + file.getName());
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String filename = uri.getLastPathSegment();
        if (filename == null) throw new FileNotFoundException("Dosya yolu geçersiz");

        File file = new File(getContext().getCacheDir(), filename);
        if (!file.exists()) {
            file = new File(getContext().getExternalFilesDir(null), filename);
        }
        if (!file.exists()) {
            File downloadDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            file = new File(downloadDir, filename);
        }
        if (!file.exists()) {
            throw new FileNotFoundException("Dosya bulunamadı: " + filename);
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        String filename = uri.getLastPathSegment();
        if (projection == null) {
            projection = new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        }
        MatrixCursor cursor = new MatrixCursor(projection);
        File file = new File(getContext().getCacheDir(), filename);
        if (!file.exists()) {
            file = new File(getContext().getExternalFilesDir(null), filename);
        }
        long size = file.exists() ? file.length() : 0;
        MatrixCursor.RowBuilder row = cursor.newRow();
        for (String col : projection) {
            if (OpenableColumns.DISPLAY_NAME.equals(col)) {
                row.add(filename);
            } else if (OpenableColumns.SIZE.equals(col)) {
                row.add(size);
            } else {
                row.add(null);
            }
        }
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        String path = uri.toString().toLowerCase();
        if (path.endsWith(".pdf")) return "application/pdf";
        if (path.endsWith(".jpg") || path.endsWith(".jpeg")) return "image/jpeg";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".tiff") || path.endsWith(".tif")) return "image/tiff";
        return "application/octet-stream";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }
}
