package xyz.dormos.nfc

import android.net.Uri
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal enum class NdefWritePhase { IDLE, EDITING, ARMED, WRITING, VERIFIED, FAILED, INTERRUPTED }

internal data class NdefWriteOutcome(
    val verified: Boolean,
    val message: String,
    val bytes: Int = 0
)

/**
 * One-shot ordinary NDEF writer hosted by ManagerActivity.
 *
 * It deliberately has no KernelSU dependency. The session is registered only
 * while the inline sheet is armed and the host Activity is resumed. Every
 * successful write is accepted only after a byte-for-byte NDEF readback.
 */
internal class NdefWriteSession(
    private val activity: ComponentActivity
) : NfcAdapter.ReaderCallback {
    private data class Request(
        val draft: PreparedNdefWrite,
        val overwriteConfirmed: Boolean
    )

    private val adapter = NfcAdapter.getDefaultAdapter(activity)
    private val gate = NfcOneShotGate<Request>()
    private val generation = AtomicInteger(0)
    private val working = AtomicBoolean(false)

    @Volatile private var active = false
    @Volatile private var armed = false
    @Volatile private var registered = false

    var phase by mutableStateOf(NdefWritePhase.IDLE)
        private set
    var status by mutableStateOf("编辑内容并预览，确认后才会开始写卡")
        private set
    var lastResult by mutableStateOf<NdefWriteOutcome?>(null)
        private set
    var nfcAvailable by mutableStateOf(adapter != null)
        private set
    var nfcEnabled by mutableStateOf(adapter?.isEnabled == true)
        private set

    val isWorking: Boolean get() = working.get() || phase == NdefWritePhase.WRITING
    val canDismiss: Boolean get() = !isWorking

    fun start(): Boolean {
        if (!stop()) return false
        active = true
        phase = NdefWritePhase.EDITING
        status = "选择文本或网址，确认后等待贴近实体标签"
        lastResult = null
        refreshDeviceState()
        return true
    }

    fun arm(draft: PreparedNdefWrite, overwriteConfirmed: Boolean): Boolean {
        if (!active || isWorking) return false
        refreshDeviceState()
        if (adapter == null) {
            status = "当前设备不支持 NFC"
            phase = NdefWritePhase.FAILED
            return false
        }
        if (!adapter.isEnabled) {
            status = "请先开启 NFC，再确认写卡"
            return false
        }
        if (!gate.arm(Request(draft, overwriteConfirmed))) {
            status = "前一次 NFC 操作尚未结束，请先确认标签状态"
            return false
        }
        armed = true
        phase = NdefWritePhase.ARMED
        lastResult = null
        status = "单次写卡已准备，请将标签贴近手机背部"
        registerIfResumed()
        return true
    }

    fun cancelArmed(): Boolean {
        if (!gate.cancelPending()) {
            status = "写入或回读已经开始，不能强制取消"
            return false
        }
        armed = false
        unregister()
        phase = NdefWritePhase.EDITING
        status = "已取消等待贴卡，没有开始写入"
        return true
    }

    fun returnToEdit(): Boolean {
        if (isWorking) return false
        gate.cancelPending()
        armed = false
        unregister()
        phase = NdefWritePhase.EDITING
        lastResult = null
        status = "可以修改内容后重新预览"
        return true
    }

    fun stop(): Boolean {
        if (isWorking) return false
        active = false
        armed = false
        generation.incrementAndGet()
        gate.cancelPending()
        unregister()
        phase = NdefWritePhase.IDLE
        lastResult = null
        return true
    }

    fun onResume() {
        refreshDeviceState()
        if (active && armed) registerIfResumed()
    }

    fun onPause() {
        generation.incrementAndGet()
        unregister()
        if (phase == NdefWritePhase.WRITING || working.get()) {
            // Also covers the tiny race where the NFC callback has claimed the tag
            // but its main-thread "writing" state update has not run yet.
            phase = NdefWritePhase.INTERRUPTED
            status = "写卡会话离开前台，结果尚未确认。请先重新读取标签，不要直接重复写入。"
        } else if (phase == NdefWritePhase.ARMED) {
            status = "等待贴卡已暂停；返回本页面后会继续等待"
        }
    }

    private fun refreshDeviceState() {
        nfcAvailable = adapter != null
        nfcEnabled = adapter?.isEnabled == true
    }

    private fun unregister() {
        if (!registered) return
        registered = false
        try { adapter?.disableReaderMode(activity) } catch (_: Exception) {}
    }

    private fun registerIfResumed() {
        if (!active || !armed || phase != NdefWritePhase.ARMED ||
            !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (adapter == null) {
            status = "当前设备不支持 NFC"
            return
        }
        if (!adapter.isEnabled) {
            refreshDeviceState()
            status = "NFC 未开启，请先到系统设置开启"
            return
        }
        if (registered) return
        try {
            adapter.enableReaderMode(
                activity, this,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS,
                null
            )
            registered = true
            refreshDeviceState()
        } catch (_: Exception) {
            armed = false
            gate.cancelPending()
            phase = NdefWritePhase.FAILED
            status = "无法开启前台 NFC 写卡，请检查系统开关和应用权限"
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        if (!active || !armed || !working.compareAndSet(false, true)) return
        val request = gate.claim()
        if (request == null) {
            working.set(false)
            return
        }
        armed = false
        val session = generation.get()
        activity.runOnUiThread {
            if (active && generation.get() == session &&
                !activity.isFinishing && !activity.isDestroyed) {
                phase = NdefWritePhase.WRITING
                status = "正在检查标签、写入并回读，请保持贴合"
            }
        }

        var writeIssued = false
        val outcome = try {
            writeAndVerify(tag, request, session) { writeIssued = true }
        } catch (error: Exception) {
            NdefWriteOutcome(false, NfcFailureFeedback.writeFailure(error, writeIssued))
        }
        gate.finish()
        activity.runOnUiThread {
            try {
                if (active && generation.get() == session &&
                    !activity.isFinishing && !activity.isDestroyed) {
                    lastResult = outcome
                    phase = if (outcome.verified) NdefWritePhase.VERIFIED else NdefWritePhase.FAILED
                    status = if (outcome.verified) "写入成功 · 完整 NDEF 回读一致"
                        else "未验证成功 · 请按提示核对标签状态"
                    unregister()
                }
            } finally {
                working.set(false)
            }
        }
    }

    private fun writeAndVerify(
        tag: Tag,
        request: Request,
        session: Int,
        onWriteIssued: () -> Unit
    ): NdefWriteOutcome {
        val record = when (request.draft.kind) {
            NdefWriteKind.TEXT -> NdefRecord.createTextRecord("zh", request.draft.content)
            NdefWriteKind.WEB_URL -> NdefRecord.createUri(Uri.parse(request.draft.content))
        }
        val intended = NdefMessage(arrayOf(record))
        val bytes = intended.toByteArray()
        val ndef = Ndef.get(tag)
            ?: return NdefWriteOutcome(false, NfcFailureFeedback.noNdef(NdefFormatable.get(tag) != null))
        ndef.connect()
        return try {
            val previous = ndef.ndefMessage
            val mimeTypes = previous?.records
                ?.filter { it.tnf == NdefRecord.TNF_MIME_MEDIA }
                ?.map { String(it.type, StandardCharsets.US_ASCII) }
                ?: emptyList()
            val deny = NdefWriteRules.replacementError(
                previous?.records?.size ?: 0,
                mimeTypes,
                request.overwriteConfirmed
            )
            if (deny != null) return NdefWriteOutcome(false, deny)
            if (!ndef.isWritable) return NdefWriteOutcome(false, "标签只读；未修改任何数据")
            if (ndef.maxSize < bytes.size) {
                return NdefWriteOutcome(
                    false, NfcFailureFeedback.insufficientCapacity(bytes.size, ndef.maxSize)
                )
            }
            if (!active || generation.get() != session ||
                !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                return NdefWriteOutcome(false, "应用已离开前台，没有开始写入。")
            }

            onWriteIssued()
            ndef.writeNdefMessage(intended)
            val actual = ndef.ndefMessage
                ?: return NdefWriteOutcome(false, NfcFailureFeedback.readbackUnavailable())
            if (!bytes.contentEquals(actual.toByteArray())) {
                return NdefWriteOutcome(false, NfcFailureFeedback.readbackMismatch())
            }
            NdefWriteOutcome(
                true,
                "写入并回读成功 · ${bytes.size} 字节。未调用 KernelSU，也未执行自动化脚本。",
                bytes.size
            )
        } finally {
            try { ndef.close() } catch (_: Exception) {}
        }
    }
}
