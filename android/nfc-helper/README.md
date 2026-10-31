# DORM OS · Ncwu NFC 一次性助手 (0.1 验证原型)

**当前不是已经成功的校园网自动登录器。** 它是一个可以构建和上手机测试的原生 Android 项目；现阶段验证 NFC 调起、联网检查和安全失败行为。

## 已实现

- 原生 NFC NDEF MIME: application/vnd.dormos.ncwu，消息内容 v1:ncwu。扫描时只运行一次检查，不常驻。
- 在用户明确确认覆盖后，可向自己的可写 NDEF 标签写入专用动作。标签不包含任何账号密码。
- Android 网络 API 确认有 Wi-Fi 连接，读取 SSID，严格要求为 Ncwu；无法读取时停止。
- 公网连通性请求使用该 Wi-Fi Network.openConnection，不会经移动数据假装成功。
- 网络不匹配、无权限、检测失败则安全退出；没有 Root、SH、开机服务、轮询或自动重试。
- 不读取也不存储认证密码。压缩业务 JS 中的登录请求没有完成真实验证，暂不提供自动认证。

## 下一步必须核实的证据

已保存的 a41.js 只负责配置与加载：在 isJSMin=1 模式下加载 a40.js；在非压缩模式下加载 a42.js、a43.js 等。该文件能说明 /drcom/chkstatus、/eportal/portal/page/loadConfig、/eportal/portal/perceive 等路径，不证明哪个请求是真实登录提交。

需要从本人正常认证会话中确认：
1. 实际 loadConfig 响应中的 login_method、en_perceive 以及必要的页面标识。
2. a40.js 中登录相关调用和最终提交请求的路径、方法、字段及必要会话状态。
3. 一次成功登录后的服务端状态变化、通过 Ncwu Wi-Fi 的独立公网验证。
4. 密码是否通过安全传输提交；曾保存的页面源代码 enableHttps=0，不能把旧源码当成当前 HTTPS 保证。

使用抓包或 HAR 时须先隐藏账号、密码、Cookie、Token 和其他标识符。未经证据闭合不应猜测参数发送请求。

## 构建方法

需要 JDK 17、Gradle 8.11.1、Android SDK Platform 36。
在 android/nfc-helper 目录执行 gradle :app:assembleDebug；独立 GitHub Actions 会生成调试 APK 文件，供用户自行验证。尚未宣称 Android 真机 NFC 测试通过。

## 验收场景

1. 手机已解锁并开启 NFC；手动打开 App，点击写入，确认旧内容可被覆盖，将自己的可写标签靠近手机；
2. 关闭 App，再碰标签，验证 Android 系统是否直接启动 App，只运行一次诊断；
3. 检查无 Wi-Fi、非 Ncwu Wi-Fi、SSID 权限未授予、已联网、Wi-Fi 被认证页阻断且移动数据可上网的各类情况；
4. Android 16 若 NFC 不启动 App，查看系统设置中 Launch via NFC 特殊应用访问权限；
5. 只有真实登录请求被确认并经过真机验收后，才能在下一版本增加真正的认证适配器。

网页 NFC HTTPS URL 标签与当前 APK 的 MIME 标签是两种类型，不可在未备份时覆盖写入同一枚标签。

## 0.2 诊断可靠性升级（本 PR 分支）

- Android 单元测试覆盖：私有 NFC MIME/负载严格匹配、单一 Ncwu Wi-Fi 网络检查、HTTP 302/307 重定向拒绝成功判定。
- 公网探测仍绑定所选 Wi-Fi 的 `Network.openConnection`，不借用移动数据；多个 Wi-Fi Network 时为安全起见停止而不随机选第一个。
- NFC 标签写入支持明确取消等待，并在写入后尝试回读验证；写入调用成功不等于后续触碰启动成功。
- **诊断 HTTPS 成功不代表 Dr.COM 认证成功**。仍未实现真实账号认证，也绝不会把账号密码提交至 GitHub。
- 当前通过编译/单元测试后仍需要一加 Ace 6 真机验证 NFC、SSID 权限和网络选择。

## 0.3 NFC 竞态保护（本次）

- 两次 NFC Intent 在 2.5 秒内被视为同一次触碰；不会重复发起网络检测（手动检测按钮不受影响）。使用单调时钟，不启用后台定时任务。
- NFC 等待写入、取消、切换 Activity 前后台使用写入 epoch 令牌。取消后迟到的 callback 不能把界面更新成「写入成功」。**注意：取消无法保证中断已经开始的物理写入**，必须重新读取标签核对。
- 写入进行中的第二次写入会被拒绝；原型仍不尝试提交 Ncwu 账号密码。
- 自动化 JUnit 验证重复触碰、单调时钟回绕与取消令牌失效。实际 NFC 标签是否能唤醒应用仍需 Ace 6 真机验收。
