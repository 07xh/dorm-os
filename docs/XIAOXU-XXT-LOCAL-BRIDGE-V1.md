# 校序 Academic OS · 学习通 xxt 本机桥接器 V1

## 这次真正接入了什么
复用仓库既有的 xuexitong-cli JSON 适配器（public/xiaoxu-chaoxing-cli.js），提供可在本人设备上运行的 Node.js 桥接脚本。它可调用已安装的 xxt 只读命令或读取此前导出的 JSON，生成校序本地任务导入文件。没有复制 xuexitong-cli 的登录代码，也没有绕过平台登录。

## 本人设备使用（需要 Node 22 和已在本机正常登录的 xxt）
仅在有权限查看本人课程与作业的设备上运行。示例命令：

~~~bash
node scripts/xiaoxu-xxt-local-bridge.mjs --run hw --out ../homework-import.json
node scripts/xiaoxu-xxt-local-bridge.mjs --run hw-ls --course "高等数学" --out ../course-import.json
node scripts/xiaoxu-xxt-local-bridge.mjs --file ./xxt-hw.json --out ../clean-import.json
~~~

然后前往校序「我的 → 数据连接与备份 → 数据迁移与手动导入 → 导入任务文件」，选择生成的 JSON。

## 精确限制
- xxt hw --json 是待处理作业看板，通常不包含已提交的所有作业；hw-ls 是单课程列表，通常没有截止时间。两者都不足以证明全课程全量采集。
- 如果上游查询失败、部分课程告警、JSON 无法解析、作业没有可信稳定 ID 或当前文件为空，桥接器拒绝输出。不会把查询失败解释成 0 作业。
- 输出仅保存课程、作业、状态、明确的截止日期及不含签名的稳定 workId；不会保存上游原始详情 URL、会话 Cookie、访问令牌和登录密码。
- 无明确年份或完整时间证据时截止时间保持未知。不执行签到、刷课、答题、提交。
- 输出使用排他创建（不能覆盖已有文件）；类 Unix 权限 0600。请把生成的个人数据文件存放在 Git 仓库之外，切勿提交或分享到公共仓库、聊天或群聊。
- 本工具始终标记为部分覆盖，只生成 xiaoxu-task-batch-v1 本地导入文件，不构造完整的 academic-snapshot-v1 云端 verified 快照。

## 仍需后续实机验收
确认华水学习通本人账号的 xxt 登录成功、课程范围、作业截止时间、状态/分页和遗漏场景。Cloudflare Access 只能保护校序的本人数据，不能代替学习通授权；生产 D1 和 Access 仍需实际配置和权限验收。
回归命令：node scripts/xiaoxu-xxt-local-bridge-tests.mjs
参考项目：https://github.com/DanMo661/xuexitong-cli
