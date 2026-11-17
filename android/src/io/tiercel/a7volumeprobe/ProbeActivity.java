package io.tiercel.a7volumeprobe;

import android.app.Activity;
import android.os.Bundle;
import android.media.AudioManager;
import android.widget.TextView;
import android.widget.ScrollView;
import java.lang.reflect.Method;
import java.io.FileOutputStream;

/** Runs as an ordinary app UID, with only MODIFY_AUDIO_SETTINGS. */
public class ProbeActivity extends Activity {
    private final StringBuilder report = new StringBuilder();
    private void line(String text) {
        report.append(text).append('\n');
        android.util.Log.i("A7VolumeProbe", text);
    }
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        AudioManager audio = (AudioManager)getSystemService(AUDIO_SERVICE);
        line("model=" + android.os.Build.MODEL + " android=" + android.os.Build.VERSION.RELEASE);
        line("uid=" + android.os.Process.myUid());
        line("media=" + audio.getStreamVolume(AudioManager.STREAM_MUSIC) + "/" + audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC));
        line("wired=" + audio.isWiredHeadsetOn() + " bluetooth=" + audio.isBluetoothA2dpOn());
        for (Method m : AudioManager.class.getDeclaredMethods()) {
            String name = m.getName().toLowerCase(java.util.Locale.ROOT);
            if (name.contains("finevolume") || name.contains("fine") && name.contains("volume")) {
                line(m.toString());
                if (m.getParameterTypes().length == 0) {
                    try { m.setAccessible(true); line("  => " + m.invoke(audio)); }
                    catch (Exception e) { line("  => " + e.toString() + " cause=" + e.getCause()); }
                }
            }
        }
        try {
            Method get = AudioManager.class.getDeclaredMethod("semGetFineVolume", int.class);
            get.setAccessible(true);
            line("semGetFineVolume(STREAM_MUSIC)=" + get.invoke(audio, AudioManager.STREAM_MUSIC));
            for (java.lang.reflect.Field f : AudioManager.class.getDeclaredFields()) {
                if (f.getName().contains("FINEVOLUME")) {
                    try { f.setAccessible(true); line("field " + f.getName() + "=" + f.get(null)); }
                    catch (Exception e) { line("field " + f.getName() + ": " + e); }
                }
            }
            if (getIntent().getBooleanExtra("test", false)) testFineVolume(audio, get);
        } catch (Exception e) { line("READ ERROR " + e + " cause=" + e.getCause()); }
        try (FileOutputStream out = openFileOutput("report.txt", MODE_PRIVATE)) {
            out.write(report.toString().getBytes("UTF-8"));
        } catch (Exception e) { line(e.toString()); }
        TextView text = new TextView(this);
        text.setText(report.toString()); text.setTextSize(15); text.setPadding(24, 24, 24, 24);
        ScrollView scroll = new ScrollView(this); scroll.addView(text); setContentView(scroll);
    }

    private void testFineVolume(AudioManager audio, Method get) throws Exception {
        Method set = AudioManager.class.getDeclaredMethod("semSetFineVolume", int.class, int.class, int.class);
        set.setAccessible(true);
        int original = (Integer)get.invoke(audio, AudioManager.STREAM_MUSIC);
        int coarse = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
        // Only decrease near the current value. Never test while muted, or if
        // this firmware's getter does not match the expected 0..150 scale.
        if (audio.isStreamMute(AudioManager.STREAM_MUSIC) || original < 2 || original > 150 || Math.abs(original - coarse * 10) > 10) {
            line("WRITE SKIPPED: unexpected/muted scale, original=" + original); return;
        }
        line("TEST BEGIN originalFine=" + original + " originalCoarse=" + coarse);
        try {
            for (int value : new int[] { original - 1, original - 2, original - 1 }) {
                set.invoke(audio, AudioManager.STREAM_MUSIC, value, 0);
                Thread.sleep(400);
                line("WRITE requested=" + value + " fineReadback=" + get.invoke(audio, AudioManager.STREAM_MUSIC)
                    + " coarseReadback=" + audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            }
        } finally {
            set.invoke(audio, AudioManager.STREAM_MUSIC, original, 0);
            Thread.sleep(400);
            int restored = (Integer)get.invoke(audio, AudioManager.STREAM_MUSIC);
            line("RESTORE requested=" + original + " fineReadback=" + restored
                + " coarseReadback=" + audio.getStreamVolume(AudioManager.STREAM_MUSIC));
            if (restored != original) throw new IllegalStateException("Restore mismatch");
        }
        line("TEST END");
    }
}
