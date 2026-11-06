package app.nettoyage;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Analyse du stockage : catégories, gros fichiers, doublons, dossiers, etc. */
class Scan {
    static class Item {
        String path;
        long size, mtime;
        boolean sel, hdr, dir;
        int gcount, gnum;
        long gsize;
        String hk = "group_hdr";
        long score;
        double q;
        String orig;
        Object ref;

        Item(String p, long s, long m) {
            path = p;
            size = s;
            mtime = m;
        }

        String name() {
            String s = orig != null ? orig : path;
            int i = s.lastIndexOf('/');
            return i >= 0 ? s.substring(i + 1) : s;
        }
    }

    /** Groupes d'éléments (doublons, photos similaires) avec liste à plat pour l'affichage. */
    static class Groups {
        ArrayList<ArrayList<Item>> groups = new ArrayList<>();
        final ArrayList<Item> flat = new ArrayList<>();
        final IdentityHashMap<Item, ArrayList<Item>> of = new IdentityHashMap<>();
        boolean ready;
        String hdrKey = "group_hdr";
        boolean sumSizes;

        void flatten() {
            flat.clear();
            of.clear();
            int gi = 0;
            for (ArrayList<Item> g : groups) {
                Item h = new Item("", 0, 0);
                h.hdr = true;
                h.hk = hdrKey;
                h.gcount = g.size();
                h.gsize = sumSizes ? sum(g) : g.get(0).size;
                h.gnum = ++gi;
                flat.add(h);
                for (Item it : g) {
                    flat.add(it);
                    of.put(it, g);
                }
            }
        }

        void defaults() {
            for (ArrayList<Item> g : groups) for (int i = 0; i < g.size(); i++) g.get(i).sel = i > 0;
        }

        long freeable() {
            long s = 0;
            for (ArrayList<Item> g : groups) s += sum(g) - g.get(0).size;
            return s;
        }

        void remove(HashSet<String> s) {
            for (ArrayList<Item> g : groups) g.removeIf(i -> s.contains(i.path));
            groups.removeIf(g -> g.size() < 2);
            flatten();
        }
    }

    static final long LARGE = 10L * 1024 * 1024;
    static final long DIR_MIN = 1024L * 1024;
    static final long BIG_PHOTO = 1572864L;
    static final long DAY30 = 30L * 86400000L;
    static final String BACKUP_DIR_NAME = "Nettoyage-sauvegardes";
    static final HashMap<String, Integer> EXT = new HashMap<>();
    static final HashSet<String> TMP = new HashSet<>();
    static volatile java.util.Set<String> EXCL = new HashSet<>();

    static {
        String[][] g = {
                {"jpg", "jpeg", "png", "gif", "webp", "bmp", "heic", "heif"},
                {"mp4", "mkv", "avi", "mov", "3gp", "webm", "flv", "wmv", "m4v"},
                {"mp3", "m4a", "aac", "wav", "ogg", "opus", "flac", "amr", "wma"},
                {"pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "odt", "rtf", "csv", "epub"},
                {"apk", "xapk", "apks"},
                {"zip", "rar", "7z", "tar", "gz"}
        };
        for (int i = 0; i < g.length; i++) for (String e : g[i]) EXT.put(e, i);
        for (String e : new String[]{"tmp", "temp", "log", "bak", "old", "chk", "dmp", "crdownload", "part"}) TMP.add(e);
    }

    static String ext(String name) {
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return "";
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    static int cat(String name) {
        Integer c = EXT.get(ext(name));
        return c == null ? 6 : c;
    }

    static boolean isExcluded(String p) {
        for (String e : EXCL) if (p.equals(e) || p.startsWith(e + "/")) return true;
        return false;
    }

    volatile boolean cancel;
    volatile long nFiles, nBytes;
    volatile String curDir = "";
    volatile int dupDone, dupTotal, photoTotal;
    final AtomicInteger photoDone = new AtomicInteger();
    boolean scanned, photosReady;
    long now;

    long[] catBytes = new long[7];
    int[] catCount = new int[7];
    final ArrayList<Item> large = new ArrayList<>(), oldDl = new ArrayList<>(), shots = new ArrayList<>(),
            apks = new ArrayList<>(), msg = new ArrayList<>(), temp = new ArrayList<>(),
            zero = new ArrayList<>(), emptyDirs = new ArrayList<>(), empties = new ArrayList<>(),
            bigPhotos = new ArrayList<>(), images = new ArrayList<>(), blurry = new ArrayList<>(),
            dirs = new ArrayList<>();
    final HashMap<Long, ArrayList<Item>> bySize = new HashMap<>();
    final HashMap<String, ArrayList<Item>> childrenOf = new HashMap<>();
    final HashMap<String, Long> dirBytes = new HashMap<>();
    final Groups dupG = new Groups();
    final Groups simG = new Groups();

    Scan() {
        simG.hdrKey = "sim_hdr";
        simG.sumSizes = true;
    }

    ArrayList<ArrayList<Item>> lists() {
        ArrayList<ArrayList<Item>> r = new ArrayList<>();
        r.add(large); r.add(oldDl); r.add(shots); r.add(apks); r.add(msg); r.add(temp); r.add(zero);
        r.add(emptyDirs); r.add(empties); r.add(bigPhotos); r.add(images); r.add(blurry);
        return r;
    }

    static long sum(List<Item> l) {
        long s = 0;
        for (Item i : l) s += i.size;
        return s;
    }

    void scan(File root) {
        cancel = false;
        scanned = false;
        photosReady = false;
        dupG.ready = false;
        simG.ready = false;
        nFiles = 0;
        nBytes = 0;
        catBytes = new long[7];
        catCount = new int[7];
        for (ArrayList<Item> l : lists()) l.clear();
        dirs.clear();
        bySize.clear();
        dupG.groups = new ArrayList<>();
        dupG.flatten();
        simG.groups = new ArrayList<>();
        simG.flatten();
        now = System.currentTimeMillis();
        walk(root, 0);
        if (cancel) return;
        for (Item i : zero) empties.add(i);
        for (Item i : emptyDirs) {
            i.dir = true;
            empties.add(i);
        }
        java.util.Comparator<Item> bySz = (a, b) -> Long.compare(b.size, a.size);
        Collections.sort(large, bySz);
        while (large.size() > 1000) large.remove(large.size() - 1);
        Collections.sort(oldDl, bySz);
        Collections.sort(shots, bySz);
        Collections.sort(apks, bySz);
        Collections.sort(msg, bySz);
        Collections.sort(temp, bySz);
        Collections.sort(bigPhotos, bySz);
        Collections.sort(empties, (a, b) -> a.path.compareTo(b.path));
        Iterator<Map.Entry<Long, ArrayList<Item>>> it = bySize.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue().size() < 2) it.remove();
        buildTree();
        scanned = true;
    }

    private Item dirItem(String p, long t, long m) {
        Item d = new Item(p, t, m);
        d.dir = true;
        return d;
    }

    private void buildTree() {
        childrenOf.clear();
        dirBytes.clear();
        for (Item d : dirs) {
            dirBytes.put(d.path, d.size);
            int i = d.path.lastIndexOf('/');
            String par = i > 0 ? d.path.substring(0, i) : "/";
            ArrayList<Item> l = childrenOf.get(par);
            if (l == null) {
                l = new ArrayList<>();
                childrenOf.put(par, l);
            }
            l.add(d);
        }
        for (ArrayList<Item> l : childrenOf.values()) Collections.sort(l, (a, b) -> Long.compare(b.size, a.size));
    }

    private long walk(File d, int depth) {
        if (cancel) return 0;
        String dp = d.getAbsolutePath();
        curDir = dp;
        File[] ch = d.listFiles();
        if (ch == null) return 0;
        if (ch.length == 0) {
            if (depth >= 2 && !dp.contains("/Android/")) emptyDirs.add(new Item(dp, 0, d.lastModified()));
            return 0;
        }
        long total = 0;
        for (File f : ch) {
            if (cancel) return total;
            String n = f.getName();
            if (f.isDirectory()) {
                if (depth == 0 && n.equals("Android")) {
                    File m = new File(f, "media");
                    if (isExcluded(m.getAbsolutePath())) continue;
                    long t = walk(m, 3);
                    if (t >= DIR_MIN) {
                        dirs.add(dirItem(m.getAbsolutePath(), t, m.lastModified()));
                        dirs.add(dirItem(f.getAbsolutePath(), t, f.lastModified()));
                    }
                    total += t;
                    continue;
                }
                if (depth >= 30 || n.equals(BACKUP_DIR_NAME)) continue;
                String fp = f.getAbsolutePath();
                if (isExcluded(fp)) continue;
                try {
                    if (!f.getCanonicalPath().equals(fp)) continue;
                } catch (IOException e) {
                    continue;
                }
                long t = walk(f, depth + 1);
                total += t;
                if (t >= DIR_MIN) dirs.add(dirItem(fp, t, f.lastModified()));
            } else {
                total += handle(f, n);
            }
        }
        return total;
    }

    private long handle(File f, String n) {
        long len = f.length();
        long mt = f.lastModified();
        String p = f.getAbsolutePath();
        if (len == 0) {
            if (!n.startsWith(".")) zero.add(new Item(p, 0, mt));
            return 0;
        }
        Item it = new Item(p, len, mt);
        nFiles++;
        nBytes += len;
        int c = cat(n);
        catBytes[c] += len;
        catCount[c]++;
        String lp = p.toLowerCase(Locale.ROOT);
        String ex = ext(n);
        if (len >= LARGE) large.add(it);
        if (len >= 51200) {
            ArrayList<Item> g = bySize.get(len);
            if (g == null) {
                g = new ArrayList<>();
                bySize.put(len, g);
            }
            g.add(it);
        }
        if (c == 4) apks.add(it);
        long age = now - mt;
        if (age > DAY30 && (lp.contains("/download/") || lp.contains("/downloads/"))) oldDl.add(it);
        boolean shot = c == 0 && (lp.contains("/screenshots/") || lp.contains("/screenshot/")
                || n.toLowerCase(Locale.ROOT).startsWith("screenshot"));
        if (shot) shots.add(it);
        if (c == 0 && !shot && !ex.equals("gif")) {
            if (len >= 51200) images.add(it);
            if (len >= BIG_PHOTO && (ex.equals("jpg") || ex.equals("jpeg"))) bigPhotos.add(it);
        }
        if (age > DAY30 && (lp.contains("/whatsapp/media/") || lp.contains("/whatsapp business/media/")
                || lp.contains("/telegram/")) && !lp.contains("/backups/") && !lp.contains("/databases/")) msg.add(it);
        if (TMP.contains(ex) || lp.contains("/.thumbnails/") || n.startsWith(".thumbdata")) temp.add(it);
        return len;
    }

    // ---------- doublons ----------

    static String hash(String path, long limit) {
        try (FileInputStream in = new FileInputStream(path)) {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] buf = new byte[65536];
            long left = limit > 0 ? limit : Long.MAX_VALUE;
            int n;
            while (left > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, left))) > 0) {
                md.update(buf, 0, n);
                left -= n;
            }
            StringBuilder s = new StringBuilder();
            for (byte b : md.digest()) s.append(String.format("%02x", b));
            return s.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static void put(HashMap<String, ArrayList<Item>> m, String k, Item it) {
        ArrayList<Item> l = m.get(k);
        if (l == null) {
            l = new ArrayList<>();
            m.put(k, l);
        }
        l.add(it);
    }

    void findDups() {
        cancel = false;
        dupG.ready = false;
        dupDone = 0;
        int tot = 0;
        for (ArrayList<Item> g : bySize.values()) tot += g.size();
        dupTotal = tot;
        ArrayList<ArrayList<Item>> res = new ArrayList<>();
        for (ArrayList<Item> g : new ArrayList<>(bySize.values())) {
            HashMap<String, ArrayList<Item>> q = new HashMap<>();
            for (Item it : g) {
                dupDone++;
                if (cancel) return;
                if (!new File(it.path).exists()) continue;
                String h = hash(it.path, 65536);
                if (h != null) put(q, h, it);
            }
            for (ArrayList<Item> g2 : q.values()) {
                if (g2.size() < 2) continue;
                if (g2.get(0).size <= 65536) {
                    res.add(new ArrayList<>(g2));
                    continue;
                }
                HashMap<String, ArrayList<Item>> full = new HashMap<>();
                for (Item it : g2) {
                    if (cancel) return;
                    String h2 = hash(it.path, 0);
                    if (h2 != null) put(full, h2, it);
                }
                for (ArrayList<Item> g3 : full.values()) if (g3.size() >= 2) res.add(g3);
            }
        }
        for (ArrayList<Item> g : res) Collections.sort(g, (a, b) -> Long.compare(a.mtime, b.mtime));
        Collections.sort(res, (a, b) -> Long.compare(b.get(0).size * (b.size() - 1), a.get(0).size * (a.size() - 1)));
        dupG.groups = res;
        dupG.flatten();
        dupG.defaults();
        dupG.ready = true;
    }

    // ---------- nettoyage rapide ----------

    static boolean apkInstalled(PackageManager pm, String path) {
        try {
            PackageInfo pi = pm.getPackageArchiveInfo(path, 0);
            if (pi == null) return false;
            PackageInfo ins = pm.getPackageInfo(pi.packageName, 0);
            return ins.versionCode >= pi.versionCode;
        } catch (Exception e) {
            return false;
        }
    }

    /** Éléments jugés sûrs : temporaires, vides, APK déjà installés, captures de plus de 30 jours. */
    LinkedHashMap<String, ArrayList<Item>> quickSet(PackageManager pm) {
        LinkedHashMap<String, ArrayList<Item>> r = new LinkedHashMap<>();
        ArrayList<Item> t = new ArrayList<>(temp);
        ArrayList<Item> e = new ArrayList<>(empties);
        ArrayList<Item> a = new ArrayList<>();
        for (Item i : apks) if (apkInstalled(pm, i.path)) a.add(i);
        ArrayList<Item> s = new ArrayList<>();
        for (Item i : shots) if (now - i.mtime > DAY30) s.add(i);
        if (!t.isEmpty()) r.put("f_temp", t);
        if (!e.isEmpty()) r.put("f_empty", e);
        if (!a.isEmpty()) r.put("f_apk", a);
        if (!s.isEmpty()) r.put("f_shots", s);
        return r;
    }

    // ---------- mises à jour après opérations ----------

    /** Retire les éléments traités de toutes les listes et met à jour les statistiques. */
    void removed(List<Item> done) {
        HashSet<String> s = new HashSet<>();
        for (Item it : done) {
            s.add(it.path);
            if (it.size > 0 && !it.dir) {
                int c = cat(it.name());
                catBytes[c] -= it.size;
                catCount[c]--;
            }
        }
        for (ArrayList<Item> l : lists()) l.removeIf(i -> s.contains(i.path));
        for (ArrayList<Item> g : bySize.values()) g.removeIf(i -> s.contains(i.path));
        dupG.remove(s);
        simG.remove(s);
    }

    /** Un fichier a changé de taille (compression). */
    void resized(Item it, long newSize) {
        int c = cat(it.name());
        catBytes[c] -= it.size - newSize;
        it.size = newSize;
    }

    // ---------- utilitaires ----------

    static long dirSize(File f) {
        if (f == null || !f.exists()) return 0;
        if (f.isFile()) return f.length();
        long s = 0;
        File[] c = f.listFiles();
        if (c != null) for (File x : c) s += dirSize(x);
        return s;
    }

    static long deleteContents(File d) {
        long freed = 0;
        if (d == null) return 0;
        File[] c = d.listFiles();
        if (c == null) return 0;
        for (File x : c) {
            if (x.isDirectory()) {
                freed += deleteContents(x);
                x.delete();
            } else {
                long l = x.length();
                if (x.delete()) freed += l;
            }
        }
        return freed;
    }
}
