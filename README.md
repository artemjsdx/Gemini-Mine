# Gemini Mine

Gemini Mine is an Android-first native voxel-survival game/engine.

## Current State: GM001 Foundation
- GameActivity + C++20 native entry (`android_main`)
- Vulkan-first is the future engine direction, but Vulkan is **NOT** implemented in GM001
- Production gameplay orientation: Landscape (`sensorLandscape`)
- Primary ABI: `arm64-v8a`
- No gameplay or rendering exists yet

## Build Command
```bash
./gradlew :app:assembleDebug
```

## Output Debug APK
```
app/build/outputs/apk/debug/app-debug.apk
```
