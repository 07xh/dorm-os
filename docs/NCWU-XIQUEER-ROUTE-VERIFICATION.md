# 校序 · 喜鹊儿青果静态路由证据与只读验收门禁（2026-11-17）

> 来源：本人提供的《三模块接口分析.md》，据称由喜鹊儿 2.6.457 的 `assets/vendorJw.json` 静态提取。这里**只存接口名、别名与参数字段来源**，不存账号、Cookie、Token、HAR、学校响应或密钥。机器可读证据见 [ncwu-xiqueer-route-catalog.json](ncwu-xiqueer-route-catalog.json)。

## 已确认的是什么

- 源文件列出 6 个模块，共 **33 条路由配置**、**28 个去重 action**。这是 **APK 内置配置的静态证据**，**不是**华北水利水电大学在线接口验证。
- `requestTye=.action`、`sysURL` 和 `encrptKey` 属于 APK 厂商配置；原文的内网 IP 是 debug 残留，不能作为学校网关。不得基于这些值直接生成线上 URL、复用默认密钥或推断可用加密算法。
- `extraParam` 字段仅表示该表列出的附加字段，`（无额外参数）` 不等于实际 HTTP 请求无需认证、公共参数、学期参数或请求信封。
- `loginbean.userId`（大写 I）、`loginbean.userid`、`loginbean.xh` 必须严格区分。即使 action 同名，不同模块仍可能有不同参数契约。
- `oriJkap` 是监考安排相关模块，可能为教师场景；**不纳入普通学生优先接入**。含 `add`/`del` 的 6 条记录仅留存静态证据，绝不进入只读调度。
- `getAllJs` 在课表模块应按“教师”理解；`getJsxx` 在空闲教室模块应按“教室”理解，不能按缩写合并。

## 与当前校序代码的实际关系

现有 `lib/academic-ncwu-qingguo-server.mjs` 在官方 `jwmis.ncwu.edu.cn` 的传统 HTML/CAS 路线工作：学校本人身份验证后，仅支持 `readSection(kind='schedule')`，读取 `/hsjw/student/xkjg.wdkb.jsp` 和动态发现的 `wsxk.xskcb10319.jsp`。`trustedSyncEnabled` 默认关闭，课表实际产品路线仍以**本机导入/离线/手动更新**为主。

喜鹊儿 `.action` 网关是否映射到同一学校服务，现无证据。**不得**把 `mycourseschedule.action` 拼到 `https://jwmis.ncwu.edu.cn/hsjw/`，不得把静态清单当作替代已存在的网页登录器。新增适配必须先通过本人授权的只读真实证据，再决定是否复用既有学校连接器的独立读取分支；不能额外创建第二个授权页、账户表或后台同步 Worker。

## 接入优先级（仅候选）

| 优先级 | 业务 | 静态 action 候选 | 后续证据 |
| --- | --- | --- | --- |
| P0 | 课表 | `mycourseschedule`、`getxnxq_kb` | 确认是否与当前官方 HTML 课表同源；真实学期、节次、单双周与课块数对齐 |
| P1 | 考试安排 | `GetXnXqKsLc`、`MyExamArrange` | 官方本人考试列表的真实字段、日期时间、校区/教室、空数据语义 |
| P1 | 考试倒计时 | `getNotOverExam` | 服务端“未结束”范围、时区和考试关联字段 |
| P2 | 教室信息 | `GetJxlbyXQ`、`getJsxx`、`getckkb` | 楼宇与教室含义、周次、时间段和数据权限 |
| 隔离 | 监考安排 | `MyJkAp` 等 | 仅有教师授权场景再单独评估；学生版不调用 |
| 禁止 | 修改类接口 | `kb_add_bz`、`kb_delete_bz`、`ksap_addCustomer`、`ksap_delCustomer`、`jkap_addCustomer`、`jkap_delCustomer` | 不作只读接入 |

## 下一道门禁：本人授权的真实协议证据

**先确认当前华水是否真的调用这组 action**。在本人正常登录且具有查询权限的页面，仅触发一次普通的课表/考试/教室查询，独立核对：

1. 真实学校网关（仅记录是否为学校明确授权的域名和经过验证的路径，不把 debug 地址作为凭证）、HTTP 方法、必要的 action/step/Envelope 层次；
2. 请求**参数名称与类型**、是否有显式学期/分页参数；特别验证 `userid`、`userId`、`xh` 的不同来源；
3. 响应状态、顶层字段名与数据类型、是否有分页、空列表与错误的区分，不需要保存原始个人数据；
4. 官方页面与仅本人可见的结果逐项比对：课表周次/单双周/节次，考试日期/地点，教室楼宇/座位/空闲时段；
5. 断网、登录失效、403/重定向、空响应以及重复查询的行为，确保旧数据与本机课表不会被无证据响应覆盖。

可以在私有环境记录**仅含字段名的脱敏契约**，例如：

```json
{
  "source": "owner-authorized-observation",
  "module": "oriKsap",
  "alias": "detail",
  "actionObserved": "MyExamArrange",
  "schoolGatewayVerified": false,
  "httpMethod": "unknown",
  "requestFieldNames": ["userid"],
  "envelopeFieldNames": [],
  "responseFieldNames": [],
  "responseSampleVerified": false
}
```

以上是**填写模板，不是真实请求结果**。`false`/`unknown` 必须在真实核对后才改变。**不上传**密码、请求头值、Cookie、Token、Ticket、登录后的 URL 查询值、账号/学号、完整 HAR、响应正文及厂商密钥；仅保留不可识别到个人的字段结构。

## 最终准入条件

- **静态可定位**：当前已达成；只有名字和附加参数目录。
- **协议可验证**：真实华水网关、请求形态、会话依赖、响应字段全部确证，且只读取本人有权访问的内容。
- **只读适配代码**：先复用唯一官方授权连接，读操作明确隔离；返回 `unverified` 候选、不触发写操作和定时任务。
- **本人数据可核对**：课表/考试/教室真实字段对照，含空/错/失效情况；本机旧记录无损。
- **正式启用**：对应只读功能自动化测试、仓库 CI 与本人设备验收均通过后，**单独**将其标为已连接。不能因为静态配置、编译通过或合成测试通过而越级。

## 非目标

本证据目录**不实现网络请求、自动登录、选课目录或选课提交**，不修改现有 `NCWU_QINGGUO_TRUSTED_SYNC_ENABLED` 默认安全门禁，不触碰已有 Android Room/ICS/JSON、Cloudflare D1 或学校账号。抢课 EXE 需使用它自己经过真实验证的选课协议，不能引用这里的课表/考试 action 作为选课接口。
