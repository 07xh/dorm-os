package io.github.x07xh.xiaoxu

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import io.github.x07xh.xiaoxu.ui.GlassPageScaffold
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import io.github.x07xh.xiaoxu.ui.GlassWindowHost
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
        enableEdgeToEdge()
        setSavedReceipt(SecondClassSaveReceipt.validate("second_class", "credits",
            savedInstanceState?.getInt(RECEIPT_COUNT, 0) ?: 0))
        setContent {
            XiaoxuTheme {
                GlassWindowHost {
                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        GlassPageScaffold(title = "第二课堂", onBack = { finish() }) { padding ->
                        Column(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                            .padding(horizontal = 20.dp)) {
                        SecondClassPage(
                            connectOnOpen = true,
                            onRefreshResult = { success ->
                                if (!success) setSavedReceipt(null)
                            },
                            onSavedReceipt = { receipt ->
                                setSavedReceipt(receipt)
                                finish()
                            },
                            modifier = Modifier.weight(1f)
                        )
                        }
                        }
                    }
                }
            }
        }
    }
}
