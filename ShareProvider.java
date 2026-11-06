package app.nettoyage;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

/** Fournit en lecture seule les fichiers choisis aux applications de partage. */
public class ShareProvider extends ContentProvider {
    static final String AUTH = "app.nettoyage.share";

    static Uri uriFor(String path) {
        return new Uri.Builder().scheme("content").authority(AUTH).appendQueryParameter("p", path).build();
    }

    static String mime(String name) {
        String m = MimeTypeMap.getSingleton().getMimeTypeFromExtension(Scan.ext(name));
        return m != null ? m : "application/octet-stream";
    }

    private File fileOf(Uri u) {
        String p = u.getQueryParameter("p");
        if (p == null) return null;
        File f = new File(p);
        return f.isFile() ? f : null;
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri u, String[] proj, String sel, String[] args, String sort) {
        File f = fileOf(u);
        if (f == null) return null;
        MatrixCursor c = new MatrixCursor(new String[]{OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE});
        c.addRow(new Object[]{f.getName(), f.length()});
        return c;
    }

    @Override
    public String getType(Uri u) {
        File f = fileOf(u);
        return f == null ? null : mime(f.getName());
    }

    @Override
    public ParcelFileDescriptor openFile(Uri u, String mode) throws FileNotFoundException {
        File f = fileOf(u);
        if (f == null) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override
    public Uri insert(Uri u, ContentValues v) {
        return null;
    }

    @Override
    public int delete(Uri u, String s, String[] a) {
        return 0;
    }

    @Override
    public int update(Uri u, ContentValues v, String s, String[] a) {
        return 0;
    }
}
