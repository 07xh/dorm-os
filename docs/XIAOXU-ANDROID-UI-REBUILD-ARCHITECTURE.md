# 校序 Android UI 重构：统一扩展契约

R4 采用一个 Shell、一个页面框架和一个共享读模型，不另建后端或本地数据库。

- AppRoute：五个一级入口唯一；子页归属一级入口，返回统一交给 Shell。新增子页只添加路由与内容分发，不新建 Scaffold 或导航栏。
- AppPageFrame / AppSpacing / XiaoxuTheme：所有业务页共享顶栏、留白、字体、状态提示。底部安全区归 Shell。
- AppWorkspaceState：复用 Activity 持有的 CloudClient、Room DAO、课表快照；Room 生命周期订阅统一处理。Loading、Ready（含空数据/旧快照）、Unavailable 不混用。
- AppPageActions：页面调用已注入的动作，不自行实例化网络客户端或改变官方数据。
- WorkspaceTaskLogic：继续使用现有来源核验及去重；本机完成操作使用 Room 前置依赖事务。非本机条目仅展示。
- 手机与平板共享同一路由及状态；子页不重复显示手机胶囊。页面 saveable 状态按路由保存。
- 当前是开发分支；成绩、考试、学习进度等尚未完成页面迁移，不使用假数据。第二课堂仍有自己的现存读取入口，后续需接入共享读模型。课表仍为基础列表，后续替换内容而非框架。
- 不删除历史测试、不更改发布流程；全量编译、旧测试迁移、许可证和真机验收通过前不得发布。

新增功能时优先扩展 AppFeaturePage 的对应业务文件与 AppPageActions；若必须新增状态或仓储接口，复用现有业务适配器，不在每个页面复制登录、刷新或持久化逻辑。

## R5 源码复用优先（固定开发原则）
上游存在成熟组件时直接移植，仅修改包名、依赖、主题/数据绑定和必要兼容层。不得重新实现同等组件或新增另一套架构。保持 AppRoute、AppPageFrame、AppWorkspaceState、AppPageActions 不变。
固定来源：znjhahaha/zhengfang-apk@bb3b80a6d64e4c2057c0544bb417696ceaa2a9ed。已移植 GlassKit 四个公共组件及完整 GlassPerformancePolicy，现有 AppEntry 与主题分组统一委托给上游组件。后续课表、成绩、设置继续按上游组件依赖迁移，保留测试及发布门禁。
