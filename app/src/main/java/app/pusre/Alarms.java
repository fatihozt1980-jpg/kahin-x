package app.pusre;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

/** Hatırlatmaları telefon alarmı olarak kurar; liste telefonda saklanır, yeniden başlatmada tekrar kurulur. */
final class Alarms {
    static final String PREF = "pusre_alarms";
    static final String KEY = "list";
    static final String ACTION = "app.pusre.ALARM";
    private static final int MAX = 60;

    private Alarms() {}

    private static PendingIntent pi(Context c, String id, String title, String body) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.setAction(ACTION);
        i.setData(Uri.parse("pusre://alarm/" + Uri.encode(id)));
        i.putExtra("id", id);
        i.putExtra("title", title);
        i.putExtra("body", body);
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static synchronized void sync(Context c, String json) {
        SharedPreferences p = c.getSharedPreferences(PREF, 0);
        cancelAll(c, p.getString(KEY, "[]"));
        p.edit().putString(KEY, json == null ? "[]" : json).apply();
        scheduleAll(c, json);
    }

    static synchronized void rescheduleAll(Context c) {
        scheduleAll(c, c.getSharedPreferences(PREF, 0).getString(KEY, "[]"));
    }

    private static void cancelAll(Context c, String json) {
        try {
            AlarmManager am = c.getSystemService(AlarmManager.class);
            JSONArray a = new JSONArray(json);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                am.cancel(pi(c, o.optString("id"), "", ""));
            }
        } catch (Exception ignored) { }
    }

    private static void scheduleAll(Context c, String json) {
        try {
            AlarmManager am = c.getSystemService(AlarmManager.class);
            JSONArray a = new JSONArray(json == null ? "[]" : json);
            long now = System.currentTimeMillis();
            int n = 0;
            for (int i = 0; i < a.length() && n < MAX; i++) {
                JSONObject o = a.optJSONObject(i);
                if (o == null) continue;
                long t = o.optLong("t", 0);
                String id = o.optString("id");
                if (id.isEmpty() || t < now - 60000) continue;
                PendingIntent p = pi(c, id, o.optString("title", "Pusre"), o.optString("body", ""));
                boolean exact = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms();
                try {
                    if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
                    else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
                } catch (SecurityException e) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, t, p);
                }
                n++;
            }
        } catch (Exception ignored) { }
    }
}
