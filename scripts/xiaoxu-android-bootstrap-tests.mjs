// Static boundary tests for native Android V0.2. Android CI also compiles the real APK.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
const read = p => readFileSync(new URL('../' + p, import.meta.url), 'utf8').replace(/\r\n/g, '\n');

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
assert.match(main, /RealAcademicTaskHub\(localTasks, dao, cloud, openWebsite,/);
assert.match(main, /openLocalSchool = openLocalSchool/);
assert.match(workspace, /openLocalSchool\("chaoxing"\)/);
assert.ok(main.includes('https://dorm-os-dk9.pages.dev/#settings'), 'school connector must deep-link into the existing connection center');
assert.match(workspace, /fun combineWorkspaceTasks\(/);
assert.match(workspace, /fun RealAcademicHome\(/);
assert.match(workspace, /fun RealAcademicTaskHub\(/);
assert.match(workspace, /LocalTaskBoard\(localTasks, dao\)/);
assert.match(workspace, /CloudTasksPanel\(client\)/);
assert.match(workspace, /不能据此推断学校没有作业/);
assert.match(workspace, /按星期匹配/);
assert.match(workspace, /教学周待核实/, '课表区须标注教学周待核实，避免误以为今天必有课');
assert.doesNotMatch(workspace, /WebView|HttpURLConnection|CookieManager/);
assert.doesNotMatch(workspace, /VerifiedLesson\(/);
assert.match(main, /ConnectionsPanel/);
// 消息页由 MessageCenterPanel 承担：公开公告 + 微信待审核计数，两个旧面板已并入。
assert.match(main, /MessageCenterPanel\(cloud, openWebsite, openArticle\)/);
assert.match(panels, /PasswordVisualTransformation/);
assert.match(panels, /fun ConnectionsPanel\(/);
// The owner can begin each school's permitted local read without cloud login
// or first navigating to Xiaoxu's website. Cloud authorization remains optional.
assert.match(panels, /var cloudAdvanced by rememberSaveable \{ mutableStateOf\(false\) \}/);
assert.ok(
    panels.indexOf('LocalSchoolConnectionPanel(openLocalSchool, recentImport)') <
        panels.indexOf('if (cloudAdvanced)'),
    '本机官方页面读取必须先于云端可选授权展示'
);
const nativeSchoolPanel = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/LocalSchoolPanels.kt');
assert.match(nativeSchoolPanel, /在 App 中打开学习通/);
assert.match(nativeSchoolPanel, /在 App 中导入青果课表/);
assert.match(nativeSchoolPanel, /连接第二课堂／刷新积分/);
assert.match(main, /showCloudSecondClass by rememberSaveable \{ mutableStateOf\(false\) \}/);

assert.match(panels, /openLocalSchool\(id\)/);
assert.match(panels, /只有一台手机？打开学习通官网/);
assert.match(panels, /本机打开青果官方课表/);
assert.match(panels, /本机打开第二课堂官网/);
assert.match(panels, /本机官网读取仅保存当前可见记录/);
assert.match(cloud, /val connectionTrusted = state.platforms.firstOrNull/);
assert.match(cloud, /val tasksFresh: Boolean = false/);
assert.match(cloud, /state = state\.copy\(tasksFresh = false, connectionsFresh = false,/);
assert.match(cloud, /tasksFresh = tasks != null/);
assert.match(cloud, /!state\.tasksFresh -> "最新云端任务列表读取失败/);
assert.match(cloud, /tasksFresh = false, connectionsFresh = false,/);
assert.match(panels, /if \(!state\.tasksFresh\)/);
assert.match(panels, /本次任务列表未能核验最新结果/);
assert.match(panels, /client\.syncChaoxingAllCourses\(\)/);
assert.match(panels, /platform\?\.verified == true && !state\.chaoxingBusy/);
assert.match(panels, /一键核验全部课程作业/);
assert.match(panels, /state\.chaoxingBatchStatus\?\.let/);
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
assert.match(cloud, /state = state\.copy\(tasksFresh = false, connectionsFresh = false,/);
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
assert.match(workspace, /ChaoxingHomeworkPanel\(client, openSchoolReader = \{ openLocalSchool\("chaoxing"\) \}\)/);
assert.match(coursePanel, /fun ChaoxingHomeworkPanel\(client: CloudClient, openSchoolReader: \(\) -> Unit\)/);
assert.match(coursePanel, /OutlinedButton\(onClick = openSchoolReader\)/);
assert.match(coursePanel, /在 App 内读取学习通作业/);
assert.doesNotMatch(coursePanel, /前往现有数据连接中心/);
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
assert.match(cloud,/val \(work, verifiedEmpty, progress\) = verifiedChaoxingWork\(response\.json, selected\)/);
assert.match(cloud,/data\.getJSONObject\("sync"\)/);
assert.match(cloud,/saved\.optInt\("received", -1\) != work\.size/);
assert.match(cloud,/chaoxingPageProgress = progress/);
assert.match(cloud,/unverified_homework_total/);
assert.match(coursePanel,/官网本课程进度/);
assert.match(coursePanel,/官网未提供可核实的作业总数/);
assert.match(cloud,/result\.code == 401/);
assert.match(cloud,/if \(consecutiveFailures >= 2\) break/);
assert.match(cloud,/val skipped = courses\.size - checked/);
assert.match(cloud,/chaoxingBatchBusy = false/);
assert.match(coursePanel,/client\.syncChaoxingAllCourses\(\)/);
assert.match(coursePanel,/一键核验全部课程作业/);
assert.match(coursePanel,/state\.chaoxingBatchStatus/);
// App restart must not require two separate owner actions to refresh the
// course list and each verified course's homework. Never poll automatically.
assert.match(cloud,/suspend fun refreshChaoxingCoursesAndHomework\(\)/);
assert.match(cloud,/loadChaoxingCourses\(\)[\s\S]*?syncChaoxingAllCourses\(\)/);
assert.match(cloud,/if \(state\.chaoxingError != null \|\| state\.chaoxingCourses\.isEmpty\(\)\)/);
assert.match(coursePanel,/client\.refreshChaoxingCoursesAndHomework\(\)/);
assert.match(coursePanel,/connection\?\.sessionState == "active"/);
assert.match(coursePanel,/仅读取本人课程列表/);
assert.match(coursePanel,/var showAllHomework by rememberSaveable/);
assert.match(coursePanel,/本次实际读取 \$count 条作业；当前展示 \$visible \/ \$count 条/);
assert.match(coursePanel,/查看剩余/);
assert.match(coursePanel,/state\.chaoxingHomework\.size > 50/);

assert.match(cloud, /interactive_login_required/);
assert.match(cloud, /work_list_incomplete/);
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
const nativeWeekGrid = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/WeeklyTimetableGrid.kt');
assert.match(schedulePanel, /var viewMode by rememberSaveable \{ mutableStateOf\("week"\) \}/);
assert.match(schedulePanel, /Text\("周视图"\)/);
assert.match(schedulePanel, /Text\("日视图"\)/);
assert.match(schedulePanel, /WeeklyTimetableGrid\(weekLessons\)/);
// A single page-owned vertical scroll must move filters, banner and week grid
// together. The timetable may horizontally pan narrow screens, but it must
// not trap vertical gestures inside a weighted inner list.
assert.match(schedulePanel, /Modifier\.fillMaxSize\(\)\.verticalScroll\(pageScroll\)/);
assert.match(schedulePanel, /ScheduleQuickActions\(openFileImport, openLocalImport\)/);
assert.match(schedulePanel, /ScheduleVerifiedContent\([\s\S]*?openFileImport = openFileImport/);
assert.doesNotMatch(schedulePanel, /LazyColumn\(/, 'no vertical nested lazy list in timetable');
assert.doesNotMatch(schedulePanel, /\.weight\(1f\)\s*,?\s*contentPadding = PaddingValues\(/,
  'no weighted vertical scrolling viewport');
assert.match(schedulePanel, /WeeklyTimetableGrid\(weekLessons\)/);
assert.match(schedulePanel, /showWeekPicker by rememberSaveable/);
assert.match(schedulePanel, /val currentIndex = weekNumbers.indexOf\(selectedWeek\)/);
assert.match(schedulePanel, /selectedWeek = weekNumbers\[currentIndex - 1\]/);
assert.match(schedulePanel, /selectedWeek = weekNumbers\[currentIndex \+ 1\]/);
assert.match(schedulePanel, /enabled = currentIndex > 0/);
assert.match(schedulePanel, /enabled = currentIndex < weekNumbers.lastIndex/);
assert.match(schedulePanel, /Text\("上一周"\)/);
assert.match(schedulePanel, /Text\("下一周"\)/);
assert.match(schedulePanel, /Modifier.heightIn\(min = 48.dp\)/);

assert.match(schedulePanel, /data\.lessons\.filter \{ selectedWeek in it\.weeks \}/);
assert.match(schedulePanel, /并非自动判断的当前教学周/);
assert.match(nativeWeekGrid, /lessons: List<VerifiedLesson>/);
assert.match(nativeWeekGrid, /section in it\.startSection\.\.it\.endSection/);
assert.match(nativeWeekGrid, /for \(day in 1\.\.7\)/);
assert.match(nativeWeekGrid, /onLessonClick\(first\)/);
assert.match(nativeWeekGrid, /BoxWithConstraints\(Modifier\.fillMaxWidth\(\)\)/,
  'seven-day grid must calculate responsive phone widths');
assert.match(nativeWeekGrid, /\(\(maxWidth - labelWidth - 8\.dp\) \/ 7\)\.coerceAtLeast\(44\.dp\)/,
  'week grid should fit all seven days when phone width allows');
assert.match(nativeWeekGrid, /if \(next != available\) break/,
  'only contiguous sections with identical class sets may merge into one block');
assert.match(nativeWeekGrid, /rowHeight \* span/);

assert.match(nativeWeekGrid, /overlapChoices = available/);
assert.doesNotMatch(nativeWeekGrid, /电路基础|E103|演示数据|faker/i);

// Issue #215 P0 补齐：平板表头选日 + 周/日 Tab 的无障碍语义（只增断言）。
assert.match(nativeWeekGrid, /selectedWeekday: Int\? = null/,
  '网格表头选日须是可选能力，手机单栏行为不变');
assert.match(nativeWeekGrid, /onSelectWeekday: \(\(Int\) -> Unit\)\? = null/);
assert.match(nativeWeekGrid, /Modifier\.selectableGroup\(\)/, '网格表头选日须成组');
assert.match(nativeWeekGrid, /role = Role\.Tab/, '网格表头选日须是 Tab 语义');
assert.match(nativeWeekGrid, /height\(48\.dp\)/, '网格表头触控高度须 ≥48dp');
assert.match(nativeWeekGrid, /，当前选中/, '网格表头须播报选中状态');
assert.match(schedulePanel, /private fun ScheduleViewModeTabs\(/);
assert.match(schedulePanel, /\.selectableGroup\(\)/, '周/日两个 Tab 须有分组语义');
assert.match(schedulePanel, /\.selectable\(selected = isSelected, role = Role\.Tab, onClick = onClick\)/,
  'Tab 须用 selectable 提供 role 与 selected');
assert.match(schedulePanel, /周视图，七日历课程网格/, '周视图 Tab 须有准确描述');
assert.match(schedulePanel, /日视图，单日课程列表/, '日视图 Tab 须有准确描述');
assert.match(schedulePanel, /private fun DayLessonList\(/,
  '当日列表须共用一份实现，手机与平板一致');
assert.match(schedulePanel, /selectedWeekday = tabletWeekday,/, '平板网格表头选日须驱动右侧当日列表');
assert.match(schedulePanel, /dayLessons = tabletDayLessons,/, '右侧须显示所选那天的真实课块');
assert.match(schedulePanel, /filtered\.filter \{ it\.weekday == tabletWeekday \}/,
  '右侧课块只能取自当前已筛选的真实课程');

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
assert.match(schedulePanel, /ScheduleNotConnectedState\(openLocalImport, openProfile\)/);
assert.match(schedulePanel, /ScheduleSourceMissingState\(openLocalImport, refresh\)/);
assert.match(schedulePanel, /ScheduleUnverifiedState\(openLocalImport, refresh\)/);
assert.match(schedulePanel, /ScheduleFailedState\(current\.message, openLocalImport, refresh\)/);
assert.match(schedulePanel, /ScheduleEmptyState\(openLocalImport, refresh\)/);
assert.match(schedulePanel, /Text\("在 App 内导入青果课表"\)/);
assert.match(schedulePanel, /Text\("在 App 内核对 \/ 更新青果课表"\)/);
assert.doesNotMatch(schedulePanel, /去「我的」连接校序云端|打开官方连接中心|查看官方连接状态或重新授权/);


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

// 消息中心来源分区（Issue #263）：官方公告与微信候选必须各有分区标题与真实计数，
// 顶部不能再用单一「官方来源」徽标覆盖整页。
assert.match(messageCenter, /fun pendingWechatShortLabel\(/);
assert.match(messageCenter, /noticeTotal == 0 -> "暂无已保存公告"/, "没有读取证据时不得冒充本次成功读到零条");
assert.match(messageCenter, /暂无已保存的官方公告/, "零条状态需区分尚未读取与官方确认空列表");
assert.match(messageCenter, /private fun SourceSectionHeader\(/, '需要独立来源分区标题');
assert.match(messageCenter, /title = "华水官方公告"/);
assert.match(messageCenter, /title = "微信候选"/);
assert.match(messageCenter, /badge = "本人云端"/);
assert.equal((messageCenter.match(/SourceSectionHeader\(/g) || []).length >= 3, true,
  '两个分区都要渲染分区标题');
assert.doesNotMatch(messageCenter, /text = "官方来源"/, '顶部不得用单一官方徽标覆盖含微信候选的页面');
assert.match(messageCenter, /SourceChip\(/);
assert.match(messageCenter, /label = "华水官方公告"/);

// 公告只有标题/分类/日期/链接：入口与抽屉都不得声称"全文"或提供正文/附件。
assert.match(messageCenter, /onClickLabel = "查看这条公告的详情与官网链接"/,
  '紧凑海报式公告卡仍须打开真实官方详情');
assert.doesNotMatch(messageCenter, /查看全文/, '公告详情不得声称查看全文');
assert.match(messageCenter, /onClickLabel = "查看这条公告的详情与官网链接"/);
assert.match(messageCenter, /sheetState = sheetState/);
assert.match(messageCenter, /App 内不保存正文与附件，需通过学校官网查看/);
assert.match(messageCenter, /不生成摘要/);
assert.doesNotMatch(messageCenter, /全部已读|提取为任务|标记为已读/, '不得展示未实现的批量操作');

// 搜索范围只覆盖官方公告，微信候选只用已有审核面板呈现。
assert.match(messageCenter, /WechatReviewPanel\(/);
assert.match(messageCenter, /pendingCount = state\.pendingWechatCount/);

// 触控与读屏：筛选 48dp、单选组 + selected、窄屏内边距与可滚动详情。
assert.match(messageCenter, /\.selectableGroup\(\)/);
assert.match(messageCenter, /\.selectable\(\s*selected = isSelected,\s*role = Role\.Tab,/);
assert.match(messageCenter, /\.heightIn\(min = 48\.dp\)/);
assert.match(messageCenter, /maxWidth < 380\.dp/);
assert.match(messageCenter, /verticalScroll\(rememberScrollState\(\)\)/);
assert.match(messageCenter, /mergeDescendants = true/);
assert.match(messageCenter, /rememberSaveable \{ mutableStateOf\(MSG_FILTER_ALL\) \}/);
assert.match(messageCenter, /var openedId by rememberSaveable/);

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
assert.match(wechatReview, /候选摘要 · 请核对原消息后确认/);
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
assert.match(main, /if \(localCaptureReturn != null\) \{/);
assert.match(main, /if \(localCaptureReturn\.provider == "chaoxing"\) Page\.TASKS/);
assert.match(main, /else \{ academic = AcademicDestination\.SECOND_CLASS; Page\.ACADEMIC \}/);
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
assert.match(workspace, /LocalSchoolRecordSection\(setOf\("course"\)\)/);
assert.match(main, /localLessons = localLessons,/);
assert.match(workspace, /val timetable = if \(localLessons\.isNotEmpty\(\)\) localLessons else state\.lessons/);
assert.match(workspace, /val nativeRows by nativeDao\.observe\(\)\.collectAsState\(initial = emptyList\(\)\)/);
assert.match(workspace, /it\.provider == "chaoxing" && it\.kind == "homework"/);
assert.match(workspace, /it\.provider == "second_class" && it\.kind in setOf\("activity", "credits"\)/);
assert.match(workspace, /学习通 · 本机作业快照/);
assert.match(workspace, /不纳入待完成\/已完成数量/);

// Issue #261：任务中心分行分区 + 来源徽标（纯 UI 增量）。
assert.match(workspace, /private fun TaskSectionHeader\(/, '任务列表须有分区标题');
// 任务中心新版必须直接改真实 Compose 页面，不引入假数据与第二套消息审核。
assert.match(workspace, /internal fun searchWorkspaceTasks\(/, '应支持对真实本机与云端记录搜索');
assert.match(workspace, /val visible = searchWorkspaceTasks\(all, filter, searchQuery, today\)/,
  '页面实际列表必须使用搜索结果');
assert.match(workspace, /if \(showSchoolRecords\) \{/,
  '本机学习通快照应可收纳，避免默认铺满任务页');
assert.ok(workspace.indexOf('title = "课程相关与云端任务"') <
    workspace.indexOf('title = "我的本机待办"'),
  '默认任务列表优先展示课程相关与云端任务');
assert.match(workspace, /WechatReviewPanel\(/, '任务中心必须复用原生微信人工审核');
assert.doesNotMatch(workspace, /PosterDemo/, '真实任务中心不可嵌入海报版演示');

assert.match(workspace, /private fun TaskRow\(/, '任务行须是独立展示组件');
assert.match(workspace, /private fun SnapshotRow\(/, '学习通快照行须是独立展示组件');
assert.match(workspace, /本机本人待办/, '本机待办须有独立分区');
assert.match(workspace, /校序云端任务/, '云端任务须有独立分区');
assert.match(workspace, /本机页面快照/, '快照须标注来源徽标');
assert.match(workspace, /含上次云端快照/,
  '上次云端任务必须明确标注快照，不可冒充新读取');
assert.match(workspace, /截止未核验/, '云端缺日期须显示未核验，不得猜测');
assert.match(workspace, /截止未记录/, '本机缺日期须显示未记录');
assert.match(workspace, /DueUi\(prefix \+ "已逾期/, '云端过期日期必须保留待核对前缀');
assert.match(workspace, /shown\.size \+ " \/ " \+ visible\.size/, '80 条展示上限必须告知实际筛选总数');
assert.match(workspace, /heightIn\(min = 64\.dp\)/, '任务行触控区域须 ≥48dp');
assert.match(workspace, /contentDescription = task\.title \+ "；来源："/,
  '任务行须有完整读屏描述');
assert.doesNotMatch(workspace, /Log\.[diwev]\(|println\(/, '任务中心不得打印日志');
assert.match(workspace, /采集时间/);
assert.match(workspace, /本机导入 · 待核对/);
assert.doesNotMatch(workspace, /add\(WorkspaceTask\("school:"|CloudTask\(.*nativeRows/);
assert.match(panels, /LocalSchoolConnectionPanel\(openLocalSchool, recentImport\)/);
assert.ok(panels.indexOf('LocalSchoolConnectionPanel(openLocalSchool, recentImport)') < panels.indexOf('if (cloudAdvanced)'), '本机记录入口始终可见，无须先展开云端或等待导入');
assert.match(localSchoolUi, /data class LocalSchoolImportReceipt\(/);
assert.match(localSchoolUi, /records\.filter \{ it\.provider == provider \}/);
assert.match(localSchoolUi, /stored\.size == justImported\.savedCount/);
assert.match(localSchoolUi, /已从本机数据库重新读取/);
assert.match(localSchoolUi, /查看本机全部/);
assert.match(localSchoolUi, /val prioritized = if \(justImported == null\) list else/);
assert.match(localSchoolUi, /item\.capturedAt/);
assert.match(localSchoolUi, /不代表当前官网状态/);
assert.match(localNative, /EXTRA_RETURN_KIND/);
assert.match(localNative, /EXTRA_SAVED_COUNT/);
assert.match(localNative, /setResult\(Activity\.RESULT_OK, Intent\(\)/);
assert.match(localNative, /savedKind = kind/);
assert.match(localNative, /savedCount = total/);
assert.match(localNative, /dao\.appendKind\(provider, kind, accepted\)/);
assert.match(main, /if \(permitted && count in 1\.\.500\)/);
assert.match(nativeRecords, /@Transaction\s+suspend fun appendKind\(/);
assert.match(nativeRecords, /require\(keys\.size <= 500\)/);
assert.match(workspace, /items\(count = minOf\(capturedHomework\.size, 500\)/);
assert.match(localNative, /\.replaceKind\(provider, kind, accepted\)/);
assert.doesNotMatch(main + localSchoolUi, /ownerCookie|schoolPassword|CAS_TICKET/);
for (const host of ['i.chaoxing.com','dekt.ncwu.edu.cn']) assert.ok(localNative.includes(host));
assert.doesNotMatch(localNative, /"ncwu" to "https:/);
assert.match(localNative, /finishAndReturn = Button\(this\)/);
assert.match(localNative, /finishAndReturn\.isEnabled = true/);
assert.match(localNative, /if \(!navigationAllowed\(provider, url\)\)/);
assert.match(localNative, /pageFailed = true/);
assert.match(localNative, /onReceivedSslError/);
// NCWU's published Chaoxing university SSO must not be rejected by the
// app's single-phone WebView allowlist; other CAS targets stay forbidden.
assert.match(localNative, /CHAOXING_SCHOOL_SERVICE = "https:\/\/fysso\.chaoxing\.com\/cassso\/ncwu"/);
assert.match(localNative, /"authserver\.ncwu\.edu\.cn" ->[\s\S]*?uri\.path == "\/authserver\/login"/);
assert.match(localNative, /uri\.getQueryParameters\("service"\)\.size == 1/);
assert.match(localNative, /uri\.getQueryParameter\("service"\) == CHAOXING_SCHOOL_SERVICE/);
assert.match(localNative, /"fysso\.chaoxing\.com" ->/);
assert.match(localNative, /uri\.path in setOf\("\/cassso\/ncwu", "\/cassso\/ncwu\/"\)/);
assert.match(localNative, /"华水统一身份认证（学习通）"/);
assert.match(localNative, /browser\.loadUrl\(CHAOXING_SCHOOL_SSO\)/);
// Never let CAS pages or network failures become an extractable work list.
assert.match(localNative, /"chaoxing" ->[\s\S]*?"mooc1-api\.chaoxing\.com"/);
assert.match(localNative, /override fun onReceivedError\(/);
assert.match(localNative, /if \(request\.isForMainFrame\) \{\s*invalidateCapture\(\)\s*pageFailed = true/);
assert.match(localNative, /private fun invalidateCapture\(\) \{\s*captureEpoch\+\+\s*scoreSaveJob\?\.cancel\(\)/);
assert.match(localNative, /scoreSaveJob = lifecycleScope\.launch/);
assert.match(localNative, /currentCoroutineContext\(\)\.ensureActive\(\)/);
assert.match(localNative, /extract\.isEnabled = loaded && !pageFailed &&/);
assert.match(localNative, /extractionAllowed\(provider, browser\.url\)/);
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
assert.match(appGradle, /versionName '0\.8\.4'/);

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
assert.match(importer, /val previous = LocalScheduleStore\.load\(this\)\.size/);
assert.match(importer, /val fewer = previous > 0 && count < previous/);
assert.match(importer, /本次课块数量比原本少/);
assert.match(importer, /已核对，仍要替换/);
assert.match(importer, /保留旧课表/);
assert.match(importer, /val saved = LocalScheduleStore\.save\(this, items\.toString\(\)\)/);

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
// A valid official seven-day td.td grid may render course text without the
// old inline 'padding-bottom:5px' style. Preserve explicit weekday geometry.
function mockPlainClassGrid({time = '1-16周[1-2]', malformed = false, extraCourse = false} = {}) {
    const first = '工程制图\n任课教师\n' + time + '\nA101';
    const second = '高等数学\n任课教师\n1-16周[3-4]\nA102';
    const text = first + (extraCourse ? '\n' + second : '') +
        (malformed ? '\n课表多余字段' : '');
    const cells = Array.from({length: 7}, (_, i) => ({
        matches: selector => selector === 'td.td',
        getAttribute: () => null,
        querySelectorAll: () => [],
        innerText: i === 1 ? text : ''
    }));
    const row = {querySelector: () => cells[0], children: cells};
    const table = {querySelector: () => cells[0], querySelectorAll: () => [row]};
    return {document: {querySelectorAll: () => [table]}, frames: []};
}
const plainClass = mockPlainClassGrid();
runInNewContext(script, {window: plainClass});
assert.equal(plainClass.__xiaoxuExtract().items[0].day, 2);
assert.equal(plainClass.__xiaoxuExtract().items[0].name, '工程制图');
const plainTwo = mockPlainClassGrid({extraCourse:true});
runInNewContext(script, {window: plainTwo});
assert.equal(plainTwo.__xiaoxuExtract().items.length, 2,
 '同一日两个完整文本课块不能被漏读');
const plainInvalid = mockPlainClassGrid({malformed:true});
runInNewContext(script, {window: plainInvalid});
assert.match(plainInvalid.__xiaoxuExtract().error, /不完整/,
 '字段数量异常必须失败，不能只导入可识别子集');

// Issue #268 第一阶段：统一设计系统 + 五页 Shell + 首页样板（纯 UI，不改数据来源）。
const design = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/XiaoxuDesignSystem.kt');
assert.match(design, /object XiaoxuPalette/, '设计系统须集中定义调色板');
assert.match(design, /val Brand = Color\(0xFF2F6BFF\)/, '主色须为 #2F6BFF');
assert.match(design, /val BrandDeep = Color\(0xFF1B47C8\)/, '深蓝须为 #1B47C8');
assert.match(design, /val Bg = Color\(0xFFF4F7FD\)/, '背景须为 #F4F7FD');
assert.match(design, /val card: Dp = 18\.dp/, '基础卡圆角须为 18dp');
assert.match(design, /val hero: Dp = 22\.dp/, '重要卡圆角须为 22dp');
assert.match(design, /val XiaoxuCourseColors/, '课程六色须集中定义');
assert.match(design, /fun xiaoxuCourseColor\(key: String\)/, '课程配色须按稳定 key 取模');
assert.match(design, /fun XiaoxuTheme\(/, '须有统一主题入口');
assert.match(design, /fun XiaoxuCard\(/, '须有通用圆角卡');
assert.match(design, /fun XiaoxuHero\(/, '须有渐变主卡');
assert.match(design, /fun MetricCard\(/, '须有统计卡');
assert.match(design, /fun CourseTile\(/, '须有课程色块');
assert.match(design, /fun SectionHeader\(/, '须有分区标题');
assert.match(design, /fun SourceBadge\(/, '须有来源徽标');
assert.match(design, /fun StatusChip\(/, '须有状态标签');
assert.match(design, /fun XiaoxuSheet\(/, '须有统一底部抽屉');
assert.match(design, /fun XiaoxuLoading\(/, '须有统一加载态');
assert.match(design, /fun XiaoxuEmpty\(/, '须有统一空态');
assert.match(design, /fun XiaoxuError\(/, '须有统一错误态');
assert.match(design, /fun XiaoxuUnverified\(/, '须有统一未核验态');
assert.match(design, /fun XiaoxuTopBar\(/, '须有统一顶栏');
assert.match(design, /fun QuickEntry\(/, '快捷入口须是共享组件');
assert.doesNotMatch(design, /WebView|HttpURLConnection|CookieManager/,
  '设计系统只画界面，不得触碰网络');
assert.doesNotMatch(design, /import io\.github\.x07xh\.xiaoxu\.data\./,
  '设计系统不得依赖数据层');
assert.match(main, /XiaoxuTheme \{/, 'Shell 须使用统一主题');
assert.match(main, /XiaoxuTopBar\(/, 'Shell 须使用统一顶栏');
assert.match(main, /AnimatedContent\(/, '页面切换须有轻量过渡');
assert.match(main, /NavigationBarItemDefaults\.colors/, '底部导航选中态须统一配色');
assert.match(main, /NavigationRailItemDefaults\.colors/, '平板侧栏选中态须统一配色');
assert.match(workspace, /private fun HomeGreeting\(/, '首页须有日期与问候区');
assert.match(workspace, /private fun HomeHero\(/, '首页须有 Hero 主卡');
assert.match(workspace, /private fun HomeTaskRow\(/, '首页待办行须复用任务状态判定');
assert.match(workspace, /QuickEntry\("课表"/, '快捷入口只放已接入页面');
assert.match(workspace, /remember\(state\) \{ deriveScheduleUiState\(state\) \}/,
  '首页课表结论须复用课表页判定');
assert.match(workspace, /按星期匹配/);
assert.match(workspace, /教学周待核实/, '课表区须标注教学周待核实');
assert.match(workspace, /本人标记完成/, '统计口径须写清来源');
assert.match(workspace, /XiaoxuTone.Neutral/, '无可信数据时 Hero 须转为未核验配色');
assert.doesNotMatch(workspace, /下一节课/, '没有节次作息不得声称"下一节课"');
assert.doesNotMatch(workspace, /Log\.[diwev]\(|println\(/, '首页不得打印日志');


// PR #269 review 修复：stale 不得算已核验 / 无教学周不得称今日实际课程 / 统计须说明云端快照。
assert.match(workspace, /val verified = state as\? ScheduleUiState\.Verified/,
  '首页须读取课表四态里的 stale 与刷新提示');
assert.match(workspace, /val stale = verified\?\.stale == true/, 'stale 必须单独判定');
assert.match(workspace, /stale -> "待刷新"/, 'stale 课表徽标须为待刷新，不得写已核验');
assert.match(workspace, /本日课表 · 上次读取/, 'stale 文案须说明是上次读取');
assert.match(workspace, /refreshWarning/, '刷新失败提示须展示');
assert.doesNotMatch(workspace, /今日课程|今日安排/,
  '未知教学周时不得把按星期筛选的记录称为今日课程/今日安排');
assert.match(workspace, /本日课表/, '课表区须按本日课表描述');
assert.match(workspace, /按星期匹配/,
  '课表按星期匹配，不宣称当天实际开课');
assert.match(workspace, /教学周待核实/,
  '课表区须标注教学周待核实，避免把按星期匹配误读为今天确有课');
assert.match(workspace, /formatWeeks\(it\.weeks\)/, '须展示课程适用周次');
assert.match(workspace, /请以学校官网为准/, '无该星期记录时须以官网为准');
assert.match(workspace, /val cloudSnapshotRows = state\.tasks\.size/, '统计须知道云端快照条数');
assert.match(workspace, /本机 \+ 云端本次读取/, '刷新成功须标注本次读取');
assert.match(workspace, /含上次云端快照 /, '未刷新时须披露统计包含上次云端快照');
assert.match(workspace, /本人标记完成 · 含云端快照/, '已完成口径须同步披露快照');

// PR #269 第二轮 review：课表新鲜度只认 scheduleFresh，不得用聚合 privateError 冒充课表提示。
assert.match(schedulePanel, /val keptFromLastRead = !state\.scheduleFresh/,
  '课表四态须以 scheduleFresh 判定是否为本次读取');
assert.match(schedulePanel, /stale = keptFromLastRead \|\| state\.scheduleStatus == "stale"/,
  '上次保留的数据与服务端 stale 都必须标记 stale');
assert.match(schedulePanel, /refreshing = keptFromLastRead && state\.busy/,
  '刷新中须可区分');
assert.match(schedulePanel, /refreshing = refreshing/, 'refreshing 须传入 UI 状态');
assert.match(schedulePanel, /refreshWarning = if \(keptFromLastRead && !state\.busy\)/,
  '刷新失败才给课表专属提示');
assert.doesNotMatch(schedulePanel, /refreshWarning = state\.privateError/,
  '聚合 privateError 不得作为课表刷新提示');
assert.doesNotMatch(schedulePanel, /Failed\(state\.privateError\)/,
  '无课程时也不得把聚合错误当作课表错误');
assert.match(schedulePanel, /fun localImportedScheduleState\(/, '本机导入课表状态须独立可测');
assert.match(workspace, /refreshing -> "刷新中"/, '首页须显示刷新中徽标');
assert.match(workspace, /本日课表 · 正在刷新/, '刷新中须说明暂显示上次读取');
assert.match(workspace, /上次读取 · 待刷新核对/, '刷新失败须标注待刷新核对');
const freshnessTest = read('apps/xiaoxu-android/app/src/test/java/io/github/x07xh/xiaoxu/ui/ScheduleFreshnessTest.kt');
assert.match(freshnessTest, /class ScheduleFreshnessTest/);
assert.match(freshnessTest, /readyWithPreviousLessonsAndFailedScheduleReadIsStale/);
assert.match(freshnessTest, /readyWithPreviousLessonsWhileRefreshingShowsRefreshing/);
assert.match(freshnessTest, /serverMarkedStaleStaysStaleEvenWhenReadSucceeded/);
assert.match(freshnessTest, /freshSuccessfulReadIsVerifiedForThisRead/);
assert.match(freshnessTest, /taskOrWechatFailureDoesNotTaintSuccessfulScheduleRead/);
assert.match(freshnessTest, /localImportedTimetableIsIndependentFromCloudFreshness/);

// Issue #268 P1 课表视觉统一：复用 DesignSystem，保留真正课表网格与来源状态。
const scheduleVisual = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleScreen.kt');
assert.match(scheduleVisual, /XiaoxuCard\(/, '课表状态与头部共用白卡组件');
assert.match(scheduleVisual, /SourceBadge\(text = badgeLabel, tone = badgeTone\)/,
    '课表来源使用统一标签而非自建不同样式');
assert.match(scheduleVisual, /stale -> "上次读取 · 待刷新"/,
    '旧课表必须明确待刷新');
assert.match(scheduleVisual, /refreshing -> "刷新中 · 上次课表"/,
    '刷新中不得将保留课表当作本次已核验');
assert.match(scheduleVisual, /localImport -> "本机导入 · 待核对"/,
    '本机导入不能冒充官方新鲜记录');
assert.match(scheduleVisual, /colorKey = id\.ifBlank \{ title \}/,
    '课程六色使用与首页一致的稳定课程标识');
assert.match(scheduleVisual, /xiaoxuCourseColor\(lesson\.colorKey\)/,
    '课表课程卡统一使用设计系统课程配色');
assert.match(scheduleVisual, /WeeklyTimetableGrid\(/,
    '不得删除已有七日网格');
assert.match(scheduleVisual, /WeekSelector\(/,
    '不得删除按真实周次筛选');
assert.match(scheduleVisual, /ModalBottomSheet\(/,
    '不得删除既有课程详情抽屉');


// Issue #268 P1：三页复用 #269 统一组件，不得悄悄改动本人数据/审核动作。
// 这里保护关键视觉结构与操作入口；真实布局仍以 Android APK/截图为最终验收。
assert.match(workspace, /MetricCard\(\s*"待完成"/, '任务统计使用首页共享数字卡');
assert.match(workspace, /MetricCard\(\s*"本周截止"/, '任务本周截止卡保持独立');
assert.match(workspace, /MetricCard\(\s*"已完成"/, '任务已完成卡保持独立');
assert.ok(workspace.includes('if (showLocal) item { LocalTaskBoard(localTasks, dao) }'),
  '本机任务仍用实际 Room 管理器，非假按钮');
assert.ok(workspace.includes('TaskRow(localRows[i], today, onOpen = { openedTaskKey = localRows[i].key })'),
  '本机任务仍从真实 Row 数据打开详情');
assert.ok(workspace.includes('TaskRow(cloudRows[i], today, onOpen = { openedTaskKey = cloudRows[i].key })'),
  '云端任务仍从真实 Row 数据打开详情且保留来源隔离');
assert.ok(workspace.includes('含上次云端快照'),
  '未核验云端数据不得被统计描述为最新');
assert.ok(workspace.includes('SourceBadge(text, tone = if (emphasized)'),
  '任务来源必须复用共享来源标签');
assert.ok(workspace.includes('WechatReviewPanel('),
  '任务中心只能复用既有原生微信审核组件，禁止重复实现候选审核器');
assert.ok(workspace.includes('showCount = state.pendingWechatCount != null'),
  '未知审核数量不得在任务页显示为 0 条');
assert.match(workspace, /var openedTaskKey by rememberSaveable/, '任务详情选中状态应可保存');
assert.match(workspace, /val selectedTask = all\.firstOrNull/, '任务详情仅从已有真实记录回查');
assert.match(workspace, /XiaoxuSheet\(onDismiss = \{ openedTaskKey = null \}, title = "任务详情"\)/,
  '任务详情必须使用共享原生底部抽屉');
assert.match(workspace, /clickable\(onClickLabel = "查看任务详情", onClick = onOpen\)/,
  '任务卡片必须可以真实点击');
assert.match(workspace, /task\.storage == "cloud"/, '任务来源必须区分云端与本机');
assert.match(workspace, /上次云端快照 · 待核对/,
  '旧云端任务不得伪装成刚同步');
assert.match(workspace, /本机记录 · 离线保存/,
  '任务详情须标注本机来源');
assert.match(workspace, /if \(selectedTask\.storage == "local"\) \{\s*showLocal = true\s*jumpTo = "local"/,
  '本机任务详情应展开本机管理并定位至真实表单');
assert.match(workspace, /else \{\s*showCloud = true\s*jumpTo = "cloud"/,
  '云端任务详情应展开云端管理并定位至真实表单');
assert.match(messageCenter, /modifier = Modifier\.fillMaxWidth\(\)\.padding\(top = 4\.dp, bottom = 2\.dp\)/,
  '消息页按海报式独立标题布局，不再包一层大卡');
assert.ok(messageCenter.includes('SourceBadge(\n            text = badge'),
  '官方公告与微信候选使用共享来源徽标');
assert.match(messageCenter, /WechatReviewPanel\(/,
  '保留原生微信候选逐条审核');
assert.match(messageCenter, /NoticeDetailSheet\(/,
  '保留公告真实详情及官方链接');
assert.ok(panels.includes('XiaoxuCard {'),
  '个人中心和学校连接卡采用共享样式');
assert.ok(panels.includes('title = "学校数据连接"'),
  '个人中心保留本机学校官方读取入口');
assert.ok(panels.includes('LocalSchoolConnectionPanel(openLocalSchool, recentImport)'),
  '不得移除原生学校读取入口');
assert.ok(panels.includes('if (cloudAdvanced) {'),
  '云端授权仍是可展开的独立可选入口');

// Issue #268 P2：任务统计卡可点击筛选，详情跳转到真实管理区，窄屏来源标题分行。
assert.match(workspace, /val taskListState = rememberLazyListState\(\)/,
  '任务中心必须保留独立可滚动列表状态');
assert.match(workspace, /taskListState\.animateScrollToItem\(targetIndex\)/,
  '任务跳转须滚到真实管理入口，不能只是展开不可见区域');
assert.match(workspace, /"cloud" -> if \(showCloud\) 5 \+ \(if \(showLocal\) 1 else 0\)/,
  '云端管理滚动下标须考虑本机管理展开与否');
assert.match(workspace, /"filters" -> 5 \+ \(if \(showLocal\) 1 else 0\) \+ \(if \(showCloud\) 1 else 0\)/,
  '筛选列表跳转下标须考虑两个管理区状态');
assert.match(workspace, /caption = if \(filter == "week"\) "当前筛选" else "点击查看"/,
  '本周截止统计卡应体现当前筛选并能点击');
assert.match(workspace, /filter = "pending"[\s\S]*?jumpTo = "filters"/,
  '待完成统计卡应真实切换过滤器');
assert.match(workspace, /filter = "done"[\s\S]*?jumpTo = "filters"/,
  '已完成统计卡应真实切换过滤器');
assert.match(workspace, /jumpTo = "local"/,
  '本机任务按钮应自动定位管理区域');
assert.match(workspace, /jumpTo = "cloud"/,
  '云端任务按钮应自动定位管理区域');
assert.match(workspace, /maxLines = 2,[\s\S]*?modifier = Modifier\.weight\(1f\)\.semantics \{ heading\(\) \}/,
  '窄屏任务分区标题必须给来源和条数预留空间');
// Issue #275：消息中心与「我的」按七张海报/四屏 HTML 精细化，只换视觉层级，
// 真实来源、审核事务与数据边界一律不动。
assert.match(messageCenter, /MESSAGE CENTER/, '消息页头部需复刻海报的英文小标');
assert.match(messageCenter, /messageFilterIcon\(/, '四枚筛选 pill 需带图标');
assert.match(messageCenter, /TextFieldDefaults\.colors\(/, '搜索条改为无边框填充样式');
assert.match(messageCenter, /fun MessageSearchField\(/, '搜索条抽成独立组件');
assert.match(messageCenter, /modifier = Modifier\.weight\(1f\)/, '双来源信息卡并排占位');
assert.match(messageCenter, /size\(42\.dp\)/, '公告卡使用 42dp 图标块');
assert.match(messageCenter, /fun SourceChip\(\n/, '来源信息卡为私有独立组件');
assert.match(messageCenter, /heading\(\)/, '消息页标题需有标题语义');
assert.doesNotMatch(messageCenter, /AI 摘要|智能整理|未读红点/,
  '海报里的智能摘要与未读红点不能写进真实消息页');

assert.match(panels, /fun ProfileHeroCard\(/, '我的页需头像视觉区');
assert.match(panels, /fun ProfileMetric\(/, '我的页真实指标格');
assert.match(panels, /fun SettingRow\(/, '我的页分组设置行');
assert.match(panels, /fun PlatformStatusCard\(/, '三个学校平台需真实状态卡');
assert.match(panels, /通用头像（未接入真实头像）/, '没有真实头像时只放通用图标');
assert.match(panels, /maxWidth >= 600\.dp/, '我的页平板双列');
assert.match(panels, /count \{ it\.sessionState == "active" \}/, '平台计数只数真实 active 会话');
assert.match(panels, /"（快照）"/, '未刷新的云端任务必须标注快照');
assert.doesNotMatch(panels, /示例学号|演示头像|张三|同学您好/,
  '不得用演示身份冒充本人账号');

assert.match(wechatReview, /fun WechatCandidateRow\(/, '候选卡抽出独立行组件');
assert.match(wechatReview, /contentDescription = wechatKindLabel/, '候选行需读屏说明');
assert.doesNotMatch(wechatReview, /未读|群成员|已读状态/,
  '不得伪造群成员数或已读状态');

// PR #281 review 修复：微信待审核数量必须区分「本次成功读取」与「缓存快照」。
// wechatError != null 表示本次候选读取失败，pendingWechatCount 是上次保留的旧值，不能冒充最新。
assert.match(messageCenter,
  /fun pendingWechatLabel\(count: Int\?, authenticated: Boolean, wechatError: String\? = null\)/,
  '长文案须接收 wechatError 以区分快照');
assert.match(messageCenter,
  /fun pendingWechatShortLabel\(count: Int\?, authenticated: Boolean, wechatError: String\? = null\)/,
  '短文案须接收 wechatError 以区分快照');
assert.match(messageCenter, /wechatError != null -> "\$count（快照）"/,
  '读取失败但保留旧数量时短文案须标（快照）');
assert.match(messageCenter, /wechatError != null -> "待审核候选 \$count 条（快照，本次读取失败）"/,
  '长文案须说明是本次读取失败的快照');
assert.match(messageCenter, /count == null -> "数量未读取"/,
  '数量为空时不得伪造为 0');
assert.match(messageCenter, /pendingWechatShortLabel\(pendingCount, authenticated, wechatError\)/,
  '来源信息卡须把 wechatError 传给短文案');
assert.match(messageCenter, /pendingWechatShortLabel\(\n\s*state\.pendingWechatCount,\n\s*state\.authenticated,\n\s*state\.wechatError\n\s*\)/,
  '微信候选分区头须把 wechatError 传给短文案');
assert.match(wechatReview, /pendingWechatLabel\(pendingCount, state\.authenticated, state\.wechatError\)/,
  '微信候选卡头部须把 wechatError 传给长文案');
assert.match(panels, /state\.wechatError != null -> state\.pendingWechatCount\.toString\(\) \+ "（快照）"/,
  '我的页待审核指标须在读取失败时标快照');
assert.match(panels, /state\.pendingWechatCount == null -> "未读取"/,
  '我的页数量为空须显示未读取，不得伪造 0');

// #286 修复：只能使用一套真实数据界面，移除 PosterDemo 与空操作。
assert.doesNotMatch(main, /posterMode|onPosterMode|PosterHome|PosterTasks|PosterMessages|PosterSchedule/,
  '主导航不得重新出现演示版/真实版切换');
assert.match(main, /PrimaryTabs = listOf\(Page\.HOME, Page\.SCHEDULE, Page\.TASKS, Page\.MESSAGES, Page\.PROFILE\)/,
  '主导航应为五个真实页面');
assert.match(main, /openAcademic = \{ current = Page\.ACADEMIC \}/,
  '教务须保留可到达的原生入口');
assert.match(main, /MessageCenterPanel\(cloud, openWebsite, openArticle, useStatusBarInset = true\)/,
  '真实消息中心应独立绘制海报式头部并应用正确安全区');
assert.match(messageCenter, /WechatReviewPanel\(/,
  '真实消息布局不得绕过微信审核事务');
assert.doesNotMatch(messageCenter, /PosterDemo|模拟群聊|高数练习要求与参考\.pdf/,
  '真实消息中心不得展示海报演示消息和附件');

console.log('校序 Android V0.6.1：本人课程/作业、只读题目预览、会话与来源边界静态检查通过');
