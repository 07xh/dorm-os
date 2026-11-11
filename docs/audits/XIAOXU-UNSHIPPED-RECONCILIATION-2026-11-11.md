# 校序未发布项核验与收敛（2026-11-11）

核验时间：2026-11-11 12:08 北京时间。以GitHub真实PR、精确提交CI、源码差异和生产revision为证据；没有把构建当作本人数据或真机验收。

## 已执行

- #435 已合并（bb22b127）：可选手动AI桥接/本地摘要，独立Python 37项通过；主线综合CI通过。不是已配置用户电脑自动AI同步。
- #441 已合并（ee432945）：核心通知备用源修复、学院通知、天气更新不清空登录输入。精确提交65ce4ff7的JVM/完整CI/APK均成功，独立复核通过。
- 官方生产采集已生成45c32877：112条公开记录；三个补充来源各10条、原82条完整保留；教务主站不可用时已验证API兜底仍正常。
- #440 8107c13c：修复模拟器脚本逐行Shell丢失APK变量；两次冷启动成功，但不能据此宣称Ace6闪退与真实Excel导入已验收。
- 关闭13个过期PR，保留分支/提交历史；四个历史功能以当前main等价实现核验，没有删除独有功能。

## 全量39项快照

| PR | 处理状态 | 证据或剩余事项 |
|---|---|---|
| [#440](https://github.com/07xh/dorm-os/pull/440) | 保留，待满足明确门禁 | 使用8107c13c：JVM/完整CI/两次模拟器冷启动已通过；本人真实Excel与Ace6闪退复测待完成 |
| [#439](https://github.com/07xh/dorm-os/pull/439) | 保留，待满足明确门禁 | 最新HEAD未检出PR工作流；仍缺本人真实教学班/名额来源核验，不是可提交的选课系统 |
| [#429](https://github.com/07xh/dorm-os/pull/429) | 保留，待满足明确门禁 | 保持草稿：Room旧库/备份与Ace6本人任务未验收；保留图与最新状态修复 |
| [#441](https://github.com/07xh/dorm-os/pull/441) | 已合并，继续发布核验 | 精确HEAD自动化与代码审查通过；生产/正式包按后续证据分别核验 |
| [#438](https://github.com/07xh/dorm-os/pull/438) | 保留，待满足明确门禁 | 先定向整合至#429：只补缺失的详情截止读取与测试，禁止旧文件覆盖新身份/删除判断 |
| [#433](https://github.com/07xh/dorm-os/pull/433) | 保留，待满足明确门禁 | 依赖#431/#432和0019；不可只上前端图而宣称云端就绪 |
| [#432](https://github.com/07xh/dorm-os/pull/432) | 保留，待满足明确门禁 | D1 0019尚未应用；生产审计恢复点和增量迁移门禁先行 |
| [#431](https://github.com/07xh/dorm-os/pull/431) | 保留，待满足明确门禁 | 需处理当前main冲突；与#432/#433按依赖收敛 |
| [#435](https://github.com/07xh/dorm-os/pull/435) | 已合并，继续发布核验 | 精确HEAD自动化与代码审查通过；生产/正式包按后续证据分别核验 |
| [#416](https://github.com/07xh/dorm-os/pull/416) | 保留，待满足明确门禁 | 保留：可信状态筛选/核对账本/原生读取修复仍有独有代码；旧服务端解析不可回盖main |
| [#415](https://github.com/07xh/dorm-os/pull/415) | 保留，待满足明确门禁 | 仅本机编辑器被接管；微信个人计划日期/提醒仍需定向移植 |
| [#412](https://github.com/07xh/dorm-os/pull/412) | 保留，待满足明确门禁 | 保留：逐课程错误分类与可见提示仍有独有代码；不恢复落后分页实现 |
| [#414](https://github.com/07xh/dorm-os/pull/414) | 保留，待满足明确门禁 | 保留：课前提前分钟与通知二级设置尚未整合 |
| [#413](https://github.com/07xh/dorm-os/pull/413) | 保留，待满足明确门禁 | 保留：4000 UTF-16脱敏正文、分批、历史回补仍有独有代码 |
| [#405](https://github.com/07xh/dorm-os/pull/405) | 保留，待满足明确门禁 | 保留：稳定Room订阅/生命周期和后台解析仍有独有优化 |
| [#383](https://github.com/07xh/dorm-os/pull/383) | 历史关闭，保留代码历史 | 明确QA-only/过期一次性发布，不合并旧构建工作流 |
| [#381](https://github.com/07xh/dorm-os/pull/381) | 历史关闭，保留代码历史 | 明确QA-only/过期一次性发布，不合并旧构建工作流 |
| [#377](https://github.com/07xh/dorm-os/pull/377) | 历史关闭，保留代码历史 | 独立比对已确认当前main接管其课表/备份/提醒/主题/第二课堂单页行为 |
| [#378](https://github.com/07xh/dorm-os/pull/378) | 历史关闭，保留代码历史 | 明确QA-only/过期一次性发布，不合并旧构建工作流 |
| [#380](https://github.com/07xh/dorm-os/pull/380) | 历史关闭，保留代码历史 | 明确QA-only/过期一次性发布，不合并旧构建工作流 |
| [#375](https://github.com/07xh/dorm-os/pull/375) | 保留，待满足明确门禁 | 原型，不部署：嵌入Python运行时及综合CI失败 |
| [#363](https://github.com/07xh/dorm-os/pull/363) | 历史关闭，保留代码历史 | 明确QA-only/过期一次性发布，不合并旧构建工作流 |
| [#352](https://github.com/07xh/dorm-os/pull/352) | 保留，待满足明确门禁 | 历史UI契约文档，需先对照当前main更新过期#351基线，未改变运行时 |
| [#350](https://github.com/07xh/dorm-os/pull/350) | 历史关闭，保留代码历史 | 明确QA-only/过期一次性发布，不合并旧构建工作流 |
| [#343](https://github.com/07xh/dorm-os/pull/343) | 历史关闭，保留代码历史 | 明确QA-only/过期一次性发布，不合并旧构建工作流 |
| [#338](https://github.com/07xh/dorm-os/pull/338) | 历史关闭，保留代码历史 | 明确QA-only/过期一次性发布，不合并旧构建工作流 |
| [#335](https://github.com/07xh/dorm-os/pull/335) | 历史关闭，保留代码历史 | 独立比对已确认当前main接管其课表/备份/提醒/主题/第二课堂单页行为 |
| [#329](https://github.com/07xh/dorm-os/pull/329) | 历史关闭，保留代码历史 | 独立比对已确认当前main接管其课表/备份/提醒/主题/第二课堂单页行为 |
| [#327](https://github.com/07xh/dorm-os/pull/327) | 历史关闭，保留代码历史 | 独立比对已确认当前main接管其课表/备份/提醒/主题/第二课堂单页行为 |
| [#322](https://github.com/07xh/dorm-os/pull/322) | 保留，待满足明确门禁 | 桌面概览候选：与main冲突，需移植至现有Web布局后回归 |
| [#247](https://github.com/07xh/dorm-os/pull/247) | 保留，待满足明确门禁 | 独立协议探针；保留历史，不当成已通过本人真实数据验收的生产功能 |
| [#29](https://github.com/07xh/dorm-os/pull/29) | 保留，待满足明确门禁 | 独立TapFlow项目，保留，不纳入校序清理 |
| [#208](https://github.com/07xh/dorm-os/pull/208) | 保留，待满足明确门禁 | 历史AI协作文档；现行开发规则以main AGENTS.md为准 |
| [#207](https://github.com/07xh/dorm-os/pull/207) | 历史关闭，保留代码历史 | 明确QA-only/过期一次性发布，不合并旧构建工作流 |
| [#205](https://github.com/07xh/dorm-os/pull/205) | 保留，待满足明确门禁 | 历史授权ADR；不可据此移除仍使用的现有鉴权 |
| [#175](https://github.com/07xh/dorm-os/pull/175) | 保留，待满足明确门禁 | 开发工具配置，不属于校序用户运行时，不冒充上线 |
| [#12](https://github.com/07xh/dorm-os/pull/12) | 保留，待满足明确门禁 | 独立TapFlow项目，保留，不纳入校序清理 |
| [#21](https://github.com/07xh/dorm-os/pull/21) | 保留，待满足明确门禁 | 独立NFC项目，保留，不纳入校序清理 |
| [#17](https://github.com/07xh/dorm-os/pull/17) | 保留，待满足明确门禁 | 独立NFC/KernelSU项目，保留，不纳入校序清理 |

## 不可误删的独有实现

- #413/#435共同修改collector.py：长正文/回补与HMAC桥接必须定向整合，不能整文件互相覆盖。
- #415旧TaskBoard不得覆盖#429图关系编辑器；仅移植微信个人计划与提醒。
- #412/#416旧Chaoxing服务端不得覆盖main的签名链接、finalUrl回退、bottomList范围及新模板支持。
- Room/D1历史迁移、备份兼容、平台只读边界、独立TapFlow/NFC项目不按名称清理。

## 正式发布收尾

主线现有0.8.15（36）与旧正式证书保持；原签名恢复后仍须签名验证、APK条目内容一致、成功main构建来源证明、公开Release实际可下载。未取得这些证据前不能称正式APK已上线。下一次最终构建请求将基于全部已发布公开档案的main，避免生产采集提交让APK来源证明失配。
