# DORM OS 网页 → DORM NFC Manager：本机桥接 V1

该版本仅提供手动确认的本机导航，不做网页远程执行 Shell 或跨设备同步。

- 站点场景和设备入口进入 `/assistant/?scene=none|study|rest|dev|away`。
- App 的 HTTPS App Links 仅接收正式候选域名的精确 `/assistant/` 路径。
- 如系统没有完成域名验证，网页兜底页提供手动点击的 `dormos://assistant/open?scene=...`。
- App 重新验证 URL、来源域名、路径和唯一白名单场景参数；任意 script、command、extra 查询参数均拒绝。
- App 每次显示确认页；确认只打开原有 ManagerActivity，绝不自动调用 Root。
- KSU 仍由原 RootModuleClient 和 dispatcher 管理；不开放 Root HTTP API、不增加后台轮询服务。

## 尚未完成
Android App Links 自动验证需要正式 APK 签名证书 SHA-256 指纹，并在各实际受控的 HTTPS 域名部署 `/.well-known/assetlinks.json`。
当前尚无可信的 release 签名指纹，不能伪造配置，也不能宣称链接已自动验证。
`dorm.0701000.xyz` 是拟用域名，需独立核实部署。

## 验收
需要执行网站 `npm run test:all`、Android `testDebugUnitTest` 和 Debug APK 构建。
在一加 Ace 6 Android 16 上人工验证接受/取消、未安装 App 的网页回退、伪造 URI、设备拒绝 Root 授权、浏览器返回行为。
未经真机验收保持 PR Draft，不合并 main。
