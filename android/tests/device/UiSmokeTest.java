package io.tiercel.a7volumeprobe;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.*;
import java.util.List;

/** Captures the app's own rendered views, including while the keyguard is up. */
public class UiSmokeTest extends Instrumentation {
    private Activity activity;
    @Override public void onCreate(Bundle args) { super.onCreate(args);start(); }
    private View topView() throws Exception {
        Class<?> global=Class.forName("android.view.WindowManagerGlobal");
        Method get=global.getDeclaredMethod("getInstance");get.setAccessible(true);
        Field views=global.getDeclaredField("mViews");views.setAccessible(true);
        List<?> all=(List<?>)views.get(get.invoke(null));return (View)all.get(all.size()-1);
    }
    private boolean has(View view,String label) {
        if(view instanceof TextView && ((TextView)view).getText().toString().contains(label))return true;
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(has(((ViewGroup)view).getChildAt(i),label))return true;
        return false;
    }
    private boolean click(View view,String label) {
        if(view instanceof TextView && ((TextView)view).getText().toString().equals(label)) {view.performClick();return true;}
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)if(click(((ViewGroup)view).getChildAt(i),label))return true;
        return false;
    }
    private void capture(String name,String required) throws Exception {
        waitForIdleSync();Thread.sleep(300);
        final Throwable[] error={null};
        runOnMainSync(()->{
            try {
                View v=topView();if(!has(v,required))throw new AssertionError("Missing UI text "+required);
                Bitmap bitmap=Bitmap.createBitmap(v.getWidth(),v.getHeight(),Bitmap.Config.ARGB_8888);v.draw(new Canvas(bitmap));
                File file=new File(getTargetContext().getExternalFilesDir(null),name+".png");
                try(FileOutputStream out=new FileOutputStream(file)){bitmap.compress(Bitmap.CompressFormat.PNG,100,out);}bitmap.recycle();
            }catch(Throwable e){error[0]=e;}
        });
        if(error[0]!=null)throw new Exception(error[0]);
        Bundle status=new Bundle();status.putString("stream","PASS UI "+name+"\n");sendStatus(0,status);
    }
    @Override public void onStart() {
        String error=null;
        try {
            activity=startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            capture("home","GitHub 时间表");
            runOnMainSync(()->{
                try {Method edit=MainActivity.class.getDeclaredMethod("editMapping");edit.setAccessible(true);edit.invoke(activity);}
                catch(Exception e){throw new RuntimeException(e);}
            });
            capture("mapping","TV 40 → 44.7%");
            runOnMainSync(()->{try{if(!click(topView(),"保存"))throw new AssertionError("Save missing");}catch(Exception e){throw new RuntimeException(e);}});
            waitForIdleSync();
            runOnMainSync(()->{
                try {Method edit=MainActivity.class.getDeclaredMethod("editRule",Schedule.Rule.class);edit.setAccessible(true);edit.invoke(activity,new Object[]{null});}
                catch(Exception e){throw new RuntimeException(e);}
            });
            capture("add-rule","输入百分比");
            runOnMainSync(()->{try{if(!click(topView(),"取消"))throw new AssertionError("Cancel missing");}catch(Exception e){throw new RuntimeException(e);}});
        } catch(Throwable e){error=e.toString();android.util.Log.e("A7UiTest","FAIL",e);}
        Bundle result=new Bundle();result.putString("stream",error==null?"UI TESTS PASSED\n":"FAILED "+error+"\n");
        finish(error==null?Activity.RESULT_OK:Activity.RESULT_CANCELED,result);
    }
}
