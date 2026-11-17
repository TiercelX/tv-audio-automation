# Galaxy Note9 兼容性验证 · 2026-11-18

设备：国行SM-N9600，Android10，API29；应用普通UID10614。用户已接入耳机孔音箱，系统媒体输出报告headset。没有对手机进行Root、系统设置放宽或三星后台策略更改。

## 同一安装包

安装现有0.2.0版A7VolumeScheduler.apk，包名和签名不变，未修改应用运行时代码。主界面正确显示当前53.3%、细调80/150，以及TV1～80→手机10%～80%映射卡片。

## 实机计时测试

使用同一个独立签名instrumentation测试APK，调用正式VolumeControl和Scheduler；系统时间未改动。

```text
PASS wired output connected
PASS safe initial volume 80
PASS startup catch-up and fine readback
PASS manual volume preserved before next checkpoint
PASS explicit catch-up reapplies current checkpoint
Waiting for real timer at Wed Nov 18 02:07:00 GMT+08:00 2026
PASS real timed rule through foreground service/AlarmManager
PASS timed execution recorded
PASS master disable
PASS original media volume restored
DEVICE TESTS PASSED; original settings and volume restored
```

因此Note9上三星细调接口无需Root可用，细调80→79可写入并准确回读，且下一分钟真实排程触发成功。测试结束细调恢复80（标准8档），所有应用设置恢复，主开关关闭。不是10%～80%全范围扫音测试。

首次测试时手机亮屏；随后补做熄屏计时测试，开始等待时dumpsys power显示 `mWakefulness=Dozing` 和应用持有的部分唤醒锁，结束后手机仍处于Dozing。02:09:00.112实际切换至细调79，全部检查通过并恢复80及原设置。

```text
Waiting for real timer at Wed Nov 18 02:09:00 GMT+08:00 2026
PASS real timed rule through foreground service/AlarmManager
PASS timed execution recorded
PASS master disable
PASS original media volume restored
DEVICE TESTS PASSED; original settings and volume restored
```

这是显示状态为Dozing时的短期测试，不代表已经进入或验证长期深度设备空闲。24小时运行、长期深度Doze、真实重启、模拟输出电平及噪声未验证。Note9此前高音量的噪声问题不能由接口成功回读排除。此前标准9档对应90/150，即60%；默认80%对应120/150，即12档，输出范围需用户按实际音箱听感设置。

系统电池优化白名单中未找到本应用。正式长期使用前，请在应用“后台运行设置”内允许忽略电池优化，并确认三星后台运行策略。
