package xyz.dormos.nfc

/**
 * Atomically transfers one confirmed NFC write request to its single callback.
 * Cancel/leave cannot race between claiming a tag and beginning its processing.
 *
 * A claimed operation cannot be forcibly cancelled because an NFC write may
 * already have reached the tag. Its outcome must still be read back to verify.
 */
internal class NfcOneShotGate<T : Any> {
    private enum class State { IDLE, ARMED, CLAIMED }
    private val mutex = Any()
    private var state = State.IDLE
    private var request: T? = null

    fun arm(next: T): Boolean = synchronized(mutex) {
        if (state == State.CLAIMED) return@synchronized false
        request = next
        state = State.ARMED
        true
    }

    fun claim(): T? = synchronized(mutex) {
        if (state != State.ARMED) return@synchronized null
        val accepted = request ?: return@synchronized null
        request = null
        state = State.CLAIMED
        accepted
    }

    /** Returns false once a callback has claimed an operation. */
    fun cancelPending(): Boolean = synchronized(mutex) {
        if (state == State.CLAIMED) return@synchronized false
        request = null
        state = State.IDLE
        true
    }

    /** The callback alone marks processing complete. */
    fun finish(): Unit = synchronized(mutex) {
        if (state == State.CLAIMED) state = State.IDLE
    }

    fun isProcessing(): Boolean = synchronized(mutex) { state == State.CLAIMED }
}
