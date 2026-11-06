package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import io.github.x07xh.xiaoxu.ui.XiaoxuTheme
import io.github.x07xh.xiaoxu.data.SecondClassSaveReceipt

private const val RECEIPT_COUNT = "secondclass_saved_receipt_count"

/** The optional standalone entry reuses the same native second-class page
 * as main navigation. Keep the Activity.RESULT_OK contract for its caller. */
class SecondClassHomeActivity : ComponentActivity() {
    private var savedReceipt: SecondClassSaveReceipt? = null

    private fun setSavedReceipt(receipt: SecondClassSaveReceipt?) {
        savedReceipt = receipt
        if (receipt == null) {
            setResult(Activity.RESULT_CANCELED)
        } else {
            setResult(Activity.RESULT_OK, Intent()
                .putExtra(NativeSchoolConnectActivity.EXTRA_PROVIDER, "second_class")
                .putExtra(NativeSchoolConnectActivity.EXTRA_RETURN_KIND, "credits")
                .putExtra(NativeSchoolConnectActivity.EXTRA_SAVED_COUNT, receipt.savedCount))
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        // Only validated count metadata survives recreation; no school data,
        // cookies, credentials or raw result Intent are stored in this Bundle.
        outState.putInt(RECEIPT_COUNT, savedReceipt?.savedCount ?: 0)
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setSavedReceipt(SecondClassSaveReceipt.validate("second_class", "credits",
            savedInstanceState?.getInt(RECEIPT_COUNT, 0) ?: 0))
        setContent {
            XiaoxuTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = { finish() }) { Text("返回校序") }
                        SecondClassPage(
                            connectOnOpen = true,
                            onRefreshResult = { success ->
                                if (!success) setSavedReceipt(null)
                            },
                            onSavedReceipt = { receipt ->
                                setSavedReceipt(receipt)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
