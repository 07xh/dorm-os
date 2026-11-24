package io.github.x07xh.campusrun.smoke;

import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

/**
 * Performance smoke on isolated process: NO account, network, or workout requests.
 *
 * Process CPU time (not a percent of device-wide CPU), elapsed wall time
 * and proportional-set-size memory are measured inside the Android process.
 * Emulator measurements are diagnostic, NOT Ace 6 performance evidence.
 */
@RunWith(AndroidJUnit4.class)
public class EmbeddedPythonSmokeTest {
    private static final String PERF_TAG = "CAMPUS_RUN_PERF";

    private static long elapsedMs(long nanos) {
        return (SystemClock.elapsedRealtimeNanos() - nanos) / 1_000_000L;
    }

    private static void publish(
        String name, long startedNanos, long cpuBeforeMs, long pssBeforeKb
    ) {
        Log.i(PERF_TAG, name
            + " wall_ms=" + elapsedMs(startedNanos)
            + " process_cpu_ms=" + (Process.getElapsedCpuTime() - cpuBeforeMs)
            + " pss_delta_kb=" + (Debug.getPss() - pssBeforeKb)
            + " pss_total_kb=" + Debug.getPss());
    }

    @Test public void pinnedOpenSourceReadModulesLoadInsideAndroid() throws InterruptedException {
        final boolean alreadyStarted = Python.isStarted();
        final long startupTime = SystemClock.elapsedRealtimeNanos();
        final long startupCpu = Process.getElapsedCpuTime();
        final long startupPss = Debug.getPss();

        if (!alreadyStarted) {
            Python.start(new AndroidPlatform(
                InstrumentationRegistry.getInstrumentation().getTargetContext()
            ));
        }
        publish(alreadyStarted ? "python_prestarted" : "python_first_start",
            startupTime, startupCpu, startupPss);

        final long importTime = SystemClock.elapsedRealtimeNanos();
        final long importCpu = Process.getElapsedCpuTime();
        final long importPss = Debug.getPss();
        String result = Python.getInstance()
            .getModule("readonly_runtime_smoke")
            .callAttr("verify_only").toString();
        publish("pinned_oss_first_import", importTime, importCpu, importPss);
        assertEquals("PINNED_OSS_READ_IMPORT_OK", result);

        final long warmTime = SystemClock.elapsedRealtimeNanos();
        final long warmCpu = Process.getElapsedCpuTime();
        final long warmPss = Debug.getPss();
        String warmResult = Python.getInstance()
            .getModule("readonly_runtime_smoke")
            .callAttr("verify_only").toString();
        publish("pinned_oss_warm_import", warmTime, warmCpu, warmPss);
        assertEquals("PINNED_OSS_READ_IMPORT_OK", warmResult);

        // This is a rough diagnostic while the app is alive and foregrounded;
        // it is NOT a long-running/background CPU or battery measurement.
        final long idleTime = SystemClock.elapsedRealtimeNanos();
        final long idleCpu = Process.getElapsedCpuTime();
        final long idlePss = Debug.getPss();
        Thread.sleep(2_000L);
        publish("two_second_idle_after_python", idleTime, idleCpu, idlePss);
    }
}
