## 2026-11-17 实机接口测试

安装包 `dist/A7VolumeProbe.apk` 以普通应用 UID 10288 运行，只请求 `MODIFY_AUDIO_SETTINGS`，没有使用 Root、系统签名或 shell 权限调用音量接口。ADB 仅用于安装、启动和读取日志。

通过反射确认以下三星固件接口存在：

```java
static boolean semIsFineVolumeSupported();
boolean semIsFineVolumeAvailable();
int semGetFineVolume(int streamType);
void semSetFineVolume(int streamType, int index, int flags);
int getFineVolume(int streamType, int device);
void setFineVolume(int streamType, int index, int flags, int device);
```

前两个支持查询都返回 `true`。对 `STREAM_MUSIC`（3）使用 `semGetFineVolume` / `semSetFineVolume` 得到：

| 操作 | 细调回读 | 标准档位回读 |
| --- | ---: | ---: |
| 原始值 | 100 | 10 |
| 写入 99 | 99 | 9 |
| 写入 98 | 98 | 9 |
| 写入 99 | 99 | 9 |
| 恢复 100 | 100 | 10 |

因此该固件的普通应用可以写入并准确回读标准整数档位之间的媒体音量值。已观察到的标度符合标准档位乘 10；用户的 2～10 档可对应细调值 20～100，但这不是线性响度百分比，也未遍历全部范围验证。原始实机日志见 `probe-log.txt`。

第一轮测试时系统报告 `wired=false`、`bluetooth=false`，输出为手机扬声器。随后用户插上音箱，系统确认 `wired=true`、当前媒体输出为 `headset`，再次得到：

| 有线输出操作 | 细调回读 | 标准档位回读 |
| --- | ---: | ---: |
| 原始值 | 60 | 6 |
| 写入 59 | 59 | 5 |
| 写入 58 | 58 | 5 |
| 写入 59 | 59 | 5 |
| 恢复 60 | 60 | 6 |

**外放及实际有线输出路径的接口写入、回读均成功，且各自恢复了原值。尚未测量模拟信号的实际电平差异、主观听感或噪声。** 写入回读成功不能单独证明模拟输出变化。铃声、通知和闹钟音量未写入。

## 构建与运行

需要 JDK（当前使用已安装 Android Studio 的 JBR）、Android API 28 的 `android.jar` 和 Android Build Tools 35.0.0。`build.ps1` 的参数可指定 Java 和 SDK 路径。当前 SDK 压缩包解压目录名为 `platforms/android-9` 和 `build-tools/android-15`，分别对应下载的 API 28 和 Build Tools 35.0.0 包，并非 API 9/15。

```powershell
.\build.ps1
adb install -r dist/A7VolumeProbe.apk
# 默认仅读取接口和当前状态
adb shell am start -W -n io.tiercel.a7volumeprobe/.ProbeActivity
# 测试：相对原始值降低 1、2、1 个细调单位，finally 恢复原值
adb shell am force-stop io.tiercel.a7volumeprobe
adb shell am start -W -n io.tiercel.a7volumeprobe/.ProbeActivity --ez test true
adb logcat -d -s A7VolumeProbe:I '*:S'
```

签名密钥本地生成，保存在 Git 忽略的 `.keys/`；安装包和构建中间文件也不提交。运行测试时保持输出设备连接不变。此工具是调试探针，不是定时音量控制成品。
