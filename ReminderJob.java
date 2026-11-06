package app.nettoyage;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;

import java.util.ArrayList;

/** Tâche périodique : estime l'espace récupérable et envoie une notification. */
public class ReminderJob extends JobService {
    private volatile Scan current;

    @Override
    public boolean onStartJob(final JobParameters p) {
        new Thread(() -> {
            try {
                work();
            } catch (Throwable e) {
                // rien à signaler
            }
            jobFinished(p, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters p) {
        Scan s = current;
        if (s != null) s.cancel = true;
        return false;
    }

    private void work() {
        Store st = new Store(this);
        st.applyLang();
        boolean access = Build.VERSION.SDK_INT >= 30
                ? Environment.isExternalStorageManager()
                : checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        String text;
        if (access) {
            Scan.EXCL = st.excluded();
            Scan s = new Scan();
            current = s;
            s.scan(Environment.getExternalStorageDirectory());
            if (s.cancel) return;
            long freeable = 0;
            for (ArrayList<Scan.Item> l : s.quickSet(getPackageManager()).values()) freeable += Scan.sum(l);
            if (freeable >= 50L * 1024 * 1024) {
                text = Tr.f("remind_text", Tr.size(freeable));
            } else {
                StatFs sf = new StatFs(Environment.getExternalStorageDirectory().getPath());
                text = Tr.f("remind_free", Tr.size(sf.getAvailableBytes()));
            }
        } else {
            StatFs sf = new StatFs(Environment.getExternalStorageDirectory().getPath());
            text = Tr.f("remind_free", Tr.size(sf.getAvailableBytes()));
        }
        notifyUser(text);
    }

    private void notifyUser(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        Notification.Builder nb;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel("rappel", Tr.t("remind_channel"), NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
            nb = new Notification.Builder(this, "rappel");
        } else {
            nb = new Notification.Builder(this);
        }
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("scan", true);
        PendingIntent pi = PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        nb.setSmallIcon(android.R.drawable.ic_menu_delete)
                .setContentTitle(Tr.t("app"))
                .setContentText(text)
                .setContentIntent(pi)
                .setAutoCancel(true);
        nm.notify(1, nb.build());
    }
}
