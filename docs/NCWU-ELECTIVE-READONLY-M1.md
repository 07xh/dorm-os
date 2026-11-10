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
