# DORM OS · Ncwu NFC 一次性助手 (0.1 验证原型)

**当前不是已经成功的校园网自动登录器。** 它是一个可以构建和上手机测试的原生 Android 项目；现阶段验证 NFC 调起、联网检查和安全失败行为。

## 已实现

- 原生 NFC NDEF MIME: application/vnd.dormos.ncwu，消息内容 v1:ncwu。扫描时只运行一次检查，不常驻。
- 在用户明确确认覆盖后，可向自己的可写 NDEF 标签写入专用动作。标签不包含任何账号密码。
- Android 网络 API 确认有 Wi-Fi 连接，读取 SSID，严格要求为 Ncwu；无法读取时停止。
- 公网连通性请求使用该 Wi-Fi Network.openConnection，不会经移动数据假装成功。
- 网络不匹配、无权限、检测失败则安全退出；原生功能无需 Root。可选 KernelSU 脚本需手动启用，不存在开机服务、轮询或自动重试。
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

## 诊断摘要（手动复制）

完成一次网络检测后，可点击「复制最近一次诊断摘要」。仅在用户点击后写入手机系统剪贴板，不会上传云端、GitHub 或后台服务。摘要仅含 Android API 级别和诊断结果，不包含账号、Cookie、密码、MAC/IP；非 Ncwu Wi-Fi 的具体 SSID 会被隐藏。仍建议在分享前核对剪贴板内容。


## 自定义 NFC 标签（V0.4）

助手新增本机自定义 NFC 标签管理入口，无需 Root 和 SH：

- 新建标签，设置名称和动作；支持 **Ncwu 网络诊断**、**打开自选 HTTPS 网页**、**打开 DORM OS 学习/休息/开发/出门网页场景**，以及可选的 **KernelSU 固定只读 Wi-Fi 状态 SH**。
- 在「自定义 NFC 标签管理」中选择已有配置，可**写入自有可写标签**、修改名称与动作、删除绑定。写卡需要用户确认覆盖。
- NFC 只写入私有 MIME application/vnd.dormos.ncwu 和 v2:tag:<UUID> 标识；**具体动作及网页地址仅存于此手机的私有应用存储**。改动作不必重写标签，但换手机或清除应用数据后旧标签没有本地绑定，不会执行。
- 原有 v1:ncwu 标签仍然按老流程执行一次诊断，兼容已有贴纸。
- 不允许用户自填 Shell、任意 Android Intent、HTTP 明文网页、含常见凭据参数的网页、未知标签脚本、自动提交密码等。仅新增经过审核的 KernelSU 固定只读脚本。未知/已删除绑定的标签会停止执行。
- 打开 DORM OS 场景仅跳转到当前约定站点并设置网页内模式；**不意味着自动更改安卓系统音量/勿扰，或打开钉钉、微信等外部 App**。
- 首版最多 32 个本地标签绑定；手机解锁后触碰触发。NFC 唤醒行为需一加 Ace 6 真机实测，并确保 Android 16 的「通过 NFC 启动」权限已允许。

### 手机使用步骤

1. 安装本 PR 构建的测试 APK，进入「自定义 NFC 标签管理」→「创建」；
2. 给标签命名并选择动作；仅当选择「打开网页」时输入 HTTPS 链接；
3. 保存后在列表点刚创建的标签，选择「写入」，确认可能覆盖旧 NDEF 内容；
4. 把可写 NFC 标签靠近手机，完成后离开应用再次触碰，验证是否由 NFC 正常唤醒并执行；
5. 如需更改动作，直接在管理中编辑即可，保存后无需重新写入实体卡。

**仅 NFC 触发一次，不后台轮询。学校 Ncwu 的真实登录接口尚未验证，仍不会自动认证账号。**


## V0.5 可选 KernelSU 单次 SH（不依赖 LSPosed）

已增加独立模块工程 modules/dormos-nfc-ksu，APK 不安装或调用 Shell 的默认行为保持不变。

模块只包含 module.prop、skip_mount、action.sh、run.sh、scripts/wifi-status.sh，不包含 service.sh、boot-completed.sh、system.prop 或系统挂载代码。Android NFC 读取由 APK 完成；本地 NFC 配置的动作列表里选择「KernelSU · Wi-Fi 网卡状态（只读 SH）」后，首次需在 APK 内点击「KernelSU 固定脚本：启用 / 关闭」进行明确许可，并由 KernelSU 管理器自主决定是否授予此 APK 的 su 权限。所有检查只能一次性执行。

- 脚本调用固定模块路径和固定 wifi-status 参数。标签和网页不能决定 Shell 内容、命令参数或执行路径。
- 脚本只读判断 wlan0 设备状态，不发送校园网凭据，不输出 MAC、IP、BSSID；不代表已经实现 Ncwu 登录。
- Root 是高风险权限，APK 默认关闭此功能，遇到不明来源的模块不要安装。
- 可通过 GitHub Actions 的 KSU ZIP 构建产物进行手动模块安装；该 ZIP 仅限拥有 KernelSU 的设备，且必须先完成真机验收。
- KSU 管理器的 Action 按钮可手动测试脚本，首次 NFC 测试应使用自有空白标签、解锁手机操作。

**避免对使用限制的误解：**脚本模块并不能独自读取 NFC；需要 Android APK 接收 NFC Intent。LSPosed Hook 系统 NFC 框架在此方案中无必要。可参考 [KernelSU 官方模块文档](https://kernelsu.org/guide/module.html) 与 [NFCC (MIT)](https://github.com/PATILYASHH/nfcc) 的本地 NFC 场景理念，但本次未复制第三方代码。

## 0.7 Android 16 Wi-Fi 身份与 HTTPS 链接验收

- SSID 优先从被选定 Wi-Fi `NetworkCapabilities.transportInfo` 中获取；仅在系统隐藏该值时回退旧式 Wi-Fi 信息。所有 HTTPS 测试仍绑定目标 Wi-Fi，不允许通过移动数据冒充成功。多个网络或无法确认 Ncwu 时安全停止。
- 自定义 HTTPS URL 会检查查询参数与片段中编码后的常见密钥字段，例如 `access%5Ftoken`、`client_secret`；仍需人工检查网址是否带私人信息。
- 原生 JUnit 新增网络身份优先级、未知 SSID、恶意链接和正常学习网址测试。真实 Android 16 NFC 读写仍需手机验收。
