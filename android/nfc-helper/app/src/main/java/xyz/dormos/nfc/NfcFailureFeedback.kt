package xyz.dormos.nfc

import java.io.IOException

/**
 * Human-facing outcomes: distinguish "not written" from "possibly written".
 * Keep messages deterministic and actionable; never dump untrusted NFC contents.
 */
internal object NfcFailureFeedback {
    fun noNdef(formatable: Boolean): String =
        if (formatable)
            "标签尚未格式化为 NDEF，当前版本不会自动格式化。请换用已初始化的普通 NFC 标签。"
        else
            "已检测到标签，但不支持标准 NDEF 读写。请使用 NTAG 等兼容的 NDEF 标签。"

    fun readFailure(error: Throwable): String = when {
        error.javaClass.simpleName == "TagLostException" ->
            "读取中标签离开感应区。保持手机与标签贴合，再点重新扫描。"
        error is SecurityException ->
            "读取被系统阻止。请检查 NFC 开关及应用 NFC 权限。"
        error is IOException ->
            "NFC 通信中断。移开标签再贴近，避免同时贴近多张卡。"
        else ->
            "未能解析此标签的 NDEF 内容。可重新扫描；不会修改标签。"
    }

    fun writeFailure(error: Throwable, writeStarted: Boolean): String {
        val action = if (writeStarted)
            "写入可能已发生，但尚未完成回读验证。请先用“读取标签”核对内容，不要直接重复写入。"
        else
            "没有确认写入成功。请移开标签后检查 NFC 状态，再重新发起。"
        return when {
            error.javaClass.simpleName == "TagLostException" ->
                "标签在操作时离开感应区。$action"
            error is SecurityException ->
                "系统拒绝 NFC 写入操作。$action"
            error is IOException ->
                "NFC 连接中断。$action"
            else ->
                "NFC 操作出现异常。$action"
        }
    }

    fun insufficientCapacity(neededBytes: Int, maxBytes: Int): String =
        "标签容量不足：需要 $neededBytes B，实际仅 $maxBytes B。请缩短内容或更换容量更大的标签。"

    fun readbackMismatch(): String =
        "写入已发送，但实际回读内容不一致。结果不可信；请先单独读卡确认。"

    fun readbackUnavailable(): String =
        "写入已发送，但无法重新读取标签进行验证。请重新进入读取页面核对。"
}
