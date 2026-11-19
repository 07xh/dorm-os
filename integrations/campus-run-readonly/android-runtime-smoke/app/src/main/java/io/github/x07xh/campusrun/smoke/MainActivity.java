package io.github.x07xh.campusrun.smoke;

import android.app.Activity;
import android.os.Bundle;
import android.os.Debug;
import android.os.Process;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.chaquo.python.Python;
import com.chaquo.python.android.AndroidPlatform;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Standalone, account-free runtime benchmark. NOT the 校序 production App.
 * No Android INTERNET permission. Uses the pinned OSS modules only to import
 * existing read method symbols; NEVER creates upstream clients or logs in.
 */
public final class MainActivity extends Activity {
    private static final String TAG = "CAMPUS_RUN_PERF";
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Button runButton;
    private TextView resultView;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (20 * getResources().getDisplayMetrics().density);
        content.setPadding(pad, pad, pad, pad);
        TextView heading = new TextView(this);
        heading.setText("校园跑 · Python 性能测试");
        heading.setTextSize(21);
        content.addView(heading);
        TextView note = new TextView(this);
        note.setText("仅检测本机 Python 与开源只读模块能否加载。无网络权限、无账号登录、无跑步数据读写。与正式校序 App 独立。");
        content.addView(note);
        runButton = new Button(this);
        runButton.setText("测试 Python 启动与 CPU 开销");
        content.addView(runButton);
        resultView = new TextView(this);
        resultView.setText("点击按钮运行。首次启动和再次运行的结果可能不同。");
        resultView.setTextIsSelectable(true);
        content.addView(resultView);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(content);
        setContentView(scroll, new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        runButton.setOnClickListener(v -> {
            runButton.setEnabled(false);
            resultView.setText("测量中…");
            worker.execute(this::measure);
        });
    }

    private static String mark(String label, long wallNs, long cpuMs, long pssKb) {
        long wall = (SystemClock.elapsedRealtimeNanos() - wallNs) / 1_000_000L;
        long cpu = Process.getElapsedCpuTime() - cpuMs;
        long pss = Debug.getPss();
        String output = String.format(Locale.ROOT,
            "%s: 耗时 %d ms；进程 CPU %d ms；PSS 变化 %+d KB；当前 PSS %d KB",
            label, wall, cpu, pss - pssKb, pss);
        Log.i(TAG, output);
        return output;
    }

    private void measure() {
        String text;
        try {
            StringBuilder output = new StringBuilder();
            boolean started = Python.isStarted();
            long w = SystemClock.elapsedRealtimeNanos();
            long c = Process.getElapsedCpuTime();
            long m = Debug.getPss();
            if (!started) Python.start(new AndroidPlatform(getApplicationContext()));
            output.append(mark(started ? "Python 已加载" : "Python 首次启动", w, c, m)).append("\n\n");

            w = SystemClock.elapsedRealtimeNanos();
            c = Process.getElapsedCpuTime();
            m = Debug.getPss();
            String first = Python.getInstance().getModule("readonly_runtime_smoke")
                .callAttr("verify_only").toString();
            if (!"PINNED_OSS_READ_IMPORT_OK".equals(first)) throw new IllegalStateException("模块校验失败");
            output.append(mark("首次导入开源只读模块", w, c, m)).append("\n\n");

            w = SystemClock.elapsedRealtimeNanos();
            c = Process.getElapsedCpuTime();
            m = Debug.getPss();
            String warm = Python.getInstance().getModule("readonly_runtime_smoke")
                .callAttr("verify_only").toString();
            if (!"PINNED_OSS_READ_IMPORT_OK".equals(warm)) throw new IllegalStateException("模块再次校验失败");
            output.append(mark("再次调用", w, c, m));
            output.append("\n\n进程 CPU 时间不是 CPU 使用率；PSS 是本进程分摊内存。测试过程中未请求运动世界校园服务器。");
            text = output.toString();
        } catch (Exception failure) {
            // Never show exception text: a future upstream exception might
            // accidentally include a URL or sensitive environment value.
            text = "测试失败，未得到可用性能数据（详见本机调试日志）。";
            Log.e(TAG, "isolated_python_runtime_test_failed");
        }
        final String display = text;
        runOnUiThread(() -> {
            if (!isFinishing() && !isDestroyed()) {
                resultView.setText(display);
                runButton.setEnabled(true);
            }
        });
    }

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
