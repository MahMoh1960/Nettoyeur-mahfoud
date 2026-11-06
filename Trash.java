package app.nettoyage;

import android.content.Context;
import android.os.Environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Corbeille interne et historique des opérations. */
class Trash {
    static class Entry {
        String name, orig;
        long size, when;
    }

    final File dir;
    private final File index;
    private final File hist;
    private int seq = 0;

    Trash(Context c) {
        File base = c.getExternalFilesDir(null);
        File d = base != null ? new File(base, "trash") : null;
        if (d == null || (!d.isDirectory() && !d.mkdirs())) d = new File(c.getFilesDir(), "trash");
        d.mkdirs();
        dir = d;
        index = new File(dir, "index.txt");
        hist = new File(c.getFilesDir(), "history.txt");
    }

    static boolean copy(File a, File b) {
        try (InputStream in = new FileInputStream(a); OutputStream out = new FileOutputStream(b)) {
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (IOException e) {
            b.delete();
            return false;
        }
    }

    private static boolean moveFile(File src, File dest) {
        if (src.renameTo(dest)) return true;
        if (copy(src, dest)) {
            if (src.delete()) return true;
            dest.delete();
        }
        return false;
    }

    private static List<String> readLines(File f) {
        ArrayList<String> r = new ArrayList<>();
        if (!f.exists()) return r;
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(f), "UTF-8"))) {
            String l;
            while ((l = br.readLine()) != null) if (!l.isEmpty()) r.add(l);
        } catch (IOException e) {
            // ignoré
        }
        return r;
    }

    private static void writeLines(File f, List<String> lines, boolean append) {
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f, append), "UTF-8")) {
            for (String l : lines) w.write(l + "\n");
        } catch (IOException e) {
            // ignoré
        }
    }

    private static String clean(String s) {
        return s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    synchronized ArrayList<Entry> list() {
        ArrayList<Entry> r = new ArrayList<>();
        for (String l : readLines(index)) {
            String[] p = l.split("\t", -1);
            if (p.length < 4) continue;
            Entry e = new Entry();
            e.name = p[0];
            e.orig = p[1];
            try {
                e.size = Long.parseLong(p[2]);
                e.when = Long.parseLong(p[3]);
            } catch (NumberFormatException ex) {
                continue;
            }
            if (new File(dir, e.name).exists()) r.add(e);
        }
        return r;
    }

    private void rewrite(List<Entry> keep) {
        ArrayList<String> ls = new ArrayList<>();
        for (Entry e : keep) ls.add(e.name + "\t" + clean(e.orig) + "\t" + e.size + "\t" + e.when);
        writeLines(index, ls, false);
    }

    synchronized boolean move(File f) {
        if (!f.exists()) return true;
        String name = System.currentTimeMillis() + "_" + (seq++) + "_" + clean(f.getName());
        File dest = new File(dir, name);
        long size = f.length();
        if (!moveFile(f, dest)) return false;
        ArrayList<String> one = new ArrayList<>();
        one.add(name + "\t" + clean(f.getAbsolutePath()) + "\t" + size + "\t" + System.currentTimeMillis());
        writeLines(index, one, true);
        return true;
    }

    /** Copie le fichier dans la corbeille sans le déplacer (utilisé avant compression). */
    synchronized boolean copyIn(File f) {
        if (!f.exists()) return false;
        String name = System.currentTimeMillis() + "_" + (seq++) + "_" + clean(f.getName());
        File dest = new File(dir, name);
        long size = f.length();
        if (!copy(f, dest)) return false;
        ArrayList<String> one = new ArrayList<>();
        one.add(name + "\t" + clean(f.getAbsolutePath()) + "\t" + size + "\t" + System.currentTimeMillis());
        writeLines(index, one, true);
        return true;
    }

    /** Crée une archive ZIP des fichiers donnés dans Téléchargements/Nettoyage-sauvegardes. */
    static File backupZip(List<String> paths) {
        File out = null;
        try {
            File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), Scan.BACKUP_DIR_NAME);
            if (!d.isDirectory() && !d.mkdirs()) return null;
            out = new File(d, "sauvegarde_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".zip");
            String root = Environment.getExternalStorageDirectory().getAbsolutePath() + "/";
            try (ZipOutputStream z = new ZipOutputStream(new FileOutputStream(out))) {
                byte[] buf = new byte[65536];
                for (String p : paths) {
                    File f = new File(p);
                    if (!f.isFile()) continue;
                    String name = p.startsWith(root) ? p.substring(root.length()) : p.substring(1);
                    ZipEntry e = new ZipEntry(name);
                    e.setTime(f.lastModified());
                    z.putNextEntry(e);
                    try (InputStream in = new FileInputStream(f)) {
                        int n;
                        while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
                    }
                    z.closeEntry();
                }
            }
            return out;
        } catch (Exception e) {
            if (out != null) out.delete();
            return null;
        }
    }

    synchronized boolean restore(Entry e) {
        File src = new File(dir, e.name);
        File dest = new File(e.orig);
        File parent = dest.getParentFile();
        if (parent != null) parent.mkdirs();
        if (dest.exists()) {
            String n = dest.getName();
            int i = n.lastIndexOf('.');
            String nn = i > 0 ? n.substring(0, i) + "_restauré" + n.substring(i) : n + "_restauré";
            dest = new File(parent, nn);
        }
        if (!moveFile(src, dest)) return false;
        removeEntries(e.name);
        return true;
    }

    synchronized long delete(Entry e) {
        File f = new File(dir, e.name);
        long s = f.length();
        f.delete();
        removeEntries(e.name);
        return s;
    }

    private void removeEntries(String... names) {
        Set<String> set = new HashSet<>();
        for (String n : names) set.add(n);
        ArrayList<Entry> keep = new ArrayList<>();
        for (Entry e : list()) if (!set.contains(e.name)) keep.add(e);
        rewrite(keep);
    }

    synchronized long purgeOlder(int days) {
        long cut = System.currentTimeMillis() - days * 86400000L;
        long freed = 0;
        ArrayList<Entry> keep = new ArrayList<>();
        for (Entry e : list()) {
            if (e.when < cut) {
                File f = new File(dir, e.name);
                freed += f.length();
                f.delete();
            } else keep.add(e);
        }
        rewrite(keep);
        if (freed > 0) log("f_trash_empty", 1, freed, "D");
        return freed;
    }

    synchronized long totalSize() {
        long s = 0;
        for (Entry e : list()) s += e.size;
        return s;
    }

    synchronized void log(String key, int count, long bytes, String mode) {
        ArrayList<String> one = new ArrayList<>();
        one.add(System.currentTimeMillis() + "\t" + key + "\t" + count + "\t" + bytes + "\t" + mode);
        writeLines(hist, one, true);
    }

    /** Lignes : {date, clé, nombre, octets, mode}, la plus récente d'abord. */
    synchronized ArrayList<String[]> history() {
        ArrayList<String[]> r = new ArrayList<>();
        List<String> ls = readLines(hist);
        for (int i = ls.size() - 1; i >= 0; i--) {
            String[] p = ls.get(i).split("\t", -1);
            if (p.length >= 5) r.add(p);
        }
        return r;
    }
}
