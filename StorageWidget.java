package app.nettoyage;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.StatFs;
import android.widget.RemoteViews;

/** Widget d'écran d'accueil : espace libre et bouton de nettoyage. */
public class StorageWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        new Store(c).applyLang();
        StatFs sf = new StatFs(Environment.getExternalStorageDirectory().getPath());
        long total = sf.getTotalBytes();
        long free = sf.getAvailableBytes();
        RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
        v.setTextViewText(R.id.w_title, Tr.t("app"));
        v.setTextViewText(R.id.w_free, Tr.f("free", Tr.size(free)));
        v.setProgressBar(R.id.w_bar, 100, (int) ((total - free) * 100 / Math.max(total, 1)), false);
        v.setTextViewText(R.id.w_btn, Tr.t("w_clean"));
        int fl = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;
        Intent scan = new Intent(c, MainActivity.class);
        scan.putExtra("scan", true);
        scan.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.w_btn, PendingIntent.getActivity(c, 0, scan, fl));
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 1, open, fl));
        for (int id : ids) m.updateAppWidget(id, v);
    }
}
