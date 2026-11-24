# 校序校园跑：只读请求预算（72 小时）

本目录的 `CampusRun72HourRequestBudget` 与 `CampusRun72HourReadCoordinator`
为后续可信授权数据源接线准备了**每账号至少 72 小时一次读取事务**的限频边界。

- 进入页面时优先展示同一账号的本地可信缓存；页面切换、重组、重启或点击刷新均不得绕过预算
- 由本地持久化检查点记录上一次尝试时间；在任何外部请求前预留预算，故请求失败也不能立即重试
- 学期成绩与历史详情共享一份预算及同一份完整快照；无论从首页汇总还是历史入口开始，均复用同一次有界读取事务，避免汇总先占用额度后历史页长期无法更新。一次读取事务内部可以包含多个不同的只读 HTTP 请求，**不意味着 72 小时仅发送一个 HTTP 请求**
- 不创建后台循环任务，下一次进入前台且已满 72 小时才具备查询资格
- 不自动登录、换设备、更新 Token 或循环处理鉴权错误；失去授权时由宿主暂停连接并让用户通过正常渠道重新授权
- 只允许已验证的只读接口；不上传轨迹、成绩或设备传感器数据
- 服务器若对 `DeviceId` 或多端会话实施限制，调用只读接口仍可能触发风控或令官方 App 登录失效；限频不承诺消除此风险
- 尚须在真正的 Android 数据源上完成接线、持久化可信缓存和实机验收。**本提交没有启用真实网络同步，也没有改变现有手动校园跑面板**

单测：`CampusRun72HourRequestBudgetTest`。


## 本轮补齐：可信快照的跨重启恢复

- 新增 `AndroidCampusRunTrustedSnapshotCache`，使用 Android Keystore AES-GCM 加密，仅持久化已验证本人只读快照所需的学期指标与记录，不保存密码、会话 Token、GPS 轨迹、设备标识。
- 缓存以已验证的校序 Owner 范围和上游本人主体指纹共同绑定 AEAD AAD；切换账号无法解密上一来源的数据，校序退出登录或上游返回未授权时清除可信缓存。
- `CampusRun72HourReadCoordinator` 可注入 `CampusRunTrustedSnapshotCache`：先恢复最后成功读取的快照（保留真实 `capturedAtEpochMillis`），再检查同一主体持久化的 72 小时预算。未到期不请求接口，也不伪造新更新时间；到期请求失败不覆盖旧缓存。
- 保留历史 `CampusRunSnapshotGate` 的过期语义：旧数据仍可能显示为“过期”，不因冷却期未满而标成实时。
- 新增 `CampusRun72HourReadCoordinatorTest`，纯合成来源核验跨重启、失败不重试、账号切换、退出清理；原有预算测试保留。

**仍未接通的部分：** `CampusRunPanel` 仍为独立手动记录入口；正式 App 尚无当前可用的运动世界正常授权源、可信 Python/Android 运行时与 `verifiedFieldSemantics=true` 的本人实际字段证据。因此不能将缓存类、预算类、Python 研究桥作为真实自动同步功能上线。正式接线时才可以注入经本人实测的 `CampusRunReadOnlySource` 和上述缓存协调器，并同步处理连接中心的授权/撤销生命周期。不额外创建新的数据库、登录器、后台 Worker 或演示数据。

安全边界：限制接口读取到 72 小时一次不保证多设备登录绝对不受影响；服务器仍可能按 DeviceId / 会话策略改变另一端登录状态。需要本人正常账号真机核验。

- 缓存恢复和可信实时读取分开：即使缓存年龄不足 36 小时，跨进程恢复仍由 `CampusRunSnapshotGate.restoreCached` 标为 `Stale(reason=null)`；只有本人授权成功的新读取才会返回 `Current`。72 小时限额内不通过后台验证伪造“实时”。
- 同 App 进程多个 `CampusRun72HourRequestBudget` 实例共享预留锁，避免同时打开面板时竞态重复查询；该锁不提供跨进程保证，正式适配必须只从单一 App 进程调用。


## 可实际使用的过渡方案：本机 JSON 导入（不等于联网同步）

在现有「教务 → 校园服务 → 校园跑」面板添加「导入工具包 JSON（仅本机）」入口。不引入新一级页面、网络组件或登录器。

1. 本人在原有工具包中**合法读取本人数据**，将 `json_client.py score` 或 `all` 的 JSON 结果另存为 UTF-8 本机文件；不要将文件发送到 GitHub、公开 Issue 或云日志
2. 在校序选择 **公里 / 米**（必须亲自核实原始结果单位，默认不选），选择本机 JSON 文件
3. 仅从业务 `error=10000` 的 `semester_completed.data` 中提取 `sname`、`semesterDis`、`semesterValidDis`、`semesterCount`、`semesterValidCount`。归档源码 `score_cli.py` 明确将前两项里程标为**米**，将后两项标为学期跑步次数/有效次数；导入仍强制用户核对实际输出单位。其他包括 Token、排行榜、原始历史、完整请求信息均不保存
4. 展示学期里程及（存在时）次数预览；用户确认后通过现有 `LocalCampusRunStore` Android Keystore 存为 `USER_IMPORT`，绝不变为 `TRUSTED_READONLY`。旧 `manual-v2` 加密记录没有次数字段时仍能读取，保持为未知值
5. 单位不明确、字段损坏、响应失败、数值过大、精度无法无损换算或次数不一致时拒绝导入，不覆盖已有本机成绩；一个学期汇总不是完整历史，不推算计分规则与目标。**外部工具 `json_client.py` 本身不受校序 72 小时限频器控制**，因此建议只按需单次运行 `score`，不要开启工具包原有 60 秒循环

JSON 最大读取 256KB，采用有界流式读取；仅本机处理且不启用外部 HTTP。用户可在无可信实时授权来源时先更新个人记录，而不是面对无效的同步按钮。

仍然缺少正式自动同步的独立授权流程、来自官方页面的单位与身份核验，以及对设备会话影响的真机测试。该过渡方案不代替这些验收。


## 本机 Keystore 读取失败时不丢失数据

原 `LocalCampusRunStore.load()` 在 Android Keystore 解密异常时会调用 `clear()`，可能把临时读取故障直接变成用户记录永久丢失。现改为**只返回不可读取状态、保留密文**：

- `hasEncryptedRecord()` 仅检查当前 owner scope 的 IV/密文键是否仍存在，不尝试显示密文或把无效数据转换成成绩
- UI 显示「本机有加密记录，但目前无法解密」，提供「重试读取」；不可读取时禁用手动修改及文件导入，防止新内容覆盖旧密文
- 删除按钮仍可使用，但展示不可恢复的二次确认；只有用户明确确认才调用 `clear()`
- 加密内容仍严格按现有 owner scope、Android Keystore AES-GCM 和本机存储管理；不上传云端，也不把解密失败当账号授权失败

此防损坏机制尚需真机重启、设备解锁与模拟异常场景验收，不应被当作完整 Android Keystore 恢复能力。


### Android 设备端回归（不使用真实账号）

`app/src/androidTest/.../CampusRunEncryptedStorageTest.kt` 已加入两个真实 Keystore 仪器化用例，通过现有 `Xiaoxu Second-class WebView Runtime` 工作流的 Android 36 模拟器执行：

- 本人手动及 JSON 等效汇总的 1 米精度与次数在 AES-GCM 加密后仍能读回；不同 owner scope 不会拿到这些数据；人为篡改 ciphertext 后拒绝读取但保留密文，只有主动调用 `clear()` 才删除
- 可信只读缓存对相同 owner 和 subject 可以恢复；不同 subject 的 AAD 解密失败但不删除合法原快照；篡改 ciphertext 时也不展示或自动删除

以上只有离线合成数据，没有运动世界会话、Token、真实 GPS 或运动结果提交。通过设备端 CI 之前不能称为已经验收。

## 2026-11-25 调度一致性修复

现有协调器的汇总和历史入口统一调用 `refreshIfDue`，同一 72 小时预算内只发布一份查询快照。历史查询失败保留上次可信快照，不额外预留额度；协程取消正常向上传递。新增两项合成回归用例。此改动没有注册后台任务，未解决真实授权与运行时接线，也未开启上游网络访问。
