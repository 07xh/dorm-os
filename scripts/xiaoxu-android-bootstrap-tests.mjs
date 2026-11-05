// Static boundary tests for native Android V0.2. Android CI also compiles the real APK.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
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
assert.match(db, /version = 3/);
assert.match(db, /Migration\(2, 3\)/);
assert.match(db, /CREATE TABLE IF NOT EXISTS native_school_records/);
assert.match(db, /Migration\(1, 2\)/);
assert.match(db, /ADD COLUMN source TEXT/);
assert.match(db, /ADD COLUMN due TEXT/);
assert.match(db, /addMigrations\(MIGRATION_1_2, MIGRATION_2_3\)/);
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
const workspace = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/RealDataWorkspace.kt');
assert.match(main, /RealAcademicHome\(localTasks, client = cloud/);
assert.match(main, /RealAcademicTaskHub\(localTasks, dao, cloud, openWebsite\)/);
assert.ok(main.includes('https://dorm-os-dk9.pages.dev/#settings'), 'school connector must deep-link into the existing connection center');
assert.match(workspace, /fun combineWorkspaceTasks\(/);
assert.match(workspace, /fun RealAcademicHome\(/);
assert.match(workspace, /fun RealAcademicTaskHub\(/);
assert.match(workspace, /LocalTaskBoard\(localTasks, dao\)/);
assert.match(workspace, /CloudTasksPanel\(client\)/);
assert.match(workspace, /不能据此推断学校没有作业/);
assert.match(workspace, /当前教学周未核实/);
assert.doesNotMatch(workspace, /WebView|HttpURLConnection|CookieManager/);
assert.doesNotMatch(workspace, /VerifiedLesson\(/);
assert.match(main, /ConnectionsPanel/);
// 消息页由 MessageCenterPanel 承担：公开公告 + 微信待审核计数，两个旧面板已并入。
assert.match(main, /MessageCenterPanel\(cloud, openWebsite, openArticle\)/);
assert.match(panels, /PasswordVisualTransformation/);
assert.match(panels, /fun ConnectionsPanel\(/);
assert.match(panels, /openLocalSchool\(id\)/);
assert.match(panels, /只有一台手机？打开学习通官网/);
assert.match(panels, /本机打开青果官方课表/);
assert.match(panels, /本机打开第二课堂官网/);
assert.match(panels, /本机官网读取仅保存当前可见记录/);
assert.match(cloud, /val connectionTrusted = state.platforms.firstOrNull/);
assert.match(cloud, /val workChecked = state.chaoxingCheckedAt != null/);
assert.match(cloud, /!connectionTrusted/);
assert.match(cloud, /!workChecked/);
assert.match(cloud, /state.chaoxingCheckedAt != null && state.chaoxingError == null/);
assert.match(panels, /openLocalSchool: \(String\) -> Unit/);
assert.match(panels, /connectionStatusLabel\(platform\)/);
assert.match(panels, /client\.loadChaoxingCourses\(\)/);
assert.match(panels, /client\.syncAuthorizedSchool\(id\)/);
assert.match(panels, /"ncwu" to "青果教务"/);
assert.match(panels, /"second_class" to "第二课堂"/);
assert.match(panels, /学校授权/);
assert.match(cloud, /suspend fun syncAuthorizedSchool\(provider: String\): Boolean/);
assert.match(cloud, /provider !in setOf\("ncwu", "second_class"\)/);
assert.match(cloud, /linked\?\.sessionState != "active"/);
assert.match(cloud, /\/api\/v1\/connections\/\$provider\/sync/);
assert.match(cloud, /result\.json\.optBoolean\("refreshed"\)/);
assert.match(cloud, /schoolSyncBusyProvider/);
// A failed unrelated WeChat/tasks read must not negate a fresh school sync,
// while a failed connection/snapshot read must not reuse stale "connected" UI.
assert.match(cloud, /connectionsFresh = platforms != null/);
assert.match(cloud, /scheduleFresh = scheduleLoaded/);
assert.match(cloud, /state = state\.copy\(connectionsFresh = false, scheduleFresh = false,/);
assert.match(cloud, /secondClassBusy = true, secondClassFresh = false/);
// An earlier owner/second-class HTTP response may arrive after a newer
// foreground, QR, or explicit refresh. Do not publish that stale response.
assert.match(cloud, /private var privateSnapshotReadId: Long = 0L/);
assert.match(cloud, /val snapshotRead = \+\+privateSnapshotReadId/);
assert.match(cloud, /snapshotRead != privateSnapshotReadId/);
assert.match(cloud, /private var secondClassReadId: Long = 0L/);
assert.match(cloud, /secondClassSnapshotReadId == privateSnapshotReadId/);
assert.match(cloud, /val readId = \+\+secondClassReadId/);
assert.match(cloud, /readId != secondClassReadId/);
assert.match(cloud, /snapshotRead != privateSnapshotReadId\) return/);
assert.match(cloud, /snapshotRead == privateSnapshotReadId/);
assert.match(cloud, /secondClass = snapshot, secondClassFresh = true/);
assert.match(cloud, /state\.connectionsFresh &&/);
assert.match(cloud, /state\.scheduleFresh && state\.scheduleStatus == "ready"/);
assert.match(cloud, /state\.secondClassFresh &&/);
assert.match(cloud, /state\.secondClass\?\.provenance == "trusted_readonly"/);
assert.doesNotMatch(cloud, /val verified = state\.privateError == null/);
assert.match(panels, /本次无法核验连接状态/);
assert.match(panels, /val canRead = state\.authenticated && state\.connectionsFresh/);
assert.match(cloud, /clearAccountSnapshots/);
assert.doesNotMatch(panels, /schoolPassword|CookieManager|WebView|READ_SMS/);

assert.doesNotMatch(cloud + panels, /addJavascriptInterface|READ_SMS|READ_CONTACTS|SCHOOL_SESSION_KEY|OWNER_LOGIN_SECRET\s*=/);
assert.doesNotMatch(cloud, /http:\/\/|https:\/\/[^\s"]+school/);

const coursePanel = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ChaoxingHomeworkPanel.kt');
assert.match(workspace, /ChaoxingHomeworkPanel\(client, openWebsite\)/);
// Native first-party QR uses existing server start/image/status, never new login routes.
assert.match(cloud, /\/api\/v1\/connections\/chaoxing\/qr\/start/);
assert.match(cloud, /\/api\/v1\/connections\/chaoxing\/qr\/image/);
assert.match(cloud, /\/api\/v1\/connections\/chaoxing\/qr\/status/);
assert.match(cloud, /suspend fun startChaoxingQr\(\)/);
assert.match(cloud, /suspend fun pollChaoxingQr\(\)/);
assert.match(cloud, /fun cancelChaoxingQr\(\)/);
assert.match(cloud, /qrGeneration/);
assert.match(cloud, /image_too_large/);
const svgRaster = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/NativeQrSvgRenderer.kt');
assert.match(svgRaster, /internal object NativeQrSvgRenderer/);
assert.match(svgRaster, /document\.matchEntire\(text\)/);
assert.match(svgRaster, /out_of_bounds_module/);
assert.match(svgRaster, /count > 20000/);
assert.match(svgRaster, /Bitmap\.CompressFormat\.PNG/);
assert.match(svgRaster, /bitmap\.recycle\(\)/);
assert.match(cloud, /NativeQrSvgRenderer\.rasterize\(bytes\)/);
assert.doesNotMatch(svgRaster, /addJavascriptInterface|evaluateJavascript|loadUrl|CookieManager/);
assert.match(cloud, /qr\.polls >= 24/);
assert.match(cloud, /completeSchoolReturn\("chaoxing"\)/);
assert.doesNotMatch(cloud, /addJavascriptInterface|CookieManager|getCookie\(/);
assert.match(panels, /显示扫码二维码（需另一设备）/);
assert.match(panels, /BitmapFactory\.decodeByteArray/);
assert.match(panels, /Lifecycle\.Event\.ON_PAUSE/);
assert.match(panels, /LaunchedEffect\(qrStartedAt, foreground\)/);
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
// Owner-initiated multi-course sync reuses the same backend validation as a
// single course. Never infer success from the HTTP status alone.
assert.match(cloud,/suspend fun syncChaoxingAllCourses\(\)/);
assert.match(cloud,/val courses = state\.chaoxingCourses\.toList\(\)/);
assert.match(cloud,/for \(course in courses\)/);
assert.match(cloud,/verifiedChaoxingWork\(result\.json, course\)/);
assert.match(cloud,/val \(work, verifiedEmpty\) = verifiedChaoxingWork\(response\.json, selected\)/);
assert.match(cloud,/result\.code == 401/);
assert.match(cloud,/if \(consecutiveFailures >= 2\) break/);
assert.match(cloud,/val skipped = courses\.size - checked/);
assert.match(cloud,/chaoxingBatchBusy = false/);
assert.match(coursePanel,/client\.syncChaoxingAllCourses\(\)/);
assert.match(coursePanel,/一键核验全部课程作业/);
assert.match(coursePanel,/state\.chaoxingBatchStatus/);
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

/* Dedicated Qingguo importer is owned by main; only Chaoxing and second-class
 * use the generic official WebView reader. This is NOT a trusted school sync. */
const localNative = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/NativeSchoolConnectActivity.kt');
const nativeRecords = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/NativeSchoolRecords.kt');
const localSchoolUi = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/LocalSchoolPanels.kt');
assert.match(manifest, /android:name="\.NativeSchoolConnectActivity"[\s\S]*?android:exported="false"/);
assert.match(main, /NativeSchoolConnectActivity\.EXTRA_PROVIDER/);
assert.match(main, /provider == "ncwu"/);
assert.match(main, /LocalQingguoImportActivity::class\.java/);
assert.match(main, /ConnectionsPanel\(cloud, openWebsite, openLocalSchool,/);
assert.match(main, /startSchoolAuthorization, localCaptureReturn\)/);
assert.match(main, /ActivityResultContracts\.StartActivityForResult/);
assert.match(main, /nativeSchoolLauncher\.launch\(/);
assert.match(main, /result\.resultCode == Activity\.RESULT_OK/);
assert.match(main, /LocalSchoolImportReceipt\(/);
assert.match(main, /localCaptureReturn = lastLocalCapture/);
assert.match(main, /if \(localCaptureReturn != null\) current = Page\.PROFILE/);
assert.match(panels, /startSchoolAuthorization: \(String\) -> Unit/);
assert.match(panels, /startSchoolAuthorization\(id\)/);
assert.match(main, /cloud\.completeSchoolReturn\(provider\)/);
assert.match(manifest, /android:scheme="xiaoxu" android:host="school-auth-return"/);
const handoff = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/SchoolAuthReturn.kt');
assert.match(handoff, /SecureRandom\(\)\.nextBytes\(bytes\)/);
assert.match(handoff, /MessageDigest\.isEqual/);
assert.match(handoff, /ownerBinding != savedOwner/);
assert.match(handoff, /prefs\.edit\(\)\.clear\(\)\.commit\(\)/);
assert.doesNotMatch(handoff, /CookieManager|document\.cookie|SCHOOL_SESSION_KEY|WebView/);
const appReturn = read('public/xiaoxu-android-return.js');
assert.match(read('public/index.html'), /xiaoxu-android-return\.js/);
assert.match(appReturn, /sessionStorage\.removeItem\(KEY\)/);
assert.match(appReturn, /xiaoxu:\/\/school-auth-return/);
assert.doesNotMatch(appReturn, /document\.cookie|schoolPassword|CAS_TICKET/);
assert.match(localSchoolUi, /LocalSchoolConnectionPanel\(/);
assert.match(localSchoolUi, /专用课表导入/);
assert.match(workspace, /LocalSchoolRecordSection\(setOf\("course", "homework", "activity", "credits"\)\)/);
assert.match(panels, /LocalSchoolConnectionPanel\(openLocalSchool, recentImport\)/);
assert.match(panels, /if \(recentImport != null\) localAdvanced = true/);
assert.match(localSchoolUi, /data class LocalSchoolImportReceipt\(/);
assert.match(localSchoolUi, /records\.filter \{ it\.provider == provider \}/);
assert.match(localSchoolUi, /stored\.size == justImported\.savedCount/);
assert.match(localSchoolUi, /已从本机数据库重新读取/);
assert.match(localSchoolUi, /查看本机全部/);
assert.match(localNative, /EXTRA_RETURN_KIND/);
assert.match(localNative, /EXTRA_SAVED_COUNT/);
assert.match(localNative, /setResult\(Activity\.RESULT_OK, Intent\(\)/);
assert.match(localNative, /savedKind = kind/);
assert.match(localNative, /savedCount = accepted\.size/);
assert.match(localNative, /\.replaceKind\(provider, kind, accepted\)/);
assert.doesNotMatch(main + localSchoolUi, /ownerCookie|schoolPassword|CAS_TICKET/);
for (const host of ['i.chaoxing.com','dekt.ncwu.edu.cn']) assert.ok(localNative.includes(host));
assert.doesNotMatch(localNative, /"ncwu" to "https:/);
assert.match(localNative, /finishAndReturn = Button\(this\)/);
assert.match(localNative, /finishAndReturn\.isEnabled = true/);
assert.match(localNative, /if \(!navigationAllowed\(provider, url\)\)/);
assert.match(localNative, /pageFailed = true/);
assert.match(localNative, /onReceivedSslError/);
assert.match(localNative, /handler\.cancel\(\)/);
assert.match(localNative, /setAcceptThirdPartyCookies\(this, false\)/);
assert.match(localNative, /mixedContentMode = WebSettings\.MIXED_CONTENT_NEVER_ALLOW/);
assert.match(localNative, /browser\.evaluateJavascript\(READ_VISIBLE_PAGE\)/);
assert.match(localNative, /replaceKind\(provider, kind, accepted\)/);
assert.match(localNative, /LocalTaskDatabase\.open\(applicationContext\)/);
assert.match(db, /abstract fun nativeSchoolRecords\(\): NativeSchoolRecordDao/);
assert.match(nativeRecords, /@Transaction/);
assert.match(nativeRecords, /require\(rows\.isNotEmpty\(\) && rows\.size <= 120\)/);
assert.doesNotMatch(localNative + nativeRecords + localSchoolUi,
 /addJavascriptInterface|document\.cookie|CookieManager\.getCookie|OWNER_LOGIN_SECRET|SCHOOL_SESSION_KEY/);
assert.match(appGradle, /versionName '0\.7\.5'/);

/* Official-page local readable content, tested only with synthetic DOM.
 * This never claims real account login or exercises hidden school APIs. */
assert.match(localNative, /CHAOXING_HOMEWORK/);
assert.match(localNative, /"学习通 · 打开我的作业"/);
assert.match(localNative, /mooc1-api\.chaoxing\.com\/mooc-ans\/mooc2\/work\/all-task/);
assert.match(localNative, /path == "\/mooc-ans\/mooc2\/work\/all-task"/);
assert.match(localNative, /li\[onclick\*="goTask"\]/);
assert.match(localNative, /p\.overHidden2\.fl/);
assert.match(localNative, /p\.status\.fl/);
assert.match(localNative, /foundKind === 'homework'/);
assert.match(localNative, /homeworkOverview && foundKind !== 'homework'/);
assert.match(localNative, /foundKind === 'homework' \? titleIndex/);
assert.match(localNative, /if \(appended\) kind = foundKind/);
assert.doesNotMatch(localNative, /getAttribute\(['"]data['"]\)/);
const inlineMatch = localNative.match(/private const val READ_VISIBLE_PAGE = """([\s\S]*?)"""/);
assert.ok(inlineMatch, 'on-device readonly DOM script must be testable');
const inlineScript = inlineMatch[1];
function fixtureDocument({table=null,li=[],courses=[]}={}) {
    return {
      querySelectorAll(selector) {
        if(selector==='iframe')return [];
        if(selector==='table')return !table?[]:Array.isArray(table)?table:[table];
        if(selector==='li[onclick*="goTask"]')return li;
        if(selector.includes('.course-name'))return courses;
        return [];
      }
    };
}
const visible = node => ({...node,getClientRects:()=>[{}]});
function parseNativePage(doc, host, pathname) {
  const returned=runInNewContext(inlineScript,{document:doc,
    location:{hostname:host,pathname,origin:'https://'+host}});
  return JSON.parse(returned);
}
const workLi=visible({
  querySelector(sel) {
    if(sel==='p.overHidden2.fl')return {innerText:'工程制图 第三次作业'};
    if(sel==='p.status.fl')return {innerText:'未提交'};
    return null;
  }
});
const workResult=parseNativePage(fixtureDocument({li:[workLi]}),
 'mooc1.chaoxing.com','/mooc2/work/list');
assert.equal(workResult.kind,'homework');
assert.equal(workResult.records.length,1);
assert.equal(workResult.records[0].title,'工程制图 第三次作业');
assert.match(workResult.records[0].details,/未提交/);
assert.ok(!JSON.stringify(workResult).includes('workEnc'));
const courseTitle=visible({getAttribute(key){return key==='title'?'高等数学':null;},innerText:'高等数学'});
const courseResult=parseNativePage(fixtureDocument({courses:[courseTitle]}),
 'mooc2-ans.chaoxing.com','/mooc2-ans/visit/courselistdata');
assert.equal(courseResult.kind,'course');
assert.equal(courseResult.records[0].title,'高等数学');
function nativeTable(headers,cells) {
 const column=text=>({innerText:text});
 const headerRow=visible({
   querySelectorAll(selector) {return selector==='th,td'?headers.map(column):[];}
 });
 const contentRow=visible({
   querySelectorAll(selector) {
     return selector===':scope > th,:scope > td'?cells.map(column):[];
   }
 });
 return visible({querySelectorAll(s){return s==='tr'?[headerRow,contentRow]:[];}});
}
const homeworkOverview=parseNativePage(fixtureDocument({
 table:nativeTable(['课程名称','作业名称','提交状态'],['高数','作业测试','未交'])
}), 'mooc1-api.chaoxing.com','/mooc-ans/mooc2/work/all-task');
assert.equal(homeworkOverview.kind,'homework');
assert.equal(homeworkOverview.records[0].title,'作业测试',
 'must prefer homework column, not course column');
assert.match(homeworkOverview.records[0].details,/高数/);
// A course title plus a homework-status column is NOT an assignment title.
const statusOnlyOverview=parseNativePage(fixtureDocument({
 table:nativeTable(['课程名称','作业状态'],['高等数学','未提交'])
}), 'mooc1-api.chaoxing.com','/mooc-ans/mooc2/work/all-task');
assert.equal(statusOnlyOverview.records.length,0,
 'do not save a course title as homework when only status is visible');
assert.equal(statusOnlyOverview.kind,'',
 'no official homework kind without an explicit work title');
const courseSummary=parseNativePage(fixtureDocument({
 table:nativeTable(['课程名称','教师'],['高等数学','李老师'])
}), 'mooc1-api.chaoxing.com','/mooc-ans/mooc2/work/all-task');
assert.equal(courseSummary.records.length,0,
 'course summary in the homework page is not a homework item');
const blankWork=parseNativePage(fixtureDocument({
 table:nativeTable(['课程名称','作业名称','提交状态'],['高等数学','','未交'])
}), 'mooc1-api.chaoxing.com','/mooc-ans/mooc2/work/all-task');
assert.equal(blankWork.records.length,0, 'blank work title must fail closed');
const multiTable=parseNativePage(fixtureDocument({table:[
 nativeTable(['课程名称','作业状态'],['高等数学','未提交']),
 nativeTable(['课程名称','作业名称','提交状态'],['工程制图','第三次作业','已交'])
]}), 'mooc1-api.chaoxing.com','/mooc-ans/mooc2/work/all-task');
assert.equal(multiTable.kind,'homework');
assert.equal(multiTable.records.length,1);
assert.equal(multiTable.records[0].title,'第三次作业');
assert.match(multiTable.records[0].details,/已交/);
const explicitWorkHeader=parseNativePage(fixtureDocument({
 table:nativeTable(['课程名称','作业','提交状态'],['工程制图','第三次作业','未交'])
}), 'mooc1-api.chaoxing.com','/mooc-ans/mooc2/work/all-task');
assert.equal(explicitWorkHeader.records[0].title,'第三次作业');
const activityWithPoints=parseNativePage(fixtureDocument({
 table:nativeTable(['活动名称','积分'],['校园公益活动','2'])
}), 'dekt.ncwu.edu.cn','/student/activities');
assert.equal(activityWithPoints.kind,'activity',
 'activity point column is not cumulative credit statement');
const creditRows=parseNativePage(fixtureDocument({
 table:nativeTable(['积分项目','积分'],['实践活动','2'])
}), 'dekt.ncwu.edu.cn','/student/credits');
assert.equal(creditRows.kind,'credits');
assert.equal(parseNativePage(fixtureDocument(),
 'mooc1.chaoxing.com','/mooc2/work/list').records.length,0);

/* Local NCWU import: an actual DOM parsing smoke test, not an official account login. */
const importer = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/LocalQingguoImportActivity.kt');
const localStore = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalScheduleStore.kt');
const script = read('apps/xiaoxu-android/app/src/main/assets/ncwu_qingguo_local.js');
assert.match(manifest, /android:name="\.LocalQingguoImportActivity"[\s\S]*?android:exported="false"/);
assert.match(main, /openLocalImport = /);
assert.match(main, /LocalScheduleStore\.load\(this\)/);
assert.match(schedulePanel, /本机导入/);
assert.match(schedulePanel, /待本人核对/);
assert.match(importer, /https:\/\/jwmis\.ncwu\.edu\.cn\//);
assert.match(importer, /uri\.scheme == "https"/);
assert.match(importer, /setAcceptThirdPartyCookies\(this, false\)/);
assert.match(importer, /MIXED_CONTENT_NEVER_ALLOW/);
assert.match(importer, /onReceivedSslError/);
assert.match(importer, /onReceivedHttpError/);
assert.match(importer, /onReceivedError/);
assert.match(importer, /epoch != pageEpoch/);
assert.match(importer, /isOfficialPage\(currentUrl\)/);
assert.match(importer, /CookieManager.getInstance\(\).flush\(\)/);
assert.match(importer, /browser\.evaluateJavascript\(/);
assert.doesNotMatch(importer + script + localStore, /addJavascriptInterface|document\.cookie|CookieManager\.getCookie|OWNER_LOGIN_SECRET|SCHOOL_SESSION_KEY|http:\/\//);
assert.match(localStore, /getSharedPreferences\(/);
assert.match(localStore, /LocalScheduleStore/);
assert.match(localStore, /JSONArray\(item\.weeks\)/);
assert.match(localStore, /没有找到完整课程，未覆盖旧课表/);

function mockGrid(time) {
    const div = { getAttribute: () => 'padding-bottom: 5px', innerText: '工程制图\n任课教师\n' + time + '\nA101' };
    const cells = Array.from({ length: 7 }, (_, i) => ({
        matches: selector => selector === 'td.td',
        getAttribute: () => null,
        querySelectorAll: () => i === 0 ? [div] : []
    }));
    const row = { querySelector: () => cells[0], children: cells };
    const table = {
        querySelector: () => cells[0],
        querySelectorAll: () => [row]
    };
    return { document: { querySelectorAll: () => [table] }, frames: [] };
}
const officialLike = mockGrid('1-16周[1-2]');
runInNewContext(script, {window: officialLike});
const parsed = officialLike.__xiaoxuExtract();
assert.equal(parsed.items.length, 1);
assert.equal(parsed.items[0].name, '工程制图');
assert.equal(parsed.items[0].day, 1);
assert.equal(parsed.items[0].startSection, 1);
assert.equal(parsed.items[0].weeks.length, 16);
const broken = mockGrid('缺少合法节次');
runInNewContext(script, {window: broken});
assert.match(broken.__xiaoxuExtract().error, /无法识别|不完整/);

function mockHeaderGrid({day = 3, time = '1-16(单)周[3-4]', invalid = false} = {}) {
    const weekNames = ['一', '二', '三', '四', '五', '六', '日'];
    const cell = text => ({
        innerText: text,
        getAttribute: () => null,
        querySelectorAll: () => []
    });
    const header = { cells: [
        cell('节次'), ...weekNames.map(name => cell('星期' + name))
    ] };
    const course = invalid ? '缺少时间信息' :
        '大学英语\n任课教师\n' + time + '\nB201';
    const row = { cells: [
        cell('第一大节'), ...weekNames.map((_, index) =>
            cell(index + 1 === day ? course : ''))
    ] };
    const table = {
        querySelector: () => null,
        querySelectorAll: selector => selector === 'tr' ? [header, row] : []
    };
    return { document: { querySelectorAll: () => [table] }, frames: [] };
}
const header = mockHeaderGrid();
runInNewContext(script, {window: header});
const headerResult = header.__xiaoxuExtract();
assert.equal(headerResult.items.length, 1);
assert.equal(headerResult.items[0].day, 3);
assert.equal(headerResult.items[0].weeks.length, 8);
assert.equal(headerResult.items[0].startSection, 3);
const invalidHeader = mockHeaderGrid({invalid:true});
runInNewContext(script, {window: invalidHeader});
assert.match(invalidHeader.__xiaoxuExtract().error, /未找到|无法识别/);
const friday = mockHeaderGrid({day:5,time:'2-10周[5-6]'});
runInNewContext(script, {window: friday});
assert.equal(friday.__xiaoxuExtract().items[0].day, 5);

console.log('校序 Android V0.6.1：本人课程/作业、只读题目预览、会话与来源边界静态检查通过');
