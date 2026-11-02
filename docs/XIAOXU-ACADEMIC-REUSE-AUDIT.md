# 校序 · 华水教务接入开源复用审计（2026-11-03）

> 目标不是把正方 App、拾光或 xuexitong-cli 的界面放进校序；是复用有证据的数据协议、提取器和结构化结果，在现有五入口 + 教务二级导航中自然显示。**截至本记录：未完成华水本人账号真实授权查询，课程、成绩、考试、学习通作业不可冒充已接通。**

## 实际查阅的上游实现

| 来源 | 已核对的真实路径 | 能直接利用什么 | 阻断 |
|---|---|---|---|
| [正方教务助手](https://github.com/znjhahaha/zhengfang-apk) GPL-3.0 | [v1.0.124 更新说明](https://github.com/znjhahaha/zhengfang-apk/blob/main/release-notes/v1.0.124.md)、[BundledAcademicProviders.kt](https://github.com/znjhahaha/zhengfang-apk/blob/main/app/src/main/java/com/tyust/course/academic/plugin/BundledAcademicProviders.kt)、内置 \`app/src/main/assets/bundled-academic/kingo.eduplugin\` | 已实现 KINGOSOFT 青果通用登录、课表、成绩、考试和学期读取；可参考其协议/错误处理和授权用户的应用能力 | 公开验收是**湖南汽车工程职业大学**，不是华水；Android Kotlin + QuickJS 插件不能不经适配直接在 Cloudflare/浏览器运行；复制 GPL 源码需满足相应分发义务 |
| [拾光适配库](https://github.com/ShiGuangSchedule/shiguang_warehouse) MIT | [qingguo_jiaowu/adapters.yaml](https://github.com/ShiGuangSchedule/shiguang_warehouse/blob/main/resources/qingguo_jiaowu/adapters.yaml)、[qingguo_01.js](https://github.com/ShiGuangSchedule/shiguang_warehouse/blob/main/resources/qingguo_jiaowu/qingguo_01.js) | 登录后的网页表格提取、星期/节次/周次解析、同课块去重；本仓 \`lib/academic-upstream-bridge.mjs\` 已严格对齐其课程行格式 | 需在学校官方会话下确认课表表格与节次定义；脚本依赖其宿主的 \`window.shiguangBridge\`，不能在校序中直接调用 |
| [xuexitong-cli](https://github.com/DanMo661/xuexitong-cli) MIT | [xuexitong_mcp/client.py](https://github.com/DanMo661/xuexitong-cli/blob/main/xuexitong_mcp/client.py)、[cli.py](https://github.com/DanMo661/xuexitong-cli/blob/main/xuexitong_mcp/cli.py) | 本人授权只读查询、课程、\`hw\`/\`hw-ls --json\`、状态/截止时间解析；**校序已存在 \`public/xiaoxu-chaoxing-cli.js\` 适配器与去重**，优先接续它，不重新编写作业 JSON 转换 | Python requests + 会话处理，不能原封不动塞进 Cloudflare Pages JS；需真实授权以及合规的最小权限个人连接服务。不得把密码或 Cookie 放公开网页/GitHub |

## 华水官方事实与接口门禁

- 华水校官网教务处的个人业务通知明确给出教务系统入口 **https://jwmis.ncwu.edu.cn**（https://www.ncwu.edu.cn/info/1063/22666.htm）；“华水e教务”官方公告说明登录时使用**教务系统账号和密码，而不是统一身份认证账号**（https://www2.ncwu.edu.cn/jiaowu/info/1008/2771.htm）。
- 以上只能证明官方入口。未确认华水当前学校首页/登录后具体 KINGO 页面版本、重定向、学期/查询接口和权限；不从其他学校的成功推断华水已经打通。
- 必须由用户本人在**官方登录页面**完成授权。无授权时后端禁止查询私有数据，不存储密码到公开 GitHub/D1 普通明文字段，不使用登录限制绕过或反爬规避。
- **真实验收**要逐项对照：课表教师/教室/单双周/节次；考试日期和地点；成绩原始值、学分、绩点口径；学习通作业的稳定 ID、截止、平台提交状态。断网、过期、空数据、部分分页都要明确区分，不能用假数据填空。
- 未获得节次作息/学期周一起始日的真实证据前，不能把青果的 \`1-2 节\`伪造为 \`08:00–09:40\`，不能把上游课程名称当成已验证的官方唯一 ID。

## 已落实到代码的复用边界

\`lib/academic-upstream-bridge.mjs\` 对齐拾光 qingguo_01 的 \`{name,teacher,position,day,startSection,endSection,weeks}\`，转换为「校序课块候选」但保留来源与 \`syncState:unverified\`，不写入正式课表，直到校内真实接口及学校作息对照通过。对应测试 \`scripts/xiaoxu-academic-bridge-tests.mjs\`。

已有学习通 CLI 转换 \`public/xiaoxu-chaoxing-cli.js\` 与课程任务合并逻辑继续保留；不重复建设“手动导入是日常使用路径”，最终目标依旧是经本人授权、可持续的只读后台同步。

## 依赖顺序（每一环有门禁）

1. 官方域名和登录后页面类型确认；确定正方 KINGO 或拾光哪个最小改动可用。
2. 用户本人授权只读会话，抽取**去敏后的字段契约/样本**；写华水学校级适配器，证明学期、周次、科目、时间不丢失。
3. 后台只读同步服务、受控会话与数据过期状态；前端只显示校序统一 DTO，不嵌第三方页面。
4. 成绩与考试同理复用 KINGO 读取组件；学习通直接复用已有 CLI 的工作 ID 规范化，不自动答题、签到、刷课或提交。
5. 所有接口必须真实本校逐项对照通过、CI 通过、上线 HTTPS 复测后，才把页面「未连接」改为「已连接」。

**无真人会话的当前进度只能叫：开源源码核验 + 严格转换契约准备，不能叫个人教务已接入。**
