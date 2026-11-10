# 华水青果选课助手：安全门禁与只读课程候选协议（M1）

## 变更范围

- `functions/api/v1/_middleware.js` 的青果可信连接器必须显式设置 `NCWU_QINGGUO_TRUSTED_SYNC_ENABLED=true` 才可能注册；未设置、拼写错误、`false` 均拒绝。仍要求加密会话密钥与 D1 实例。
- 禁用时清除上下文已有 `ncwu` 适配器，避免中间件链中的意外启用；其他数据来源保持现状。
- `lib/academic-ncwu-elective-contract.mjs` 新增一个**纯函数**候选数据规范化器。它只处理外部只读来源未来提供的结构化数据，不主动访问华水、不读取现有 Cookie、不创建登录入口或 D1 表。
- 该协议不会生成选课提交地址、请求表单、验证码行为、自动提交任务或高并发轮询。

## 候选模型与来源边界

输入为 `schema=xiaoxu/ncwu-elective-candidate-v1`、`provider=ncwu`、已知学期、来源观察时间与 `classes` 数组。每个教学班有真实来源待核验的课程代码、教学班代码、课程名；可选教师、校区、容量、**来源明示**的剩余名额和周次/节次。

输出始终固定为：

- `verified=false`、`sourceState=unverified`、`readOnly=true`、`submissionAllowed=false`。
- `eligibility=unknown`：剩余名额不等于本人可以选课；不自动推断跨校区、年级、时间冲突或选课资格。
- 缺失的容量与剩余人数为 `null`，**不是 0**，不自动计算余量；发现不一致的数据拒绝解析。
- 不允许 Cookie、Token、登录信息、个人标识、选课提交 URL 或其他未声明字段进入模型；出现重复班级、非法时间或周次直接失败。

它不写入 `academic_verified_records`、Room 课表、现有数据连接状态或生产 D1，符合课表本地导入优先的最新仓库规则。

## 回归测试

`npm run check` 包含：
- `node scripts/xiaoxu-ncwu-trust-gate-tests.mjs`
- `node scripts/xiaoxu-ncwu-elective-contract-tests.mjs`

场景覆盖：开关缺省及错误值、缺少有效 Vault 条件、明确启用、移除旧上下文适配器、未知容量、重复教学班、恶意附加字段与非法课块。

## 未完成门禁

1. 没有从华水真实选课页面读取教学班信息，所有测试数据为明确的虚构样本。
2. 未核对选课规则和各教学班的有效可选范围，协议**不能**直接声明实时余量。
3. 未提供前端选课 UI 或任何自动提交功能；后续如加入只读入口，仍须由本人正常官方授权，并独立核验源站、身份、字段与请求频率。
4. 原青果可信会话即使开关明确启用，也不能仅凭单测宣称真实登录、课表核验或生产部署完成。
5. 不主动修改 Cloudflare 生产环境变量；只有完成本人实测后才可考虑人工启用。

## M2：只读筛选与时间冲突提示

新增 `lib/academic-ncwu-elective-view.mjs`，调用现有候选契约的严格校验，不添加学校 API 请求。

- `filterNcwuElectiveClasses(candidate, filters)` 支持课程名/代码/教学班、教师、校区匹配，并按 `all`、`known_available`、`known_full`、`unknown` 过滤余量；`null`（未知）绝不当作 0 或大于 0。
- `compareNcwuElectiveTimetables(candidate, personalMeetings, {personalScheduleComplete})` 支持星期、节次区间和具体教学周交叉比较。只有确实发现重叠才返回 `conflict_detected`，缺少课程时间或个人课表数据则返回 `insufficient_data`，其余仅返回 `no_known_conflict`（并不代表可选）。
- 所有输出仍保持 `verified=false`、`readOnly=true` 和 `eligibility=unknown`，不能绕过学籍、校区、课程资格或学校正式结果查询。
- 测试：`scripts/xiaoxu-ncwu-elective-view-tests.mjs` 已加入 `npm run check`，使用虚构教学班和个人时间段，无真实学校数据。

这只是可供后续原生 Compose 界面复用的业务逻辑层，**并未接入当前 App UI、学校课程接口或 Room**，也不会自动修改用户课表。未来如接入真实来源，必须明确数据完整性、观察时间、本人授权与请求限频，并由本人在官方选课页面执行最后确认。

## M3：本人官网页面结构验证入口

- **复用现有** `LocalQingguoImportActivity` 的华水教务 WebView、正常学校登录、HTTPS 阻断和页面切换检测，不新增登录器。
- 原有“提取本页课表”不变；增加手动按钮 **“检查选课页面结构”**。只能在 `jwmis.ncwu.edu.cn` 已加载且无错误的页面点击，不自动进入选课或发送任何额外的网络请求。
- `apps/xiaoxu-android/app/src/main/assets/ncwu_elective_source_probe.js` 只列出课程相关标准**表头类别**、表格数量和行数，可以查看同源 iframe；不输出实际单元格值、姓名、学号、原始 HTML、地址、Cookie 或 Token。跨域 iframe 直接跳过。
- 检查结果仅由 App 本机弹窗展示，不上传 Cloudflare/D1、不更新现有个人课表，也不生成被视为真实的教学班数据。
- `scripts/xiaoxu-ncwu-elective-source-probe-tests.mjs`（已纳入 `npm run check`）用虚构页面测试标准表头识别、跨源 iframe 处理、私密列值不得泄漏、不得发出新请求、WebView HTTPS 门禁与导航期间中断。

此操作只能判断当前可访问网页是否出现了可能的选课列表结构，**不证明**实际课程记录、实时余量、可选资格、选课时段或身份已经核验。不能从当前个人课表页面自动推断课程目录。要完成下一阶段的真正教学班列表读取，还需要本人在官方允许的选课页面点击检查，取得不含隐私的结构证据，并对照当前学校规则验收。开关和任何正式来源标签在此之前继续保持关闭。


## M4：Android 本机志愿计划（不等同于选课）

- 教务 → 校园服务 → 选课助手：新增原生 Compose 子页，沿用教务页面导航，不增加底部 Tab；提供“打开华水官网 · 检查选课页面结构”，复用现有 LocalQingguoImportActivity，而不新建学校登录方式。
- `NcwuElectiveWishPlan.kt`：本人**手动录入**学期、课程名、课程代码、教学班代码、可选教师和已知上课时间；只保存本人键入的笔记，不读取、推断或生成任何真实课程列表、余量、报名资格。没有官方批次确认或选课写入能力。
- 本机计划按照学期 + 课程代码 + 教学班代码去重，支持上移、下移和移除，最多 60 项。保存使用独立 SharedPreferences，操作均先严格验证、再原子写入；解码损坏时阻止覆盖旧记录。不会写入课表存储、Room 或 D1。
- 按准确教学周、星期和节次检查不同课程的**手动记录**是否重叠；未知周次/时间只显示“无法完整判断”。同一课程的不同教学班按备选处理，不把它们误当作必须同时选课；结果不能替代学校资格校验。
- 测试：`NcwuElectiveWishPlanTest` 涵盖非法学期、重复教学班、周次解析、同周冲突、跨周不冲突、未知时间、不可信存储字段和编码往返。

### M4 尚未实现的功能与隔离要求

- **未接入华水真实选课课程与教学班数据**；选择项全由本人手动录入，不能宣称官方已读取或已选成功。
- **未建立正式的多教务账号绑定**；此版本的手动计划是同一设备本机笔记，页面明确提示这一限制。正式数据源和账号切换隔离验收前，不能把该计划当作可操作的提交队列。
- 不提供后台轮询、自动提交、请求加速、验证码识别或规避选课限制；任何提交行为仍只能通过本人在学校官方页面完成。
- 不修改 `LocalScheduleStore`、当前课表导入门禁、Cloudflare 配置、生产 D1、现有学校会话或已有任务中心；是否合并/发布以 Android JVM 测试及 APK 构建结果为准。
