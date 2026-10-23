package app.tvdetox.startup;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class StartupReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && StartupBridge.ALARM_ACTION.equals(intent.getAction())) {
            StartupBridge.runScheduled(context);
        }
    }
}
