package com.artemjsdx.geminimine.forensics;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ForensicReport {
    private static final String TAG = "ForensicReport";
    public static final String GAME_PROCESS = "com.artemjsdx.geminimine";

    public static String mapReason(int reason) {
        switch (reason) {
            case 0: return "REASON_UNKNOWN (0)";
            case 1: return "REASON_EXIT_SELF (1)";
            case 2: return "REASON_SIGNALED (2)";
            case 3: return "REASON_LOW_MEMORY (3)";
            case 4: return "REASON_CRASH (4)";
            case 5: return "REASON_CRASH_NATIVE (5)";
            case 6: return "REASON_ANR (6)";
            case 7: return "REASON_INITIALIZATION_FAILURE (7)";
            case 8: return "REASON_PERMISSION_CHANGE (8)";
            case 9: return "REASON_EXCESSIVE_RESOURCE_USAGE (9)";
            case 10: return "REASON_USER_REQUESTED (10)";
            case 11: return "REASON_USER_STOPPED (11)";
            case 12: return "REASON_DEPENDENCY_DIED (12)";
            case 13: return "REASON_OTHER (13)";
            case 14: return "REASON_FREEZER (14)";
            case 15: return "REASON_PACKAGE_STATE_CHANGE (15)";
            case 16: return "REASON_PACKAGE_UPDATED (16)";
            default: return "REASON_UNKNOWN (" + reason + ")";
        }
    }

    public static String buildReport(Context context, long launchWallTimeMs, long launchUptimeMs) {
        StringBuilder sb = new StringBuilder();
        sb.append("============================================================\n");
        sb.append("GEMINI MINE — GM001-R3A2 PHYSICAL FORENSIC EVIDENCE REPORT\n");
        sb.append("============================================================\n\n");

        sb.append("DEVICE CONTEXT:\n");
        sb.append("- Manufacturer/Model: ").append(Build.MANUFACTURER).append(" / ").append(Build.MODEL).append(" (").append(Build.DEVICE).append(")\n");
        sb.append("- Android Version: ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        sb.append("- Fingerprint: ").append(Build.FINGERPRINT).append("\n");
        sb.append("- Forensics Process: ").append(context.getPackageName()).append(":forensics (PID: ").append(android.os.Process.myPid()).append(")\n");
        sb.append("- Game Target Process: ").append(GAME_PROCESS).append("\n");
        sb.append("- Launch Time (Wall): ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(launchWallTimeMs))).append("\n\n");

        // Query ApplicationExitInfo
        sb.append("APPLICATION EXIT INFO (Game Process Only):\n");
        ApplicationExitInfo matchingInfo = null;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ApplicationExitInfo> exitList = am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 15);
                    sb.append("- Records found for package: ").append(exitList != null ? exitList.size() : 0).append("\n");
                    if (exitList != null) {
                        for (ApplicationExitInfo info : exitList) {
                            if (GAME_PROCESS.equals(info.getProcessName())) {
                                if (matchingInfo == null) {
                                    matchingInfo = info;
                                }
                                sb.append(String.format(Locale.US,
                                        "  * [PID:%d | Timestamp:%s] Reason=%s | Status=%d | Importance=%d | PSS=%d KB | RSS=%d KB | Desc='%s'\n",
                                        info.getPid(),
                                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(info.getTimestamp())),
                                        mapReason(info.getReason()),
                                        info.getStatus(),
                                        info.getImportance(),
                                        info.getPss() / 1024,
                                        info.getRss() / 1024,
                                        info.getDescription() != null ? info.getDescription() : "null"
                                ));
                            }
                        }
                    }
                }
            } catch (Exception e) {
                sb.append("  [Error querying ApplicationExitInfo: ").append(e.getMessage()).append("]\n");
            }
        } else {
            sb.append("- ApplicationExitInfo API not supported on SDK < 30\n");
        }
        sb.append("\n");

        // Trace evaluation
        sb.append("EXIT TRACE STREAM:\n");
        if (matchingInfo != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try (InputStream is = matchingInfo.getTraceInputStream()) {
                if (is != null) {
                    File traceFile = new File(new File(context.getFilesDir(), "startup-probe"), "exit-trace.bin");
                    MessageDigest md = MessageDigest.getInstance("SHA-256");
                    long byteCount = 0;
                    try (FileOutputStream fos = new FileOutputStream(traceFile)) {
                        byte[] buf = new byte[8192];
                        int r;
                        while ((r = is.read(buf)) != -1) {
                            fos.write(buf, 0, r);
                            md.update(buf, 0, r);
                            byteCount += r;
                        }
                    }
                    byte[] digest = md.digest();
                    StringBuilder hexSha = new StringBuilder();
                    for (byte b : digest) {
                        hexSha.append(String.format("%02x", b));
                    }
                    sb.append("- Trace Present: YES\n");
                    sb.append("- Saved File: ").append(traceFile.getAbsolutePath()).append("\n");
                    sb.append("- Byte Size: ").append(byteCount).append(" bytes\n");
                    sb.append("- SHA-256: ").append(hexSha.toString()).append("\n");
                } else {
                    sb.append("- Trace Present: NO (null InputStream)\n");
                }
            } catch (Exception e) {
                sb.append("- Trace Error: ").append(e.getMessage()).append("\n");
            }
        } else {
            sb.append("- Trace Present: NO\n");
        }
        sb.append("\n");

        // Read Game Process Log
        sb.append("GAME PROCESS BREADCRUMBS (game-current.log):\n");
        File gameLogFile = ProbeLogger.getLogFile(context, "game-current.log");
        String lastBreadcrumb = "NONE (log empty or unwritten)";
        boolean hasNativeEntry = false;
        boolean hasNativeExit = false;
        boolean hasJavaCrash = false;

        if (gameLogFile.exists() && gameLogFile.length() > 0) {
            sb.append("------------------------------------------------------------\n");
            try (BufferedReader br = new BufferedReader(new FileReader(gameLogFile))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line).append("\n");
                    if (!line.trim().isEmpty()) {
                        lastBreadcrumb = line.trim();
                    }
                    if (line.contains("ANDROID_MAIN_ENTER")) {
                        hasNativeEntry = true;
                    }
                    if (line.contains("ANDROID_MAIN_EXIT")) {
                        hasNativeExit = true;
                    }
                    if (line.contains("JAVA_UNCAUGHT_EXCEPTION") || line.contains("FATAL EXCEPTION")) {
                        hasJavaCrash = true;
                    }
                }
            } catch (Exception e) {
                sb.append("[Error reading game log: ").append(e.getMessage()).append("]\n");
            }
            sb.append("------------------------------------------------------------\n");
        } else {
            sb.append("[game-current.log is missing or 0 bytes]\n");
        }
        sb.append("\n");

        sb.append("LAST CONFIRMED BREADCRUMB:\n");
        sb.append(lastBreadcrumb).append("\n\n");

        // Exit Classification Synthesis
        sb.append("SYNTHESIZED EXIT CLASSIFICATION:\n");
        if (hasJavaCrash) {
            sb.append("CLASSIFICATION: JAVA_FATAL\n");
        } else if (matchingInfo != null && matchingInfo.getReason() == 5 /* REASON_CRASH_NATIVE */) {
            sb.append("CLASSIFICATION: NATIVE_FATAL (REASON_CRASH_NATIVE, signal status=").append(matchingInfo.getStatus()).append(")\n");
        } else if (matchingInfo != null && matchingInfo.getReason() == 2 /* REASON_SIGNALED */) {
            sb.append("CLASSIFICATION: EXTERNAL_SIGNAL (signal=").append(matchingInfo.getStatus()).append(")\n");
        } else if (hasNativeExit) {
            sb.append("CLASSIFICATION: NORMAL_LIFECYCLE_DESTROY (android_main returned cleanly after destroyRequested)\n");
        } else if (matchingInfo != null && matchingInfo.getReason() == 1 /* REASON_EXIT_SELF */) {
            sb.append("CLASSIFICATION: NORMAL_SELF_EXIT (status=").append(matchingInfo.getStatus()).append(")\n");
        } else if (matchingInfo != null && matchingInfo.getReason() == 7 /* REASON_INITIALIZATION_FAILURE */) {
            sb.append("CLASSIFICATION: INITIALIZATION_FAILURE\n");
        } else if (matchingInfo != null) {
            sb.append("CLASSIFICATION: ").append(mapReason(matchingInfo.getReason())).append(" (status=").append(matchingInfo.getStatus()).append(")\n");
        } else {
            sb.append("CLASSIFICATION: UNKNOWN (awaiting ExitInfo publish)\n");
        }
        sb.append("\n============================================================\n");

        return sb.toString();
    }
}
