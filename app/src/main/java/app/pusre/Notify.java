package app.pusre;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

/** Bildirim kanalları ve bildirimlerin kendisi. */
final class Notify {
    static final String CH_REM = "pusre_rem";
    static final String CH_EQ = "pusre_eq";

    private Notify() {}

    static void channels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel rem = new NotificationChannel(CH_REM, "Hatırlatmalar", NotificationManager.IMPORTANCE_HIGH);
        rem.setDescription("Kurduğun hatırlatmalar ve nota bağlı hatırlatmalar");
        rem.enableVibration(true);
        nm.createNotificationChannel(rem);
        NotificationChannel eq = new NotificationChannel(CH_EQ, "Deprem", NotificationManager.IMPORTANCE_HIGH);
        eq.setDescription("Yakınında kayda değer deprem olduğunda haber verir");
        eq.enableVibration(true);
        nm.createNotificationChannel(eq);
    }

    static void show(Context c, String channel, int id, String title, String body) {
        try {
            channels(c);
            Intent i = new Intent(c, MainActivity.class);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent open = PendingIntent.getActivity(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(c, channel)
                    .setSmallIcon(R.drawable.ic_stat)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setAutoCancel(true)
                    .setShowWhen(true)
                    .setContentIntent(open)
                    .build();
            c.getSystemService(NotificationManager.class).notify(id, n);
        } catch (Exception ignored) { }
    }
}
