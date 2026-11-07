# 校序 · 运动世界校园开源只读桥接（研究/验证层）

关联 [#302](https://github.com/07xh/dorm-os/issues/302)。不再把“第三方官方开放 API”当作必要条件，优先复用 [Inverx/com.zjwh.android_wh_physicalfitness](https://github.com/Inverx/com.zjwh.android_wh_physicalfitness)（MIT）已有的 **7.3.92 个人只读查询**。

## 复用的现成方法

锁定参考版本 `412a2e28bed209201057bf8b943fcd0a0e2211c1`：

| 现有上游类 | 已实现的方法 | 校序用途 |
| --- | --- | --- |
| `profile.client.ProfileClient` | `get_user_info` | 本人账号核验 |
| `policy.client.PolicyClient` | `get_semester_summary`、`get_semester_info` | 学期汇总/目标的候选字段 |
| `history.client.HistoryClient` | `get_run_records` | 历史分页查询的候选字段 |

本目录**不复制上游协议实现**，只通过注入的三个上游客户端对象调用上述方法。绝不初始化包含跑步上传等能力的 `SportClient792`；不导入 `running`、`telemetry`、`ads`、`login`，不自动处理验证码或生成模拟设备参数。

`sport_world_readonly_bridge.py` 提供：

- 严格 HTTPS + exact host/path/method 只读白名单；阻止跑步结算、OBS 上传、用户事件等其他端点；
- 三个客户端必须属于**同一个本人、同一正常授权会话**；每次读取前后向个人资料只读接口核实身份；
- 接口响应必须业务成功 `error == 10000`；仅 HTTP 200 / `r/s/v` / 异常响应一律不认定为成功；
- 只取必要的候选字段，并删除原始响应中的其他字段；没有验证的里程口径不自动变成“计入成绩里程”；
- 历史列表同时支持 `data = [...] ` 与 `data.recordList = [...]`；只有明确的 `hasNext`/`pages`/`total` 元信息才确定最后一页；
- HMAC-SHA256 本人会话主体指纹；需要宿主提供仅本地保存的随机密钥（不能是固定全局密钥）；
- `verifiedFieldSemantics=false` 永远不会由该兼容性探针自动翻转为 true。

**关键区别：** 这是可实际调用开源只读方法的适配层，不是校序 App 已经能自动获取官方成绩。运动世界校园的正常授权会话、当前客户端版本兼容、真实身份与成绩数值仍须本人实测。

## 如何接现有架构（不再重建模型）

```text
来自本人正常授权会话的已认证开源只读客户端（外部 Python 运行时）
                 │
                 ▼
SportWorldReadOnlyBridge（本目录，白名单 + 身份/业务校验）
                 │    仅输出脱敏候选 fields / recordsPayload
                 ▼
CampusRunAuthorizedDecodedReader（#368 Android 数据边界）
                 │    经官方页面核验键名、单位、分页后才能正式实现
                 ▼
CampusRunObservedMappingSource（#368） → CampusRunReadOnlyQuery（#367）
                 │
                 ▼
校序现有「校园服务」界面
```

Cloudflare Pages/Workers 不运行这个 Python 库，Android 原生运行时也没有默认 Python 环境。**本 PR 不新增网络桥接服务、手机端密码采集、重复登录页或“同步成功”按钮**。未来如果实际选择本机/个人服务器的受控 Python 运行时，必须另外实现安全传输、会话隔离和 Android 真实读取验收，才能称自动查询接通。

不允许把旧 HAR 的 Token、Cookie、账号密码、设备识别码、个人真实成绩提交到 GitHub。不要运行上游的跑步提交、设备模拟、验证码自动处理或广告模拟能力。

## 无个人账号的验证

本仓库提供完全离线的合成单元测试：

```bash
python -m unittest discover -s integrations/campus-run-readonly -p 'test_*.py' -v
```

该命令不会访问运动世界校园、不需要登录或网络。可选的上游兼容测试仅导入 MIT 仓库中三个真实只读类，并以离线桩替代所有 HTTP 请求；用于判断开源源码的读方法签名是否改变。

验收门禁保持不变：①本人正常登录态；②当前版本真实业务结果；③学期总里程与计入成绩里程分别比对；④历史状态和完整分页；⑤ Android 16 本机同步、断网与账号切换。任何一项未完成都不得在 App 显示“官方成绩已同步”。

## 第四阶段：Android 解析同一份只读结果（当前为离线端到端测试）

本地 Python `SportWorldReadOnlyBridge` 新增 `read_subject()`；已有 `read_semester()` 和 `read_history_page()` 输出增加 `bridgeSchemaVersion=1` 和 `bridgeKind=subject/semester/history`。不会修改用户的上游账号或请求协议，不会传出原始 token。

Android 新增 `CampusRunBridgeJsonReader`，仅用于读取由可信、已授权传输取得的 JSON，**无独立登录系统、网络客户端或 Compose 新页面**。它复用原有 `CampusRunAuthorizedDecodedReader` → `CampusRunObservedMappingSource` → `CampusRunReadOnlyQuery`。

Python 桥接的候选字段始终明确标注 `verifiedFieldSemantics=false`；Android 在正式数据映射前**必须核验为 true**，否则返回 `INVALID_DATA`，绝不会显示为官方可信成绩。历史 `paginationVerified=false` 或 `isLastPage=null` 也会拒绝；不能用短列表猜测已读取完全部历史。

这里的 `verifiedFieldSemantics` 是可信数据源适配器在**本人官方 App 逐项比对后**设置的内部条件，而不是用户可以编辑的导入 JSON 自证。仅凭一个 JSON 布尔值不具备身份认证或数据来源证明。未来只能从经身份和完整性验收的实际传输渠道注入该 Reader。

**尚未实现/不可冒称完成**：本人已有登录态传给 Python 运行环境、Android 与 Python 的受保护传输渠道、实际个人账号成绩、目标与计入成绩口径核对、一加 Ace 6 真机验证。本轮只验证跨语言格式与错误门禁，**不是**真实账号自动同步。

与此前“必须等待官方开放 API”不同，真实上游仍优先复用 MIT 开源客户端的合法只读功能，不重新抓三份 HAR，不新建成绩数据库、同步 Worker 或登录界面。
