# 校序 Academic OS · M1-B 旧版任务安全迁移（2026-11-03）

## 交付性质
本批在 M1-A D1 结构之上，新增 **已登录本人主动操作的任务迁移 API**。不会自动读取浏览器 localStorage，也不会在用户确认前写入云端。迁移不会清空或更改老网站的 `dorm-os-state-v1`，保留旧备份格式、NFC、通知和原任务逻辑。

**注意：** 本批后端 API 不代表前端已开启自动迁移，不代表任何学习通/雨课堂/青果真实账号已连接。迁移后的平台字段标识为 **历史导入数据**，不能标识为真实在线同步。

## 数据库
先执行 `migrations/0001_academic_core.sql`，再执行 `migrations/0002_academic_legacy_links.sql`。新表 `academic_legacy_links` 保存本人浏览器旧任务 ID、原来源/状态、原分类/课程文本、指向 D1 作业 ID 的映射、原内容 fingerprint 以及迁移时间。没有推断同名课程的官方 ID。所有记录以已签名 Access 用户 ID 隔离。

## API
`POST /api/v1/assignments/legacy-import`，只允许已验证 Cloudflare Access 签名的身份。必须是 `application/json`，同源请求，最大 32 KB，单批 1–20 条，模式必须明确为 `preview` 或 `commit`。

示例（完全虚构的脱敏数据）：
```json
{
  "format": "xiaoxu-legacy-tasks-v1",
  "mode": "preview",
  "tasks": [
    {
      "id": "task-demo-a",
      "title": "工程制图作业",
      "category": "工程制图",
      "due": "2026-11-20",
      "dueAt": "",
      "done": false,
      "source": "manual",
      "sourceId": "",
      "sourceStatus": "unknown",
      "course": "",
      "createdAt": ""
    }
  ]
}
```

返回 `{mode,total,toCreate,unchanged,conflicts}`。只有用户确认后，将 `mode` 改为 `commit` 并重发同一数据。成功返回 HTTP 201（有新增）或 200（无需变更）；同一批次重复提交安全无重复。若同一 `user_id + legacy_id` 对应旧数据已经发生变化，返回 409 和冲突 ID，**整个批次不写入**；可重新比对之后再处理。写入使用 D1 `batch` 事务，任何失败均不留下本批半成品。

超过 20 条须按原顺序分批调用，失败后允许重复执行已提交批次；同名/不同 ID 的手动任务不会被无证据自动合并。旧浏览器任务不因成功上传被删除。

## 截止时间与来源语义
- `due`：仅日期，显示日期而不是自造 23:59；
- `dueAt`：真实 `YYYY-MM-DDTHH:mm:00+08:00`；保存为 UTC 即时时间，并保留本地日期；
- 两者缺失：`unknown`，不得使用准确倒计时；
- `done` 仅映射本地个人完成状态；
- `sourceStatus` 保存在迁移来源记录中，不写为平台已在线核验；
- 学习通、华水、二课原始 ID 保留为历史来源文本，未来实时数据连接器还需经过核验，明确匹配后再关联，不可按标题自动合并。

## 验收
`npm run check` 包含 `scripts/xiaoxu-academic-legacy-tests.mjs`，在实际 SQLite 内存库执行 0001+0002 迁移。覆盖预览无写入、成功提交、重复提交、日期错误拒绝、20 条上限、任务 ID 冲突原子回退、跨用户隔离、Access 签名和跨站请求拒绝。

正式上线前必须配置私有学业路由 Access 和 D1 绑定。下一批前端增加由用户主动触发的“预览迁移 → 确认提交 → 分批进度与冲突处理”，在验证生产授权和恢复能力前不得自动从本地切换到云端数据。

## 当前限制
- 旧设备在成功迁移后继续编辑本地任务，不会自动双向同步；如果旧任务内容改变，再次上传会提示冲突而非覆盖。
- 同名且不同旧 ID 的任务不会无证据自动合并；这是避免误删的保守策略。
- 未建立服务端自动同步连接器；这批是历史任务迁移，不是第三方只读同步。
- 如果 Cloudflare 配置或 D1 迁移缺失，API 返回明确错误；不会将本地数据删除。
