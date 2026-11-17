package io.tiercel.a7volumeprobe;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

public final class Scheduler {
    public static final String ACTION="io.tiercel.a7volumeprobe.TICK";
    private Scheduler() {}
    private static PendingIntent pending(Context c) {
        return PendingIntent.getBroadcast(c,1,new Intent(c,ScheduleReceiver.class).setAction(ACTION),PendingIntent.FLAG_UPDATE_CURRENT);
    }
    public static void start(Context c, boolean catchUp) {
        c.startService(new Intent(c,ScheduleService.class).putExtra("catchUp",catchUp));
    }
    public static synchronized void tick(Context c, boolean force) {
        Store store=new Store(c);
        AlarmManager alarm=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if(!store.enabled()) {
            alarm.cancel(pending(c));
            c.stopService(new Intent(c,ScheduleService.class));
            store.prefs.edit().putString("status","自动调音已关闭").commit();
            return;
        }
        long now=System.currentTimeMillis();
        Schedule.Event current=Schedule.latest(store.all(),now);
        Schedule.Event next=Schedule.next(store.all(),now);
        // Retry a temporary disconnect/readback error; the next real checkpoint
        // still wins. A foreground service provides an additional timer path.
        long wake=next==null ? now+60000 : Math.min(next.when,now+60000);
        alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,wake,pending(c));
        if(current==null) { store.prefs.edit().putString("status","请启用至少一张规则卡片").commit(); return; }
        try {
            VolumeControl volume=new VolumeControl(c);
            if(store.wiredOnly() && !volume.wired()) {
                store.prefs.edit().putString("status","等待耳机孔连接音箱").putBoolean("waitingWired",true).commit(); return;
            }
            force |= store.prefs.getBoolean("waitingWired",false);
            if(force || !current.key().equals(store.prefs.getString("lastKey",""))) {
                volume.write(current.rule.fine);
                store.prefs.edit().putString("lastKey",current.key()).putBoolean("waitingWired",false)
                    .putString("status","已应用 "+current.rule.clock()+" · "+Schedule.percent(current.rule.fine)).commit();
                store.log(current.rule.label+" "+current.rule.clock()+" → "+Schedule.percent(current.rule.fine)+"（"+current.rule.fine+"/150），回读成功");
            }
        } catch(Exception e) {
            String message="音量设置失败："+rootMessage(e);
            if(!message.equals(store.prefs.getString("status",""))) store.log(message);
            store.prefs.edit().putString("status",message).commit();
        }
    }
    public static String rootMessage(Throwable e) {
        while(e.getCause()!=null) e=e.getCause();
        return e.getMessage()==null ? e.toString() : e.getMessage();
    }
}
