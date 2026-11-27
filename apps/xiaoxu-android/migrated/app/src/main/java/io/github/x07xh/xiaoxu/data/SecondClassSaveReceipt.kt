package io.github.x07xh.xiaoxu.data

/** Metadata from our own reader after its Room transaction has committed. */
class SecondClassSaveReceipt private constructor(val savedCount: Int) {
    companion object {
        fun validate(provider: String?, kind: String?, count: Int): SecondClassSaveReceipt? =
            if (provider == "second_class" && kind == "credits" && count in 1..500)
                SecondClassSaveReceipt(count) else null
    }
}
