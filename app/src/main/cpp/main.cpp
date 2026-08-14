#include <game-activity/native_app_glue/android_native_app_glue.h>
#include <android/log.h>

#define LOG_TAG "GeminiMineNative"
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

static void handle_cmd(struct android_app* app, int32_t cmd) {
    switch (cmd) {
        case APP_CMD_INIT_WINDOW:
            LOGI("Native lifecycle: APP_CMD_INIT_WINDOW received");
            if (app->window != nullptr) {
                LOGI("Native window initialized successfully");
            }
            break;
        case APP_CMD_TERM_WINDOW:
            LOGI("Native lifecycle: APP_CMD_TERM_WINDOW received");
            break;
        case APP_CMD_RESUME:
            LOGI("Native lifecycle: APP_CMD_RESUME received");
            break;
        case APP_CMD_PAUSE:
            LOGI("Native lifecycle: APP_CMD_PAUSE received");
            break;
        case APP_CMD_DESTROY:
            LOGI("Native lifecycle: APP_CMD_DESTROY received, destroy requested");
            break;
        default:
            LOGI("Native lifecycle: command %d received", cmd);
            break;
    }
}

void android_main(struct android_app* app) {
    LOGI("Gemini Mine native entry reached (GM001 foundation)");

    app->onAppCmd = handle_cmd;

    // GM001: Process native lifecycle events without busy-spinning while no renderer exists
    while (!app->destroyRequested) {
        int events = 0;
        struct android_poll_source* source = nullptr;

        // Block indefinitely until an event arrives (timeout = -1) to prevent busy-spinning
        if (ALooper_pollOnce(-1, nullptr, &events, (void**)&source) >= 0) {
            if (source != nullptr) {
                source->process(app, source);
            }
        }
    }

    LOGI("Gemini Mine native loop terminated, destroyRequested is true");
}
