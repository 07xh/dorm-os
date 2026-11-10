package io.github.x07xh.xiaoxu.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.time.Instant

class PublicNoticeArchiveTest {
    private fun notice(n: Int, source: String, category: String, host: String = "www2.ncwu.edu.cn",
                       prefix: String = "/dianqi"): JSONObject = JSONObject()
        .put("id", "ncwu-" + n.toString(16).padStart(16, '0'))
        .put("sourceId", source)
        .put("title", "关于开展2026年度校园学业活动的通知" + n)
        .put("url", "https://" + host + prefix + "/info/1001/" + n + ".htm")
        .put("category", category)
        .put("date", JSONObject.NULL)

    private fun archive(items: JSONArray): JSONObject = JSONObject()
        .put("schema", "xiaoxu/notices-v1")
        .put("mode", "auto")
        .put("lastSuccessAt", Instant.now().minusSeconds(60).toString())
        .put("items", items)

    @Test fun validatedDepartmentsUseExistingMessageCenterModel() {
        val source = archive(JSONArray()
            .put(notice(1001, "dianqi", "教学"))
            .put(notice(1002, "gcxlzx", "校园", prefix = "/gcxlzx")))
        val result = PublicNoticeArchive.parse(source)
        assertEquals(2, result.items.size)
        assertEquals("notice", result.items[0].category)
        assertEquals("", result.items[0].dateLabel)
        assertEquals("https://www2.ncwu.edu.cn/gcxlzx/info/1001/1002.htm",
            result.items[1].url)
    }

    @Test fun rejectsUnofficialHost() {
        assertThrows(IllegalArgumentException::class.java) {
            PublicNoticeArchive.parse(archive(JSONArray()
                .put(notice(1001, "dianqi", "教学", host = "www2.ncwu.edu.cn.evil.example"))
                .put(notice(1002, "dianqi", "教学"))))
        }
    }

    @Test fun rejectsUnknownDepartmentOrForgedCategory() {
        assertThrows(IllegalArgumentException::class.java) {
            PublicNoticeArchive.parse(archive(JSONArray()
                .put(notice(1001, "fake_dept", "教学"))
                .put(notice(1002, "dianqi", "校园"))))
        }
        assertThrows(IllegalArgumentException::class.java) {
            PublicNoticeArchive.parse(archive(JSONArray()
                .put(notice(1001, "dianqi", "伪造分类"))
                .put(notice(1002, "dianqi", "校园"))))
        }
    }

    @Test fun seedCannotBecomeVerifiedByLaterAutoMode() {
        val seed = notice(1001, "dianqi", "教学").put("id", "seed-1001")
        assertThrows(IllegalArgumentException::class.java) {
            PublicNoticeArchive.parse(archive(JSONArray()
                .put(seed)
                .put(notice(1002, "dianqi", "教学"))))
        }
    }

    @Test fun incompleteArchiveFailsClosed() {
        assertThrows(IllegalArgumentException::class.java) {
            PublicNoticeArchive.parse(archive(JSONArray()
                .put(notice(1001, "dianqi", "教学"))))
        }
    }
}
