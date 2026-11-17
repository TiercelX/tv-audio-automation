package io.tiercel.a7volumeprobe;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public final class Store {
    public final SharedPreferences prefs;
    public Store(Context context) { prefs=context.getSharedPreferences("schedule",Context.MODE_PRIVATE); }
    public boolean enabled() { return prefs.getBoolean("enabled",false); }
    public boolean templateEnabled() { return prefs.getBoolean("template",true); }
    public boolean wiredOnly() { return prefs.getBoolean("wiredOnly",true); }
    public int min() { return prefs.getInt("min",10); }
    public int max() { return prefs.getInt("max",80); }
    public int templateDays() { return prefs.getInt("templateDays",127); }
    public List<Schedule.Rule> custom() {
        List<Schedule.Rule> result=new ArrayList<>();
        try {
            JSONArray array=new JSONArray(prefs.getString("rules","[]"));
            for(int i=0;i<array.length();i++) {
                JSONObject o=array.getJSONObject(i);
                result.add(new Schedule.Rule(o.getString("id"),o.optString("label","自定义规则"),o.getInt("minute"),o.getInt("days"),o.getInt("fine"),o.getBoolean("enabled"),i+1));
            }
        } catch(Exception e) { android.util.Log.e("A7Schedule","Cannot read rules",e); }
        return result;
    }
    public List<Schedule.Rule> all() {
        List<Schedule.Rule> result=templateEnabled() ? Schedule.template(min(),max(),templateDays()) : new ArrayList<Schedule.Rule>();
        result.addAll(custom()); return result;
    }
    public List<Schedule.Rule> sortedCustom() {
        List<Schedule.Rule> result=custom();
        Collections.sort(result,new Comparator<Schedule.Rule>() {
            public int compare(Schedule.Rule a,Schedule.Rule b) { return Integer.compare(a.minute,b.minute); }
        });
        return result;
    }
    public void save(List<Schedule.Rule> rules) {
        JSONArray array=new JSONArray();
        try {
            for(Schedule.Rule rule:rules) {
                JSONObject o=new JSONObject(); o.put("id",rule.id); o.put("label",rule.label); o.put("minute",rule.minute);
                o.put("days",rule.days); o.put("fine",rule.fine); o.put("enabled",rule.enabled); array.put(o);
            }
            prefs.edit().putString("rules",array.toString()).commit();
        } catch(Exception e) { throw new IllegalStateException(e); }
    }
    public void put(Schedule.Rule rule) {
        List<Schedule.Rule> rules=custom(); boolean replaced=false;
        for(int i=0;i<rules.size();i++) if(rules.get(i).id.equals(rule.id)) { rules.set(i,rule); replaced=true; break; }
        if(!replaced) rules.add(rule); save(rules);
    }
    public void delete(String id) {
        List<Schedule.Rule> rules=custom();
        for(int i=rules.size()-1;i>=0;i--) if(rules.get(i).id.equals(id)) rules.remove(i);
        save(rules);
    }
    public void log(String message) {
        String line=new java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.ROOT).format(new java.util.Date())+"  "+message;
        String log=line+"\n"+prefs.getString("log","");
        String[] lines=log.split("\n");
        if(lines.length>80) { StringBuilder out=new StringBuilder(); for(int i=0;i<80;i++) out.append(lines[i]).append('\n'); log=out.toString(); }
        prefs.edit().putString("log",log).commit();
        android.util.Log.i("A7Schedule",message);
    }
}
