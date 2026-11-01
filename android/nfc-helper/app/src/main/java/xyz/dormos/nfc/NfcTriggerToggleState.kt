package xyz.dormos.nfc

/** Visual request and KernelSU-confirmed NFC execution gate are distinct. */
internal data class NfcTriggerToggleState(
    val confirmed: Boolean = false,
    val requested: Boolean? = null
) {
    val pending: Boolean get() = requested != null
    val displayed: Boolean get() = requested ?: confirmed

    fun request(target: Boolean): NfcTriggerToggleState =
        if (pending || target == confirmed) this else copy(requested = target)

    fun withConfirmed(value: Boolean): NfcTriggerToggleState =
        if (pending) this else copy(confirmed = value)

    fun complete(success: Boolean): NfcTriggerToggleState =
        if (!pending) this else NfcTriggerToggleState(
            confirmed = if (success) requested!! else confirmed
        )
}
