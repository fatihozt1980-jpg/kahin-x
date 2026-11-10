package app.kahinx;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;

/** Bildirim kanalları ve bildirimlerin kendisi. */
final class Notes {
    static final String CH_RUN = "kx_run";
    static final String CH_ALERT = "kx_alert";
    static final int ID_RUN = 1;

    private Notes() {}

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel run = new NotificationChannel(CH_RUN, "Arka plan çalışması", NotificationManager.IMPORTANCE_LOW);
        run.setDescription("Uygulamanın arka planda taradığını gösteren sabit satır");
        run.setShowBadge(false);
        nm.createNotificationChannel(run);
        NotificationChannel alert = new NotificationChannel(CH_ALERT, "Sinyaller ve ani hareketler", NotificationManager.IMPORTANCE_HIGH);
        alert.setDescription("Güçlü sinyal, ani yükseliş/düşüş ve tasfiye dalgası bildirimleri");
        alert.enableVibration(true);
        nm.createNotificationChannel(alert);
    }

    private static PendingIntent open(Context c) {
        Intent i = new Intent(c, MainActivity.class);
        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static Notification running(Context c, String text) {
        Intent stop = new Intent(c, KeepAliveService.class).setAction(KeepAliveService.ACTION_STOP);
        PendingIntent ps = PendingIntent.getService(c, 1, stop, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Action stopAction = new Notification.Action.Builder((Icon) null, "Durdur", ps).build();
        return new Notification.Builder(c, CH_RUN)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("KAHİN X çalışıyor")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(open(c))
                .addAction(stopAction)
                .build();
    }

    static void status(Context c, String text) {
        if (!KeepAliveService.running) return;
        c.getSystemService(NotificationManager.class).notify(ID_RUN, running(c, text));
    }

    static void alert(Context c, String title, String body, String tag) {
        int id = tag == null || tag.isEmpty()
                ? 1000 + (int) (System.currentTimeMillis() % 100000)
                : 100000 + (tag.hashCode() & 0xFFFF);
        Notification n = new Notification.Builder(c, CH_ALERT)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setAutoCancel(true)
                .setShowWhen(true)
                .setContentIntent(open(c))
                .build();
        c.getSystemService(NotificationManager.class).notify(id, n);
    }
}
