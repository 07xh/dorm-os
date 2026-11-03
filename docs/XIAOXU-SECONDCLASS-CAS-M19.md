# 校序 M19｜第二课堂真实 CAS 入口核验 + 本人登录后只读导入

## 2026-11-03 公开无账号核验结果

访问 `https://dekt.ncwu.edu.cn/` 会进入
`https://dekt.ncwu.edu.cn/rump_frontend/login/?next=...`。
学校登录页继续通过
`https://authserver.ncwu.edu.cn/authserver/login`
认证，CAS `service` 参数指向**校方拥有的**
`https://sec.ncwu.edu.cn/rump_frontend/loginFromCas/`。
公开页面支持学校密码、短信等认证方式。

校序 **没有注册为该 CAS 服务**，不能把此页面的认证结果直接当成授权到
校序的 OAuth/CAS 回调，不会收集学校密码、Cookie、Ticket、验证码、
扫码登录令牌或跨域监听学校登录页面。

## 已实现

- `lib/academic-ncwu-secondclass-sso.mjs`：记录已观察到的官方 SSO 与校方回跳服务；
  `GET /api/v1/connections` 本人私有响应公开
  `authFlow.method=school_cas_external`、固定学校入口与
  `accountAuthorization=not_verified`。没有真正授权适配器时不误报连接成功。
- `POST /api/v1/connections/second_class/login` 在服务端直接拒绝采集第三方
  学校密码。旧 M18 的服务端加密会话同步器保留，但不允许网页凭空生成学校授权。
- 复用**同一个「我的 → 数据连接」**，第二课堂显示官方学校登录链接、
  本机导出脚本和本人 JSON 导入，不再另建第二套账户系统。
- `public/xiaoxu-second-class-export.user.js` 是可在用户本人已登录
  `dekt.ncwu.edu.cn` 页面运行的可选只读脚本。由用户点击按钮后，仅
  通过已有 HAR 确认的 `signupactivity/list`、`score/types`、
  `score/list` 三个 POST 业务查询读取完整分页；不同学生 ID、
  未完成分页、非法日期、分数、未登录页面和响应非 JSON 都失败关闭。
  所有学生身份字段只在当前网页内存中用于一致性核对，不写入文件。
  不读取 Cookie，不访问校序域名，不自动签到、报名或提交。
- 下载的 JSON 含严格 `xiaoxu/academic-snapshot-v1` 活动结构与独立的
  `schoolReportedPreview` 素质分/志愿服务时长预览。
  在校序中选择文件时，只把 `snapshot` 部分交给已有私人
  `POST /api/v1/verified/snapshot`。D1 按 `user_import` 记录，
  用户数据 **不得** 覆盖已存在的 `trusted_readonly` 学校同步。
  官方合计预览不会冒充已独立验证的 Cloudflare 分数记录。

## 本人真实数据验证步骤（用户自行执行）

1. 在校序「我的 → 数据连接 → 第二课堂」点击官方登录链接，学校账号只在
   校方登录页输入，不在校序或聊天里输入。
2. 在支持脚本的本人浏览器（例如已安装的 ScriptCat）导入本站下载的
   `xiaoxu-second-class-export.user.js`，允许其仅在学校第二课堂域名运行。
3. 返回本人已登录的第二课堂页面，点击右下方「导出本人二课数据」。
   下载 `xiaoxu-second-class-personal-export.json` 到本机，确认活动条数、
   学校页面上的素质分总计与志愿服务时长一致。
4. 在已经登录校序个人账户的「数据连接 → 第二课堂」选择该文件，
   预览并确认只保存活动明细；返回教务页核对活动记录。
5. 这只是**本机显式导出与个人导入**，不是校序获得独立可信的校方授权，
   不会自动更新。学校允许的服务端授权接口/第三方 CAS 客户端注册
   尚未验证，因此真实服务器端自动同步仍未接通。

## 本次验收和边界

`node scripts/xiaoxu-second-class-cas-export-tests.mjs`
用全合成数据模拟学校接口与分页，覆盖密文/个人标识不外泄、
核算前积分不计入、`score_plus` 是志愿时长、`totalPage=0` 分数页、
身份跨页变化、分数身份不一致、分页缺失和
`validateSnapshot` 实际数据模型。
完整 `npm run check` 与 Playwright CI 再核验主项目。

公开 SSO 页面并未证明学校正式授权校序作第三方客户端，
也不能以模拟测试宣称用户个人账号已经验收。
