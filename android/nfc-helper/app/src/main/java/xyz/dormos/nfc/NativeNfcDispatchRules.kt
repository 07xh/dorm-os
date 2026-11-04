package xyz.dormos.nfc

/**
 * Pure NFC scene-dispatch verification. A URI supplied in an Android Intent is never
 * proof that the real tag contains that URI: the receiver must re-read the tag.
 */
internal enum class PhysicalUriReadState {
    FOUND, NOT_NDEF, MISSING_URI_RECORD, IO_FAILED
}

internal data class PhysicalUriRead(
    val state: PhysicalUriReadState,
    val uri: String? = null
) {
    companion object {
        fun found(uri: String) = PhysicalUriRead(PhysicalUriReadState.FOUND, uri)
        fun notNdef() = PhysicalUriRead(PhysicalUriReadState.NOT_NDEF)
        fun missingUri() = PhysicalUriRead(PhysicalUriReadState.MISSING_URI_RECORD)
        fun ioFailed() = PhysicalUriRead(PhysicalUriReadState.IO_FAILED)
    }
}

internal enum class NativeNfcDispatchDecision {
    VERIFIED, INVALID_INTENT_URI, NOT_NDEF, MISSING_URI_RECORD, IO_FAILED, URI_MISMATCH
}

internal object NativeNfcDispatchRules {
    fun verify(intentUri: String?, physical: PhysicalUriRead): NativeNfcDispatchDecision {
        val intentId = NativeSceneRules.sceneId(intentUri)
            ?: return NativeNfcDispatchDecision.INVALID_INTENT_URI
        return when (physical.state) {
            PhysicalUriReadState.NOT_NDEF -> NativeNfcDispatchDecision.NOT_NDEF
            PhysicalUriReadState.MISSING_URI_RECORD -> NativeNfcDispatchDecision.MISSING_URI_RECORD
            PhysicalUriReadState.IO_FAILED -> NativeNfcDispatchDecision.IO_FAILED
            PhysicalUriReadState.FOUND -> {
                val actualUri = physical.uri
                if (actualUri != intentUri || NativeSceneRules.sceneId(actualUri) != intentId) {
                    NativeNfcDispatchDecision.URI_MISMATCH
                } else NativeNfcDispatchDecision.VERIFIED
            }
        }
    }
}
