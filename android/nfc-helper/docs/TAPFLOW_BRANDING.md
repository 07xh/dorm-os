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

## 下一轮 UI 待办（独立于本次品牌发布）

1. 普通 NDEF 文本/URL 写入仍使用独立 `NdefWriterActivity`；可逐步合并到工具页的统一原位写卡弹层，但必须保留防覆盖及回读验证。
2. 在真实手机上核查主题色、输入法避让、底部导航触控区域、过长脚本名与零脚本空状态。
3. 标签到货后验证读卡、格式化、写入、回读、取消、锁屏分发与 KSU 实际执行；自动构建不能替代实体测试。
