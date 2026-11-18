# 校序校园跑：只读请求预算（72 小时）

本目录的 `CampusRun72HourRequestBudget` 与 `CampusRun72HourReadCoordinator`
为后续可信授权数据源接线准备了**每账号至少 72 小时一次读取事务**的限频边界。

- 进入页面时优先展示同一账号的本地可信缓存；页面切换、重组、重启或点击刷新均不得绕过预算
- 由本地持久化检查点记录上一次尝试时间；在任何外部请求前预留预算，故请求失败也不能立即重试
- 学期成绩与历史详情共享一份预算；一次读取事务内部可以包含多个不同的只读 HTTP 请求，**不意味着 72 小时仅发送一个 HTTP 请求**
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
