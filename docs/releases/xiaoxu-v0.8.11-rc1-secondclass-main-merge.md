# 校序 0.8.11 RC｜第二课堂活动读取修复与主线整合（2026-11-07）

## 来源与边界
- 此发布候选基于最新统一原生 UI #351 和第二课堂修复 #407，合并 main 已有的 12 份独立校园跑只读文件与最新公告数据，避免旧分支覆盖新版 UI。
- main 与 #351 曾同时编辑的文件：Android build.gradle 保留新 UI 的 0.8.11 配置与已增加的 JVM JSON 测试依赖；package.json 保留新 UI 已清退的旧测试脚本及最新版学习通轮换测试；academic-school-login.mjs 保留新 UI 已退役的二课 CAS 探测清理和现存学习通安全会话路径。
- 第二课堂保留 NativeSchoolConnectActivity / SecondClassPage / Room / 现有本人会话，不引入第二套登录或云端凭据采集。
- 同账号活动广场查询不可用时保留前次已核验目录与原始时间戳；账号变化清空旧目录；积分和本人报名活动仍可独立正常保存。

## 代码清理
- 从 #351 引入已验证的旧 SecondClassPanel、CAS 探测/Sudy 实验、未接线目录/映射库和冗余静态测试删除；不删除历史 D1 迁移、Room、备份或有效官方只读适配器。
- 历史 APK 请求是 CI 触发证据，删除本身可能触发构建；不为清理而盲目批量删除。

## 验收边界
- CI/JVM/Android 仪器测试及 APK 构建按每次具体 commit 单独核验。
- 真实学校活动广场非空字段与分页仍需本人授权下核验；不能因通过合成测试而标记活动同步完全可用。
- Android 生产版没有发布签名密钥，GitHub debug/隔离 QA 包仅供测试；与 Cloudflare Pages 的正式部署是两件事。
