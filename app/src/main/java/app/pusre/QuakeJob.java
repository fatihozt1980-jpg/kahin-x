package app.pusre;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Yaklaşık 15 dakikada bir son depremlere bakar; yakında kayda değer bir deprem varsa bildirim gönderir. */
public class QuakeJob extends JobService {
    static final String PREF = "pusre_eq";
    static final int JOB_ID = 4201;

    static void ensure(Context c) {
        try {
            JobScheduler js = c.getSystemService(JobScheduler.class);
            if (!c.getSharedPreferences(PREF, 0).getBoolean("on", false)) {
                js.cancel(JOB_ID);
                return;
            }
            if (js.getPendingJob(JOB_ID) != null) return;
            JobInfo ji = new JobInfo.Builder(JOB_ID, new ComponentName(c, QuakeJob.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPeriodic(15 * 60 * 1000L)
                    .setPersisted(true)
                    .build();
            js.schedule(ji);
        } catch (Exception ignored) { }
    }

    @Override
    public boolean onStartJob(final JobParameters params) {
        final Context app = getApplicationContext();
        new Thread(() -> {
            try {
                check(app);
            } catch (Exception ignored) { }
            jobFinished(params, false);
        }).start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) { return false; }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(10000);
            c.setReadTimeout(15000);
            c.setRequestProperty("User-Agent", "Pusre/1.0 (Android)");
            c.setRequestProperty("Accept", "application/json");
            if (c.getResponseCode() != 200) throw new Exception("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0 && out.size() < 2 * 1024 * 1024) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private static double km(double a, double b, double c, double d) {
        double r = Math.PI / 180, x = Math.sin((c - a) * r / 2), y = Math.sin((d - b) * r / 2);
        double h = x * x + Math.cos(a * r) * Math.cos(c * r) * y * y;
        return 12742 * Math.asin(Math.sqrt(h));
    }

    /** Bulunan depremler: her biri {zaman(ms), büyüklük, enlem, boylam} ve yer adı. */
    private static void check(Context c) throws Exception {
        SharedPreferences p = c.getSharedPreferences(PREF, 0);
        if (!p.getBoolean("on", false)) return;
        double lat = p.getFloat("lat", 0), lon = p.getFloat("lon", 0);
        if (lat == 0 && lon == 0) return;
        long last = p.getLong("last", 0), now = System.currentTimeMillis(), newest = last;
        double bestMag = 0, bestKm = 0;
        long bestT = 0;
        String bestPl = "";

        JSONArray list = null;
        boolean kandilli = true;
        try {
            list = new JSONObject(get("https://api.orhanaydogdu.com.tr/deprem/kandilli/live?limit=60")).optJSONArray("result");
        } catch (Exception e) {
            list = null;
        }
        if (list == null || list.length() == 0) {
            kandilli = false;
            list = new JSONObject(get("https://www.seismicportal.eu/fdsnws/event/1/query?format=json&limit=80&minlat=34&maxlat=43.5&minlon=24&maxlon=46&minmag=2.5&orderby=time")).optJSONArray("features");
        }
        if (list == null) return;

        SimpleDateFormat tr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        tr.setTimeZone(TimeZone.getTimeZone("Europe/Istanbul"));
        SimpleDateFormat utc = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
        utc.setTimeZone(TimeZone.getTimeZone("UTC"));

        for (int i = 0; i < list.length(); i++) {
            JSONObject o = list.optJSONObject(i);
            if (o == null) continue;
            long t;
            double mag, qlat, qlon;
            String pl;
            try {
                if (kandilli) {
                    Date d = tr.parse(o.optString("date_time"));
                    if (d == null) continue;
                    t = d.getTime();
                    mag = o.optDouble("mag", 0);
                    JSONArray g = o.getJSONObject("geojson").getJSONArray("coordinates");
                    qlon = g.getDouble(0);
                    qlat = g.getDouble(1);
                    pl = o.optString("title");
                } else {
                    JSONObject q = o.getJSONObject("properties");
                    String ts = q.optString("time");
                    if (ts.length() < 19) continue;
                    Date d = utc.parse(ts.substring(0, 19));
                    if (d == null) continue;
                    t = d.getTime();
                    mag = q.optDouble("mag", 0);
                    qlat = q.optDouble("lat", 0);
                    qlon = q.optDouble("lon", 0);
                    pl = q.optString("flynn_region");
                }
            } catch (Exception e) {
                continue;
            }
            if (t > now + 5 * 60000L) t -= 3 * 3600000L;
            if (t > newest) newest = t;
            if (t <= last || now - t > 2 * 3600000L) continue;
            double dist = km(lat, lon, qlat, qlon);
            boolean notable = (mag >= 4 && dist <= 400) || (mag >= 3 && dist <= 120);
            if (notable && mag > bestMag) {
                bestMag = mag;
                bestKm = dist;
                bestT = t;
                bestPl = pl;
            }
        }
        p.edit().putLong("last", newest).apply();
        if (bestMag > 0) {
            SimpleDateFormat hm = new SimpleDateFormat("HH:mm", Locale.US);
            String body = String.format(Locale.US, "%.1f", bestMag).replace('.', ',') + " büyüklüğünde · " + bestPl + " · " + Math.round(bestKm) + " km uzakta · " + hm.format(new Date(bestT));
            Notify.show(c, Notify.CH_EQ, 30001, "Deprem", body);
        }
    }
}
