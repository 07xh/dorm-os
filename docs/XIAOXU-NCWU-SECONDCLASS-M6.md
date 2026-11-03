# 校序 M6｜华水第二课堂专用只读连接器

## 已确认的公开证据（2026-11-03）
- 学校入口 `https://dekt.ncwu.edu.cn/` 实际跳至
  `/rump_frontend/login/?next=...`，再通向华水统一身份认证。
- **公开入口不是可直接访问本人活动/学时/积分的 API。**
- 找到其他学校或自建的第二课堂系统，如
  `https://gitcode.com/lyz66666/campus-growth`（FastAPI/Vue 3），
  `https://github.com/1192704429/campus-club-activity-management-system`。
  它们可用于参考业务字段/权限设计，不是华水生产系统的客户端；
  此次没有复制其代码或宣称这些项目已支持华水。确认许可证及维护状态后才能进一步复用。

## 已落地代码
- `lib/academic-second-class-connector.mjs`：后端/个人本地受信任适配器核心。
  两个依赖接口：`verifySession()` 和 `readPage({cursor,session})`。
  **这些方法需要在本人实际授权的官方学校会话下填充**，本 PR 不包含猜测的 URL、隐式登录、验证码绕过或会话抓取。
- `migrations/0008_academic_second_class_source.sql`：
  在原有个人五域快照中新增来源 `provenance`，历史记录默认为
  `user_import`。只允许服务端受信任读取得到 `trusted_readonly`；
  单独记录本人同步审计（无凭据）。
- `functions/api/v1/verified/[section].js`：现有受 Access 保护的
  `GET /api/v1/verified/second-class`、连接状态、POST 用户个人快照接口；
  只有内部服务端同步写入标记 `trusted_readonly`，
  用户通过 POST 上传的仍为 `user_import`，不可伪造官方已连接。
- `public/xiaoxu-academic-client.js`：在现有界面明确区分
  「本人授权只读同步 / 个人快照 / 数据已过期 / 未连接」，不新建冗余页面。
- `scripts/xiaoxu-second-class-sync-tests.mjs`：
  合成授权、分页、来源字段、重复 ID、异常、用户隔离、空结果证据和
  回滚正反例；`scripts/xiaoxu-db-preflight.mjs` 已包含 0008。

### 内部连接器返回结构（示例，不是华水实际响应）
```js
const adapter = {
  provider: 'second_class',
  async verifySession() {
    // 这里必须对学校的本人授权会话做实际核验，不接受浏览器自报布尔值。
    return {authorized:true,scope:'self',school:'ncwu',
      provider:'second_class',identityBound:true,session:trustedSession};
  },
  async readPage({cursor,session}) {
    // 仅调用已从真实学校页面确认的本人只读请求。
    // 映射实际字段，不推定积分=学时、报名=完成、审核通过=已签到。
    return {activities:[
      {activityId:'activity-123',title:'示例活动',hours:2,points:5,
       activityStatus:'completed',reviewStatus:'approved'}
    ],complete:true,nextCursor:null};
  }
};
await runAuthorizedSecondClassSync(db,userId,adapter);
```
以上数据为文档中的接口样例，不是用户本人活动或已证实的学校 API。

**安全与准确性约束：**
1. 严格限制平台、学校、本人范围、受信任身份绑定、活动稳定 ID 和分页；
   不能用用户发来的 `verified:true` 代替后端授权。
2. 不收集或保存登录口令、Cookie、会话票据、个人原始 HTML、未过滤响应、
   学号、签到二维码、他人信息。禁止报名、签到、学时申报和成绩修改操作。
3. 对完整读取后的活动用同一 D1 batch 原子保存；分页失败/权限过期保留旧批次，
   不把缺失行当作已取消活动。若本人有可确认的空数据则允许保存空批次。
4. 无真实会话或 Cloudflare Access/D1 未配置时依然显示未连接，无法自动同步。
5. 不设置自动抓取频率或 Cron，直到真实平台只读接口和服务端授权路径验收。

## 真正接通华水时仍须验证的证据
1. 由用户本人在官方页面登录，并进入「我的活动」「学时/积分记录」页面。
2. 核验个人页正常加载的、专属本人范围的只读 GET/查询请求或可见 DOM，
   确定实际请求方法、是否翻页、活动稳定 ID、字段含义及登录有效期。
   只能在本人正常会话范围里核验，不要求向聊天发送凭证或 HAR。
3. 先用一条本人实际活动对照标题、日期、状态、学时/积分；再用两页、
   0 条、会话过期、缺失字段验证。字段含义未经官方页面确认则留空。
4. 在用户控制的隔离环境实现 `verifySession` 和 `readPage`；
   若官方页面只允许浏览器会话，就优先保留本机同源只读方式，不把敏感
   会话长期存进 D1、公开仓库或公共代理。
5. 生产先备份 D1 并按顺序只应用未执行的迁移 0001–0008；对本人 Access
   身份验证读回数据与已读状态后才启用自动调度。

## 仍未实现
- 华水第二课堂真实 API URL、页面选择器、实际账号授权及数据对照；
- 可运行的生产会话续期、无人值守自动同步任务；
- 关闭网站后的系统 Push。此 PR 的基础适配器不能代替上述验收。
