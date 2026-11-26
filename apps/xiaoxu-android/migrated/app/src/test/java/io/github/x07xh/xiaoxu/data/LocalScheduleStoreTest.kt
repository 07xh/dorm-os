package io.github.x07xh.xiaoxu.data

import com.tyust.course.xiaoxu.XiaoxuLocalScheduleAdapter
import android.app.Application
import android.content.Context
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class LocalScheduleStoreTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clearStore() {
        context.getSharedPreferences("xiaoxu-local-schedule-v1", Context.MODE_PRIVATE)
            .edit().clear().commit()
    }

    @Test
    fun editedCourseKeepsItsStableIdAcrossPersistence() {
        val original = VerifiedLesson(
            id = "local-course-001",
            term = "local:current",
            title = "高等数学",
            teacher = "王老师",
            location = "教学楼 A101",
            weekday = 1,
            startSection = 1,
            endSection = 2,
            weeks = (1..35).toList()
        )
        LocalScheduleStore.saveEdited(context, listOf(original))
        val loaded = LocalScheduleStore.load(context).single()
        assertEquals(original.id, loaded.id)

        val edited = loaded.copy(title = "高等数学（上）", location = "教学楼 A102")
        LocalScheduleStore.saveEdited(context, listOf(edited))
        val afterEdit = LocalScheduleStore.load(context).single()
        assertEquals(original.id, afterEdit.id)
        assertEquals(edited.title, afterEdit.title)
        assertEquals(edited.location, afterEdit.location)
    }

    @Test
    fun originalScheduleUiReceivesEditableLocalCourseIdentity() {
        val lesson = VerifiedLesson(
            id = "local-course-002",
            term = "local:current",
            title = "大学物理",
            teacher = "",
            location = "实验楼",
            weekday = 2,
            startSection = 3,
            endSection = 4,
            weeks = listOf(1, 3, 5)
        )
        val ui = XiaoxuLocalScheduleAdapter.toUi(listOf(lesson), listOf(Color.Blue)).single()
        assertTrue(ui.isCustom)
        assertEquals("local-course-002", ui.customId)
        assertEquals("custom:local-course-002", ui.id)
        assertEquals("custom:local-course-002", ui.record().id)
    }
}
