# 校序响应速度修复

**目标：** 基于 #351 的即时切页实现，减少交互时重复 SQL、重复列表整理和主线程解析；保持五页及数据边界。

**依据：** 用户要求全面提升流畅度，最新反馈要求恢复无整页转场体验；当前主线 4eff7b5b 已完成转场回退。以现有 Compose、Room、CloudClient、WorkManager 为实现基础。

- [x] 审查六处 `dao.observe().collectAsState()`：每次重组会新建冷 Flow，重新查询。用共享 `rememberLocalRows` 固定 owner/query、去除相同结果，并按 Activity 生命周期收集。
- [x] 缓存首页本机作业、当天课程、任务摘要；第二课堂缓存积分/活动分组、批次校验、学期排序及目录筛选。任务完成数只随真实列表变化计算。
- [x] 公告、任务分页的行投影与验证放在 Default dispatcher；仍在 main 发布 CloudUiState，仍需完整分页和账号/请求代次核验。
- [x] 现有隔离 QA 改用 R8 优化的 release 构建，保留 debug 编译/JVM验证与独立包名。正式 release 仍默认未签名；仅显式性能参数使用临时 debug 签名。
- [x] 新增离线 Compose 运行时用例：20 次界面更新只启动一次查询、新记录持续可见、后台停止查询、返回读取最新值、切换 owner 释放旧查询。
- [x] 清退四处二级页旧动画容器、空过渡适配和两个仅验证旧适配器的测试；统一 `ImmediateRouteContent`，每个路由独立保存状态，新增快速切换/销毁/恢复的 Compose 运行时用例。
- [x] 清理后本机 Android Kotlin 编译、132 项 JVM 单元测试（22 个测试套件，0 失败/错误/跳过）与 AndroidTest APK 编译通过。清理前 134 项通过的记录属于 f01e2f04 阶段，不能混用测试数量。
- [x] 优化隔离 APK 构建；aapt 核实 `.unifiedqa` 包名、主 Activity 和不可调试状态；apksigner v2 与 ZIP/DEX 检查通过。最终提交后重建 APK，避免只交付旧 SHA 的包。
- [ ] Actions 精确 SHA 综合 CI、Android 模拟器执行新用例（用户已明确授权推送到 07xh/dorm-os 并创建指向 #351 的子 PR，待推送后核验）。
- [ ] Ace 6 本人数据：连续切页/勾选/弹窗/课表滚动、前后台切换、帧耗时及状态保留。

## 验证与边界

本机 `npm test`、完整 `npm run check`、Android 静态检查和工作流 YAML/Shell 语法检查通过。本轮在临时目录补齐 Gradle 8.13、SDK 36、JDK 17，解决继承代理和 CA 信任配置后，Android 编译与 JVM 测试成功；没有 /dev/kvm，新增运行时用例已编译但未执行。运行时测试必须使用无 INTERNET 权限的 `.secondclassruntime` 变体，不修改本人数据。

代码修复不构成帧率改善的实测证据。保持 #351 Draft，不自动合并 main；只有真机对照后才能宣称流畅度达标。未新增 UI 体系、数据连接器、Worker 或课表同步；保留北化玻璃导航材质、课程颜色和数据核验语义。

## 审查保留项

主线只组合当前主页面；北化导航的位移/缩放已有 layout/draw 阶段隔离；课表网格已有周次缓存和节次索引；任务已有逐行临时反馈与失败回滚；消息筛选和稳定 key 已缓存；截止提醒已有 IO 调度与差量重排。本轮不重复实现这些已存在的优化。
