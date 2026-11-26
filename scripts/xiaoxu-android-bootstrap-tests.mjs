// Static boundary tests for native Android V0.2. Android CI also compiles the real APK.
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { runInNewContext } from 'node:vm';
const read = p => readFileSync(new URL('../' + p, import.meta.url), 'utf8').replace(/\r\n/g, '\n');

const manifest = read('apps/xiaoxu-android/app/src/main/AndroidManifest.xml');
const strings = read('apps/xiaoxu-android/app/src/main/res/values/strings.xml');
const appGradle = read('apps/xiaoxu-android/app/build.gradle');
const rootGradle = read('apps/xiaoxu-android/build.gradle');
const main = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/MainActivity.kt');
const pageMotion = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ImmediateRouteTransition.kt');
// RC1 retired these sources and superseded QA requests. Deleting them is not
// enough: later UI branches must not silently restore obsolete containers.
const obsoleteUiPaths = [
  'apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/PrimaryPageMotion.kt',
  'apps/xiaoxu-android/app/src/test/java/io/github/x07xh/xiaoxu/ui/PrimaryPageMotionTest.kt',
  '.github/xiaoxu-apk-build-requests/pending/20261107-sleepdown-page-motion-pr351-qa.txt'
];
for (const path of obsoleteUiPaths) {
  assert.equal(existsSync(new URL('../' + path, import.meta.url)), false,
    '淘汰代码或构建请求不可从旧 UI 分支恢复: ' + path);
}

// Settings navigation and reminders now live in separate, active upstream-hosted routes.
const profileSettings = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ReminderSettingsFeature.kt');
const settingsIndex = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/SettingsScreen.kt');
const settingsRoutes = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/AppFeaturePages.kt');
const sharedFrame = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/AppPageFrame.kt');
const db = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalTaskDatabase.kt');
const ci = read('.github/workflows/build-xiaoxu-android.yml');
const visualQa = read('scripts/xiaoxu-ace6-visual-qa.mjs');
assert.match(visualQa, /\['03-course-detail',/,'一加 Ace 6 真机流程必须单独采集课程详情底部弹窗');
assert.match(visualQa, /\['04-schedule-settings',/,'一加 Ace 6 真机流程必须采集原课表设置页');
assert.match(visualQa, /\['05-schedule-day',/,'需要采集日视图，避免只核验周视图');
assert.match(visualQa, /\['06-course-editor',/,'编辑课程全屏玻璃页面是必需的真机截图场景');
assert.match(visualQa, /requiredReferenceScenes:[^\n]*'02-schedule'[^\n]*'03-course-detail'[^\n]*'04-schedule-settings'[^\n]*'05-schedule-day'[^\n]*'06-course-editor'[^\n]*'11-glass-rest'[^\n]*'16-glass-dialog-gray-edge'/,
  '课表及玻璃导航关键截图缺一不可');
assert.match(visualQa, /\['12-glass-rapid-tabs',/, '必须采集快速切页流畅度');
assert.match(visualQa, /\['13-glass-drag-return',/, '必须采集拖拽回弹');
assert.match(visualQa, /\['14-glass-course-content',/, '必须采集课表背景折射');
assert.match(visualQa, /\['15-glass-gesture-inset',/, '必须检查系统手势安全区');
assert.match(visualQa, /incomplete_reference_screenshots/,'缺失关键截图时不可误报验收完成');
assert.match(visualQa, /function isTargetInForeground\(targetPackage = packageName\)/,
  '真机截图须核验校序应用位于前台');
assert.match(visualQa, /const foregroundVerified = isTargetInForeground\(\)/,
  '必须在实际截图时检查应用前台状态');
assert.match(visualQa, /x\.captured && x\.foregroundVerified/,
  '前台应用身份不明的截图不可计入课表视觉验收');
assert.match(visualQa, /sha256sum', installedBase/,'必须尝试核对手机实际安装 APK 的哈希');
assert.match(visualQa, /installedMatchesLocal !== true/,'无法核实安装包与本机文件一致时不可开始人工视觉验收');
assert.match(visualQa, /unverified_installed_build/,'截图完整也不能掩盖 APK 版本证据不足');
assert.match(visualQa, /gitCommitVerifiedAgainstRepository: false/,'采集脚本不可把用户填写的提交 SHA 冒充已核验');
assert.match(visualQa, /jankyPercent/, '玻璃导航性能采集必须保留卡顿帧比例');
assert.match(visualQa, /frameSampleUsable: validFrameSample && totalFrames >= 30/,
  '不足30帧不得当作有效性能样本');
assert.match(visualQa, /performanceEvidenceComplete = metadata.performanceSamples.every/,
  '必须逐场景记录快速切页、拖拽与真实课程背景的性能样本');
assert.match(visualQa, /requires_manual_frame_and_visual_review/,
  '不能仅凭 gfxinfo 帧数把玻璃复刻标记为验收完成');
assert.match(visualQa, /--reference-package/, '真机采集须允许指定北化课表原版包名');
assert.match(visualQa, /referenceSceneSet\.has\(name\)/,
  '只在对应原版有参考意义的场景采集同机对照，避免重复全量取证');
assert.match(visualQa, /isTargetInForeground\(referencePackage\)/,
  '必须确认原版的截图确实在原版应用前台');
assert.match(visualQa, /buct-original-/, '原版与校序截图必须分别保存，不能覆盖');
assert.match(visualQa, /referenceComparisonComplete/,
  '缺少真实原版画面时不得称为复刻完成');
assert.match(visualQa, /requires_manual_side_by_side_review/,
  '即使原版图片齐全也不能把脚本当成像素级对照验收');
assert.doesNotMatch(visualQa, /(?:upload|fetch|axios)\s*\(/,
  '截图只能在本机保存，不能自动传输私人课程或任务截图');

assert.match(strings, /<string name="app_name">校序<\/string>/);
const glassKit = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/GlassKit.kt');
const pageScaffold = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/GlassPageScaffold.kt');
const windowHost = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/GlassWindowHost.kt');
const dialogHost = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/AppDialog.kt');
const uiTheme = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/RebuildTheme.kt');
const systemUi = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/UpstreamSystemUi.kt');
const motionHost = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleMotionProfile.kt');
const rebuildShell = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/RebuildShell.kt');
const navDock = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/RebuildCapsuleDock.kt');
const designSystem = [glassKit, pageScaffold, windowHost, dialogHost, uiTheme, systemUi, sharedFrame].join('\n');
const scheduleUi = ['ScheduleFeature.kt','ScheduleHeader.kt','UpstreamTimetable.kt','UpstreamCourseDetailSheet.kt'].map(name => read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/' + name)).join('\n');
const messageUi = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/MessageCenterFeature.kt');
// Current UI uses a single upstream layout/theme/window host. The old fixed
// XiaoxuPalette geometry is intentionally retired; device comparison remains
// independently required by the unchanged Ace6 visual QA evidence checks.
assert.match(sharedFrame, /widthIn\(max = if \(wideContent\) AppSpacing\.workspaceMaxWidth else AppSpacing\.formMaxWidth\)/);
assert.match(settingsIndex, /widthIn\(max = AppSpacing\.formMaxWidth\)/);
assert.match(settingsRoutes, /wideContent = route == AppRoute\.HOME \|\| route == AppRoute\.TASKS \|\| route == AppRoute\.MESSAGES/);
assert.doesNotMatch(scheduleUi + messageUi, /private fun Modifier\.pageWidth\(/);
assert.match(uiTheme, /CourseSelectorTheme\(content = content\)/);
assert.match(systemUi, /fun SystemTopBar\(/);
assert.match(sharedFrame, /SystemTopBar\(title = title, collapseFraction = if \(compactHeader\) 1f else lift/);
assert.match(pageScaffold, /GlassWindowHost\(modifier\)/);
assert.match(windowHost, /LocalGlassWindowOwner\.current === view && !isolatePage/);
assert.match(dialogHost, /window\?\.setDimAmount\(DialogScrimAlpha\)/);
assert.match(glassKit, /fun InsetGroupedSection\(/);
assert.match(glassKit, /fun InsetGroupedRow\(/);
assert.match(glassKit, /\.background\(glassSurfaceColor\(\)\)/);
assert.match(scheduleUi, /ModalBottomSheet\(onDismissRequest = onClose/);
assert.match(scheduleUi, /IconButton\(onEdit, Modifier\.size\(48\.dp\)\)/);
assert.match(scheduleUi, /\.combinedClickable\([\s\S]*?indication = null,/);
assert.match(messageUi, /InsetGroupedRow\(title = notice\.title/);
assert.match(messageUi, /AppDialog\(usePortal = true,/);
assert.match(motionHost, /Box\(modifier\.fillMaxSize\(\)\.clipToBounds\(\)\)/);
assert.match(rebuildShell, /activePage = route\.ordinal, retainOutgoing = false/);
assert.match(motionHost, /else listOf\(activePage\)/);
assert.match(rebuildShell, /savedPages\.SaveableStateProvider\(pageRoute\.name\)/);
assert.match(rebuildShell, /BackHandler\(enabled = route\.parent != null \|\| routeTrail\.isNotEmpty\(\)\)/);
assert.match(rebuildShell, /if \(!wide && showPrimary\) RebuildCapsuleDock\(/);
assert.match(navDock, /rememberCombinedBackdrop\(backdrop, tabsBackdrop\)/);
assert.match(navDock, /else iconPlayback\.replay\(index\)/);
assert.match(windowHost, /rememberCombinedBackdrop\(wallpaper, page\)/);

assert.doesNotMatch(strings, /<string name="start_url"/, '已退役 TWA 默认主页');
assert.match(manifest, /android:name="\.MainActivity"[\s\S]*?android:exported="true"/);
assert.doesNotMatch(manifest, /com\.google\.androidbrowserhelper\.trusted\.LauncherActivity/, '退役 TWA 页面不可回流');
assert.doesNotMatch(appGradle, /com\.google\.androidbrowserhelper:androidbrowserhelper/, '退役 TWA 依赖不可回流');
assert.match(manifest, /android:allowBackup="false"/);
assert.match(manifest, /android:usesCleartextTraffic="false"/);
assert.doesNotMatch(manifest, /READ_SMS|READ_CONTACTS|QUERY_ALL_PACKAGES|BIND_ACCESSIBILITY_SERVICE/i);
assert.doesNotMatch(strings + main + manifest, /wxu_[A-Za-z0-9_-]+|SCHOOL_SESSION_KEY|OWNER_LOGIN_SECRET/i);
assert.doesNotMatch(main, /\bWebView\s*\(|addJavascriptInterface\s*\(|CookieManager\s*\.|document\.cookie/);
assert.match(rootGradle, /org\.jetbrains\.kotlin\.plugin\.compose/);
assert.match(appGradle, /io\.github\.x07xh\.xiaoxu/);
assert.match(appGradle, /androidx\.room:room-runtime:2\.8\.5/);
assert.match(appGradle, /androidx\.compose:compose-bom:2026\.03\.01/);
assert.match(rebuildShell, /if \(!wide && showPrimary\) RebuildCapsuleDock\(/,
  '主壳层须只放一套可折射玻璃底栏');
assert.match(rebuildShell, /if \(wide\) NavigationRail \{/,
  '宽屏保留唯一横屏侧栏适配');
assert.match(main, /LocalTaskDatabase/);
assert.match(db, /@Entity\(tableName = "local_tasks"/);
// Room v1 -> v2：只增列、不做破坏性迁移，旧任务不得被清空。
assert.match(db, /val source: String\?/);
assert.match(db, /val due: String\?/);
assert.match(db, /version = 5/);
assert.match(db, /Migration\(2, 3\)/);
assert.match(db, /CREATE TABLE IF NOT EXISTS native_school_records/);
assert.match(db, /Migration\(1, 2\)/);
assert.match(db, /ADD COLUMN source TEXT/);
assert.match(db, /ADD COLUMN due TEXT/);
assert.match(db, /Migration\(3, 4\)/);
assert.match(db, /CREATE TABLE IF NOT EXISTS local_task_dependencies/);
assert.match(db, /addMigrations\(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5\)/);
assert.doesNotMatch(db, /fallbackToDestructiveMigration|DROP TABLE/);
assert.match(db, /suspend fun insert\(/);
assert.match(db, /suspend fun setCompleted\(/);
assert.match(db, /suspend fun remove\(/);
const reminder = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalDeadlineReminders.kt');
const localTaskUi = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/LocalTaskEditSheet.kt');
const taskWorkspaceForComposerGate = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/TasksFeature.kt');
assert.match(profileSettings, /fun requestNotificationAccess\(target: String\)/,
  'Two reminder toggles must share one Android permission gateway');
assert.equal(profileSettings.split('ContextCompat.checkSelfPermission(').length - 1, 1,
  'Notification runtime permission must be checked at one call site');
assert.equal(profileSettings.split('requestPermission.launch(Manifest.permission.POST_NOTIFICATIONS)').length - 1, 1,
  'Notification permission launcher must have one call site');
assert.match(profileSettings, /requestNotificationAccess\("course"\)/,
  'Course reminder uses the shared permission gateway');
assert.match(profileSettings, /requestNotificationAccess\("deadline"\)/,
  'Deadline reminder uses the shared permission gateway');
assert.doesNotMatch(localTaskUi, /tween\(180\)/,
  'Do not restore an independent hard-coded task pill animation duration');

assert.match(manifest, /android\.permission\.POST_NOTIFICATIONS/,
  'Notification permission must be explicitly declared for Android 13+');
assert.match(appGradle, /androidx\.work:work-runtime-ktx:2\.11\.0/,
  'Local reminders must use OS-persistent WorkManager, not a fragile timer');
assert.match(db, /suspend fun findById\(id: Long\): LocalTask\?/,
  'Notification must re-read the live Room row before delivery');
assert.match(reminder, /getBoolean\(ENABLED, false\)/,
  'Deadline notifications must be opt-in, never enabled by default');
assert.match(reminder, /cancelAllWorkByTag\(TAG\)/,
  'Disabling reminders must cancel scheduled work');
assert.match(reminder, /task\.completed \|\| task\.due != due/,
  'Completed or edited tasks cannot emit stale notifications');
assert.match(reminder, /LocalDate\.now\(\) != expected/,
  'Late work must not invent a new deadline');
assert.match(reminder, /LocalTaskDatabase\.open\(applicationContext\)\.tasks\(\)\.findById\(id\)/,
  'Reminder worker must only read local tasks, not school snapshots');
assert.doesNotMatch(reminder, /CloudClient|NativeSchoolRecord|CookieManager|SCHOOL_SESSION_KEY/,
  'Local notifications may not use school accounts, credentials or cached homework');
// Screenshot regression 2026-11-09: creating one local task must not show
// the large management list and truncated horizontal source selector.
assert.match(localTaskUi, /internal fun LocalTaskCreateSheet\(/,
  '本机新建独立紧凑表单，不再嵌套筛选和空任务列表');
assert.match(localTaskUi, /private fun LocalTaskSourcePicker\(/,
  '分类使用统一完整下拉选择器，避免横向截断');
assert.doesNotMatch(localTaskUi,
  /LazyRow\(horizontalArrangement = Arrangement\.spacedBy\(8\.dp\)\)\s*\{\s*items\(count = TASK_SOURCES\.size/,
  '新建和编辑任务禁止再出现横向截断的五项分类');
assert.match(taskWorkspaceForComposerGate, /adding by rememberSaveable/,
  '顶栏新增任务必须独立于本机任务管理');
assert.match(taskWorkspaceForComposerGate, /if \(data\.cloud\.authenticated\) chooseDestination = true else adding = true[\s\S]*?if \(adding\) LocalTaskCreateSheet\(actions\.localTaskDao,/,
  '新增按钮直接进入单用途新建页');
// The secondary local manager was retired; main hub and Room execution tests own this behavior.
assert.match(profileSettings, /ActivityResultContracts\.RequestPermission\(\)/,
  '通知权限只在用户开启提醒时申请');
assert.match(profileSettings, /LocalDeadlineReminders\.setEnabled\(context, value\)/,
  '本机截止通知的已有授权状态仅在通知设置中切换');
assert.doesNotMatch(localTaskUi, /LocalDeadlineReminders\.setEnabled|rememberLauncherForActivityResult/,
  '任务编辑页不再保留第二套截止通知总开关');
const appWorkspace = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/AppWorkspaceState.kt');
assert.match(rebuildShell, /val data = rememberAppWorkspace\(dao, cloud,/,
  'Reminder observer must be owned by the application shell rather than a task page');
assert.match(appWorkspace, /val tasks by rememberSnapshotRows\(dao\) \{ dao\.observe\(\) \}/);
assert.match(appWorkspace, /LaunchedEffect\(tasks\) \{\s*val snapshot = tasks \?: return@LaunchedEffect[\s\S]*?LocalDeadlineReminders\.reconcile\(context, snapshot, previousTasks = previous\)[\s\S]*?previousTasks = snapshot/,
  'Room edits must re-plan notifications, including when the task page is absent, without inventing an initial empty snapshot');

assert.match(ci, /assembleDebug/);
assert.match(ci, /upload-artifact/);

const cloud = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/CloudClient.kt');
const taskUpdateRoute = read('functions/api/v1/assignments/[id].js');
assert.match(taskUpdateRoute, /export async function onRequestPost\(context\)/,
  'Android 个人完成状态须通过现有云端任务路径 POST 兼容方法');
assert.match(taskUpdateRoute, /Object\.keys\(body\)\.sort\(\)\.join\(','\)!=='localStatus,revision'/,
  '兼容 POST 严格限定个人状态与版本，不开放标题或官方字段修改');
const panels = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ProfileFeature.kt');
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
assert.match(appWorkspace, /LifecycleEventObserver/);
assert.match(appWorkspace, /Lifecycle\.Event\.ON_RESUME/);
assert.match(appWorkspace, /DisposableEffect/);
assert.match(appWorkspace, /cloud\.refreshOnForeground\(\)/);
assert.match(main, /shouldRefreshAfterWebsite = true/);
assert.match(appWorkspace, /if \(force\) cloud\.refresh\(\)/);

assert.match(cloud, /\/api\/v1\/connections/);
assert.doesNotMatch(cloud, /request\("\/api\/v1\/schedule"/,
  '本机课表不能再从云端自动拉取课程');
assert.match(settingsRoutes, /ScheduleFeature\(data, actions,/);
assert.match(cloud, /\/api\/v1\/wechat\/candidates/);
assert.match(cloud, /\/api\/notices/);
assert.match(cloud, /www2\.ncwu\.edu\.cn/);
const workspaceLogic = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/WorkspaceTaskLogic.kt');
const readContracts = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/RebuildReadContracts.kt');
const workspace = [taskWorkspaceForComposerGate, workspaceLogic, settingsRoutes, appWorkspace, readContracts].join('\n');
assert.match(localTaskUi, /internal fun TaskCompletionCheckbox\(/,
  'Local editor and task hub must share one completion-checkbox renderer');
assert.equal((localTaskUi.match(/\bCheckbox\(/g) || []).length, 1,
  'Only the reusable task control may instantiate a Material checkbox');
assert.equal((taskWorkspaceForComposerGate.match(/TaskCompletionCheckbox\(/g) || []).length, 2,
  'Local and accepted WeChat rows share the same completion checkbox');
assert.doesNotMatch(taskWorkspaceForComposerGate, /\bCheckbox\(/,
  'Unified task list must not maintain duplicate Material checkbox chrome');
assert.match(profileSettings, /private fun ReminderToggleRow\(/,
  'Course and deadline reminders must share one settings row');
assert.equal((profileSettings.match(/ReminderToggleRow\(/g) || []).length, 6,
  'Course, deadline, two date phases and local quiet hours share one toggle implementation');
assert.equal((profileSettings.match(/LiquidSwitch\(/g) || []).length, 1,
  'Both reminders must use a shared switch layout');
assert.match(settingsRoutes, /AppRoute\.HOME -> HomeFeature\(data, actions\)/);
assert.match(settingsRoutes, /AppRoute\.TASKS -> TasksFeature\(data, actions,/);
assert.match(rebuildShell, /connectLocal = openLocalSchool/);
assert.match(panels, /actions\.connectLocal\("chaoxing"\)/);
assert.ok(main.includes('https://dorm-os-dk9.pages.dev/#settings'), 'school connector must deep-link into the existing connection center');
assert.match(workspace, /fun combineWorkspaceTasks\(/);
assert.match(settingsRoutes, /fun HomeFeature\(/);
assert.doesNotMatch(workspace, /private fun (?:WorkspaceCard|Metric)\s*\(|private val WorkspaceBlue\b/,
  '已废弃的首页展示卡片和专用颜色不得回流');
assert.match(workspace, /fun (?:ColumnScope\.)?TasksFeature\(/, '真实任务工作台继续复用既有组件');
assert.match(workspace, /fun (?:ColumnScope\.)?TasksFeature\(/);
const localEditorSource = localTaskUi;
assert.match(localEditorSource, /fun LocalTaskEditSheet\(/);
assert.doesNotMatch(localEditorSource, /\bCardDefaults\b|\bCard\s*\(/,
  '禁止独立 Material3 Card 回流本机任务编辑器');
assert.doesNotMatch(localEditorSource, /fun LocalTaskBoard\(/);
assert.doesNotMatch(localEditorSource, /private fun StatTile\(/,
  '任务 KPI 仅由新版任务中心展示，不复活旧数字大卡');
assert.match(rebuildShell, /dao\.trySetCompleted\(id, value\)/, '统一任务列表保留真实完成写入');
assert.match(taskWorkspaceForComposerGate, /actions\.localTaskDao\.remove\(deleting\.id\)/, '统一详情保留删除确认与真实 Room 写入');
assert.match(profileSettings, /LocalDeadlineReminders\.setEnabled\(context, value\)/,
  '本机截止提醒应由统一通知页面控制');
assert.match(profileSettings, /LocalDeadlineReminders\.reconcile\(context, tasks\)/,
  '新通知设置必须沿用真实 Room 任务的既有提醒计划');
assert.match(taskWorkspaceForComposerGate, /CloudTaskEditor\(data\.cloud, actions,/);
assert.match(taskWorkspaceForComposerGate, /fun CloudTaskEditor\(/, '云端任务编辑器复用已有 CloudClient');
assert.match(rebuildShell, /cloud\.addCloudTask\(title, owner\)/, '云端新增仍使用现有真实接口');
assert.doesNotMatch(panels, /fun CloudTasksPanel\(/,
  '退役的嵌套云端任务列表不应重新出现在管理面板');
assert.doesNotMatch(panels, /state\.tasks\.take\(50\)/,
  '云端列表仅由统一任务中心显示，避免重复卡片和不同截断上限');
assert.match(taskWorkspaceForComposerGate, /仅显示已读取的任务，不推断平台状态/);
assert.match(settingsRoutes, /lessons\.filter \{ it\.weekday == day && week in it\.weeks \}/, '首页只匹配本机导入课表星期');
assert.match(readContracts,
  /本机导入（待本人核对）/,
  '课表来源与未核验教学周必须在课表页保留提示，首页无需反复显示');
assert.doesNotMatch(workspace, /WebView|HttpURLConnection|CookieManager/);
assert.doesNotMatch(workspace, /VerifiedLesson\(/);
assert.match(settingsRoutes, /SettingsScreen\(data, actions\)/, '我的页面应接入唯一原生设置路由');
assert.match(settingsRoutes, /AppRoute\.CONNECTIONS -> ConnectionsFeature\(data, actions\)/,
  '已有真实授权面板应仅被复用于数据连接二级页');
// 消息页由 MessageCenterPanel 承担：公开公告 + 微信待审核计数，两个旧面板已并入。
assert.match(settingsRoutes, /AppRoute\.MESSAGES -> MessageCenterFeature\(data, actions\)/);
assert.match(panels, /PasswordVisualTransformation/);
assert.match(panels, /internal fun ConnectionsFeature\(/);
assert.match(panels, /internal fun AccountFeature\(/);
assert.equal((panels.match(/header = "本机学校读取"/g) || []).length, 1);
assert.equal((panels.match(/header = "云端学校授权"/g) || []).length, 1);
assert.ok(panels.indexOf('header = "本机学校读取"') < panels.indexOf('header = "云端学校授权"'),
  '本机官方页面读取必须先于云端可选授权展示');
const nativeSchoolPanel = panels;
assert.match(nativeSchoolPanel, /actions\.connectLocal\("chaoxing"\)/);
assert.match(nativeSchoolPanel, /actions\.connectLocal\("ncwu"\)/);
assert.match(nativeSchoolPanel, /actions\.connectLocal\("second_class"\)/);
assert.match(panels, /val verified = cloud\.authenticated && cloud\.connectionsFresh/);
assert.match(panels, /if \(verified && linked\?\.sessionState == "active"\)/);
assert.doesNotMatch(main, /var selectedTool by rememberSaveable|private fun ConnectionsPage\(/,
  '旧版工具弹窗和混合式连接入口应彻底退役');
assert.match(settingsIndex, /actions\.navigate\(AppRoute\.CONNECTIONS\)/,
  '设置按分类索引；业务内容应进入独立二级路由');
assert.match(settingsRoutes, /AppRoute\.REMINDER_SETTINGS -> ReminderSettingsFeature\(data\)/,
  '通知设置必须共用一套二级路由，而不是复制页面');
assert.match(profileSettings, /AppSnapshotContent\(data\.localTasks\)[\s\S]*?NotificationSettingsContent\(tasks\)/,
  '分类设置复用唯一提醒权限与调度网关');
assert.doesNotMatch(profileSettings, /onRouteChange\("notifications-course"\)/,
  'Reminder settings must not require a second navigation hop');
assert.doesNotMatch(profileSettings, /onRouteChange\("notifications-task"\)/,
  'Course and task toggles must share a single screen');
assert.match(profileSettings, /XiaoxuGroupedSection\(title = "个人任务"\)/);
assert.match(profileSettings, /updateReminderSchedule\(reminderSchedule\.copy\(/);
assert.match(profileSettings, /title = "上课提醒", subtitle = "", checked = enabled/,
  '课程提醒状态直接来自本机真实开关，单页操作不再二次跳转');
assert.match(profileSettings, /title = "截止提醒", subtitle = "", checked = deadlineEnabled/,
  '本机截止提醒独立显示真实开关状态');
assert.match(profileSettings, /LocalCourseReminders\.setLeadMinutes\(context, minutes\)/,
  '课程提前时间调整应写入唯一的本机提醒配置');
assert.match(settingsRoutes, /AppRoute\.TASK_BACKUP -> LazyColumn \{ item \{ LocalTaskBackupPanel\(\) \} \}/);
assert.match(settingsRoutes, /AppRoute\.LICENSES -> LazyColumn \{ item \{ OpenSourceCreditsPanel\(\) \} \}/);
assert.doesNotMatch(settingsIndex, /第二课堂记录|selectedTool/,
  '第二课堂记录属于业务页，不回流设置首页');

assert.match(panels, /actions\.connectLocal\("ncwu"\)/);
assert.match(panels, /fun ConnectionsFeature\(/);
assert.match(rebuildShell, /readGrades = \{ cloud\.loadGrades\(\) \}/,
  '青果连接保留，旧课表同步不得冒充成绩读取');
assert.doesNotMatch(panels, /"ncwu" -> "本机打开青果官方课表"/);
assert.match(panels, /actions\.connectLocal\("second_class"\)/);
assert.match(panels, /只保存学校本人可见记录/);
assert.match(cloud, /val tasksFresh: Boolean = false/);
assert.match(cloud, /state = state\.copy\(tasksFresh = false, connectionsFresh = false,/);
assert.match(cloud, /tasksFresh = tasks != null/);
assert.match(cloud, /tasksFresh = false, connectionsFresh = false,/);
assert.match(taskWorkspaceForComposerGate, /data\.cloud\.authenticated && !data\.cloud\.tasksFresh/,
  '云端任务快照新鲜度必须由统一任务中心说明');
assert.match(taskWorkspaceForComposerGate, /data\.cloud\.tasks\.takeIf \{ data\.cloud\.authenticated && data\.cloud\.tasksFresh \}\.orEmpty\(\)/,
  '新版明确提示未核验，并且隐藏未通过当前账户验证的云端任务');
assert.match(settingsRoutes, /val connectLocal: \(String\) -> Unit/);
assert.match(panels, /connectionStatusLabel\(record\)/);
assert.match(rebuildShell, /cloud\.syncAuthorizedSchool\(provider\)/);
assert.match(panels, /actions\.authorize\("ncwu"\)/);
assert.match(panels, /actions\.authorize\("second_class"\)/);
assert.match(panels, /学校授权/);
assert.match(cloud, /suspend fun syncAuthorizedSchool\(provider: String\): Boolean/);
assert.match(cloud, /provider != "second_class"/,
  '旧青果课程同步 POST 不得在本机课表模式执行');
assert.match(cloud, /linked\?\.sessionState != "active"/);
assert.match(cloud, /\/api\/v1\/connections\/\$provider\/sync/);
assert.match(cloud, /result\.json\.optBoolean\("refreshed"\)/);
assert.match(cloud, /schoolSyncBusyProvider/);
// A failed unrelated WeChat/tasks read must not negate a fresh school sync,
// while a failed connection/snapshot read must not reuse stale "connected" UI.
assert.match(cloud, /connectionsFresh = platforms != null/);
assert.doesNotMatch(cloud, /scheduleFresh|scheduleLoaded/);
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
assert.doesNotMatch(cloud, /state\.scheduleStatus/);
assert.match(cloud, /state\.secondClassFresh &&/);
assert.match(cloud, /state\.secondClass\?\.provenance == "trusted_readonly"/);
assert.doesNotMatch(cloud, /val verified = state\.privateError == null/);
assert.match(panels, /云端状态尚未核验/);
assert.match(panels, /val verified = cloud\.authenticated && cloud\.connectionsFresh/);
assert.match(cloud, /clearAccountSnapshots/);
assert.doesNotMatch(panels, /schoolPassword|CookieManager|WebView|READ_SMS/);

assert.doesNotMatch(cloud + panels, /addJavascriptInterface|READ_SMS|READ_CONTACTS|SCHOOL_SESSION_KEY|OWNER_LOGIN_SECRET\s*=/);
assert.doesNotMatch(cloud, /http:\/\/|https:\/\/[^\s"]+school/);

// Chaoxing now has one local reader. Its protocol and parsing behavior run
// in Android JVM tests; cloud endpoint and old DOM implementation assertions
// were retired with those implementations, not kept as false requirements.
assert.doesNotMatch(cloud, /suspend fun (?:loadChaoxingCourses|syncChaoxingAllCourses|startChaoxingQr)/);
assert.doesNotMatch(panels + workspace, /client\.(?:loadChaoxingCourses|syncChaoxingAllCourses|refreshChaoxingCoursesAndHomework)/);

const secondClass = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/SecondClassSnapshot.kt');
assert.equal(existsSync(new URL('../apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/SecondClassPanel.kt', import.meta.url)), false,
  '已退役的重复第二课堂云端快照页面不可重新加入源码');
assert.doesNotMatch(main, /SecondClassPanel\(cloud, openWebsite\)/,
  '第二课堂个人记录不应返回我的设置入口');
const secondClassMain = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/SecondClassPage.kt');
assert.match(settingsRoutes, /AppRoute\.SECOND_CLASS -> SecondClassContent\(data\.nativeRecords/, '第二课堂通过唯一共享数据入口');
assert.match(rebuildShell, /savedPages\.SaveableStateProvider\(pageRoute\.name\)/, '统一页面状态持有者');
assert.match(secondClassMain, /SecondClassContent\(pageSnapshot\(all\), onRefreshResult, onSavedReceipt, connectOnOpen, modifier\)/, '独立授权活动复用同一内容');
assert.match(secondClassMain, /LiquidSegmentedControl\(listOf\("报名中", "待验证", "即将报名"\), catalogFilter/, '真实目录类别使用统一分段控件');
assert.match(secondClassMain, /detail\?\.let \{ row ->\s*AppDialog\(/, '仅真实记录打开统一详情弹窗');
assert.match(secondClassMain, /LazyColumn\(Modifier\.weight\(1f\)/, '单一惰性列表');
assert.match(secondClassMain, /requestedTerm by rememberSaveable\(binding!!\.details\)/, '学期选择按已核验身份隔离');
assert.match(secondClassMain, /item\(key = "credits-overview"\)[\s\S]*?Text\("积分概览", Modifier\.weight\(1f\)[\s\S]*?AcademicTermPicker\(termTitles, selected/, '积分标题同行学期选择');
assert.match(secondClassMain, /item\(key = "credits-overview"\)[\s\S]*?title = "积分明细"/, '积分明细只展示入口');
assert.match(secondClassMain, /if \(row\.kind == "credits"\)[\s\S]*?secondClassCategories\(row\)/, '积分分类在真实详情弹窗显示');
assert.match(secondClassMain, /secondClassPoints\(total\) \?: "—"/, '未知总积分不可伪造零');
assert.match(secondClassMain, /if \(summary == null\) "未读取" else "\$\{activities\.size\} 项"/, '未核验活动列表不能推导数量');
assert.match(secondClassMain, /it\.provider == "second_class" && it\.sourceHost == "dekt\.ncwu\.edu\.cn"/, '仅学校来源');
assert.match(secondClassMain, /it\.details\.matches\(Regex\("\[a-f0-9\]\{64\}"\)\)/, '真实身份绑定');
assert.match(secondClassMain, /capture != null && capture > 0 && binding\?\.capturedAt == capture/, '身份和积分同次读取');
assert.match(secondClassMain, /it\.kind in setOf\("activity", "activity_summary"\) && it\.capturedAt == stamp/, '个人活动与积分读取时间一致');
assert.match(secondClassMain, /personal\.filter \{ it\.kind == "activity" \}/, '汇总不是个人活动卡片');
assert.match(secondClassMain, /AcademicReadAction\("手动读取", refreshing\) \{ refresh\(\) \}/, '唯一手动刷新入口');
assert.match(secondClassMain, /Intent\(context, NativeSchoolConnectActivity::class\.java\)/, '授权读取复用原Activity');
assert.match(secondClassMain, /\.putExtra\(NativeSchoolConnectActivity\.EXTRA_PROVIDER, "second_class"\)/, '学校读取限定第二课堂');
assert.match(secondClassMain, /Text\("学校官网"\)/, '官网入口');
for (const label of ['积分概览', '我的活动', '活动广场', '积分明细']) {
  assert.ok(secondClassMain.includes('"' + label + '"'), '单页必须直接呈现：' + label);
}
assert.doesNotMatch(secondClassMain, /sectionOptions|val currentScroll|ScrollableTabRow|TabRow\(/,
  '不允许恢复旧三顶栏或独立滚动容器');

const localCatalogParser = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalSecondClassCatalogEmpty.kt');
const localReader = read('apps/xiaoxu-android/app/src/main/assets/ncwu_secondclass_local.js');
const localNativeCatalog = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/NativeSchoolConnectActivity.kt');
const secondClassDao = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/NativeSchoolRecords.kt');
assert.match(secondClassMain, /"活动暂未获取"/, '未同步不能虚构暂无活动');
assert.doesNotMatch(secondClassMain, /发现活动|学校活动广场尚未完成本机读取|个人已报名活动不是活动广场|TabRow\(|FilterChip\(/,
 '二课不能恢复网站式导航与说明卡');
assert.match(secondClassMain, /"catalog_empty"/);
assert.match(secondClassMain, /val visibleCatalogActivities = remember\(catalog, activeCatalogFilter\) \{ catalog\.filter/,
  '第二课堂活动广场必须读取本机已验证的非空目录记录');
assert.match(secondClassMain, /onSelect = \{ catalogFilter = it \}/,
 '活动筛选继续保持所选官方目录类别');
assert.match(secondClassMain, /val catalogKeys = listOf\("报名中", "待验证", "即将开始报名"\)/,
 '活动类别须保留报名中筛选');

assert.doesNotMatch(rebuildShell, /\bInitialPageLoad\(/, '共享快照页面不等待异步挂载');
assert.match(motionHost, /val frame = if \(retainOutgoing\) state\.transform\(page\) else PageMotion\(\)/, '即时页无额外位移');
assert.match(rebuildShell, /LaunchedEffect\(route\) \{\s*if \(route == AppRoute\.SECOND_CLASS\) latestSecondClassRefresh\(\)/, '进入二课复用Activity读取');
assert.match(main, /secondClassAutoJob\?\.isActive == true\) return/, '已有读取不重复启动');
assert.match(main, /requestSecondClassRefresh = ::requestSecondClassRefresh/, '读取由Activity生命周期拥有');
assert.doesNotMatch(main, /delay\(1_000\)/, '没有人为固定刷新延迟');
assert.match(secondClassMain, /正在同步 · 保留当前数据/);
assert.match(secondClassMain, /InsetGroupedSection\(animateSize = false\)/);

const segmentControl = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/UpstreamLiquidSegmentedControl.kt');
assert.match(segmentControl, /height: Dp = 48\.dp/, "上游分段控件默认48dp");
assert.match(segmentControl, /\/ optionCount\)\.coerceAtLeast\(1f\)/, "按分段数量平均分配宽度");
assert.match(segmentControl, /\.weight\(1f\)[\s\S]*?role = Role\.Tab/, "分段等宽且有标签语义");
assert.match(taskWorkspaceForComposerGate, /onSelect = \{ filter = filters\[it\]\.first \}, height = 48\.dp\)/, "任务页使用48dp分段控件");
assert.match(taskWorkspaceForComposerGate, /SystemStatStrip\(items = listOf\("待办"[\s\S]*?"待核验"[\s\S]*?"已完成"/, "统一三态统计");
assert.match(taskWorkspaceForComposerGate, /DropdownMenu\(expanded = sourceMenuOpen/, "来源筛选可关闭");
assert.match(taskWorkspaceForComposerGate, /if \(onlyThisWeek\) TextButton\(onClick = \{ onlyThisWeek = false \}\)/, "本周筛选可直接解除");
assert.match(taskWorkspaceForComposerGate, /onClick = \{ detailKey = task\.key \}/, "任务直接进入详情");
assert.match(taskWorkspaceForComposerGate, /sourceFilter = "all"; onlyThisWeek = false; query = ""/, "清除筛选及搜索词");
assert.match(taskWorkspaceForComposerGate, /val saved = actions\.updateLocalTask\(localId, value\)[\s\S]*?if \(saved && value\) lastCompletedLocalId = localId/, "仅实际写入成功后提供撤销");
assert.match(taskWorkspaceForComposerGate, /TextButton\(enabled = !saving,[\s\S]*?actions\.updateLocalTask\(undoTask\.id, false\)/, "撤销使用同一真实Room写入，保存期间禁用");
assert.match(workspace, /row\.storage == "cloud" && row\.dateSource == "学习通本机官方截止"/, "仅经核验学习通官方截止参与排序");
assert.match(workspace, /if\(observedDeadline\?\.date != null\) "学习通本机官方截止"/, "不能将旧云端日期冒充官方截止");
assert.doesNotMatch(workspace, /showAdvancedFilters|showManager/, '不恢复旧多层管理栏');
assert.match(sharedFrame, /SystemTopBar\(title = title, collapseFraction = if \(compactHeader\) 1f else lift/);
assert.match(secondClassMain, /fun refresh\(\)/, '授权读取仍有效');
assert.match(secondClassMain, /if \(catalogTime != null && catalogRecord != null\) "上次读取：暂无活动" else "活动暂未获取"/,
 '仅已核验空结果可标暂无活动；非空列表暂未有可映射的安全数据');
assert.match(secondClassMain, /val catalogTime = catalog\.map \{ it\.capturedAt \}\.distinct\(\)\.singleOrNull\(\)/, '空结果必须关联真实读取时间');

assert.match(localCatalogParser, /parseLocalSecondClassCatalogEmpty\(/);
assert.match(localCatalogParser, /catalogStatus/);
assert.match(localCatalogParser, /"verified_empty"/);
assert.match(localCatalogParser, /catalogEmptyFilters/);
const localCatalogActivitiesParser = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalSecondClassCatalogActivities.kt');
assert.match(localCatalogActivitiesParser, /parseLocalSecondClassCatalogActivities\(/,
  '非空目录必须由独立严格字段解析器校验');
assert.doesNotMatch(localCatalogActivitiesParser, /password|cookie|student_code|ticket/i,
  '目录投影不得存储本人身份与登录凭据');
assert.match(secondClassMain, /else items\(visibleCatalogActivities, key = /,
  '真实活动广场记录需要显示成原生卡片');
assert.match(localReader, /includeCatalog = false/);
assert.match(localReader, /catalogPath='\/sys\/stu\/activity'/);
assert.match(localReader, /catalogStatus='nonempty_unverified'/);
assert.match(localReader, /catalogStatus='verified_nonempty'/,
  '完整可信非空活动列表必须区别于结构不明的非空结果');
assert.match(localReader, /catalogActivities/,
  '必须只返回经过字段白名单投影的活动目录');
assert.match(localNativeCatalog, /parseLocalSecondClassCatalogEmpty\(payload, rows\.first\(\)\.capturedAt\)/);
assert.match(localNativeCatalog, /pendingOfficialCatalogRead = true/,
  '学校活动卡不能再交给仅识别 table 的旧页面读取器');
assert.match(localNativeCatalog, /browser\.loadUrl\(entries\.getValue\("second_class"\)\)/,
  '人工点击当前活动页必须回用已授权的本人积分与活动联合读取链路');
assert.match(localNativeCatalog, /parseLocalSecondClassCatalogActivities\(payload, rows\.first\(\)\.capturedAt\)/,
  '非空目录进入严格 Kotlin 字段校验后才写入本机');
assert.match(localNativeCatalog,
  /replaceSecondClassSnapshot\(rows, activities, catalog, catalogActivities,\s*accountHash = accountHash, catalogVerified = catalogVerified\)/,
  '第二课堂手动保存应区分活动广场完整快照和暂不可用');
assert.match(secondClassDao, /val preserveCatalog = !catalogVerified/,
  '只有同账号可保留旧目录，换号需清理');
assert.match(secondClassDao, /deleteKind\("second_class", "catalog_empty"\)/);
assert.doesNotMatch(localReader, /document\.cookie|localStorage|sessionStorage|addJavascriptInterface/);

assert.doesNotMatch(secondClassMain, /LaunchedEffect\((?:scores|section|tab)|autoAttempted/,
 '切换子导航不应自动弹出学校授权窗口');
assert.match(secondClassMain, /NativeSchoolConnectActivity\.EXTRA_AUTO_SCORE/);
assert.match(secondClassMain, /onRefreshResult\?\.invoke\(succeeded\)/, '学校官方读取返回结果必须传给调用页');
const secondClassHome = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/SecondClassHomeActivity.kt');
assert.match(secondClassHome, /SecondClassPage\(/);
assert.match(secondClassHome, /onSavedReceipt = \{ receipt ->/);
assert.match(secondClassHome, /onSavedReceipt = \{ receipt ->\s*setSavedReceipt\(receipt\)\s*finish\(\)/,
  'a validated save must return directly to the initiating page after setting its receipt');
assert.match(secondClassHome, /setResult\(Activity\.RESULT_OK, Intent\(\)/);
assert.match(secondClassHome, /EXTRA_SAVED_COUNT, receipt\.savedCount/);
assert.match(secondClassHome, /connectOnOpen = true/);
assert.match(secondClassHome, /if \(!success\) setSavedReceipt\(null\)/);
assert.match(secondClassHome, /savedInstanceState\?\.getInt\(RECEIPT_COUNT, 0\)/);
assert.match(secondClassHome, /outState\.putInt\(RECEIPT_COUNT, savedReceipt\?\.savedCount \?: 0\)/);
assert.match(secondClassMain, /SecondClassSaveReceipt\.validate/);
assert.doesNotMatch(secondClassHome, /dao\.observe\(|nativeSchoolRecords\(\)/,
    'second-class local entry must reuse the main page, not maintain duplicate UI');

assert.match(secondClassMain, /val capture = credits\.map \{ it\.capturedAt \}\.distinct\(\)\.singleOrNull\(\)/);
assert.match(secondClassMain, /val confirmed = capture != null && capture > 0 && binding\?\.capturedAt == capture/);
assert.match(secondClassMain, /val personal = remember\(records, stamp\) \{ records\.filter \{ it\.kind in setOf\("activity", "activity_summary"\) && it\.capturedAt == stamp \} \}/);
assert.match(secondClassMain, /val catalogTime = catalog\.map \{ it\.capturedAt \}\.distinct\(\)\.singleOrNull\(\)/);
assert.doesNotMatch(secondClassMain, /SecondClassActivity\(|HttpURLConnection|CookieManager|addJavascriptInterface|schoolPassword/);

assert.match(cloud, /\/api\/v1\/second-class/);
assert.match(cloud, /parseSecondClassSnapshot\(response\.json\)/);
assert.match(cloud, /secondClass = null/);
assert.match(secondClass, /provenance == "trusted_readonly"/);
assert.match(secondClass, /"user_import"/);
assert.match(secondClass, /isSchoolReportedAggregate/);
assert.match(secondClass, /"user_browser_export"/);
// Keep the native Academic > SecondClass page and verified cloud data model,
// without retaining a second unused Compose page in Profile.
assert.match(secondClassMain, /fun SecondClassPage\(/);
assert.doesNotMatch(secondClass, /CookieManager|schoolPassword|READ_SMS|addJavascriptInterface/);

// 课表改为唯一的本机导入来源：以前的云端四态仅保留为旧工具函数。
const schedulePanel = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleFeature.kt');
const nativeWeekGrid = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/UpstreamTimetable.kt');
const scheduleHeaderSource = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleHeader.kt');
const timetableSettings = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleSettingsFeature.kt');
const courseEditor = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/CourseEditorDialog.kt');
const timetableTimeWheel = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/GlassWheelPicker.kt');
const courseDetail = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/UpstreamCourseDetailSheet.kt');
const academicGradeArea = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/GradesFeature.kt');
const localTimetableEntry = schedulePanel;
const timetablePageSource = timetableSettings;
const timetablePrefs = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalSemesterConfigStore.kt');
assert.match(schedulePanel, /AppSnapshotContent\(data\.lessons\)/, "课表唯一真实本机快照");
assert.match(schedulePanel, /localImportedScheduleView\(lessons\)\.sourceLabel/, "保留本机来源标注");
assert.match(schedulePanel, /if \(lessons\.isEmpty\(\)\)[\s\S]*?SystemEmptyState\("本机尚无课程"/, "真实空态");
assert.match(schedulePanel, /onSettingsClick = \{ actions\.navigate\(AppRoute\.SCHEDULE_SETTINGS\) \}/, "设置入口");
assert.match(schedulePanel, /onAddClick = if \(data\.lessons is PageSnapshot\.Ready\)/, "只在数据已读取时新增");
assert.match(schedulePanel, /mutableStateOf\(LocalSemesterConfigStore\.loadViewMode\(context\)\)/, "读取持久化视图偏好");
assert.match(schedulePanel, /LocalSemesterConfigStore\.saveViewMode\(context, mode\)/, "保存视图偏好");
assert.match(schedulePanel, /requestedWeek\.takeIf \{ it in weeks \} \?: todayWeek\?\.takeIf \{ it in weeks \}/, "选择有效教学周");
assert.match(schedulePanel, /onDayClick = \{ selectedDay = it; selectView\("day"\) \}/, "用户选择星期");
assert.match(schedulePanel, /rememberPagerState\(initialPage = index, pageCount = \{ weeks\.size \}\)/, "保留真实周次翻页");
assert.match(schedulePanel, /courses\.filter \{ it\.day == selectedDay \}/, "日视图按用户选择过滤");
assert.match(schedulePanel, /val selected = lessons\.firstOrNull \{ it\.id == selectedId \}/, "详情只用真实课程身份");
assert.match(schedulePanel, /UpstreamCourseDetailSheet\(/, "统一周日详情");
assert.match(schedulePanel, /LocalScheduleEditor\.upsert\(lessons, editingId, replacement, editingWeek\.takeIf \{ only \}\)/, "本机编辑保留周次范围");
assert.match(schedulePanel, /LocalScheduleEditor\.delete\(lessons, requireNotNull\(editingId\), editingWeek\.takeIf \{ only \}\)/, "本机删除保留周次范围");
assert.match(scheduleHeaderSource, /GlassOptionWheelDialog\(weeks\.map \{ "第 \$it 周" \}, weeks\.indexOf\(currentWeek\)\.coerceAtLeast\(0\)/, "周次轮盘定位当前周");
assert.match(scheduleHeaderSource, /weeks\.getOrNull\(it\)\?\.let\(onWeekSelect\)/, "有效周次选择");
assert.match(scheduleHeaderSource, /onAddClick\(\)/, "新增课程可达");
assert.match(scheduleHeaderSource, /onSettingsClick\(\)/, "课表设置可达");
assert.match(timetableSettings, /internal fun ScheduleSettingsFeature\(/, "唯一设置页");
assert.match(timetableSettings, /Modifier\.fillMaxSize\(\)\.verticalScroll/, "共享页面内布局");
assert.match(timetableSettings, /LiquidSegmentedControl\(options = listOf\("周", "日"\)/, "上游分段控件");
assert.match(timetableSettings, /selectedIndex = if \(defaultView == "day"\) 1 else 0/, "真实偏好");
assert.match(timetableSettings, /LocalSemesterConfigStore\.saveViewMode\(context, defaultView\)/, "保存视图偏好");
assert.match(timetableSettings, /SemesterCalendar\.parse\(dateText, weeksText, periodsText\)/, "学期数据严格验证");
assert.match(timetableSettings, /actions\.saveScheduleSettings\(config, periodCount\)/, "已有保存接口");
assert.match(timetableSettings, /date\.dayOfWeek != java\.time\.DayOfWeek\.MONDAY/, "第一周起始必须周一");
assert.match(timetableSettings, /GlassDatePickerDialog\(/, "统一日期选择");
assert.match(timetableSettings, /options = \(1\.\.35\)\.map/, "教学周有效范围");
assert.match(timetableSettings, /options = \(1\.\.24\)\.map/, "每天节数范围");
assert.match(timetableSettings, /onConfirm = \{ periodCount = it \+ 1; accepted\(\) \}/, "确认节数");
assert.match(timetableSettings, /GlassTimeRangePickerDialog\(/, "统一起止时间轮盘");
assert.match(timetableSettings, /java\.time\.LocalTime\.parse\(end\)\.isAfter\(java\.time\.LocalTime\.parse\(start\)\)/, "结束必须晚于开始");
assert.match(timetableSettings, /updatePeriod\(period, start, end\)/, "只更新目标节次");
assert.match(timetableSettings, /maxOf\(periodCount, rows\.keys\.maxOrNull\(\) \?: 1\)/, "减少显示节数不丢失原作息");
assert.match(timetableSettings, /onClick = actions\.importTimetable/, "真实导入");
assert.match(timetableSettings, /onClick = actions\.exportTimetable/, "真实导出");
assert.match(timetableTimeWheel, /fun GlassWheelColumn\(/, "上游共享轮盘");
assert.match(timetableTimeWheel, /fun GlassTimeRangePickerDialog\(/, "统一时间范围选择");
assert.match(timetableTimeWheel, /fun GlassOptionWheelDialog\(/, "统一数字轮盘");
assert.match(timetableTimeWheel, /LazyColumn\(/, "惰性滚动轮盘");
assert.match(courseEditor, /onSave\(it, weekOnly\)/, "保存真实课程");
assert.match(courseEditor, /onDelete\(weekOnly && canScope\)/, "仅删除选中周");
assert.match(courseEditor, /GlassFilterCapsule\(label = label\.toString\(\), selected = weekday == key, onClick = \{ weekday = key \}, enabled = !busy\)/, "课程星期交互");
assert.match(courseEditor, /enabled = !weekOnly && !busy/, "提交时锁定周次");
assert.match(courseEditor, /GlassFormField\(/, "统一表单");
assert.match(courseDetail, /LocalCourseReminders\.enabledForCourse\(context, course\.id\)/, "课程提醒来源");
assert.match(courseDetail, /LocalCourseReminders\.setCourseEnabled\(context, course\.id, enabled\)/, "课程提醒持久化");
assert.match(courseDetail, /ActivityResultContracts\.RequestPermission\(\)/, "按需运行时授权");
assert.match(courseDetail, /LocalCourseReminders\.canNotify\(context\)/, "通知权限核验");
assert.match(courseDetail, /LocalCourseReminders\.reconcileAfterSave\(/, "提醒调度");
assert.match(nativeWeekGrid, /fun TimetableLayout\(/, "直接上游网格");
assert.match(nativeWeekGrid, /private fun timetableEntries\(courses: List<ScheduleCourseUi>\)/, "复用重叠分组");
assert.match(nativeWeekGrid, /if \(entry\.courses\.size == 1\) onCourseClick\(entry\.courses\.first\(\)\) else selectedGroup = entry\.courses/, "重叠课程选择不虚构原始记录");
assert.match(nativeWeekGrid, /onClick = \{ selectedGroup = null; onCourseClick\(course\) \}/, "点击原始课程");
assert.match(nativeWeekGrid, /val days = columns\.size/, "按实际日期列布局");
assert.match(academicGradeArea, /cloud\.grades\.takeIf \{ owner != null && cloud\.gradesOwner == owner \}/, "成绩所有者校验");
assert.match(academicGradeArea, /cloud\.exams\.takeIf \{ owner != null && cloud\.examsOwner == owner \}/, "考试所有者校验");
assert.match(academicGradeArea, /val semesterListState = rememberLazyListState\(\)/, "上游独立滚动状态");
assert.match(academicGradeArea, /val overallListState = rememberLazyListState\(\)/, "总体独立滚动");
assert.match(academicGradeArea, /val examListState = rememberLazyListState\(\)/, "考试独立滚动");
assert.match(academicGradeArea, /val travel: Dp get\(\) = \(expanded - collapsed\)\.coerceAtLeast\(1\.dp\)/, "上游测量折叠行程");
assert.match(academicGradeArea, /非实时/, "成绩考试快照口径");
assert.match(academicGradeArea, /if \(tab == 2\) actions\.readExams\(\) else actions\.readGrades\(\)/, "真实只读接口");
assert.match(academicGradeArea, /actions\.navigate\(AppRoute\.CONNECTIONS\)/, "数据连接入口");
assert.doesNotMatch(schedulePanel, /VerifiedLesson\(|HttpURLConnection|openConnection|WebView|addJavascriptInterface|CookieManager|deriveScheduleUiState\(state\)|state\.lessons|client\.refresh\(\)/);
assert.doesNotMatch(timetableSettings, /reminderEnabled: Boolean|wantReminders|android\.app\.(DatePickerDialog|TimePickerDialog)|\bTimePicker\(/);
assert.doesNotMatch(nativeWeekGrid, /电路基础|E103|演示数据|faker/i);
assert.doesNotMatch(academicGradeArea, /DemoData|mockGrades|fakeExam/);
assert.match(profileSettings, /LocalCourseReminders\.setEnabled\(context, value\)/);
assert.match(profileSettings, /ActivityResultContracts\.RequestPermission\(\)/);
assert.match(profileSettings, /ACTION_APP_NOTIFICATION_SETTINGS/);
assert.match(rebuildShell, /saveScheduleSettings = \{ calendar, sections ->[\s\S]*?LocalCourseReminders\.reconcileAfterSave\(/);
assert.match(rebuildShell, /val showPrimary = route\.parent == null/);
assert.match(rebuildShell, /BackHandler\(enabled = route\.parent != null \|\| routeTrail\.isNotEmpty\(\)\)/);
assert.match(settingsRoutes, /AppRoute\.SCHEDULE_SETTINGS -> ScheduleSettingsFeature\(data, actions\)/);
assert.match(settingsRoutes, /route == AppRoute\.GRADES \|\| route == AppRoute\.EXAMS/);
assert.match(timetablePrefs, /fun loadViewMode\(context: Context\)/);
assert.match(timetablePrefs, /fun saveViewMode\(context: Context, mode: String\)/);
assert.match(timetablePrefs, /putString\(VIEW_MODE_KEY, mode\)\.apply\(\)/);
assert.match(timetablePrefs, /fun loadSections\(context: Context\)/);
assert.match(timetablePrefs, /fun saveSections\(context: Context, count: Int\)/);
assert.match(segmentControl, /\.weight\(1f\)[\s\S]*?role = Role\.Tab/);

assert.match(messageUi, /GlassFormField\(/, '消息搜索复用共享表单');
assert.match(timetableSettings, /InsetGroupedSection\(header = "基础设置"\)/);
assert.match(timetableSettings, /InsetGroupedSection\(header = "节次时间"/);
assert.match(timetableSettings, /InsetGroupedSection\(header = "课表文件"\)/);
assert.doesNotMatch(timetableSettings, /\b(?:fun|class)\s+SettingsSurface\b/);


// 消息中心：公开公告与微信待审核分开，失败/过期要显式提示，不伪造正文。
const messageCenter = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/MessageCenterFeature.kt');
const stepEditor = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/TaskExecutionPanel.kt');
const importPreview = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/TimetableImportPreviewSheet.kt');
const bottomActions = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/AppDialog.kt');
const wechatReview = messageCenter;
const taskBoard = localTaskUi;
const reviewRoute = read('functions/api/v1/wechat/candidates/[id].js');
assert.match(messageCenter, /fun filterNotices\(/, "公告检索");
assert.match(messageCenter, /filterNotices\(state\.notices, query, filter\)\.distinctBy \{ it\.url \}/, "真实公告与去重");
assert.match(messageCenter, /pendingWechatLabel\(state\.pendingWechatCount, state\.authenticated, state\.wechatError\)/, "待审数量含账户及错误状态");
assert.match(messageCenter, /state\.publicError != null && state\.notices\.isNotEmpty\(\)/, "明确缓存数据");
assert.match(messageCenter, /if \(state\.noticesPartial\) item \{ AppNotice/, "部分读取失败");
assert.match(messageCenter, /MessagePhase\.NotConnected -> item \{ SystemEmptyState\("尚未读取公告"/, "未读取不同于空列表");
assert.match(messageCenter, /MessagePhase\.Failed -> item \{ AppNotice\(phase\.message, error = true\)/, "真实错误语义");
assert.match(messageCenter, /LiquidSegmentedControl\(listOf\("官方通知", "微信审核"\)/, "独立来源");
assert.match(messageCenter, /state = if \(showWechat\) reviewList else officialList/, "各来源滚动状态");
assert.match(messageCenter, /InsetGroupedSection\(header = "微信候选"\)/, "微信来源独立分组");
assert.match(messageCenter, /if \(state\.authenticated\) \{\s*items\(state\.wechatCandidates, key = \{ it\.id \}\)/, "仅账户内候选");
assert.match(messageCenter, /var filter by rememberSaveable \{ mutableStateOf\(MSG_FILTER_ALL\) \}/, "恢复筛选状态");
assert.match(messageCenter, /var openedUrl by rememberSaveable/, "恢复真实公告身份");
assert.match(messageCenter, /state\.notices\.firstOrNull \{ it\.url == openedUrl \}/, "选中真实公告");
assert.match(messageCenter, /maxWidth >= 840\.dp && !showWechat/, "宽屏双栏与手机统一内容");
assert.match(messageCenter, /AppDialog\(usePortal = true, onDismissRequest = \{ openedUrl = null \}, title = \{ Text\("公告详情"\) \}/, "共用公告浮层");
assert.match(messageCenter, /Text\(notice\.title, style = MaterialTheme\.typography\.titleMedium\)/, "真实标题");
assert.match(messageCenter, /正文请在学校官网查看/, "无正文数据不得编造");
assert.match(messageCenter, /Button\(onClick = \{ openArticle\(notice\.url\) \}\)/, "官网原文入口");
assert.match(messageCenter, /GlassFormField\(query, \{ query = it\.take\(60\) \}/, "共享检索表单");
assert.match(messageCenter, /GlassFilterCapsule\(label = option\.label, selected = filter == option\.key/, "共享公告筛选");
assert.match(messageCenter, /fun pendingWechatShortLabel\(/, "区分待审计数未知");
assert.match(messageCenter, /key\(state\.ownerId, candidate\.id\)/, "候选详情按账号及ID隔离");
assert.match(messageCenter, /val fresh = state\.authenticated && state\.pendingWechatCount != null && state\.wechatError == null/, "旧候选不可审核");
assert.match(messageCenter, /reviewingCandidate\?\.let \{ candidate ->[\s\S]*?WechatCandidateReview\(candidate, state, actions,/, "审核使用同一个弹窗");
assert.match(messageCenter, /if \(pendingDecision == null\) \{/, "二次确认而非独立窗口");
assert.match(messageCenter, /Button\(enabled = fresh && !reviewing && !failed,[\s\S]*?actions\.reviewWechatCandidate\(item\.id, decision, owner\)/, "确认后才通过原接口提交");
assert.match(messageCenter, /没有摘要，请核对微信原消息/, "候选摘要缺失提示");
assert.match(messageCenter, /核对原消息后逐条确认/, "人工审核");
assert.match(stepEditor, /AppDialog\(/, "步骤删除共享弹窗");
assert.match(stepEditor, /dao\.removeStep\(root, step\.id\)/, "真实本机删除");
assert.match(stepEditor, /onDismissRequest = \{ if \(!busy\) deleting = null \}/, "写入期间禁止误关闭");
assert.match(importPreview, /if \(decreased && !confirmedReduced\) confirmedReduced = true/, "导入减少二次确认");
assert.match(importPreview, /HorizontalDivider\(color = MaterialTheme\.colorScheme\.outlineVariant/, "共享主题分隔线");
assert.match(importPreview, /onDismissRequest = \{ if \(!saving\) onDismiss\(\) \}/, "导入期间禁止关闭");
assert.match(bottomActions, /DialogProperties\(usePlatformDefaultWidth = false\)/, "原生窗口回退");
assert.match(bottomActions, /val dialogWidth = minOf\(/, "动态窗口宽度");
assert.match(bottomActions, /window\?\.setDimAmount\(DialogScrimAlpha\)/, "唯一遮罩");
assert.match(bottomActions, /host\.show\(onDismiss = \{ currentDismiss\(\) \}\)/, "同窗口弹窗宿主");
assert.match(bottomActions, /host\.dismiss\(handle, notify = false\)/, "退出释放弹窗");
assert.match(bottomActions, /semantics \{ paneTitle = "对话框" \}/, "读屏对话框语义");
assert.match(localTaskUi, /private fun LocalTaskSourcePicker\(/, "任务分类唯一选择器");
assert.match(localTaskUi, /TASK_SOURCES\.forEach \{ option ->/, "真实分类");
assert.match(localTaskUi, /dao\.insert\(LocalTask\(/, "本机写入");
assert.match(localTaskUi, /onDismissRequest = \{ if \(!saving\) onDismiss\(\) \}/, "保存期间禁止关闭");
assert.match(localTaskUi, /GlassDatePickerDialog\(/, "统一日期选择");

assert.doesNotMatch(messageCenter, /HttpURLConnection|openConnection|WebView|addJavascriptInterface|CookieManager|document\.cookie|READ_SMS|READ_CONTACTS/);
assert.doesNotMatch(messageCenter, /AcademicNotice\(|查看全文|全部已读|提取为任务|标记为已读|AlertDialog\(|ModalBottomSheet\(/);
assert.doesNotMatch(stepEditor, /AlertDialog\(/);
assert.doesNotMatch(importPreview, /XiaoxuCard\(/);
assert.doesNotMatch(bottomActions, /LocalConfiguration\.current\.screenHeightDp/);
assert.doesNotMatch(localTaskUi, /HttpURLConnection|openConnection|WebView|addJavascriptInterface|CookieManager/);
assert.match(cloud, /data class WechatCandidate/);
assert.match(cloud, /wechatCandidates = candidates \?: state\.wechatCandidates/);
assert.match(cloud, /reviewStatus"\) != "needs_review"/);
assert.match(cloud, /suspend fun reviewWechatCandidate\(/);
assert.match(cloud, /\/api\/v1\/wechat\/candidates\/\$candidateId/);
assert.match(cloud, /JSONObject\(\)\.put\("decision", decision\)/);
assert.match(cloud, /reviewWechatCandidate[\s\S]*?sessionEpoch/);
assert.match(cloud, /clearAccountSnapshots\(/);
assert.match(reviewRoute, /authorize\(context\)/);
assert.match(reviewRoute, /sec-fetch-site/);
assert.match(reviewRoute, /reviewWechatCandidate\(auth\.db,auth\.userId/);

const taskBackup = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalTaskBackup.kt');
const taskBackupPanel = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/LocalTaskBackupPanel.kt');
assert.match(settingsRoutes, /AppRoute\.TASK_BACKUP -> LazyColumn \{ item \{ LocalTaskBackupPanel\(\) \} \}/,
  '本机备份与恢复只能由账户与数据二级页面打开');
assert.match(settingsIndex, /"任务备份",[\s\S]*?actions\.navigate\(AppRoute\.TASK_BACKUP\)/,
  '本机备份仍可通过唯一入口进入');
const aboutPage = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/AboutFeature.kt');
assert.match(settingsRoutes, /AppRoute\.ABOUT -> AboutFeature\(\{ actions\.navigate\(AppRoute\.LICENSES\) \}/,
  '关于校序应使用单独的品牌信息页面');
assert.match(aboutPage, /AppGroupedEntry\("开源许可与致谢", showDivider = false, onClick = onOpenLicenses\)/,
  '开源许可从关于页进入，不在设置首页重复');
assert.match(aboutPage, /InsetGroupedSection\(header = "项目"\)/,
  '关于页保留开源署名入口');
assert.match(settingsIndex, /header = "数据与安全"/,
  '账户与备份入口须保留在设置首页同一分组，不改变二级路由');
assert.doesNotMatch(main, /if \(showLocalBackup\) item|CompactActionRow\("第二课堂记录"/,
  '移除退役的旧工具入口和向下展开式备份');
assert.doesNotMatch(settingsIndex, /"工具" ->|第二课堂记录/,
  '业务记录不应混入设置');
assert.doesNotMatch(main, /云端第二课堂个人快照（可选）|按需使用系统文件选择器，不自动上传本机任务/,
  '我的页面默认不再展示多余解释性小字');
assert.doesNotMatch(main, /item \{ Heading\("我的",/,
  '个人中心已有资料卡，不能叠加旧版重复主标题');
const backupSource = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/LocalTaskBackupPanel.kt');
assert.doesNotMatch(backupSource, /安装 V0\.6/,
  '不得保留过期版本号导致签名升级与本机数据提示误导');
assert.match(backupSource, /backup\.mergeFrom\(uri\)/,
  '原有备份文件恢复操作不得在 UI 清理中删除');
assert.match(db, /suspend fun snapshot\(\): List<LocalTask>/);
assert.match(taskBackup, /xiaoxu\/local-tasks-backup-v1/);
assert.match(taskBackup, /xiaoxu\/local-tasks-backup-v2/);
assert.match(taskBackup, /xiaoxu\/local-tasks-backup-v3/);
assert.match(taskBackup, /wouldCreateDependencyCycle/);
assert.match(taskBackup, /schema == FORMAT_V1 \|\| schema == FORMAT_V2/);
assert.match(taskBackup, /"source", "due"/);
assert.match(taskBackup, /validDue/);
assert.match(taskBackup, /source = source, due = due/);
assert.match(taskBackup, /withTransaction/);
assert.match(taskBackup, /MAX_BYTES/);
assert.match(taskBackup, /MAX_TASKS/);
assert.match(taskBackup, /seen\.add\(key\)/);
assert.match(taskBackup, /data class BackupTaskMatch[\s\S]*?val completed: Boolean[\s\S]*?val source: String\?[\s\S]*?val due: String\?/,
  'V3 备份身份不得仅依靠标题和创建时刻，还须包含完成状态、来源和日期');
assert.match(taskBackup, /available\[task\.backupMatch\(\)\]\?\.pollFirst\(\)/,
  '恢复前置依赖必须优先匹配完整任务字段以避免同名同时间记录错链');
assert.match(taskBackup, /tryAddDependency\(from, to\)/);
assert.match(taskBackupPanel, /ActivityResultContracts\.CreateDocument/);
assert.match(taskBackupPanel, /ActivityResultContracts\.OpenDocument/);
assert.match(taskBackupPanel, /LocalTaskBackup/);
assert.match(taskBackupPanel, /XiaoxuGroupedSection\(title = "任务数据"\)/,
  '备份数据分组须保持');
assert.match(taskBackupPanel, /XiaoxuGroupedSection\(title = "安全与说明"\)/,
  '安全说明需分组呈现');
assert.match(backupSource, /onBusyChange\(true\)/,
  '本机备份开始时需向父页面通知繁忙状态');
assert.match(backupSource, /onBusyChange\(false\)/,
  '本机备份完成后需解除按钮锁定');
assert.match(taskBackupPanel, /if \(uri != null && !busy\) \{/,
  '文件操作期间禁止并发备份');
assert.match(taskBackupPanel, /enabled = !busy,/,
  '真实文件操作期间锁定按钮');
assert.match(settingsRoutes, /AppRoute\.TASK_BACKUP -> LazyColumn \{ item \{ LocalTaskBackupPanel\(\) \} \}/,
  '独立二级备份页复用唯一备份面板');
assert.doesNotMatch(taskBackup + taskBackupPanel, /CookieManager|addJavascriptInterface|SCHOOL_SESSION_KEY|OWNER_LOGIN_SECRET|READ_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE/);
assert.doesNotMatch(manifest, /READ_EXTERNAL_STORAGE|WRITE_EXTERNAL_STORAGE|MANAGE_EXTERNAL_STORAGE/);

/* Dedicated Qingguo importer is owned by main; only Chaoxing and second-class
 * use the generic official WebView reader. This is NOT a trusted school sync. */
const localNative = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/NativeSchoolConnectActivity.kt');
const nativeRecords = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/NativeSchoolRecords.kt');

// User-activated path-only observation of the school's genuine activity list:
// no automatic page instrumentation, session export or guessed catalog URL.
const catalogObserver = read('apps/xiaoxu-android/app/src/main/assets/ncwu_secondclass_source_observer.js');
assert.match(localNative, /观测活动列表请求（手动）/);
assert.match(localNative, /setOnClickListener \{ toggleCatalogObserver\(\) \}/);
assert.match(localNative, /val outcomes = item\.optJSONArray\("outcomes"\)/);
assert.match(localNative, /HTTP \$statusCode\/\$familyName/);
assert.match(localNative, /未获得响应元信息/);
assert.match(localNative, /assets\.open\("ncwu_secondclass_source_observer\.js"\)/);
assert.match(localNative, /if \(provider == "second_class"\) root\.addView\(catalogProbe\)/);
assert.match(catalogObserver, /window\.fetch=wrappedFetch/);
assert.match(catalogObserver, /XMLHttpRequest\.prototype\.open=wrappedOpen/);
assert.match(catalogObserver, /window\.fetch===wrappedFetch/);
// Only the status and response Content-Type of the existing school request
// are readable. Do not allow any response body or other response header.
assert.match(catalogObserver, /response\.headers&&response\.headers\.get\('content-type'\)/);
assert.match(catalogObserver, /getResponseHeader\('content-type'\)/);
const metadataOnly = catalogObserver
  .replace("response.headers&&response.headers.get('content-type')", "")
  .replace("getResponseHeader('content-type')", "");
assert.doesNotMatch(metadataOnly, /document\.cookie|localStorage|sessionStorage|response\.text\(|response\.json\(|headers\.get\(|getResponseHeader\(|XMLHttpRequest\.prototype\.send\s*=/);

const localSchoolUi = panels;
assert.match(manifest, /android:name="\.NativeSchoolConnectActivity"[\s\S]*?android:exported="false"/);
assert.match(main, /NativeSchoolConnectActivity\.EXTRA_PROVIDER/);
assert.match(main, /provider == "ncwu"/);
assert.match(main, /LocalQingguoImportActivity::class\.java/);
assert.match(rebuildShell, /authorize = startSchoolAuthorization, connectLocal = openLocalSchool/);
assert.match(main, /nativeSchoolLauncher = registerForActivityResult/);
assert.match(main, /ActivityResultContracts\.StartActivityForResult/);
assert.match(main, /nativeSchoolLauncher\.launch\(/);
assert.match(main, /result\.resultCode == Activity\.RESULT_OK/);
assert.match(main, /LocalSchoolImportReceipt\(/);
assert.match(main, /localCaptureReturn = lastLocalCapture/);
assert.match(rebuildShell, /LaunchedEffect\(localCaptureReturn\)/);
assert.match(rebuildShell, /goTo\(if \(receipt\.provider == "chaoxing"\) AppRoute\.TASKS else AppRoute\.SECOND_CLASS\)/);
assert.match(rebuildShell, /cloud\.completeSchoolReturn\(provider\)/);
assert.match(manifest, /android:scheme="xiaoxu" android:host="\$\{schoolAuthHost\}"/);
assert.match(appGradle, /schoolAuthHost: 'school-auth-return'/,
  'The normal application must retain its existing authorization return URI');
assert.match(appGradle, /resValue 'string', 'school_auth_return_host', 'school-auth-return'/,
  '正式版回跳使用原 Host');
assert.match(appGradle, /resValue 'string', 'school_auth_return_host', 'school-auth-return-unified-qa'/,
  '独立 QA 回跳 Host 不得与正式版或旧 QA 混用');
assert.match(appGradle, /applicationIdSuffix '\.unifiedqa'/,
  'QA installs must have a distinct Android application id');
assert.match(appGradle, /manifestPlaceholders\.schoolAuthHost = 'school-auth-return-unified-qa'/,
  'QA must not steal the normal application authorization deep link');
assert.match(ci, /xiaoxu-unified-native-qa-isolated/,
  'QA APK must be uploaded separately, not replace the normal build artifact');
const handoff = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/SchoolAuthReturn.kt');
assert.match(handoff, /SecureRandom\(\)\.nextBytes\(bytes\)/);
assert.match(handoff, /expectedReturnHost = context\.applicationContext\.getString\(R\.string\.school_auth_return_host\)/,
  '独立 QA 与正式版必须分别验证配置的安全回跳 Host');
assert.match(handoff, /uri\.host != expectedReturnHost/,
  'Host 验证不得硬编码到正式版，避免 QA 误收或拒绝有效回跳');
assert.match(handoff, /MessageDigest\.isEqual/);
assert.match(handoff, /ownerBinding != savedOwner/);
assert.match(handoff, /prefs\.edit\(\)\.clear\(\)\.commit\(\)/);
assert.doesNotMatch(handoff, /CookieManager|document\.cookie|SCHOOL_SESSION_KEY|WebView/);
const appReturn = read('public/xiaoxu-android-return.js');
assert.match(read('public/index.html'), /xiaoxu-android-return\.js/);
assert.match(appReturn, /sessionStorage\.removeItem\(KEY\)/);
assert.match(appReturn, /xiaoxu:\/\/school-auth-return/);
assert.doesNotMatch(appReturn, /document\.cookie|schoolPassword|CAS_TICKET/);
assert.match(localSchoolUi, /header = "本机学校读取"/);
assert.match(localSchoolUi, /actions\.connectLocal\("ncwu"\)/);
assert.match(localSchoolUi, /actions\.connectLocal\("chaoxing"\)/);
assert.match(localSchoolUi, /actions\.connectLocal\("second_class"\)/);
assert.match(localSchoolUi, /localRows\.filter \{ it\.provider == provider && it\.kind != "account_binding" \}/);
assert.match(localSchoolUi, /stored\.maxOfOrNull \{ it\.capturedAt \}/);
assert.match(localSchoolUi, /已保存 \$\{stored\.size\} 条/);
assert.match(localSchoolUi, /AppGroupedEntry\("查看本机课表", onClick = \{ actions\.navigate\(AppRoute\.SCHEDULE\) \}\)/);
assert.match(localSchoolUi, /AppGroupedEntry\("查看作业与任务", onClick = \{ actions\.navigate\(AppRoute\.TASKS\) \}\)/);
assert.match(localSchoolUi, /AppGroupedEntry\("查看课程进度", onClick = \{ actions\.navigate\(AppRoute\.STUDY_PROGRESS\) \}\)/);
assert.match(localSchoolUi, /actions\.navigate\(AppRoute\.SECOND_CLASS\)/);
assert.match(rebuildShell, /data class LocalSchoolImportReceipt\(/);
assert.match(appWorkspace, /rememberSnapshotRows\(nativeDao\) \{ nativeDao\.observe\(\) \}/);
assert.match(settingsRoutes, /lessons\.filter \{ it\.weekday == day && week in it\.weeks \}/);
assert.match(settingsRoutes, /all\.count\(::isActionableWorkspaceTask\)/);
assert.match(settingsRoutes, /all\.count\(::requiresChaoxingVerification\)/);
assert.match(workspaceLogic, /nativeChaoxingWorkSourceId\(record\)/);
assert.match(workspaceLogic, /localEvidence\.filterKeys \{ it !in represented \}/);
assert.match(workspaceLogic, /internal fun searchWorkspaceTasks\(/);
assert.match(workspaceLogic, /"pending", "now" -> isActionableWorkspaceTask\(task\)/);
assert.match(workspaceLogic, /"verify" -> requiresChaoxingVerification\(task\)/);
assert.match(taskWorkspaceForComposerGate, /val visible = remember\(combined, filter, query, sourceFilter, onlyThisWeek, today\)/);
assert.match(taskWorkspaceForComposerGate, /combined\.count\(::isActionableWorkspaceTask\)/);
assert.match(taskWorkspaceForComposerGate, /"verify" to "待核验"/);
assert.match(taskWorkspaceForComposerGate, /items\(visible, key = \{ it\.key \}\)/);
assert.match(taskWorkspaceForComposerGate, /val cloud = data\.cloud\.tasks\.takeIf \{ data\.cloud\.authenticated && data\.cloud\.tasksFresh \}\.orEmpty\(\)/);
assert.match(taskWorkspaceForComposerGate, /云端任务尚未核验最新状态/);
assert.match(taskWorkspaceForComposerGate, /showDivider = false, onClick = \{ detailKey = task\.key \}/);
assert.doesNotMatch(taskWorkspaceForComposerGate, /WechatCandidateReview\(|PosterDemo|visible\.take\(|visible\.size > 80/);
assert.doesNotMatch(workspace, /Log\.[diwev]\(|println\(/);
assert.doesNotMatch(localSchoolUi, /CardDefaults\.cardColors|CardDefaults\.cardElevation/);
assert.match(readContracts, /本机导入（待本人核对）/);
assert.match(localNative, /EXTRA_RETURN_KIND/);
assert.match(localNative, /EXTRA_SAVED_COUNT/);
assert.match(localNative, /setResult\(Activity\.RESULT_OK, Intent\(\)/);
assert.match(main, /if \(permitted && count in 1\.\.5000\)/);
assert.match(taskWorkspaceForComposerGate, /items\(visible, key = \{ it\.key \}\)/);
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
assert.match(localNative, /if \(provider == "chaoxing"\) loaded && chaoxingReadJob\?\.isActive != true/);
assert.match(localNative, /override fun onReceivedError\(/);
assert.match(localNative, /if \(request\.isForMainFrame\) \{\s*invalidateCapture\(\)\s*pendingOfficialCatalogRead = false\s*pageFailed = true/);
assert.match(localNative, /private fun invalidateCapture\(\) \{\s*captureEpoch\+\+\s*scoreSaveJob\?\.cancel\(\)/);
assert.match(localNative, /scoreSaveJob = lifecycleScope\.launch/);
assert.match(localNative, /currentCoroutineContext\(\)\.ensureActive\(\)/);
assert.match(localNative, /extract\.isEnabled = loaded && !pageFailed &&/);
assert.match(localNative, /extractionAllowed\(provider, browser\.url\)/);
assert.match(localNative, /handler\.cancel\(\)/);
assert.match(localNative, /setAcceptThirdPartyCookies\(this, provider == "chaoxing"\)/);
assert.match(localNative, /mixedContentMode = WebSettings\.MIXED_CONTENT_NEVER_ALLOW/);
assert.match(localNative, /LocalTaskDatabase\.open\(applicationContext\)/);
assert.match(db, /abstract fun nativeSchoolRecords\(\): NativeSchoolRecordDao/);
assert.match(nativeRecords, /@Transaction/);
assert.doesNotMatch(localNative + nativeRecords + localSchoolUi,
 /addJavascriptInterface|document\.cookie|CookieManager\.getCookie|OWNER_LOGIN_SECRET|SCHOOL_SESSION_KEY/);
assert.match(appGradle, /versionName '0\.8\.16'/, '正式版本名必须明确');
assert.match(appGradle, /versionCode 37\b/, '正式版版本码应高于已发布 RC');

// The removed Chaoxing DOM parser has been replaced by Jsoup JVM fixtures.
/* Local NCWU import: an actual DOM parsing smoke test, not an official account login. */
const importer = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/LocalQingguoImportActivity.kt');
const localStore = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalScheduleStore.kt');
const script = read('apps/xiaoxu-android/app/src/main/assets/ncwu_qingguo_local.js');
assert.match(manifest, /android:name="\.LocalQingguoImportActivity"[\s\S]*?android:exported="false"/);
assert.match(main, /if \(provider == "ncwu"\) \{\s*startActivity\(Intent\(this@MainActivity, LocalQingguoImportActivity::class\.java\)\)/,
  '本机青果入口仍通过数据连接中心的官方来源选择器可达');
assert.doesNotMatch(main, /\bopenLocalImport\b|\bSchedulePage\(/,
  '旧课表转发回调与空代理页面不应继续存在');
assert.match(settingsRoutes, /ScheduleFeature\(data, actions,/,
  '课表路由应直接渲染本机课表');
// Timetable persistence must not parse on the cold-start UI thread.
const startup = main.slice(main.indexOf('override fun onCreate'), main.indexOf('private fun importTimetableFile'));
assert.doesNotMatch(startup, /LocalScheduleStore\.load\(/);
assert.match(main, /withContext\(Dispatchers\.IO\)\s*\{\s*LocalScheduleStore\.load\(applicationContext\)/);
assert.match(main, /if \(readSerial == localScheduleReadSerial\) \{/);
assert.match(main, /localLessons = lessons\s*localScheduleRevision\+\+/,
  '日历设置变化时即使课程数据相同也必须更新本机课表视图');
assert.match(main, /if \(LocalCourseReminders\.enabled\(applicationContext\)\)/,
  '提醒维护必须默认跳过未启用的用户');
assert.match(cloud, /coroutineScope\s*\{\s*launch\s*\{\s*refreshNotices\(\)\s*\}\s*launch\s*\{\s*refreshPrivate\(\)/);
assert.match(cloud, /withContext\(Dispatchers\.IO\) \{ session\.load\(\) \}/);
assert.match(readContracts, /本机导入/);
assert.match(readContracts, /待本人核对/);
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
// Active upstream design, navigation and home contracts. Historical pixel constants
// belong to the deleted V1 design system, not the current Compose renderer.
const design = [glassKit, pageScaffold, windowHost, dialogHost, uiTheme, systemUi, sharedFrame].join('\n');
const primaryNavigation = rebuildShell;
assert.match(glassKit, /private val GlassCardShape = RoundedCornerShape\(24\.dp\)/, "上游分组统一形状");
assert.match(glassKit, /\.clip\(GlassCardShape\)[\s\S]*?\.border\(0\.5\.dp, glassBorderColor\(\), GlassCardShape\)/, "同源轻边");
assert.match(glassKit, /internal fun glassSurfaceColor\(\): Color/, "统一壁纸可读材质");
assert.match(glassKit, /rememberGlassAccessibilityMode\(\)\.highContrast/, "高对比度回退");
assert.match(uiTheme, /CourseSelectorTheme\(content = content\)/, "直接上游主题");
assert.match(dialogHost, /val shape = RoundedCornerShape\(28\.dp\)/, "弹层圆角");
assert.match(windowHost, /LocalGlassWindowOwner\.current === view && !isolatePage/, "同窗口不重复创建背景");
assert.match(windowHost, /rememberCombinedBackdrop\(wallpaper, page\)/, "弹窗共用背景");
assert.match(windowHost, /page != null && dialogs\.hasBlockingSurface/, "仅需要时捕获页面");
assert.match(windowHost, /DialogHost\(dialogs\)/, "唯一弹窗宿主");
assert.match(motionHost, /Box\(modifier\.fillMaxSize\(\)\.clipToBounds\(\)\)/, "边界裁剪");
assert.match(motionHost, /alpha = if \(retainOutgoing\) frame\.alpha else 1f/, "不叠加旧页透明残影");
assert.match(motionHost, /fun pageSpring\(\)/, "复用统一动效");
assert.match(rebuildShell, /activePage = route\.ordinal, retainOutgoing = false/, "只组合当前页面");
assert.match(rebuildShell, /savedPages\.SaveableStateProvider\(pageRoute\.name\)/, "按路由保存状态");
assert.match(rebuildShell, /val accountKey = if \(data\.cloud\.authenticated\) "cloud:" \+ data\.cloud\.ownerId\.orEmpty\(\) else "local"/, "账号隔离");
assert.match(rebuildShell, /val showPrimary = route\.parent == null/, "子页隐藏底栏");
assert.match(rebuildShell, /\.padding\(bottom = if \(!wide && showPrimary\) RebuildDockMetrics\.contentReserve\(\) else 0\.dp\)/, "导航占位同步");
assert.match(rebuildShell, /if \(!wide && showPrimary\) RebuildCapsuleDock\(/, "唯一手机底栏");
assert.match(rebuildShell, /if \(wide\) NavigationRail/, "宽屏侧栏");
assert.match(navDock, /NavBarMetrics\.blockPadding\(\) \* 2 \+ NavBarMetrics\.trackHeight\(\)/, "导航与内容占位一致");
assert.match(navDock, /rememberCombinedBackdrop\(backdrop, tabsBackdrop\)/, "上游光学采样");
assert.match(navDock, /haptics\.performHapticFeedback\(HapticFeedbackType\.ContextClick\)/, "真实切换触觉反馈");
assert.match(navDock, /else iconPlayback\.replay\(index\)/, "重复点击仅重播图标");
assert.match(navDock, /private fun FallbackNavigationBar\(/, "设备不支持时降级");
assert.match(sharedFrame, /widthIn\(max = if \(wideContent\) AppSpacing\.workspaceMaxWidth else AppSpacing\.formMaxWidth\)/, "统一内容宽度");
assert.match(sharedFrame, /SystemTopBar\(title = title, collapseFraction = if \(compactHeader\) 1f else lift/, "同源顶栏");
assert.match(settingsRoutes, /private fun HomeFeature\(/, "首页真实入口");
assert.match(settingsRoutes, /if \(maxWidth >= 680\.dp\)/, "响应式双栏");
assert.match(settingsRoutes, /homeCoursePreview\(lessons\.filter \{ it\.weekday == day && week in it\.weeks \}\.sortedBy \{ it\.startSection \}\)/, "按实际本机课程日期过滤");
assert.match(settingsRoutes, /if \(week == null\) "本机课表" else "今日课程"/, "未知教学周不冒称今日");
assert.match(settingsRoutes, /if \(lessons\.isEmpty\(\)\) AppGroupedEntry\("导入课表", onClick = actions\.importTimetable\)/, "空课表直接导入");
assert.match(settingsRoutes, /actions\.openLocalCourse\(lesson\.id, lesson\.weekday\)/, "首页与课表共用实际课程详情");
assert.match(settingsRoutes, /combineWorkspaceTasks\(tasks, cloud, native\.orEmpty\(\)\)/, "聚合实际任务");
assert.match(settingsRoutes, /data\.cloud\.tasks\.takeIf \{ data\.cloud\.authenticated && data\.cloud\.tasksFresh \}/, "拒绝未核验云端任务");
assert.match(settingsRoutes, /部分任务来源尚未读取或核验/, "来源未确认明确提示");
assert.match(settingsRoutes, /if \(id != null\) actions\.openLocalTask\(id\) else actions\.navigate\(AppRoute\.TASKS\)/, "任务真实入口");
assert.match(settingsRoutes, /data\.cloud\.publicError != null && notices\.isNotEmpty\(\)/, "首页公告缓存标识");
assert.match(settingsRoutes, /actions\.openArticle\(notice\.url\)/, "真实公告原文");
assert.match(settingsRoutes, /actions\.navigate\(AppRoute\.GRADES\)/, "成绩路由");
assert.match(settingsRoutes, /actions\.navigate\(AppRoute\.EXAMS\)/, "考试路由");
assert.match(settingsRoutes, /actions\.navigate\(AppRoute\.SECOND_CLASS\)/, "第二课堂路由");
assert.match(settingsRoutes, /actions\.navigate\(AppRoute\.STUDY_PROGRESS\)/, "学习进度路由");
assert.match(settingsRoutes, /actions\.navigate\(AppRoute\.CAMPUS_RUN\)/, "校园记录路由");
assert.match(workspaceLogic, /task\.storage == "local" && !task\.done/, "近期截止仅本人任务");
assert.match(workspaceLogic, /!it\.isBefore\(today\) && !it\.isAfter\(today\.plusDays\(3\)\)/, "近期截止不混入逾期");
assert.doesNotMatch(design, /WebView|HttpURLConnection|CookieManager/);
assert.doesNotMatch(design, /import io\.github\.x07xh\.xiaoxu\.data\./);
assert.match(appGradle, /io\.github\.kyant0:backdrop:2\.0\.0-alpha03/);
assert.match(appGradle, /io\.github\.kyant0:shapes:1\.2\.0/);
assert.match(appGradle, /xiaoxuRuntimeTest/);
assert.match(main, /XiaoxuTheme \{/);
assert.doesNotMatch(main, /private const val PageSwitchMs/);
assert.match(settingsIndex, /wrapContentWidth\(androidx\.compose\.ui\.Alignment\.CenterHorizontally\)/);
assert.match(settingsIndex, /header = "应用与支持"/);
assert.doesNotMatch(workspace, /private fun HomeHero\(|resolveHomeCourseIndex|selectedHomeCourseKey|TodayScheduleBlock\(/);
assert.doesNotMatch(settingsRoutes, /下一节课|倒计时为|else state\.lessons/);

const homeCourseSequence = read('apps/xiaoxu-android/app/src/test/java/io/github/x07xh/xiaoxu/ui/HomeCourseSequenceTest.kt');
assert.match(homeCourseSequence, /firstFourPreserveSequenceFromLongList/);
const dueTest = read('apps/xiaoxu-android/app/src/test/java/io/github/x07xh/xiaoxu/ui/WorkspaceDueProvenanceTest.kt');
assert.match(dueTest, /upcomingCountRequiresLocalNotOverdueAndNotDone/,
  '真实JVM用例须覆盖本人逾期及云端未核验日期');
assert.match(dueTest, /weeklyFiltersDoNotTreatCloudRecordsAsVerifiedSchoolDueDates/,
  '真实JVM用例须拒绝把云端未核实日期标为本周学校作业');
assert.match(workspace, /未命名|已保存|待完成/, '首页显示本人真实任务数据');
assert.doesNotMatch(workspace, /Log\.[diwev]\(|println\(/, '首页不得输出调试日志');
// 课表主页面继续保留本机导入及来源分离门禁。
assert.doesNotMatch(cloud, /scheduleStatus|scheduleFetchedAt|val lessons: List<VerifiedLesson> = emptyList/);
assert.doesNotMatch(cloud, /request\("\/api\/v1\/schedule"/);
assert.match(schedulePanel, /fun localImportedScheduleView\(/);
assert.match(schedulePanel, /本机课表 · 离线查看/);
assert.match(schedulePanel, /sourceLabel = "本机导入 · 待本人核对"/,
  "课表仍需明确来源与核对状态，界面不得强制重复显示课块统计");
assert.match(panels, /enabled = id != "ncwu"/,
  '青果的旧服务器课表同步不可被当做成绩同步');


const localSourceTest = read('apps/xiaoxu-android/app/src/test/java/io/github/x07xh/xiaoxu/ui/LocalTimetableSourceTest.kt');
assert.match(localSourceTest, /class LocalTimetableSourceTest/);
assert.match(localSourceTest, /importedLessonsArePassedThroughWithoutInventingOrFiltering/);
assert.match(localSourceTest, /localImportNeverClaimsOfficialCloudVerification/);
assert.match(localSourceTest, /missingImportDoesNotSynthesizeCourses/);
assert.match(localSourceTest, /replacingImportDoesNotMergeStaleLocalLessons/);
assert.doesNotMatch(schedulePanel, /sealed interface ScheduleUiState|fun deriveScheduleUiState\(/,
  'Retired cloud timetable display logic must not return');

// 课表视觉只检查仍然渲染的本机页面，停止约束旧海报/云端样式。
const scheduleVisual = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleScreen.kt');
assert.match(scheduleVisual, /XiaoxuCard\(/);
assert.match(scheduleVisual, /private fun SchedulePageHeading\([\s\S]*?Text\(\s*"我的课表"/,
  '本机课表空态与有课程态须使用同一标题组件');
assert.doesNotMatch(scheduleVisual, /label = "重新导入课表"/);
assert.match(timetableSettings, /title = "导入课表"/);
assert.match(scheduleVisual, /XiaoxuToolbarAction\(/, '课表工具操作和其他原生页面必须共用组件');
assert.doesNotMatch(scheduleVisual, /SchedulePageHeading\(onMessages\)\s*Text\(/,
  "课表顶部不得恢复重复的操作文字栏");
assert.match(scheduleVisual, /colorKey = id\.ifBlank \{ title \}/);
assert.match(scheduleVisual, /xiaoxuCourseColor\(lesson\.colorKey\)/);
assert.match(scheduleVisual, /WeekSelector\(/);
assert.match(scheduleVisual, /ModalBottomSheet\(/);


// Issue #268 P1：三页复用 #269 统一组件，不得悄悄改动本人数据/审核动作。
// 这里保护关键视觉结构与操作入口；真实布局仍以 Android APK/截图为最终验收。
assert.match(workspace, /private fun TaskStatusTab\(/, '三态统计筛选必须可操作');
assert.match(workspace, /One continuous status strip/, '三项统计必须位于同一个白色容器中');
assert.match(workspace, /verticalArrangement = Arrangement\.spacedBy\(3\.dp, Alignment\.CenterVertically\)/,
  '三项统计应采用上下标签和数量而非横排大卡');
assert.match(workspace, /label = "待完成", count = all\.count/, '待办胶囊计数必须是真实任务');
assert.match(workspace, /onlyThisWeek by rememberSaveable/, '截止日期筛选须保留状态');
assert.match(workspace, /Text\("本周日期"\)/, '一键筛选应标明仅本机任务，避免误判官方截止日期');
assert.match(workspace, /label = "已完成", count = all\.count/, '完成状态只能统计现有记录');
assert.match(workspace, /private fun TaskGroupHeader\(/, '任务分区必须保留来源与折叠入口');
assert.match(workspace, /var showCourseTasks by rememberSaveable \{ mutableStateOf\(false\) \}/,
  '首次进入任务中心的课程任务必须默认折叠');
assert.match(workspace, /onClick = \{ showCourseTasks = !showCourseTasks \}/,
  '课程任务仍支持直接展开与收起');
assert.match(workspace, /item\(key = "group:course"\)/,
  '保留稳定分组 key 以维护 LazyColumn 的可视位置');
assert.doesNotMatch(workspace, /requestScrollToItem\(/,
  '不得以变动的数值索引强行定位，导致其他任务分组跳跃');
assert.match(workspace, /if \(showCourseTasks\) \{/,
  '课程列表显示必须只由当前展开状态控制');
assert.match(workspace, /item\(key = "group:course"\)/,
  '课程任务分组必须使用稳定 LazyColumn key 保留位置');
assert.match(workspace, /item\(key = "group:wechat"\)/,
  '微信分组必须使用稳定 key，而非依赖前面的项目索引');
assert.match(workspace, /var showWechatTasks by rememberSaveable \{ mutableStateOf\(false\) \}/,
  '首次进入任务中心的微信任务必须默认折叠');
assert.match(workspace, /var showPersonalTasks by rememberSaveable \{ mutableStateOf\(false\) \}/,
  '首次进入任务中心的本机任务必须默认折叠');
assert.doesNotMatch(workspace, /var showOfficialAudit by rememberSaveable \{ mutableStateOf\(false\) \}/,
  '旧云端作业读取分组已经退役');
assert.match(workspace, /var showSchoolRecords by rememberSaveable \{ mutableStateOf\(false\) \}/,
  '首次进入任务中心的本机读取必须默认折叠');
const taskHubRegion = workspace.slice(workspace.indexOf('fun RealAcademicTaskHub('),
  workspace.indexOf('/** 首页与任务中心共用任务详情'));
assert.match(taskHubRegion, /verticalArrangement = Arrangement\.spacedBy\(0\.dp\)/,
  '任务列表应取消 LazyColumn 默认行间距以连接同组任务');
assert.match(taskHubRegion,
  /items\(count = cloudRows\.size, key = \{ i -> cloudRows\[i\]\.key \}\)[\s\S]*?ConnectedTaskShell\(index = i, total = cloudRows\.size\)/,
  '云端任务维持逐条 lazy item 与稳定 key，不允许合并成一次性大 Column');
assert.match(taskHubRegion,
  /items\(count = localRows\.size, key = \{ i -> localRows\[i\]\.key \}\)[\s\S]*?ConnectedTaskShell\(index = i, total = localRows\.size\)/,
  '本机任务维持逐条 lazy item 与稳定 key');
assert.match(workspace, /private fun ConnectedTaskShell\(/,
  '任务行使用共享首尾圆角与连接线容器');
assert.match(workspace, /if \(index > 0\) \{\s*HorizontalDivider\(/,
  '相邻任务之间必须只有轻分割线');
assert.match(workspace, /index == total - 1 -> RoundedCornerShape\(/,
  '任务分组最后一条保留与玻璃外壳一致的底部圆角');
assert.match(workspace, /joinedRows = true,/,
  '课程、微信和本机任务分组须与所属任务行衔接为单层玻璃列表');
assert.match(workspace, /label = "task-group-chevron"/,
  '任务分组折叠箭头须使用共享动画时序');
assert.match(workspace, /modifier = Modifier\.size\(20\.dp\)\.rotate\(chevronRotation\)/,
  '分组状态改变时只旋转箭头，不重新绘制业务数据');

assert.match(designSystem, /val listRowMin: Dp = 68\.dp/,
  '任务与消息共用同一紧凑最小行高');
assert.match(workspace, /val groupRadius = XiaoxuRadius\.group/,
  '任务列表首尾圆角必须沿用唯一设计系统');
assert.match(workspace, /heightIn\(min = XiaoxuSpacing\.listRowMin\)/,
  '任务行仍由内容扩展且使用统一最小行高');
assert.match(messageCenter, /heightIn\(min = 64\.dp\)/,
  '公告紧凑行保持不低于64dp的触控区');
assert.match(messageCenter, /private val CardShape = RoundedCornerShape\(XiaoxuRadius\.list\)/,
  '官方消息卡片圆角必须与其他页面统一');


const compactTaskHeader = workspace.slice(workspace.indexOf('private fun TaskGroupHeader('),
  workspace.indexOf('private fun TaskSectionHeader('));
assert.doesNotMatch(compactTaskHeader, /XiaoxuCard\(/,
  '任务列表分组不应再套独立大卡造成页面拼接感');
assert.match(compactTaskHeader, /heightIn\(min = 64\.dp\)/,
  '精简分组后仍须保留可触控的最小高度');
assert.match(compactTaskHeader, /background\(XiaoxuPalette\.GlassCard\)/,
  '展开分组必须使用与正文连续的玻璃材质');
assert.match(compactTaskHeader, /if \(badge\.isNotBlank\(\)\) append\("，"\)\.append\(badge\)/,
  '有实际来源状态时保留无障碍描述，普通任务不追加无用小字');
assert.match(compactTaskHeader, /if \(expanded && \(badge\.isNotBlank\(\) \|\| note\.isNotBlank\(\)\)\)/,
  '仅在展开且有额外说明时占用第二行，默认保持紧凑');
assert.doesNotMatch(workspace, /title = "群消息任务候选"|showCandidates/,
  '任务中心不得再展示待审核微信群消息区块或保存其折叠状态');
assert.match(workspace, /title = "微信任务"/,
  '审核通过的微信任务必须在任务中心独立成组');
assert.match(workspace, /var showWechatTasks by rememberSaveable/,
  '微信任务独立于课程/本机分组折叠');
assert.match(taskHubRegion,
  /items\(count = wechatRows\.size, key = \{ i -> wechatRows\[i\]\.key \}\)[\s\S]*?ConnectedTaskShell\(\s*index = i, total = wechatRows\.size\s*[,\)]/,
  '微信任务必须维持独立带 key 的 LazyColumn 列表');
assert.match(workspace, /val wechatRows = remember\(shown\) \{ shown\.filter \{ it\.category == "wechat" \} \}/,
  '审核通过微信任务保持独立缓存分组');
assert.match(workspace, /val cloudRows = remember\(shown\) \{ shown\.filter \{ it\.category == "cloud" \} \}/,
  '云端任务保持独立缓存分组');
assert.match(workspace, /"wechat" -> "微信任务"/,
  '原生任务标题与筛选菜单必须识别微信来源');
assert.match(workspace, /"wechat" -> "wechat"/,
  '微信来源仅由后端存储记录判断，不得通过标题或群聊文本猜测');
assert.match(messageCenter, /val wechatTitle = "微信审核" \+/,
  '微信审核标签仍由真实候选计数生成');
assert.match(messageCenter, /XiaoxuSegmentOption\(MSG_FILTER_WECHAT, wechatTitle\)/,
  '微信候选入口必须留在消息中心的唯一来源选择器');
assert.match(messageCenter, /WechatReviewPanel\(/,
  '已保存候选只允许从消息中心审核');
assert.doesNotMatch(workspace, /WechatReviewPanel\(/,
  '任务中心不得恢复重复微信审核器');
assert.match(workspace, /if \(showSearch\) item \{/, '搜索框应由头部按钮控制，默认不抢首屏空间');
assert.match(workspace, /taskMenuOpen by rememberSaveable \{ mutableStateOf\(false\) \}/,
  '低频任务管理收进默认关闭的更多菜单');
assert.doesNotMatch(workspace, /PosterDemo/, '真实任务中心不得有独立海报演示版');
assert.match(workspace, /if \(showCloudEditor\) \{[\s\S]*?CloudTaskEditor\(client\)/,
  '云端新建使用独立浮层');
assert.doesNotMatch(workspace, /showManager && showLocal|jumpTo =/,
  '移除已淘汰的内联编辑器与固定索引跳转');
assert.match(workspace, /val row = localRows\[i\][\s\S]*?TaskRow\(row, today,[\s\S]*?onOpen = \{ onTaskTap\(row\) \}/,
  '本机任务使用来源 key 直达编辑弹层，不需要先进入详情');
assert.match(workspace, /dao\.trySetCompleted\(id, checked\)/,
  '首页任务中心勾选必须在 Room 事务里核对前置依赖');
assert.match(db, /suspend fun trySetCompleted\(taskId: Long, completed: Boolean\): Boolean[\s\S]*?unfinishedDependencyCount\(taskId\)[\s\S]*?setCompleted\(taskId, completed\)/,
  '所有本机任务视图共享单事务的前置依赖完成门禁');
assert.match(db, /UPDATE local_tasks SET due = :due WHERE id = :id/,
  '快捷设置日期仅更新 due，不覆盖任务标题与来源');
assert.match(workspace, /val pendingLocalTaskUpdates = remember \{ mutableStateMapOf<String, Boolean>\(\) \}/,
  '本机任务仅即时预览状态，Room 仍是事实来源');
assert.match(workspace, /LaunchedEffect\(localTasks\) \{[\s\S]*?pendingLocalTaskUpdates\.remove\(key\)/,
  'Room 确认或删除后清理待保存预览');
assert.match(workspace, /pendingLocalTaskUpdates\.remove\(row\.key\)[\s\S]*?本机任务保存失败，请重试/,
  '本机任务写入失败后必须回滚');
assert.match(workspace, /checked = displayDone,[\s\S]*?enabled = actionEnabled/,
  '本机任务预览应立即反馈且防止重复提交');
assert.match(reminder, /internal fun changedLocalReminderIds\(/,
  '只协调截止日期或完成状态真正变化的提醒任务');
assert.match(reminder, /affectedIds\.forEach \{ taskId ->/,
  '删除任务或变更截止时间必须逐个处理受影响的 WorkManager ID');
assert.match(workspace, /task\.storage == "local" && onToggleLocal != null/,
  '云端任务不可复用本机完成操作或假装已提交到学校');
assert.match(cloud, /suspend fun setWechatTaskCompleted\(/,
  '微信审核任务必须复用本人云端任务写接口而不是只读装饰勾选框');
assert.match(cloud, /it\.provider == "wechat" && it\.revision == revision/,
  '仅后端审核已确认微信来源、且版本匹配的任务允许个人勾选');
assert.match(cloud, /request\("\/api\/v1\/assignments\/\$id", "POST", body, authenticated = true,[\s\S]*?boundSessionToken = boundToken\)/,
  '微信任务个人完成使用 Android 支持的 POST 且携带原有本人认证');
assert.match(cloud, /put\("localStatus", if \(completed\) "done" else "todo"\)/,
  '微信任务勾选只操作个人状态，不更改学校官方提交状态');
assert.match(workspace, /onToggleWechat = if \(state\.tasksFresh && row\.revision >= 0\)/,
  '只有本次新鲜读取并携带版本号的微信任务才能勾选');
assert.match(workspace, /task\.category == "wechat" && onToggleWechat != null/,
  '微信任务复用可点击 Checkbox，普通云端任务继续保持只读');
assert.match(workspace, /actionEnabled = !pendingWechatTaskUpdates\.containsKey\(row\.key\)/,
  '仅锁定正在提交的单条微信任务，其它任务仍可交互');
assert.match(workspace, /val pendingWechatTaskUpdates = remember \{ mutableStateMapOf<String, Boolean>\(\) \}/,
  '微信任务勾选应立即显示临时状态而不提前更改真实统计');
assert.match(workspace, /pendingWechatTaskUpdates\[row\.key\] = checked/,
  '点击立即反馈，不等待网络返回才能勾选');
assert.match(workspace, /pendingWechatTaskUpdates\.remove\(row\.key\)/,
  '成功确认与失败回滚均清理临时状态');
assert.match(workspace, /val secondary = when \{\s*isPending -> "保存中…"/,
  '尚未收到云端回执时必须在唯一辅助行明确显示保存中');
assert.doesNotMatch(workspace, /modifier = Modifier\.animateItem\(/,
  '展开课程任务时不能再让微信列表补间移动并产生跳跃');
assert.match(cloud, /preferConfirmedWechatRevisions\(it, state\.tasks\)/,
  '旧的并发读取不得覆盖已确认的微信任务状态');
const wechatMutation = cloud.slice(cloud.indexOf('suspend fun setWechatTaskCompleted('),
  cloud.indexOf('suspend fun addCloudTask(', cloud.indexOf('suspend fun setWechatTaskCompleted(')));
assert.doesNotMatch(wechatMutation, /refreshPrivate\(\)\s*val updated/,
  '成功写入个人状态不应再等待整套云端多页面刷新');
assert.match(wechatMutation, /committedRevision != revision\.toLong\(\) \+ 1L/,
  '不验证后端已提交版本就不能标记为完成');

assert.ok(workspace.includes('TaskRow(cloudRows[i], today, onOpen = { openedTaskKey = cloudRows[i].key })'),
  '云端任务仍从真实 Row 数据打开详情且保留来源隔离');
assert.ok(workspace.includes('含上次云端快照'),
  '未核验云端数据不得被统计描述为最新');
assert.ok(workspace.includes('SourceBadge(text, tone = if (emphasized)'),
  '任务来源必须复用共享来源标签');
assert.ok(!workspace.includes('WechatReviewPanel('),
  '任务中心只展示已保存任务，不能复制微信候选审核器');
assert.ok(messageCenter.includes('WechatReviewPanel('),
  '消息中心保留唯一原生审核器和真实候选计数');
assert.match(messageCenter, /pendingCount = state\.pendingWechatCount/,
  '待审核数量仍以消息中心真实读取结果为准');
assert.match(workspace, /var openedTaskKey by rememberSaveable/, '任务详情选中状态应可保存');
assert.match(workspace, /val selectedTask = all\.firstOrNull/, '任务详情仅从已有真实记录回查');
assert.match(workspace, /XiaoxuBottomSheet\(\s*title = "任务详情"/,
  '任务详情必须使用共享原生底部浮层');
assert.match(workspace, /Text\("来源与日期"/,
  '任务详情须区分来源和时间');
assert.match(workspace, /Text\("前置关系"/,
  '任务详情区分前置条件');
assert.match(workspace, /clickable\(onClickLabel = "查看任务详情", onClick = onOpen\)/,
  '任务卡片必须可以真实点击');
assert.match(workspace, /task\.storage == "cloud"/, '任务来源必须区分云端与本机');
assert.match(workspace, /云端历史快照；日期与完成情况可能已变化/,
  '任务详情必须区分历史快照与当前同步成功');
assert.match(workspace, /onVerifyChaoxing = \{ openLocalSchool\("chaoxing"\) \}/,
  '学习通相关云端任务详情必须可以打开官方页面进行人工核对');
assert.match(workspace, /selectedTask.provider == "chaoxing"/,
  '只有学习通相关任务显示学习通核对入口');
assert.match(workspace, /尚未确认当前官方作业身份/,
  '没有可信来源 ID 的旧记录必须提供人工核对入口');
assert.match(workspace, /已识别官方作业身份/,
  '只有可信 work 元组才可标识已识别官方作业身份');
assert.match(workspace, /trustedChaoxingPlatformStatus\(/,
  '学习通状态必须通过后端受信任来源元数据校验，不按标题猜测');
assert.match(workspace, /上次官方同步：/,
  '学校提交状态须单独显示为历史同步结果，不混入个人完成状态');
assert.match(cloud, /val sourceExternalId: String\? = null/,
  '学籍任务需保留可信作业来源 ID');
assert.match(cloud, /val officialStatus: String\? = null/,
  '后端已返回的官方状态不得在客户端丢弃');
assert.match(cloud, /source_external_id/,
  '学校任务来源应读取已有 API 的稳定 external ID');
assert.match(cloud, /platform_status/,
  '学校任务应读取已有 API 的可信平台状态');
assert.match(workspace, /本机记录 · 离线保存/,
  '任务详情须标注本机来源');
assert.match(workspace, /editingLocalId = selectedTask\.key\.removePrefix\("local:"\)/,
  '本机详情编辑现有任务而非打开新建表单');
assert.match(workspace, /LocalTaskEditSheet\(task = editingLocal, dao = dao/);
assert.match(db, /UPDATE local_tasks SET title = :title, source = :source, due = :due WHERE id = :id/);
assert.match(messageCenter, /modifier = Modifier\.fillMaxWidth\(\)\.heightIn\(min = 56\.dp\)/,
  '消息页使用 56dp 紧凑标题，不再包一层大卡');
assert.ok(messageCenter.includes('SourceBadge(\n            text = badge'),
  '官方公告与微信候选使用共享来源徽标');
assert.match(messageCenter, /WechatReviewPanel\(/,
  '保留原生微信候选逐条审核');
assert.match(messageCenter, /NoticeDetailSheet\(/,
  '保留公告真实详情及官方链接');
assert.ok(panels.includes('XiaoxuCard {'),
  '个人中心和学校连接卡采用共享样式');
assert.doesNotMatch(panels, /SchoolReaderSection\(|SectionHeader\(title = "数据连接"\)/,
  '二级数据连接已有页面标题，不得再叠加无效包装层及同名标题');
assert.ok(panels.includes('LocalSchoolConnectionPanel('),
  '不得移除原生学校读取入口');
assert.match(panels, /if \(cloudAdvanced\) XiaoxuBottomSheet\(/,
  '云端授权仍是独立可选入口，详情改为居中浮层而不向下展开');

// Issue #268 P2：任务统计卡可点击筛选，详情跳转到真实管理区，窄屏来源标题分行。
assert.match(workspace, /active = filter == "all"/, '全部任务为独立状态');
assert.match(workspace, /sourceFilter = source/, '来源菜单选项必须真实更新筛选条件');
assert.match(workspace, /DropdownMenu\(/, '来源筛选收纳为单个下拉入口');
assert.match(workspace, /sourceMenuOpen = false/, '来源菜单选择后必须关闭');
assert.match(workspace, /facetWorkspaceTasks\(/, '来源与日期筛选纯函数可测');
assert.match(workspace, /filter = "pending"/,
  '待完成统计卡应真实切换过滤器');
assert.match(workspace, /filter = "done"/,
  '已完成统计卡应真实切换过滤器');

assert.match(workspace, /showCloudEditor = true/);
assert.match(workspace, /maxLines = 2,[\s\S]*?modifier = Modifier\.weight\(1f\)\.semantics \{ heading\(\) \}/,
  '窄屏任务分区标题必须给来源和条数预留空间');
// Issue #275：消息中心与「我的」按七张海报/四屏 HTML 精细化，只换视觉层级，
// 真实来源、审核事务与数据边界一律不动。
assert.doesNotMatch(messageCenter, /MESSAGE CENTER/,
  '消息顶栏不再重复展示英文装饰标题');
assert.match(messageCenter, /messageFilterIcon\(/, '四枚筛选 pill 需带图标');
assert.match(messageCenter, /TextFieldDefaults\.colors\(/, '搜索条改为无边框填充样式');
assert.match(messageCenter, /fun MessageSearchField\(/, '搜索条抽成独立组件');
assert.match(messageCenter, /XiaoxuSegmentedControl\(/,
  '官方与微信来源使用共享原生分段组件');
assert.match(messageCenter, /Box\(Modifier\.size\(7\.dp\)\.clip\(CircleShape\)/,
  'B2 公告使用紧凑状态圆点，避免重复大图标造成视觉拥挤');
assert.match(messageCenter, /private fun SourceSectionHeader\(/,
  '保留列表内的来源标题与状态语义');
assert.match(messageCenter, /private fun MessageHeader\(onRefresh:/,
  '消息统一使用紧凑页头');
assert.match(messageCenter, /XiaoxuToolbarAction\(/,
  '消息刷新及返回须复用工具按钮');
assert.match(messageCenter, /heading\(\)/, '消息页标题需有标题语义');
assert.doesNotMatch(messageCenter, /AI 摘要|智能整理|未读红点/,
  '海报里的智能摘要与未读红点不能写进真实消息页');

assert.match(panels, /fun ProfileHeroCard\(/,
  '真实账户概览保留在数据连接二级页，不在我的首页复制卡片');
assert.match(panels, /XiaoxuCard\(contentPadding = 14\.dp\)/,
  '我的页面账户信息应为紧凑白色状态卡');
assert.doesNotMatch(panels, /我的学习空间|学校账号各自授权；本机读取不依赖校序云端登录/,
  '账户区不得恢复大面积旧版宣传卡及解释文字');
assert.match(settingsRoutes, /AppRoute\.CONNECTIONS -> ConnectionsFeature\(data, actions\)/,
  '真实学校读取入口归属我的 → 数据连接二级页面，不回退独立首页容器');
assert.match(panels, /title = "云端同步"/,
  '云端授权应继续收纳为独立可操作区域');
assert.match(main, /private fun CompactPageHeading\(/,
  '教务业务页仍复用紧凑标题，设置页标题由 ProfileSettingsPage 唯一维护');
assert.match(main, /topBar = \{\},/,
  'Scaffold 顶栏不随五个标签跳动，避免切页时整体内容高度变化');
assert.match(main, /applyStatusBarInset = false/,
  '首页顶栏移动到页面内后应使用Scaffold既有状态栏内边距，不可重复下移');
assert.match(design, /then\(if \(applyStatusBarInset\) Modifier\.statusBarsPadding\(\) else Modifier\)/,
  '统一顶栏允许复用Scaffold安全区，同时保持原有独立顶栏默认行为');
assert.match(main, /MainDestination\.HOME -> Column\(Modifier\.fillMaxSize\(\)\)/,
  '首页顶栏必须成为唯一一级页面内容，不影响其他页面的 Scaffold 几何');
assert.match(main, /onMessages = \{ messagesOpen = true \}/,
  '页面消息中心入口应继续可达');
assert.doesNotMatch(main, /Content\(withTopInset = true\)|\.fillMaxSize\(\)\.statusBarsPadding\(\)/,
  'Scaffold 已处理状态栏，不能重复添加顶部安全区');
assert.match(main, /if \(academicRoute != AcademicDestination\.OVERVIEW\)/,
  '教务各详情页在 AnimatedContent 当前目标页面统一原生返回，不叠加二级标签');
assert.doesNotMatch(main, /val visibleTabs = listOf\(/,
  '教务总览操作行已承担导航，不能再重复显示四项标签');
assert.match(main, /academic != AcademicDestination\.OVERVIEW/,
  '所有教务详情统一支持系统返回至总览');
assert.match(main, /fun AcademicActionRow\(/,
  '教务入口必须使用连续可点击操作行');
assert.match(main, /AcademicDestination\.OVERVIEW -> Content\(topPadding = 6\.dp\)/,
  '教务总览须收紧标题和内容之间的空白');
const academicOverviewSource = main.slice(
    main.indexOf('AcademicDestination.OVERVIEW -> Content('),
    main.indexOf('AcademicDestination.SECOND_CLASS ->', main.indexOf('AcademicDestination.OVERVIEW -> Content('))
);
assert.match(academicOverviewSource, /AcademicEditorialSection\(title = "教务"\)/,
  '成绩和考试须归入教务分类');
assert.match(academicOverviewSource, /AcademicEditorialSection\(title = "校园生活"\)/,
  '第二课堂和校园服务须归入校园生活分类');
assert.doesNotMatch(academicOverviewSource, /XiaoxuCard\(/,
  '教务连续分组内部不能再套第二层方形卡片');
assert.match(main, /private fun AcademicEditorialSection\(/,
  '教务分区复用编辑式布局组件，不添加第二套导航');
const academicEditorial = main.slice(main.indexOf('private fun AcademicEditorialSection('),
  main.indexOf('private fun AcademicActionRow('));
assert.match(academicEditorial, /HorizontalDivider\(color = XiaoxuPalette\.Line, thickness = 1\.dp\)/,
  '教务分区用开放式细线与标题组织');
assert.doesNotMatch(academicEditorial, /XiaoxuCard\(|XiaoxuGroupSurface\(/,
  '教务分区不应重新叠加大卡片');
const academicAction = main.slice(main.indexOf('private fun AcademicActionRow('));
assert.match(academicAction, /heightIn\(min = 60\.dp\)/,
  '教务入口保持足够触控高度');
assert.match(academicAction, /onClickLabel = "打开\$title"/,
  '教务入口保持有意义的无障碍点击描述');
assert.doesNotMatch(academicAction, /background\(XiaoxuPalette\.BrandSoft\)/,
  '教务入口不新增蓝色装饰瓷砖');
// Preserve the settings organization contract against the shared upstream groups.
for (const label of ['账号与教务','学习与课程','数据与安全','应用与支持']) {
  assert.ok(settingsIndex.includes('header = "' + label + '"'),
    label + ' settings group remains reachable');
}
assert.match(settingsIndex, /InsetGroupedSection\(/);
assert.doesNotMatch(settingsIndex, /XiaoxuCard\(|XiaoxuGroupSurface\(/,
  'Do not restore the replaced settings surface system');
assert.match(settingsRoutes, /AppRoute\.CONNECTIONS -> ConnectionsFeature\(data, actions\)/,
  '实际连接页必须继续在原二级路由内');
assert.match(settingsIndex, /"任务备份",[\s\S]*?actions\.navigate\(AppRoute\.TASK_BACKUP\)/,
  '备份恢复入口仍可达');


assert.doesNotMatch(academicOverviewSource, /"未读取"/,
  '教务总览只做导航，未核验状态留在真实详情页');

assert.match(main, /AcademicActionRow\("第二课堂"/,
  '真实第二课堂必须保留直接可达入口');
assert.match(main, /CampusRunPanel\(cloud\)/,
  '校园跑/学校服务真实面板不可在 UI 精简时丢失');
const campusRunUi = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/CampusRunPanel.kt');
assert.match(campusRunUi, /fun CampusRunPanel\(client: CloudClient\)/,
  '校园跑继续使用原有 CloudClient 会话作用域');
assert.match(campusRunUi, /key\(scope\) \{ CampusRunScopedPanel\(scope\) \}/,
  '切换账号时必须重置本机校园跑 UI 状态');
assert.equal((campusRunUi.match(/XiaoxuCard\(/g) || []).length, 2,
  '校园跑锁定态与记录态必须共用唯一玻璃卡片');
assert.doesNotMatch(campusRunUi, /\bCardDefaults\b|\bCard\s*\(/,
  '校园跑不得恢复独立 Material3 Card 容器');
assert.match(main, /SecondClassPage\(\)/,
  '第二课堂独立内容仍须可达');

assert.match(panels, /fun ProfileMetric\(/, '我的页真实指标格');

const profileMetricSource = panels.slice(panels.indexOf('private fun ProfileMetric('),
  panels.indexOf('private fun CloudAuthorizationSection(', panels.indexOf('private fun ProfileMetric(')));
assert.doesNotMatch(profileMetricSource, /background\(XiaoxuPalette\.SurfaceAlt\)/,
  '我的账户统计不能再各自嵌套灰色背景小卡，保持单层玻璃信息结构');
assert.match(profileMetricSource, /MaterialTheme\.typography\.titleMedium/,
  '我的真实数值须保持可读字级');

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
assert.match(messageCenter, /state\.authenticated && state\.wechatError == null &&/,
  '微信标签仅在会话正常且计数有效时展示真实数量');
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
assert.match(primaryNavigation, /PrimaryTabs = listOf\(\s*MainDestination\.HOME,\s*MainDestination\.SCHEDULE,\s*MainDestination\.TASKS,\s*MainDestination\.ACADEMIC,\s*MainDestination\.PROFILE/,
  '唯一导航配置为今日、课表、任务、校园、我的');
assert.doesNotMatch(main, /private val PrimaryTabs|NavigationRail\(/,
  '业务壳层不能重建第二套导航表或侧栏组件');
assert.doesNotMatch(main, /MainDestination\.MESSAGES/, '消息不占底栏');
assert.match(main, /onMessages = \{ messagesOpen = true \}/, '全局消息入口');
assert.match(main, /openAcademic = \{ current = MainDestination\.ACADEMIC; academic = AcademicDestination\.OVERVIEW \}/,
  '教务快捷入口打开总览');
assert.match(main, /MessageCenterPanel\(cloud, openWebsite, openArticle,/,
  '消息中心复用真实数据');
assert.match(main, /useStatusBarInset = false, onBack = \{ messagesOpen = false \},/,
  '消息页面直接传入返回来源页的回调，无重复中间包装组件');
assert.match(main, /localTasks = localTasks, localLessons = localLessons/,
  '本机提醒聚合仅使用本机 Room 任务与本机课表，不冒充官方公告');
assert.match(main, /BackHandler\(enabled = messagesOpen\)/,
  '系统返回键也必须关闭消息页并恢复原一级页');
assert.match(main, /pageStateHolder\.SaveableStateProvider\(page\.name\)/,
  '页面状态跨导航保留');
assert.match(messageCenter, /XiaoxuSegmentedControl\(/,
  '消息来源采用共享玻璃分段控件');
assert.match(messageCenter, /XiaoxuSegmentOption\(MSG_FILTER_NOTICE, noticeTitle\)/,
  '仍须保留官方通知来源');
assert.match(messageCenter, /sourceTab == MSG_FILTER_WECHAT/, '区分微信与官方来源');
assert.match(messageCenter, /onBack: \(\(\) -> Unit\)\? = null/,
  '消息提供返回回调');
assert.match(messageCenter, /WechatReviewPanel\(/,
  '真实消息布局不得绕过微信审核事务');
assert.doesNotMatch(messageCenter, /PosterDemo|模拟群聊|高数练习要求与参考\.pdf/,
  '真实消息中心不得展示海报演示消息和附件');

// Navigation V2 P1: source-scoped message scroll and evidence-based empty states.
assert.match(messageCenter, /val officialListState = rememberLazyListState\(\)/,
    '官方公告必须保存独立滚动位置');
assert.match(messageCenter, /val wechatListState = rememberLazyListState\(\)/,
    '微信审核必须保存独立滚动位置');
assert.match(messageCenter, /state = if \(showWechat\) wechatListState else officialListState/,
    '切换来源时不可沿用另一个来源的列表滚动位置');
assert.match(messageCenter, /if \(state\.noticeFetchedAt\.isNullOrBlank\(\)\) return MessagePhase\.NotConnected/,
    '仅官方公告本人读取证据可证明真实空状态，微信或登录状态不能冒充');
assert.match(messageCenter, /学校公开公告无需登录校序云端/,
    '首次尚未读取官方公告需要可操作的空状态');
assert.match(workspace, /filter == "done" && onlyThisWeek/,
    '已完成与本周截止冲突须说明而非提示数据丢失');
assert.match(workspace, /清除本周截止筛选/,
    '任务空状态需要直接撤销冲突筛选');

console.log('校序 Android V0.6.1：本人课程/作业、只读题目预览、会话与来源边界静态检查通过');

// The shell must not stack a global banner above the local timetable heading.
assert.match(main, /MainDestination\.HOME -> Column\(Modifier\.fillMaxSize\(\)\)/);
assert.doesNotMatch(main, /private fun MessagesPage\(/, '消息使用唯一原生页面');
const unifiedSchedule = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleScreen.kt');
assert.match(unifiedSchedule, /private fun SchedulePageHeading\(/);
assert.match(unifiedSchedule, /label = "消息中心",\s*onClick = onMessages/,
  '课表消息入口复用统一工具按钮但保留原回调');

// Automatic reads reuse the bounded local session, never credentials or a new Activity.
const autoDriver=read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/SecondClassAutoReadDriver.kt');
const autoRefresh=read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/SecondClassAutoRefresh.kt');
assert.doesNotMatch(autoDriver+autoRefresh,/addJavascriptInterface|document\.cookie|getCookie\(|startActivity\(|HttpURLConnection/);
assert.match(autoDriver,/withTimeout\(90_000\)/);
assert.match(autoDriver,/ncwu_secondclass_local\.js/);
assert.match(autoRefresh,/matchesConfirmedAccount/);
assert.match(autoRefresh,/expectedBinding = expected/);
assert.match(localNative,/accountHash = accountHash/);

const autoControls=read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/SecondClassAutoReadControls.kt');
assert.doesNotMatch(autoControls,/rememberCoroutineScope|refreshIfDue/, 'foreground automatic reads must be owned by the Activity lifecycle, not a retained composition');
assert.match(main,/override fun onStop\(\)[\s\S]*secondClassAutoJob\?\.cancel\(\)/);


// Settings switches use a single high-contrast component, even when disabled.
const switchFiles = [
  'ui/ProfileSettingsPage.kt',
  'ui/SecondClassAutoReadControls.kt',
  'ui/ScheduleScreen.kt'
];
for (const name of switchFiles) {
  const source = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/' + name);
  assert.match(source, /XiaoxuSettingsSwitch\s*\(/, name + ': shared switch');
  assert.doesNotMatch(source, /\bSwitch\s*\(/, name + ': no unstyled switch');
}
assert.doesNotMatch(timetableSettings, /XiaoxuSettingsSwitch\s*\(|\bSwitch\s*\(/,
  '课表设置不再维护全局提醒开关');
assert.doesNotMatch(localTaskUi, /XiaoxuSettingsSwitch\s*\(|\bSwitch\s*\(/,
  '任务编辑页不应存在重复的通知总开关');
assert.match(designSystem, /fun XiaoxuSettingsSwitch\(/);
assert.match(designSystem, /uncheckedThumbColor\s*=/);
assert.match(designSystem, /disabledUncheckedTrackColor\s*=/);
const semesterSettingsUi = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/SemesterSettingsDialog.kt');
assert.match(semesterSettingsUi, /Text\("默认课表视图"/);
assert.match(semesterSettingsUi, /fillWidth = false,\s*compact = true/,
  'default timetable view must use the compact inline choice, not a full-row segmented bar');

// Preserve the upstream inset contract and reuse the same window on standalone entries.
assert.match(pageScaffold, /contentWindowInsets = WindowInsets\.safeDrawing\.only\(WindowInsetsSides\.Bottom\)/);
assert.match(secondClassHome, /GlassWindowHost\s*\{/);
assert.match(secondClassHome, /AppPageFrame\(title = "第二课堂"/);
assert.doesNotMatch(secondClassHome, /Surface\(Modifier\.fillMaxSize\(\)/);
const upstreamBarTheme = read('apps/xiaoxu-android/app/src/main/java/com/tyust/course/ui/theme/Theme.kt');
assert.match(upstreamBarTheme, /window\.isNavigationBarContrastEnforced = false/);

// A local reminder adapter must delegate row chrome to the shared upstream group.
assert.match(profileSettings, /private fun ReminderToggleRow\([\s\S]*?\) \{\s*InsetGroupedRow\([\s\S]*?trailing = \{ LiquidSwitch\(/);
