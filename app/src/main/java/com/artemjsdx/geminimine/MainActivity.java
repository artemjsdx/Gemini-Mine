package com.artemjsdx.geminimine;

import android.os.Bundle;

import com.artemjsdx.geminimine.forensics.ProbeLogger;
import com.google.androidgamesdk.GameActivity;

import java.io.File;

public class MainActivity extends GameActivity {

    private static native void nativeSetStartupProbePath(String path);

    static {
        ProbeLogger.log("MAIN_ACTIVITY_CLASS_INIT_BEGIN", null);
        try {
            ProbeLogger.log("MAIN_ACTIVITY_NATIVE_LIBRARY_LOAD_BEGIN", "lib=geminimine");
            System.loadLibrary("geminimine");
            ProbeLogger.log("MAIN_ACTIVITY_NATIVE_LIBRARY_LOAD_END", "success");
        } catch (Throwable t) {
            ProbeLogger.logThrowable("MAIN_ACTIVITY_NATIVE_LIBRARY_LOAD_FAILED", t);
            throw t;
        }
        ProbeLogger.log("MAIN_ACTIVITY_CLASS_INIT_END", null);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        try {
            File probeFile = ProbeLogger.getLogFile(this, "game-current.log");
            nativeSetStartupProbePath(probeFile.getAbsolutePath());
        } catch (Throwable t) {
            ProbeLogger.logThrowable("NATIVE_SET_STARTUP_PROBE_PATH_FAILED", t);
        }

        ProbeLogger.log("MAIN_ACTIVITY_ONCREATE_BEFORE_SUPER", savedInstanceState != null ? "restoring" : "fresh");
        super.onCreate(savedInstanceState);
        ProbeLogger.log("MAIN_ACTIVITY_ONCREATE_AFTER_SUPER", null);
    }

    @Override
    protected void onStart() {
        ProbeLogger.log("MAIN_ACTIVITY_ONSTART_BEFORE_SUPER", null);
        super.onStart();
        ProbeLogger.log("MAIN_ACTIVITY_ONSTART_AFTER_SUPER", null);
    }

    @Override
    protected void onResume() {
        ProbeLogger.log("MAIN_ACTIVITY_ONRESUME_BEFORE_SUPER", null);
        super.onResume();
        ProbeLogger.log("MAIN_ACTIVITY_ONRESUME_AFTER_SUPER", null);
    }

    @Override
    protected void onPause() {
        ProbeLogger.log("MAIN_ACTIVITY_ONPAUSE_BEFORE_SUPER", null);
        super.onPause();
        ProbeLogger.log("MAIN_ACTIVITY_ONPAUSE_AFTER_SUPER", null);
    }

    @Override
    protected void onStop() {
        ProbeLogger.log("MAIN_ACTIVITY_ONSTOP_BEFORE_SUPER", null);
        super.onStop();
        ProbeLogger.log("MAIN_ACTIVITY_ONSTOP_AFTER_SUPER", null);
    }

    @Override
    protected void onDestroy() {
        ProbeLogger.log("MAIN_ACTIVITY_ONDESTROY_BEFORE_SUPER", "isFinishing=" + isFinishing());
        super.onDestroy();
        ProbeLogger.log("MAIN_ACTIVITY_ONDESTROY_AFTER_SUPER", null);
    }
}
