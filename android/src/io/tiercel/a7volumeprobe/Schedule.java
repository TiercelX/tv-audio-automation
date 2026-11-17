package io.tiercel.a7volumeprobe;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/** Pure schedule math. All times follow the phone's current timezone. */
public final class Schedule {
    private Schedule() {}
    public static final class Rule {
        public final String id, label;
        public final int minute, days, fine, priority;
        public final boolean enabled;
        public Rule(String id, String label, int minute, int days, int fine, boolean enabled, int priority) {
            if (minute < 0 || minute >= 1440 || days < 1 || days > 127 || fine < 0 || fine > 150)
                throw new IllegalArgumentException("Invalid rule");
            this.id=id; this.label=label; this.minute=minute; this.days=days;
            this.fine=fine; this.enabled=enabled; this.priority=priority;
        }
        public String clock() { return String.format(Locale.ROOT,"%02d:%02d",minute/60,minute%60); }
    }
    public static final class Event {
        public final Rule rule;
        public final long when;
        public Event(Rule rule, long when) { this.rule=rule; this.when=when; }
        public String key() { return rule.id + ":" + when + ":" + rule.fine; }
    }
    public static int tvToFine(int tv, int minPercent, int maxPercent) {
        if (tv < 1 || tv > 80 || minPercent < 0 || maxPercent > 100 || minPercent >= maxPercent)
            throw new IllegalArgumentException("Invalid mapping");
        // Round only once, after mapping directly onto the real 0..150 scale.
        return (int)Math.round(1.5 * (minPercent + (tv-1) * (maxPercent-minPercent) / 79.0));
    }
    public static int percentToFine(int percent) {
        if (percent < 0 || percent > 100) throw new IllegalArgumentException("Invalid percent");
        return (int)Math.round(percent*1.5);
    }
    public static String percent(int fine) { return String.format(Locale.ROOT,"%.1f%%",fine*100.0/150); }
    public static String daysText(int days) {
        if (days==127) return "每天";
        if (days==31) return "周一至周五";
        if (days==96) return "周六、周日";
        String[] names={"一","二","三","四","五","六","日"};
        StringBuilder out=new StringBuilder("周");
        for (int i=0;i<7;i++) if ((days & (1<<i))!=0) { if(out.length()>1) out.append("、"); out.append(names[i]); }
        return out.toString();
    }
    private static boolean selected(Rule r, Calendar date) {
        int mondayZero=(date.get(Calendar.DAY_OF_WEEK)+5)%7;
        return (r.days & (1<<mondayZero))!=0;
    }
    public static Event latest(List<Rule> rules, long now) { return find(rules,now,false); }
    public static Event next(List<Rule> rules, long now) { return find(rules,now,true); }
    private static Event find(List<Rule> rules, long now, boolean future) {
        Event best=null;
        for (Rule r:rules) {
            if (!r.enabled) continue;
            for(int offset=0;offset<=7;offset++) {
                Calendar date=Calendar.getInstance(); date.setTimeInMillis(now);
                date.add(Calendar.DAY_OF_YEAR, future ? offset : -offset);
                date.set(Calendar.HOUR_OF_DAY,r.minute/60); date.set(Calendar.MINUTE,r.minute%60);
                date.set(Calendar.SECOND,0); date.set(Calendar.MILLISECOND,0);
                if(!selected(r,date)) continue;
                long when=date.getTimeInMillis();
                if(future ? when<=now : when>now) continue;
                if(best==null || (future ? when<best.when : when>best.when)
                    || (when==best.when && r.priority>best.rule.priority)) best=new Event(r,when);
                break;
            }
        }
        return best;
    }
    // TV checkpoints from tv-audio-automation/ubuntu (not the G1 evening curve).
    // Morning checkpoints mirror the SA9023 script's original TV targets.
    public static final int[][] TV = {
        {351,5},{361,10},{371,15},{381,20},{391,25},{401,30},{411,35},{421,40},
        {531,45},{691,50},{881,55},{901,60},{1021,65},{1031,70},{1041,75},{1051,80},
        {1141,75},{1171,70},{1201,65},{1231,60},{1251,55},{1261,50},{1271,45},
        {1281,40},{1291,35},{1296,30},{1301,25},{1306,20},{1311,15},{1316,10},
        {1321,5},{1326,5},{1331,5},{1334,1}
    };
    public static List<Rule> template(int min, int max, int days) {
        List<Rule> rules=new ArrayList<>();
        for(int[] point:TV) rules.add(new Rule("tv-"+point[0],"GitHub 时间表 · TV "+point[1],point[0],days,tvToFine(point[1],min,max),true,0));
        return rules;
    }
}
