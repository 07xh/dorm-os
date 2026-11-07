# 校序校园跑 · 第三阶段：复用已发现接口与保守数据映射

跟踪 [#302](https://github.com/07xh/dorm-os/issues/302)，基于已合并的 [#367](https://github.com/07xh/dorm-os/pull/367)。目标是复用之前的 HAR **只读**端点和已有查询管线，而不是重新寻找接口或增加第二个登录器。

## 既有本人 HAR 证据（不含任何身份与请求正文）

2026-11-06 已审查三组本人 HAR：107 条（首页/学期/日历），47 条（响应 status=0、无正文），53 条（全部 HTTP 200，保存正文）。2026-11-07 从用户私有文件库找回三份原始 HAR，仅作离线结构审计，不上传公开仓库。

经重新离线验证：107 条那份中，官方 run/discovery 域名有 **40/40** 条带正文的 JSON 业务响应是且仅是 `r/s/v`；53 条那份为 **41/41**；两份共 **81/81**。全部 `r`、`s` 值均是合法 Base64，`v` 均为整数。

- 学期汇总 `/recordssummary/semester`：`r` 长度 364 个字符（Base64 解码后 **272 字节**），`s` 长度 172（解码后 **128 字节**）。
- 历史记录 `/runnings/records`：`r` 长度 12,716（**9,536 字节**），`s` 长度 172（**128 字节**）。
- `r` 字节长度均为 16 的倍数，且不是直接可解析的 UTF-8 JSON；这与加密载荷结构相符，但**不能仅据此证明具体算法或取得业务明文**。
- 对应 POST 请求体只有 `d/h/k/p/t` 这五个顶层键，亦为封装格式；未从现存 HAR 得到可验证的业务明文/授权状态。HTTP 200 仍不是成绩确认。

本地审计仅输出长度、字段名、类型和计数，**不**输出或保存原始请求头、身份、Token、Cookie、响应密文或个人运动数值。

| 曾观测端点 | 方法 | 用途候选 | 可映射级别 |
| --- | --- | --- | --- |
| `/api/v55/runnings/recordssummary/semester` | POST | 学期汇总 | 路径与包裹结构；业务字段未解出 |
| `/api/v70230/runnings/records` | POST | 历史跑步记录 | 路径与包裹结构；分页字段未核对 |
| `/api/v41/running/getPersonalSemesterInfo` | POST | 个人学期 | 路径；内容未核对 |
| `/api/v70100/run/data/index` | GET | 首页数据 | 路径；内容未核对 |
| `/api/v76/user/homePageBaseUserInfo` | GET | 用户基础信息 | 路径；真实身份字段未核对 |
| `/api/v74/runnings/indoor/findForPage` | POST | 室内记录 | 独立来源；不得混入室外有效里程 |
| `/api/v70100/run/calendar/list` | POST | 日历 | 日期候选，不能作为成绩证明 |
| `/api/v70100/run/getHistoryConfig` | GET | 历史查询配置 | 不代表历史记录本身 |

## 本次源码

1. `CampusRunObservedEndpoints.kt`：以上**只读路径**和严格 `r/s/v` 形状检查，绝不把 HTTP 200 或编码响应当作业务成功；不重放抓包凭据、不输出编码载荷。
2. `CampusRunObservedMappingSource.kt`：显式字段绑定、单位转换、可选记录计入里程、按官方明确字段判定达标状态；未知字段保留 `null`，不使用 `status=1`、距离阈值推算计入成绩。
3. `CampusRunObservedMappingSource` 实现现有 `CampusRunReadOnlySource`，可直接接 #367 的 `refreshSummary` 和 `refresh`。只有**授权读取、已验证且解码后的**数据提供者可以供应数据，未写入真实网络请求。
4. `CampusRunObservedMappingTest.kt`：严格信封、历史旧/新列表形式、单位、空值、状态门禁、身份不一致、与 #367 查询流组合验证，全部使用合成数据。

第三方 [Inverx/com.zjwh.android_wh_physicalfitness](https://github.com/Inverx/com.zjwh.android_wh_physicalfitness) 的 MIT 许可历史只读模块仅用于交叉验证**候选字段名**（例如 `rrid`、`totalDis`、`totalTime`、`complete`、`isValid`）。其历史成绩查询输出还涉及 `sname`、`semesterDis`、`semesterValidDis`、`semesterCount` 和 `semesterValidCount`（参考版本较旧，均属于**待真实确认的候选键名**；不等于当前抓包明文字段，也没有证实“学期目标”键名）。未复制加解密、认证绕过、验证码、轨迹合成、成绩上报等任何实现；候选字段**不等于**本人的 HAR 已经证实。

## 必须保留的区别

- `rawMeters` = 学期累计总里程；
- `effectiveMeters` = 学期明确计入成绩里程；
- `dashboardAccumulatedMeters` = 首页累计，统计区间尚未验证，不能混加；
- `weeklyMeters` = 本周里程；
- `record.rawMeters` = 单次记录总距离；
- `record.effectiveMeters` = **只在官方核实单次计入的字段时绑定**，不能直接把猜测的 `validDis` 映射进去；
- 记录达标和单次异常必须独立判断；`status=1` 单独不构成“计入成绩”的证据。

## 真实验收阻塞（不虚构）

三份原始 HAR 已从用户私有文件库找回并完成离线结构复核，但**没有得到已解码的本人业务响应**，因此不能在 App 上声称已自动读取。没有写死无验证的业务键名或请求参数，也不提供无效果的“同步”按钮。

下一步真正接线之前应具备：①本人正常授权下可使用的只读数据渠道；②对应返回的**明文业务字段**与计量单位；③可可靠验证的上游本人身份及分页终止；④官方 App 同期逐项对照。随后将授权数据提供者注入 `CampusRunObservedMappingSource`，再复用 #367 与已有数据连接中心。Android 16 真机登录、重启、失效、离线和页面数据一致性验收属于下一阶段，不能由本次 JVM 测试替代。

代码完成与通过 CI 仅代表“只读映射底座可用”，**不是已接入真实账号**。

## 2026-11-07 正常授权读取渠道评估与下一步门禁

已在用户原始 HAR 中核实：`recordssummary/semester` 与 `runnings/records` 各有两条成功传输的响应，二者的 POST 请求均带有加密请求封装 `d/h/k/p/t`，并包含会话鉴权标识与加密头。响应 `r/s/v` 仅证明编码传输，不能从 HAR 恢复业务明文字段；本项目不尝试凭据重放、绕过鉴权、提取受保护密钥或调用成绩上报接口。

**渠道可用性审计：**

| 渠道 | 目前证据 | 是否可作为校序数据源 |
| --- | --- | --- |
| 运动世界官方 App 本人正常登录后「我的 → 跑步记录」 | [学校使用指南](https://gt.hbzy.edu.cn/info/1045/1607.htm)确认可查看 | 可用于人工对照；不是自动读取 API |
| 官方 App「我的 → 联系客服」 | 上述指南提供的支持入口 | 可询问本人数据副本或允许的导出方式；未确认已有导出文件 |
| 经官方允许的个人数据文件 | 未找到可核实的用户端导出格式与字段契约 | 文件格式/来源核实后，可接现有解码映射层；目前不实现猜测格式 |
| 独立第三方访问、OAuth/只读 API | 未找到可靠的官方文档或授权流程 | 未认证，不在生产 App 中添加登录/同步入口 |
| 之前的三份 HAR | 请求/响应的编码壳与路径已核对 | **不可**直接当作成绩数据源，禁止上传/重放 |

可交付门禁：拿到正常授权、且**业务明文和计量单位已核对**的数据时，由 `CampusRunAuthorizedDecodedReader` 接现有 `CampusRunObservedMappingSource` 与 #367，不再建立新表、新连接中心或重复数据模型。届时以官方 App 同期截图/个人导出文件逐项核验；需另做 Android 16 真机验收，当前所有 JVM/模拟器 CI 都不是本人跑步成绩的实证。

代码已加入 fail-closed 检查：如果错误地把 `r/s/v` 作为明文 `Map` 交给学期或单次记录映射器，直接抛出 `protected_response_requires_authorized_decoder`，不会把密文误标为 `TRUSTED_READONLY`。此检查只保护数据边界，不具备解码、登录或自动同步能力。
