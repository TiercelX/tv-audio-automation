package io.tiercel.a7volumeprobe;

import android.app.Service;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;

public class ScheduleService extends Service {
    private final Handler handler=new Handler();
    private PowerManager.WakeLock wake;
    private final Runnable ticker=new Runnable() { public void run() {
        Scheduler.tick(ScheduleService.this,false); updateNotification();
        handler.postDelayed(this,15000);
    }};
    private final BroadcastReceiver route=new BroadcastReceiver() {
        @Override public void onReceive(Context c,Intent i) { Scheduler.tick(c,false); updateNotification(); }
    };
    @Override public void onCreate() {
        super.onCreate();
        IntentFilter filter=new IntentFilter(Intent.ACTION_HEADSET_PLUG);
        filter.addAction("android.bluetooth.a2dp.profile.action.CONNECTION_STATE_CHANGED");
        registerReceiver(route,filter);
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"A7Volume:scheduler");
        // This dedicated 24-hour player needs screen-off timing. Released as
        // soon as automatic control is disabled; no screen or audio focus held.
        wake.acquire();
        updateNotification();
        handler.postDelayed(ticker,15000);
    }
    private void updateNotification() {
        Store store=new Store(this);
        Schedule.Event event=Schedule.next(store.all(),System.currentTimeMillis());
        String status=store.prefs.getString("status","正在等待规则");
        String text=event==null ? status : "下一次 "+event.rule.clock()+" → "+Schedule.percent(event.rule.fine);
        PendingIntent open=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this).setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("定时音量 · 正在运行").setContentText(text).setSubText(status)
            .setContentIntent(open).setOngoing(true).setPriority(Notification.PRIORITY_LOW).build();
        startForeground(7,n);
    }
    @Override public int onStartCommand(Intent i,int flags,int startId) {
        Scheduler.tick(this,i!=null && i.getBooleanExtra("catchUp",false));
        if(!new Store(this).enabled()) { stopSelf(); return START_NOT_STICKY; }
        updateNotification(); return START_STICKY;
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        unregisterReceiver(route);
        if(wake!=null && wake.isHeld()) wake.release();
        stopForeground(true); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
