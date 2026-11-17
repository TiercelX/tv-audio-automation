package io.tiercel.a7volumeprobe;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent intent) {
        String action=intent.getAction();
        if(!new Store(c).enabled()) return;
        boolean force=Intent.ACTION_TIME_CHANGED.equals(action) || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
            || Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action);
        Scheduler.start(c,force);
    }
}
