# 触序 · TapFlow｜品牌与兼容性约定

## 产品身份

- 正式名称：**触序 · TapFlow**；Android 桌面短名称：**触序**（防止图标下方被省略）。
- 定位：个人 NFC 场景触发 + SH 脚本管理；**DORM OS** 是可联动的平台，并非 App 品牌。
- 风格：工程蓝与白、轻量卡片、三栏（首页／场景／工具）、当前页读卡/场景写卡弹层。
- 图标：`ic_tapflow.xml` 简化蓝底白色 T 与两条感应波纹；没有 Splash、片头动画或独立欢迎页。
- 不新增侧键、三段式或 KernelSU WebUI；校园网认证仍只是用户导入和审核的普通 SH 脚本。

## 兼容性硬规则

- Android `applicationId` / Kotlin 包名继续用 `xyz.dormos.nfc`，保证与已安装测试版升级路径一致；**不得因改名替换包名**。
- KernelSU `module.prop` 的 `id=dormos_nfc_actions` 保留，升级不生成第二个模块。
- 保留 `NfcProfiles.MIME`、旧 MIME 支持、UUID 内容格式，既有 NFC 标签无需因品牌变更重写。
- 保留模块存储路径、SH 脚本/场景 TSV 文件、`DORM_*` 状态字段、Root 调度参数和 ZIP 备份头 `DORMOS-NFC-SH-BACKUP/1`，只改可见名称。
- DORM OS 既有 `dormos://assistant/open` 和 HTTPS App Link 不变，以免网站打开 App 失效。
- `versionCode` 单调增加；新品牌 APK 仍由 CI 编译成套发布，PR 保持 Draft 至实体 NFC 和 OnePlus 实机验收。

## UI 验收与后续待办

1. 普通 NDEF 文本/URL 写入已合并到工具页当前工作台底部弹层；独立 `NdefWriterActivity` 路由与源码已删除，保留明确覆盖授权、DORM 标签保护、容量检查及完整 NDEF 回读。
2. 在真实手机上核查主题色、输入法避让、底部导航触控区域、过长脚本名与零脚本空状态。
3. 标签到货后验证读卡、格式化、写入、回读、取消、锁屏分发与 KSU 实际执行；自动构建不能替代实体测试。


## V1.16.0 · 工具页交互收敛

- 普通 NDEF 文本/URL 写入由 `NdefWriteSession` + `NdefWriteSheet` 在 `ManagerActivity` 内完成，与只读扫描和场景写卡共用同一个工作台，不再启动独立 Activity。
- 单次前台 ReaderMode；离开前台会暂停。若写入已发出但回读未确认，界面明确要求先读卡，不提供直接重复写入按钮。
- 工具页管理五项图标统一为 24dp 细线矢量，运行记录/备份/外观/Root/NFC 设置各自有明确语义；每行由单个 Material Surface 提供完整点击区域与 ripple。
- 普通标签已有内容需显式勾选允许覆盖；`application/vnd.dormos.*` 自动化标签始终拒绝普通写入；未格式化标签不自动格式化。
