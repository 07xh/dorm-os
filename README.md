# DORM OS V3.3 · 个人版

**当前交付：**个人寝室学习生活和 NFC 场景网页。蓝白轻玻璃主题，手机底部导航、平板和桌面布局；保留 V1 课表、任务、CSV / ICS 导入与 JSON 备份。新增长期保存的个人 NFC 标签配置、提醒清单和物品登记。当前是 **本地优先静态网站**。

## 功能状态

| 已有可用功能 | 当前没有的能力 |
| --- | --- |
| 个人课表与任务的新增、编辑、保存、导入/导出 | 云端账户、多设备自动同步 |
| 学习 / 休息 / 开发 / 出门场景的**网页内**切换 | 自动修改 Android 勿扰、音量、灯光 |
| NFC 标签**在浏览器本地**注册、编辑、删除、绑定场景 | 后台 NFC 扫描及 MacroDroid / Tasker 执行端回执 |
| Web NFC 条件支持时读取/写入本站 URL 类型的 NDEF 标签 | 保证任意手机或 NFC 标签都能读写 |
| 个人提醒和物品清单 | 系统级的后台定时提醒、室友共享 |

> Web NFC 需要兼容的 Android 浏览器、HTTPS、权限和可用 NDEF 标签，必须使用真实手机验证。当前端成功读取或写入，不代表系统自动化已完成。站点中的个人资料只在当前浏览器存储，清理数据或换设备前必须导出 JSON 备份。

## 技术与本地检查

直接使用静态 HTML、CSS 和浏览器 JavaScript，不引入昂贵依赖或重新换框架。

```bash
npm run check
python3 -m http.server 8000 --directory public
```

打开 http://localhost:8000 。Web NFC 在桌面浏览器一般不可用，属于正常兼容性限制。

## 部署

- GitHub `main` 推送会自动触发 CI 和 Cloudflare Pages Wrangler 部署。
- Pages 项目名：`dorm-os`；发布目录：`public`。
- 配置 GitHub Actions Secrets：`CLOUDFLARE_API_TOKEN` 和 `CLOUDFLARE_ACCOUNT_ID`。
- 现有自动部署工作流 `.github/workflows/deploy-cloudflare.yml` 保持不变。
- 账号密钥和个人作业/课表数据绝不能提交到公开仓库。

## 验收和后续

浏览器桌面及手机视口已经回归检查基本导航、任务新增、标签注册、个人提醒和物品的保存操作。**不等同于 Android 真机的 NFC 读写通过。** 后续优先处理真实手机 NFC 测试、跨设备数据、Tasker/MacroDroid 权限和安全的回执接口。