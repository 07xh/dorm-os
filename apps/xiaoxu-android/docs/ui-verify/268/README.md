# Issue #268 / PR #269 视觉核验截图

## 采集环境

| 项 | 值 |
| --- | --- |
| 设备 | Android 14（API 34）`google_apis;x86_64` 模拟器，AVD `xiaoxu34`，无窗口模式 |
| APK | 本分支 `:app:assembleDebug` 产物，`adb install -r` 实装 |
| 宽度档位 | `wm size` + `wm density` 切换：393dp（默认 1080×2340@440）、360dp（@480）、432dp（@400）、800dp 平板（1600×2560@320） |
| 截图方式 | `adb exec-out screencap -p`，未经任何后期修改（仅转 WebP 压缩） |

**不是** HTML 预览稿，也不是设计稿截图。

## 两种状态怎么来的

- **空状态**：`adb shell pm clear io.github.x07xh.xiaoxu` 后冷启动，没有任何课表与任务记录。
- **已导入状态**：把下面这份 `.ics` 推进 `/sdcard/Download/`，在应用内走「课表 → 从手机导入 → 选择文件 → 确认保存」，由应用自己的 `IcsTimetableParser` + `LocalScheduleStore` 解析入库，再从首页看渲染结果。

```ics
BEGIN:VCALENDAR
VERSION:2.0
BEGIN:VEVENT
DTSTART;TZID=Asia/Shanghai:20261105T080000
DTEND;TZID=Asia/Shanghai:20261105T094000
SUMMARY:课程导入验证（示例）
LOCATION:教三楼302
DESCRIPTION:教师：示例教师\n课程节次：1\,2\n原始周次：9\,10
STATUS:CONFIRMED
END:VEVENT
BEGIN:VEVENT
DTSTART;TZID=Asia/Shanghai:20261112T080000
DTEND;TZID=Asia/Shanghai:20261112T094000
SUMMARY:课程导入验证（示例）
LOCATION:教三楼302
DESCRIPTION:教师：示例教师\n课程节次：1\,2\n原始周次：9\,10
STATUS:CONFIRMED
END:VEVENT
END:VCALENDAR
```

课程名写的是「课程导入验证（示例）」，**不是任何真实课程**；界面上该记录始终带「本机导入 · 待核对」与「待核对」徽标，并注明「本机文件导入，不等于学校实时同步」。

## 文件清单

| 文件 | 宽度 | 状态 |
| --- | --- | --- |
| `home-360dp-empty.webp` | 360dp | 空状态 / 未连接 |
| `home-393dp-empty.webp` | 393dp | 空状态 / 未连接 |
| `home-432dp-empty.webp` | 432dp | 空状态 / 未连接 |
| `home-800dp-tablet-empty.webp` | 800dp 平板 | 空状态，双列 + NavigationRail |
| `home-360dp-imported.webp` | 360dp | 已导入本机课表 |
| `home-393dp-imported.webp` | 393dp | 已导入本机课表 |
| `home-432dp-imported.webp` | 432dp | 已导入本机课表 |
| `home-800dp-tablet-imported.webp` | 800dp 平板 | 已导入本机课表，双列 |

## 没有覆盖到的状态

- **官方课表 stale（上次读取、待刷新）**：需要真实云端会话并制造一次刷新失败，模拟器上没有账号，只能由代码与静态断言覆盖，未截图。
- **真实 Android 16 物理设备**：本轮全部截图为 Android 14 模拟器。
- 深色主题（未实现）、TalkBack、字体缩放 2.0、平板横竖屏切换。
