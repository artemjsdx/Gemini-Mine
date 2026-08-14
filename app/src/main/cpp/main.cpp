#include <game-activity/native_app_glue/android_native_app_glue.h>
#include <android/log.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/time.h>
#include <cstdio>
#include <cstring>
#include <ctime>

#define LOG_TAG "GeminiMineNative"
#define LOGI(...) ((void)__android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__))
#define LOGW(...) ((void)__android_log_print(ANDROID_LOG_WARN, LOG_TAG, __VA_ARGS__))
#define LOGE(...) ((void)__android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__))

static const char* LOG_PATH = "/data/data/com.artemjsdx.geminimine/files/startup-probe/game-current.log";

static void probe_native_log(const char* stage, const char* details) {
    struct timeval tv;
    gettimeofday(&tv, nullptr);
    struct tm tm_info;
    localtime_r(&tv.tv_sec, &tm_info);

    char time_buf[64];
    strftime(time_buf, sizeof(time_buf), "%Y-%m-%d %H:%M:%S", &tm_info);

    char line_buf[512];
    int len = snprintf(line_buf, sizeof(line_buf),
                       "[%s.%03d | PID:%d | TID:%d] %s%s%s\n",
                       time_buf, (int)(tv.tv_usec / 1000),
                       getpid(), gettid(),
                       stage,
                       (details && details[0]) ? " : " : "",
                       (details && details[0]) ? details : "");

    LOGI("%s", line_buf);

    int fd = open(LOG_PATH, O_WRONLY | O_CREAT | O_APPEND, 0644);
    if (fd >= 0) {
        write(fd, line_buf, len);
        fsync(fd);
        close(fd);
    }
}

static void handle_cmd(struct android_app* app, int32_t cmd) {
    char cmd_detail[64];
    snprintf(cmd_detail, sizeof(cmd_detail), "cmd=%d", cmd);

    switch (cmd) {
        case APP_CMD_INIT_WINDOW:
            probe_native_log("APP_CMD_INIT_WINDOW", (app->window != nullptr) ? "window_non_null" : "window_null");
            break;
        case APP_CMD_TERM_WINDOW:
            probe_native_log("APP_CMD_TERM_WINDOW", nullptr);
            break;
        case APP_CMD_RESUME:
            probe_native_log("APP_CMD_RESUME", nullptr);
            break;
        case APP_CMD_PAUSE:
            probe_native_log("APP_CMD_PAUSE", nullptr);
            break;
        case APP_CMD_STOP:
            probe_native_log("APP_CMD_STOP", nullptr);
            break;
        case APP_CMD_DESTROY:
            probe_native_log("APP_CMD_DESTROY", "destroy_requested");
            break;
        default:
            probe_native_log("APP_CMD_OTHER", cmd_detail);
            break;
    }
}

void android_main(struct android_app* app) {
    probe_native_log("JNI_PROBE_PATH_SET", LOG_PATH);
    probe_native_log("ANDROID_MAIN_ENTER", "native_app_glue");

    app->onAppCmd = handle_cmd;

    probe_native_log("ANDROID_MAIN_LOOP_BEGIN", "polling_alooper");

    while (!app->destroyRequested) {
        int events = 0;
        struct android_poll_source* source = nullptr;

        int ident = ALooper_pollOnce(-1, nullptr, &events, (void**)&source);
        if (ident >= 0) {
            if (source != nullptr) {
                source->process(app, source);
            }
        }
    }

    probe_native_log("DESTROY_REQUESTED_OBSERVED", "loop_exiting");
    probe_native_log("ANDROID_MAIN_EXIT", "clean_native_return");
}
