package io.tiercel.a7volumeprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import java.util.*;

/** Separate signed instrumentation APK. All settings/volume restored in finally. */
public class DeviceSmokeTest extends Instrumentation {
    @Override public void onCreate(Bundle args) { super.onCreate(args);start(); }
    private void check(boolean ok,String label) {
        if(!ok) throw new AssertionError(label);
        Bundle b=new Bundle(); b.putString("stream","PASS "+label+"\n");sendStatus(0,b);
    }
    @Override public void onStart() {
        Context c=getTargetContext(); Store s=new Store(c);
        Map<String,?> snapshot=s.prefs.getAll();
        VolumeControl volume=null;int original=-1;String error=null;
        try {
            volume=new VolumeControl(c);original=volume.read();
            check(volume.wired(),"wired output connected");
            check(original>=5 && original<=145 && !volume.muted(),"safe initial volume "+original);
            s.prefs.edit().putBoolean("enabled",false).commit();
            Scheduler.tick(c,false);Thread.sleep(200);
            Calendar now=Calendar.getInstance();int minute=now.get(Calendar.HOUR_OF_DAY)*60+now.get(Calendar.MINUTE);
            List<Schedule.Rule> rules=new ArrayList<>();
            rules.add(new Schedule.Rule("test-current","device-test",(minute+1439)%1440,127,original,true,1));
            rules.add(new Schedule.Rule("test-future","device-test",(minute+1)%1440,127,original-1,true,2));
            s.save(rules);s.prefs.edit().putBoolean("template",false).putBoolean("wiredOnly",true).putBoolean("enabled",true).remove("lastKey").commit();
            Scheduler.tick(c,true);
            check(volume.read()==original,"startup catch-up and fine readback");
            volume.write(original-2);Scheduler.tick(c,false);
            check(volume.read()==original-2,"manual volume preserved before next checkpoint");
            Scheduler.tick(c,true);check(volume.read()==original,"explicit catch-up reapplies current checkpoint");
            // The real foreground service and AlarmManager must cross an actual
            // wall-clock minute. No system clock changes or fake receiver calls.
            Scheduler.start(c,false);
            long due=Schedule.next(s.all(),System.currentTimeMillis()).when;
            Bundle progress=new Bundle();progress.putString("stream","Waiting for real timer at "+new Date(due)+"\n");sendStatus(0,progress);
            long deadline=due+22000;
            while(System.currentTimeMillis()<deadline && volume.read()!=original-1) Thread.sleep(250);
            check(volume.read()==original-1,"real timed rule through foreground service/AlarmManager");
            check(s.prefs.getString("lastKey","").startsWith("test-future:"),"timed execution recorded");
            s.prefs.edit().putBoolean("enabled",false).commit();Scheduler.tick(c,false);
            check(!s.enabled(),"master disable");
            volume.write(original);
            check(volume.read()==original,"original media volume restored");
        } catch(Throwable e) { error=e.toString();android.util.Log.e("A7DeviceTest","FAIL",e); }
        finally {
            s.prefs.edit().putBoolean("enabled",false).commit();Scheduler.tick(c,false);
            SharedPreferences.Editor edit=s.prefs.edit().clear();
            for(Map.Entry<String,?> entry:snapshot.entrySet()) {
                Object v=entry.getValue();String k=entry.getKey();
                if(v instanceof String)edit.putString(k,(String)v);else if(v instanceof Boolean)edit.putBoolean(k,(Boolean)v);
                else if(v instanceof Integer)edit.putInt(k,(Integer)v);else if(v instanceof Long)edit.putLong(k,(Long)v);
                else if(v instanceof Float)edit.putFloat(k,(Float)v);
            }
            edit.commit();
            try {if(volume!=null && original>=0)volume.write(original);}catch(Exception e){error="RESTORE FAILED: "+e;}
            if(s.enabled())Scheduler.start(c,false);
        }
        Bundle result=new Bundle();result.putString("stream",error==null?"DEVICE TESTS PASSED; original settings and volume restored\n":"FAILED "+error+"\n");
        finish(error==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
