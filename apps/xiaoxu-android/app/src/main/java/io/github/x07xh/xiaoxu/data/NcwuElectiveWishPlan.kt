package io.github.x07xh.xiaoxu.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Manually entered, device-local NCWU elective planning notes.
 * Neither a source of official offerings nor an enrollment queue. No credentials,
 * school requests, automatic submission, or authoritative availability.
 */
data class NcwuElectiveWish(
    val term: String,
    val courseCode: String,
    val classCode: String,
    val courseName: String,
    val teacher: String = "",
    val weekday: Int? = null,
    val startSection: Int? = null,
    val endSection: Int? = null,
    val weeks: List<Int> = emptyList()
) {
    val stableKey: String get() = "$term|$courseCode|$classCode"
    val hasFullMeeting: Boolean get() =
        weekday != null && startSection != null && endSection != null && weeks.isNotEmpty()
}

enum class NcwuWishConflict { OVERLAP, NO_KNOWN_CONFLICT, INSUFFICIENT_DATA }

/** Pure operations; never infer eligibility from manual entries or unknown meeting times. */
object NcwuElectiveWishPlan {
    private val codePattern = Regex("[A-Za-z0-9._:-]{1,96}")
    private val termPattern = Regex("20\\d{2}-20\\d{2}-[1-3]")
    private const val MAX_ITEMS = 60

    fun normalize(wish: NcwuElectiveWish): NcwuElectiveWish {
        val term = wish.term.trim()
        require(termPattern.matches(term) &&
            term.substring(5, 9).toInt() == term.substring(0, 4).toInt() + 1) { "学期格式应为 2026-2027-1" }
        val course = wish.courseCode.trim()
        val section = wish.classCode.trim()
        require(codePattern.matches(course) && codePattern.matches(section)) {
            "课程代码与教学班代码必须是官网显示的有效标识"
        }
        val name = wish.courseName.trim()
        val teacher = wish.teacher.trim()
        require(name.isNotEmpty() && name.length <= 120 && teacher.length <= 90 &&
            listOf(name, teacher).none { it.any { ch -> ch.isISOControl() || ch == '<' || ch == '>' } }) {
            "课程名称或教师信息无效"
        }
        val parts = listOf(wish.weekday, wish.startSection, wish.endSection)
        require(parts.all { it == null } || parts.all { it != null }) {
            "上课星期、开始节与结束节必须同时填写"
        }
        if (wish.weekday != null) {
            require(wish.weekday in 1..7 &&
                wish.startSection!! in 1..24 &&
                wish.endSection!! in wish.startSection..24) { "上课星期或节次无效" }
        }
        require(wish.weeks.size <= 35 && wish.weeks.all { it in 1..35 } &&
            wish.weeks.distinct().size == wish.weeks.size) { "上课周次无效" }
        require(wish.weeks.isEmpty() || wish.weekday != null) { "填写周次前需要完整节次" }
        return wish.copy(term = term, courseCode = course, classCode = section,
            courseName = name, teacher = teacher, weeks = wish.weeks.sorted())
    }

    fun add(current: List<NcwuElectiveWish>, wish: NcwuElectiveWish): List<NcwuElectiveWish> {
        val validated = validate(current)
        val normalized = normalize(wish)
        require(validated.none { it.stableKey == normalized.stableKey }) {
            "该学期同一教学班已在志愿中"
        }
        require(validated.size < MAX_ITEMS) { "本机最多保存 60 个志愿" }
        return validated + normalized
    }

    fun remove(current: List<NcwuElectiveWish>, key: String): List<NcwuElectiveWish> {
        val validated = validate(current)
        require(validated.any { it.stableKey == key }) { "志愿已经变化，请重新打开" }
        return validated.filterNot { it.stableKey == key }
    }

    fun move(current: List<NcwuElectiveWish>, from: Int, to: Int): List<NcwuElectiveWish> {
        val validated = validate(current)
        require(from in validated.indices && to in validated.indices) { "志愿位置已变化" }
        return validated.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun conflict(wish: NcwuElectiveWish, others: List<NcwuElectiveWish>): NcwuWishConflict {
        val target = normalize(wish)
        val compared = validate(others).filter {
            it.term == target.term && it.stableKey != target.stableKey &&
                it.courseCode != target.courseCode // Same course may intentionally have alternatives.
        }
        if (compared.isEmpty()) return NcwuWishConflict.INSUFFICIENT_DATA
        if (!target.hasFullMeeting) return NcwuWishConflict.INSUFFICIENT_DATA
        if (compared.any { other -> other.hasFullMeeting &&
            other.weekday == target.weekday &&
            other.startSection!! <= target.endSection!! &&
            target.startSection <= other.endSection!! &&
            other.weeks.any { it in target.weeks } }) return NcwuWishConflict.OVERLAP
        return if (compared.any { !it.hasFullMeeting }) NcwuWishConflict.INSUFFICIENT_DATA
            else NcwuWishConflict.NO_KNOWN_CONFLICT
    }

    /** Accept simple official week numbers; reject unknown 单双周 expressions rather than guessing. */
    fun parseWeeks(input: String): List<Int> {
        if (input.isBlank()) return emptyList()
        require(input.length <= 150) { "周次过长" }
        val weeks = mutableListOf<Int>()
        for (token in input.trim().replace('，', ',').split(',')) {
            val span = Regex("(\\d{1,2})(?:-(\\d{1,2}))?").matchEntire(token.trim())
                ?: throw IllegalArgumentException("周次请使用 1,3,5 或 1-16")
            val first = span.groupValues[1].toInt()
            val last = span.groupValues[2].takeIf(String::isNotEmpty)?.toInt() ?: first
            require(first in 1..35 && last in first..35) { "周次范围无效" }
            weeks.addAll(first..last)
        }
        require(weeks.size <= 35 && weeks.distinct().size == weeks.size) { "周次重复或过多" }
        return weeks.sorted()
    }

    fun validate(wishes: List<NcwuElectiveWish>): List<NcwuElectiveWish> {
        require(wishes.size <= MAX_ITEMS) { "志愿数量超出上限" }
        val normalized = wishes.map(::normalize)
        require(normalized.map { it.stableKey }.distinct().size == normalized.size) {
            "志愿列表存在重复教学班"
        }
        return normalized
    }

    fun encode(wishes: List<NcwuElectiveWish>): String = JSONArray().apply {
        validate(wishes).forEach { wish ->
            put(JSONObject()
                .put("term", wish.term)
                .put("courseCode", wish.courseCode)
                .put("classCode", wish.classCode)
                .put("courseName", wish.courseName)
                .put("teacher", wish.teacher)
                .put("weekday", wish.weekday ?: JSONObject.NULL)
                .put("startSection", wish.startSection ?: JSONObject.NULL)
                .put("endSection", wish.endSection ?: JSONObject.NULL)
                .put("weeks", JSONArray(wish.weeks)))
        }
    }.toString()

    fun decode(raw: String): List<NcwuElectiveWish> {
        require(raw.length <= 60_000) { "本机志愿数据过大" }
        val array = JSONArray(raw)
        require(array.length() <= MAX_ITEMS) { "志愿数量超出上限" }
        val keys = setOf("term", "courseCode", "classCode", "courseName", "teacher",
            "weekday", "startSection", "endSection", "weeks")
        return validate((0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            require(row.keys().asSequence().all { it in keys }) { "发现未知字段，未修改旧志愿" }
            val weeks = row.getJSONArray("weeks")
            NcwuElectiveWish(
                term = row.getString("term"), courseCode = row.getString("courseCode"),
                classCode = row.getString("classCode"), courseName = row.getString("courseName"),
                teacher = row.getString("teacher"),
                weekday = if (row.isNull("weekday")) null else row.getInt("weekday"),
                startSection = if (row.isNull("startSection")) null else row.getInt("startSection"),
                endSection = if (row.isNull("endSection")) null else row.getInt("endSection"),
                weeks = (0 until weeks.length()).map(weeks::getInt)
            )
        })
    }
}

object NcwuElectiveWishStore {
    private const val PREFS = "xiaoxu-ncwu-elective-wishes-v1"
    private const val KEY = "manually-entered-plans"

    /** A corrupt record throws instead of silently erasing or overwriting it. */
    fun load(context: Context): List<NcwuElectiveWish> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        return NcwuElectiveWishPlan.decode(raw)
    }

    fun save(context: Context, wishes: List<NcwuElectiveWish>) {
        val encoded = NcwuElectiveWishPlan.encode(wishes)
        val edit = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        if (wishes.isEmpty()) edit.remove(KEY) else edit.putString(KEY, encoded)
        require(edit.commit()) { "本机保存失败，旧志愿保持不变" }
    }
}
