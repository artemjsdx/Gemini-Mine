package com.artemjsdx.geminimine.forensics;

import android.app.Activity;
import android.app.ApplicationExitInfo;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.artemjsdx.geminimine.MainActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;

public class ForensicLauncherActivity extends Activity {
    private static final String TAG = "ForensicLauncher";
    private static final int MAX_POLL_ITERATIONS = 32; // 32 * 250ms = 8.0s
    private static final long POLL_INTERVAL_MS = 250L;

    private final LoopbackReportServer mServer = new LoopbackReportServer();
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private boolean mGameLaunched = false;
    private boolean mEvidenceCaptured = false;
    private long mLaunchWallTimeMs = 0;
    private long mLaunchUptimeMs = 0;

    private TextView mTitleText;
    private TextView mStatusText;
    private TextView mDetailsText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ProbeLogger.log("FORENSICS_LAUNCHER_ONCREATE", "PID=" + android.os.Process.myPid());

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(32, 48, 32, 32);

        mTitleText = new TextView(this);
        mTitleText.setText("Gemini Mine — Forensic Probe (R3A2-R1)");
        mTitleText.setTextColor(Color.WHITE);
        mTitleText.setTextSize(18f);
        mTitleText.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(mTitleText);

        mStatusText = new TextView(this);
        mStatusText.setText("Initializing loopback evidence channel...");
        mStatusText.setTextColor(Color.LTGRAY);
        mStatusText.setTextSize(14f);
        mStatusText.setPadding(0, 16, 0, 16);
        root.addView(mStatusText);

        ScrollView scroll = new ScrollView(this);
        scroll.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        mDetailsText = new TextView(this);
        mDetailsText.setText("Starting GameActivity in separate process...");
        mDetailsText.setTextColor(Color.GREEN);
        mDetailsText.setTextSize(12f);
        mDetailsText.setTypeface(Typeface.MONOSPACE);
        scroll.addView(mDetailsText);

        root.addView(scroll);
        setContentView(root);

        File gameLog = ProbeLogger.getLogFile(this, "game-current.log");
        mServer.start(System.currentTimeMillis(), gameLog);
        int port = mServer.getBoundPort();
        mStatusText.setText(String.format("Loopback bound on 127.0.0.1:%d | Launching GameActivity...", port));
    }

    @Override
    protected void onResume() {
        super.onResume();
        ProbeLogger.log("FORENSICS_LAUNCHER_ONRESUME", "launched=" + mGameLaunched + " | captured=" + mEvidenceCaptured);

        if (!mGameLaunched) {
            mGameLaunched = true;
            mLaunchWallTimeMs = System.currentTimeMillis();
            mLaunchUptimeMs = SystemClock.uptimeMillis();
            ProbeLogger.log("FORENSICS_PROCESS_READY", "launching game process");

            ProbeLogger.clearFile(this, "game-current.log");
            ProbeLogger.clearFile(this, "forensics-report.txt");

            Intent gameIntent = new Intent(this, MainActivity.class);
            ProbeLogger.log("GAME_ACTIVITY_START_REQUESTED", "Intent dispatched");
            startActivity(gameIntent);
        } else {
            ProbeLogger.log("GAME_ACTIVITY_RETURNED_OR_DIED", "Forensics process foregrounded");
            if (!mEvidenceCaptured) {
                mEvidenceCaptured = true;
                mStatusText.setText("Game exited. Polling ApplicationExitInfo and breadcrumbs...");
                collectEvidenceAsync();
            }
        }
    }

    private void collectEvidenceAsync() {
        new Thread(() -> {
            boolean timedOut = true;
            ApplicationExitInfo matchingInfo = null;

            for (int i = 0; i < MAX_POLL_ITERATIONS; i++) {
                try {
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException ignored) {
                }
                matchingInfo = ForensicReport.queryCurrentRunExitInfo(ForensicLauncherActivity.this, mLaunchWallTimeMs);
                if (matchingInfo != null) {
                    timedOut = false;
                    ProbeLogger.log("EXIT_INFO_POLL_SUCCESS", "iteration=" + (i + 1) + " | PID=" + matchingInfo.getPid());
                    break;
                }
            }

            if (timedOut) {
                ProbeLogger.log("EXIT_INFO_POLL_TIMEOUT", "No current-run exit record found within 8.0s");
            }

            final String report = ForensicReport.buildReport(ForensicLauncherActivity.this, mLaunchWallTimeMs, mLaunchUptimeMs, timedOut);

            try {
                File reportFile = ProbeLogger.getLogFile(ForensicLauncherActivity.this, "forensics-report.txt");
                try (FileOutputStream fos = new FileOutputStream(reportFile)) {
                    fos.write(report.getBytes(StandardCharsets.UTF_8));
                    fos.flush();
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to persist report file: " + e.getMessage());
            }

            String classification = "UNKNOWN";
            if (report.contains("CLASSIFICATION: ")) {
                int idx = report.indexOf("CLASSIFICATION: ");
                int end = report.indexOf("\n", idx);
                if (end > idx) {
                    classification = report.substring(idx + 16, end).trim();
                }
            }
            mServer.updateReport(report, classification);

            final String finalClass = classification;
            final int port = mServer.getBoundPort();
            mHandler.post(() -> {
                mStatusText.setText(String.format("EVIDENCE READY ON 127.0.0.1:%d/report\nClassification: %s\nDO NOT CLOSE THIS SCREEN.", port, finalClass));
                mStatusText.setTextColor(Color.CYAN);
                mDetailsText.setText(report);
            });
        }).start();
    }

    @Override
    protected void onDestroy() {
        ProbeLogger.log("FORENSICS_LAUNCHER_ONDESTROY", null);
        mServer.stop();
        super.onDestroy();
    }
}
