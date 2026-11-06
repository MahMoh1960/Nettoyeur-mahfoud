package app.nettoyage;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Analyse du stockage : catégories, gros fichiers, doublons, etc. */
class Scan {
    static class Item {
        String path;
        long size, mtime;
        boolean sel, hdr, dir;
        int group = -1, gcount, gnum;
        long gsize;
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

    static final long LARGE = 10L * 1024 * 1024;
    static final long DAY30 = 30L * 86400000L;
    static final HashMap<String, Integer> EXT = new HashMap<>();
    static final HashSet<String> TMP = new HashSet<>();

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

    volatile boolean cancel;
    volatile long nFiles, nBytes;
    volatile String curDir = "";
    volatile int dupDone, dupTotal;
    boolean scanned, dupsReady;
    long now;

    long[] catBytes = new long[7];
    int[] catCount = new int[7];
    final ArrayList<Item> large = new ArrayList<>(), oldDl = new ArrayList<>(), shots = new ArrayList<>(),
            apks = new ArrayList<>(), msg = new ArrayList<>(), temp = new ArrayList<>(),
            zero = new ArrayList<>(), emptyDirs = new ArrayList<>(), empties = new ArrayList<>(),
            dups = new ArrayList<>();
    final HashMap<Long, ArrayList<Item>> bySize = new HashMap<>();
    ArrayList<ArrayList<Item>> dupGroups = new ArrayList<>();

    ArrayList<ArrayList<Item>> lists() {
        ArrayList<ArrayList<Item>> r = new ArrayList<>();
        r.add(large); r.add(oldDl); r.add(shots); r.add(apks); r.add(msg); r.add(temp); r.add(zero); r.add(emptyDirs); r.add(empties);
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
        dupsReady = false;
        nFiles = 0;
        nBytes = 0;
        catBytes = new long[7];
        catCount = new int[7];
        for (ArrayList<Item> l : lists()) l.clear();
        bySize.clear();
        dupGroups = new ArrayList<>();
        dups.clear();
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
        while (large.size() > 300) large.remove(large.size() - 1);
        Collections.sort(oldDl, bySz);
        Collections.sort(shots, bySz);
        Collections.sort(apks, bySz);
        Collections.sort(msg, bySz);
        Collections.sort(temp, bySz);
        Collections.sort(empties, (a, b) -> a.path.compareTo(b.path));
        Iterator<Map.Entry<Long, ArrayList<Item>>> it = bySize.entrySet().iterator();
        while (it.hasNext()) if (it.next().getValue().size() < 2) it.remove();
        scanned = true;
    }

    private void walk(File d, int depth) {
        if (cancel) return;
        String dp = d.getAbsolutePath();
        curDir = dp;
        File[] ch = d.listFiles();
        if (ch == null) return;
        if (ch.length == 0) {
            if (depth >= 2 && !dp.contains("/Android/")) emptyDirs.add(new Item(dp, 0, d.lastModified()));
            return;
        }
        for (File f : ch) {
            if (cancel) return;
            String n = f.getName();
            if (f.isDirectory()) {
                if (depth == 0 && n.equals("Android")) {
                    walk(new File(f, "media"), 3);
                    continue;
                }
                if (depth >= 30) continue;
                try {
                    if (!f.getCanonicalPath().equals(f.getAbsolutePath())) continue;
                } catch (IOException e) {
                    continue;
                }
                walk(f, depth + 1);
            } else {
                handle(f, n);
            }
        }
    }

    private void handle(File f, String n) {
        long len = f.length();
        long mt = f.lastModified();
        String p = f.getAbsolutePath();
        if (len == 0) {
            if (!n.startsWith(".")) zero.add(new Item(p, 0, mt));
            return;
        }
        Item it = new Item(p, len, mt);
        nFiles++;
        nBytes += len;
        int c = cat(n);
        catBytes[c] += len;
        catCount[c]++;
        String lp = p.toLowerCase(Locale.ROOT);
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
        if (c == 0 && (lp.contains("/screenshots/") || lp.contains("/screenshot/")
                || n.toLowerCase(Locale.ROOT).startsWith("screenshot"))) shots.add(it);
        if (age > DAY30 && (lp.contains("/whatsapp/media/") || lp.contains("/whatsapp business/media/")
                || lp.contains("/telegram/")) && !lp.contains("/backups/") && !lp.contains("/databases/")) msg.add(it);
        if (TMP.contains(ext(n)) || lp.contains("/.thumbnails/") || n.startsWith(".thumbdata")) temp.add(it);
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
        dupsReady = false;
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
        dupGroups = res;
        flatten();
        dupDefaults();
        dupsReady = true;
    }

    void flatten() {
        dups.clear();
        int gi = 0;
        for (ArrayList<Item> g : dupGroups) {
            Item h = new Item("", 0, 0);
            h.hdr = true;
            h.gcount = g.size();
            h.gsize = g.get(0).size;
            h.gnum = gi + 1;
            dups.add(h);
            for (Item it : g) {
                it.group = gi;
                dups.add(it);
            }
            gi++;
        }
    }

    void dupDefaults() {
        for (ArrayList<Item> g : dupGroups) for (int i = 0; i < g.size(); i++) g.get(i).sel = i > 0;
    }

    long dupFreeable() {
        long s = 0;
        for (ArrayList<Item> g : dupGroups) s += g.get(0).size * (g.size() - 1);
        return s;
    }

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
        for (ArrayList<Item> g : dupGroups) g.removeIf(i -> s.contains(i.path));
        dupGroups.removeIf(g -> g.size() < 2);
        flatten();
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
