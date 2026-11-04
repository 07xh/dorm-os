// Static boundary tests for native Android V0.2. Android CI also compiles the real APK.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
const read = p => readFileSync(new URL('../' + p, import.meta.url), 'utf8');

const manifest = read('apps/xiaoxu-android/app/src/main/AndroidManifest.xml');
const strings = read('apps/xiaoxu-android/app/src/main/res/values/strings.xml');
const appGradle = read('apps/xiaoxu-android/app/build.gradle');
const rootGradle = read('apps/xiaoxu-android/build.gradle');
const main = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/MainActivity.kt');
const db = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalTaskDatabase.kt');
const ci = read('.github/workflows/build-xiaoxu-android.yml');

assert.match(strings, /https:\/\/dorm-os-dk9\.pages\.dev\/#dashboard/);
assert.match(manifest, /android:name="\.MainActivity"[\s\S]*?android:exported="true"/);
assert.match(manifest, /android:name="com\.google\.androidbrowserhelper\.trusted\.LauncherActivity"[\s\S]*?android:exported="false"/);
assert.match(manifest, /android:allowBackup="false"/);
assert.match(manifest, /android:usesCleartextTraffic="false"/);
assert.doesNotMatch(manifest, /READ_SMS|READ_CONTACTS|QUERY_ALL_PACKAGES|BIND_ACCESSIBILITY_SERVICE/i);
assert.doesNotMatch(strings + main + manifest, /wxu_[A-Za-z0-9_-]+|SCHOOL_SESSION_KEY|OWNER_LOGIN_SECRET/i);
assert.doesNotMatch(main, /\bWebView\s*\(|addJavascriptInterface\s*\(|CookieManager\s*\.|document\.cookie/);
assert.match(rootGradle, /org\.jetbrains\.kotlin\.plugin\.compose/);
assert.match(appGradle, /io\.github\.x07xh\.xiaoxu/);
assert.match(appGradle, /androidx\.room:room-runtime:2\.8\.5/);
assert.match(appGradle, /androidx\.compose:compose-bom:2026\.03\.01/);
assert.match(main, /NavigationBar/);
assert.match(main, /NavigationRail/);
assert.match(main, /LocalTaskDatabase/);
assert.match(db, /@Entity\(tableName = "local_tasks"\)/);
// Room v1 -> v2：只增列、不做破坏性迁移，旧任务不得被清空。
assert.match(db, /val source: String\?/);
assert.match(db, /val due: String\?/);
assert.match(db, /version = 2/);
assert.match(db, /Migration\(1, 2\)/);
assert.match(db, /ADD COLUMN source TEXT/);
assert.match(db, /ADD COLUMN due TEXT/);
assert.match(db, /addMigrations\(MIGRATION_1_2\)/);
assert.doesNotMatch(db, /fallbackToDestructiveMigration|DROP TABLE/);
assert.match(db, /suspend fun insert\(/);
assert.match(db, /suspend fun setCompleted\(/);
assert.match(db, /suspend fun remove\(/);
assert.match(ci, /assembleDebug/);
assert.match(ci, /upload-artifact/);

const cloud = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/CloudClient.kt');
const panels = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/CloudPanels.kt');
assert.match(cloud, /https:\/\/dorm-os-dk9\.pages\.dev/);
assert.match(cloud, /instanceFollowRedirects = false/);
assert.match(cloud, /AndroidKeyStore/);
assert.match(cloud, /AES\/GCM\/NoPadding/);
assert.match(cloud, /__Host-xiaoxu_session/);
assert.match(cloud, /\/api\/v1\/auth\/me/);
assert.match(cloud, /\/api\/v1\/auth\/login/);
assert.match(cloud, /\/api\/v1\/auth\/logout/);
assert.match(cloud, /\/api\/v1\/assignments/);
assert.match(cloud, /refreshOnForeground/);
assert.match(cloud, /SystemClock\.elapsedRealtime/);
assert.match(cloud, /60_000L/);
assert.match(main, /LifecycleEventObserver/);
assert.match(main, /Lifecycle\.Event\.ON_RESUME/);
assert.match(main, /DisposableEffect/);
assert.match(main, /cloud\.refreshOnForeground\(\)/);
assert.match(main, /shouldRefreshAfterWebsite = true/);
assert.match(main, /if \(force\) cloud\.refresh\(\)/);

assert.match(cloud, /\/api\/v1\/connections/);
assert.match(cloud, /\/api\/v1\/schedule/);
assert.match(main, /CloudTimetablePanel/);
assert.match(cloud, /\/api\/v1\/wechat\/candidates/);
assert.match(cloud, /\/api\/notices/);
assert.match(cloud, /www2\.ncwu\.edu\.cn/);
assert.match(main, /CloudTasksPanel/);
assert.match(main, /ConnectionsPanel/);
// 消息页由 MessageCenterPanel 承担：公开公告 + 微信待审核计数，两个旧面板已并入。
assert.match(main, /MessageCenterPanel\(cloud, openWebsite, openArticle\)/);
assert.match(panels, /PasswordVisualTransformation/);
assert.doesNotMatch(cloud + panels, /addJavascriptInterface|READ_SMS|READ_CONTACTS|SCHOOL_SESSION_KEY|OWNER_LOGIN_SECRET\s*=/);
assert.doesNotMatch(cloud, /http:\/\/|https:\/\/[^\s"]+school/);

const coursePanel = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ChaoxingHomeworkPanel.kt');
assert.match(main, /ChaoxingHomeworkPanel\(cloud, openWebsite\)/);
assert.match(cloud, /\/api\/v1\/chaoxing-homework\?view=courses/);
assert.match(cloud, /\/api\/v1\/chaoxing-homework\/sync/);
assert.match(cloud, /loadChaoxingCourses/);
assert.match(cloud, /setChaoxingAutoSync\(enabled: Boolean\)/);
assert.match(cloud, /\/api\/v1\/connections\/chaoxing\/auto-sync/);
assert.match(cloud, /JSONObject\(\)\.put\("enabled", enabled\)/);
assert.match(cloud, /autoSyncAvailable/);
assert.match(cloud, /autoSyncLastResult/);
assert.match(cloud, /autoSyncEnabled = isChaoxingAuto && auto!!\.optBoolean/);
assert.match(coursePanel, /connection\.autoSyncAvailable \|\| connection\.autoSyncEnabled/);
assert.match(coursePanel, /定时 Worker 未确认可用/);
assert.match(coursePanel, /开启每小时自动同步/);
assert.match(coursePanel, /关闭每小时自动同步/);
assert.match(coursePanel, /默认关闭/);
assert.match(coursePanel, /定时任务已登记；仍需验证服务端实际运行/);
assert.doesNotMatch(coursePanel, /自动代交|后台自动提交|已经完成全部学校同步/);


assert.match(cloud, /syncChaoxingCourse/);
assert.match(cloud, /interactive_login_required/);
assert.match(cloud, /courseId|classId|cpi/);
assert.match(cloud, /chaoxingVerifiedEmpty/);
assert.match(coursePanel, /同步本课程真实作业及状态/);
assert.doesNotMatch(coursePanel, /自动提交作业|自动作答/);
assert.match(cloud, /loadChaoxingWorkDetail/);
assert.match(cloud, /view=detail/);
assert.match(cloud, /xiaoxu\/chaoxing-work-preview-v1/);
assert.match(cloud, /state\.chaoxingHomework\.firstOrNull/);
assert.match(cloud, /chaoxingDetailBusy/);
assert.match(cloud, /chaoxingSelectedKey != selected\.key/);
assert.match(coursePanel, /只读查看作业内容/);
assert.match(coursePanel, /包含图片，请在学习通官方页面查看原图/);
assert.doesNotMatch(coursePanel, /自动填写|自动代交|显示正确答案/);

const secondClass = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/SecondClassSnapshot.kt');
const secondClassPanel = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/SecondClassPanel.kt');
assert.match(main, /SecondClassPanel\(cloud, openWebsite\)/);
assert.match(cloud, /\/api\/v1\/second-class/);
assert.match(cloud, /parseSecondClassSnapshot\(response\.json\)/);
assert.match(cloud, /secondClass = null/);
assert.match(secondClass, /provenance == "trusted_readonly"/);
assert.match(secondClass, /"user_import"/);
assert.match(secondClass, /isSchoolReportedAggregate/);
assert.match(secondClass, /"user_browser_export"/);
assert.match(secondClassPanel, /官方汇总不与逐条活动分数相加/);
assert.match(secondClassPanel, /用户自行导入的数据（非官方实时同步）/);
assert.doesNotMatch(secondClass + secondClassPanel, /CookieManager|schoolPassword|READ_SMS|addJavascriptInterface/);

// 课表页（Issue #209）：只读 CloudClient.state，四态显式，不伪造课程与当前周。
const schedulePanel = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleScreen.kt');
assert.match(schedulePanel, /fun CloudTimetablePanel\(/);
assert.match(schedulePanel, /deriveScheduleUiState/);
assert.match(schedulePanel, /ScheduleUiState\.Loading/);
assert.match(schedulePanel, /ScheduleUiState\.NotConnected/);
assert.match(schedulePanel, /ScheduleUiState\.Failed/);
assert.match(schedulePanel, /state\.lessons/);
assert.match(schedulePanel, /trusted_readonly|可信/);
assert.doesNotMatch(schedulePanel, /HttpURLConnection|openConnection|WebView|addJavascriptInterface|CookieManager/);
assert.doesNotMatch(schedulePanel, /VerifiedLesson\(/, '不得在界面层手写课程对象冒充真实数据');
assert.match(schedulePanel, /CourseDetailBottomSheet\(/, '可信课程应有原生详情抽屉');
assert.match(schedulePanel, /ModalBottomSheet\(/, '课程详情应使用原生底部抽屉');
assert.match(schedulePanel, /filtered\.firstOrNull \{ it\.key == selectedCourseKey \}/, '抽屉只能读取当前可信课程');
assert.match(schedulePanel, /onClickLabel = "查看课程详情"/);

// 消息中心：公开公告与微信待审核分开，失败/过期要显式提示，不伪造正文。
const messageCenter = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/MessageCenter.kt');
assert.match(messageCenter, /fun filterNotices\(/);
assert.match(messageCenter, /state\.notices/);
assert.match(messageCenter, /state\.pendingWechatCount/);
assert.match(messageCenter, /state\.publicError/);
assert.match(messageCenter, /noticesPartial/);
assert.match(messageCenter, /openArticle\(notice\.url\)/);
assert.match(messageCenter, /未连接校序云端/);
assert.doesNotMatch(messageCenter, /HttpURLConnection|openConnection|WebView|addJavascriptInterface|CookieManager|document\.cookie/);
assert.doesNotMatch(messageCenter, /AcademicNotice\(/, '不得在界面层手写公告对象冒充真实数据');

// App 原生微信审核：只通过现有 Owner Session 读取真实候选、二次确认后提交。
// 后端沿用已上线的同源 POST + 人工审核事务，绝不新增微信采集器。
const wechatReview = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/WechatReviewPanel.kt');
const reviewRoute = read('functions/api/v1/wechat/candidates/[id].js');
assert.match(messageCenter, /WechatReviewPanel\(/);
assert.doesNotMatch(messageCenter, /WechatSourceCard\(/);
assert.match(cloud, /data class WechatCandidate/);
assert.match(cloud, /wechatCandidates = candidates \?: state\.wechatCandidates/);
assert.match(cloud, /reviewStatus"\) != "needs_review"/);
assert.match(cloud, /suspend fun reviewWechatCandidate\(/);
assert.match(cloud, /\/api\/v1\/wechat\/candidates\/\$candidateId/);
assert.match(cloud, /JSONObject\(\)\.put\("decision", decision\)/);
assert.match(cloud, /reviewWechatCandidate[\s\S]*?sessionEpoch/);
assert.match(cloud, /clearAccountSnapshots\(/);
assert.match(wechatReview, /ModalBottomSheet\(/);
assert.match(wechatReview, /AlertDialog\(/);
assert.match(wechatReview, /client\.reviewWechatCandidate\(selected\.id, decision\)/);
assert.match(wechatReview, /本条候选没有摘要；请前往微信核实原消息/);
assert.match(wechatReview, /不等于学校官方作业/);
assert.doesNotMatch(wechatReview, /HttpURLConnection|openConnection|WebView|CookieManager|READ_SMS|READ_CONTACTS/);
assert.match(reviewRoute, /authorize\(context\)/);
assert.match(reviewRoute, /sec-fetch-site/);
assert.match(reviewRoute, /reviewWechatCandidate\(auth\.db,auth\.userId/);

// 任务看板：来源与截止只写本地库，不碰网络，不冒充官方截止。
const taskBoard = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/TaskBoard.kt');
assert.match(taskBoard, /TASK_SOURCES/);
assert.match(taskBoard, /dao\.insert\(LocalTask\(/);
assert.match(taskBoard, /本人标注/);
assert.doesNotMatch(taskBoard, /HttpURLConnection|openConnection|WebView|addJavascriptInterface|CookieManager/);

const taskBackup = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalTaskBackup.kt');
const taskBackupPanel = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/LocalTaskBackupPanel.kt');
assert.match(main, /LocalTaskBackupPanel\(\)/);
assert.match(db, /suspend fun snapshot\(\): List<LocalTask>/);
assert.match(taskBackup, /xiaoxu\/local-tasks-backup-v1/);
assert.match(taskBackup, /xiaoxu\/local-tasks-backup-v2/);
assert.match(taskBackup, /schema == FORMAT_V1 \|\| schema == FORMAT_V2/);
assert.match(taskBackup, /"source", "due"/);
assert.match(taskBackup, /validDue/);
assert.match(taskBackup, /source = source, due = due/);
assert.match(taskBackup, /withTransaction/);
assert.match(taskBackup, /MAX_BYTES/);
assert.match(taskBackup, /MAX_TASKS/);
assert.match(taskBackup, /seen\.add\(Pair/);
assert.match(taskBackupPanel, /ActivityResultContracts\.CreateDocument/);
assert.match(taskBackupPanel, /ActivityResultContracts\.OpenDocument/);
assert.match(taskBackupPanel, /LocalTaskBackup/);
assert.doesNotMatch(taskBackup + taskBackupPanel, /CookieManager|addJavascriptInterface|SCHOOL_SESSION_KEY|OWNER_LOGIN_SECRET|READ_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE/);
assert.doesNotMatch(manifest, /READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE/);
console.log('校序 Android V0.6.1：本人课程/作业、只读题目预览、会话与来源边界静态检查通过');
