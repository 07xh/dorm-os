package io.github.x07xh.xiaoxu.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SecondClassSaveReceiptTest {
    @Test fun onlyCommittedSecondClassCreditReceiptsAreAccepted() {
        assertEquals(2, SecondClassSaveReceipt.validate("second_class", "credits", 2)?.savedCount)
        assertEquals(500, SecondClassSaveReceipt.validate("second_class", "credits", 500)?.savedCount)
        for (count in listOf(-1, 0, 501))
            assertNull(SecondClassSaveReceipt.validate("second_class", "credits", count))
        assertNull(SecondClassSaveReceipt.validate(null, "credits", 2))
        assertNull(SecondClassSaveReceipt.validate("chaoxing", "credits", 2))
        assertNull(SecondClassSaveReceipt.validate("second_class", "activity", 2))
    }
}
