package io.github.x07xh.campusrun.smoke;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * Runs on the emulator or a real test device: import pinned read modules,
 * but NEVER instantiate clients, open sockets or access any user credentials.
 */
@RunWith(AndroidJUnit4.class)
public class EmbeddedPythonSmokeTest {
    @Test public void pinnedOpenSourceReadModulesLoadInsideAndroid() {
        if (!Python.isStarted()) {
            Python.start(new AndroidPlatform(
                InstrumentationRegistry.getInstrumentation().getTargetContext()
            ));
        }
        String result = Python.getInstance()
            .getModule("readonly_runtime_smoke")
            .callAttr("verify_only").toString();
        assertEquals("PINNED_OSS_READ_IMPORT_OK", result);
    }
}
