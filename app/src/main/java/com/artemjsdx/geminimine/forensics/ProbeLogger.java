package com.artemjsdx.geminimine.forensics;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class ProbeLogger {
    private static final String TAG = "GeminiMineProbe";
    private static final Object sLock = new Object();
    private static File sLogFile = null;

    public static void init(Context context, String fileName) {
        synchronized (sLock) {
            File dir = new File(context.getFilesDir(), "startup-probe");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            sLogFile = new File(dir, fileName);
        }
    }

    public static void clearFile(Context context, String fileName) {
        synchronized (sLock) {
            File dir = new File(context.getFilesDir(), "startup-probe");
            if (!dir.exists()) {
                dir.mkdirs();
            }
            File f = new File(dir, fileName);
            if (f.exists()) {
                f.delete();
            }
        }
    }

    public static File getLogFile(Context context, String fileName) {
        File dir = new File(context.getFilesDir(), "startup-probe");
        return new File(dir, fileName);
    }

    public static File getActiveLogFile() {
        synchronized (sLock) {
            return sLogFile;
        }
    }

    public static void log(String stage, String details) {
        long uptimeMs = SystemClock.uptimeMillis();
        long wallMs = System.currentTimeMillis();
        String timeStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(new Date(wallMs));

        String entry = String.format(Locale.US, "[%s | +%dms | PID:%d | TID:%d] %s%s\n",
                timeStr, uptimeMs, android.os.Process.myPid(), android.os.Process.myTid(),
                stage, (details != null && !details.isEmpty()) ? " : " + details : "");

        Log.i(TAG, entry.trim());

        synchronized (sLock) {
            if (sLogFile == null) {
                return;
            }
            try (FileOutputStream fos = new FileOutputStream(sLogFile, true);
                 OutputStreamWriter osw = new OutputStreamWriter(fos, StandardCharsets.UTF_8)) {
                osw.write(entry);
                osw.flush();
                try {
                    fos.getFD().sync();
                } catch (Exception ignored) {
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to write probe log: " + e.getMessage());
            }
        }
    }

    public static void logThrowable(String stage, Throwable t) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        t.printStackTrace(pw);
        pw.flush();
        log(stage, t.getClass().getName() + ": " + t.getMessage() + "\n" + sw.toString());
    }
}
