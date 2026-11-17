import io.tiercel.a7volumeprobe.Schedule;
import java.util.*;

public final class ScheduleTest {
    static int checks;
    static void eq(Object expected,Object actual) {
        checks++; if(!expected.equals(actual)) throw new AssertionError("Expected "+expected+", got "+actual);
    }
    static long at(String value) throws Exception {
        return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.ROOT).parse(value).getTime();
    }
    static Schedule.Rule rule(String id,int minute,int days,int fine,int priority) {
        return new Schedule.Rule(id,id,minute,days,fine,true,priority);
    }
    public static void main(String[] args) throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
        eq(15,Schedule.tvToFine(1,10,80));eq(120,Schedule.tvToFine(80,10,80));
        eq(67,Schedule.tvToFine(40,10,80));eq(15,Schedule.percentToFine(10));
        eq(120,Schedule.percentToFine(80));
        int previous=0;
        for(int tv=1;tv<=80;tv++) {int fine=Schedule.tvToFine(tv,10,80);if(fine<previous)throw new AssertionError();previous=fine;checks++;}
        List<Schedule.Rule> template=Schedule.template(10,80,127);
        eq(34,template.size());eq(15,Schedule.latest(template,at("2026-11-17 04:00:00")).rule.fine);
        eq("05:51",Schedule.next(template,at("2026-11-17 04:00:00")).rule.clock());
        eq(120,Schedule.latest(template,at("2026-11-17 17:31:00")).rule.fine);
        eq("19:01",Schedule.next(template,at("2026-11-17 17:31:00")).rule.clock());
        eq(15,Schedule.latest(template,at("2026-11-17 22:14:00")).rule.fine);
        eq(at("2026-11-18 05:51:00"),Schedule.next(template,at("2026-11-17 23:59:59")).when);
        template.add(rule("custom",1051,127,30,1));
        eq("custom",Schedule.latest(template,at("2026-11-17 17:31:00")).rule.id);
        eq("custom",Schedule.next(template,at("2026-11-17 17:30:59")).rule.id);
        List<Schedule.Rule> monday=Arrays.asList(rule("monday",480,1,45,1));
        eq(at("2026-11-16 08:00:00"),Schedule.latest(monday,at("2026-11-17 10:00:00")).when);
        eq(at("2026-11-23 08:00:00"),Schedule.next(monday,at("2026-11-17 10:00:00")).when);
        eq(at("2026-11-23 08:00:00"),Schedule.next(monday,at("2026-11-16 08:00:00")).when);
        List<Schedule.Rule> weekend=Arrays.asList(rule("weekend",540,96,60,1));
        eq(at("2026-11-21 09:00:00"),Schedule.next(weekend,at("2026-11-20 12:00:00")).when);
        eq(at("2026-11-22 09:00:00"),Schedule.latest(weekend,at("2026-11-23 08:00:00")).when);
        List<Schedule.Rule> disabled=Arrays.asList(new Schedule.Rule("off","off",0,127,15,false,1));
        eq(true,Schedule.latest(disabled,System.currentTimeMillis())==null);
        eq(true,Schedule.next(disabled,System.currentTimeMillis())==null);
        // Recurring local time must follow DST, not add a fixed 24 hours.
        TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
        List<Schedule.Rule> daily=Arrays.asList(rule("daily",480,127,60,1));
        eq(at("2026-11-01 08:00:00"),Schedule.next(daily,at("2026-10-31 09:00:00")).when);
        eq(at("2026-10-31 08:00:00"),Schedule.latest(daily,at("2026-11-01 07:59:59")).when);
        System.out.println("PASS "+checks+" schedule checks");
    }
}
