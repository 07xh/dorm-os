# Ace 6 / Android 16 NFC 与待机耗电真机验收

## 先决条件

- KSU 模块、DORM NFC Manager APK 同版本安装；在 KernelSU 管理器只给此 App Root 授权。
- 在 App 中明确启用 NFC 触发，创建安全的无副作用测试 SH 后写入自己的 NDEF 标签。
- **无需 Termux**。真正认证脚本和任何账号密码不纳入本次耗电、读卡验证。

## Android 16 NFC 启动检查

官方文档：https://developer.android.com/develop/connectivity/nfc/nfc

1. 在 Android 16 系统特殊应用权限打开“通过 NFC 启动”；App 设置页新增跳转入口（`NfcAdapter.ACTION_CHANGE_TAG_INTENT_PREFERENCE`，API 36）。
2. 使用本 App 写入的私有 MIME NDEF 标签；自 Android 16 起，HTTP(S) 链接标签改为 ACTION_VIEW，本项目仍用 MIME ACTION_NDEF_DISCOVERED。
3. 分别核实 App 前台、App 未运行、亮屏解锁、锁屏、息屏五种场景；记录能否拉起、是否询问许可、执行结果及耗时。
4. ColorOS 锁屏/息屏行为必须以真机结果为准；系统可能限制后台 Activity 启动，不能预先保证息屏一定触发。
5. 碰卡写入后回读验证 UUID 与脚本绑定，未绑定标签、关闭 NFC 触发和错误 UUID 均不得执行。

## 真实待机耗电：对照测量而非推测百分比

官方工具说明：https://developer.android.com/tools/dumpsys

选用同一台 Ace 6，同一网络/亮度/信号/位置、同一电量范围、同样屏幕息屏状态。尽量在同温度条件下分别测量：

- A 组：DORM NFC 模块已安装但 App 未启用 NFC 触发；不碰标签。
- B 组：DORM NFC 触发已启用但不碰标签，后台无脚本进程。
- C 组：NFC 启用并进行固定次数（例如 10 次）的主动碰卡，仅使用无副作用测试 SH。

每组开始前使用 `adb shell dumpsys batterystats --reset`，记录开始时系统电量、温度和是否充电；观察期后保存 `adb shell dumpsys batterystats --charged xyz.dormos.nfc` 及系统电池页面读数。按官方文档，batterystats 只是近似功耗统计；不能把 Android UID 统计当成 KernelSU root 子进程的完整耗电。

还可通过 `adb shell ps -A | grep -E 'dormos|script-manager|folder-manager'` 对比空闲时是否有残留执行进程。无常驻服务、无轮询是源码特征，不等于 NFC 本身零耗电。

## 本轮尚未完成

没有连接用户的 Ace 6 真机，未运行电量对照实验，也未得到锁屏 NFC/ColorOS 行为证据。因此不能报告实际耗电百分比或“完全不耗电”。