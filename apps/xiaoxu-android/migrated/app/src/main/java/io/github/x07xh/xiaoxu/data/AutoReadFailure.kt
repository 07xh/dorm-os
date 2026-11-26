package io.github.x07xh.xiaoxu.data

/**
 * Typed, non-secret failure state for the read-only second-class reader.
 * Carries only a bounded status label; never stores a URL, cookie, or credential.
 */
internal class AutoReadFailure(val status: String) : Exception(status) {
    init {
        require(status in setOf("needs_reconnect", "needs_confirmation", "failed"))
    }
}
