# 校序微信 · Cloudflare 云端监控（第一阶段）

本实现复用已发布的 Cloudflare Pages Functions + D1 + 校序本人登录，**无需 Docker、无需单独服务器、无需第二个微信读取器**。

## 现有数据与新接口

- `GET /api/v1/wechat/monitor` 仅允许已登录的校序本人读取。
- 统计现有 `academic_wechat_devices`、`academic_wechat_batches` 与 `academic_wechat_candidates`；**不新增 D1 迁移**。
- 每台设备显示：最后云端确认、过去24小时确认批次数、当前待审核数，以及只代表确认新鲜度的状态。
- `recent` 表示最近90分钟云端确认过，不代表电脑此刻在线；`overdue` 表示同步时段内超过90分钟未确认，不代表故障原因确定；`outside_window` 表示处于夜间非采集时段；`not_verified` 表示从未成功同步。
- 状态仅依赖已有云端接收证据；**不能凭此判断微信本地扫描是否失败、电脑是否睡眠、HTTPS 失败原因、AI 是否执行**。

## 部署与验收

1. 先运行 `node scripts/xiaoxu-wechat-monitor-tests.mjs` 和现有 `node scripts/xiaoxu-wechat-device-sync-tests.mjs`。
2. 通过 PR/CI 后按项目现有 Pages 发布流程上线；生产 D1 不需新迁移。
3. 使用校序本人登录访问「任务 → 微信待审核 → 电脑设备管理」查看已有设备的聚合数据。
4. 断网/切换网络/电脑休眠时验证状态是否遵循已有最后确认时间变化；不要将显示“最近确认”当作实时在线。
5. 与 Windows 电脑实际 `run.ps1 sync` 返回 0 和候选审核数交叉核实。

## 第二阶段：真正的远程控制（尚未实现）

远程暂停/恢复需要 Windows 采集端**主动拉取并执行**经鉴权、有限指令集的策略。仅添加网页按钮不会让 Windows 计划任务响应。为防止误操作，控制接口应复用已有 owner/device 授权，使用版本/回执、审计及失败关闭；不允许远程执行任意 PowerShell。电脑离线时仅能保存待执行意图，不能声称已暂停。下一阶段应在拿到**用户电脑当前实际运行脚本**并回归 07:00–23:00、23:10 日报及断点续读逻辑后实施。
