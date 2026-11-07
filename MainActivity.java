package app.nettoyage;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.usage.StorageStatsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaScannerConnection;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.StatFs;
import android.os.SystemClock;
import android.os.storage.StorageManager;
import android.provider.MediaStore;
import android.provider.Settings;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.util.LruCache;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    static final int BG = 0xFF0B1A2E, CARD = 0xFF14294A, CARD2 = 0xFF1F3C6B, TXT = 0xFFEAF1FB,
            SUB = 0xFF9DB2CF, ACC = 0xFFFFC83D, GRN = 0xFF3DDC97, RED = 0xFFFF5D5D, WHITE = 0xFFFFFFFF;
    static final int[] CATC = {0xFF4DA3FF, 0xFFB57BFF, 0xFF3DDC97, 0xFFFFC83D, 0xFFFF8A4D, 0xFFFF5D8F, 0xFF8294AD};
    static final String[] CATE = {"🖼️", "🎞️", "🎵", "📄", "📦", "🗜️", "📁"};

    Store st;
    Trash trash;
    final Scan scan = new Scan();
    FrameLayout root, content;
    View lockView;
    final Handler ui = new Handler(Looper.getMainLooper());
    final ExecutorService bg = Executors.newSingleThreadExecutor();
    final ExecutorService thumbEx = Executors.newFixedThreadPool(2);
    final LruCache<String, Bitmap> thumbs = new LruCache<>(80);
    final ArrayDeque<Runnable> back = new ArrayDeque<>();
    Runnable cur;
    final String ROOT = Environment.getExternalStorageDirectory().getAbsolutePath();
    int largeCat = -1;
    long largeMin = Scan.LARGE;
    String pickPath, foldPath;
    boolean pendingScan, optAfterScan;
    final Runnable pickRun = this::picker;
    final Runnable foldRun = this::folders;
    boolean locked, scanning, working, needRefresh;
    long lastStop, blockUntil;
    int fails;

    // =====================================================================
    // Cycle de vie
    // =====================================================================

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = new Store(this);
        trash = new Trash(this);
        st.applyLang();
        Scan.EXCL = st.excluded();
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (st.hasPin()) getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        root = new FrameLayout(this);
        root.setBackgroundColor(BG);
        content = new FrameLayout(this);
        root.addView(content, new FrameLayout.LayoutParams(-1, -1));
        setContentView(root);
        lastStop = System.currentTimeMillis();
        cur = this::home;
        cur.run();
        if (st.hasPin()) showLock();
        bg.execute(() -> trash.purgeOlder(st.purgeDays()));
        pendingScan = getIntent().getBooleanExtra("scan", false);
        maybeAutoScan();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);
        pendingScan = i.getBooleanExtra("scan", false);
        maybeAutoScan();
    }

    void maybeAutoScan() {
        if (pendingScan && !locked && !scanning && hasAccess()) {
            pendingScan = false;
            startScan();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        long n = System.currentTimeMillis();
        if (!locked && st.hasPin() && n - lastStop > 120000) {
            showLock();
        } else if (!locked && needRefresh) {
            needRefresh = false;
            cur.run();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        lastStop = System.currentTimeMillis();
    }

    @Override
    public void onBackPressed() {
        if (locked) {
            moveTaskToBack(true);
            return;
        }
        if (scanning) {
            scan.cancel = true;
            return;
        }
        if (working) scan.cancel = true;
        if (cur == pickRun && pickPath != null && !pickPath.equals(ROOT)) {
            pickPath = parentOf(pickPath);
            picker();
            return;
        }
        if (cur == foldRun && foldPath != null && !foldPath.equals(ROOT)) {
            foldPath = parentOf(foldPath);
            folders();
            return;
        }
        if (!back.isEmpty()) {
            cur = back.pop();
            cur.run();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        cur.run();
    }

    String parentOf(String p) {
        int i = p.lastIndexOf('/');
        String r = i > 0 ? p.substring(0, i) : ROOT;
        return r.length() < ROOT.length() ? ROOT : r;
    }

    void nav(Runnable r) {
        if (cur != null) back.push(cur);
        cur = r;
        r.run();
    }

    // =====================================================================
    // Petits utilitaires d'interface
    // =====================================================================

    int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    TextView tv(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    GradientDrawable rr(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    LinearLayout vbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    LinearLayout hbox() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        return l;
    }

    Button btn(String s, int bgc, int fg, View.OnClickListener c) {
        Button b = new Button(this);
        b.setText(s);
        b.setAllCaps(false);
        b.setTextColor(fg);
        b.setBackground(rr(bgc, 12));
        b.setPadding(dp(14), dp(10), dp(14), dp(10));
        if (c != null) b.setOnClickListener(c);
        return b;
    }

    LinearLayout.LayoutParams lpw(int top) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.topMargin = dp(top);
        return p;
    }

    LinearLayout card() {
        LinearLayout c = vbox();
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        c.setBackground(rr(CARD, 16));
        return c;
    }

    TextView note(String s) {
        TextView t = tv(s, 13, SUB, false);
        t.setPadding(dp(14), dp(10), dp(14), dp(6));
        return t;
    }

    void toast(String s) {
        Toast.makeText(this, s, Toast.LENGTH_LONG).show();
    }

    View bar(long[] v, int[] col) {
        LinearLayout l = hbox();
        l.setBackground(rr(CARD2, 6));
        l.setClipToOutline(true);
        float sum = 0;
        for (long x : v) sum += Math.max(0, x);
        l.setWeightSum(Math.max(sum, 1f));
        for (int i = 0; i < v.length; i++) {
            if (v[i] <= 0) continue;
            View x = new View(this);
            x.setBackgroundColor(col[i]);
            l.addView(x, new LinearLayout.LayoutParams(0, -1, (float) v[i]));
        }
        return l;
    }

    LinearLayout.LayoutParams barLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(12));
        p.topMargin = dp(8);
        p.bottomMargin = dp(8);
        return p;
    }

    void setScreen(View v) {
        content.removeAllViews();
        content.addView(v, new FrameLayout.LayoutParams(-1, -1));
        content.setLayoutDirection(Tr.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
    }

    LinearLayout scaffold(String title, boolean withBack, View action) {
        LinearLayout page = vbox();
        page.setBackgroundColor(BG);
        LinearLayout bar = hbox();
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setPadding(dp(8), dp(12), dp(8), dp(12));
        bar.setBackgroundColor(CARD);
        if (withBack) {
            TextView b = tv(Tr.ar ? "→" : "←", 24, ACC, true);
            b.setPadding(dp(12), 0, dp(12), 0);
            b.setOnClickListener(v -> onBackPressed());
            bar.addView(b);
        } else {
            View sp = new View(this);
            bar.addView(sp, new LinearLayout.LayoutParams(dp(8), 1));
        }
        TextView t = tv(title, 19, TXT, true);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        bar.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        if (action != null) bar.addView(action);
        page.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        setScreen(page);
        return page;
    }

    AlertDialog busy(String msg) {
        LinearLayout l = hbox();
        l.setGravity(Gravity.CENTER_VERTICAL);
        l.setPadding(dp(20), dp(20), dp(20), dp(20));
        l.addView(new ProgressBar(this));
        TextView t = tv(msg, 15, TXT, false);
        t.setPaddingRelative(dp(16), 0, 0, 0);
        l.addView(t);
        l.setLayoutDirection(Tr.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        AlertDialog d = new AlertDialog.Builder(this).setView(l).setCancelable(false).create();
        d.show();
        return d;
    }

    // =====================================================================
    // Autorisations
    // =====================================================================

    boolean hasAccess() {
        if (Build.VERSION.SDK_INT >= 30) return Environment.isExternalStorageManager();
        return checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    void askAccess() {
        if (Build.VERSION.SDK_INT >= 30) {
            needRefresh = true;
            try {
                startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (Exception e2) {
                    toast("Settings ?");
                }
            }
        } else {
            requestPermissions(new String[]{android.Manifest.permission.READ_EXTERNAL_STORAGE,
                    android.Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1);
        }
    }

    // =====================================================================
    // Verrouillage par code PIN (optionnel)
    // =====================================================================

    void showLock() {
        if (locked) return;
        locked = true;
        LinearLayout l = vbox();
        l.setBackgroundColor(BG);
        l.setGravity(Gravity.CENTER);
        l.setPadding(dp(30), dp(30), dp(30), dp(30));
        l.setClickable(true);
        l.setLayoutDirection(Tr.ar ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        TextView icon = tv("🔒", 54, TXT, false);
        icon.setGravity(Gravity.CENTER);
        l.addView(icon);
        TextView t = tv(Tr.t("lock_title"), 20, TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(10), 0, dp(20));
        l.addView(t);
        final EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setHint(Tr.t("pin_hint"));
        e.setTextColor(TXT);
        e.setHintTextColor(SUB);
        e.setGravity(Gravity.CENTER);
        e.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        e.setImeOptions(EditorInfo.IME_ACTION_DONE);
        l.addView(e, new LinearLayout.LayoutParams(-1, -2));
        final TextView err = tv("", 14, RED, false);
        err.setGravity(Gravity.CENTER);
        err.setPadding(0, dp(8), 0, dp(8));
        l.addView(err);
        final View.OnClickListener tryPin = v -> {
            long n = System.currentTimeMillis();
            if (n < blockUntil) {
                err.setText(Tr.f("pin_wait", (int) ((blockUntil - n) / 1000) + 1));
                return;
            }
            if (st.checkPin(e.getText().toString())) {
                unlock();
            } else {
                e.setText("");
                fails++;
                if (fails >= 5) {
                    fails = 0;
                    blockUntil = n + 30000;
                    err.setText(Tr.f("pin_wait", 30));
                } else {
                    err.setText(Tr.t("pin_bad"));
                }
            }
        };
        l.addView(btn(Tr.t("unlock"), ACC, BG, tryPin), new LinearLayout.LayoutParams(-1, -2));
        e.setOnEditorActionListener((x, id, ev) -> {
            tryPin.onClick(x);
            return true;
        });
        lockView = l;
        root.addView(l, new FrameLayout.LayoutParams(-1, -1));
        ui.postDelayed(() -> {
            e.requestFocus();
            InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.showSoftInput(e, 0);
        }, 250);
    }

    void unlock() {
        locked = false;
        fails = 0;
        if (lockView != null) root.removeView(lockView);
        lockView = null;
        InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (im != null) im.hideSoftInputFromWindow(root.getWindowToken(), 0);
        if (needRefresh) {
            needRefresh = false;
            cur.run();
        }
        maybeAutoScan();
    }

    EditText pinField(String hint) {
        EditText e = new EditText(this);
        e.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        e.setHint(hint);
        e.setFilters(new InputFilter[]{new InputFilter.LengthFilter(8)});
        return e;
    }

    void askPin(final Runnable ok) {
        final EditText e = pinField(Tr.t("pin_cur"));
        LinearLayout l = vbox();
        l.setPadding(dp(20), dp(10), dp(20), 0);
        l.addView(e);
        new AlertDialog.Builder(this).setView(l)
                .setPositiveButton(Tr.t("ok"), (d, w) -> {
                    if (st.checkPin(e.getText().toString())) ok.run();
                    else toast(Tr.t("pin_bad"));
                })
                .setNegativeButton(Tr.t("cancel"), null).show();
    }

    void pinSetup() {
        final EditText e1 = pinField(Tr.t("pin_new"));
        final EditText e2 = pinField(Tr.t("pin_conf"));
        LinearLayout l = vbox();
        l.setPadding(dp(20), dp(10), dp(20), 0);
        l.addView(e1);
        l.addView(e2);
        new AlertDialog.Builder(this).setView(l)
                .setPositiveButton(Tr.t("ok"), (d, w) -> {
                    String a = e1.getText().toString();
                    String c = e2.getText().toString();
                    if (a.length() >= 4 && a.length() <= 8 && a.equals(c)) {
                        st.setPin(a);
                        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
                        toast(Tr.t("pin_saved"));
                        cur.run();
                    } else {
                        toast(Tr.t("pin_mismatch"));
                    }
                })
                .setNegativeButton(Tr.t("cancel"), null).show();
    }

    // =====================================================================
    // Accueil
    // =====================================================================

    void home() {
        TextView gear = tv("⚙️", 22, TXT, false);
        gear.setPadding(dp(12), 0, dp(12), 0);
        gear.setOnClickListener(v -> nav(this::settings));
        LinearLayout page = scaffold(Tr.t("app"), false, gear);
        ScrollView sv = new ScrollView(this);
        LinearLayout b = vbox();
        b.setPadding(dp(14), dp(12), dp(14), dp(24));
        sv.addView(b);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Stockage
        LinearLayout c = card();
        StatFs sf = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long total = sf.getTotalBytes();
        long free = sf.getAvailableBytes();
        long used = total - free;
        c.addView(tv(Tr.t("storage"), 16, TXT, true));
        c.addView(tv(Tr.f("used_of", Tr.size(used), Tr.size(total)), 14, SUB, false));
        int usedColor = used * 100 / Math.max(total, 1) > 85 ? RED : ACC;
        c.addView(bar(new long[]{used, free}, new int[]{usedColor, CARD2}), barLp());
        c.addView(tv(Tr.f("free", Tr.size(free)), 14, GRN, true));
        b.addView(c, lpw(0));

        long upDays = SystemClock.elapsedRealtime() / 86400000L;
        if (upDays >= 7) {
            LinearLayout rb = card();
            rb.addView(tv("🔄 " + Tr.f("reboot_banner", (int) upDays), 13, ACC, false));
            b.addView(rb, lpw(10));
        }

        // Autorisation
        if (!hasAccess()) {
            LinearLayout p = card();
            p.addView(tv(Tr.t("perm_title"), 16, ACC, true));
            TextView m = tv(Tr.t("perm_msg"), 13, SUB, false);
            m.setPadding(0, dp(4), 0, dp(8));
            p.addView(m);
            p.addView(btn(Tr.t("grant"), ACC, BG, v -> askAccess()), new LinearLayout.LayoutParams(-1, -2));
            b.addView(p, lpw(10));
        }

        // Analyse
        b.addView(btn(Tr.t(scan.scanned ? "rescan" : "scan"), GRN, BG, v -> startScan()), lpw(10));
        if (scan.scanned) b.addView(btn(Tr.t("quick"), ACC, BG, v -> quickClean()), lpw(8));

        // Répartition
        if (scan.scanned) {
            LinearLayout cc = card();
            cc.addView(tv(Tr.t("cats"), 16, TXT, true));
            long[] vals = new long[7];
            for (int i = 0; i < 7; i++) vals[i] = Math.max(0, scan.catBytes[i]);
            cc.addView(bar(vals, CATC), barLp());
            for (int i = 0; i < 7; i++) {
                LinearLayout r = hbox();
                r.setGravity(Gravity.CENTER_VERTICAL);
                r.setPadding(0, dp(4), 0, dp(4));
                View dot = new View(this);
                dot.setBackground(rr(CATC[i], 6));
                r.addView(dot, new LinearLayout.LayoutParams(dp(12), dp(12)));
                TextView n = tv(Tr.t("c" + i), 14, TXT, false);
                n.setPaddingRelative(dp(8), 0, dp(8), 0);
                r.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
                r.addView(tv(scan.catCount[i] + " · " + Tr.size(Math.max(0, scan.catBytes[i])), 13, SUB, false));
                cc.addView(r);
            }
            b.addView(cc, lpw(10));
        }

        // Fonctions
        boolean sc = scan.scanned;
        menu(b, "🚀", "f_opt", Tr.t("s_opt"), () -> nav(this::optimizer));
        menu(b, "📏", "f_large", sc ? Tr.f("sub_items", scan.large.size(), Tr.size(Scan.sum(scan.large))) : Tr.t("s_large"),
                () -> needScan(() -> nav(this::largeScreen)));
        menu(b, "👯", "f_dups", scan.dupG.ready ? Tr.f("dups_sub", scan.dupG.groups.size(), Tr.size(scan.dupG.freeable())) : Tr.t("s_dups"),
                () -> needScan(() -> nav(this::dupsScreen)));
        menu(b, "🔍", "f_similar", scan.photosReady ? Tr.f("dups_sub", scan.simG.groups.size(), Tr.size(scan.simG.freeable())) : Tr.t("s_similar"),
                () -> needScan(() -> nav(this::similarScreen)));
        menu(b, "🌀", "f_blur", scan.photosReady ? Tr.f("sub_items", scan.blurry.size(), Tr.size(Scan.sum(scan.blurry))) : Tr.t("s_blur"),
                () -> needScan(() -> nav(this::blurScreen)));
        menu(b, "📉", "f_compress", sc ? Tr.f("sub_items", scan.bigPhotos.size(), Tr.size(Scan.sum(scan.bigPhotos))) : Tr.t("s_compress"),
                () -> needScan(() -> nav(() -> fileList(cfg("f_compress", "d_compress", scan.bigPhotos, false, "compress")))));
        menu(b, "📂", "f_folders", Tr.t("s_folders"),
                () -> needScan(() -> {
                    foldPath = ROOT;
                    nav(foldRun);
                }));
        menu(b, "📥", "f_old", sc ? Tr.f("sub_items", scan.oldDl.size(), Tr.size(Scan.sum(scan.oldDl))) : Tr.t("s_old"),
                () -> needScan(() -> nav(() -> fileList(cfg("f_old", "d_old", scan.oldDl, false, "del")))));
        menu(b, "📸", "f_shots", sc ? Tr.f("sub_items", scan.shots.size(), Tr.size(Scan.sum(scan.shots))) : Tr.t("s_shots"),
                () -> needScan(() -> nav(() -> fileList(cfg("f_shots", "d_shots", scan.shots, false, "del")))));
        menu(b, "📦", "f_apk", sc ? Tr.f("sub_items", scan.apks.size(), Tr.size(Scan.sum(scan.apks))) : Tr.t("s_apk"),
                () -> needScan(() -> nav(() -> fileList(cfg("f_apk", "d_apk", scan.apks, false, "del")))));
        menu(b, "💬", "f_msg", sc ? Tr.f("sub_items", scan.msg.size(), Tr.size(Scan.sum(scan.msg))) : Tr.t("s_msg"),
                () -> needScan(() -> nav(() -> fileList(cfg("f_msg", "d_msg", scan.msg, false, "del")))));
        menu(b, "🧹", "f_temp", sc ? Tr.f("sub_items", scan.temp.size(), Tr.size(Scan.sum(scan.temp))) : Tr.t("s_temp"),
                () -> needScan(() -> nav(() -> fileList(cfg("f_temp", "d_temp", scan.temp, true, "del")))));
        menu(b, "🗂️", "f_empty", sc ? Tr.f("sub_items", scan.empties.size(), Tr.size(0)) : Tr.t("s_empty"),
                () -> needScan(() -> nav(() -> fileList(cfg("f_empty", "d_empty", scan.empties, true, "del")))));
        menu(b, "📱", "f_apps", Tr.t("s_apps"), () -> nav(this::apps));
        menu(b, "💾", "f_cache", Tr.t("s_cache"), () -> nav(this::cacheScreen));
        menu(b, "🧺", "f_appcache", Tr.t("s_appcache"), () -> nav(this::appCache));
        menu(b, "🗑️", "f_trash", Tr.t("s_trash") + " · " + Tr.size(trash.totalSize()), () -> nav(this::trashScreen));
        menu(b, "🗄️", "f_backups", Tr.t("s_backups"), () -> nav(this::backups));
        menu(b, "📊", "f_report", Tr.t("s_report"), () -> nav(this::report));
    }

    void needScan(Runnable r) {
        if (!scan.scanned) {
            toast(Tr.t("scan_first"));
            return;
        }
        r.run();
    }

    void menu(LinearLayout parent, String emoji, String key, String sub, final Runnable go) {
        LinearLayout r = hbox();
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(dp(14), dp(12), dp(14), dp(12));
        r.setBackground(rr(CARD, 14));
        TextView e = tv(emoji, 24, TXT, false);
        e.setPaddingRelative(0, 0, dp(12), 0);
        LinearLayout col = vbox();
        col.addView(tv(Tr.t(key), 16, TXT, true));
        col.addView(tv(sub, 12, SUB, false));
        r.addView(e);
        r.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(tv(Tr.ar ? "‹" : "›", 24, SUB, false));
        r.setOnClickListener(v -> go.run());
        parent.addView(r, lpw(8));
    }

    // =====================================================================
    // Analyse
    // =====================================================================

    void startScan() {
        if (!hasAccess()) {
            askAccess();
            return;
        }
        Scan.EXCL = st.excluded();
        scanning = true;
        working = true;
        LinearLayout page = scaffold(Tr.t("scanning"), false, null);
        LinearLayout b = vbox();
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(24), dp(24), dp(24), dp(24));
        b.addView(new ProgressBar(this));
        final TextView t1 = tv("", 17, TXT, true);
        t1.setGravity(Gravity.CENTER);
        t1.setPadding(0, dp(16), 0, dp(6));
        final TextView t2 = tv("", 12, SUB, false);
        t2.setGravity(Gravity.CENTER);
        t2.setSingleLine(true);
        t2.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        b.addView(t1, new LinearLayout.LayoutParams(-1, -2));
        b.addView(t2, new LinearLayout.LayoutParams(-1, -2));
        b.addView(btn(Tr.t("cancel"), CARD2, TXT, v -> scan.cancel = true), lpw(20));
        page.addView(b, new LinearLayout.LayoutParams(-1, 0, 1f));
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (!scanning) return;
                t1.setText(Tr.f("scan_prog", scan.nFiles, Tr.size(scan.nBytes)));
                t2.setText(scan.curDir);
                ui.postDelayed(this, 250);
            }
        });
        bg.execute(() -> {
            scan.scan(Environment.getExternalStorageDirectory());
            ui.post(() -> {
                scanning = false;
                working = false;
                back.clear();
                cur = this::home;
                home();
                if (optAfterScan) {
                    optAfterScan = false;
                    if (scan.scanned) quickClean();
                }
            });
        });
    }

    void progressScreen(String titleKey, final java.util.function.Supplier<String> status, final Runnable work) {
        LinearLayout page = scaffold(Tr.t(titleKey), true, null);
        LinearLayout b = vbox();
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(24), dp(24), dp(24), dp(24));
        b.addView(new ProgressBar(this));
        final TextView t = tv(status.get(), 16, TXT, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, dp(16), 0, 0);
        b.addView(t, new LinearLayout.LayoutParams(-1, -2));
        page.addView(b, new LinearLayout.LayoutParams(-1, 0, 1f));
        working = true;
        scan.cancel = false;
        final Runnable me = cur;
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (!working || cur != me) return;
                t.setText(status.get());
                ui.postDelayed(this, 300);
            }
        });
        bg.execute(() -> {
            work.run();
            ui.post(() -> {
                working = false;
                if (scan.cancel || cur != me) return;
                cur.run();
            });
        });
    }

    void dupsScreen() {
        if (!scan.dupG.ready) {
            progressScreen("f_dups", () -> Tr.t("dups_search") + " " + scan.dupDone + "/" + scan.dupTotal, scan::findDups);
            return;
        }
        Cfg c = cfg("f_dups", "d_dups", scan.dupG.flat, false, "del");
        c.groups = scan.dupG;
        fileList(c);
    }

    void photosProgress() {
        progressScreen("f_similar", () -> Tr.t("photos_search") + " " + scan.photoDone.get() + "/" + scan.photoTotal,
                () -> Photos.analyze(scan));
    }

    void similarScreen() {
        if (!scan.photosReady) {
            photosProgress();
            return;
        }
        Cfg c = cfg("f_similar", "d_similar", scan.simG.flat, false, "del");
        c.groups = scan.simG;
        fileList(c);
    }

    void blurScreen() {
        if (!scan.photosReady) {
            photosProgress();
            return;
        }
        fileList(cfg("f_blur", "d_blur", scan.blurry, false, "del"));
    }

    void backups() {
        File d = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), Scan.BACKUP_DIR_NAME);
        ArrayList<Scan.Item> l = new ArrayList<>();
        File[] fs = d.listFiles();
        if (fs != null) for (File f : fs) if (f.isFile()) l.add(new Scan.Item(f.getAbsolutePath(), f.length(), f.lastModified()));
        Collections.sort(l, (a, b) -> Long.compare(b.mtime, a.mtime));
        fileList(cfg("f_backups", "d_backups", l, false, "del"));
    }

    // =====================================================================
    // Gros fichiers avec filtres
    // =====================================================================

    TextView chip(String label, boolean on, final Runnable click) {
        TextView c = tv(label, 13, on ? BG : TXT, on);
        c.setPadding(dp(12), dp(6), dp(12), dp(6));
        c.setBackground(rr(on ? ACC : CARD2, 16));
        c.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMarginEnd(dp(6));
        c.setLayoutParams(p);
        return c;
    }

    void largeScreen() {
        final ArrayList<Scan.Item> view = new ArrayList<>();
        final Runnable refresh = () -> {
            view.clear();
            for (Scan.Item it : scan.large) {
                if (it.size >= largeMin && (largeCat < 0 || Scan.cat(it.name()) == largeCat)) view.add(it);
            }
        };
        refresh.run();
        LinearLayout ex = vbox();
        ex.setPadding(dp(10), 0, dp(10), dp(6));
        HorizontalScrollView h1 = new HorizontalScrollView(this);
        LinearLayout r1 = hbox();
        h1.addView(r1);
        r1.addView(chip(Tr.t("large_all"), largeCat < 0, () -> {
            largeCat = -1;
            cur.run();
        }));
        for (int i = 0; i < 7; i++) {
            final int ci = i;
            r1.addView(chip(CATE[i] + " " + Tr.t("c" + i), largeCat == i, () -> {
                largeCat = ci;
                cur.run();
            }));
        }
        ex.addView(h1);
        LinearLayout r2 = hbox();
        r2.setPadding(0, dp(6), 0, 0);
        long[] mins = {10, 50, 100};
        for (long mb : mins) {
            final long min = mb * 1024 * 1024;
            r2.addView(chip("≥ " + Tr.size(min), largeMin == min, () -> {
                largeMin = min;
                cur.run();
            }));
        }
        ex.addView(r2);
        Cfg c = cfg("f_large", "d_large", view, false, "del");
        c.extra = ex;
        c.refresh = refresh;
        fileList(c);
    }

    // =====================================================================
    // Listes de fichiers
    // =====================================================================

    static class Cfg {
        String fkey, dkey, act = "del";
        ArrayList<Scan.Item> list;
        Scan.Groups groups;
        boolean pre;
        View extra;
        Runnable refresh;
    }

    Cfg cfg(String f, String d, ArrayList<Scan.Item> l, boolean pre, String act) {
        Cfg c = new Cfg();
        c.fkey = f;
        c.dkey = d;
        c.list = l;
        c.pre = pre;
        c.act = act;
        return c;
    }

    void fileList(final Cfg c) {
        final ArrayList<Scan.Item> list = c.list;
        final boolean dupMode = c.groups != null;
        final boolean compress = c.act.equals("compress");
        LinearLayout page = scaffold(Tr.t(c.fkey), true, null);
        if (dupMode) c.groups.defaults();
        else for (Scan.Item it : list) it.sel = c.pre;
        page.addView(note(Tr.t(c.dkey)));
        if (c.extra != null) page.addView(c.extra);

        final FileAdapter ad = new FileAdapter(list);
        final ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setAdapter(ad);
        final TextView empty = tv(Tr.t("empty_list"), 15, SUB, false);
        empty.setGravity(Gravity.CENTER);
        FrameLayout fl = new FrameLayout(this);
        fl.addView(lv, new FrameLayout.LayoutParams(-1, -1));
        fl.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        lv.setEmptyView(empty);

        final TextView summary = tv("", 13, TXT, false);
        final Button delBtn = btn(Tr.t(compress ? "compress" : "del"), compress ? GRN : RED, compress ? BG : WHITE, null);
        final Button shBtn = btn("📤", CARD2, TXT, null);
        final Runnable upd = () -> {
            int n = 0;
            long s = 0;
            for (Scan.Item it : list) {
                if (!it.hdr && it.sel) {
                    n++;
                    s += it.size;
                }
            }
            summary.setText(Tr.f("selinfo", n, Tr.size(s)));
            delBtn.setEnabled(n > 0);
            delBtn.setAlpha(n > 0 ? 1f : 0.4f);
            shBtn.setEnabled(n > 0);
            shBtn.setAlpha(n > 0 ? 1f : 0.4f);
            ad.notifyDataSetChanged();
        };
        final Runnable after = () -> {
            if (c.refresh != null) c.refresh.run();
            upd.run();
        };

        LinearLayout ctl = hbox();
        ctl.setPadding(dp(10), dp(2), dp(10), dp(6));
        Button all = btn(Tr.t("all"), CARD2, TXT, v -> {
            if (dupMode) {
                c.groups.defaults();
            } else {
                for (Scan.Item it : list) it.sel = true;
            }
            upd.run();
        });
        Button none = btn(Tr.t("none"), CARD2, TXT, v -> {
            for (Scan.Item it : list) it.sel = false;
            upd.run();
        });
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
        bp.setMarginEnd(dp(8));
        ctl.addView(all, bp);
        ctl.addView(none, new LinearLayout.LayoutParams(0, -2, 1f));
        page.addView(ctl, new LinearLayout.LayoutParams(-1, -2));
        page.addView(fl, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout foot = hbox();
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(dp(14), dp(8), dp(14), dp(8));
        foot.setBackgroundColor(CARD);
        foot.addView(summary, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.setMarginEnd(dp(8));
        foot.addView(shBtn, sp);
        foot.addView(delBtn);
        page.addView(foot, new LinearLayout.LayoutParams(-1, -2));

        lv.setOnItemClickListener((p, v, pos, id) -> {
            Scan.Item it = list.get(pos);
            if (it.hdr) return;
            if (dupMode && !it.sel) {
                boolean other = false;
                ArrayList<Scan.Item> g = c.groups.of.get(it);
                if (g != null) for (Scan.Item o : g) if (o != it && !o.sel) other = true;
                if (!other) {
                    toast(Tr.t("keepone"));
                    return;
                }
            }
            it.sel = !it.sel;
            upd.run();
        });
        lv.setOnItemLongClickListener((p, v, pos, id) -> {
            Scan.Item it = list.get(pos);
            if (!it.hdr) info(it);
            return true;
        });
        shBtn.setOnClickListener(v -> {
            ArrayList<Scan.Item> sel = new ArrayList<>();
            for (Scan.Item it : list) if (!it.hdr && it.sel) sel.add(it);
            share(sel);
        });
        if (compress) delBtn.setOnClickListener(v -> confirmCompress(list, after));
        else delBtn.setOnClickListener(v -> confirmDelete(c.fkey, list, after));
        upd.run();
    }

    void info(final Scan.Item it) {
        String p = it.orig != null ? it.orig : it.path;
        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setMessage(Tr.f("file_info", p, Tr.size(it.size), Tr.date(it.mtime)))
                .setPositiveButton(Tr.t("ok"), null);
        if (!it.dir) b.setNeutralButton(Tr.t("share"), (d, w) -> share(Collections.singletonList(it)));
        b.show();
    }

    // ---- Partage ----

    void share(List<Scan.Item> items) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (Scan.Item it : items) if (!it.dir && !it.hdr) uris.add(ShareProvider.uriFor(it.path));
        if (uris.isEmpty()) return;
        if (uris.size() > 50) {
            toast(Tr.t("share_max"));
            return;
        }
        Intent i;
        if (uris.size() == 1) {
            i = new Intent(Intent.ACTION_SEND);
            i.putExtra(Intent.EXTRA_STREAM, uris.get(0));
            i.setType("*/*");
        } else {
            i = new Intent(Intent.ACTION_SEND_MULTIPLE);
            i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris);
            i.setType("*/*");
        }
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        ClipData cd = ClipData.newRawUri("", uris.get(0));
        for (int k = 1; k < uris.size(); k++) cd.addItem(new ClipData.Item(uris.get(k)));
        i.setClipData(cd);
        try {
            startActivity(Intent.createChooser(i, Tr.t("share")));
        } catch (Exception e) {
            toast("?");
        }
    }

    // ---- Suppression ----

    static class Res {
        int ok, fail;
        long freed;
        final ArrayList<Scan.Item> done = new ArrayList<>();
    }

    void rescanMedia(List<Scan.Item> items) {
        ArrayList<String> p = new ArrayList<>();
        for (Scan.Item it : items) if (!it.dir && Scan.cat(it.name()) <= 2) p.add(it.path);
        if (!p.isEmpty() && p.size() <= 500) {
            try {
                MediaScannerConnection.scanFile(this, p.toArray(new String[0]), null, null);
            } catch (Exception e) {
                // ignoré
            }
        }
    }

    /** À appeler hors du fil d'interface. */
    Res deleteItems(String fkey, List<Scan.Item> chosen, boolean toTrash) {
        Res r = new Res();
        for (Scan.Item it : chosen) {
            if (Scan.isExcluded(it.path)) {
                r.fail++;
                continue;
            }
            File f = new File(it.path);
            boolean ok;
            if (!f.exists()) ok = true;
            else if (f.isDirectory()) ok = f.delete();
            else if (toTrash) ok = trash.move(f);
            else ok = f.delete();
            if (ok) {
                r.ok++;
                r.freed += it.size;
                r.done.add(it);
            } else {
                r.fail++;
            }
        }
        if (r.ok > 0) trash.log(fkey, r.ok, r.freed, toTrash ? "T" : "D");
        rescanMedia(r.done);
        return r;
    }

    boolean directOnly(String fkey) {
        return fkey.equals("f_empty") || fkey.equals("f_backups");
    }

    void confirmDelete(final String fkey, final ArrayList<Scan.Item> list, final Runnable after) {
        final ArrayList<Scan.Item> chosen = new ArrayList<>();
        long s = 0;
        for (Scan.Item it : list) {
            if (!it.hdr && it.sel) {
                chosen.add(it);
                s += it.size;
            }
        }
        if (chosen.isEmpty()) return;
        final boolean toTrash = st.useTrash() && !directOnly(fkey);
        String msg = Tr.f(toTrash ? "confirm_trash" : "confirm_del", chosen.size(), Tr.size(s));
        if (st.zipBackup() && !directOnly(fkey)) msg += "\n\n" + Tr.t("zip_note");
        new AlertDialog.Builder(this).setTitle(Tr.t("confirm")).setMessage(msg)
                .setPositiveButton(Tr.t("yes"), (d, w) -> doDelete(fkey, chosen, toTrash, after))
                .setNegativeButton(Tr.t("no"), null).show();
    }

    void doDelete(final String fkey, final ArrayList<Scan.Item> chosen, final boolean toTrash, final Runnable after) {
        final AlertDialog dlg = busy(Tr.t("working"));
        final boolean zip = st.zipBackup() && !directOnly(fkey);
        bg.execute(() -> {
            File z = null;
            if (zip) {
                ArrayList<String> ps = new ArrayList<>();
                for (Scan.Item it : chosen) if (!it.dir) ps.add(it.path);
                if (!ps.isEmpty()) {
                    z = Trash.backupZip(ps);
                    if (z == null) {
                        ui.post(() -> {
                            dlg.dismiss();
                            toast(Tr.t("zip_fail"));
                        });
                        return;
                    }
                }
            }
            final File zf = z;
            final Res r = deleteItems(fkey, chosen, toTrash);
            ui.post(() -> {
                scan.removed(r.done);
                dlg.dismiss();
                String m = Tr.f("done", r.ok, Tr.size(r.freed));
                if (r.fail > 0) m += " — " + Tr.f("failed", r.fail);
                if (zf != null) m += "\n" + Tr.f("zip_ok", zf.getName());
                toast(m);
                after.run();
            });
        });
    }

    // ---- Compression ----

    void confirmCompress(final ArrayList<Scan.Item> list, final Runnable after) {
        final ArrayList<Scan.Item> chosen = new ArrayList<>();
        for (Scan.Item it : list) if (!it.hdr && it.sel) chosen.add(it);
        if (chosen.isEmpty()) return;
        new AlertDialog.Builder(this).setTitle(Tr.f("compress_title", chosen.size()))
                .setItems(new String[]{Tr.t("q_high"), Tr.t("q_mid")}, (d, which) ->
                        doCompress(chosen, which == 0 ? 85 : 70, which == 0 ? 3000 : 2048, after))
                .setNegativeButton(Tr.t("cancel"), null).show();
    }

    void doCompress(final ArrayList<Scan.Item> chosen, final int quality, final int maxSide, final Runnable after) {
        final AlertDialog dlg = busy(Tr.t("working"));
        final boolean keep = st.useTrash();
        bg.execute(() -> {
            int ok = 0, skip = 0;
            long saved = 0;
            final ArrayList<Scan.Item> touched = new ArrayList<>();
            final ArrayList<long[]> sizes = new ArrayList<>();
            for (Scan.Item it : chosen) {
                File f = new File(it.path);
                if (!f.isFile() || Scan.isExcluded(it.path)) {
                    skip++;
                    continue;
                }
                long old = f.length();
                File tmp = Photos.compressTo(f, quality, maxSide);
                if (tmp == null) {
                    skip++;
                    continue;
                }
                long nw = tmp.length();
                if (nw >= old * 0.9) {
                    tmp.delete();
                    skip++;
                    continue;
                }
                if (keep && !trash.copyIn(f)) {
                    tmp.delete();
                    skip++;
                    continue;
                }
                if (tmp.renameTo(f)) {
                    ok++;
                    saved += old - nw;
                    touched.add(it);
                    sizes.add(new long[]{nw});
                } else {
                    tmp.delete();
                    skip++;
                }
            }
            if (ok > 0) trash.log("f_compress", ok, saved, "C");
            rescanMedia(touched);
            final int fo = ok, fs = skip;
            final long fsaved = saved;
            ui.post(() -> {
                for (int i = 0; i < touched.size(); i++) scan.resized(touched.get(i), sizes.get(i)[0]);
                dlg.dismiss();
                toast(Tr.f("compressed", fo, Tr.size(fsaved), fs));
                after.run();
            });
        });
    }

    // =====================================================================
    // Nettoyage rapide
    // =====================================================================

    void quickClean() {
        final AlertDialog dlg = busy(Tr.t("working"));
        bg.execute(() -> {
            final LinkedHashMap<String, ArrayList<Scan.Item>> set = scan.quickSet(getPackageManager());
            ui.post(() -> {
                dlg.dismiss();
                showQuick(set);
            });
        });
    }

    void showQuick(final LinkedHashMap<String, ArrayList<Scan.Item>> set) {
        if (set.isEmpty()) {
            toast(Tr.t("quick_none"));
            return;
        }
        long tot = 0;
        StringBuilder sb = new StringBuilder();
        for (java.util.Map.Entry<String, ArrayList<Scan.Item>> e : set.entrySet()) {
            long s = Scan.sum(e.getValue());
            tot += s;
            sb.append("• ").append(Tr.t(e.getKey())).append(" : ").append(e.getValue().size())
                    .append(" · ").append(Tr.size(s)).append("\n");
        }
        String msg = Tr.f("quick_msg", Tr.size(tot)) + "\n\n" + sb + "\n" + Tr.t("quick_what") + "\n\n" + Tr.t("quick_ask");
        new AlertDialog.Builder(this).setTitle(Tr.t("quick_title")).setMessage(msg)
                .setPositiveButton(Tr.t("yes"), (d, w) -> runQuick(set))
                .setNegativeButton(Tr.t("no"), null).show();
    }

    void runQuick(final LinkedHashMap<String, ArrayList<Scan.Item>> set) {
        final AlertDialog dlg = busy(Tr.t("working"));
        final boolean useTrash = st.useTrash();
        bg.execute(() -> {
            int ok = 0, fail = 0;
            long freed = 0;
            final ArrayList<Scan.Item> all = new ArrayList<>();
            for (java.util.Map.Entry<String, ArrayList<Scan.Item>> e : set.entrySet()) {
                Res r = deleteItems(e.getKey(), e.getValue(), useTrash && !directOnly(e.getKey()));
                ok += r.ok;
                fail += r.fail;
                freed += r.freed;
                all.addAll(r.done);
            }
            final int fo = ok, ff = fail;
            final long fb = freed;
            ui.post(() -> {
                scan.removed(all);
                dlg.dismiss();
                String m = Tr.f("done", fo, Tr.size(fb));
                if (ff > 0) m += " — " + Tr.f("failed", ff);
                toast(m);
                cur.run();
            });
        });
    }

    // =====================================================================
    // Dossiers les plus lourds
    // =====================================================================

    void folders() {
        LinearLayout page = scaffold(Tr.t("f_folders"), true, null);
        long total;
        if (foldPath.equals(ROOT)) {
            total = scan.nBytes;
        } else {
            Long t = scan.dirBytes.get(foldPath);
            total = t == null ? 0 : t;
        }
        String shown = foldPath.length() > ROOT.length() ? foldPath.substring(ROOT.length()) : "/";
        page.addView(note(shown + " · " + Tr.size(total) + "\n" + Tr.t("fold_info")));
        ScrollView sv = new ScrollView(this);
        LinearLayout b = vbox();
        b.setPadding(dp(14), dp(4), dp(14), dp(20));
        sv.addView(b);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        ArrayList<Scan.Item> ch = scan.childrenOf.get(foldPath);
        long kids = 0;
        if (ch != null) {
            for (Scan.Item c : ch) {
                kids += c.size;
                b.addView(folderRow(c, total), lpw(8));
            }
        }
        long own = total - kids;
        if (own > 0) {
            LinearLayout row = hbox();
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            row.setBackground(rr(CARD, 14));
            row.addView(tv("📄 " + Tr.t("fold_files"), 14, SUB, false), new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(tv(Tr.size(own), 14, SUB, true));
            b.addView(row, lpw(8));
        }
        if (ch == null && own <= 0) b.addView(tv(Tr.t("empty_list"), 15, SUB, false));
    }

    View folderRow(final Scan.Item c, long total) {
        LinearLayout row = vbox();
        row.setPadding(dp(14), dp(12), dp(14), dp(8));
        row.setBackground(rr(CARD, 14));
        LinearLayout top = hbox();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView n = tv("📁 " + c.name(), 15, TXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        top.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        top.addView(tv(Tr.size(c.size), 14, ACC, true));
        row.addView(top);
        row.addView(bar(new long[]{c.size, Math.max(total - c.size, 0)}, new int[]{ACC, CARD2}), barLp());
        row.setOnClickListener(v -> {
            if (scan.childrenOf.containsKey(c.path)) {
                foldPath = c.path;
                folders();
            }
        });
        row.setOnLongClickListener(v -> {
            askExclude(c.path);
            return true;
        });
        return row;
    }

    void askExclude(final String path) {
        new AlertDialog.Builder(this).setTitle(Tr.t("confirm"))
                .setMessage(Tr.t("excl_ask") + "\n" + path)
                .setPositiveButton(Tr.t("yes"), (d, w) -> {
                    Set<String> s = st.excluded();
                    s.add(path);
                    st.setExcluded(s);
                    Scan.EXCL = st.excluded();
                    toast(Tr.t("excl_done"));
                })
                .setNegativeButton(Tr.t("no"), null).show();
    }

    // =====================================================================
    // Exclusions
    // =====================================================================

    void exclusions() {
        LinearLayout page = scaffold(Tr.t("excl_title"), true, null);
        ScrollView sv = new ScrollView(this);
        LinearLayout b = vbox();
        b.setPadding(dp(14), dp(4), dp(14), dp(24));
        sv.addView(b);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        b.addView(note(Tr.t("excl_info")));
        ArrayList<String> sorted = new ArrayList<>(st.excluded());
        Collections.sort(sorted);
        if (sorted.isEmpty()) {
            TextView t = tv(Tr.t("excl_none"), 14, SUB, false);
            t.setPadding(dp(14), dp(6), dp(14), dp(6));
            b.addView(t);
        }
        for (final String p : sorted) {
            LinearLayout row = hbox();
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(8), dp(8), dp(8));
            row.setBackground(rr(CARD, 14));
            String shown = p.startsWith(ROOT) && p.length() > ROOT.length() ? p.substring(ROOT.length()) : p;
            TextView t = tv("📁 " + shown, 14, TXT, false);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            row.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
            row.addView(btn(Tr.t("excl_remove"), CARD2, TXT, v -> {
                Set<String> s = st.excluded();
                s.remove(p);
                st.setExcluded(s);
                Scan.EXCL = st.excluded();
                cur.run();
            }));
            b.addView(row, lpw(8));
        }
        b.addView(btn(Tr.t("excl_add"), ACC, BG, v -> {
            pickPath = ROOT;
            nav(pickRun);
        }), lpw(14));
    }

    void picker() {
        String shown = pickPath.length() > ROOT.length() ? pickPath.substring(ROOT.length()) : "/";
        LinearLayout page = scaffold(shown, true, null);
        LinearLayout.LayoutParams lp = lpw(8);
        lp.leftMargin = dp(14);
        lp.rightMargin = dp(14);
        page.addView(btn(Tr.t("excl_pick"), GRN, BG, v -> {
            Set<String> s = st.excluded();
            s.add(pickPath);
            st.setExcluded(s);
            Scan.EXCL = st.excluded();
            toast(Tr.t("excl_done"));
            pickPath = ROOT;
            onBackPressed();
        }), lp);
        ScrollView sv = new ScrollView(this);
        LinearLayout b = vbox();
        b.setPadding(dp(14), dp(4), dp(14), dp(20));
        sv.addView(b);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        ArrayList<File> ds = new ArrayList<>();
        File[] all = new File(pickPath).listFiles();
        if (all != null) for (File f : all) if (f.isDirectory()) ds.add(f);
        Collections.sort(ds, (x, y) -> x.getName().compareToIgnoreCase(y.getName()));
        if (ds.isEmpty()) {
            TextView t = tv(Tr.t("pick_empty"), 14, SUB, false);
            t.setPadding(dp(4), dp(10), dp(4), dp(10));
            b.addView(t);
        }
        for (final File f : ds) {
            TextView t = tv("📁 " + f.getName(), 15, TXT, false);
            t.setPadding(dp(14), dp(12), dp(14), dp(12));
            t.setBackground(rr(CARD, 12));
            t.setOnClickListener(v -> {
                pickPath = f.getAbsolutePath();
                picker();
            });
            b.addView(t, lpw(6));
        }
    }

    // =====================================================================
    // Rappel périodique
    // =====================================================================

    void applyReminder(int days) {
        try {
            JobScheduler js = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
            js.cancel(7);
            if (days > 0) {
                js.schedule(new JobInfo.Builder(7, new ComponentName(this, ReminderJob.class))
                        .setPeriodic(days * 86400000L)
                        .setPersisted(true)
                        .build());
            }
        } catch (Exception e) {
            toast("?");
        }
    }

    // ---- Adaptateur de liste avec miniatures ----

    class Row {
        LinearLayout root;
        CheckBox cb;
        TextView emoji, name, sub, size;
        ImageView img;

        View build() {
            root = hbox();
            root.setGravity(Gravity.CENTER_VERTICAL);
            root.setPadding(dp(10), dp(8), dp(10), dp(8));
            cb = new CheckBox(MainActivity.this);
            cb.setFocusable(false);
            cb.setClickable(false);
            cb.setButtonTintList(ColorStateList.valueOf(ACC));
            root.addView(cb);
            FrameLayout th = new FrameLayout(MainActivity.this);
            th.setBackground(rr(CARD, 8));
            th.setClipToOutline(true);
            emoji = tv("", 20, TXT, false);
            emoji.setGravity(Gravity.CENTER);
            th.addView(emoji, new FrameLayout.LayoutParams(-1, -1));
            img = new ImageView(MainActivity.this);
            img.setScaleType(ImageView.ScaleType.CENTER_CROP);
            th.addView(img, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(dp(46), dp(46));
            tp.setMarginEnd(dp(10));
            root.addView(th, tp);
            LinearLayout col = vbox();
            name = tv("", 14, TXT, false);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            sub = tv("", 11, SUB, false);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.MIDDLE);
            col.addView(name);
            col.addView(sub);
            root.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
            size = tv("", 13, ACC, true);
            size.setPaddingRelative(dp(8), 0, 0, 0);
            root.addView(size);
            return root;
        }
    }

    class FileAdapter extends BaseAdapter {
        final ArrayList<Scan.Item> data;

        FileAdapter(ArrayList<Scan.Item> d) {
            data = d;
        }

        @Override public int getCount() { return data.size(); }
        @Override public Object getItem(int i) { return data.get(i); }
        @Override public long getItemId(int i) { return i; }
        @Override public int getViewTypeCount() { return 2; }
        @Override public int getItemViewType(int i) { return data.get(i).hdr ? 1 : 0; }
        @Override public boolean isEnabled(int i) { return !data.get(i).hdr; }

        @Override
        public View getView(int pos, View cv, ViewGroup parent) {
            Scan.Item it = data.get(pos);
            if (it.hdr) {
                TextView h;
                if (cv instanceof TextView) {
                    h = (TextView) cv;
                } else {
                    h = tv("", 13, ACC, true);
                    h.setPadding(dp(14), dp(12), dp(14), dp(6));
                    h.setBackgroundColor(CARD);
                }
                h.setText(Tr.f(it.hk, it.gnum, it.gcount, Tr.size(it.gsize)));
                return h;
            }
            Row r;
            View v;
            if (cv != null && cv.getTag() instanceof Row) {
                v = cv;
                r = (Row) cv.getTag();
            } else {
                r = new Row();
                v = r.build();
                v.setTag(r);
            }
            r.cb.setChecked(it.sel);
            String disp = it.orig != null ? it.orig : it.path;
            int slash = disp.lastIndexOf('/');
            String parentPath = slash > 0 ? disp.substring(0, slash) : "/";
            parentPath = parentPath.replace("/storage/emulated/0", "");
            if (parentPath.isEmpty()) parentPath = "/";
            r.name.setText(it.name());
            r.sub.setText((it.mtime > 0 ? Tr.date(it.mtime) + " · " : "") + parentPath);
            r.size.setText(it.size > 0 ? Tr.size(it.size) : "");
            int c = Scan.cat(it.name());
            r.emoji.setText(it.dir ? "📂" : CATE[c]);
            loadThumb(r.img, it.path, c);
            return v;
        }
    }

    void loadThumb(final ImageView iv, final String path, final int cat) {
        iv.setTag(path);
        iv.setImageBitmap(null);
        if (cat != 0 && cat != 1) return;
        Bitmap b = thumbs.get(path);
        if (b != null) {
            iv.setImageBitmap(b);
            return;
        }
        thumbEx.execute(() -> {
            if (!path.equals(iv.getTag())) return;
            final Bitmap t = makeThumb(path, cat);
            if (t == null) return;
            thumbs.put(path, t);
            ui.post(() -> {
                if (path.equals(iv.getTag())) iv.setImageBitmap(t);
            });
        });
    }

    Bitmap makeThumb(String path, int cat) {
        try {
            if (cat == 0) {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(path, o);
                int s = 1;
                int m = Math.max(o.outWidth, o.outHeight);
                while (m / (s * 2) >= 128) s *= 2;
                BitmapFactory.Options o2 = new BitmapFactory.Options();
                o2.inSampleSize = s;
                return BitmapFactory.decodeFile(path, o2);
            } else if (cat == 1) {
                return ThumbnailUtils.createVideoThumbnail(path, MediaStore.Images.Thumbnails.MINI_KIND);
            }
        } catch (Throwable e) {
            // ignoré
        }
        return null;
    }

    // =====================================================================
    // Applications rarement utilisées
    // =====================================================================

    static class AppRow {
        String label, pkg;
        long last, apk;
        Drawable icon;
    }

    boolean usageGranted() {
        try {
            AppOpsManager a = (AppOpsManager) getSystemService(Context.APP_OPS_SERVICE);
            int m = a.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), getPackageName());
            return m == AppOpsManager.MODE_ALLOWED;
        } catch (Exception e) {
            return false;
        }
    }

    void apps() {
        LinearLayout page = scaffold(Tr.t("f_apps"), true, null);
        if (!usageGranted()) {
            page.addView(note(Tr.t("usage_need")));
            Button g = btn(Tr.t("grant"), ACC, BG, v -> {
                needRefresh = true;
                try {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                } catch (Exception e) {
                    toast("Settings ?");
                }
            });
            LinearLayout.LayoutParams lp = lpw(10);
            lp.leftMargin = dp(14);
            lp.rightMargin = dp(14);
            page.addView(g, lp);
            return;
        }
        page.addView(note(Tr.t("apps_hint")));
        final TextView load = tv(Tr.t("apps_loading"), 14, SUB, false);
        load.setPadding(dp(14), dp(10), dp(14), dp(10));
        page.addView(load);
        ScrollView sv = new ScrollView(this);
        final LinearLayout list = vbox();
        list.setPadding(dp(14), dp(4), dp(14), dp(20));
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        bg.execute(() -> {
            final ArrayList<AppRow> rows = loadApps();
            ui.post(() -> {
                load.setVisibility(View.GONE);
                for (AppRow r : rows) list.addView(appView(r), lpw(8));
            });
        });
    }

    ArrayList<AppRow> loadApps() {
        ArrayList<AppRow> rows = new ArrayList<>();
        PackageManager pm = getPackageManager();
        long now = System.currentTimeMillis();
        Map<String, UsageStats> m = null;
        try {
            UsageStatsManager u = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            m = u.queryAndAggregateUsageStats(now - 180L * 86400000L, now);
        } catch (Exception e) {
            // ignoré
        }
        List<ApplicationInfo> l = pm.getInstalledApplications(0);
        for (ApplicationInfo ai : l) {
            if ((ai.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (ai.packageName.equals(getPackageName())) continue;
            AppRow r = new AppRow();
            r.pkg = ai.packageName;
            r.label = String.valueOf(pm.getApplicationLabel(ai));
            UsageStats us = m == null ? null : m.get(ai.packageName);
            r.last = us == null ? 0 : us.getLastTimeUsed();
            r.apk = ai.sourceDir != null ? new File(ai.sourceDir).length() : 0;
            r.icon = pm.getApplicationIcon(ai);
            rows.add(r);
        }
        Collections.sort(rows, (a, b) -> a.last != b.last ? Long.compare(a.last, b.last) : Long.compare(b.apk, a.apk));
        return rows;
    }

    View appView(final AppRow r) {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(rr(CARD, 14));
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(r.icon);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40));
        ip.setMarginEnd(dp(12));
        row.addView(ic, ip);
        LinearLayout col = vbox();
        TextView n = tv(r.label, 15, TXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        col.addView(n);
        col.addView(tv(r.last > 0 ? Tr.f("last_used", Tr.date(r.last)) : Tr.t("never_used"), 12, r.last > 0 ? SUB : ACC, false));
        row.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView sz = tv(Tr.size(r.apk), 12, SUB, false);
        sz.setPaddingRelative(dp(8), 0, dp(8), 0);
        row.addView(sz);
        row.addView(tv("🗑️", 20, TXT, false));
        row.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setMessage(Tr.f("uninstall_q", r.label))
                .setPositiveButton(Tr.t("yes"), (d, w) -> {
                    needRefresh = true;
                    startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + r.pkg)));
                })
                .setNegativeButton(Tr.t("no"), null).show());
        return row;
    }

    // =====================================================================
    // Cache
    // =====================================================================

    void openStorageSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_INTERNAL_STORAGE_SETTINGS));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e2) {
                toast("Settings ?");
            }
        }
    }

    void cacheScreen() {
        LinearLayout page = scaffold(Tr.t("f_cache"), true, null);
        ScrollView sv = new ScrollView(this);
        LinearLayout b = vbox();
        b.setPadding(dp(14), dp(4), dp(14), dp(24));
        sv.addView(b);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        b.addView(note(Tr.t("cache_info")));

        LinearLayout c1 = card();
        long sz = Scan.dirSize(getCacheDir()) + Scan.dirSize(getExternalCacheDir());
        c1.addView(tv(Tr.f("cache_app", Tr.size(sz)), 15, TXT, true));
        c1.addView(btn(Tr.t("cache_clear"), ACC, BG, v -> {
            long fr = Scan.deleteContents(getCacheDir()) + Scan.deleteContents(getExternalCacheDir());
            if (fr > 0) trash.log("f_cache", 1, fr, "D");
            toast(Tr.f("done", 1, Tr.size(fr)));
            cur.run();
        }), lpw(10));
        b.addView(c1, lpw(8));

        LinearLayout c2 = card();
        TextView d = tv(Tr.t("cache_sys_d"), 13, SUB, false);
        c2.addView(d);
        c2.addView(btn("🧺 " + Tr.t("f_appcache"), ACC, BG, v -> nav(this::appCache)), lpw(10));
        c2.addView(btn(Tr.t("cache_sys"), GRN, BG, v -> {
            try {
                if (Build.VERSION.SDK_INT >= 26) {
                    startActivity(new Intent(StorageManager.ACTION_CLEAR_APP_CACHE));
                } else {
                    openStorageSettings();
                }
            } catch (Exception e) {
                openStorageSettings();
            }
        }), lpw(10));
        c2.addView(btn(Tr.t("open_storage"), CARD2, TXT, v -> openStorageSettings()), lpw(8));
        b.addView(c2, lpw(10));
    }


    // =====================================================================
    // Cache des applications (vidage manuel via les réglages d'Android)
    // =====================================================================

    static class CacheRow {
        String label, pkg;
        long cache;
        Drawable icon;
    }

    ArrayList<CacheRow> loadCaches() {
        ArrayList<CacheRow> rows = new ArrayList<>();
        if (Build.VERSION.SDK_INT < 26) return rows;
        PackageManager pm = getPackageManager();
        StorageStatsManager ssm = (StorageStatsManager) getSystemService(Context.STORAGE_STATS_SERVICE);
        if (ssm == null) return rows;
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            try {
                long c = ssm.queryStatsForPackage(StorageManager.UUID_DEFAULT, ai.packageName, Process.myUserHandle()).getCacheBytes();
                if (c <= 0) continue;
                CacheRow r = new CacheRow();
                r.pkg = ai.packageName;
                r.cache = c;
                r.label = String.valueOf(pm.getApplicationLabel(ai));
                r.icon = pm.getApplicationIcon(ai);
                rows.add(r);
            } catch (Exception e) {
                // application inaccessible : ignorée
            }
        }
        Collections.sort(rows, (a, b) -> Long.compare(b.cache, a.cache));
        return rows;
    }

    void appCache() {
        LinearLayout page = scaffold(Tr.t("f_appcache"), true, null);
        if (!usageGranted()) {
            page.addView(note(Tr.t("usage_need")));
            Button g = btn(Tr.t("grant"), ACC, BG, v -> {
                needRefresh = true;
                try {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                } catch (Exception e) {
                    toast("Settings ?");
                }
            });
            LinearLayout.LayoutParams lp = lpw(10);
            lp.leftMargin = dp(14);
            lp.rightMargin = dp(14);
            page.addView(g, lp);
            return;
        }
        if (Build.VERSION.SDK_INT < 26) {
            page.addView(note(Tr.t("appcache_unavail")));
            return;
        }
        page.addView(note(Tr.t("appcache_hint")));
        final TextView total = tv(Tr.t("apps_loading"), 15, ACC, true);
        total.setPadding(dp(14), 0, dp(14), dp(6));
        page.addView(total);
        ScrollView sv = new ScrollView(this);
        final LinearLayout list = vbox();
        list.setPadding(dp(14), dp(4), dp(14), dp(20));
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        bg.execute(() -> {
            final ArrayList<CacheRow> rows = loadCaches();
            long sum = 0;
            for (CacheRow r : rows) sum += r.cache;
            final long fsum = sum;
            ui.post(() -> {
                total.setText(rows.isEmpty() ? Tr.t("appcache_none") : Tr.f("appcache_total", Tr.size(fsum)));
                for (CacheRow r : rows) list.addView(cacheView(r), lpw(8));
            });
        });
    }

    View cacheView(final CacheRow r) {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(rr(CARD, 14));
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(r.icon);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40));
        ip.setMarginEnd(dp(12));
        row.addView(ic, ip);
        TextView n = tv(r.label, 15, TXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView sz = tv(Tr.size(r.cache), 14, ACC, true);
        sz.setPaddingRelative(dp(8), 0, 0, 0);
        row.addView(sz);
        row.setOnClickListener(v -> {
            needRefresh = true;
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + r.pkg)));
            } catch (Exception e) {
                toast("Settings ?");
            }
        });
        return row;
    }

    // =====================================================================
    // Optimiseur
    // =====================================================================

    void openSetting(String action) {
        try {
            startActivity(new Intent(action));
        } catch (Exception e) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
            } catch (Exception e2) {
                toast(Tr.t("setting_fail"));
            }
        }
    }

    void metric(LinearLayout parent, String label, String value) {
        LinearLayout r = hbox();
        r.setPadding(0, dp(4), 0, dp(4));
        r.addView(tv(label, 14, SUB, false), new LinearLayout.LayoutParams(0, -2, 1f));
        r.addView(tv(value, 14, TXT, true));
        parent.addView(r);
    }

    void optimizer() {
        LinearLayout page = scaffold(Tr.t("f_opt"), true, null);
        ScrollView sv = new ScrollView(this);
        LinearLayout b = vbox();
        b.setPadding(dp(14), dp(12), dp(14), dp(24));
        sv.addView(b);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        StatFs sf = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long total = sf.getTotalBytes();
        long free = sf.getAvailableBytes();
        int freePct = (int) (free * 100 / Math.max(total, 1));

        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(mi);

        int level = -1, temp = -1, health = BatteryManager.BATTERY_HEALTH_GOOD;
        Intent bi = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (bi != null) {
            int lv = bi.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int sc = bi.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
            level = lv < 0 ? -1 : lv * 100 / Math.max(sc, 1);
            int tr = bi.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
            temp = tr < 0 ? -1 : tr / 10;
            health = bi.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_GOOD);
        }
        long up = SystemClock.elapsedRealtime();
        int days = (int) (up / 86400000L);

        int score = 100;
        ArrayList<String> adv = new ArrayList<>();
        if (freePct < 10) {
            score -= 35;
            adv.add(Tr.t("adv_storage"));
        } else if (freePct < 20) {
            score -= 20;
            adv.add(Tr.t("adv_storage"));
        } else if (freePct < 30) {
            score -= 8;
        }
        if (mi.lowMemory) {
            score -= 15;
            adv.add(Tr.t("adv_ram"));
        }
        if (days >= 14) {
            score -= 15;
            adv.add(Tr.f("adv_up", days));
        } else if (days >= 7) {
            score -= 8;
            adv.add(Tr.f("adv_up", days));
        }
        if (temp >= 40) {
            score -= 15;
            adv.add(Tr.f("adv_temp", temp));
        } else if (temp >= 37) {
            score -= 5;
        }
        if (health != BatteryManager.BATTERY_HEALTH_GOOD && health != BatteryManager.BATTERY_HEALTH_UNKNOWN) {
            score -= 10;
            adv.add(Tr.t("adv_health"));
        }
        score = Math.max(0, score);
        if (adv.isEmpty()) adv.add(Tr.t("adv_ok"));
        int sc = score >= 80 ? GRN : (score >= 60 ? ACC : RED);

        LinearLayout c = card();
        c.addView(tv(Tr.t("opt_score"), 13, SUB, false));
        c.addView(tv(score + " / 100 · " + Tr.t(score >= 80 ? "score_good" : (score >= 60 ? "score_mid" : "score_low")), 24, sc, true));
        c.addView(bar(new long[]{score, 100 - score}, new int[]{sc, CARD2}), barLp());
        b.addView(c, lpw(0));

        LinearLayout m = card();
        metric(m, Tr.t("m_storage"), freePct + " % · " + Tr.size(free));
        metric(m, Tr.t("m_ram"), Tr.size(mi.availMem) + " / " + Tr.size(mi.totalMem));
        metric(m, Tr.t("m_batt"), (level >= 0 ? level + " %" : "—") + (temp >= 0 ? " · " + temp + " °C" : ""));
        metric(m, Tr.t("m_up"), Tr.f("up_fmt", days, (int) ((up / 3600000L) % 24)));
        b.addView(m, lpw(10));

        LinearLayout a = card();
        a.addView(tv(Tr.t("opt_adv"), 15, TXT, true));
        for (String x : adv) {
            TextView t = tv("• " + x, 13, SUB, false);
            t.setPadding(0, dp(4), 0, 0);
            a.addView(t);
        }
        b.addView(a, lpw(10));

        b.addView(btn(Tr.t("opt_all"), ACC, BG, v -> optimizeAll()), lpw(12));
        menu(b, "🔥", "f_hungry", Tr.t("s_hungry"), () -> nav(this::hungry));
        menu(b, "🛠️", "f_tips", Tr.t("s_tips"), () -> nav(this::tips));
    }

    /** Vide le cache de l'application puis lance le nettoyage rapide (après analyse si besoin). */
    void optimizeAll() {
        if (!hasAccess()) {
            askAccess();
            return;
        }
        long fr = Scan.deleteContents(getCacheDir()) + Scan.deleteContents(getExternalCacheDir());
        if (fr > 0) trash.log("f_cache", 1, fr, "D");
        toast(Tr.f("opt_cache", Tr.size(fr)));
        if (scan.scanned) {
            quickClean();
        } else {
            optAfterScan = true;
            startScan();
        }
    }

    // ---- Applications gourmandes ----

    static class TimeRow {
        String label, pkg;
        long ms;
        Drawable icon;
    }

    ArrayList<TimeRow> loadHungry() {
        ArrayList<TimeRow> rows = new ArrayList<>();
        PackageManager pm = getPackageManager();
        long now = System.currentTimeMillis();
        try {
            UsageStatsManager u = (UsageStatsManager) getSystemService(Context.USAGE_STATS_SERVICE);
            Map<String, UsageStats> m = u.queryAndAggregateUsageStats(now - 7L * 86400000L, now);
            for (Map.Entry<String, UsageStats> e : m.entrySet()) {
                long t = e.getValue().getTotalTimeInForeground();
                String pkg = e.getKey();
                if (t < 60000 || pkg.equals(getPackageName())) continue;
                if (pm.getLaunchIntentForPackage(pkg) == null) continue;
                try {
                    ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                    TimeRow r = new TimeRow();
                    r.pkg = pkg;
                    r.ms = t;
                    r.label = String.valueOf(pm.getApplicationLabel(ai));
                    r.icon = pm.getApplicationIcon(ai);
                    rows.add(r);
                } catch (Exception ex) {
                    // application disparue : ignorée
                }
            }
        } catch (Exception e) {
            // statistiques indisponibles
        }
        Collections.sort(rows, (x, y) -> Long.compare(y.ms, x.ms));
        return rows;
    }

    void hungry() {
        LinearLayout page = scaffold(Tr.t("f_hungry"), true, null);
        if (!usageGranted()) {
            page.addView(note(Tr.t("usage_need")));
            Button g = btn(Tr.t("grant"), ACC, BG, v -> {
                needRefresh = true;
                try {
                    startActivity(new Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS));
                } catch (Exception e) {
                    toast(Tr.t("setting_fail"));
                }
            });
            LinearLayout.LayoutParams lp = lpw(10);
            lp.leftMargin = dp(14);
            lp.rightMargin = dp(14);
            page.addView(g, lp);
            return;
        }
        page.addView(note(Tr.t("hungry_hint")));
        final TextView load = tv(Tr.t("apps_loading"), 14, SUB, false);
        load.setPadding(dp(14), dp(6), dp(14), dp(6));
        page.addView(load);
        ScrollView sv = new ScrollView(this);
        final LinearLayout list = vbox();
        list.setPadding(dp(14), dp(4), dp(14), dp(20));
        sv.addView(list);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        bg.execute(() -> {
            final ArrayList<TimeRow> rows = loadHungry();
            ui.post(() -> {
                load.setText(rows.isEmpty() ? Tr.t("hungry_none") : "");
                if (!rows.isEmpty()) load.setVisibility(View.GONE);
                for (TimeRow r : rows) list.addView(timeView(r), lpw(8));
            });
        });
    }

    View timeView(final TimeRow r) {
        LinearLayout row = hbox();
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(10), dp(12), dp(10));
        row.setBackground(rr(CARD, 14));
        ImageView ic = new ImageView(this);
        ic.setImageDrawable(r.icon);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(dp(40), dp(40));
        ip.setMarginEnd(dp(12));
        row.addView(ic, ip);
        TextView n = tv(r.label, 15, TXT, true);
        n.setSingleLine(true);
        n.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(n, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView sz = tv(Tr.dur(r.ms), 14, ACC, true);
        sz.setPaddingRelative(dp(8), 0, 0, 0);
        row.addView(sz);
        row.setOnClickListener(v -> {
            needRefresh = true;
            try {
                startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + r.pkg)));
            } catch (Exception e) {
                toast(Tr.t("setting_fail"));
            }
        });
        return row;
    }

    // ---- Conseils et raccourcis ----

    void tipCard(LinearLayout parent, String titleKey, String descKey, String[][] buttons) {
        LinearLayout c = card();
        c.addView(tv(Tr.t(titleKey), 16, TXT, true));
        TextView d = tv(Tr.t(descKey), 13, SUB, false);
        d.setPadding(0, dp(4), 0, dp(4));
        c.addView(d);
        for (final String[] bt : buttons) {
            c.addView(btn(Tr.t(bt[0]), CARD2, TXT, v -> openSetting(bt[1])), lpw(8));
        }
        parent.addView(c, lpw(10));
    }

    void tips() {
        LinearLayout page = scaffold(Tr.t("f_tips"), true, null);
        ScrollView sv = new ScrollView(this);
        LinearLayout b = vbox();
        b.setPadding(dp(14), dp(4), dp(14), dp(24));
        sv.addView(b);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        tipCard(b, "tips_batt", "tips_batt_d", new String[][]{
                {"b_opt", "android.settings.IGNORE_BATTERY_OPTIMIZATION_SETTINGS"},
                {"b_saver", "android.settings.BATTERY_SAVER_SETTINGS"},
                {"b_display", "android.settings.DISPLAY_SETTINGS"}});
        tipCard(b, "tips_smooth", "tips_smooth_d", new String[][]{
                {"b_about", "android.settings.DEVICE_INFO_SETTINGS"},
                {"b_dev", "android.settings.APPLICATION_DEVELOPMENT_SETTINGS"}});
        tipCard(b, "tips_data", "tips_data_d", new String[][]{
                {"b_data", "android.settings.DATA_USAGE_SETTINGS"}});
    }

    // =====================================================================
    // Corbeille
    // =====================================================================

    void trashScreen() {
        LinearLayout page = scaffold(Tr.t("f_trash"), true, null);
        page.addView(note(Tr.f("trash_info", st.purgeDays())));
        final ArrayList<Scan.Item> items = new ArrayList<>();
        for (Trash.Entry e : trash.list()) {
            Scan.Item it = new Scan.Item(new File(trash.dir, e.name).getAbsolutePath(), e.size, e.when);
            it.orig = e.orig;
            it.ref = e;
            items.add(it);
        }
        Collections.sort(items, (a, b) -> Long.compare(b.mtime, a.mtime));

        final FileAdapter ad = new FileAdapter(items);
        final ListView lv = new ListView(this);
        lv.setDivider(null);
        lv.setAdapter(ad);
        final TextView empty = tv(Tr.t("empty_list"), 15, SUB, false);
        empty.setGravity(Gravity.CENTER);
        FrameLayout fl = new FrameLayout(this);
        fl.addView(lv, new FrameLayout.LayoutParams(-1, -1));
        fl.addView(empty, new FrameLayout.LayoutParams(-1, -1));
        lv.setEmptyView(empty);

        final TextView summary = tv("", 13, TXT, false);
        final Button restoreBtn = btn(Tr.t("restore"), GRN, BG, null);
        final Button delBtn = btn(Tr.t("del_forever"), RED, WHITE, null);
        final Runnable upd = () -> {
            int n = 0;
            long s = 0;
            for (Scan.Item it : items) {
                if (it.sel) {
                    n++;
                    s += it.size;
                }
            }
            summary.setText(Tr.f("selinfo", n, Tr.size(s)));
            restoreBtn.setEnabled(n > 0);
            delBtn.setEnabled(n > 0);
            restoreBtn.setAlpha(n > 0 ? 1f : 0.4f);
            delBtn.setAlpha(n > 0 ? 1f : 0.4f);
            ad.notifyDataSetChanged();
        };

        LinearLayout ctl = hbox();
        ctl.setPadding(dp(10), dp(2), dp(10), dp(6));
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(0, -2, 1f);
        bp.setMarginEnd(dp(8));
        ctl.addView(btn(Tr.t("all"), CARD2, TXT, v -> {
            for (Scan.Item it : items) it.sel = true;
            upd.run();
        }), bp);
        LinearLayout.LayoutParams bp2 = new LinearLayout.LayoutParams(0, -2, 1f);
        bp2.setMarginEnd(dp(8));
        ctl.addView(btn(Tr.t("none"), CARD2, TXT, v -> {
            for (Scan.Item it : items) it.sel = false;
            upd.run();
        }), bp2);
        ctl.addView(btn(Tr.t("empty_trash"), RED, WHITE, v -> {
            if (items.isEmpty()) return;
            new AlertDialog.Builder(this).setTitle(Tr.t("confirm"))
                    .setMessage(Tr.f("confirm_empty", Tr.size(Scan.sum(items))))
                    .setPositiveButton(Tr.t("yes"), (d, w) -> trashAction(items, false, true))
                    .setNegativeButton(Tr.t("no"), null).show();
        }), new LinearLayout.LayoutParams(0, -2, 1.4f));
        page.addView(ctl, new LinearLayout.LayoutParams(-1, -2));
        page.addView(fl, new LinearLayout.LayoutParams(-1, 0, 1f));

        LinearLayout foot = hbox();
        foot.setGravity(Gravity.CENTER_VERTICAL);
        foot.setPadding(dp(14), dp(8), dp(14), dp(8));
        foot.setBackgroundColor(CARD);
        foot.addView(summary, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-2, -2);
        rp.setMarginEnd(dp(8));
        foot.addView(restoreBtn, rp);
        foot.addView(delBtn);
        page.addView(foot, new LinearLayout.LayoutParams(-1, -2));

        lv.setOnItemClickListener((p, v, pos, id) -> {
            items.get(pos).sel = !items.get(pos).sel;
            upd.run();
        });
        lv.setOnItemLongClickListener((p, v, pos, id) -> {
            info(items.get(pos));
            return true;
        });
        restoreBtn.setOnClickListener(v -> trashAction(items, true, false));
        delBtn.setOnClickListener(v -> new AlertDialog.Builder(this).setTitle(Tr.t("confirm"))
                .setMessage(Tr.f("confirm_del", countSel(items), Tr.size(sumSel(items))))
                .setPositiveButton(Tr.t("yes"), (d, w) -> trashAction(items, false, false))
                .setNegativeButton(Tr.t("no"), null).show());
        upd.run();
    }

    int countSel(List<Scan.Item> l) {
        int n = 0;
        for (Scan.Item i : l) if (i.sel) n++;
        return n;
    }

    long sumSel(List<Scan.Item> l) {
        long s = 0;
        for (Scan.Item i : l) if (i.sel) s += i.size;
        return s;
    }

    void trashAction(final ArrayList<Scan.Item> items, final boolean restore, final boolean all) {
        final ArrayList<Scan.Item> chosen = new ArrayList<>();
        for (Scan.Item i : items) if (all || i.sel) chosen.add(i);
        if (chosen.isEmpty()) return;
        final AlertDialog dlg = busy(Tr.t("working"));
        bg.execute(() -> {
            int ok = 0, fail = 0;
            long bytes = 0;
            for (Scan.Item i : chosen) {
                Trash.Entry e = (Trash.Entry) i.ref;
                if (restore) {
                    if (trash.restore(e)) {
                        ok++;
                        bytes += i.size;
                    } else {
                        fail++;
                    }
                } else {
                    bytes += trash.delete(e);
                    ok++;
                }
            }
            if (ok > 0) trash.log(restore ? "f_trash" : "f_trash_empty", ok, bytes, restore ? "R" : "D");
            final int fo = ok, ff = fail;
            final long fb = bytes;
            ui.post(() -> {
                dlg.dismiss();
                String m = restore ? Tr.f("restored", fo) : Tr.f("done", fo, Tr.size(fb));
                if (ff > 0) m += " — " + Tr.f("failed", ff);
                toast(m);
                cur.run();
            });
        });
    }

    // =====================================================================
    // Rapport
    // =====================================================================

    void report() {
        LinearLayout page = scaffold(Tr.t("f_report"), true, null);
        ScrollView sv = new ScrollView(this);
        LinearLayout b = vbox();
        b.setPadding(dp(14), dp(12), dp(14), dp(24));
        sv.addView(b);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        ArrayList<String[]> h = trash.history();
        long freed = 0, comp = 0;
        for (String[] r : h) {
            try {
                if (r[4].equals("C")) comp += Long.parseLong(r[3]);
                if (r[4].equals("D")) freed += Long.parseLong(r[3]);
            } catch (NumberFormatException e) {
                // ignoré
            }
        }
        LinearLayout c = card();
        c.addView(tv(Tr.t("rep_freed"), 13, SUB, false));
        c.addView(tv(Tr.size(freed), 26, GRN, true));
        c.addView(tv(Tr.t("rep_trash") + " : " + Tr.size(trash.totalSize()), 14, TXT, false));
        c.addView(tv(Tr.t("rep_comp") + " : " + Tr.size(comp), 14, TXT, false));
        c.addView(tv(Tr.t("rep_ops") + " : " + h.size(), 14, TXT, false));
        b.addView(c, lpw(0));
        TextView title = tv(Tr.t("rep_hist"), 16, TXT, true);
        title.setPadding(0, dp(16), 0, dp(4));
        b.addView(title);
        if (h.isEmpty()) b.addView(tv(Tr.t("rep_none"), 14, SUB, false));
        int shown = 0;
        for (String[] r : h) {
            if (shown++ >= 100) break;
            try {
                String line = Tr.f("rep_line", Tr.dateTime(Long.parseLong(r[0])), Tr.t(r[1]), Integer.parseInt(r[2]),
                        Tr.size(Long.parseLong(r[3])), Tr.t("mode_" + r[4]));
                TextView t = tv(line, 13, SUB, false);
                t.setPadding(dp(12), dp(8), dp(12), dp(8));
                t.setBackground(rr(CARD, 10));
                b.addView(t, lpw(6));
            } catch (NumberFormatException e) {
                // ignoré
            }
        }
    }

    // =====================================================================
    // Réglages
    // =====================================================================

    void setLang(String l) {
        st.setLang(l);
        Tr.ar = l.equals("ar");
        cur.run();
    }

    void settings() {
        LinearLayout page = scaffold(Tr.t("f_settings"), true, null);
        ScrollView sv = new ScrollView(this);
        LinearLayout b = vbox();
        b.setPadding(dp(14), dp(12), dp(14), dp(24));
        sv.addView(b);
        page.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        // Langue
        LinearLayout c = card();
        c.addView(tv(Tr.t("lang"), 16, TXT, true));
        LinearLayout row = hbox();
        LinearLayout.LayoutParams p1 = new LinearLayout.LayoutParams(0, -2, 1f);
        p1.setMarginEnd(dp(8));
        row.addView(btn("Français", Tr.ar ? CARD2 : ACC, Tr.ar ? TXT : BG, v -> setLang("fr")), p1);
        row.addView(btn("العربية", Tr.ar ? ACC : CARD2, Tr.ar ? BG : TXT, v -> setLang("ar")),
                new LinearLayout.LayoutParams(0, -2, 1f));
        c.addView(row, lpw(8));
        b.addView(c, lpw(0));

        // PIN
        LinearLayout cp = card();
        cp.addView(tv(Tr.t("pin_title"), 16, TXT, true));
        cp.addView(tv(Tr.t(st.hasPin() ? "pin_on" : "pin_off"), 14, st.hasPin() ? GRN : SUB, false));
        TextView pi = tv(Tr.t("pin_info"), 12, SUB, false);
        pi.setPadding(0, dp(4), 0, 0);
        cp.addView(pi);
        if (!st.hasPin()) {
            cp.addView(btn(Tr.t("pin_set"), ACC, BG, v -> pinSetup()), lpw(8));
        } else {
            cp.addView(btn(Tr.t("pin_change"), ACC, BG, v -> askPin(this::pinSetup)), lpw(8));
            cp.addView(btn(Tr.t("pin_disable"), CARD2, TXT, v -> askPin(() -> {
                st.clearPin();
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_SECURE);
                toast(Tr.t("pin_removed"));
                cur.run();
            })), lpw(8));
        }
        b.addView(cp, lpw(10));

        // Corbeille
        LinearLayout ct = card();
        ct.addView(tv(Tr.t("f_trash"), 16, TXT, true));
        CheckBox cb = new CheckBox(this);
        cb.setText(Tr.t("trash_use"));
        cb.setTextColor(TXT);
        cb.setButtonTintList(ColorStateList.valueOf(ACC));
        cb.setChecked(st.useTrash());
        cb.setOnCheckedChangeListener((x, ch) -> st.setUseTrash(ch));
        ct.addView(cb);
        TextView pd = tv(Tr.t("purge_days"), 13, SUB, false);
        pd.setPadding(0, dp(6), 0, dp(4));
        ct.addView(pd);
        LinearLayout dr = hbox();
        int[] opts = {7, 15, 30};
        for (int i = 0; i < opts.length; i++) {
            final int d = opts[i];
            boolean on = st.purgeDays() == d;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i < opts.length - 1) lp.setMarginEnd(dp(8));
            dr.addView(btn(Tr.f("days", d), on ? ACC : CARD2, on ? BG : TXT, v -> {
                st.setPurgeDays(d);
                cur.run();
            }), lp);
        }
        ct.addView(dr);
        b.addView(ct, lpw(10));

        // Sauvegarde ZIP
        LinearLayout cz = card();
        CheckBox zb = new CheckBox(this);
        zb.setText(Tr.t("zip_use"));
        zb.setTextColor(TXT);
        zb.setButtonTintList(ColorStateList.valueOf(ACC));
        zb.setChecked(st.zipBackup());
        zb.setOnCheckedChangeListener((x, ch) -> st.setZipBackup(ch));
        cz.addView(zb);
        cz.addView(tv(Tr.t("zip_info"), 12, SUB, false));
        b.addView(cz, lpw(10));

        // Rappel périodique
        LinearLayout cr = card();
        cr.addView(tv(Tr.t("remind_title"), 16, TXT, true));
        TextView ri = tv(Tr.t("remind_info"), 12, SUB, false);
        ri.setPadding(0, dp(2), 0, dp(6));
        cr.addView(ri);
        LinearLayout rrow = hbox();
        int[] ropts = {0, 7, 15, 30};
        for (int i = 0; i < ropts.length; i++) {
            final int d = ropts[i];
            boolean on = st.reminderDays() == d;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1f);
            if (i < ropts.length - 1) lp.setMarginEnd(dp(6));
            Button rb = btn(d == 0 ? Tr.t("remind_off") : String.valueOf(d), on ? ACC : CARD2, on ? BG : TXT, v -> {
                st.setReminderDays(d);
                applyReminder(d);
                if (d > 0 && Build.VERSION.SDK_INT >= 33) {
                    requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, 2);
                }
                cur.run();
            });
            rb.setPadding(dp(4), dp(10), dp(4), dp(10));
            rrow.addView(rb, lp);
        }
        cr.addView(rrow);
        b.addView(cr, lpw(10));

        // Exclusions
        b.addView(btn("📁 " + Tr.t("excl_title"), CARD2, TXT, v -> nav(this::exclusions)), lpw(10));

        TextView wi = tv(Tr.t("widget_info"), 12, SUB, false);
        wi.setPadding(dp(4), dp(12), dp(4), 0);
        b.addView(wi);

        TextView ab = tv(Tr.t("about"), 12, SUB, false);
        ab.setPadding(dp(4), dp(16), dp(4), 0);
        b.addView(ab);
    }
}
