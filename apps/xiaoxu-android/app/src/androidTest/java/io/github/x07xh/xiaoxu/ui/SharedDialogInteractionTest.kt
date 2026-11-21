package io.github.x07xh.xiaoxu.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.x07xh.xiaoxu.data.assertRuntimeIsolation
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/**
 * Real Compose dialog interaction tests run with the isolated, offline Android
 * instrumentation suite. No synthetic school accounts, tasks or backend calls.
 */
@RunWith(AndroidJUnit4::class)
class SharedDialogInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Before fun isolated() = assertRuntimeIsolation(compose.activity)

    @Test fun selectingAnActionActuallyInvokesCallbackAndClosesSheet() {
        val shown = mutableStateOf(true)
        val chosen = AtomicInteger(-1)
        compose.setContent {
            MaterialTheme {
                if (shown.value) {
                    XiaoxuBottomActionSheet(
                        title = "选择个人分类",
                        onDismiss = { shown.value = false },
                        actions = listOf(
                            XiaoxuSheetAction("学习", onClick = {
                                chosen.set(1)
                                shown.value = false
                            }),
                            XiaoxuSheetAction("生活", onClick = {
                                chosen.set(2)
                                shown.value = false
                            })
                        )
                    )
                }
            }
        }
        compose.onNodeWithText("生活").performClick()
        compose.waitForIdle()
        assertEquals(2, chosen.get())
        compose.onNodeWithText("选择个人分类").assertDoesNotExist()
    }

    @Test fun busySheetDisablesCancelAndDestructiveActionUntilCompletion() {
        val shown = mutableStateOf(true)
        val busy = mutableStateOf(true)
        val dismissed = AtomicInteger(0)
        val deleted = AtomicInteger(0)
        compose.setContent {
            MaterialTheme {
                if (shown.value) {
                    XiaoxuBottomActionSheet(
                        title = "删除个人步骤？",
                        onDismiss = {
                            dismissed.incrementAndGet()
                            shown.value = false
                        },
                        dismissEnabled = !busy.value,
                        actions = listOf(
                            XiaoxuSheetAction(
                                label = "确认删除",
                                destructive = true,
                                enabled = !busy.value,
                                onClick = { deleted.incrementAndGet() }
                            )
                        )
                    )
                }
            }
        }
        compose.onNodeWithText("确认删除").assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
        assertEquals(0, dismissed.get())
        assertEquals(0, deleted.get())
        compose.runOnUiThread { busy.value = false }
        compose.waitForIdle()
        compose.onNodeWithText("取消").assertIsEnabled()
        compose.onNodeWithText("取消").performClick()
        compose.waitForIdle()
        assertEquals(1, dismissed.get())
        assertEquals(0, deleted.get())
    }

    @Test fun longBottomSheetCanScrollWithoutLosingPinnedCancelButton() {
        val shown = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (shown.value) {
                    XiaoxuBottomSheet(
                        title = "完整项目条目",
                        onDismiss = { shown.value = false },
                        cancelLabel = "关闭"
                    ) {
                        repeat(40) { Text("项目条目 $it") }
                    }
                }
            }
        }
        compose.onNodeWithText("关闭").assertIsDisplayed()
        // Real drag gestures verify that long content can move beneath the pinned
        // close button, rather than relying on a text node's ancestor semantics.
        repeat(6) {
            compose.onNodeWithTag("xiaoxu-bottom-scroll-content")
                .performTouchInput { swipeUp() }
        }
        compose.onNodeWithText("项目条目 39").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("完整项目条目").assertDoesNotExist()
    }

    @Test fun longCenteredGlassDialogKeepsFinishActionReachable() {
        val shown = mutableStateOf(true)
        compose.setContent {
            MaterialTheme {
                if (shown.value) {
                    XiaoxuGlassDialog(
                        title = "长内容许可",
                        onDismiss = { shown.value = false }
                    ) {
                        repeat(40) { Text("许可条目 $it") }
                    }
                }
            }
        }
        compose.onNodeWithText("完成").performScrollTo()
        compose.onNodeWithText("完成").assertIsDisplayed()
        compose.onNodeWithText("完成").performClick()
        compose.onNodeWithText("长内容许可").assertDoesNotExist()
    }
}
