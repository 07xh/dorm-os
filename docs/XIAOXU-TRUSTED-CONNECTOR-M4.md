# 校序 M4：可信只读连接器的通用运行边界

## 已完成：不依赖本人数据
`lib/academic-connector-runner.mjs` 提供内部函数 `runTrustedConnector(db,userId,serverAdapter,options)`。这是服务端适配器的统一接口，**不是浏览器对任意平台的万能登录接口**。

适配器必须由受信任的服务端代码创建，并实现：
```js
const adapter = {
 provider: 'chaoxing',
 async verifySession() {
   // 这里必须真正核对本人经合法授权的会话和账户范围。
   // 不能仅通过客户端 JSON 中的 verifiedSchool 或 complete 字段。
   return {authorized:true,scope:'self',provider:'chaoxing',session:opaqueSession};
 },
 async readPage({cursor,session}) {
   // 真实只读查询，并转换为 1-20 条 xiaoxu-task-batch-v1 标准任务记录。
   return {tasks:[/* ... */],complete:false,nextCursor:'next-page'};
 }
};
```
这里是**接口样例而非真实连接器实现**。不得在 GitHub、D1 或日志储存明文账号、Cookie、session、签名 URL、原始 HTML。

工作逻辑：
1. 服务端适配器独立验证合法授权本人会话；
2. 验证 provider、本人范围；未授权直接拒绝，旧连接标记 `expired`；
3. 读取一页（最多 20 条），校验稳定 externalId、截止时间和提交状态；
4. 仅第一次真正读到合格页面后，在 D1 建立或恢复 `mode=readonly` 连接；
5. 每页调用现有 `applyReadOnlySnapshot`，仅最后完整页标记 complete，中间页 partial；
6. 真实源数据暂时缺项不会删除已有任务；失败时保留已落库的有效批次，连接状态标记 error/expired，错误码不含私人原始日志；
7. 重复运行依赖来源稳定 ID 幂等去重，平台状态与本地完成状态独立；
8. 默认最多 100 页，防止意外无限分页，重复游标和重复作业 ID 报错。

本批未创建浏览器可调用的“伪同步” POST 路由；尚无已验证学校账号。实际 Cloudflare Cron、Python 学习通执行器、华水专用青果解析器都需要在本人授权/学校真实环境具备后完成适配与生产验证。**现阶段不得把测试模拟 adapter 结果标为真实在线更新。**

## 已验收的合成行为
`node scripts/xiaoxu-academic-connector-tests.mjs` 使用实际内存 SQLite 顺序执行 0001–0005，模拟 21 条作业分两页、重复同步不重建、部分页失败后旧数据保留、错误码脱敏、授权过期、恢复、核验空结果和不支持的 provider。项目 `npm run check` 与 Playwright CI 保留。

## 明天接入的真正依赖
- 用户在官方渠道授权本人学习通/青果；确认平台允许且实际可用的只读查询方式；
- 实际平台返回的课程/任务稳定 ID、截止时间与状态字段；
- 按连接器协议实现服务器端 `verifySession/readPage`（或与用户控制的隔离连接服务联动），核验真实课程与作业的整条证据链；
- Cloudflare D1 + Access 在生产环境配置并验收；
- Cron/手动“刷新源”必须在真实连接器通过后再启用，不允许用用户上传快照伪造真实连接状态。

本批完整说明与上线前置条件详见 `docs/XIAOXU-TOMORROW-REAL-DATA-CHECKLIST.md`。
