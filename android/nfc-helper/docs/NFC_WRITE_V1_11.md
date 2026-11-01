# V1.11 · NFC 通用文本和网址写入

## 本轮范围
- 延续独立 Kotlin / Compose APK 和原生 Android NFC API；不复刻 NFC Tools 的非开源代码。
- 在现有 NFC 页面加入“读取标签 / 写入内容”双操作，不增加重复底部导航入口。
- 写入 Text RTD（中文文本）或 HTTP(S) URI NDEF 单记录；裸域名默认补 https://。
- 所有写入需先检查草稿并在对话框确认；页面前台 ReaderMode 才能处理实体标签；写完重新从实际标签读回，字节一致才报告成功。
- NFC 标签已有 NDEF 记录：需要显式勾选允许覆盖已有普通内容；任何 DORM MIME 自动化标签无条件拒绝覆盖。
- 不支持对校园卡、门禁、密码保护/非 NDEF 标签写入，不尝试绕过保护；普通读写不调用 KernelSU。
- 保护已有 action.sh/dispatcher.sh/UUID 绑定/执行日志，普通写入不修改 Root 目录。

## 复用与许可证
- 直接使用已有 Android SDK NdefMessage/NdefRecord/Ndef/NfcAdapter 和已安装 AndroidX Compose Material 3；不引入 React Native。
- NFCReader 的 AOSP Text RTD 解码参考及 Apache-2.0 许可保留在 V1.10 读卡器与 assets/licenses，写卡使用 Android SDK 原生实现。

## 自动验收
- NdefWriteRulesTest 覆盖中文、裸域名 HTTPS 默认、HTTP、无效/带账号 URL、字节限制、DORM MIME 防覆盖、普通 NDEF 覆盖确认。
- GitHub Actions 应执行 :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease 和 KernelSU ZIP 测试。

## 真机门禁（不能用模拟测试代替）
- 一加 Ace 6 Android 16：空白 NDEF 卡写入中文文本并立即回读。
- HTTPS 和 HTTP 标签 URL 字符串回读对比，不自动打开网页。
- 已有普通记录：未授权覆盖应拒绝；同意后写入并回读。
- 已绑定 DORM UUID 标签：无论覆盖选项如何都拒绝写入；原有脚本绑定还能使用。
- 容量不足、只读、标签移走、后台离开、屏幕锁定时的实际行为，耗电 A/B。
- 真机验收前 PR #12 保持 Draft，不合并 main、不宣布正式版 APK。
