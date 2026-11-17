package io.tiercel.a7volumeprobe;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Native, dependency-free UI sized for the A7's 360dp display. */
public class MainActivity extends Activity {
    private static final int INK=0xff142e37, MUTED=0xff5a7078, TEAL=0xff087f78, BG=0xfff1f5f4;
    private Store store;
    private LinearLayout root;
    private TextView currentText,statusText,nextText,routeText;
    private final Handler handler=new Handler();
    private final Runnable refresh=new Runnable() { public void run() { refreshStatus(); handler.postDelayed(this,2000); } };
    private boolean previewExpanded=false;
    private int d(float value) { return (int)(value*getResources().getDisplayMetrics().density+.5f); }
    private GradientDrawable shape(int color,int radius) {
        GradientDrawable b=new GradientDrawable(); b.setColor(color); b.setCornerRadius(d(radius)); return b;
    }
    private LinearLayout column() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.VERTICAL); return l; }
    private LinearLayout row() { LinearLayout l=new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); return l; }
    private TextView text(String value,int sp,int color,boolean bold) {
        TextView t=new TextView(this); t.setText(value); t.setTextSize(sp); t.setTextColor(color);
        if(bold) t.setTypeface(Typeface.DEFAULT,Typeface.BOLD); t.setIncludeFontPadding(false); return t;
    }
    private void gap(LinearLayout l,int height) { View v=new View(this); l.addView(v,new LinearLayout.LayoutParams(1,d(height))); }
    private void weight(LinearLayout r,View v) { r.addView(v,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1)); }
    private LinearLayout card() {
        LinearLayout c=column(); c.setPadding(d(18),d(18),d(18),d(18)); c.setBackground(shape(Color.WHITE,18)); c.setElevation(d(1));
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2); p.bottomMargin=d(14); root.addView(c,p); return c;
    }
    private Button button(String label,View.OnClickListener listener) {
        Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(14); b.setTextColor(TEAL);
        b.setOnClickListener(listener); return b;
    }
    private TextView link(String label,View.OnClickListener listener) {
        TextView t=text(label,14,TEAL,true); t.setPadding(d(4),d(12),d(4),d(12)); t.setOnClickListener(listener); return t;
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); store=new Store(this);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        render();
        if(store.enabled()) Scheduler.start(this,false);
    }
    @Override protected void onResume() { super.onResume(); handler.post(refresh); }
    @Override protected void onPause() { handler.removeCallbacks(refresh); super.onPause(); }
    private void changed() {
        if(store.enabled()) Scheduler.start(this,true);
        else Scheduler.tick(this,false);
        render();
    }
    private void render() {
        int scrollY=0;
        if(root!=null && root.getParent() instanceof ScrollView) scrollY=((ScrollView)root.getParent()).getScrollY();
        ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(BG);
        root=column(); root.setPadding(d(18),d(24),d(18),d(28)); scroll.addView(root); setContentView(scroll);
        root.addView(text("定时音量",30,INK,true)); gap(root,8);
        root.addView(text("只调媒体音量，让音乐按时间轻重有序",13,MUTED,false)); gap(root,22);

        LinearLayout state=card(), head=row(), title=column();
        title.addView(text("自动调音",19,INK,true)); gap(title,5);
        title.addView(text("开启后立即应用当前时段的音量",12,MUTED,false)); weight(head,title);
        Switch master=new Switch(this); master.setContentDescription("自动调音总开关"); master.setChecked(store.enabled()); head.addView(master); state.addView(head);
        master.setOnCheckedChangeListener((v,checked)-> {
            if(checked) {
                if(Schedule.latest(store.all(),System.currentTimeMillis())==null) { v.setChecked(false); toast("请先启用一张规则卡片"); return; }
                try { new VolumeControl(this); } catch(Exception e) { v.setChecked(false); toast(Scheduler.rootMessage(e)); return; }
            }
            store.prefs.edit().putBoolean("enabled",checked).remove("lastKey").commit();
            if(checked) Scheduler.start(this,true); else Scheduler.tick(this,false);
            render();
        });
        gap(state,18); currentText=text("",27,TEAL,true); state.addView(currentText); gap(state,7);
        routeText=text("",12,MUTED,false); state.addView(routeText); gap(state,12);
        statusText=text("",13,INK,false); state.addView(statusText); gap(state,7);
        nextText=text("",13,MUTED,false); state.addView(nextText);

        LinearLayout preset=card(), presetHead=row(), presetTitle=column();
        presetTitle.addView(text("GitHub 时间表",19,INK,true)); gap(presetTitle,5);
        presetTitle.addView(text("整套时间点，一张卡片管理",12,MUTED,false)); weight(presetHead,presetTitle);
        Switch presetSwitch=new Switch(this); presetSwitch.setContentDescription("GitHub 时间表开关"); presetSwitch.setChecked(store.templateEnabled()); presetHead.addView(presetSwitch); preset.addView(presetHead);
        presetSwitch.setOnCheckedChangeListener((v,on)->{store.prefs.edit().putBoolean("template",on).commit();changed();});
        gap(preset,17);
        TextView range=text("TV 1～80  →  手机 "+store.min()+"%～"+store.max()+"%",19,TEAL,true); preset.addView(range);
        range.setOnClickListener(v->editMapping()); gap(preset,9);
        preset.addView(text(Schedule.daysText(store.templateDays())+" · 早间渐增，晚间渐降，夜间保持低音量",12,MUTED,false));
        gap(preset,8);
        preset.addView(text("细调 "+Schedule.tvToFine(1,store.min(),store.max())+"～"+Schedule.tvToFine(80,store.min(),store.max())+" / 150 · 只在时间点切换",12,MUTED,false));
        LinearLayout presetActions=row();
        weight(presetActions,link("调整映射与重复日期",v->editMapping()));
        presetActions.addView(link(previewExpanded?"收起时间表":"查看时间表",v->{previewExpanded=!previewExpanded;render();}));
        preset.addView(presetActions);
        if(previewExpanded) {
            preset.addView(text("以下是原始 TV 检查时间；保持 GitHub 中的 +1 分钟校正时间。",12,MUTED,false)); gap(preset,10);
            for(Schedule.Rule r:Schedule.template(store.min(),store.max(),store.templateDays())) {
                LinearLayout point=row(); weight(point,text(r.clock()+"  ·  "+r.label.replace("GitHub 时间表 · ",""),13,INK,false));
                point.addView(text(Schedule.percent(r.fine),13,TEAL,true)); preset.addView(point); gap(preset,9);
            }
            preset.addView(link("在 GitHub 查看来源 ↗",v->startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://github.com/TiercelX/tv-audio-automation/tree/main/ubuntu")))));
        }

        LinearLayout customHead=row(); weight(customHead,text("我的时间规则",19,INK,true)); customHead.addView(link("＋ 添加",v->editRule(null))); root.addView(customHead);
        TextView ruleHelp=text("自定义规则直接设置手机输出百分比。同一时刻优先于 GitHub；手动调音保持到下一个时间点。",12,MUTED,false);
        root.addView(ruleHelp); gap(root,14);
        List<Schedule.Rule> customs=store.sortedCustom();
        if(customs.isEmpty()) {
            LinearLayout empty=card(); empty.addView(text("还没有自定义规则",15,INK,true)); gap(empty,8);
            empty.addView(text("例如：周末 09:00 调到 30%。选时间、选日期、设音量就好。",13,MUTED,false));
            empty.addView(button("＋ 添加时间规则",v->editRule(null)));
        }
        for(Schedule.Rule r:customs) {
            LinearLayout c=card(), h=row(), info=column();
            info.addView(text(r.clock()+"   →   "+Schedule.percent(r.fine),22,INK,true)); gap(info,8);
            info.addView(text(Schedule.daysText(r.days)+" · "+r.fine+" / 150",12,MUTED,false)); weight(h,info);
            Switch toggle=new Switch(this); toggle.setContentDescription(r.clock()+" 规则开关"); toggle.setChecked(r.enabled); h.addView(toggle); c.addView(h);
            toggle.setOnCheckedChangeListener((v,on)->{store.put(new Schedule.Rule(r.id,r.label,r.minute,r.days,r.fine,on,r.priority));changed();});
            c.addView(link("编辑规则",v->editRule(r))); info.setOnClickListener(v->editRule(r));
        }

        LinearLayout settings=card(); settings.addView(text("运行设置",17,INK,true)); gap(settings,12);
        LinearLayout wiredRow=row(); TextView wiredInfo=text("仅在耳机孔连接时调音",14,INK,false); weight(wiredRow,wiredInfo);
        Switch wired=new Switch(this); wired.setContentDescription("仅有线输出"); wired.setChecked(store.wiredOnly()); wiredRow.addView(wired); settings.addView(wiredRow);
        wired.setOnCheckedChangeListener((v,on)->{store.prefs.edit().putBoolean("wiredOnly",on).commit();changed();});
        gap(settings,8); settings.addView(text("拔下音箱后暂停写入，重新连接时应用当前时段。",12,MUTED,false));
        settings.addView(link("后台运行设置",v->backgroundSettings()));
        settings.addView(link("查看执行记录",v->showLog()));
        settings.addView(link("手动试听一个音量",v->manualVolume()));
        gap(settings,4); settings.addView(text("自动调音开启时会保持后台计时，耗电会增加，建议接电使用。此软件不播放音乐，请使用原有音乐播放器。",12,MUTED,false));
        root.addView(text("音量百分比是系统控制值，不代表实际响度。\n自定义规则生效后，持续到下一个启用规则的时间点。",12,MUTED,false));
        refreshStatus();
        final int restoreY=scrollY; scroll.post(()->scroll.scrollTo(0,restoreY));
    }
    private void refreshStatus() {
        if(currentText==null) return;
        try {
            VolumeControl v=new VolumeControl(this); int fine=v.read();
            currentText.setText("当前 "+Schedule.percent(fine));
            routeText.setText((v.wired()?"耳机孔已连接":"耳机孔未连接")+" · 细调 "+fine+" / 150"+(v.muted()?" · 媒体已静音":""));
        } catch(Exception e) { currentText.setText("细调不可用");routeText.setText(Scheduler.rootMessage(e)); }
        statusText.setText(store.enabled()?store.prefs.getString("status","正在启动…"):"自动调音已关闭，当前音量保持不变");
        Schedule.Event next=Schedule.next(store.all(),System.currentTimeMillis());
        if(next==null) nextText.setText("启用规则后可查看下一次切换");
        else {
            String when=new java.text.SimpleDateFormat("MM-dd HH:mm",Locale.ROOT).format(new java.util.Date(next.when));
            nextText.setText((store.enabled()?"下一次 ":"开启后下一次 ")+when+" → "+Schedule.percent(next.rule.fine));
        }
    }
    private void toast(String message) { Toast.makeText(this,message,Toast.LENGTH_LONG).show(); }
    private LinearLayout dialogBody() { LinearLayout b=column(); b.setPadding(d(20),d(10),d(20),d(4)); return b; }
    private CheckBox[] dayPicker(LinearLayout body,int mask) {
        body.addView(text("重复日期",14,INK,true)); gap(body,8);
        LinearLayout days=row(); CheckBox[] boxes=new CheckBox[7]; String[] labels={"一","二","三","四","五","六","日"};
        for(int i=0;i<7;i++) {
            CheckBox box=new CheckBox(this); box.setText(labels[i]); box.setTextSize(12); box.setPadding(0,0,0,0); box.setButtonDrawable(null);
            box.setGravity(Gravity.CENTER); box.setMinWidth(0); box.setMinHeight(d(42)); box.setChecked((mask&(1<<i))!=0);
            final Runnable paint=()->{box.setBackground(shape(box.isChecked()?0xffd8eee8:0xfff0f3f2,8));box.setTextColor(box.isChecked()?TEAL:MUTED);};
            paint.run(); box.setOnCheckedChangeListener((v,on)->paint.run());
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,d(42),1); p.setMargins(d(1),0,d(1),0); days.addView(box,p);boxes[i]=box;
        }
        body.addView(days); LinearLayout shortcuts=row();
        weight(shortcuts,link("每天",v->setDays(boxes,127))); weight(shortcuts,link("工作日",v->setDays(boxes,31))); weight(shortcuts,link("周末",v->setDays(boxes,96)));body.addView(shortcuts);
        return boxes;
    }
    private void setDays(CheckBox[] boxes,int mask) { for(int i=0;i<7;i++)boxes[i].setChecked((mask&(1<<i))!=0); }
    private int getDays(CheckBox[] boxes) { int mask=0;for(int i=0;i<7;i++)if(boxes[i].isChecked()) mask|=1<<i;return mask; }
    private SeekBar slider(LinearLayout parent,int value,int max) {
        SeekBar s=new SeekBar(this);s.setMax(max);s.setProgress(value);parent.addView(s,new LinearLayout.LayoutParams(-1,d(45)));return s;
    }
    private void onSlide(SeekBar bar,Runnable update) {
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int p,boolean user){update.run();}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
    }
    private void fineControls(LinearLayout body,SeekBar volume) {
        LinearLayout controls=row();
        weight(controls,button("−",v->volume.setProgress(Math.max(0,volume.getProgress()-1))));
        weight(controls,button("输入百分比",v->{
            EditText input=new EditText(this);input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL);
            input.setText(String.format(Locale.ROOT,"%.1f",volume.getProgress()*100.0/150));input.selectAll();
            input.setPadding(d(24),d(16),d(24),d(16));
            AlertDialog numeric=new AlertDialog.Builder(this).setTitle("输出音量（0～100%）").setView(input).setNegativeButton("取消",null).setPositiveButton("确定",null).create();
            numeric.setOnShowListener(x->numeric.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(y->{
                try {
                    double percent=Double.parseDouble(input.getText().toString());
                    if(Double.isNaN(percent) || Double.isInfinite(percent) || percent<0 || percent>100) throw new IllegalArgumentException();
                    volume.setProgress((int)Math.round(percent*1.5));numeric.dismiss();
                }catch(Exception e){input.setError("请输入 0～100 之间的数字");}
            }));numeric.show();
        }));
        weight(controls,button("＋",v->volume.setProgress(Math.min(150,volume.getProgress()+1))));body.addView(controls);
    }
    private void editMapping() {
        LinearLayout b=dialogBody();b.addView(text("TV 原始范围固定为 1～80，下面设置手机实际输出范围。",13,MUTED,false));gap(b,16);
        TextView low=text("",17,INK,true);b.addView(low);SeekBar min=slider(b,store.min(),100);
        TextView high=text("",17,INK,true);b.addView(high);SeekBar max=slider(b,store.max(),100);
        TextView preview=text("",13,TEAL,false);b.addView(preview);gap(b,18);CheckBox[] days=dayPicker(b,store.templateDays());
        Runnable update=()->{
            low.setText("最低输出："+min.getProgress()+"%"); high.setText("最高输出："+max.getProgress()+"%");
            if(min.getProgress()<max.getProgress()) preview.setText("TV 1 → "+Schedule.percent(Schedule.tvToFine(1,min.getProgress(),max.getProgress()))+"\nTV 40 → "+Schedule.percent(Schedule.tvToFine(40,min.getProgress(),max.getProgress()))+"\nTV 80 → "+Schedule.percent(Schedule.tvToFine(80,min.getProgress(),max.getProgress())));
            else preview.setText("最高输出需要大于最低输出");
        };onSlide(min,update);onSlide(max,update);update.run();
        ScrollView s=new ScrollView(this);s.addView(b);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("GitHub 时间表 · 音量映射").setView(s).setNegativeButton("取消",null).setPositiveButton("保存",null).create();
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            int mask=getDays(days);if(min.getProgress()>=max.getProgress()){toast("最高输出需要大于最低输出");return;}
            if(mask==0){toast("至少选择一天");return;}
            store.prefs.edit().putInt("min",min.getProgress()).putInt("max",max.getProgress()).putInt("templateDays",mask).commit();
            dialog.dismiss();changed();
        }));dialog.show();
    }
    private void editRule(Schedule.Rule old) {
        LinearLayout b=dialogBody();int now=Calendar.getInstance().get(Calendar.HOUR_OF_DAY)*60+Calendar.getInstance().get(Calendar.MINUTE);
        final int[] minute={old==null ? (now+5)%1440 : old.minute};
        Button time=button(String.format(Locale.ROOT,"%02d:%02d  ·  点击选择时间",minute[0]/60,minute[0]%60),null);
        time.setTextSize(24);time.setOnClickListener(v->new TimePickerDialog(this,(picker,h,m)->{
            minute[0]=h*60+m;time.setText(String.format(Locale.ROOT,"%02d:%02d  ·  点击选择时间",h,m));
        },minute[0]/60,minute[0]%60,true).show());b.addView(time);gap(b,12);
        TextView output=text("",22,TEAL,true);b.addView(output);
        // 0..150 steps exposes every verified fine-volume value, displayed as percent.
        SeekBar volume=slider(b,old==null?45:old.fine,150);
        fineControls(b,volume);
        TextView info=text("",12,MUTED,false);b.addView(info);
        Runnable update=()->{output.setText("输出音量 "+Schedule.percent(volume.getProgress()));info.setText("细调 "+volume.getProgress()+" / 150 · 直接控制手机输出");};onSlide(volume,update);update.run();gap(b,20);
        CheckBox[] days=dayPicker(b,old==null?127:old.days);
        b.addView(text("这条规则的音量持续到下一条规则的时间点。",12,MUTED,false));
        ScrollView scroller=new ScrollView(this);scroller.addView(b);
        AlertDialog.Builder builder=new AlertDialog.Builder(this).setTitle(old==null?"添加时间规则":"编辑时间规则").setView(scroller).setNegativeButton("取消",null).setPositiveButton("保存",null);
        if(old!=null) builder.setNeutralButton("删除",(v,w)->new AlertDialog.Builder(this).setMessage("删除 "+old.clock()+" 的规则？").setNegativeButton("取消",null).setPositiveButton("删除",(x,y)->{store.delete(old.id);changed();}).show());
        AlertDialog dialog=builder.create();dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(w->{
            int mask=getDays(days);if(mask==0){toast("至少选择一天");return;}
            for(Schedule.Rule r:store.custom()) if((old==null || !r.id.equals(old.id)) && r.minute==minute[0] && (r.days&mask)!=0) {toast("这个时间和日期已有自定义规则，请修改原规则");return;}
            store.put(new Schedule.Rule(old==null?UUID.randomUUID().toString():old.id,"自定义规则",minute[0],mask,volume.getProgress(),old==null || old.enabled,1));
            dialog.dismiss();changed();
        }));dialog.show();
    }
    private void backgroundSettings() {
        PowerManager power=(PowerManager)getSystemService(POWER_SERVICE);
        String message=(power.isIgnoringBatteryOptimizations(getPackageName())?"已允许忽略系统电池优化。":"尚未允许忽略系统电池优化。")
            +"\n\n建议允许后台运行，并在三星电池管理中将本应用设为不监视。系统省电限制可能延迟规则；强行停止后需重新打开应用。";
        new AlertDialog.Builder(this).setTitle("后台运行").setMessage(message).setNegativeButton("关闭",null).setPositiveButton("前往设置",(v,w)->{
            try {startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,Uri.parse("package:"+getPackageName())));}
            catch(Exception e){startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));}
        }).show();
    }
    private void showLog() {
        TextView t=text(store.prefs.getString("log","暂无执行记录"),13,INK,false);t.setTextIsSelectable(true);t.setPadding(d(20),d(10),d(20),d(10));
        ScrollView s=new ScrollView(this);s.addView(t);new AlertDialog.Builder(this).setTitle("最近执行记录").setView(s).setPositiveButton("关闭",null).show();
    }
    private void manualVolume() {
        try {
            VolumeControl control=new VolumeControl(this);
            if(store.wiredOnly() && !control.wired()){toast("请先连接耳机孔音箱");return;}
            LinearLayout b=dialogBody();TextView amount=text("",24,TEAL,true);b.addView(amount);
            SeekBar volume=slider(b,control.read(),150);Runnable update=()->amount.setText(Schedule.percent(volume.getProgress()));onSlide(volume,update);update.run();
            fineControls(b,volume);
            b.addView(text("点击应用后才改变音量，保持到下一个规则时间点。",13,MUTED,false));
            new AlertDialog.Builder(this).setTitle("手动试听音量").setView(b).setNegativeButton("取消",null).setPositiveButton("应用",(v,w)->{
                try {
                    if(store.wiredOnly() && !control.wired()){toast("耳机孔已断开，未应用");return;}
                    control.write(volume.getProgress());
                    Schedule.Event current=Schedule.latest(store.all(),System.currentTimeMillis());
                    if(current!=null) store.prefs.edit().putString("lastKey",current.key()).commit();
                    store.log("手动音量 → "+Schedule.percent(volume.getProgress()));refreshStatus();
                }catch(Exception e){toast(Scheduler.rootMessage(e));}
            }).show();
        }catch(Exception e){toast(Scheduler.rootMessage(e));}
    }
}
