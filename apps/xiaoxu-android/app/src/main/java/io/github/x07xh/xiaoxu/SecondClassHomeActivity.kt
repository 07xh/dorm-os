package io.github.x07xh.xiaoxu

import android.app.Activity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.weight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import io.github.x07xh.xiaoxu.ui.XiaoxuTheme

/** The optional standalone entry reuses the same native second-class page
 * as main navigation. Keep the Activity.RESULT_OK contract for its caller. */
class SecondClassHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            XiaoxuTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(Modifier.fillMaxSize()) {
                        TextButton(onClick = { finish() }) { Text("返回校序") }
                        SecondClassPage(
                            onRefreshResult = { success ->
                                if (success) setResult(Activity.RESULT_OK)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}
