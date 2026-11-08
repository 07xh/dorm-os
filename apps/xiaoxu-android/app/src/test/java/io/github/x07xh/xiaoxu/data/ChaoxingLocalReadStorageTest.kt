package io.github.x07xh.xiaoxu.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ChaoxingLocalReadStorageTest {
    private class MemoryDao : NativeSchoolRecordDao {
        val rows=mutableListOf<NativeSchoolRecord>()
        override fun observe(): Flow<List<NativeSchoolRecord>> = MutableStateFlow(rows)
        override suspend fun snapshot()=rows.toList()
        override suspend fun snapshotKind(provider:String,kind:String)=rows.filter {it.provider==provider&&it.kind==kind}
        override suspend fun insertAll(rows:List<NativeSchoolRecord>) {
            for(row in rows) {this.rows.removeAll {it.provider==row.provider&&it.kind==row.kind&&it.fingerprint==row.fingerprint};this.rows.add(row)}
        }
        override suspend fun deleteKind(provider:String,kind:String) {rows.removeAll {it.provider==provider&&it.kind==kind}}
    }
    private fun item(key:String,kind:String="study_task")=NativeSchoolRecord("chaoxing",kind,
        ChaoxingLocalReader.hash("chaoxing|$kind|$key"),"本人任务","学习计划状态：已完成 · 学习任务身份：$key","task.chaoxing.com",1000)
    @Test fun partialReadRemovesOnlyExplicitSupersededPlanIdentity()=runBlocking {
        val dao=MemoryDao();val owner="a".repeat(64)
        dao.mergeChaoxingRead(listOf(item("plan:701"),item("plan:702")),owner)
        dao.mergeChaoxingRead(listOf(item("work:123:456:901","homework")),owner,
            complete=false,supersededKeys=setOf("plan:701"))
        assertEquals(listOf(item("plan:702")),dao.snapshotKind("chaoxing","study_task"))
        assertEquals(1,dao.snapshotKind("chaoxing","homework").size)
    }
    @Test fun switchingOfficialAccountCannotInheritPriorOwnersTasks()=runBlocking {
        val dao=MemoryDao()
        dao.mergeChaoxingRead(listOf(item("plan:701")),"a".repeat(64))
        dao.mergeChaoxingRead(listOf(item("plan:702")),"b".repeat(64),complete=false)
        assertEquals(listOf(item("plan:702")),dao.snapshotKind("chaoxing","study_task"))
        assertEquals("b".repeat(64),dao.snapshotKind("chaoxing","account_binding").single().details)
    }
    @Test fun fullNewSnapshotReplacesOnlyChaoxingSourceRecords()=runBlocking {
        val dao=MemoryDao();val owner="a".repeat(64)
        dao.rows.add(item("plan:800").copy(provider="second_class"))
        dao.mergeChaoxingRead(listOf(item("plan:701")),owner)
        dao.mergeChaoxingRead(listOf(item("plan:702")),owner,complete=true)
        assertEquals(listOf(item("plan:702")),dao.snapshotKind("chaoxing","study_task"))
        assertEquals(1,dao.rows.count {it.provider=="second_class"})
    }
}
