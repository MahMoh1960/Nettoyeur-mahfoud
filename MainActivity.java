package app.nettoyage;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.usage.UsageStats;
import android.app.usage.UsageStatsManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.media.ThumbnailUtils;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.os.StatFs;
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
        String l = st.lang();
        Tr.ar = l != null ? l.equals("ar") : Locale.getDefault().getLanguage().equals("ar");
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
        menu(b, "📏", "f_large", sc ? Tr.f("sub_items", scan.large.size(), Tr.size(Scan.sum(scan.large))) : Tr.t("s_large"),
                () -> needScan(() -> nav(() -> fileList("f_large", "d_large", scan.large, false, false))));
        menu(b, "👯", "f_dups", scan.dupsReady ? Tr.f("dups_sub", scan.dupGroups.size(), Tr.size(scan.dupFreeable())) : Tr.t("s_dups"),
                () -> needScan(() -> nav(this::dupsScreen)));
        menu(b, "📥", "f_old", sc ? Tr.f("sub_items", scan.oldDl.size(), Tr.size(Scan.sum(scan.oldDl))) : Tr.t("s_old"),
                () -> needScan(() -> nav(() -> fileList("f_old", "d_old", scan.oldDl, false, false))));
        menu(b, "📸", "f_shots", sc ? Tr.f("sub_items", scan.shots.size(), Tr.size(Scan.sum(scan.shots))) : Tr.t("s_shots"),
                () -> needScan(() -> nav(() -> fileList("f_shots", "d_shots", scan.shots, false, false))));
        menu(b, "📦", "f_apk", sc ? Tr.f("sub_items", scan.apks.size(), Tr.size(Scan.sum(scan.apks))) : Tr.t("s_apk"),
                () -> needScan(() -> nav(() -> fileList("f_apk", "d_apk", scan.apks, false, false))));
        menu(b, "💬", "f_msg", sc ? Tr.f("sub_items", scan.msg.size(), Tr.size(Scan.sum(scan.msg))) : Tr.t("s_msg"),
                () -> needScan(() -> nav(() -> fileList("f_msg", "d_msg", scan.msg, false, false))));
        menu(b, "🧹", "f_temp", sc ? Tr.f("sub_items", scan.temp.size(), Tr.size(Scan.sum(scan.temp))) : Tr.t("s_temp"),
                () -> needScan(() -> nav(() -> fileList("f_temp", "d_temp", scan.temp, true, false))));
        menu(b, "🗂️", "f_empty", sc ? Tr.f("sub_items", scan.empties.size(), Tr.size(0)) : Tr.t("s_empty"),
                () -> needScan(() -> nav(() -> fileList("f_empty", "d_empty", scan.empties, true, false))));
        menu(b, "📱", "f_apps", Tr.t("s_apps"), () -> nav(this::apps));
        menu(b, "💾", "f_cache", Tr.t("s_cache"), () -> nav(this::cacheScreen));
        menu(b, "🗑️", "f_trash", Tr.t("s_trash") + " · " + Tr.size(trash.totalSize()), () -> nav(this::trashScreen));
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
            });
        });
    }

    void dupsScreen() {
        if (scan.dupsReady) {
            fileList("f_dups", "d_dups", scan.dups, false, true);
            return;
        }
        LinearLayout page = scaffold(Tr.t("f_dups"), true, null);
        LinearLayout b = vbox();
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(24), dp(24), dp(24), dp(24));
        b.addView(new ProgressBar(this));
        final TextView t = tv(Tr.t("dups_search"), 16, TXT, true);
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
                t.setText(Tr.t("dups_search") + " " + scan.dupDone + "/" + scan.dupTotal);
                ui.postDelayed(this, 300);
            }
        });
        bg.execute(() -> {
            scan.findDups();
            ui.post(() -> {
                working = false;
                if (scan.cancel || cur != me) return;
                cur.run();
            });
        });
    }

    // =====================================================================
    // Listes de fichiers
    // =====================================================================

    void fileList(final String fkey, String dkey, final ArrayList<Scan.Item> list, boolean pre, final boolean dupMode) {
        LinearLayout page = scaffold(Tr.t(fkey), true, null);
        if (dupMode) scan.dupDefaults();
        else for (Scan.Item it : list) it.sel = pre;
        page.addView(note(Tr.t(dkey)));

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
        final Button delBtn = btn(Tr.t("del"), RED, WHITE, null);
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
            ad.notifyDataSetChanged();
        };

        LinearLayout ctl = hbox();
        ctl.setPadding(dp(10), dp(2), dp(10), dp(6));
        Button all = btn(Tr.t("all"), CARD2, TXT, v -> {
            if (dupMode) {
                scan.dupDefaults();
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
        foot.addView(delBtn);
        page.addView(foot, new LinearLayout.LayoutParams(-1, -2));

        lv.setOnItemClickListener((p, v, pos, id) -> {
            Scan.Item it = list.get(pos);
            if (it.hdr) return;
            if (dupMode && !it.sel) {
                boolean other = false;
                for (Scan.Item o : scan.dupGroups.get(it.group)) if (o != it && !o.sel) other = true;
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
        delBtn.setOnClickListener(v -> confirmDelete(fkey, list, upd));
        upd.run();
    }

    void info(Scan.Item it) {
        String p = it.orig != null ? it.orig : it.path;
        new AlertDialog.Builder(this)
                .setMessage(Tr.f("file_info", p, Tr.size(it.size), Tr.date(it.mtime)))
                .setPositiveButton(Tr.t("ok"), null).show();
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
        final boolean toTrash = st.useTrash() && !fkey.equals("f_empty");
        String msg = Tr.f(toTrash ? "confirm_trash" : "confirm_del", chosen.size(), Tr.size(s));
        new AlertDialog.Builder(this).setTitle(Tr.t("confirm")).setMessage(msg)
                .setPositiveButton(Tr.t("yes"), (d, w) -> doDelete(fkey, chosen, toTrash, after))
                .setNegativeButton(Tr.t("no"), null).show();
    }

    void doDelete(final String fkey, final ArrayList<Scan.Item> chosen, final boolean toTrash, final Runnable after) {
        final AlertDialog dlg = busy(Tr.t("working"));
        bg.execute(() -> {
            int ok = 0, fail = 0;
            long freed = 0;
            final ArrayList<Scan.Item> done = new ArrayList<>();
            for (Scan.Item it : chosen) {
                File f = new File(it.path);
                boolean r;
                if (!f.exists()) r = true;
                else if (f.isDirectory()) r = f.delete();
                else if (toTrash) r = trash.move(f);
                else r = f.delete();
                if (r) {
                    ok++;
                    freed += it.size;
                    done.add(it);
                } else {
                    fail++;
                }
            }
            if (ok > 0) trash.log(fkey, ok, freed, toTrash ? "T" : "D");
            final int fo = ok, ff = fail;
            final long fb = freed;
            ui.post(() -> {
                scan.removed(done);
                dlg.dismiss();
                String m = Tr.f("done", fo, Tr.size(fb));
                if (ff > 0) m += " — " + Tr.f("failed", ff);
                toast(m);
                after.run();
            });
        });
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
                h.setText(Tr.f("group_hdr", it.gnum, it.gcount, Tr.size(it.gsize)));
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
        long freed = 0;
        for (String[] r : h) {
            try {
                if (r[4].equals("D")) freed += Long.parseLong(r[3]);
            } catch (NumberFormatException e) {
                // ignoré
            }
        }
        LinearLayout c = card();
        c.addView(tv(Tr.t("rep_freed"), 13, SUB, false));
        c.addView(tv(Tr.size(freed), 26, GRN, true));
        c.addView(tv(Tr.t("rep_trash") + " : " + Tr.size(trash.totalSize()), 14, TXT, false));
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

        TextView ab = tv(Tr.t("about"), 12, SUB, false);
        ab.setPadding(dp(4), dp(16), dp(4), 0);
        b.addView(ab);
    }
}
