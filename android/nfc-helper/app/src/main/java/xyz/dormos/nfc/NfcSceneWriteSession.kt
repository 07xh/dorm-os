package xyz.dormos.nfc

import android.nfc.NfcAdapter
import android.nfc.Tag
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.lifecycle.Lifecycle
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

internal data class SceneWriteRequest(
    val tagId: String,
    val scriptId: String,
    val sceneName: String
)

internal enum class SceneWritePhase {
    WAITING, WRITING, CONFIRM_OVERWRITE, CONFIRM_FORMAT,
    FORMATTED, REGISTERING, DONE, FAILED, INTERRUPTED
}

/**
 * Explicit one-shot scene writer on the existing workbench, not a new Activity.
 * Tag I/O is on NFC's callback thread; Compose state changes are posted to main.
 * Root binding is done ONLY by the caller after verified full-message readback.
 */
internal class NfcSceneWriteSession(private val activity: ComponentActivity) : NfcAdapter.ReaderCallback {
    private val adapter = NfcAdapter.getDefaultAdapter(activity)
    private val generation = AtomicInteger(0)
    private val working = AtomicBoolean(false)
    @Volatile private var active = false
    @Volatile private var registered = false
    @Volatile private var requested: SceneWriteRequest? = null
    @Volatile private var overwriteAllowed = false
    @Volatile private var formatAllowed = false
    @Volatile private var expectedUid: String? = null
    private var verifiedCallback: ((SceneWriteRequest, SceneWriteOutcome) -> Unit)? = null

    var phase by mutableStateOf(SceneWritePhase.WAITING)
        private set
    var status by mutableStateOf("请将 NFC 标签靠近手机背部")
        private set
    var lastResult by mutableStateOf<SceneWriteOutcome?>(null)
        private set
    val available: Boolean get() = adapter != null
    val enabled: Boolean get() = adapter?.isEnabled == true
    val isWorking: Boolean get() = working.get() || phase == SceneWritePhase.REGISTERING

    fun begin(request: SceneWriteRequest, afterVerified: (SceneWriteRequest, SceneWriteOutcome) -> Unit): Boolean {
        if (isWorking || !NfcProfiles.validId(request.tagId) ||
            !NfcProfiles.validId(request.scriptId) || request.sceneName.isBlank()) return false
        stop()
        requested = request
        verifiedCallback = afterVerified
        active = true
        phase = SceneWritePhase.WAITING
        status = "准备写入「${request.sceneName}」，请贴近自己的 NFC 标签"
        lastResult = null
        registerIfResumed()
        return true
    }

    fun confirmExistingData() {
        if (!active || isWorking) return
        when (phase) {
            SceneWritePhase.CONFIRM_OVERWRITE -> overwriteAllowed = true
            SceneWritePhase.CONFIRM_FORMAT -> formatAllowed = true
            else -> return
        }
        phase = SceneWritePhase.WAITING
        status = "请再次贴近刚才的标签，确认后才会实际写入"
        registerIfResumed()
    }

    fun retry() {
        if (!active || isWorking) return
        generation.incrementAndGet()
        overwriteAllowed = false
        formatAllowed = false
        expectedUid = null
        lastResult = null
        phase = SceneWritePhase.WAITING
        status = "请移开标签后重新贴近"
        registerIfResumed()
    }

    fun markRegistered(ok: Boolean, message: String) {
        if (!active || phase != SceneWritePhase.REGISTERING) return
        phase = if (ok) SceneWritePhase.DONE else SceneWritePhase.FAILED
        status = message
    }

    fun stop(): Boolean {
        if (isWorking) return false
        active = false
        generation.incrementAndGet()
        requested = null
        verifiedCallback = null
        expectedUid = null
        overwriteAllowed = false
        formatAllowed = false
        unregister()
        return true
    }

    fun onResume() {
        if (active) registerIfResumed()
    }

    fun onPause() {
        generation.incrementAndGet()
        unregister()
        if (phase == SceneWritePhase.WRITING) {
            phase = SceneWritePhase.INTERRUPTED
            status = "写卡过程因应用切到后台而中断，结果未确认，未建立新绑定"
        }
    }

    private fun unregister() {
        if (!registered) return
        registered = false
        try { adapter?.disableReaderMode(activity) } catch (_: Exception) {}
    }

    private fun registerIfResumed() {
        if (!active || !activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
        if (phase != SceneWritePhase.WAITING) return
        if (adapter == null) {
            status = "此设备没有可用 NFC"
            return
        }
        if (!adapter.isEnabled) {
            status = "NFC 未开启，请先到系统设置开启"
            return
        }
        if (registered) return
        try {
            adapter.enableReaderMode(
                activity, this,
                NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_NFC_V or
                    NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS, null
            )
            registered = true
        } catch (_: Exception) {
            phase = SceneWritePhase.FAILED
            status = "无法开启前台写卡，请检查 NFC 开关或系统权限"
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        if (!active || phase != SceneWritePhase.WAITING || !working.compareAndSet(false, true)) return
        val request = requested
        if (request == null) {
            working.set(false)
            return
        }
        val session = generation.get()
        val tagUid = SceneTagWriter.uid(tag)
        val expected = expectedUid
        if (expected != null && expected != tagUid) {
            activity.runOnUiThread {
                if (active && generation.get() == session) {
                    phase = SceneWritePhase.FAILED
                    status = "检测到另一张标签，未覆盖。请重新开始后选择正确标签"
                    unregister()
                }
                working.set(false)
            }
            return
        }
        activity.runOnUiThread {
            if (active && generation.get() == session) {
                phase = SceneWritePhase.WRITING
                status = "正在检查、写入与回读，请保持标签贴合"
            }
        }
        val outcome = try {
            SceneTagWriter.write(tag, request.tagId, overwriteAllowed, formatAllowed)
        } catch (error: Exception) {
            SceneWriteOutcome(
                SceneWriteState.FAILED,
                "NFC 写入异常，结果未经验证，不会建立绑定：" +
                    (error.message ?: error.javaClass.simpleName)
            )
        }
        activity.runOnUiThread {
            try {
                // Losing foreground while physically writing must never create a Root binding.
                if (active && generation.get() == session &&
                    !activity.isFinishing && !activity.isDestroyed) {
                    lastResult = outcome
                    status = outcome.message
                    when (outcome.state) {
                        SceneWriteState.NEED_OVERWRITE -> {
                            phase = SceneWritePhase.CONFIRM_OVERWRITE
                            expectedUid = outcome.tagUid
                            unregister()
                        }
                        SceneWriteState.NEED_FORMAT -> {
                            phase = SceneWritePhase.CONFIRM_FORMAT
                            expectedUid = outcome.tagUid
                            unregister()
                        }
                        SceneWriteState.FORMATTED -> {
                            phase = SceneWritePhase.FORMATTED
                            unregister()
                        }
                        SceneWriteState.VERIFIED -> {
                            phase = SceneWritePhase.REGISTERING
                            unregister()
                            try {
                                verifiedCallback?.invoke(request, outcome)
                            } catch (_: Exception) {
                                phase = SceneWritePhase.FAILED
                                status = "标签回读通过，但 KernelSU 登记未开始；未确认绑定"
                            }
                        }
                        SceneWriteState.FAILED -> {
                            phase = SceneWritePhase.FAILED
                            unregister()
                        }
                    }
                }
            } finally {
                working.set(false)
            }
        }
    }
}
