# 校序 · 华水第二课堂只读同步助手（Chrome / Edge 桌面版）

## 已核实的华水查询接口

这些路径来自用户本人 2026-11-03 HAR 中的成功查询请求；HAR 本身不会被提交到仓库。

| 用途 | 方法与路径 | 关键结构 |
|---|---|---|
| 已报活动 | POST `/sys/stu/center/signupactivity/list` | form: `page`, `pagesize`; JSON: `data`, `totalPage`, `totalSize` |
| 学期/合计成绩 | POST `/sys/stu/score/list` | `data` 数组；`school_term_index=100` 是网页明确显示的“合计” |
| 六类成绩名称 | POST `/sys/stu/score/types` | 6 项 `code` / `name` |
| 活动类别 | POST `/sys/activitytype/all` | 6 项类别；当前成绩卡片不依赖它 |

活动列表只读取本人已报记录。网页源码确认状态映射：
- `verify_state=1` 且 `activity_state=5`：已核算；
- `verify_state=1` 且 `activity_state=4`：未核算；
- `verify_state=1` 且 `activity_state=6`：等待核算；
- `verify_state=2`：已核算；
- 其他情况根据 `activity_mode=3` 显示已验证，否则未验证。

成绩页源码确认 `score_value` 是“素质分”，`score_plus` 是“志愿服务时长”；六类分数由 `activity_type1_score` 至 `activity_type6_score` 与 `score/types` 的名称对应。不能把素质分和志愿服务时长混为同一个积分字段。

## 工作方式

1. 扩展只在本人浏览器的官方华水域名页面运行。
2. 使用同源 `fetch` 发送已在 HAR 核实的只读 POST 查询，浏览器自行附带当前登录会话；扩展不读取或导出 Cookie / Authorization。
3. 只白名单映射活动 ID、标题、日期、学期、类别、素质分和核算状态；学号、姓名、组织信息及原始响应不会进入同步快照。
4. 分页完整且六类成绩校验成功后，生成 `xiaoxu/academic-snapshot-v1` 快照。
5. 扩展在校序页面同源调用 `POST /api/v1/verified/snapshot`。该 API 仍由 Cloudflare Access 保护，写入来源如实标为 `user_import`（个人授权浏览器快照），不会伪装成服务端 `trusted_readonly`。
6. 扩展本地仅保存开关、间隔、同步时间和计数，不保存活动记录、学校凭据、Cookie 或原始 HAR。

## 安装（Chrome / Edge 桌面）

1. 在同一浏览器先正常登录华水第二课堂：<https://dekt.ncwu.edu.cn/sys/stu/center/signupactivity>。
2. 在同一浏览器登录校序：<https://dorm-os-dk9.pages.dev/#academic>，并完成 Cloudflare Access 授权。校序 D1 必须已绑定。
3. 从 GitHub 仓库下载本目录，解压到本地。
4. 打开 `chrome://extensions` 或 `edge://extensions`，开启“开发者模式”，选择“加载已解压的扩展程序”，选中本目录。
5. 点击扩展图标，先点“立即同步”验证；成功后再启用自动同步并选择 30 分钟、2 小时或 6 小时。
6. 成功后校序第二课堂卡片会显示六类成绩、学期汇总、活动记录及快照时间。

## 自动更新的边界

- 定时器由浏览器扩展的 Alarms API 执行。浏览器需要保持运行；浏览器完全退出时不会在云端独立运行。
- 学校会话仍有效时，扩展可在后台创建非活动标签页执行只读查询；不需要用户反复手动抓包。
- 华水会话过期时停止同步，提示用户在官方页面重新登录；不尝试绕过统一身份认证、验证码或登录保护。
- 校序 Cloudflare Access 会话过期时，需要重新登录校序。
- 首次登录和权限确认仍由用户本人完成。不会把学校 Cookie 放入 Cloudflare、D1、源码或 GitHub。
- 当前版本面向 Chrome / Edge 桌面。Android Chrome 不支持普通桌面扩展安装；Android 版本需另行做本机助手，不能把桌面扩展宣称为手机后台同步。
- 本地快照进入现有 API 后保持 `user_import` 来源标记；这是准确的信任边界。

## 当前明确不做

不自动报名、不签到、不提交活动报告、不生成验证、不修改成绩、不访问他人数据、不尝试续期或重放认证票据、不保存原始 HTML/HAR/会话凭据。

## 测试

在仓库根目录运行：

```bash
node scripts/ncwu-second-class-adapter-tests.mjs
npm run check
```

测试使用合成/脱敏响应夹具，不包含用户 HAR 或个人记录。
