# 校序 · Android 安装入口 V0.1（TWA Alpha）

**定位：可安装的首轮体验包，不是“完整原生 App”，更不是新的教务数据连接器。**

- 复用生产站点 `https://dorm-os-dk9.pages.dev/`、已有个人账号、真实 Cloudflare API / D1、任务列表与微信审核。
- 不复制或缓存学校密码、微信 Token；不加 WebView JavaScript bridge；不创建第二套云端账户。
- 基于 Google `android-browser-helper` 的 Trusted Web Activity (TWA)。优先使用支持 TWA 的手机浏览器；不支持时可能退回 Custom Tab / 浏览器，**不冒充原生页面**。
- UI 直接取自校序站点：网站更新后 App 同步看到线上界面变化，且只展示已经真实接入的数据。
- 本版本无系统级定时通知、原生课表小组件、离线学校同步或手机端直接读取微信；仍使用 PC 采集器。

## 构建

独立项目：`apps/xiaoxu-android`；不影响网站 Cloudflare 部署流程和 TapFlow Kotlin App。

要求：JDK 17、Android SDK API 35、Gradle 8.13；使用 Google Maven 依赖（Android Gradle Plugin 8.13.0、Google Android Browser Helper 2.7.4）。

```sh
gradle -p apps/xiaoxu-android :app:assembleDebug
```

也可在 GitHub Actions 手动运行 **Build Xiaoxu Android Alpha**，提取 `xiaoxu-android-alpha-debug` artifact 中的 `app-debug.apk`。

Debug APK 是个人体验构建。CI 可能每次用临时调试签名，升级前不要假定能覆盖安装；正式版需在可信环境中配置**固定私有签名密钥**。不要把 keystore 上传到公开仓库。

## 正式无浏览器地址栏：必须另行验收

TWA 全屏需要 **Digital Asset Links** 双方身份验证。当前代码没有凭空生成 SHA-256 指纹，也没有写假的 `/.well-known/assetlinks.json`：

1. 先确定不再变化的正式 `applicationId` 与用户持有的正式签名密钥。
2. 从正式签名证书提取 SHA-256 指纹，在现有校序网站 `public/.well-known/assetlinks.json` 部署准确声明（`delegate_permission/common.handle_all_urls`）。
3. 对正式 APK 与 `https://dorm-os-dk9.pages.dev/.well-known/assetlinks.json` 验证匹配；在用户 Android 16 真机测无地址栏/登录/任务/通知/离线降级。
4. 未完成时仍可能显示浏览器地址栏，不能宣称 TWA 信任校验成功。

官方参考：https://developer.android.com/develop/ui/views/layout/webapps/trusted-web-activities
上游依赖：https://github.com/GoogleChrome/android-browser-helper（Apache-2.0）。

## 进入原生阶段之前的验收门禁

- 本人校序登录后：真实通知可查看，微信待审核正常读取、**必须手动确认**才能入任务；未连接学校数据显示“未连接”。
- 学习通本人真实课程/作业、青果课表、二课活动/积分仍与网站共用相同验收门禁，不能用 Android 合成数据覆盖。
- 真机通过后，按功能优先级逐项加入 **原生通知/小组件 → 原生任务和课表 UI → 本地安全缓存**；必要时再评估 Kotlin/Compose 主体迁移，不另外开发学校接口。
