package com.artemjsdx.geminimine;

import com.google.androidgamesdk.GameActivity;

public class MainActivity extends GameActivity {
    static {
        System.loadLibrary("geminimine");
    }
}
