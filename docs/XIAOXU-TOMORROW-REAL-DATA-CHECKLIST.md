# 校序 Academic OS · 明天接入真实数据的上线验收清单

> 本文面向单人私有使用。不要把学校密码、登录 Cookie、Access JWT、学号和真实学生数据提交到 GitHub 或聊天消息。所有自动同步状态以真实授权查询及逐项数据对照为准。

## A. 本轮无需真实个人数据，代码已准备的能力
- 一套原有 V8.5 网站，保留课表/纸质作业/官方通知/NFC/个人备份；
- D1 结构 0001–0005；云端作业 GET/POST/PATCH、来源健康、任务历史；
- Cloudflare Access JWT 验签、按用户隔离、跨站写入拒绝；
- 旧 `dorm-os-state-v1` 任务可由本人主动预览、确认、分批复制到云端，原浏览器数据不被删除；
- 可信内部只读差异合并（新增、变化、缺项不删、平台提交状态与个人完成状态分开）；
- 个人五域快照 UI，仅表示用户保存的快照，**不代表实时平台已接通**；
- Playwright 桌面/移动端及 SQLite 独立正反例门禁。

## B. Cloudflare 生产环境一次性配置（仍需本人授权）
1. 在 Cloudflare Workers & Pages 的既有项目 `dorm-os` 下确认域名、绑定和 GitHub Actions 部署目标；**不要创建第二个站点**。
2. 创建独立 D1 数据库（预览与生产分开），在 Pages > Settings > Bindings 中添加 D1 绑定，变量名必须是 **`ACADEMIC_DB`**。保存后重新部署，绑定才会生效。
3. 对已有 DB 先导出备份，并确认已应用哪几份迁移。新建空数据库按顺序执行：
```bash
npx wrangler d1 execute YOUR_D1_DATABASE --remote --file=./migrations/0001_academic_core.sql
npx wrangler d1 execute YOUR_D1_DATABASE --remote --file=./migrations/0002_academic_legacy_links.sql
npx wrangler d1 execute YOUR_D1_DATABASE --remote --file=./migrations/0003_academic_verified_snapshots.sql
npx wrangler d1 execute YOUR_D1_DATABASE --remote --file=./migrations/0004_academic_source_course.sql
npx wrangler d1 execute YOUR_D1_DATABASE --remote --file=./migrations/0005_academic_workspace.sql
```
**不要对已执行 0004、0005 的数据库重复运行 ALTER TABLE。** `YOUR_D1_DATABASE` 是 Cloudflare 中实际新建数据库的名字；不在 GitHub 写入数据库密钥。先在预览环境验证相同迁移，确定回滚和备份方案。
4. 在 Cloudflare Zero Trust 建立只允许本人登录的 Access 应用，完整保护 `/api/v1/*` 私人路径，包括 `/api/v1/verified/*`、`/api/v1/assignments/*`、`/api/v1/sources`。Pages 运行环境设置 `ACCESS_TEAM_DOMAIN`（例如 `example.cloudflareaccess.com`）和 `ACCESS_AUD`（该 Access 应用 Audience 值）。
5. 检查匿名请求被拒绝、本人登录通过、跨站修改被拒绝；**不能只在 UI 隐藏入口**。所有私人响应必须 `Cache-Control: no-store`。
6. 每次部署确认 GitHub Actions commit SHA、D1 当前迁移、实际生产 URL 和 `/api/v1/*` 的 Access 覆盖；不要只凭静态首页成功断言数据库已接通。

相关官方文档：
- https://developers.cloudflare.com/pages/functions/bindings/
- https://developers.cloudflare.com/d1/wrangler-commands/

## C. 明天真实数据接入的推荐顺序
1. 先用一条**自己创建的普通手动作业**完成 API GET/POST/PATCH、版本冲突、刷新、另一设备读取、事件历史、断网表现；旧本地任务只在明确预览、确认后迁移。
2. 学习通：在本人合法授权状态下只读取得真实课程、作业稳定 ID、标题、课程归属、完整截止时间和提交状态；分别验证“未交/已交/已评分/没有日期/分页不完整”，避免据缺项删除任务。
3. 华水青果：仅验证实际学校系统的课表、课程/学期、教师、节次、单双周、考试数据契约。不得根据其他学校的青果页面推定华水一定兼容。
4. 雨课堂：优先官方授权和本人导出；不能稳定只读取得时保持“未连接”，使用人工导入，不绕过平台保护。
5. 二课与官网通知先保留现有入口和公开通知采集；没有真实个人授权不假装二课学分已自动同步。

### 学习通最小字段对照
`externalId` 必须稳定、来源绑定到本人课程；`title`、`course`、`status` 与校内真实页面一致；`due` 是确切年月日或空，`dueAt` 是有时区的精确时刻或空。拒绝任何包含 Cookie、签名 URL 或密码的原始日志进入持久化表。云端同步当前只接受授权服务器内部连接器调用，普通页面不能通过用户自报 status 生成“已验证”同步记录。

## D. 成功验收定义
- **真实动态**：刷新页面后，云端已保存任务依然存在；手机和平板使用同一受保护身份读取结果一致。
- **无需重做**：纸质任务、旧备份、原 V8.5 页面与 NFC 不受影响。
- **准确截止**：只知日期绝不假造 23:59；实际截止日变更记录可查询。
- **准确状态**：个人勾选完成与第三方提交状态永远不混淆。
- **失败诚实**：无授权、接口失败、分页不全、会话过期均显示失败或历史缓存，不自动清空数据。
- **可恢复**：迁移失败可安全重试，DB 备份可验证恢复，合并前完整 CI 通过。
- **真实接通**：至少一条本人实际作业先经过合法授权读取并对照一致，然后才能标记该数据源已连接。

## E. 当前仍需要真实授权的数据
- 实际华水教务学期/节次与课程标识；
- 本人学习通登录后只读查询结果、状态和截止日；
- 雨课堂可用官方读取或手动导出方式；
- Cloudflare 私有数据库和 Access 应用的真实绑定/授权。

这些资料明天由本人在官方平台授权/配置即可，不需要今天把个人口令交给开发者。
