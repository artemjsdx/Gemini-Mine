package com.artemjsdx.geminimine.forensics;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class ForensicReport {

    public static final String GAME_PROCESS = "com.artemjsdx.geminimine";
    public static final long LAUNCH_TIMESTAMP_NEGATIVE_TOLERANCE_MS = 2000L;

    public static String mapReason(int reason) {
        switch (reason) {
            case 1: return "REASON_EXIT_SELF";
            case 2: return "REASON_SIGNALED";
            case 3: return "REASON_LOW_MEMORY";
            case 4: return "REASON_CRASH";
            case 5: return "REASON_CRASH_NATIVE";
            case 6: return "REASON_ANR";
            case 7: return "REASON_INITIALIZATION_FAILURE";
            case 8: return "REASON_PERMISSION_CHANGE";
            case 9: return "REASON_EXCESSIVE_RESOURCE_USAGE";
            case 10: return "REASON_USER_REQUESTED";
            case 11: return "REASON_USER_STOPPED";
            case 12: return "REASON_DEPENDENCY_DIED";
            case 13: return "REASON_OTHER";
            case 14: return "REASON_FREEZER";
            case 15: return "REASON_PACKAGE_STATE_CHANGE";
            case 16: return "REASON_PACKAGE_UPDATED";
            default: return "REASON_UNKNOWN(" + reason + ")";
        }
    }

    public static ApplicationExitInfo queryCurrentRunExitInfo(Context context, long launchWallTimeMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return null;
        }
        try {
            ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (am == null) {
                return null;
            }
            List<ApplicationExitInfo> exitList = am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 15);
            if (exitList == null || exitList.isEmpty()) {
                return null;
            }
            long minTimestamp = launchWallTimeMs - LAUNCH_TIMESTAMP_NEGATIVE_TOLERANCE_MS;
            ApplicationExitInfo newestCurrentRun = null;
            for (ApplicationExitInfo info : exitList) {
                if (GAME_PROCESS.equals(info.getProcessName())) {
                    if (info.getTimestamp() >= minTimestamp) {
                        if (newestCurrentRun == null || info.getTimestamp() > newestCurrentRun.getTimestamp()) {
                            newestCurrentRun = info;
                        }
                    }
                }
            }
            return newestCurrentRun;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static String buildReport(Context context, long launchWallTimeMs, long launchUptimeMs, boolean timedOutWaitingForExitInfo) {
        StringBuilder sb = new StringBuilder();
        sb.append("============================================================\n");
        sb.append("GEMINI MINE — FORENSIC PROBE EVIDENCE REPORT (R3A2-R1)\n");
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
        List<ApplicationExitInfo> allGameRecords = new ArrayList<>();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
                if (am != null) {
                    List<ApplicationExitInfo> exitList = am.getHistoricalProcessExitReasons(context.getPackageName(), 0, 15);
                    sb.append("- Records found for package: ").append(exitList != null ? exitList.size() : 0).append("\n");
                    if (exitList != null) {
                        long minTimestamp = launchWallTimeMs - LAUNCH_TIMESTAMP_NEGATIVE_TOLERANCE_MS;
                        for (ApplicationExitInfo info : exitList) {
                            if (GAME_PROCESS.equals(info.getProcessName())) {
                                allGameRecords.add(info);
                                boolean isCurrentRun = info.getTimestamp() >= minTimestamp;
                                if (isCurrentRun) {
                                    if (matchingInfo == null || info.getTimestamp() > matchingInfo.getTimestamp()) {
                                        matchingInfo = info;
                                    }
                                }
                                long pssKb = info.getPss();
                                long rssKb = info.getRss();
                                double pssMib = pssKb / 1024.0;
                                double rssMib = rssKb / 1024.0;

                                sb.append(String.format(Locale.US,
                                        "  * [%s] [PID:%d | Timestamp:%s] Reason=%s | Status=%d | Importance=%d | PSS=%d kB (%.2f MiB) | RSS=%d kB (%.2f MiB) | Desc=%s\n",
                                        isCurrentRun ? "CURRENT_RUN" : "STALE_HISTORY",
                                        info.getPid(),
                                        new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(info.getTimestamp())),
                                        mapReason(info.getReason()),
                                        info.getStatus(),
                                        info.getImportance(),
                                        pssKb, pssMib,
                                        rssKb, rssMib,
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

        sb.append("\nCURRENT-RUN EXITINFO CORRELATION:\n");
        if (matchingInfo != null) {
            sb.append("- Matched Record: YES (PID ").append(matchingInfo.getPid())
              .append(", Reason=").append(mapReason(matchingInfo.getReason()))
              .append(", Status=").append(matchingInfo.getStatus()).append(")\n");
        } else {
            if (timedOutWaitingForExitInfo) {
                sb.append("- Matched Record: CURRENT_RUN_EXITINFO_NOT_AVAILABLE_AFTER_TIMEOUT\n");
            } else {
                sb.append("- Matched Record: CURRENT_RUN_EXITINFO_NOT_YET_AVAILABLE\n");
            }
        }
        sb.append("\n");

        // Trace evaluation
        sb.append("EXIT TRACE STREAM (Current Run):\n");
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
            sb.append("- Trace Present: NO (no current-run matching exit record)\n");
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
            sb.append("CLASSIFICATION: JAVA_FATAL (Uncaught Java exception in game process)\n");
        } else if (matchingInfo != null && matchingInfo.getReason() == 5 /* REASON_CRASH_NATIVE */) {
            sb.append("CLASSIFICATION: NATIVE_FATAL (REASON_CRASH_NATIVE, signal status=").append(matchingInfo.getStatus()).append(")\n");
        } else if (matchingInfo != null && matchingInfo.getReason() == 4 /* REASON_CRASH */) {
            sb.append("CLASSIFICATION: JAVA_FATAL (REASON_CRASH, status=").append(matchingInfo.getStatus()).append(")\n");
        } else if (matchingInfo != null && matchingInfo.getReason() == 2 /* REASON_SIGNALED */) {
            sb.append("CLASSIFICATION: EXTERNAL_SIGNAL (signal=").append(matchingInfo.getStatus()).append(")\n");
        } else if (matchingInfo != null && matchingInfo.getReason() == 7 /* REASON_INITIALIZATION_FAILURE */) {
            sb.append("CLASSIFICATION: INITIALIZATION_FAILURE\n");
        } else if (hasNativeExit) {
            sb.append("CLASSIFICATION: NORMAL_LIFECYCLE_DESTROY (android_main returned cleanly after destroyRequested)\n");
        } else if (matchingInfo != null && matchingInfo.getReason() == 1 /* REASON_EXIT_SELF */) {
            sb.append("CLASSIFICATION: NORMAL_SELF_EXIT (status=").append(matchingInfo.getStatus()).append(")\n");
        } else if (matchingInfo != null) {
            sb.append("CLASSIFICATION: ").append(mapReason(matchingInfo.getReason())).append(" (status=").append(matchingInfo.getStatus()).append(")\n");
        } else {
            sb.append("CLASSIFICATION: UNKNOWN_CURRENT_RUN (no current-run ExitInfo available and no fatal breadcrumb)\n");
        }

        sb.append("\n============================================================\n");
        return sb.toString();
    }
}
