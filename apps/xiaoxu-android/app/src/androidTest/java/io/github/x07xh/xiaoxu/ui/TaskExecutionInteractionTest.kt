package io.github.x07xh.xiaoxu.ui

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.x07xh.xiaoxu.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TaskExecutionInteractionTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()
    @Test fun userAddsStepsThenExplicitlyConfirmsParent() {
        assertRuntimeIsolation(compose.activity)
        val db=Room.inMemoryDatabaseBuilder(compose.activity,LocalTaskDatabase::class.java).build()
        try {
            val id=runBlocking {db.tasks().insert(LocalTask(title="报告"))}
            val root=ExecutionRoot("device","local:$id")
            compose.setContent {MaterialTheme {TaskExecutionPanel(root,false,db.executions(),onFinish={db.tasks().trySetCompleted(id,true)})}}
            compose.onNodeWithText("分解成步骤").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("写代码\n截图")
            compose.onNodeWithText("添加步骤").performClick()
            compose.waitUntil(10000) {runBlocking {db.executions().stepsFor(root.scope,root.key).size==2}}
            compose.onNodeWithContentDescription("写代码，未完成").performClick()
            compose.waitUntil(10000) {runBlocking {db.executions().stepsFor(root.scope,root.key).count {it.completed}==1}}
            compose.onNodeWithContentDescription("截图，未完成").performClick()
            compose.waitUntil(10000) {runBlocking {db.executions().stepsFor(root.scope,root.key).all {it.completed}}}
            assertFalse(runBlocking {db.tasks().findById(id)!!.completed})
            compose.onNodeWithText("确认结束个人任务").performClick()
            compose.waitUntil(10000) {runBlocking {db.tasks().findById(id)!!.completed}}
        } finally {compose.activityRule.scenario.close();db.close()}
    }
    @Test fun rejectedSaveKeepsInputAndDoesNotCreatePartialSteps() {
        assertRuntimeIsolation(compose.activity)
        val db=Room.inMemoryDatabaseBuilder(compose.activity,LocalTaskDatabase::class.java).build()
        try {
            val id=runBlocking {db.tasks().insert(LocalTask(title="报告"))}
            val root=ExecutionRoot("device","local:$id")
            compose.setContent {MaterialTheme {TaskExecutionPanel(root,false,db.executions(),onFinish={false})}}
            compose.onNodeWithText("分解成步骤").performClick()
            compose.onNode(hasSetTextAction()).performTextInput("重复\n重复")
            compose.onNodeWithText("添加步骤").performClick()
            compose.waitUntil(10000) {compose.onAllNodesWithText("操作未保存，请刷新任务或检查前置条件").fetchSemanticsNodes().isNotEmpty()}
            compose.onNode(hasSetTextAction()).assertTextContains("重复\n重复")
            assertTrue(runBlocking {db.executions().stepSnapshot().isEmpty()})
        } finally {compose.activityRule.scenario.close();db.close()}
    }
}
