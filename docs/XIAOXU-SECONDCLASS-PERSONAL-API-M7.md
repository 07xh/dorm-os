# 校序第二课堂 M7｜可查询的私人 API（2026-11-03）

## 已实现
- `GET /api/v1/second-class`：校序自己的 Cloudflare Pages Functions 私人 API。
  只读取经 Cloudflare Access JWT 验证后，当前用户自己 D1 中的
  `academic_verified_batches` + `academic_verified_records`，不访问其他用户。
- 无 Access、不具备 D1，分别返回 401/503，所有私人响应 `private,no-store`。
  查询接口没有 POST/PATCH，也不会代表用户发起学校报名、签到和提交。
- 业务返回 `status`、`records`、`provenance`、`lastSuccessAt`、
  `lastReadOnlyAt`、`summary`、`automaticSync:false`。
  `not_connected` 时记录为空、summary 为 null；`stale` 不隐瞒历史来源。
- `summary` 中的 recordCount 为本人当前保存的活动条数；
  `recordedHours`、`recordedPoints`、`recordedCredits` 分别提供
  `{knownCount,sum}`；没有字段时 **sum=null，不是 0**。
  这些数值只是**已存活动行数值字段之和**，不能当作学校颁发的累计学时/
  毕业积分或学分；必须单独抓实测学校汇总接口才能出现官方总值。
- 浏览器前端第二课堂现统一从 `GET /api/v1/second-class` 读取，
  在既有教务二级页面展示汇总与活动记录，没有增加重复导航。
  不读取本地的默认示例当作个人数据。
- `POST /api/v1/verified/snapshot` 如果将覆盖
  `provenance=trusted_readonly` 的记录，现在返回 409；
  用户个人上传不能降级覆盖可信学校同步结果。
- 发布门禁对新私人路由进行匿名拒绝验证；Node SQLite/签名 Access JWT、
  前端 HTML 防注入及桌面/手机 Playwright 一并验收。

## API 示例（纯契约，非学校实际响应）
```json
{
  "kind":"second-class",
  "source":"second_class",
  "status":"not_connected",
  "provenance":null,
  "records":[],
  "summary":null,
  "automaticSync":false,
  "syncMode":"awaiting_school_authorized_adapter",
  "lastSuccessAt":null,
  "lastReadOnlyAt":null
}
```

## 真正自动更新仍需验证
M6 `lib/academic-second-class-connector.mjs` 已有授权只读
`verifySession`、`readPage` 契约、分页和受信任 D1 写入，但还没有真实
`dekt.ncwu.edu.cn` 登录后的活动/积分接口证据及安全续期机制。

首次由本人登录官方第二课堂网页，脱敏提供以下每个业务页面的
**只读请求方法/路径/响应字段名**即可：
1. 「我的活动」实际活动 ID、日期、状态，分页游标。
2. 「学时／积分明细」字段语义：预计学时/已获得学时/是否审核。
3. 「累计学时／积分」官方页面原始字段，防止把明细数值错误相加。
不需要密码、学号、Cookie、Token、登录跳转或签到请求。

基于真实请求确认后再补本地/服务端只读适配器、最小权限会话，
逐条与官方页面对照，最后启用授权期间的定时任务。目前不能称为
`已连通华水` 或 `已自动同步`。

## 生产上线前
Cloudflare Access 必须覆盖正式、预览所有 `/api/v1/*` 路径；
D1 0001–0008 按序 **仅执行尚未执行的迁移**，升级前备份，离线
SQLite 预演不能替代生产配置；同一用户手机/平板核对已存记录，
匿名请求必须得到合法 Access 登录跳转或正确的私人 API 拒绝，而非 200/404。
