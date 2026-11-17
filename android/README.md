# 定时音量 · Galaxy A7 / Note9

为 Galaxy A7（2016）SM-A7100 / Android 7.1.1 制作的中文卡片式媒体音量控制软件，同一APK也已在Galaxy Note9 SM-N9600 / Android10验证。供持续播放音乐、3.5mm 耳机孔连接音箱使用；音乐由原有播放器播放，本应用只控制媒体音量，不申请音频焦点。

安装包：[GitHub Release 下载](https://github.com/TiercelX/tv-audio-automation/releases/tag/android-v0.2.0)。自行构建输出为 `dist/A7VolumeScheduler.apk`。无需 Root，无联网权限、账号或自动化流程编辑器。包名沿用早期探针的 `io.tiercel.a7volumeprobe`，可直接覆盖升级。

## 使用

1. 打开 **定时音量**，先保持总开关关闭，配置规则。
2. **GitHub 时间表**卡片内置整套 TV 时间点，可独立启停。点击“调整映射与重复日期”，设置手机输出下限/上限和星期；“查看时间表”可展开全部时间点。
3. **我的时间规则 → 添加**：选择时间、重复星期、手机实际输出百分比；支持输入百分比、滑动、每次一个细调单位的加减。每张卡片可启停、编辑、删除。
4. 在“后台运行设置”允许忽略电池优化，并在三星电池管理中允许后台运行。建议接电使用。
5. 连接音箱，打开顶部 **自动调音**。开启时立即应用当前时段音量；关闭时保留当前音量。后台运行时有常驻通知。

初次安装默认：总开关关闭；GitHub卡片启用、每天重复；输出映射10%～80%；没有自定义规则；只在耳机孔连接时写入音量。首次打开不会自动更改音量。

“仅在耳机孔连接时调音”开启后，拔下音箱暂停写入，重新插入后应用当前时段。蓝牙A2DP连接时不会把它当成有线音箱。“手动试听一个音量”点击应用后才写入；试听或音量键调整保持到下一个规则时间点。

## TV 1～80 → 手机10%～80%

按系统细调全范围0～150计算手机百分比，不使用此前Note9的2～9档范围，也不使用G1或Sound Blaster Z的响度曲线。

```text
手机理论输出百分比 = 下限 + (TV原值 - 1) × (上限 - 下限) / 79
系统细调目标 = round(手机理论输出百分比 × 150 / 100)

默认下限10、上限80：
TV  1 → 10.0000% →  15/150 → 回读显示10.0%
TV 40 → 44.5570% →  67/150 → 回读显示44.7%
TV 80 → 80.0000% → 120/150 → 回读显示80.0%
```

仅在映射到细调值时取整一次。手机实际控制步进是1/150（约0.667个百分点）；界面显示量化后的实际目标，不声称任意小数百分比都能精确输出。百分比是系统音量控制值，不代表模拟电压或主观响度比例。

## 时间表来源与冲突规则

时间表取自 [TiercelX/tv-audio-automation](https://github.com/TiercelX/tv-audio-automation) 的Ubuntu TV检查点：

- [monitor-volume-today.sh](https://github.com/TiercelX/tv-audio-automation/blob/main/ubuntu/monitor-volume-today.sh)：日间和晚间原始TV值，夜间最后为1。
- [monitor-sa9023-volume-today.sh](https://github.com/TiercelX/tv-audio-automation/blob/main/ubuntu/monitor-sa9023-volume-today.sh)：补齐05:51～07:01对应的TV 5、10、15…40晨间检查点；不复用该脚本的SA9023输出百分比。

本地快照有34个检查点，保留原脚本的+1分钟校正时间。22:06、22:11按原guard目标TV5执行，22:14设为TV1；夜间维持该值至次日05:51。不会自动从GitHub更新规则，也不会把TV1改为静音。

全体启用规则按实际发生时间排序：同一时刻自定义规则优先于GitHub；自定义规则一经执行，保持到下一条启用规则（含GitHub）的时间点。相交日期上同一时刻的重复自定义规则会被界面阻止。日期、星期和时间使用手机当前时区；跨天继承上一条有效规则，手机重启、时间/时区变化时重新对齐。

## 后台实现与限制

Android7上使用 `AlarmManager.setExactAndAllowWhileIdle`，并用前台服务每15秒补查及持有部分唤醒锁，熄屏后不依赖界面存活。主开关关闭时取消闹钟、结束服务并释放唤醒锁。写入失败或有线设备缺席时重试；成功后不反复覆盖用户手动音量。执行记录最多保留80条。

唤醒锁在自动调音开启期间持续持有，适合本机作为接电的全天播放器，会增加耗电；不保持屏幕常亮。仍需允许系统/三星后台运行，强制停止应用后需重新打开。已完成短时真实定时测试，尚未做24小时耐久、真实重启恢复或模拟输出电平/噪声测量。APK已验证上述A7和Note9，不声称兼容其他系统版本的后台限制或其他厂商细调接口。

三星 `semSetFineVolume` / `semGetFineVolume` 已在普通应用UID下验证；不降级到粗档位来冒充细调。历史探针报告见 [docs/fine-volume-probe.md](docs/fine-volume-probe.md) 和 `probe-log.txt`；旧探针不再作为启动器入口。

## 构建和验证

```powershell
.\build.ps1
.\test.ps1             # 103个纯排程检查
.\test.ps1 -Device     # 真机计时测试，会临时改变媒体音量并finally恢复
.\test.ps1 -Ui         # 真机界面测试，截图只包含应用自身界面
.\test.ps1 -Device -Serial <ADB设备序列号>  # 多设备时明确指定目标
```

Windows构建需要JDK17或更新版本、Android SDK平台API28或更新版本、稳定版Build Tools35.0.0或更新版本，以及PowerShell。设置 `JAVA_HOME` 和 `ANDROID_SDK_ROOT`（也接受ANDROID_HOME）；未设置SDK变量时尝试当前用户的标准Android/Sdk安装位置。脚本通过SDK元数据选择工具，不依赖项目作者的磁盘路径。构建不使用Gradle或额外框架。

也可以显式传入路径：

```powershell
.\build.ps1 -JavaHome '<JDK目录>' -SdkRoot '<Android SDK目录>'
.\test.ps1 -JavaHome '<JDK目录>'
.\test.ps1 -Device -Serial '<设备序列号>' -JavaHome '<JDK目录>' -SdkRoot '<Android SDK目录>' -AdbPath '<adb.exe路径>'
```

纯排程测试只需要JDK，不需要连接手机或安装SDK。真机测试会临时改变媒体音量，运行前保持有线连接稳定且不要同时编辑应用配置；UI测试用于默认10%～80%配置、总开关关闭的测试设备。

默认首次构建会生成本地开发签名密钥，保存在Git忽略的 `.keys/probe.jks`（alias为probe，密码android）。请保留私钥，覆盖升级需要同一密钥；也可以通过 `-KeyStore` 指向同格式的既有本地密钥。官方Release使用维护者的既有私钥签名，仓库不包含该私钥；自行生成不同密钥的APK不能覆盖已安装的官方包，卸载重装会清除规则。build、dist目录也忽略；测试APK单独构建，测试类不进入正式APK。

真机测试默认要求恰好一台已授权设备；连接多台设备时必须指定 `-Serial`，不会自动选另一台手机。Note9兼容性记录见 [docs/note9-validation.md](docs/note9-validation.md)。

2026-11-17：103个排程检查通过；真机完成当前时段补齐、手动音量保留、真实下一分钟触发、日志记录、关闭服务及音量恢复测试；主界面、映射窗口和添加规则窗口检查通过。测试时有线音量从60切换到59，结束恢复60，自动调音保持关闭。详细记录见 [docs/validation.md](docs/validation.md)。
