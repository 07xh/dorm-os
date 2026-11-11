package com.dormos.nfc

import android.content.Context
import java.io.File

/**
 * Inspired by the user's DORM-SRC ScriptRepository / PathGuard:
 * private, non-root .sh storage only. File contents are NEVER executed.
 */
class LocalScriptLibrary(context: Context) {
    private val root: File = context.getDir("scripts", Context.MODE_PRIVATE).canonicalFile
    data class Entry(val name: String, val updatedAt: Long)

    private fun checkedName(name: String): String {
        val cleaned = name.trim()
        require(cleaned.matches(Regex("[A-Za-z0-9_-]{1,56}\\.sh"))) {
            "仅允许英文字母、数字、下划线或短横线组成的 .sh 文件名"
        }
        return cleaned
    }

    private fun path(name: String): File {
        val candidate = File(root, checkedName(name)).canonicalFile
        require(candidate.parentFile == root) { "路径越界已拦截" }
        return candidate
    }

    @Synchronized fun list(): List<Entry> =
        root.listFiles().orEmpty()
            .filter { it.isFile && !it.isHidden && it.name.matches(Regex("[A-Za-z0-9_-]{1,56}\\.sh")) }
            .sortedBy { it.name.lowercase() }
            .map { Entry(it.name, it.lastModified()) }

    @Synchronized fun read(name: String): String {
        val file = path(name)
        require(file.isFile && !file.isDirectory && file.length() <= 65536) { "文件不存在或超过 64 KiB" }
        return file.readText(Charsets.UTF_8)
    }

    /** Atomic replace; no user-controlled absolute paths or recursive delete. */
    @Synchronized fun save(name: String, content: String) {
        require(content.toByteArray(Charsets.UTF_8).size <= 65536) { "脚本内容超过 64 KiB" }
        val file = path(name)
        val temp = File(root, file.name + ".part")
        try {
            temp.writeText(content, Charsets.UTF_8)
            check(temp.renameTo(file)) { "保存失败" }
        } finally {
            temp.delete()
        }
    }

    @Synchronized fun remove(name: String) {
        val file = path(name)
        require(file.isFile && file.delete()) { "文件不存在或删除失败" }
    }
}
