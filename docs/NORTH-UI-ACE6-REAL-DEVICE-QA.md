# 一加 Ace 6 真机验收：North UI 完整源码迁移

本文件用于最终的物理设备验收。GitHub Actions 的 Android Emulator 截图、帧统计不能代替一加 Ace 6 真机结果。

## 设备与证据要求

- 设备：OnePlus Ace 6，优先识别型号 `PLQ110`
- 系统：Android 16（API 36）或 Android 17（API 37）
- Windows 电脑已安装 Android Platform Tools，手机已开启 USB 调试并授权当前电脑
- 记录本次 APK 文件、Git commit SHA、设备型号、系统版本、屏幕分辨率和密度
- 采集脚本只截图和读取 `dumpsys gfxinfo`；不自动登录、不点击课程写入操作、不上传私人截图

## 选择正确的 APK

CI 提供三种不同用途的包：

1. **同包名 Release 候选包**：包名为 `io.github.x07xh.xiaoxu`。只有 CI 明确报告使用仓库固定证书签名成功时，才能作为现有校序的覆盖安装包。它会使用相同应用身份，正常升级安装可保留应用数据。
2. **Ace 6 UI 预览包**：包名为 `io.github.x07xh.xiaoxu.uipreview`，由 Android Debug 证书签名、仅包含 arm64 原生库，可以与当前校序并排安装，但它有独立数据目录，不会读取现有校序的本机课表数据。
3. **普通 Debug 包**：CI 用于编译验证，不作为用户安装包分发。

如果 Release 候选包未签名，或证书 SHA-256 与仓库固定证书不一致，**不要尝试覆盖安装，也不要卸载当前校序**。应先解决签名问题。

## Windows 操作

在手机上确认 USB 调试授权后，在 PowerShell 中执行：

```powershell
adb devices -l
```

同包名且已由 CI 验证固定证书的候选包：

```powershell
$apk = ".\Xiaoxu-1.0.135-PLQ110-arm64.apk" # 将 CI 签名校验通过的 Ace 6 ARM64 APK 原样复制到此文件名
adb install -r $apk
node .\scripts\xiaoxu-ace6-visual-qa.mjs --package io.github.x07xh.xiaoxu --apk $apk --commit <CI提交SHA>
```

如果没有可用的同包名签名候选包，使用 CI 的 Ace 6 UI 预览 APK：

```powershell
$apk = ".\app-uiPreview.apk" # CI Artifact 中的 arm64 UI 预览包
adb install $apk
node .\scripts\xiaoxu-ace6-visual-qa.mjs --package io.github.x07xh.xiaoxu.uipreview --apk $apk --commit <CI提交SHA>
```

脚本不会替用户安装 APK。`adb install -r` 只应用于包名和签名均已验证的同包名候选包；签名不匹配时停止，不要卸载旧应用绕过检查。

## 必验场景

脚本会逐项提示用户手动打开页面，避免自动化误触真实课程：

1. 首页和根导航、底栏安全区
2. 周课表、周次、横向切周、课程卡布局
3. 课程详情底部抽屉
4. 课表设置
5. 周视图/日视图切换与恢复
6. 课程编辑器：只打开检查，最后取消，不保存
7. 主导航快速切换、转场和返回
8. 课表连续滚动、切周、打开/关闭详情和返回设置

每个场景采集 PNG、`gfxinfo` 原始数据和截图 SHA-256。帧数不足 30 的样本会标为不可用；采集脚本不会把短暂静止截图误判为流畅度通过。

## 结果判定

- **视觉**：逐页对照固定上游 v1.0.135 的布局、材质、动画、圆角、间距和系统安全区。
- **交互**：检查导航状态、返回行为、课程详情开合、编辑取消、周/日视图恢复。
- **流畅度**：比较冷启动、连续切页、横向切周、滚动时的原始帧数据；不能只凭单个 janky 百分比下结论。
- **数据安全**：确认课程编辑测试最终取消；确认原有本机课表、提醒和设置未丢失。
- **隐私**：截图可能包含个人课程、教师、地点等信息，默认只保存在本机；分享前自行脱敏。

只有真实 Ace 6 的证据齐全并完成逐页人工审查后，才能把“真机验收”标记为通过。