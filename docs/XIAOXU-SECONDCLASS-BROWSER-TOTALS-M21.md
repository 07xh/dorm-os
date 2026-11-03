# 校序 M21｜本人第二课堂素质分总计、志愿时长与活动同步闭环

M20 已实现用户在 `dekt.ncwu.edu.cn` 完成本人正常登录后，通过
ScriptCat 私有存储中的配对密钥定时同步活动。M21 补齐 M20 没有上云的
素质分总计、志愿服务时长字段。

## 数据来源的明确界线

1. **独立学校可信连接器**：仍使用
   `academic_second_class_official_scores`，
   仅当 `academic_verified_batches.provenance='trusted_readonly'` 且
   `batch_id` 完全一致时，在 `officialScore` 字段展示。
2. **用户自己已登录官网的浏览器**：从
   `/sys/stu/score/list` 中 `school_term_index=100` 的合计记录
   取 `score_value`、`score_plus`，经脱敏及严格检查后提交
   `{snapshot,schoolReportedPreview}`。
   `parsePersonalTotals` 只接受两个合法非负数字或 null，
   明确 `origin='unverified_local_browser_export'`；
   学号、姓名、原始响应、Cookie、密码、Ticket 均不会入库。
3. 保存到新的 `academic_secondclass_personal_totals` 表，
   `source='user_browser_export'`；随原本活动批次在同一 D1 事务中更新，
   因此异常时活动与合计均不应部分写入。
4. `GET /api/v1/second-class` 只有当
   `captured_at===academic_verified_batches.last_success_at` 且批次属于
   `user_import` 时，才返回 `personalScore`。
   `officialScore` 和 `personalScore` 完全分离。
5. 校序第二课堂视图单独显示
   “**本人网页同步 · 分数合计**”，包括素质分与志愿服务时长，
   且醒目注明为个人快照、以校方官网为准。

## 部署与不兼容保护

- 数据库只做 0014 前向迁移；保留已存在的 0001–0013 学业、
  个人登录和配对会话表。
- 旧 M20 脚本只上传纯 `snapshot` 仍兼容，新版 0.3.0 上传包裹
  `snapshot` 与 `schoolReportedPreview`。
- 如果用户本人页没有合计记录则不伪造为零，
  新活动批次不应显示旧的过期合计。
- 本功能并未取得学校提供的校序专属 OAuth，云端无法在学校网页
  关闭时无感继续登录；仍依赖本人已登录页面和浏览器脚本运行。

## 验收门禁

`npm run check` 含两个层面的回归：
- 浏览器上传：完整快照 + 合计同事务保存、错误格式拒绝、
  密钥撤销/限流与原有历史保护。
- 第二课堂 API：本人分数与志愿时长能够显示，其他用户不可读，
  可信来源出现时不会把个人快照合计冒充成校方独立可信数据。

此外要求 Playwright 桌面与移动端回归和正式 D1 0014
迁移成功。**真实学校本人页面的运行兼容仍需本人实测**，
不能用合成数据断言学校自动更新已经完成真实验收。
