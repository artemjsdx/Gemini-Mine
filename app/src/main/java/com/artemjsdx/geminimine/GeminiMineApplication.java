package com.artemjsdx.geminimine;

import android.app.ActivityManager;
import android.app.Application;
import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import com.artemjsdx.geminimine.forensics.ProbeLogger;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.List;

public class GeminiMineApplication extends Application {
    private static final String TAG = "GeminiMineApp";
    public static final String GAME_PROCESS_NAME = "com.artemjsdx.geminimine";
    public static final String FORENSICS_PROCESS_NAME = "com.artemjsdx.geminimine:forensics";

    private String mProcessName = "";

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        mProcessName = resolveProcessName(base);

        if (GAME_PROCESS_NAME.equals(mProcessName)) {
            ProbeLogger.init(base, "game-current.log");
            ProbeLogger.log("GAME_APPLICATION_ATTACH_BASE_CONTEXT_BEGIN", "proc=" + mProcessName);
            installGameUncaughtHandler();
            ProbeLogger.log("GAME_APPLICATION_ATTACH_BASE_CONTEXT_END", null);
        } else if (FORENSICS_PROCESS_NAME.equals(mProcessName)) {
            ProbeLogger.init(base, "forensics-current.log");
            ProbeLogger.log("FORENSICS_APPLICATION_ATTACH_BASE_CONTEXT_BEGIN", "proc=" + mProcessName);
            ProbeLogger.log("FORENSICS_APPLICATION_ATTACH_BASE_CONTEXT_END", null);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (GAME_PROCESS_NAME.equals(mProcessName)) {
            ProbeLogger.log("GAME_APPLICATION_ONCREATE_BEGIN", "SDK=" + Build.VERSION.SDK_INT);
            ProbeLogger.log("GAME_APPLICATION_ONCREATE_END", null);
        } else if (FORENSICS_PROCESS_NAME.equals(mProcessName)) {
            ProbeLogger.log("FORENSICS_APPLICATION_ONCREATE_BEGIN", "SDK=" + Build.VERSION.SDK_INT);
            ProbeLogger.log("FORENSICS_APPLICATION_ONCREATE_END", null);
        }
    }

    private void installGameUncaughtHandler() {
        final Thread.UncaughtExceptionHandler defaultHandler = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
            @Override
            public void uncaughtException(Thread t, Throwable e) {
                try {
                    ProbeLogger.log("JAVA_UNCAUGHT_BEGIN", "Thread=" + t.getName() + " (id=" + t.getId() + ")");
                    ProbeLogger.logThrowable("JAVA_UNCAUGHT_EXCEPTION", e);
                    ProbeLogger.log("JAVA_UNCAUGHT_END", null);
                } catch (Throwable logError) {
                    Log.e(TAG, "Error in uncaught exception handler", logError);
                } finally {
                    if (defaultHandler != null) {
                        defaultHandler.uncaughtException(t, e);
                    }
                }
            }
        });
        ProbeLogger.log("UNCAUGHT_HANDLER_INSTALLED", "Default handler chained");
    }

    public static String resolveProcessName(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            String pName = Application.getProcessName();
            if (pName != null && !pName.isEmpty()) {
                return pName;
            }
        }
        int pid = Process.myPid();
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am != null) {
            List<ActivityManager.RunningAppProcessInfo> runningProcesses = am.getRunningAppProcesses();
            if (runningProcesses != null) {
                for (ActivityManager.RunningAppProcessInfo processInfo : runningProcesses) {
                    if (processInfo.pid == pid) {
                        return processInfo.processName;
                    }
                }
            }
        }
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/cmdline"))) {
            String line = reader.readLine();
            if (line != null) {
                return line.trim();
            }
        } catch (Exception ignored) {
        }
        return GAME_PROCESS_NAME;
    }
}
