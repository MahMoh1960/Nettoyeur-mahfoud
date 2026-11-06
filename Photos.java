package app.nettoyage;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

/** Analyse des photos (similaires, floues) et compression JPEG. */
class Photos {
    static class Info {
        Scan.Item it;
        long hash;
        int w, h;
        double sharp;
        boolean ok;
    }

    private static int lum(int c) {
        int r = (c >> 16) & 255, g = (c >> 8) & 255, b = c & 255;
        return (int) (0.299 * r + 0.587 * g + 0.114 * b);
    }

    static Info one(Scan.Item it) {
        Info f = new Info();
        f.it = it;
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(it.path, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return f;
            f.w = o.outWidth;
            f.h = o.outHeight;
            int s = 1;
            int m = Math.max(f.w, f.h);
            while (m / (s * 2) >= 192) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            Bitmap b = BitmapFactory.decodeFile(it.path, o2);
            if (b == null) return f;

            // empreinte de différence (dHash 64 bits)
            Bitmap sm = Bitmap.createScaledBitmap(b, 9, 8, true);
            long h = 0;
            int bit = 0;
            for (int y = 0; y < 8; y++) {
                for (int x = 0; x < 8; x++) {
                    if (lum(sm.getPixel(x, y)) > lum(sm.getPixel(x + 1, y))) h |= (1L << bit);
                    bit++;
                }
            }
            if (sm != b) sm.recycle();
            f.hash = h;

            // netteté : variance du laplacien sur une version réduite (160 px max)
            int bw = b.getWidth(), bh = b.getHeight();
            float k = 160f / Math.max(bw, bh);
            Bitmap g = b;
            if (k < 1f) g = Bitmap.createScaledBitmap(b, Math.max(3, Math.round(bw * k)), Math.max(3, Math.round(bh * k)), true);
            int tw = g.getWidth(), th = g.getHeight();
            int[] px = new int[tw * th];
            g.getPixels(px, 0, tw, 0, 0, tw, th);
            int[] gray = new int[tw * th];
            for (int i = 0; i < px.length; i++) gray[i] = lum(px[i]);
            double sum = 0, sq = 0;
            int n = 0;
            for (int y = 1; y < th - 1; y++) {
                for (int x = 1; x < tw - 1; x++) {
                    int c = y * tw + x;
                    double lap = 4.0 * gray[c] - gray[c - 1] - gray[c + 1] - gray[c - tw] - gray[c + tw];
                    sum += lap;
                    sq += lap * lap;
                    n++;
                }
            }
            if (n > 0) {
                double mean = sum / n;
                f.sharp = sq / n - mean * mean;
                f.ok = true;
            }
            if (g != b) g.recycle();
            b.recycle();
        } catch (Throwable e) {
            f.ok = false;
        }
        return f;
    }

    private static int find(int[] p, int x) {
        while (p[x] != x) {
            p[x] = p[p[x]];
            x = p[x];
        }
        return x;
    }

    /** Calcule les groupes de photos similaires et la liste des photos floues. */
    static void analyze(final Scan scan) {
        scan.cancel = false;
        scan.photosReady = false;
        scan.photoDone.set(0);
        final ArrayList<Scan.Item> imgs = new ArrayList<>(scan.images);
        scan.photoTotal = imgs.size();
        final Info[] infos = new Info[imgs.size()];
        final int nt = 3;
        Thread[] th = new Thread[nt];
        for (int t = 0; t < nt; t++) {
            final int tt = t;
            th[t] = new Thread(() -> {
                for (int i = tt; i < imgs.size(); i += nt) {
                    if (scan.cancel) return;
                    infos[i] = one(imgs.get(i));
                    scan.photoDone.incrementAndGet();
                }
            });
            th[t].start();
        }
        for (Thread t : th) {
            try {
                t.join();
            } catch (InterruptedException e) {
                scan.cancel = true;
            }
        }
        if (scan.cancel) return;

        int n = infos.length;
        for (int i = 0; i < n; i++) {
            if (infos[i] != null && infos[i].ok) infos[i].it.score = (long) infos[i].w * infos[i].h;
        }

        // photos floues
        ArrayList<Info> bl = new ArrayList<>();
        for (Info f : infos) {
            if (f != null && f.ok && f.sharp < 20.0 && Math.max(f.w, f.h) >= 800) bl.add(f);
        }
        Collections.sort(bl, (a, b) -> Double.compare(a.sharp, b.sharp));
        scan.blurry.clear();
        for (Info f : bl) {
            f.it.q = f.sharp;
            scan.blurry.add(f.it);
            if (scan.blurry.size() >= 300) break;
        }

        // photos similaires : regroupement par distance de Hamming
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) parent[i] = i;
        for (int i = 0; i < n; i++) {
            Info a = infos[i];
            if (a == null || !a.ok || a.sharp < 1.0) continue;
            if (scan.cancel) return;
            double ra = (double) a.w / a.h;
            for (int j = i + 1; j < n; j++) {
                Info b = infos[j];
                if (b == null || !b.ok || b.sharp < 1.0) continue;
                if (Long.bitCount(a.hash ^ b.hash) > 5) continue;
                double rb = (double) b.w / b.h;
                if (Math.abs(ra - rb) > 0.03) continue;
                int x = find(parent, i), y = find(parent, j);
                if (x != y) parent[x] = y;
            }
        }
        HashMap<Integer, ArrayList<Scan.Item>> m = new HashMap<>();
        for (int i = 0; i < n; i++) {
            if (infos[i] == null || !infos[i].ok) continue;
            int r = find(parent, i);
            ArrayList<Scan.Item> l = m.get(r);
            if (l == null) {
                l = new ArrayList<>();
                m.put(r, l);
            }
            l.add(infos[i].it);
        }
        ArrayList<ArrayList<Scan.Item>> res = new ArrayList<>();
        for (ArrayList<Scan.Item> g : m.values()) {
            if (g.size() < 2) continue;
            Collections.sort(g, (a, b) -> a.score != b.score ? Long.compare(b.score, a.score) : Long.compare(b.size, a.size));
            res.add(g);
        }
        Collections.sort(res, (a, b) -> Long.compare(Scan.sum(b), Scan.sum(a)));
        scan.simG.groups = res;
        scan.simG.flatten();
        scan.simG.defaults();
        scan.simG.ready = true;
        scan.photosReady = true;
    }

    // ---------- compression ----------

    private static final String[] EXIF_TAGS = {
            ExifInterface.TAG_ORIENTATION, ExifInterface.TAG_DATETIME, ExifInterface.TAG_DATETIME_ORIGINAL,
            ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF,
            ExifInterface.TAG_GPS_LONGITUDE, ExifInterface.TAG_GPS_LONGITUDE_REF,
            ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_TIMESTAMP,
            ExifInterface.TAG_FOCAL_LENGTH, ExifInterface.TAG_EXPOSURE_TIME, ExifInterface.TAG_F_NUMBER
    };

    private static void copyExif(File src, File dst, int w, int h) {
        try {
            ExifInterface a = new ExifInterface(src.getAbsolutePath());
            ExifInterface b = new ExifInterface(dst.getAbsolutePath());
            for (String t : EXIF_TAGS) {
                String v = a.getAttribute(t);
                if (v != null) b.setAttribute(t, v);
            }
            b.setAttribute(ExifInterface.TAG_IMAGE_WIDTH, String.valueOf(w));
            b.setAttribute(ExifInterface.TAG_IMAGE_LENGTH, String.valueOf(h));
            b.saveAttributes();
        } catch (Throwable e) {
            // les métadonnées sont facultatives
        }
    }

    /** Crée une version compressée dans un fichier temporaire voisin ; null en cas d'échec. */
    static File compressTo(File src, int quality, int maxSide) {
        try {
            String path = src.getAbsolutePath();
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            if (o.outWidth <= 0 || o.outHeight <= 0) return null;
            int s = 1;
            int m = Math.max(o.outWidth, o.outHeight);
            while (m / (s * 2) >= maxSide) s *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = s;
            Bitmap b = BitmapFactory.decodeFile(path, o2);
            if (b == null) return null;
            int bm = Math.max(b.getWidth(), b.getHeight());
            if (bm > maxSide) {
                float k = (float) maxSide / bm;
                Bitmap nb = Bitmap.createScaledBitmap(b, Math.round(b.getWidth() * k), Math.round(b.getHeight() * k), true);
                if (nb != b) {
                    b.recycle();
                    b = nb;
                }
            }
            File tmp = new File(src.getParentFile(), src.getName() + ".cmp.tmp");
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                b.compress(Bitmap.CompressFormat.JPEG, quality, out);
            }
            int w = b.getWidth(), h = b.getHeight();
            b.recycle();
            copyExif(src, tmp, w, h);
            tmp.setLastModified(src.lastModified());
            return tmp;
        } catch (Throwable e) {
            return null;
        }
    }
}
