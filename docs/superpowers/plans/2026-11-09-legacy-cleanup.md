# 校序旧实现清理计划

目标：以 main@3909384 为基准，删除明确无生产调用且被现有实现替代的代码，纠正过期开发规则；不改 UI 外观、数据格式、鉴权、迁移与学校读写行为。

- [x] 核查 main、#351 关闭记录（#410 已整合）、开放 PR 与当前引用。
- [x] 删除通用旧 scheduler 的 dueJob/runDueJobs，保留当前学习通自动同步实际使用的 nextAttempt；测试保留时间/退避边界。
- [x] 删除第二课堂只写活动的旧 runAuthorizedSecondClassSync，保留 collector；由当前活动+积分原子写入链路及其测试承担持久化验证。
- [x] 删除无生产调用的 candidateAcademic/candidateSecondClass 与 ownerModeEnabled，保留实际课表候选和成绩考试契约。
- [x] 收敛 AGENTS 与 Android README 的当前事实，历史版本记录不作为开发指令。
- [x] 全量 Node 检查、Android 静态安全、二课本机读取、微信 Python、桌面/手机 E2E；审查删除项和开放分支兼容性，提交独立 PR。

删除证据：git grep 在 lib/functions/public/workers 中仅命中定义或同一旧链路内部引用；#429/#431/#432/#433 对上述模块没有修改，也没有调用被删符号。测试只迁移到当前路径，不以删测试掩盖功能缺口。保留所有 migrations 与旧用户数据恢复路径。

边界：不自动合并任何现有 Draft，不运行生产 D1 迁移，不把 CI 通过当作学校本人数据或 Ace 6 验收。Android SDK/真机测试未执行时明确说明。

结果：本地 Node/静态/二课/微信检查通过。浏览器因 Chromium 缺失及下载 ZIP 损坏未完成；Android 编译与浏览器 E2E 待远端 CI，不算已验收。独立审查未发现已证实的阻断问题，报告链接已补齐。
