# DORM OS NFC 助手 · 开源复用审计

审计日期：2026-11-01。目标：一加 Ace 6，Android 16，NFC 一次性触发 Ncwu Wi-Fi 认证诊断。仅复用经过审查的设计思想及 Android 公共 API；**本次未复制第三方项目的源码文本或资产**，因此不增加第三方库及运行时许可依赖。

## 已检查的开源参考

| 项目 | 许可证 | 可借鉴部分 | 不照搬的部分 |
| --- | --- | --- | --- |
| [andijakl/NfcDemo](https://github.com/andijakl/NfcDemo) | MIT | Kotlin NFC 读写生命周期：onCreate / onNewIntent 分发、NDEF 类型路由与前台写卡 | Android 16 已改用 ACTION_VIEW 处理网页 URL 标签；不直接复用其旧 URI 触发方式 |
| [demo133/campusnet](https://github.com/demo133/campusnet) | MIT | 已联网跳过、Portal 协议适配器、先判定网关再发请求、明确状态和失败原因 | PC/Python 守护进程、其他校园网接口参数、开机自启、猜测登录请求 |
| [Biubush/AutoNet4AHU-Android](https://github.com/Biubush/AutoNet4AHU-Android) | MIT | Android 网络连通性检测、认证与 UI 分层、日志与诊断界面 | 后台常驻、定时循环、开机启动、企业微信回调和安徽大学专有接口 |
| [lucf15/NtagToolkit](https://github.com/lucf15/NtagToolkit) | Apache-2.0 | 测试边界：不同标签容量、空白未格式化 NFC、写入后回读 | 暂不引入密码保护/NTAG21x 专有指令或完整第三方依赖 |
| [PATILYASHH/nfcc](https://github.com/PATILYASHH/nfcc) | MIT | 多标签、多场景动作绑定、本地优先、一次触碰执行动作的交互模型 | Flutter/PC 伴随服务、多端同步、所有第三方动作，不直接搬入 Kotlin 原生项目 |
| [tiann/KernelSU](https://github.com/tiann/KernelSU) | GPL-3.0 等，按文件核验 | 官方模块 ZIP 格式、module.prop、skip_mount、action.sh、应用 Root 授权边界 | 不复制内核代码、不改系统分区、不引入 service.sh 常驻服务 |

## 当前落实的改进

1. NFC 读取采用私有 MIME 标签 `application/vnd.dormos.ncwu`，避免 Android 16 网页 URL NDEF 自动路由到浏览器。
2. Android 16 检查 `NfcAdapter.isTagIntentAllowed`，给出系统“通过 NFC 启动”设置提示。
3. 原有 NDEF 写卡逻辑新增 `NdefFormatable`：仅在用户明确同意覆盖后，对未格式化的可写标签尝试格式化；已格式化标签仍走 `Ndef`，并做写后回读。
4. 网络检测继续将访问绑定到 Wi-Fi `Network`，拒绝把移动数据可联网算作校园网认证成功。
5. 原生 NFC 默认无 Root。V0.5 另提供可选 KernelSU 固定只读 SH：需本机明确启用且 KernelSU 单独授权；无常驻进程、开机执行或自动重试。
6. 在缺少 Ncwu 真正登录接口的情况下，禁止提交任何账号密码。

## 仍无法被开源项目替代的证据

已保存的学校网页 `a41.js` 只说明 `/drcom/chkstatus`、`/eportal/portal/page/loadConfig`、`/eportal/portal/perceive` 和 `a40.js` 业务脚本加载路径。**其他学校同为 Dr.COM/ePortal 也不能据此推断 Ncwu 的真实参数。**

登录功能仍需要校内真实 `loadConfig` 响应、`a40.js` 以及本人正常认证过程的脱敏成功请求；同时验证认证状态与通过 Ncwu Wi-Fi 的公网连接，并确认密码传输方式符合安全要求。

## 许可证规则

这些参考仓库均提供开放许可证，但**借鉴架构思路不等于可以忽略许可证**。若未来直接复制 MIT/Apache-2.0 项目源码，必须按对应许可证保留版权和许可证声明、记录修改；若改用 GPL 项目则先评估相应分发义务，不在未经审计时拷贝进入仓库。

实际接口、标签及网络行为以 Android 16 真机验证为准；本文件不是完整兼容性保证。

## KernelSU 扩展审计（V0.5）

- 本次只复用 KernelSU 文档中的**模块封装规范**和 NFCC 的本地场景设计，并未复制其运行时源代码；Android 部分仍依赖系统 NFC/Network API。
- Root 执行固定路径模块内的只读脚本、固定动作 wifi-status，拒绝从 NFC 标签/网页/用户文本直接生成命令。
- 模块包不包含 service.sh、post-fs-data.sh、system.prop 或 sepolicy.rule；只有 NFC 触发时 APK 才主动申请 su 启动脚本，最多等待 12 秒后请求终止。
- 开源项目的其他校园网登录适配器仍不能证明 Ncwu 的真实登录接口。脚本当前不提交账号密码。
