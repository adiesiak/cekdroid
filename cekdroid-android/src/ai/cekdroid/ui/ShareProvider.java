package ai.cekdroid.ui;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;

import java.io.File;

/**
 * Minimal file provider for the shared screenshot.
 *
 * Android has refused `file://` URIs in an Intent since API 24, and this app
 * ships no AndroidX, so `FileProvider` is not available. A provider that serves
 * exactly one directory is all the share sheet needs, and keeping it narrow is
 * the point: nothing outside the app's own cache can be requested.
 */
public class ShareProvider extends ContentProvider {

    public static final String AUTHORITY = "ai.cekdroid.share";
    private static final String SHARE_DIR = "share";

    /** The content:// URI for a file already written into cache/share. */
    public static Uri uriFor(Context ctx, File file) {
        return new Uri.Builder()
                .scheme("content")
                .authority(AUTHORITY)
                .appendPath(file.getName())
                .build();
    }

    /**
     * Resolve a request to a file inside cache/share, or null.
     *
     * The name is reduced to its last path segment and the ".." forms are
     * rejected, so a crafted URI cannot walk out of the share folder.
     */
    private File resolve(Uri uri) {
        if (getContext() == null) {
            return null;
        }
        String name = uri.getLastPathSegment();
        if (name == null || name.isEmpty() || name.contains("..")
                || name.contains("/") || name.contains("\\")) {
            return null;
        }
        File file = new File(new File(getContext().getCacheDir(), SHARE_DIR), name);
        return file.isFile() ? file : null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode)
            throws java.io.FileNotFoundException {
        File file = resolve(uri);
        if (file == null) {
            throw new java.io.FileNotFoundException(String.valueOf(uri));
        }
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        File file = resolve(uri);
        if (file == null) {
            return null;
        }
        String[] columns = {OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE};
        MatrixCursor cursor = new MatrixCursor(columns, 1);
        cursor.addRow(new Object[]{file.getName(), file.length()});
        return cursor;
    }

    @Override
    public String getType(Uri uri) {
        return "image/png";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection,
                      String[] selectionArgs) {
        throw new UnsupportedOperationException("read-only");
    }
}
