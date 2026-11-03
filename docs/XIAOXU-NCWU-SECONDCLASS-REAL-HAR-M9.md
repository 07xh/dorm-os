# 校序 · 华水第二课堂实测 HAR 契约 M9（2026-11-03）

## 证据来源与隐私
本人提供 dekt.ncwu.edu.cn 的网络 HAR。离线分析 112 条网络记录，
101 条属于校方域名。**原始 HAR 未加入本仓库、PR、Cloudflare KV/D1 或任何公开日志。**
HAR 可能含本人的 Cookie、请求头、微信授权信息和其他个人标识；
不要把它上传到公开 Issues、CI artifacts 或 Git 提交。

以下仅记录原始业务请求的**路径、方法与字段名称**，不记录学号、姓名、
Cookie、Token、个人活动详情、真实分数或时间。

## 已验证真实的本人只读查询

| 学校路径（均为 POST 查询） | HAR 中的响应结构 | 功能 |
| --- | --- | --- |
| /sys/stu/center/signupactivity/list | data[], startIndex, totalPage, totalSize | 本人活动与核算状态 |
| /sys/stu/score/list | data[], startIndex, totalPage, totalSize | 本人分学期素质分与汇总 |
| /sys/stu/score/types | [{code,name}] | 六个素质分类别名称 |
| /sys/activitytype/all | [{code,name,pass_score,required_flag,id}] | 活动类别及要求 |
| /sys/stu/activity | data[], startIndex, totalPage, totalSize | 活动检索，本次为空 |

报名活动列表请求表单字段 **page、pagesize**（HAR 原页每页 50）。
本人活动行有：id、activity_id、activity_name、activity_start、activity_end、
school_year、school_term、signup_state、verify_state、activity_state、
score_value 等，另有不可存入校序的学生身份字段。
分数列表有 school_term_index、score_value、score_plus、
activity_type1_score 到 activity_type6_score 等。

### 从学校页面 JavaScript 核对的关键语义

- 个人成绩页面显示 score_value 的栏目名称为 **素质分**。
- score_plus 在页面配置中的名称为 **志愿服务时长**，它不是素质分。
- school_term_index = 100 的行被学校页面显示为 **合计**；
  分学期和合计不可重复加总。
- 报名活动页面对 verify_state=1 且 activity_state=5
  显示 **已核算**；state=4 显示 **未核算**；state=6 显示 **等待核算**；
  verify_state=2 也显示 **已核算**。
- 报名、审核、核算不等价。校序新增独立 settlementStatus，不再将
  只有报名记录的活动误标为审核通过或完成。
- 当活动处于已核算状态时，才将该活动的 score_value
  作为已核算素质分保存在记录 points 字段。未核算、等待核算
  不自动计入积分或实际学时。

## 本批代码
- lib/academic-ncwu-second-class.mjs
  - 真实学校路径白名单；POST 只读查询契约，不支持签到、报名、注销、修改。
  - 对应 page / pagesize 与 totalPage / totalSize 分页处理；
    最后一页仍要求条数满足总数，不凭 HTTP 200 判定完成。
  - 个人活动、核算状态、学期字段严格解码；请求期间核对学校会话的
    student_code 与受信任授权的当前用户绑定；绝不保存 student_code。
  - school_term_index=100 作为学校原始合计，不将其与学期行相加。
- lib/academic-ncwu-second-class-sync.mjs
  - **先完整取回活动 + 学校分数，再单批 D1 原子写入**；
    活动/成绩任何一端无授权、失败、分页不完整就保留旧数据。
- migrations/0009_ncwu_second_class_official_scores.sql
  - 素质分与志愿服务时长官方汇总按用户隔离，与活动明细分开保存，
    绑定同一验证批次。
- functions/api/v1/second-class.js
  - Access JWT 验证后，只有当前可信批次才可向本人展示学校真实汇总。
  - 本人自行上传的 user_import 快照不能宣称官方汇总。
- public/xiaoxu-academic-client.js
  - 当前已有官方合计时，分别显示素质分和志愿时长；
    未有真实数据时仍显示未连接。没有假设后台 Cron 已启动。

## 已完成与未完成的界限
**本轮的路径和字段是真实 HAR 证实的**，而不是依靠其他学校的 API 猜测。
但 HAR 代表浏览器已授权会话的历史记录：
它本身**不提供可安全长期续期的会话**，也不等于校序现在能直接后台登录学校。

正式无人值守自动更新仍需：
1. 在用户控制的受信任本地组件或授权服务中实现 verifySession，
   严格绑定当前学校学生身份与校序 Access 本人身份。
2. 实现实际的 readPost：只向学校已确认的 HTTPS 域名发上述四项
   已确认的 POST 查询，确保平台同意该调用且不滥用请求频率。
3. 核验会话过期后的重新授权方式；没有合适续期方式则提示用户再次正常登录，
   不绕过 SSO、验证码或安全策略。
4. Cloudflare D1 0009 迁移前按现有规程备份并检查 0001–0008；
   验证生产 Access 覆盖所有本人私有路由，先在测试库验收。
5. 最后再开启受限间隔的后台调度；目前 automaticSync 仍为 false。

## 合成测试与验收
scripts/xiaoxu-ncwu-second-class-real-contract-tests.mjs
只使用与原始响应**结构相同但内容完全合成**的样例，
包括：活动状态、核算前后积分、学期与合计不重复计数、
学校身份异常、空列表、分页缺口、素质分与志愿时长分离、
D1 可信批次原子写入/失败保持旧数据、敏感字段不得持久化。

真实账号的原始 HAR 不会被上传到 CI 参与测试。
