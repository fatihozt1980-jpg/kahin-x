package app.pusre;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Vakti gelen hatırlatmayı bildirim olarak gösterir. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (i == null || !Alarms.ACTION.equals(i.getAction())) return;
        String id = i.getStringExtra("id");
        String title = i.getStringExtra("title");
        String body = i.getStringExtra("body");
        Notify.show(c, Notify.CH_REM, 20000 + ((id == null ? 0 : id.hashCode()) & 0xFFFF), title == null ? "Pusre" : title, body == null ? "" : body);
    }
}
