package io.github.x07xh.xiaoxu.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import org.junit.Assert.*

/** Fail before any mutation if somebody targets an installed owner QA app. */
internal fun assertRuntimeIsolation(c: Context) {
    assertTrue("run with -PxiaoxuRuntimeTest=true", c.packageName.endsWith(".secondclassruntime"))
    assertEquals("runtime APK must be unable to contact school servers",
        PackageManager.PERMISSION_DENIED, c.checkSelfPermission(Manifest.permission.INTERNET))
}
