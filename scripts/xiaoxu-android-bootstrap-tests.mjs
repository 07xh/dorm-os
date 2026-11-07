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
const pageMotion = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/PrimaryPageMotion.kt');
const profileSettings = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ProfileSettingsPage.kt');
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
const designSystem = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/XiaoxuDesignSystem.kt');
const scheduleUi = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleScreen.kt');
const messageUi = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/MessageCenter.kt');
assert.match(designSystem, /fun Modifier\.xiaoxuPageWidth\(/, '正文最大宽度由唯一设计系统定义');
assert.match(scheduleUi, /\.xiaoxuPageWidth\(XiaoxuPageLayout\.ContentMaxWidth\)/);
assert.match(messageUi, /\.xiaoxuPageWidth\(XiaoxuPageLayout\.ContentMaxWidth\)/);
assert.doesNotMatch(scheduleUi + messageUi, /private fun Modifier\.pageWidth\(/, '页面不得残留重复宽度实现');
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
assert.match(main, /PrimaryBottomNavigation\(selected = current, backdrop = navBackdrop\)/,
  '主壳层须只放一套可折射玻璃底栏');
assert.match(main, /PrimarySideNavigation\(selected = current\)/,
  '宽屏保留唯一横屏侧栏适配');
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
const reminder = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalDeadlineReminders.kt');
const localTaskUi = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/TaskBoard.kt');
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
assert.equal(localTaskUi.split('animationSpec = tween(XiaoxuMotion.SelectionMs)').length - 1, 2,
  'Local task editor selection pills use the global motion setting');
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
assert.match(localTaskUi, /var visibleLimit by rememberSaveable\(filterKey\) \{ mutableStateOf\(30\) \}/,
  '本机任务编辑器只组合首30条，切换筛选重置显示进度');
assert.match(localTaskUi, /visible\.take\(visibleLimit\)/,
  '本机任务必须按需显示更多，而非一次构造200张卡');
assert.match(localTaskUi, /XiaoxuGroupSurface\s*\{/,
  '本机任务列表复用唯一玻璃连续分组外壳');
assert.match(localTaskUi, /dao\.setCompleted\(task\.id, checked\)/,
  '本机任务完成操作必须仍写原Room记录');
assert.match(localTaskUi, /onDelete = \{ pendingDelete = task \}/,
  '本机任务删除仍需原确认流程');
assert.doesNotMatch(localTaskUi, /visible\.take\(200\)/,
  '旧式200行即时合成已清退');
assert.match(profileSettings, /ActivityResultContracts\.RequestPermission\(\)/,
  '通知权限只在用户开启提醒时申请');
assert.match(profileSettings, /LocalDeadlineReminders\.setEnabled\(context, value\)/,
  '本机截止通知的已有授权状态仅在通知设置中切换');
assert.doesNotMatch(localTaskUi, /LocalDeadlineReminders\.setEnabled|rememberLauncherForActivityResult/,
  '任务编辑页不再保留第二套截止通知总开关');
assert.match(main, /LocalDeadlineReminders\.reconcile\(reminderContext, localTasks\)/,
  'Room edits must re-plan notifications even when the task manager is collapsed');

assert.match(ci, /assembleDebug/);
assert.match(ci, /upload-artifact/);

const cloud = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/CloudClient.kt');
const taskUpdateRoute = read('functions/api/v1/assignments/[id].js');
assert.match(taskUpdateRoute, /export async function onRequestPost\(context\)/,
  'Android 个人完成状态须通过现有云端任务路径 POST 兼容方法');
assert.match(taskUpdateRoute, /Object\.keys\(body\)\.sort\(\)\.join\(','\)!=='localStatus,revision'/,
  '兼容 POST 严格限定个人状态与版本，不开放标题或官方字段修改');
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
assert.doesNotMatch(cloud, /request\("\/api\/v1\/schedule"/,
  '本机课表不能再从云端自动拉取课程');
assert.match(main, /CloudTimetablePanel/);
assert.match(cloud, /\/api\/v1\/wechat\/candidates/);
assert.match(cloud, /\/api\/notices/);
assert.match(cloud, /www2\.ncwu\.edu\.cn/);
const workspace = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/RealDataWorkspace.kt');
assert.match(localTaskUi, /internal fun TaskCompletionCheckbox\(/,
  'Local editor and task hub must share one completion-checkbox renderer');
assert.equal((localTaskUi.match(/\bCheckbox\(/g) || []).length, 1,
  'Only the reusable task control may instantiate a Material checkbox');
assert.equal((workspace.match(/TaskCompletionCheckbox\(/g) || []).length, 2,
  'Local and approved WeChat task rows must reuse the shared checkbox');
assert.doesNotMatch(workspace, /\bCheckbox\(/,
  'Unified task list must not maintain duplicate Material checkbox chrome');
assert.match(profileSettings, /private fun ReminderToggleRow\(/,
  'Course and deadline reminders must share one settings row');
assert.equal((profileSettings.match(/ReminderToggleRow\(/g) || []).length, 3,
  'Two reminder settings must reuse one row implementation');
assert.equal((profileSettings.match(/XiaoxuSettingsSwitch\(/g) || []).length, 1,
  'Both reminders must use a shared switch layout');
assert.match(main, /RealAcademicHome\(localTasks, client = cloud/);
assert.match(main, /RealAcademicTaskHub\(localTasks, dao, cloud, openWebsite,/);
assert.match(main, /openLocalSchool = openLocalSchool/);
assert.match(workspace, /openLocalSchool\("chaoxing"\)/);
assert.ok(main.includes('https://dorm-os-dk9.pages.dev/#settings'), 'school connector must deep-link into the existing connection center');
assert.match(workspace, /fun combineWorkspaceTasks\(/);
assert.match(workspace, /fun RealAcademicHome\(/);
assert.doesNotMatch(workspace, /private fun (?:WorkspaceCard|Metric)\s*\(|private val WorkspaceBlue\b/,
  '已废弃的首页展示卡片和专用颜色不得回流');
assert.match(workspace, /fun RealAcademicTaskHub\(/, '真实任务工作台继续复用既有组件');
assert.match(workspace, /fun RealAcademicTaskHub\(/);
assert.match(workspace, /LocalTaskEditor\(localTasks, dao\)/);

const localEditorSource = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/TaskBoard.kt');
assert.match(localEditorSource, /fun LocalTaskEditor\(/);
assert.match(localEditorSource, /XiaoxuCard\(/,
  '本机任务编辑与列表必须使用唯一设计系统玻璃卡片');
assert.doesNotMatch(localEditorSource, /\bCardDefaults\b|\bCard\s*\(/,
  '禁止独立 Material3 Card 回流本机任务编辑器');
assert.doesNotMatch(localEditorSource, /fun LocalTaskBoard\(/);
assert.doesNotMatch(localEditorSource, /private fun StatTile\(/,
  '任务 KPI 仅由新版任务中心展示，不复活旧数字大卡');
assert.match(localEditorSource, /dao\.setCompleted\(task\.id, checked\)/,
  '本机任务完成/取消完成操作不能丢失');
assert.match(localEditorSource, /dao\.remove\(task\.id\)/,
  '本机删除确认功能不能丢失');
assert.match(profileSettings, /LocalDeadlineReminders\.setEnabled\(context, value\)/,
  '本机截止提醒应由统一通知页面控制');
assert.match(profileSettings, /LocalDeadlineReminders\.reconcile\(context, tasks\)/,
  '新通知设置必须沿用真实 Room 任务的既有提醒计划');
assert.match(workspace, /CloudTaskEditor\(client\)/);
assert.match(panels, /fun CloudTaskEditor\(/, '云端任务编辑器复用已有 CloudClient');
assert.match(panels, /client\.addCloudTask\(value\)/, '云端新增仍使用现有真实接口');
assert.doesNotMatch(panels, /fun CloudTasksPanel\(/,
  '退役的嵌套云端任务列表不应重新出现在管理面板');
assert.doesNotMatch(panels, /state\.tasks\.take\(50\)/,
  '云端列表仅由统一任务中心显示，避免重复卡片和不同截断上限');
assert.match(workspace, /不能据此推断学校没有作业/);
assert.match(workspace, /val sameWeekday = localLessons\.filter/, '首页只匹配本机导入课表星期');
assert.match(read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleScreen.kt'),
  /本机导入 · 待本人核对/,
  '课表来源与未核验教学周必须在课表页保留提示，首页无需反复显示');
assert.doesNotMatch(workspace, /WebView|HttpURLConnection|CookieManager/);
assert.doesNotMatch(workspace, /VerifiedLesson\(/);
assert.match(main, /ProfileSettingsPage\(/, '我的页面应接入唯一原生设置路由');
assert.match(profileSettings, /ConnectionsPanel\(client, openWebsite, openLocalSchool,/,
  '已有真实授权面板应仅被复用于数据连接二级页');
// 消息页由 MessageCenterPanel 承担：公开公告 + 微信待审核计数，两个旧面板已并入。
assert.match(main, /MessageCenterPanel\(cloud, openWebsite, openArticle,/);
assert.match(panels, /PasswordVisualTransformation/);
assert.match(panels, /fun ConnectionsPanel\(/);
assert.equal((panels.match(/\bLocalSchoolConnectionPanel\s*\(/g) || []).length, 1,
  'Native school connection content must not be duplicated for different screen widths');
assert.equal((panels.match(/\bCloudAuthorizationSection\s*\(/g) || []).length, 2,
  'Cloud authorization must have exactly one definition and one responsive invocation');
assert.match(panels, /val localConnections: @Composable \(\) -> Unit = \{/,
  'Responsive native connection content must be shared');
assert.match(panels, /val cloudConnections: @Composable \(\) -> Unit = \{/,
  'Responsive cloud authorization content must be shared');
// The owner can begin each school's permitted local read without cloud login
// or first navigating to Xiaoxu's website. Cloud authorization remains optional.
assert.match(panels, /var cloudAdvanced by rememberSaveable \{ mutableStateOf\(false\) \}/);
assert.ok(
    panels.indexOf('LocalSchoolConnectionPanel(') <
        panels.indexOf('if (cloudAdvanced)'),
    '本机官方页面读取必须先于云端可选授权展示'
);
const nativeSchoolPanel = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/LocalSchoolPanels.kt');
assert.match(nativeSchoolPanel, /在 App 中打开学习通/);
assert.match(nativeSchoolPanel, /在 App 中导入青果课表/);
assert.match(nativeSchoolPanel, /连接第二课堂／刷新积分/);
assert.doesNotMatch(main, /var selectedTool by rememberSaveable|private fun ConnectionsPage\(/,
  '旧版工具弹窗和混合式连接入口应彻底退役');
assert.match(profileSettings, /"home" ->[\s\S]*?"connections" -> item/,
  '设置按分类索引；业务内容应进入独立二级路由');
assert.match(profileSettings, /"notifications" -> item \{ NotificationSettingsContent\(localTasks\) \}/);
assert.match(profileSettings, /"backup" -> item \{ LocalTaskBackupPanel\(\) \}/);
assert.match(profileSettings, /"licenses" -> item \{ OpenSourceCreditsPanel\(\) \}/);
assert.doesNotMatch(profileSettings, /第二课堂记录|selectedTool/,
  '第二课堂记录属于业务页，不回流设置首页');

assert.match(panels, /openLocalSchool\(id\)/);
assert.match(panels, /只有一台手机？打开学习通官网/);
assert.match(panels, /成绩读取待接入/,
  '青果连接保留，旧课表同步不得冒充成绩读取');
assert.doesNotMatch(panels, /"ncwu" -> "本机打开青果官方课表"/);
assert.match(panels, /本机打开第二课堂官网/);
assert.match(panels, /本机官网读取仅保存当前可见记录/);
assert.match(cloud, /val connectionTrusted = state.platforms.firstOrNull/);
assert.match(cloud, /val tasksFresh: Boolean = false/);
assert.match(cloud, /state = state\.copy\(tasksFresh = false, connectionsFresh = false,/);
assert.match(cloud, /tasksFresh = tasks != null/);
assert.match(cloud, /!fresh -> "云端任务清单刷新失败/, '批量核验必须披露任务清单刷新失败');
assert.match(cloud, /tasksFresh = false, connectionsFresh = false,/);
assert.match(workspace, /!state\.tasksFresh && state\.tasks\.isNotEmpty\(\)/,
  '云端任务快照新鲜度必须由统一任务中心说明');
assert.match(workspace, /含上次云端快照/,
  '旧快照提醒不可因删除嵌套列表而消失');
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
assert.match(panels, /本次无法核验连接状态/);
assert.match(panels, /val canRead = state\.authenticated && state\.connectionsFresh/);
assert.match(cloud, /clearAccountSnapshots/);
assert.doesNotMatch(panels, /schoolPassword|CookieManager|WebView|READ_SMS/);

assert.doesNotMatch(cloud + panels, /addJavascriptInterface|READ_SMS|READ_CONTACTS|SCHOOL_SESSION_KEY|OWNER_LOGIN_SECRET\s*=/);
assert.doesNotMatch(cloud, /http:\/\/|https:\/\/[^\s"]+school/);

const coursePanel = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ChaoxingHomeworkPanel.kt');
const batchAudit = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/ChaoxingBatchAudit.kt');
assert.match(batchAudit, /enum class ChaoxingCourseCoverage \{ COUNT_VERIFIED, LIST_ONLY, FAILED, NOT_CHECKED \}/,
  '必须区分所有课程条数已核验与仅逐项列表状态');
assert.match(batchAudit, /audit\.map \{ it\.courseKey \}\.distinct\(\)\.size == totalCourses/,
  '重复课程不能冒充已经完整覆盖');
assert.match(batchAudit, /countVerified == totalCourses &&\s*taskLedgerFresh/,
  '只有全部课程条数可信且任务清单刷新成功才能声明覆盖完整');
assert.match(cloud, /chaoxingBatchAudit: List<ChaoxingCourseAudit>/,
  '云端只读批量核验结果必须在当前会话中保留结构化证据');
assert.match(cloud, /if \(progress != null\) ChaoxingCourseCoverage\.COUNT_VERIFIED\s*else ChaoxingCourseCoverage\.LIST_ONLY/,
  '缺失官网作业总数时不允许宣称本课程完整核验');
assert.match(cloud, /ChaoxingCourseCoverage\.NOT_CHECKED, emptyList\(\)/,
  '未检查课程必须显式列出，不能冒充零作业');
assert.match(cloud, /if \(epoch != sessionEpoch \|\| !state\.authenticated\) return/,
  '学校授权失效后不得恢复旧会话里的审核账本');
assert.match(workspace, /title = "学习通作业"/,
  '官方作业读取入口必须直接出现在真实任务页');
assert.match(workspace, /state\.chaoxingBatchCheckedAt != null -> "部分已读取"/,
  '学习通部分读取不得冒充全部课程已核验');
assert.match(workspace, /auditSummary\.fullyCoveredAndRefreshed -> "全部课程条数已核验"/,
  '只有证据完整且刷新成功才显示完整核验');
assert.match(workspace, /title = "本机读取",\s*count = capturedHomework\.size/,
  '本机页面记录需保留入口及真实数量');
assert.doesNotMatch(workspace, /title = "学习通与本机页面快照"/,
  '正式任务首页不得继续显示面向开发的旧卡片标题');
assert.match(workspace, /summarizeChaoxingAudit\(state\.chaoxingBatchAudit/,
  '任务页报告必须从真实逐门证据计算，禁止手填演示课程');
assert.match(workspace, /if \(showAllOfficialHomework\) officialRows\.size/,
  '用户必须可以展开全部已读取作业而非静默只展示前30项');
assert.doesNotMatch(coursePanel, /其他记录请在校序任务列表查看/,
  '本课程未展开的记录不得谎称已经自动并入本机任务清单');

assert.match(workspace, /ChaoxingHomeworkPanel\(client, openSchoolReader = \{ openLocalSchool\("chaoxing"\) \}\)/);
assert.match(coursePanel, /fun ChaoxingHomeworkPanel\(client: CloudClient, openSchoolReader: \(\) -> Unit\)/);
assert.match(coursePanel, /XiaoxuCard\(modifier = Modifier\.fillMaxWidth\(\), contentPadding = 0\.dp\)/,
  '学习通课业页面必须复用统一玻璃卡片');
assert.match(coursePanel, /state\.chaoxingPreview\?\.let \{ preview ->\s*XiaoxuSheet\(/,
  '只读作业详情必须走共享浮层，不可在列表内嵌旧方形 Card');
assert.match(coursePanel, /onDismiss = \{ client\.clearChaoxingDetail\(\) \}/,
  '学习通详情可返回关闭并清理只读预览状态');
assert.doesNotMatch(coursePanel, /\bCardDefaults\b|\bCard\s*\(/,
  '不得恢复第二套独立 Material3 作业卡片');
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
assert.match(cloud,/courses\.filterNot \{ it\.key in checkedKeys \}/, '中断后需要明确列出未检查课程');
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
assert.equal(existsSync(new URL('../apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/SecondClassPanel.kt', import.meta.url)), false,
  '已退役的重复第二课堂云端快照页面不可重新加入源码');
assert.doesNotMatch(main, /SecondClassPanel\(cloud, openWebsite\)/,
  '第二课堂个人记录不应返回我的设置入口');
const secondClassMain = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/SecondClassPage.kt');
assert.match(main, /AcademicDestination\.SECOND_CLASS\s*->\s*tabStateHolder\.SaveableStateProvider\("second_class"\)\s*\{\s*SecondClassPage\(\)/, '第二课堂依然沿用保存页面状态的真实内容入口');
assert.match(secondClassMain, /XiaoxuSegmentedControl\(\s*options = sectionOptions/, '二课必须使用唯一共享分段组件');
assert.match(secondClassMain, /XiaoxuExpandableGlassRow\(/,
  '真实二课积分与活动支持统一玻璃式展开');
assert.match(secondClassMain, /row\.details\.isNotBlank\(\)/,
  '真实详情不存在时不创建假展开内容');
for (const [key, label] of [['0', '活动'], ['1', '我的活动'], ['2', '积分']]) {
  assert.ok(secondClassMain.includes(`XiaoxuSegmentOption("${key}", "${label}")`),
    '二课三个真实业务分区必须保留：' + label);
}
assert.doesNotMatch(secondClassMain, /ScrollableTabRow|TabRow\(/, '二课不可恢复网站式标签栏');
assert.match(secondClassMain, /Text\("学校官网"\)/, '学校页面按需打开');
assert.match(secondClassMain, /IconButton\(onClick = ::refreshPersonalRecords, enabled = !refreshing\)/,
  '第二课堂从学校读取入口与分类栏同列且只保留一个刷新按钮');
assert.match(secondClassMain, /activities\.filter \{ it\.kind == "activity" \}/,
  '汇总标题不可显示为个人活动卡片');
assert.doesNotMatch(secondClassMain, /Text\(if \(refreshing\) "读取中…" else "刷新"\)/,
  '禁止回退为内容列表上方的重复刷新文字');
assert.match(secondClassMain, /Intent\(context, NativeSchoolConnectActivity::class\.java\)/);
assert.match(secondClassMain, /\.putExtra\(NativeSchoolConnectActivity\.EXTRA_PROVIDER, "second_class"\)/);



const localCatalogParser = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalSecondClassCatalogEmpty.kt');
const localReader = read('apps/xiaoxu-android/app/src/main/assets/ncwu_secondclass_local.js');
const localNativeCatalog = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/NativeSchoolConnectActivity.kt');
const secondClassDao = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/NativeSchoolRecords.kt');
assert.match(secondClassMain, /"活动暂未获取"/, '未同步不能虚构暂无活动');
assert.doesNotMatch(secondClassMain, /发现活动|学校活动广场尚未完成本机读取|个人已报名活动不是活动广场|TabRow\(|FilterChip\(/,
 '二课不能恢复网站式导航与说明卡');
assert.match(secondClassMain, /"catalog_empty"/);
assert.match(secondClassMain, /catalogRows\.size == 3/);
assert.match(secondClassMain, /selectedKey = catalogFilter\.toString\(\)/,
 '活动筛选继续保持所选官方目录类别');
assert.match(secondClassMain, /XiaoxuSegmentOption\("0", "报名中"\)/,
 '活动类别须保留报名中筛选');
assert.match(designSystem, /Modifier\.weight\(1f\)\.heightIn\(min = 44\.dp\)/,
 '共享分段控件必须等宽且具有至少44dp触控高度');
assert.match(designSystem, /titleLarge = TextStyle\(fontSize = 20\.sp, lineHeight = 26\.sp/,
 '一级页面共用20sp字级与26sp行高');
assert.match(scheduleUi, /"我的课表"[\s\S]*?style = MaterialTheme\.typography\.titleLarge/,
 '课表标题从统一主题取得字级');
assert.match(messageUi, /text = "消息中心",\s*style = MaterialTheme\.typography\.titleLarge/,
 '消息标题从统一主题取得字级');
assert.match(workspace, /Text\("任务中心",[\s\S]*?style = MaterialTheme\.typography\.titleLarge/,
 '任务标题从统一主题取得字级');
assert.match(workspace, /\.heightIn\(min = 56\.dp\)/,
 '三态数字同卡保留内容且压缩多余高度');
assert.match(designSystem, /internal fun XiaoxuToolbarAction\(/,
 '任务顶部四个工具按钮必须复用唯一组件');
assert.match(designSystem, /modifier = Modifier\.size\(42\.dp\)/,
 '共享工具按钮必须具有统一42dp几何尺寸');
assert.equal((workspace.match(/XiaoxuToolbarAction\(/g) || []).length, 4,
 '任务中心顶部恰好使用四个统一工具按钮');
assert.match(secondClassMain, /refreshPersonalRecords\(\)/, '授权读取仍有效');
assert.match(secondClassMain, /if \(record != null\) "上次读取：暂无活动" else "活动暂未获取"/,
 '只有已核验空结果可标暂无活动，并注明上次读取');
assert.match(secondClassMain, /record\.capturedAt/, '空结果必须关联真实读取时间');

assert.match(localCatalogParser, /parseLocalSecondClassCatalogEmpty\(/);
assert.match(localCatalogParser, /catalogStatus/);
assert.match(localCatalogParser, /"verified_empty"/);
assert.match(localCatalogParser, /catalogEmptyFilters/);
assert.match(localReader, /includeCatalog = false/);
assert.match(localReader, /catalogPath='\/sys\/stu\/activity'/);
assert.match(localReader, /catalogStatus='nonempty_unverified'/);
assert.match(localNativeCatalog, /parseLocalSecondClassCatalogEmpty\(payload, rows\.first\(\)\.capturedAt\)/);
assert.match(localNativeCatalog, /replaceSecondClassSnapshot\(rows, activities, catalog, accountHash = accountHash\)/);
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

assert.match(secondClassMain, /candidates\.all \{ it\.capturedAt == batch\.capturedAt \}/);
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
const schedulePanel = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/ScheduleScreen.kt');
const nativeWeekGrid = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/WeeklyTimetableGrid.kt');
const localTimetableEntry = schedulePanel.slice(
    schedulePanel.indexOf('fun CloudTimetablePanel('),
    schedulePanel.indexOf('private fun ScheduleVerifiedContent(')
);
assert.match(schedulePanel, /if \(data\.lessons\.isEmpty\(\)\)/,
  '无本机课程走唯一课表设置宿主的空态');
assert.match(schedulePanel, /LocalTimetableEmptyState\(/);
assert.match(localTimetableEntry, /onMessages = onMessages/, '课表消息入口由唯一内容页处理');
assert.match(localTimetableEntry, /localImportedScheduleView\(localLessons\)/);
assert.doesNotMatch(localTimetableEntry, /deriveScheduleUiState\(state\)|state\.lessons|client\.refresh\(\)|ScheduleQuickActions\(/,
  '课表不依赖云端、没有旧版重复登录/同步动作');
assert.match(schedulePanel, /Text\("打开课表设置"\)/);
assert.doesNotMatch(schedulePanel, /label = "重新导入课表"/);
assert.match(schedulePanel, /label = "新增课程"/);
assert.match(schedulePanel, /label = "课表设置"/);
const timetableSettings = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/SemesterSettingsDialog.kt');
const timetablePrefs = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/data/LocalSemesterConfigStore.kt');
assert.doesNotMatch(timetableSettings, /SettingsGroupTitle\("课程提醒"\)/,
  '全局上课提醒开关只能保留在通知与提醒二级页');
assert.match(profileSettings, /LocalCourseReminders\.setEnabled\(context, value\)/,
  '全局开关必须继续写入现有本地提醒设置');
assert.match(profileSettings, /ActivityResultContracts\.RequestPermission\(\)/,
  'Android 13+ 运行时通知权限应按用户操作申请');
assert.match(profileSettings, /ACTION_APP_NOTIFICATION_SETTINGS/,
  '通知权限只作为通知二级页内的系统入口');
assert.match(timetableSettings, /internal fun SemesterSettingsPage\(/,
  '课表设置必须是同 Activity 内原生子页面');
const timetablePageSource = timetableSettings.slice(
  timetableSettings.indexOf('internal fun SemesterSettingsPage('),
  timetableSettings.indexOf('// Use Material3 date/time controls')
);
assert.doesNotMatch(timetablePageSource, /Dialog\(|systemBarsPadding\(/,
  '整页设置不能再创建额外的全屏 Window 和状态栏灰色遮罩');
assert.match(timetablePageSource, /Modifier\.fillMaxSize\(\)/,
  '课表设置需要填满当前主导航内容窗口');
assert.match(schedulePanel, /targetState = settingsOpen/,
  '课表和设置应复用一个动画内容宿主');
assert.match(schedulePanel, /label = "timetableSettingsRoute"/,
  '课表设置推进和返回应具有明确的动画入口');
assert.match(schedulePanel, /xiaoxuSettingsRouteTransition\(/,
  '课表设置应复用统一阻尼推进与返回动画');
assert.match(schedulePanel, /if \(!animatedSettingsOpen\) \{/,
  '默认课表与设置按动画目标状态互斥，不得额外叠加 Dialog');
assert.match(schedulePanel, /if \(animatedSettingsOpen\) \{\s*SemesterSettingsPage\(/,
  '课表设置应在同窗口中绘制，不得再建独立 Window');
assert.match(main, /var scheduleSettingsOpen by rememberSaveable \{ mutableStateOf\(false\) \}/,
  '导航层应保存课表子页面状态');
assert.match(main, /BackHandler\(enabled = current == MainDestination\.SCHEDULE && scheduleSettingsOpen\)/,
  '安卓系统返回键应从课表设置回到课表');
assert.match(main, /settingsOpen = scheduleSettingsOpen,\s*onSettingsOpenChanged = \{/,
  '课表路由状态必须由唯一 NativeShell 维护');
assert.match(main, /MainDestination\.PROFILE -> \{\s*if \(profileSettingsRoute == "timetable-settings"/,
  '课表设置须作为我的原生二级路由，不切换主标签页');
assert.match(main, /settingsOpen = profileSettingsRoute == "timetable-settings"/,
  '课表设置应直接渲染同一课表设置组件，不能先露出周视图');
assert.match(main, /onScheduleSettings = \{\s*profileSettingsRoute = "timetable-settings"\s*\}/,
  '进入课表设置只切换内部路由');
assert.doesNotMatch(main, /onScheduleSettings = \{\s*current = MainDestination\.SCHEDULE/,
  '禁止设置入口触发主导航页切换和玻璃残影');
assert.match(main, /onEditorClosed = \{ profileSettingsRoute = "home" \}/,
  '从设置添加课程完成或取消应回到设置');
assert.doesNotMatch(main, /scheduleSettingsFromProfile/,
  '删去旧跨主标签路由的额外状态');
assert.match(main, /profileSettingsRoute != "home"/,
  '设置二级页应处理系统返回并收起主导航');
assert.match(main, /val showPrimaryNavigation = !messagesOpen &&[\s\S]*?!\(current == MainDestination\.SCHEDULE && scheduleSettingsOpen\) &&[\s\S]*?!\(current == MainDestination\.PROFILE && profileSettingsRoute != "home"\)/,
  '主导航需共用唯一显隐判断，所有二级页都不能露出底栏或背景采样');
assert.equal((main.match(/if \(!tabletLayout && showPrimaryNavigation\)/g) || []).length, 2,
  '底栏本体与 Scaffold 占位必须同时使用统一显隐标记');
assert.match(main, /if \(tabletLayout && showPrimaryNavigation\)/,
  '平板侧栏也遵守相同子页面规则');
assert.match(main, /navBackdrop = if \(!tabletLayout && showPrimaryNavigation/,
  '没有底栏时不得额外保留全屏玻璃采样层');
assert.match(main, /profileSettingsRoute == "timetable-editor"/,
  '新增课程编辑页应保留独立二级路由，不能回退到跨标签状态');
assert.match(main, /onSettingsFileAction = \{ profileSettingsRoute = "home" \}/,
  '导入导出只需要退出到设置，不必分派旧字符串动作');
assert.doesNotMatch(main, /onSettingsAction|timetable-action/,
  '旧字符串动作分发和泛化二级页状态应彻底退场');
const courseEditor = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/CourseEditorDialog.kt');
assert.match(courseEditor, /Dialog\(onDismissRequest[\s\S]*DialogProperties\(usePlatformDefaultWidth = false\)/,
  '课程编辑须与课表设置共享原生全屏页面位置，不回退到系统表单弹框');
assert.match(courseEditor, /\.background\(XiaoxuPalette\.PageGradient\)/,
  '课程编辑背景必须沿用现有课表设置背景');
assert.match(courseEditor, /XiaoxuGroupedSection\(title = "课程信息"\)/,
  '课程信息必须复用同一玻璃分组组件');
assert.match(courseEditor, /XiaoxuGroupedSection\(title = "上课时间"\)/,
  '编辑节次不能再出现孤立弹框');
assert.match(courseEditor, /XiaoxuSegmentedControl\(/,
  '编辑课程星期须使用与课表设置同一套原生分段选择组件');
assert.match(courseEditor, /selectedKey = weekday/,
  '星期选中态必须来自正在编辑的真实课程');
assert.match(courseEditor, /onSelected = \{ weekday = it \}/,
  '选择星期必须更新已有课程字段而非仅展示');
assert.match(courseEditor, /XiaoxuGroupedSection\(title = "上课周次"\)/);
assert.match(courseEditor, /onDelete\(weekOnly && canScope\)/,
  '保留已确认的仅删除选中周次行为');
assert.match(courseEditor, /onSave\(it, weekOnly\)/,
  '表单仍须连接已有本机课程编辑回调');
assert.doesNotMatch(courseEditor, /\n    AlertDialog\(\s*onDismissRequest = \{ if \(!busy\) onDismiss\(\) \}/,
  '主编辑页不能再使用 AlertDialog');
const dayCardSource = schedulePanel.slice(
  schedulePanel.indexOf('private fun LessonCard('),
  schedulePanel.indexOf('/** 首页与完整课表共用')
);
assert.match(dayCardSource, /onClickLabel = "查看课程详情"/,
  '点击日视图课程应打开与周视图共用的详情抽屉');
assert.match(dayCardSource, /Icons\.Outlined\.ChevronRight/,
  '日视图不要用向下箭头暗示不存在的卡内展开');
assert.doesNotMatch(dayCardSource, /Icons\.Outlined\.ExpandMore/,
  '不要误导用户课程卡向下展开');
assert.match(schedulePanel, /color = XiaoxuPalette\.Ink2[\s\S]*?val openCourse:/,
  '顶部日期信息需使用统一的次级文字颜色');
assert.match(schedulePanel, /mutableStateOf\(LocalSemesterConfigStore\.loadViewMode\(context\)\)/);
assert.match(schedulePanel, /initialViewMode = viewMode/);
assert.match(schedulePanel, /initialSections = visibleSections/);
assert.match(schedulePanel, /dailySections = visibleSections/);
assert.match(schedulePanel, /LocalSemesterConfigStore\.saveViewMode\(context, newViewMode\)/);
assert.doesNotMatch(schedulePanel, /ScheduleViewModeTabs\(|private fun ViewModeTab\(/,
  '周视图和日视图不得再占用课表顶部');
assert.match(timetableSettings, /XiaoxuSegmentOption\("week", "周视图"\)/,
 '设置必须保留周视图选项');
assert.match(timetableSettings, /XiaoxuSegmentOption\("day", "日视图"\)/,
 '设置必须保留日视图选项');
assert.match(timetableSettings, /XiaoxuSegmentedControl\(/,
 '设置与其它页面必须复用同一原生分段控件');
assert.match(timetableSettings, /selectedKey = viewMode/,
 '视图选中态必须来自真实用户偏好');
assert.match(timetableSettings, /onSelected = \{ viewMode = it \}/,
 '点击分段项应更新设置草稿，保存动作仍由完成按钮触发');
assert.match(timetableSettings, /onSave\(config, viewMode, sections\)/,
 '周日偏好与学期数据必须通过原保存回调提交');
assert.match(timetableSettings, /DatePicker\(state = dateState/,
  '日期选择须使用统一 Compose 控件');
assert.match(timetableSettings, /TimePicker\(state = pickerState\)/,
  '节次时间选择须使用统一 Compose 控件');
assert.match(timetableSettings, /if \(sectionsMenu \|\| weeksMenu\)/,
  '节数与周数弹层需统一为可滚动玻璃选择器');
assert.doesNotMatch(timetableSettings, /android\.app\.(DatePickerDialog|TimePickerDialog)|DropdownMenu\(/,
  '课表设置不可重新混用系统旧弹窗或漂浮长菜单');
assert.match(timetableSettings, /monday\.isBlank\(\) && weeks\.isBlank\(\) && times\.isBlank\(\)/,
  '未设置学期时仍应允许保存视图偏好');
assert.match(timetableSettings, /onSave\(config, viewMode, sections\)/);
assert.match(timetablePrefs, /fun loadViewMode\(context: Context\)/);
assert.match(timetablePrefs, /fun saveViewMode\(context: Context, mode: String\)/);
assert.match(timetablePrefs, /putString\(VIEW_MODE_KEY, mode\)\.apply\(\)/);
assert.match(timetablePrefs, /fun loadSections\(context: Context\)/);
assert.match(timetablePrefs, /fun saveSections\(context: Context, count: Int\)/);
assert.match(schedulePanel, /Text\("课程详情"/);
assert.match(schedulePanel, /Text\("课程提醒"/);
assert.match(schedulePanel, /enabledForCourse\(context, lesson\.sourceId\)/);
assert.match(schedulePanel, /ContextCompat\.checkSelfPermission\(/,
  '课程提醒必须区分Android通知运行时权限与通知总开关');
assert.match(schedulePanel, /AndroidSettings\.ACTION_APP_NOTIFICATION_SETTINGS/,
  '已经授权但系统通知被禁用时，应打开通知设置而不是重复请求权限');
assert.match(schedulePanel, /context\.startActivity\(settings\)/,
  '通知设置入口必须真正连接系统设置');
const timetableSettingsCallback = schedulePanel.slice(
  schedulePanel.indexOf('if (animatedSettingsOpen) {'),
  schedulePanel.indexOf('private fun DayLessonList(')
);
assert.match(timetableSettingsCallback, /LocalCourseReminders\.anyEnabled\(context\)/,
  '修改学期后只有已经启用的提醒需要重新规划');
assert.match(timetableSettingsCallback, /LocalCourseReminders\.reconcileAfterSave\(/,
  '保存学期作息仍需更新已存在的单课程提醒');
assert.doesNotMatch(timetableSettingsCallback,
  /LocalCourseReminders\.setEnabled\(|reminderPermissionLauncher|ACTION_APP_NOTIFICATION_SETTINGS/,
  '课表保存不可重复修改全局提醒状态或再申请通知权限');
assert.doesNotMatch(timetableSettings, /reminderEnabled: Boolean|wantReminders/,
  '退役的课表提醒开关及其保存参数必须同时删除');

const weekPickerBody = schedulePanel.slice(
  schedulePanel.indexOf('private fun WeekSelector('),
  schedulePanel.indexOf('private fun SelectablePill(')
);
assert.match(weekPickerBody, /rememberLazyListState\(/,
  '周次选择条必须保存真实横向滚动状态');
assert.match(weekPickerBody, /LaunchedEffect\(weeks, selected\)/,
  '第10周等当前选中周次必须自动定位至可见范围');
assert.match(weekPickerBody, /weekListState\.scrollToItem\(/,
  '周次列表必须随选中项滚动，而非永远从第1周开始');
assert.match(weekPickerBody, /LazyRow\(state = weekListState/,
  '周次列表必须绑定滚动状态');
const settingsHeaderError = timetableSettings.slice(
  timetableSettings.indexOf('internal fun SemesterSettingsPage('),
  timetableSettings.indexOf('SettingsGroupTitle("学期与显示")')
);
assert.match(settingsHeaderError, /if \(error != null\) \{/,
  '设置验证错误须在顶部完成按钮附近显示');
assert.match(timetableSettings, /hasError = errorField == "weeks"/,
  '学期周数错误须高亮对应设置行');
assert.match(timetableSettings, /hasError = errorField == "date"/,
  '学期日期错误须高亮对应设置行');
assert.doesNotMatch(timetableSettings, /modifier = Modifier\.padding\(top = 14\.dp\)/,
  '设置错误不得仅显示在节次列表末尾');

assert.match(schedulePanel, /showWeekPicker by rememberSaveable/);
assert.doesNotMatch(schedulePanel, /"全部周次 ▾"|"显示全部周次"|label = "全部",\s*isSelected = selected == ALL_WEEKS/,
  '课表不得暴露无用途的整学期混合周次入口');
assert.match(schedulePanel, /if \(weekNumbers\.isNotEmpty\(\) && selectedWeek !in weekNumbers\)/,
  '导入课程或变更教学周后必须回到有效的具体周次');
assert.match(schedulePanel, /Text\(if \(currentIndex >= 0\) "第 \$selectedWeek 周 ▾" else "选择周次 ▾"/,
  '周次栏必须以具体教学周为主要状态');

assert.match(schedulePanel, /val currentIndex = weekNumbers.indexOf\(selectedWeek\)/);
assert.match(schedulePanel, /selectedWeek = weekNumbers\[currentIndex - 1\]/);
assert.match(schedulePanel, /Icons\.Outlined\.ChevronLeft/);
assert.match(schedulePanel, /selectedWeek = weekNumbers\[currentIndex \+ 1\]/);
assert.match(schedulePanel, /Icons\.Outlined\.ChevronRight/);
assert.match(schedulePanel, /data\.lessons\.filter \{ selectedWeek in it\.weeks \}/);
assert.match(schedulePanel, /val effectiveWeekday = selectedWeekday/,
  '用户选了空白星期不能自动跳到另一天');
assert.match(schedulePanel, /selectedWeekday = tabletWeekday/,
  '平板需要保持七日网格选中状态与侧栏一致');
assert.match(schedulePanel, /onSelectWeekday = \{ selectedWeekday = it \}/);
assert.match(schedulePanel, /WeekdayStrip\(/);
assert.match(schedulePanel, /WeeklyTimetableGrid\(/);
assert.match(schedulePanel, /DayLessonList\(/);
assert.match(schedulePanel, /CourseDetailBottomSheet\(/);
assert.match(schedulePanel, /ModalBottomSheet\(/);
assert.match(schedulePanel, /filtered\.firstOrNull \{ it\.key == selectedCourseKey \}/,
  '课程详情应引用当前筛选真实课程');
assert.doesNotMatch(schedulePanel, /VerifiedLesson\(/, '不得在界面层写死课程');
assert.doesNotMatch(schedulePanel, /HttpURLConnection|openConnection|WebView|addJavascriptInterface|CookieManager/);
assert.match(nativeWeekGrid, /lessons: List<VerifiedLesson>/);
// Indexed occupancy is equivalent to the former per-cell lesson filter, while
// avoiding a full list scan for every slot during Compose recompositions.
assert.match(nativeWeekGrid, /for \(section in lesson\.startSection\.coerceAtLeast\(1\)\.\.lesson\.endSection\.coerceAtMost\(maxSection\)\)/);
assert.match(nativeWeekGrid, /buckets\[lesson\.weekday\]\[section\]\.add\(lesson\)/);
assert.match(nativeWeekGrid, /val available = occupancy\[day\]\[section\]/);
assert.match(nativeWeekGrid, /val next = occupancy\[day\]\[probe\]/);
assert.match(nativeWeekGrid, /for \(day in 1\.\.7\)/);
assert.match(nativeWeekGrid, /onLessonClick\(first\)/);
assert.match(nativeWeekGrid, /val dayWidth = if \(compact\) \(maxWidth - labelWidth - 8\.dp\) \/ 7/,
  '周日不能被挤出屏幕：七日按当前设备宽度分配');
assert.match(nativeWeekGrid, /first\.teacher\.isNotBlank\(\)/);
assert.match(nativeWeekGrid, /if \(next != available\) break/,
  '不同课程交叠时必须分段而非合成一张假课程');
assert.match(nativeWeekGrid, /overlapChoices = available/);
assert.match(nativeWeekGrid, /role = Role\.Tab/);
assert.doesNotMatch(nativeWeekGrid, /电路基础|E103|演示数据|faker/i);
assert.match(main, /onRefresh = if \(current == MainDestination\.SCHEDULE\) null/,
  '课表顶栏不显示云端刷新入口');
assert.match(main, /AcademicDestination\.GRADES -> Content\(topPadding = 6\.dp\)/,
  '成绩页面入口仍为独立原生内容页');
assert.match(main, /AcademicDestination\.EXAMS -> Content\(topPadding = 6\.dp\)/,
  '考试页面与成绩统一页内间距');
const academicGradeArea = main.slice(main.indexOf('AcademicDestination.GRADES -> Content('),
    main.indexOf('AcademicDestination.SERVICES -> Content('));
assert.equal((academicGradeArea.match(/XiaoxuEmpty\(/g) || []).length, 2,
  '成绩与考试须使用唯一的玻璃空态组件，禁止两套自绘白卡');
assert.match(academicGradeArea, /title = "成绩尚未读取"/,
  '成绩未取得本人真实数据时明确为空态');
assert.match(academicGradeArea, /title = "考试安排尚未读取"/,
  '考试未读取前不能显示模拟考试安排');
assert.match(academicGradeArea, /actionLabel = "连接青果教务",\s*onAction = openProfile/,
  '成绩操作必须打开现有学校连接中心');
assert.match(academicGradeArea, /actionLabel = "查看数据连接",\s*onAction = openProfile/,
  '考试操作必须打开现有学校连接中心');
assert.doesNotMatch(academicGradeArea, /DemoData|mockGrades|fakeExam/,
  '未核验数据不能混入成绩或考试页面');

const statusTabSource = workspace.slice(workspace.indexOf('private fun TaskStatusTab('),
    workspace.indexOf('/** 轻量分组标题',workspace.indexOf('private fun TaskStatusTab(')));
assert.match(statusTabSource, /selectable\(selected = active, role = Role\.Tab, onClick = onClick\)/,
  '任务三态筛选须具备可访问的选中态，不只是视觉高亮');
assert.match(workspace, /\.selectableGroup\(\)/,
  '任务三态筛选须成组供无障碍导航');
assert.match(statusTabSource, /XiaoxuRadius\.tile/,
  '任务筛选圆角必须复用唯一设计系统');
// messageUi is loaded before these assertions; messageCenter is declared later.
const messageSearchSource = messageUi.slice(
    messageUi.indexOf('private fun MessageSearchField('),
    messageUi.indexOf('private fun FilterRow('));
assert.match(messageSearchSource, /focusedContainerColor = XiaoxuPalette\.GlassCard/,
  '消息搜索框需与列表共用玻璃材质');
assert.match(messageSearchSource, /GlassBorder/,
  '消息搜索框描边须取自唯一组件调色板');
assert.equal((timetableSettings.match(/XiaoxuGroupSurface\s*\{/g) || []).length, 3,
  '课表设置的三个分组必须直接复用设计系统的玻璃容器');
assert.doesNotMatch(timetableSettings, /\b(?:fun|class)\s+SettingsSurface\b/,
  '课表设置不得恢复已移除的重复分组包装');
const groupedGlassSource = designSystem.slice(
    designSystem.indexOf('internal fun XiaoxuGroupSurface('),
    designSystem.indexOf('/** 连续分组设置', designSystem.indexOf('internal fun XiaoxuGroupSurface(')));
assert.match(groupedGlassSource, /RoundedCornerShape\(XiaoxuRadius\.card\)/,
  '课表设置分组圆角必须与所有设置页同源');
assert.match(groupedGlassSource, /background\(XiaoxuPalette\.GlassCard\)/,
  '课表设置分组使用同一玻璃材质，但不改变原版弹出位置与文字');
assert.match(groupedGlassSource, /border\(XiaoxuSpacing\.glassStroke, XiaoxuPalette\.GlassBorder/,
  '课表设置分组边界必须使用轻玻璃描边');
assert.match(designSystem, /XiaoxuGroupSurface\(verticalPadding = 4\.dp, content = content\)/,
  '普通分组继续保持原有上下4dp内边距；课表设置独立保持0dp');



// 消息中心：公开公告与微信待审核分开，失败/过期要显式提示，不伪造正文。
const messageCenter = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/MessageCenter.kt');
assert.doesNotMatch(messageCenter, /private val TileShape\s*=/,
  '消息中心不得恢复未使用的页面级形状常量');
assert.match(messageCenter, /fun filterNotices\(/);
assert.match(messageCenter, /state\.notices/);
assert.match(messageCenter, /state\.pendingWechatCount/);
assert.match(messageCenter, /state\.publicError/);
assert.match(messageCenter, /noticesPartial/);
assert.match(messageCenter, /openArticle\(notice\.url\)/);
assert.match(messageCenter, /未连接校序云端/);
assert.doesNotMatch(messageCenter, /HttpURLConnection|openConnection|WebView|addJavascriptInterface|CookieManager|document\.cookie/);
assert.doesNotMatch(messageCenter, /AcademicNotice\(/, '不得在界面层手写公告对象冒充真实数据');

const messagePillSource = messageCenter.slice(
  messageCenter.indexOf('private fun MessagePill('),
  messageCenter.indexOf('private fun MessageCountLine('));
assert.match(messagePillSource, /XiaoxuPalette\.GlassTrack/,
  '官方消息筛选未选中态与导航共用轻玻璃材质');
assert.match(messagePillSource, /XiaoxuSpacing\.glassStroke/,
  '官方消息筛选不可恢复旧1dp灰色硬描边');

const skeletonSource = messageCenter.slice(
  messageCenter.indexOf('private fun MessageSkeleton('));
assert.match(skeletonSource, /height\(76\.dp\)/,
  '消息占位不应在内容为空时占据过厚的灰色长卡');
assert.match(skeletonSource, /background\(XiaoxuPalette\.GlassTrack\)/,
  '消息加载占位必须采用统一玻璃轨道材质');
assert.match(messageCenter, /container = MaterialTheme\.colorScheme\.errorContainer/,
  '云端公告读取失败仍显示明确的错误语义');

const messageBannerSource = messageCenter.slice(
  messageCenter.indexOf('private fun MessageBanner('),
  messageCenter.indexOf('private fun MessageErrorCard('));
assert.match(messageBannerSource, /XiaoxuPalette\.GlassBorder/,
  '官方通知状态横幅采用统一柔和玻璃边界');
assert.doesNotMatch(messageBannerSource, /border\(1\.dp, XiaoxuPalette\.Line/,
  '状态横幅禁止恢复旧1dp灰色硬边');
const noticeDetailSource = messageCenter.slice(
  messageCenter.indexOf('internal fun NoticeDetailSheet('),
  messageCenter.indexOf('private fun SheetMetaRow('));
assert.match(noticeDetailSource, /text = notice\.title/,
  '官方公告标题仍来自真实读取记录');
assert.doesNotMatch(noticeDetailSource, /background\(XiaoxuPalette\.BrandSoft\)/,
  '官方公告玻璃抽屉不再额外嵌套实心标题卡片');
const secondaryActionSource = panels.slice(panels.indexOf('private fun SecondaryAction('));
assert.match(secondaryActionSource, /border\(XiaoxuSpacing\.glassStroke, XiaoxuPalette\.GlassBorder/,
  '我的连接次要操作统一玻璃描边而非旧1dp灰色硬边');


// 消息中心来源分区（Issue #263）：官方公告与微信候选必须各有分区标题与真实计数，
// 顶部不能再用单一「官方来源」徽标覆盖整页。
assert.match(messageCenter, /fun pendingWechatShortLabel\(/);
assert.match(messageCenter, /state\.notices\.isNotEmpty\(\)/,
  "只有已读取的公告数量非零时，顶部来源标签才显示计数");
assert.match(messageCenter, /暂无已保存的官方公告/, "零条状态需区分尚未读取与官方确认空列表");
assert.match(messageCenter, /private fun SourceSectionHeader\(/, '需要独立来源分区标题');
assert.match(messageCenter, /XiaoxuSegmentOption\(MSG_FILTER_NOTICE, noticeTitle\)/,
  '官方通知的真实数量只在来源分段栏显示，不再堆额外标题');
assert.match(messageCenter, /title = "微信候选"/);
assert.match(messageCenter, /badge = "本人云端"/);
assert.equal((messageCenter.match(/SourceSectionHeader\(/g) || []).length, 2,
  '只有微信审核保留来源身份行，官方通知标题与数量归入单一分段栏');
assert.doesNotMatch(messageCenter, /MessageFooterNote\(/,
  '消息中心禁止持续显示面向开发人员的来源免责声明');
assert.doesNotMatch(messageCenter, /text = "官方来源"/, '顶部不得用单一官方徽标覆盖含微信候选的页面');
assert.doesNotMatch(messageCenter, /private fun SourceChip\(/,
  '来源摘要卡已退出消息主布局，防止第二套来源容器回流');
assert.ok(messageCenter.includes('val noticeTitle = "官方通知" +'),
  '官方来源分段标题必须仍显示真实公告数量');

// 公告只有标题/分类/日期/链接：入口与抽屉都不得声称"全文"或提供正文/附件。
assert.match(messageCenter, /onClickLabel = "查看这条公告的详情与官网链接"/,
  '紧凑海报式公告卡仍须打开真实官方详情');
assert.doesNotMatch(messageCenter, /查看全文/, '公告详情不得声称查看全文');
assert.match(messageCenter, /onClickLabel = "查看这条公告的详情与官网链接"/);
assert.match(messageCenter, /XiaoxuSheet\(onDismiss = onDismiss, title = "公告详情"\)/,
  '公告详情必须复用唯一玻璃弹层');
assert.doesNotMatch(messageCenter, /ModalBottomSheet\(/,
  '消息页不能再创建第二套底部弹层容器');
assert.match(messageCenter, /正文请在学校官网查看/,
  '仅有官方元数据时不编造公告正文');
const officialNoticeDetail = messageCenter.slice(
  messageCenter.indexOf('internal fun NoticeDetailSheet('),
  messageCenter.indexOf('private fun SheetMetaRow(')
);
assert.doesNotMatch(officialNoticeDetail, /text = "关闭"/,
  '公告详情仅使用共享弹层的完成按钮，不能重复关闭');
assert.doesNotMatch(messageCenter, /全部已读|提取为任务|标记为已读/, '不得展示未实现的批量操作');

// 搜索范围只覆盖官方公告，微信候选只用已有审核面板呈现。
assert.match(messageCenter, /WechatReviewPanel\(/);
assert.match(messageCenter, /pendingCount = state\.pendingWechatCount/);

// 触控与读屏：筛选 48dp、单选组 + selected、窄屏内边距与可滚动详情。
assert.match(messageCenter, /\.selectableGroup\(\)/);
assert.match(messageCenter, /\.selectable\(\s*selected = isSelected,\s*role = Role\.Tab,/);
assert.match(messageCenter, /\.heightIn\(min = 48\.dp\)/);
assert.match(messageCenter, /maxWidth < 380\.dp/);
assert.match(designSystem, /verticalScroll\(rememberScrollState\(\)\)/,
  '统一底部弹层必须支持内容滚动');
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
assert.match(profileSettings, /"backup" -> item \{ LocalTaskBackupPanel\(\) \}/,
  '本机备份与恢复只能由账户与数据二级页面打开');
assert.match(profileSettings, /SettingsEntry\("备份与恢复", Icons\.Outlined\.CheckCircle\)/,
  '本机备份仍可通过唯一入口进入');
assert.match(profileSettings, /SettingsEntry\("开源许可", Icons\.Outlined\.Info\)/,
  '开源许可从顶部应用信息进入，不在首页重复');
assert.match(profileSettings, /XiaoxuGroupedSection\(title = "账户与数据"\)/,
  '用户信息与备份等项目应采用现有玻璃分组组件');
assert.doesNotMatch(main, /if \(showLocalBackup\) item|CompactActionRow\("第二课堂记录"/,
  '移除退役的旧工具入口和向下展开式备份');
assert.doesNotMatch(profileSettings, /"工具" ->|第二课堂记录/,
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

const localSchoolUi = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/LocalSchoolPanels.kt');
assert.match(manifest, /android:name="\.NativeSchoolConnectActivity"[\s\S]*?android:exported="false"/);
assert.match(main, /NativeSchoolConnectActivity\.EXTRA_PROVIDER/);
assert.match(main, /provider == "ncwu"/);
assert.match(main, /LocalQingguoImportActivity::class\.java/);
assert.match(main, /ProfileSettingsPage\([\s\S]*?startSchoolAuthorization = startSchoolAuthorization,/,
  '我的页保留原学校授权回调，不另建授权系统');
assert.match(main, /recentImport = localCaptureReturn,\s*localTasks = localTasks,\s*onMessages = \{ messagesOpen = true \}/,
  '本人读取结果与消息入口仍要传给二级页');
assert.match(main, /ActivityResultContracts\.StartActivityForResult/);
assert.match(main, /nativeSchoolLauncher\.launch\(/);
assert.match(main, /result\.resultCode == Activity\.RESULT_OK/);
assert.match(main, /LocalSchoolImportReceipt\(/);
assert.match(main, /localCaptureReturn = lastLocalCapture/);
assert.match(main, /if \(localCaptureReturn != null\) \{/);
assert.match(main, /if \(localCaptureReturn\.provider == "chaoxing"\) MainDestination\.TASKS/);
assert.match(main, /else \{ academic = AcademicDestination\.SECOND_CLASS; MainDestination\.ACADEMIC \}/);
assert.match(panels, /startSchoolAuthorization: \(String\) -> Unit/);
assert.match(panels, /startSchoolAuthorization\(id\)/);
assert.match(main, /cloud\.completeSchoolReturn\(provider\)/);
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
assert.match(localSchoolUi, /LocalSchoolConnectionPanel\(/);
assert.match(localSchoolUi, /课表在课表页查看/,
  '青果本机课表入口保持可达，不重复堆技术说明');
assert.match(localSchoolUi, /XiaoxuExpandableGlassRow\(/,
  '三个本机平台入口须共用玻璃展开组件');
assert.match(localSchoolUi, /mutableStateOf\(justImported != null\)/,
  '读取成功自动显示真实本机回执');
assert.doesNotMatch(localSchoolUi, /CardDefaults\.cardElevation\(defaultElevation = 1\.dp\)/,
  '删除本机连接中的旧 Material 灰色卡片阴影');
assert.match(workspace, /LocalSchoolRecordSection\(setOf\("course"\)\)/);
assert.match(main, /localLessons = localLessons,/);
assert.match(workspace, /val sameWeekday = localLessons\.filter/,
  '主页必须只展示已导入的本机课表，不可回退云端');
assert.match(workspace, /val nativeRows by nativeDao\.observe\(\)\.collectAsState\(initial = emptyList\(\)\)/);
assert.match(workspace, /it\.provider == "chaoxing" && it\.kind == "homework"/);
assert.match(workspace, /QuickEntry\("第二课堂"/, '第二课堂由真实学业入口打开');
assert.match(workspace, /学习通 · 本机作业快照/);
assert.match(workspace, /不纳入待完成\/已完成数量/);

// Issue #261：任务中心分行分区 + 来源徽标（纯 UI 增量）。
assert.match(workspace, /private fun TaskSectionHeader\(/, '任务列表须有分区标题');
// 任务中心新版必须直接改真实 Compose 页面，不引入假数据与第二套消息审核。
assert.match(workspace, /internal fun searchWorkspaceTasks\(/, '应支持对真实本机与云端记录搜索');
assert.match(workspace, /val visible = facetWorkspaceTasks\(/, '页面对真实任务独立筛选');
assert.match(workspace, /searchWorkspaceTasks\(all, filter, searchQuery, today\)/, '任务仍使用真实搜索结果');
assert.match(workspace, /if \(showSchoolRecords\) \{/,
  '本机学习通快照应可收纳，避免默认铺满任务页');
assert.ok(workspace.indexOf('title = "课程任务"') <
    workspace.indexOf('title = "微信任务"') &&
    workspace.indexOf('title = "微信任务"') <
    workspace.indexOf('title = "本机任务"'),
  '任务按课程、审核通过微信、本机三个来源独立排序');
assert.doesNotMatch(workspace, /WechatReviewPanel\(/,
  '任务中心不能重复承载群消息候选审核；必须归属消息中心');
assert.match(messageCenter, /WechatReviewPanel\(/,
  '消息中心继续保留原生微信候选审核');
assert.doesNotMatch(workspace, /PosterDemo/, '真实任务中心不可嵌入海报版演示');

assert.match(workspace, /private fun TaskRow\(/, '任务行须是独立展示组件');
assert.match(workspace, /private fun SnapshotRow\(/, '学习通快照行须是独立展示组件');
assert.match(workspace, /title = "本机任务"/, '本机待办须有独立分区');
assert.match(workspace, /title = "课程任务"/, '云端课程任务须有独立分区');
assert.match(workspace, /title = "微信任务"/, '已审核微信任务须有独立分区');
assert.match(workspace, /本机页面快照/, '快照须标注来源徽标');
assert.match(workspace, /含上次云端快照/,
  '上次云端任务必须明确标注快照，不可冒充新读取');
assert.match(workspace, /DueUi\("日期未记录", "云端任务没有已核实的截止日期"/,
  '云端无截止来源时只显示日期未记录，不虚构学校核验结果');
assert.match(workspace, /截止未记录/, '本机缺日期须显示未记录');
assert.match(workspace, /if \(task.storage == "cloud"\) \{\s*return DueUi\("记录 \$text",/,
  '未经来源核实的云端日期只能称记录日期，不得直接标为学校逾期作业');
assert.match(workspace, /!task.done && date.isBefore\(today\)/,
  '本人填写的实际逾期任务仍须保留过期提醒');
assert.doesNotMatch(workspace, /visible\.take\(|visible\.size > 80/, '任务中心使用懒列表展示全部筛选结果，不得静默截断任务');
assert.match(workspace, /heightIn\(min = 64\.dp\)/, '任务行触控区域须 ≥48dp');
assert.match(workspace, /contentDescription = task\.title \+ "；来源："/,
  '任务行须有完整读屏描述');
assert.doesNotMatch(workspace, /Log\.[diwev]\(|println\(/, '任务中心不得打印日志');
assert.match(workspace, /采集时间/);
assert.match(schedulePanel, /本机导入 · 待本人核对/);
assert.doesNotMatch(workspace, /add\(WorkspaceTask\("school:"|CloudTask\(.*nativeRows/);
assert.match(panels, /LocalSchoolConnectionPanel\(/);
assert.ok(panels.indexOf('LocalSchoolConnectionPanel(') < panels.indexOf('if (cloudAdvanced)'), '本机记录入口始终可见，无须先展开云端或等待导入');
assert.match(localSchoolUi, /data class LocalSchoolImportReceipt\(/);
assert.match(localSchoolUi, /records\.filter \{ it\.provider == provider && it\.kind != "account_binding" \}/);
assert.match(localSchoolUi, /stored\.size == justImported\.savedCount/);
assert.match(localSchoolUi, /已从本机数据库重新读取/);

assert.match(localSchoolUi, /fun LocalSchoolRecordSection\(/,
  '本机历史读取仍有独立只读组件，不丢真实记录');
assert.match(localSchoolUi, /title = "手机本机读取"/,
  '本机保存记录列表使用原生展开标题');
assert.match(localSchoolUi, /subtitle = "已保存 \$\{rows\.size\} 条本机记录"/,
  '历史记录折叠态仍显示真实数量');
assert.doesNotMatch(localSchoolUi, /CardDefaults\.cardColors|CardDefaults\.cardElevation/,
  '本机读取不得混入旧 Material 实心卡片体系');
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
assert.match(appGradle, /versionName '0\.8\.10'/, '联合验收保持主分支版本号');
assert.match(appGradle, /versionCode 31\b/, '联合验收不得回退版本码');

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
assert.match(main, /if \(provider == "ncwu"\) \{\s*startActivity\(Intent\(this@MainActivity, LocalQingguoImportActivity::class\.java\)\)/,
  '本机青果入口仍通过数据连接中心的官方来源选择器可达');
assert.doesNotMatch(main, /\bopenLocalImport\b|\bSchedulePage\(/,
  '旧课表转发回调与空代理页面不应继续存在');
assert.match(main, /MainDestination\.SCHEDULE -> CloudTimetablePanel\(/,
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
assert.match(design, /val card: Dp = 24\.dp/, '基础玻璃卡圆角须统一为24dp');
assert.match(design, /fun XiaoxuExpandableGlassRow\(/,
  '保留唯一原生玻璃展开控件');
assert.match(design, /fun XiaoxuGlassDialog\(/,
  '必须存在同一居中玻璃详情容器');
const glassRowDialogSource = design.slice(design.indexOf('fun XiaoxuExpandableGlassRow('),
  design.indexOf('fun MetricCard('));
assert.match(glassRowDialogSource, /if \(expanded\) \{\s*XiaoxuGlassDialog\(/,
  '平台与二课的详情应使用浮层，不再在卡片内向下展开');
assert.doesNotMatch(glassRowDialogSource, /AnimatedVisibility\(/,
  '禁止在详情卡片内重新引入向下展开');
assert.match(profileSettings, /"connections" -> item \{/,
  '数据连接详情独立成二级页面，而非旧版工具浮层');
assert.match(panels, /if \(cloudAdvanced\) XiaoxuGlassDialog\(/,
  '云端同步详情不得顶开本机连接列表');
assert.match(design, /val GlassCard = Color.White\.copy\(alpha = 0\.90f\)/,
  '统一玻璃卡透明度，避免彩色底透出硬灰边');

assert.match(design, /label = "glass-row-chevron"/,
  '展开箭头动画与其他页面共用时序');
assert.match(design, /val glassStroke: Dp = 0\.5\.dp/,
  '玻璃描边必须保持为单一轻量尺寸，避免肉眼可见硬灰边');
assert.match(design, /val GlassBorder = Color\.White\.copy\(alpha = 0\.55f\)/,
  '轻玻璃边界须接近北化的柔和白色描边而非旧灰色轮廓');
assert.match(design, /Brush\.linearGradient\(/,
  '全局背景使用一致的淡蓝紫渐变，不得各页面覆盖');

assert.match(design, /val hero: Dp = 28\.dp/, '重要卡与底部弹层圆角须统一为28dp');
assert.match(design, /val XiaoxuCourseColors/, '课程六色须集中定义');
assert.match(design, /fun xiaoxuCourseColor\(key: String\)/, '课程配色须按稳定 key 取模');
assert.match(design, /fun XiaoxuTheme\(/, '须有统一主题入口');
assert.match(design, /object XiaoxuMotion/, '动画时间参数只能在设计系统维护');
assert.match(main, /xiaoxuPrimaryPageTransition\(/, '主导航切换须走统一动效入口');
assert.match(pageMotion, /XiaoxuMotion\.PageSwitchMs/, '主页 360ms 动效从设计系统读取');
assert.match(pageMotion, /XiaoxuMotion\.SettingsDetailMs/, '设置页 500ms 动效从设计系统读取');
assert.match(pageMotion, /slideOutHorizontally\(PrimaryPageSlide\)/, '旧页必须退出而不是交叉淡出造成重影');
assert.match(profileSettings, /xiaoxuSettingsRouteTransition\(initialState, targetState\)/,
  '设置二级页须保持方向明确的推进与返回');
assert.doesNotMatch(main, /fadeIn\(animationSpec = tween\(XiaoxuMotion\.PageSwitchMs\)/,
  '不允许恢复主导航文字重影交叉淡入');
assert.doesNotMatch(main, /private const val PageSwitchMs/, '删除重复的本地切页时间常量');
assert.match(schedulePanel, /tween\(XiaoxuMotion\.SelectionMs\)/);
assert.match(messageCenter, /tween\(XiaoxuMotion\.SelectionMs\)/);
assert.match(design, /fun XiaoxuCard\(/, '须有通用圆角卡');
const primaryCardSource = design.slice(design.indexOf('fun XiaoxuCard('),
  design.indexOf('/** 同一个分段选择器', design.indexOf('fun XiaoxuCard(')));
assert.match(primaryCardSource, /XiaoxuSpacing\.glassStroke/,
  '最常用玻璃卡必须使用设计系统的0.5dp边缘，而不是遗留的0.75dp硬灰边');
assert.doesNotMatch(primaryCardSource, /\.shadow\(/,
  '普通玻璃卡不得重新叠加可见灰色投影');

assert.doesNotMatch(design, /fun XiaoxuHero\(/, '已无生产调用的旧渐变主卡不得由静态门禁强制保留');
assert.match(design, /fun MetricCard\(/, '须有统计卡');
assert.match(design, /fun CourseTile\(/, '须有课程色块');
assert.match(design, /fun SectionHeader\(/, '须有分区标题');
assert.match(design, /fun SourceBadge\(/, '须有来源徽标');
assert.match(design, /fun XiaoxuToolbarAction\(/, '工具区图标应只有一套组件');
assert.ok(workspace.includes('XiaoxuToolbarAction('), '任务页复用统一工具图标');
assert.ok(workspace.includes('taskScope.launch { client.refreshPrivate() }'),
  '统一工具图标不能移除真实刷新动作');
assert.match(design, /fun StatusChip\(/, '须有状态标签');
assert.match(design, /fun XiaoxuSheet\(/, '任务和消息必须共用详情容器');
assert.match(design, /fun XiaoxuEmpty\(/, '保留实际使用的统一空态');
assert.match(design, /fun XiaoxuStatePanel\(/, '复用统一空态容器');
assert.match(design, /fun XiaoxuTopBar\(/, '须有统一顶栏');
assert.match(design, /fun QuickEntry\(/, '快捷入口须是共享组件');
assert.doesNotMatch(design, /WebView|HttpURLConnection|CookieManager/,
  '设计系统只画界面，不得触碰网络');
assert.doesNotMatch(design, /import io\.github\.x07xh\.xiaoxu\.data\./,
  '设计系统不得依赖数据层');
assert.match(main, /XiaoxuTheme \{/, 'Shell 须使用统一主题');
assert.match(main, /XiaoxuTopBar\(/, 'Shell 须使用统一顶栏');
assert.match(main, /AnimatedContent\(/, '页面切换须有轻量过渡');
const floatingBar = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/FloatingBottomBar.kt');
const primaryNavigation = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/PrimaryNavigation.kt');
assert.match(floatingBar, /indicatorColor: Color = XiaoxuPalette\.GlassIndicator/,
  '悬浮导航选中玻璃材质须取自唯一设计系统');
assert.match(floatingBar, /selectedContentColor: Color = XiaoxuPalette\.BrandDeep/,
  '悬浮导航旧设备回退继续使用统一调色板');
const navGeometry = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/PrimaryBottomGeometry.kt');
const glassChrome = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/NorthGlassNavChrome.kt');
const northIcon = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/northglass/NorthPhosphorNavigationIcon.kt');
const northMotion = read('apps/xiaoxu-android/app/src/main/java/io/github/x07xh/xiaoxu/ui/northglass/NorthNavigationIconPlayback.kt');
assert.match(appGradle, /io\.github\.kyant0:backdrop:2\.0\.0-alpha03/);
assert.match(appGradle, /io\.github\.kyant0:shapes:1\.2\.0/);
assert.match(main, /rememberLayerBackdrop\(\)/);
assert.match(main, /Modifier\.layerBackdrop\(navBackdrop\)/);
assert.match(main, /PrimaryBottomNavigationReserve\(glass = navBackdrop != null\)/);
assert.equal((main.match(/Scaffold\(/g) || []).length, 1,
  '主壳层只能有一套 Scaffold');
assert.doesNotMatch(main, /\bFloatingBottomBar\(/,
  '实际底栏统一经过 PrimaryBottomNavigation 适配器');
assert.match(primaryNavigation, /FloatingBottomBar\(/);
assert.match(primaryNavigation, /PrimaryBottomNavigationReserve\(/);
assert.match(navGeometry, /val contentReserve: Dp get\(\) = trackHeight \+ topPadding \+ bottomPadding/);
assert.match(navGeometry, /trackHeight = track/);
assert.match(floatingBar, /rememberCombinedBackdrop\(backdrop, tintLayer\)/,
  '透镜必须合并当前页面与原版前景采样');
assert.match(floatingBar, /Modifier\.northGlassTrack\(/);
assert.match(floatingBar, /Modifier\.northGlassIndicator\(/);
assert.match(floatingBar, /onDragCancel = \{/);
assert.match(floatingBar, /drag\.updateValue\(currentIndex\.toFloat\(\)\)/,
  '中断拖拽不能误选新页面');
assert.match(floatingBar, /currentHaptics\.performHapticFeedback\(HapticFeedbackType\.ContextClick\)/,
  '导航拖拽选中后应仅反馈一次触感');
assert.match(floatingBar, /if \(index != safeIndex\) \{\s*haptics\.performHapticFeedback/,
  '点击当前标签不得重复导航，实际切换才触发原版触感');
assert.match(floatingBar, /iconPlayback\?\.replay\(index\)/,
  '重复点击当前标签仍可重播原版图标动画');
assert.doesNotMatch(floatingBar, /if \(index == safeIndex\) iconPlayback\?\.replay\(index\)\s*onSelect\(index\)/,
  '禁止同一标签重触发页面刷新和动画');
assert.match(glassChrome, /fun Modifier\.northGlassTrack\(/);
assert.match(glassChrome, /fun Modifier\.northGlassIndicator\(/);
assert.match(northIcon, /NorthPhosphorNavigationIcon\(/);
assert.match(northMotion, /rememberNorthNavigationIconPlayback\(/);
assert.match(design, /fun XiaoxuGlassDialog\(/,
  '#361 共享玻璃浮层实现必须保留；设置路由不再依赖旧工具 Dialog');
assert.match(appGradle, /xiaoxuRuntimeTest/,
  '不能用旧 #353 Gradle 覆盖 #351 本机运行时安全测试配置');

assert.match(primaryNavigation, /NavigationRailItemDefaults\.colors/,
  '侧栏选中态须统一配色，不在 MainActivity 重复维护');
assert.match(workspace, /private fun HomeGreeting\(/, '首页须有日期与问候区');
assert.match(main, /current == MainDestination\.HOME\) XiaoxuTopBar\(\s*title = "今日",\s*subtitle = null/,
  '首页只保留一个今日主标题，不重复品牌、副标题和问候大标题');
const compactGreeting = workspace.slice(workspace.indexOf('private fun HomeGreeting('),
    workspace.indexOf('/** 分区外壳', workspace.indexOf('private fun HomeGreeting(')));
assert.match(compactGreeting, /Row\(/,
  '日期和问候同排展示，不额外占用一层大标题');
assert.doesNotMatch(compactGreeting, /headlineMedium|headlineLarge/,
  '首页问候不得在全局标题下重复显示一个大标题');
const homeQuickEntry = design.slice(design.indexOf('fun QuickEntry('));
assert.match(homeQuickEntry, /background\(XiaoxuPalette\.GlassCard\)/,
  '首页学业入口和全 App 普通卡片使用同一轻玻璃材质');
assert.match(homeQuickEntry, /border\(XiaoxuSpacing\.glassStroke, XiaoxuPalette\.GlassBorder/,
  '首页学业快捷入口不能回退到实心白卡和灰色硬描边');

assert.doesNotMatch(workspace, /private fun HomeHero\(/, '旧首页轮播必须清退');
assert.ok(workspace.includes('internal fun <T> homeCoursePreview('), '首页预览应保留本人课程原顺序');
assert.ok(workspace.includes('private fun HomeCourseList('), '首页使用统一课程列表');
const homeCourseBlock = workspace.slice(workspace.indexOf('private fun HomeCourseList('),
  workspace.indexOf('private fun HomeCourseRecordRow('));
assert.match(homeCourseBlock, /if \(hasTimetable && lessons\.isEmpty\(\)\) \{/,
  '首页有课表但当天无课时，应显示紧凑提示而非多余的厚玻璃空卡');
assert.match(homeCourseBlock, /暂无本机课程记录/,
  '未知当前教学周时仅描述本机记录，不推定学校官方无课');
assert.match(homeCourseBlock, /else \{\s*HomeSection\([\s\S]*?XiaoxuCard\(contentPadding = 12\.dp\)/,
  '仅有课程记录或未导入课表时渲染实际列表或导入入口');
assert.match(homeCourseBlock, /if \(hasTimetable && lessons.isEmpty\(\)\) \{\s*[\s\S]*?Row\(/,
  '本机当日无课时用单行对齐提示替代独立厚卡');
assert.doesNotMatch(homeCourseBlock, /caption = "\$weekdayLabel/,
  '首页课程不重复铺设周次来源诊断文案');

const homeRowSource = workspace.slice(workspace.indexOf('private fun HomeCourseRecordRow('),
    workspace.indexOf('/** 两张统计卡', workspace.indexOf('private fun HomeCourseRecordRow(')));
assert.match(homeRowSource, /heightIn\(min = XiaoxuSpacing\.listRowMin\)/,
  '首页课程行与消息及任务列表使用一致的最小触控高度');
assert.match(schedulePanel, /Modifier\.width\(4\.dp\)\.heightIn\(min = XiaoxuSpacing\.listRowMin\)/,
  '课表课程色条沿用既有高度并复用设计系统，课程颜色不改变');
assert.ok(workspace.includes('HomeCourseRecordRow(lesson, onOpen = { onOpenLesson(lesson) })'),
  '课程行点击复用原详情');
assert.ok(workspace.includes('homeCoursePreview(lessons).forEachIndexed'),
  '首页一次最多显示四条课程记录');
assert.ok(workspace.includes('xiaoxuCourseColor(lesson.id.ifBlank { lesson.title })'),
  '首页和课表必须共享稳定的课程颜色');
assert.doesNotMatch(workspace.slice(workspace.indexOf('val hero: @Composable'), workspace.indexOf('val tasks: @Composable')),
  /HomeHero\(/, '首页不能继续调用旧轮播');
assert.doesNotMatch(workspace, /resolveHomeCourseIndex|selectedHomeCourseKey|onSelectCourse\(/,
  '首页旧轮播状态须清退');
assert.ok(workspace.includes('maxWidth < 420.dp'), '窄屏学业入口须自动调整为两行');
const homeCourseSequence = read('apps/xiaoxu-android/app/src/test/java/io/github/x07xh/xiaoxu/ui/HomeCourseSequenceTest.kt');
assert.match(homeCourseSequence, /firstFourPreserveSequenceFromLongList/, '首页列表必须保持记录顺序');
assert.match(workspace, /private fun HomeTaskRow\(/, '首页待办行须复用任务状态判定');
assert.match(workspace, /QuickEntry\("成绩"/, '首页学业入口须指向真实子页面');
assert.match(workspace, /QuickEntry\("考试"/);
assert.match(workspace, /QuickEntry\("第二课堂"/);
assert.match(workspace, /QuickEntry\("学习通"/);
assert.match(workspace, /val sameWeekday = localLessons\.filter/,
  '首页只能依据本机导入课表进行星期匹配');
assert.match(workspace, /title = "课程记录"/, '未知教学周只能称课程记录而非今日真实课表');
assert.match(workspace, /XiaoxuTone\.Neutral/, '无课表时须展示明确空态');
assert.match(workspace, /TextButton\(onClick = openFileImport\) \{ Text\("导入课表"\) \}/,
 '首页没有课表时必须保留实际可点击的本机课表导入按钮');
assert.match(main, /openFileImport = openFileImport/, '主导航须连接已有文件选择器');
assert.match(main, /openGrades = [^,]*AcademicDestination\.GRADES/s, '成绩须打开教务真实子页面');
assert.match(main, /openExams = [^,]*AcademicDestination\.EXAMS/s, '考试须打开教务真实子页面');
assert.match(main, /openSecondClass = \{ openSecondClass\(\) \}/);
assert.match(workspace, /HomeCourseDetailSheet\(selectedLesson/, '首页课程点击须复用课表原生详情');
assert.match(schedulePanel, /internal fun HomeCourseDetailSheet\(/, '课表页面应暴露同一原生详情');
assert.match(workspace, /WorkspaceTaskDetailSheet\(/, '首页和任务中心应复用任务详情');
assert.match(workspace, /NoticeDetailSheet\(selectedNotice/, '首页公告应复用消息中心详情');
assert.match(messageCenter, /internal fun NoticeDetailSheet\(/);
assert.match(workspace, /clickable\(onClickLabel = "查看任务详情", onClick = onOpen\)/);
assert.match(workspace, /clickable\(onClickLabel = "查看公告详情"\)/);
assert.match(workspace, /One scroll owner on phones AND tablets/, '平板应采用整页单一滚动');
assert.doesNotMatch(workspace, /TodayScheduleBlock\(/, '首页不应重复展示同一组课程');
assert.doesNotMatch(workspace, /下一节课|倒计时为/, '缺少作息时间时不允许推断下一节课');
assert.doesNotMatch(workspace, /else state\.lessons/, '本机课表不能回退旧云端数据');
assert.match(workspace, /val cloudSnapshotRows = state\.tasks\.size/, '云端任务快照须披露数量');
assert.match(workspace, /含上次云端快照 /, '云端旧记录必须保留快照标签');
assert.match(workspace, /云端日期仅供参考/,
  '首页只保留一次云端日期来源提示，不伪称学校正式截止日');
assert.match(workspace, /countUpcomingLocalDeadlines\(all, today\)/,
  '首页近期截止使用单一已测试的来源安全统计函数');
assert.match(workspace, /task.storage == "local" && !task.done/,
  '近期截止仅统计本人填写的未完成任务');
assert.match(workspace, /!it.isBefore\(today\) && !it.isAfter\(today.plusDays\(3\)\)/,
  '历史逾期记录不得混入未来三天的近期截止统计');
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
assert.match(workspace, /Text\("本周截止"\)/, '本周截止在筛选器中可见');
assert.match(workspace, /label = "已完成", count = all\.count/, '完成状态只能统计现有记录');
assert.match(workspace, /private fun TaskGroupHeader\(/, '任务分区必须保留来源与折叠入口');
assert.match(workspace, /var showCourseTasks by rememberSaveable \{ mutableStateOf\(false\) \}/,
  '首次进入任务中心的课程任务必须默认折叠');
assert.match(workspace, /onClick = \{ showCourseTasks = !showCourseTasks \}/,
  '用户点击课程任务后必须仍能展开和收起');
assert.match(workspace, /if \(showCourseTasks\) \{/,
  '课程列表显示必须只由当前展开状态控制');
assert.match(workspace, /var showWechatTasks by rememberSaveable \{ mutableStateOf\(false\) \}/,
  '首次进入任务中心的微信任务必须默认折叠');
assert.match(workspace, /var showPersonalTasks by rememberSaveable \{ mutableStateOf\(false\) \}/,
  '首次进入任务中心的本机任务必须默认折叠');
assert.match(workspace, /var showOfficialAudit by rememberSaveable \{ mutableStateOf\(false\) \}/,
  '学习通数据读取完成后也不得自动展开作业分组');
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

assert.match(designSystem, /val listRowMin: Dp = 76\.dp/,
  '任务与消息共用同一紧凑最小行高');
assert.match(workspace, /val radius = XiaoxuRadius\.card/,
  '任务列表首尾圆角必须沿用唯一设计系统');
assert.match(workspace, /heightIn\(min = XiaoxuSpacing\.listRowMin\)/,
  '任务行仍由内容扩展且使用统一最小行高');
assert.match(messageCenter, /heightIn\(min = XiaoxuSpacing\.listRowMin\)/,
  '官方消息行与任务行共用最小尺寸基准');
assert.match(messageCenter, /private val CardShape = RoundedCornerShape\(XiaoxuRadius\.card\)/,
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
assert.match(compactTaskHeader, /if \(badge\.isNotBlank\(\) \|\| note\.isNotBlank\(\)\)/,
  '没有必要说明的任务分组不应占第二行辅助文字');
assert.doesNotMatch(workspace, /title = "群消息任务候选"|showCandidates/,
  '任务中心不得再展示待审核微信群消息区块或保存其折叠状态');
assert.match(workspace, /title = "微信任务"/,
  '审核通过的微信任务必须在任务中心独立成组');
assert.match(workspace, /var showWechatTasks by rememberSaveable/,
  '微信任务独立于课程/本机分组折叠');
assert.match(taskHubRegion,
  /items\(count = wechatRows\.size, key = \{ i -> wechatRows\[i\]\.key \}\)[\s\S]*?ConnectedTaskShell\(\s*index = i, total = wechatRows\.size,/,
  '微信任务必须维持独立带 key 的 LazyColumn 列表');
assert.match(workspace, /val wechatRows = shown\.filter \{ it\.category == "wechat" \}/,
  '微信任务不能混入普通云端任务的列表');
assert.match(workspace, /val cloudRows = shown\.filter \{ it\.category == "cloud" \}/,
  '普通云端任务不应包含微信审核通过的任务');
assert.match(workspace, /"wechat" -> "微信任务"/,
  '原生任务标题与筛选菜单必须识别微信来源');
assert.match(workspace, /task\.provider == "wechat"/,
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
assert.match(workspace, /showManager by rememberSaveable \{ mutableStateOf\(false\) \}/,
  '高频任务列表之前不得默认展开管理面板');
assert.doesNotMatch(workspace, /PosterDemo/, '真实任务中心不得有独立海报演示版');
assert.match(workspace, /if \(showManager && showLocal\) item \{[\s\S]*?LocalTaskEditor\(localTasks, dao\)/,
  '本机任务只在管理展开时展示真实 Room 编辑器');
assert.match(workspace, /if \(showManager && showCloud\) item \{[\s\S]*?CloudTaskEditor\(client\)/,
  '关闭任务管理也必须隐藏云端新建表单');
assert.match(workspace, /if \(showManager && showLocal\) \{[\s\S]*?showManager = false[\s\S]*?showCloud = false/,
  '顶部新增按钮再次点击应完整收起编辑区');
assert.match(workspace, /TaskRow\(localRows\[i\], today,[\s\S]*?onOpen = \{ openedTaskKey = localRows\[i\]\.key \}/,
  '本机任务仍从真实 Row 数据打开详情');
assert.match(workspace, /dao\.setCompleted\(id, checked\)/,
  '首页任务中心勾选必须真实更新本机 Room 状态');
assert.match(workspace, /task\.storage == "local" && onToggleLocal != null/,
  '云端任务不可复用本机完成操作或假装已提交到学校');
assert.match(cloud, /suspend fun setWechatTaskCompleted\(/,
  '微信审核任务必须复用本人云端任务写接口而不是只读装饰勾选框');
assert.match(cloud, /it\.provider == "wechat" && it\.revision == revision/,
  '仅后端审核已确认微信来源、且版本匹配的任务允许个人勾选');
assert.match(cloud, /request\("\/api\/v1\/assignments\/\$id", "POST", body, authenticated = true\)/,
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
assert.match(workspace, /val statusText = if \(isPending\) "保存中…"/,
  '尚未收到云端回执时必须明确显示保存中');
assert.match(workspace, /modifier = Modifier\.animateItem\(/,
  '云端已确认后移除任务行应做短时退场动画而不是突跳');
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
assert.match(workspace, /XiaoxuSheet\(onDismiss = onDismiss, title = "任务详情"\)/,
  '任务详情必须使用共享原生底部抽屉');
assert.match(workspace, /clickable\(onClickLabel = "查看任务详情", onClick = onOpen\)/,
  '任务卡片必须可以真实点击');
assert.match(workspace, /task\.storage == "cloud"/, '任务来源必须区分云端与本机');
assert.match(workspace, /云端历史快照；日期与完成情况可能已变化/,
  '任务详情必须区分历史快照与当前同步成功');
assert.match(workspace, /onVerifyChaoxing = \{ openLocalSchool\("chaoxing"\) \}/,
  '学习通相关云端任务详情必须可以打开官方页面进行人工核对');
assert.match(workspace, /selectedTask.provider == "chaoxing"/,
  '只有学习通相关任务显示学习通核对入口');
assert.match(workspace, /本条记录尚无可关联的官方作业 ID/,
  '没有可信来源 ID 的旧记录必须提供人工核对入口');
assert.match(workspace, /已关联学习通来源作业 ID/,
  '具有可信来源 ID 的任务必须明确显示已关联状态');
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
assert.match(workspace, /showManager = true\s*if \(selectedTask\.storage == "local"\) \{\s*showLocal = true\s*showCloud = false\s*jumpTo = "local"/,
  '本机任务详情必须同时展开管理器及本机表单，关闭云端编辑器');
assert.match(workspace, /else \{\s*showCloud = true\s*showLocal = false\s*jumpTo = "cloud"/,
  '云端任务详情必须展开正确的云端表单，关闭本机编辑器');
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
assert.match(panels, /if \(cloudAdvanced\) XiaoxuGlassDialog\(/,
  '云端授权仍是独立可选入口，详情改为居中浮层而不向下展开');

// Issue #268 P2：任务统计卡可点击筛选，详情跳转到真实管理区，窄屏来源标题分行。
assert.match(workspace, /val taskListState = rememberLazyListState\(\)/,
  '任务中心必须保留独立可滚动列表状态');
assert.match(workspace, /taskListState\.animateScrollToItem\(targetIndex\)/,
  '任务跳转须滚到真实管理入口，不能只是展开不可见区域');
assert.match(workspace, /"cloud" -> if \(showCloud\) managerStart \+ \(if \(showLocal\) 1 else 0\)/,
  '云端表单跳转须考虑本机管理展开与否');
assert.match(workspace, /"filters" -> 1/, '蓝色三状态切换应回到顶部筛选区');
assert.match(workspace, /active = filter == "all"/, '全部任务为独立状态');
assert.match(workspace, /sourceFilter = source/, '来源菜单选项必须真实更新筛选条件');
assert.match(workspace, /DropdownMenu\(/, '来源筛选收纳为单个下拉入口');
assert.match(workspace, /sourceMenuOpen = false/, '来源菜单选择后必须关闭');
assert.match(workspace, /facetWorkspaceTasks\(/, '来源与日期筛选纯函数可测');
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
assert.doesNotMatch(messageCenter, /MESSAGE CENTER/,
  '消息顶栏不再重复展示英文装饰标题');
assert.match(messageCenter, /messageFilterIcon\(/, '四枚筛选 pill 需带图标');
assert.match(messageCenter, /TextFieldDefaults\.colors\(/, '搜索条改为无边框填充样式');
assert.match(messageCenter, /fun MessageSearchField\(/, '搜索条抽成独立组件');
assert.match(messageCenter, /XiaoxuSegmentedControl\(/,
  '官方与微信来源使用共享原生分段组件');
assert.match(messageCenter, /size\(46\.dp\)/, '公告卡图标块放大至 46dp，提升 Ace 6 可读性');
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
assert.match(profileSettings, /"connections" -> item \{/,
  '真实学校读取入口归属我的 → 数据连接二级页面，不回退独立首页容器');
assert.match(panels, /title = "云端同步"/,
  '云端授权应继续收纳为独立可操作区域');
assert.match(main, /private fun CompactPageHeading\(/,
  '教务业务页仍复用紧凑标题，设置页标题由 ProfileSettingsPage 唯一维护');
assert.match(main, /if \(!messagesOpen && current == MainDestination\.HOME\) XiaoxuTopBar\(/,
  '全局顶栏只在首页出现，课表和其他分栏由内容页自行提供紧凑标题');
assert.match(main, /onMessages = \{ messagesOpen = true \}/,
  '页面消息中心入口应继续可达');
assert.doesNotMatch(main, /Content\(withTopInset = true\)|\.fillMaxSize\(\)\.statusBarsPadding\(\)/,
  'Scaffold 已处理状态栏，不能重复添加顶部安全区');
assert.match(main, /if \(selected != AcademicDestination\.OVERVIEW\)/,
  '教务各详情页统一原生返回，不再依赖叠加的二级标签');
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
assert.match(academicOverviewSource, /XiaoxuGroupedSection\s*\{/,
  '四个教务入口只能使用一层连续玻璃分组');
assert.doesNotMatch(academicOverviewSource, /XiaoxuCard\(/,
  '教务连续分组内部不能再套第二层方形卡片');
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
assert.match(primaryNavigation, /PrimaryTabs = listOf\([\s\S]*MainDestination\.HOME,[\s\S]*MainDestination\.SCHEDULE,[\s\S]*MainDestination\.ACADEMIC,[\s\S]*MainDestination\.TASKS,[\s\S]*MainDestination\.PROFILE/,
  '唯一导航配置保留今日、课表、教务、任务、我的顺序');
assert.doesNotMatch(main, /private val PrimaryTabs|NavigationRail\(/,
  '业务壳层不能重建第二套导航表或侧栏组件');
assert.doesNotMatch(main, /MainDestination\.MESSAGES/, '消息不占底栏');
assert.match(main, /onMessages = \{ messagesOpen = true \}/, '全局消息入口');
assert.match(main, /openAcademic = \{ current = MainDestination\.ACADEMIC; academic = AcademicDestination\.OVERVIEW \}/,
  '教务快捷入口打开总览');
assert.match(main, /MessageCenterPanel\(cloud, openWebsite, openArticle,/,
  '消息中心复用真实数据');
assert.match(main, /useStatusBarInset = false, onBack = \{ messagesOpen = false \}\)/,
  '消息页面直接传入返回来源页的回调，无重复中间包装组件');
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
assert.match(main, /current == MainDestination\.HOME\) XiaoxuTopBar\(/);
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
