# 校序 Docker 本地开发环境

此入口复用现有 public、Pages Functions、lib、D1 migrations。不是生产级自托管服务器，不迁移线上 D1，不改变现有 Pages 发布、Android API 地址、学校授权或 Cron。Wrangler 4 本地模拟运行时与生产环境存在差异。

## Windows（Docker Desktop / WSL 2）

从仓库根目录运行 PowerShell：

```powershell
Copy-Item docker/local.env.example .env.docker
notepad .env.docker
docker compose up -d --build
docker compose ps
docker compose logs -f xiaoxu
```

填写 OWNER_LOGIN_SECRET 为自己独立设置的长密码（至少 8 字符，建议 16 字符以上），不要填学校密码或线上密钥。文件受现有 .gitignore 的 .env.* 规则保护，且不进入镜像。访问 http://localhost:8788；服务端默认保留 owner_password 鉴权。现代浏览器通常允许 localhost 的 Secure Cookie；若登录 Cookie 无法保存，不降低安全属性，应改用可信的本地 HTTPS 环境。

本地端口仅绑定 127.0.0.1，不对校园网、公网或其他设备开放。首次构建需要获取镜像和 npm 依赖；仓库未提供 package-lock.json，依赖安装沿用现有 CI 的 npm install 策略，Wrangler 同样使用 4.x，未锁定精确补丁版本。

## 管理

```powershell
docker compose stop
docker compose start
docker compose up -d --build
docker compose logs --tail=100 xiaoxu
docker compose down
```

数据保存在 xiaoxu-local-data 命名卷；普通 down 不删除数据。不要运行 down -v，除非明确希望清空本地数据库。每次启动由 Wrangler 按迁移记录仅执行未应用迁移；失败会停止启动，不会自动清空数据。

## 验收

- compose ps 显示 healthy（首页成功，匿名 /api/v1/health 返回 401）。
- 登录现有校序网页，再验证 /api/v1/health 返回 database_ready，新增一条非敏感测试任务，重启后仍存在。
- 首次启动、二次迁移及所有 SQL 文件顺序需要在实际 Docker 环境验收；本地空库不会自动同步生产数据。
- 不配置学校会话、正式 Cloudflare Token 或生产密钥；不能将本地健康检查视为学校真实同步成功。
- 不开放其他设备访问；后续正式自托管需要另行设计 HTTPS、身份验证、备份恢复、定时任务和 Android 后端地址切换。

生产发布仍执行原来的 GitHub Actions + Cloudflare Pages，不读取 wrangler.local.toml。
