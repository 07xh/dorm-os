# DORM OS · Personal

个人寝室生活与 NFC 自动化网站，基于已实现的 V1.2 单文件版本继续开发 V3.3。

**当前状态：** V1.2 基线，课表、任务、导入导出、本地场景管理可用；真实手机系统控制未接入。个人版优先，不包含室友共享功能。

## 本地运行

```bash
python3 -m http.server 8000 --directory public
npm run check
```

## 发布到 Cloudflare Pages

预先创建名为 `dorm-os` 的 Pages 项目，部署目录为 `public`。配置 GitHub Secrets：`CLOUDFLARE_API_TOKEN` 和 `CLOUDFLARE_ACCOUNT_ID`，然后在 GitHub Actions 手动运行部署工作流。自定义域名需要在 Pages 控制台另外绑定。

**数据提示：** 网站数据仅保存在当前浏览器 localStorage；清理缓存或更换设备前请导出 JSON 备份。不要将个人课表数据或凭据提交到公开仓库。

## V3.3 目标

- 蓝白简洁、轻玻璃设计，手机底栏和平板侧栏
- 个人学习、课表、任务、生活提醒、设备信息
- 全新 NFC 标签注册、场景绑定与执行状态区分
- NFC 硬件读写与第三方自动化须经真实设备验证
