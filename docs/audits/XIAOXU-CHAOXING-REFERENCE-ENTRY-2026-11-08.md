# 学习通真实作业入口与官方导航修复

用户要求修复全部课程读取，并对照已提供的丛雨幼刀学习通 2.1.14 实现。基于 PR #419，保留现有 UI 和本机任务归并，整合 main 至 d6cbb1d（包含 #423），不恢复旧 UI 分支。

## 已核实差异

- 参考 APK `CourseEntry.url` 先从 `a[data-url*=work/list],iframe[src*=work/list],a[href*=work/list]` 获取真实作业入口，保留查询字段，再用同页课程身份和隐藏参数补全。
- 校序此前跳过真实入口，自行生成请求；缺少隐藏 workEnc 即拒绝读取，即使入口已包含合法签名。
- 官方 `passport2.chaoxing.com/login` 匿名访问确实可重定向 `passport2-api.chaoxing.com`；旧 Android 导航名单遗漏该域。
- 参考 App 用桌面 UA 和本机 WebView CookieManager；校序移动 UA、拒绝第三方 Cookie、拒绝旧 HTTP 官方链接，存在授权模板兼容差异。
- 新版课程可重定向至 `mooc2-ans.chaoxing.com/mooc2-ans/mycourse/stu`；旧本机扫描只接受 mooc1，未到真实作业入口就中止。

## 实施及验证

- [x] 在现有服务端解析器中读取官方实际入口；保留学校提供的时间戳/参数和 10 页/200 条总数验证；校验 courseId/classId/cpi 和隐藏身份。
- [x] Android 固定脚本跟随该入口，签名仅留在 WebView；支持新版学生页及已有分页读取，不新增数据库或登录器。
- [x] 官方 chaoxing.com HTTPS 页面导航兼容子域；仅升级同一官方主机 HTTP 链接，不允许任意站点/端口/外部协议；提取仍限定业务主机和路径。
- [x] 参考桌面页面 UA；本机官方会话落盘，其他来源 Cookie 策略保持独立。
- [x] 操作入口默认收起，未保存时隐藏返回数据按钮；修复状态栏/键盘 inset。
- [x] 回归先失败：缺 workEnc 但存在正式入口、mooc2 学生页有真实入口；修复后通过。补充恶意域、端口、重复参数、跨课程、隐藏身份不符等拒绝案例。
- [ ] Android JVM/可安装 QA APK CI。
- [ ] Ace 6 本人登录、三门课程数量及官方提交状态对照、重启刷新。

仅借鉴接口、页面行为及互操作流程，未复制参考 APK 源码。参考客户端的通知发现及自动阅读/作答不是本次已完成范围；不得把本次代码测试说成全部本人数据已验收。
