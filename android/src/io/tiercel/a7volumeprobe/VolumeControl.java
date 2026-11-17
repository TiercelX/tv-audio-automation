package io.tiercel.a7volumeprobe;

import android.content.Context;
import android.media.AudioManager;
import java.lang.reflect.Method;

public final class VolumeControl {
    private final AudioManager audio;
    private final Method get,set;
    public VolumeControl(Context c) throws Exception {
        audio=(AudioManager)c.getSystemService(Context.AUDIO_SERVICE);
        get=AudioManager.class.getDeclaredMethod("semGetFineVolume",int.class);
        set=AudioManager.class.getDeclaredMethod("semSetFineVolume",int.class,int.class,int.class);
        get.setAccessible(true); set.setAccessible(true);
        Method supported=AudioManager.class.getDeclaredMethod("semIsFineVolumeSupported");
        Method available=AudioManager.class.getDeclaredMethod("semIsFineVolumeAvailable");
        if(!(Boolean)supported.invoke(null) || !(Boolean)available.invoke(audio)) throw new IllegalStateException("此设备未开放三星音量细调");
    }
    public int read() throws Exception { return (Integer)get.invoke(audio,AudioManager.STREAM_MUSIC); }
    public boolean wired() { return audio.isWiredHeadsetOn() && !audio.isBluetoothA2dpOn(); }
    public boolean muted() { return audio.isStreamMute(AudioManager.STREAM_MUSIC); }
    public void write(int fine) throws Exception {
        if(fine<0 || fine>150) throw new IllegalArgumentException("音量必须在 0～150 之间");
        set.invoke(audio,AudioManager.STREAM_MUSIC,fine,0);
        int observed=read();
        if(observed!=fine) throw new IllegalStateException("系统回读 "+observed+"，目标 "+fine);
    }
}
