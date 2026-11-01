# Ncwu Dr.COM / ePortal 接口证据审计

审计对象：用户此前上传并保存的 `ncwu-a41-decoded.js`（文件时间 2026-10-31）。这是静态脚本快照，不是当前门户的实时配置或成功登录抓包。文件本身不包含用户账号密码。

## 已从 a41 快照确认的事实

### 1. 这份快照默认强制 HTTP

文件顶部配置：

- `enableHttps=0`：代码注释定义为不启用 HTTPS；
- `epHTTPPort=801`；
- `enHTTPSPort=802`；
- `enableHttps===0` 分支会将非 HTTP 页面重定向到 HTTP；
- `ep_port` 按当前协议选择 801 或 802；
- `page.portal_api` 使用当前页面协议、主机和 `ep_port` 拼接。

因此，**这份被保存的脚本快照会把门户引导到 HTTP:801**。这不是当前实时门户一定仍然如此的证明，但足以禁止助手根据它发送账号密码。必须先在当前网络环境确认门户现在是否支持 HTTPS、证书是否可信，以及实际登录请求是否全程 TLS。

### 2. 已确认的状态/配置路径，不等于登录提交接口

| 方法 | 快照中的路径 | 用途/字段 | 当前处理 |
|---|---|---|---|
| JSONP GET | `/eportal/portal/page/loadConfig` | 读取 `program_index`、VLAN、IP/IPv6、SSID、区域、AC IP、AP MAC、gateway ID；响应给出 `login_method`、`en_perceive`、`en_md5`、`password_cut` 等配置 | 只确认代码结构；仍缺当前真实响应 |
| JSONP GET | `/drcom/chkstatus` | 检查在线状态；可返回 MAC/IP 等状态字段 | 可能作为只读状态检查候选，需确认当前门户主机与响应 |
| JSONP GET | `/eportal/portal/online_list` | 仅在 `check_online_method=1` 时使用；快照中 `user_account` 与 `user_password` 是空字符串，另带 MAC/IP | 不能据此当作登录接口 |
| JSONP GET | `/eportal/portal/perceive` | 发送 login_method、IP/IPv6、VLAN、MAC、AC IP/name、data_format、suffix、SSID 等；`data_format=2` 表示请求无感知登录 | **不是纯只读接口**；未经当前配置与授权验证，不自动调用 |

JSONP 工具会创建 `script.src` 并把参数拼进 URL 查询串，同时加入 callback、jsVersion、随机数和语言字段。因此不能把它当作安全的密码传输方案；查询参数也可能出现在代理、服务端或历史记录中。

### 3. 真正登录请求仍未闭合

当 `isJSMin=1` 时，a41 会加载压缩业务文件 `a40.js`。本次可用的 a41 快照只说明加载路径，未给出登录按钮的最终提交方法、字段、密码处理顺序、会话依赖或成功响应契约。

a41 中出现 `login_method`、`en_md5`、`password_cut` 和账号字段，只是配置/变量定义，**不能反推出**最终登录请求。不得猜测 endpoint、字段、MD5 顺序或构造登录 POST/GET。

## 对 Android 助手的决定

- 当前只执行 Wi-Fi / active default route / Android captive portal / HTTPS 连通性诊断。
- 不自动调用 `perceive`，因为其 `data_format=2` 分支可能触发无感知认证。
- 不提交账号密码，不将 HTTP 结果解释为安全认证成功。
- 如果系统默认路由是 VPN 或移动网络，诊断停止，不绕过系统路由。
- 如果 Android 已标记 captive portal，提示用户使用系统门户通知或浏览器手动认证。
- 只有在当前环境证实 HTTPS、证书验证、真实登录请求、成功状态与失败回退后，才可设计明确的认证适配器。

## 仍需的脱敏证据

1. 当前 Ncwu 门户打开时的完整 scheme/host/port（可遮蔽公网 IP、MAC 和个人参数）。
2. 当前 `loadConfig` 响应中与认证相关的字段：`login_method`、`en_perceive`、`en_md5`、`password_cut`、`check_online_method` 等；删除账号、Cookie、Token、IP/MAC 和任何个人标识。
3. 当前门户实际加载的 `a40.js`（或管理员提供的未压缩对应文件）；不要上传带凭据的完整会话 HAR。
4. 本人正常登录的一次脱敏成功请求结构与响应：隐藏密码、Cookie、Token、账号、IP/MAC，只保留方法、路径、非敏感字段名、状态码和必要的成功标记。
5. 确认请求全程 HTTPS 且证书可信；若门户仍只支持 HTTP，则不在助手内自动提交密码。

**结论：目前可以做得更好的，是诊断和证据收集；还不能安全地实现 Ncwu 自动密码认证。**
