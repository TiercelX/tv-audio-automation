# 2026-11-17 验证记录

设备：SM-A7100，Android7.1.1/API25；普通应用UID10288；耳机孔已连接用户音箱。

## 排程检查

`test.ps1`：PASS 103 schedule checks。覆盖映射端点1→15、80→120、中点40→67和单调性；夜间及跨天继承；下一检查点严格晚于当前时间；同刻自定义优先；工作日/周末/单周一；关闭规则；跨周；夏令时变化前后按当地日期排程。

## 实机计时

`test.ps1 -Device`的独立签名instrumentation测试在目标应用进程内调用正式排程器，没有改变系统时间。

```text
PASS wired output connected
PASS safe initial volume 60
PASS startup catch-up and fine readback
PASS manual volume preserved before next checkpoint
PASS explicit catch-up reapplies current checkpoint
Waiting for real timer at Tue Nov 17 22:21:00 GMT+08:00 2026
PASS real timed rule through foreground service/AlarmManager
PASS timed execution recorded
PASS master disable
PASS original media volume restored
DEVICE TESTS PASSED; original settings and volume restored
```

等待期间dumpsys power显示 `mWakefulness=Asleep` 和 `PARTIAL_WAKE_LOCK 'A7Volume:scheduler'`；服务为前台服务，AlarmManager记录22:21的实际RTC_WAKEUP闹钟。此测试证明短时熄屏能执行，不等同于24小时耐久或厂商长期后台清理测试。

测试临时规则在下一分钟将细调60变为59，并恢复60；铃声、闹钟和通知未改动。finally恢复全部应用设置，主开关原为关闭，结束后仍关闭。

## 界面检查

```text
PASS UI home
PASS UI mapping
PASS UI add-rule
UI TESTS PASSED
```

实机主界面截图及应用自身视图绘制的映射、规则编辑窗口均已目视检查：中文布局完整；TV40目标显示44.7%；星期、滑杆、输入百分比、加减控制与保存/取消按钮可见。系统图案锁不被测试代码解除，视图截图只包含应用自身界面。

## 尚未验证

- 连续24小时运行、真实重启恢复。
- 模拟输出电平与噪声、10%～80%全范围主观试听。
- 其他手机和系统版本。
