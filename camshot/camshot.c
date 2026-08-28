// camshot —— 自包含 Android 拍照 ELF（静态 musl，直接放 /system/bin 或任意目录以 root 运行）
// 内嵌 CamShot.java 编译出的 dex，用 app_process 走 Camera2 框架拍一张照片。
// 用法: camshot [-o <路径>]       默认输出目录 /tmp
//        -o 为目录则生成 camshot_<时间戳>.jpg；为文件路径则直接写该文件
// 原理: 把内嵌 dex 用 memfd（内存）交付给 app_process，避免磁盘替换；完成后清理。
#define _GNU_SOURCE
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <signal.h>
#include <sys/wait.h>
#include <sys/prctl.h>
#include <sys/stat.h>
#include <sys/types.h>
#include <fcntl.h>
#include <errno.h>
#include <stdint.h>
#include <sys/syscall.h>
#include <sys/ioctl.h>

extern const unsigned char _binary_CamShot_dex_start[];
extern const unsigned char _binary_CamShot_dex_end[];

static char g_dexpath_buf[1024];
static const char *g_dexpath = NULL;
static volatile pid_t g_child_pid = -1;

/* 信号清理：先终止子进程，再删临时文件 */
static void on_signal(int sig) {
    if (g_child_pid > 0)
        kill(g_child_pid, SIGTERM);
    if (g_dexpath) unlinkat(AT_FDCWD, g_dexpath, 0);
    _exit(128 + sig);
}

/* 用 execvp 直接执行，绕开 shell 转义与长度限制 */
static int run_argv(char *const argv[], int dex_fd) {
    sigset_t set, old;
    sigemptyset(&set);
    sigaddset(&set, SIGINT); sigaddset(&set, SIGTERM); sigaddset(&set, SIGHUP);
    sigprocmask(SIG_BLOCK, &set, &old);
    pid_t p = fork();
    if (p == 0) {
        prctl(PR_SET_PDEATHSIG, SIGTERM);
        sigprocmask(SIG_SETMASK, &old, NULL);
        if (dex_fd >= 0) {
            char cp[48];
            snprintf(cp, sizeof(cp), "/proc/self/fd/%d", dex_fd);
            setenv("CLASSPATH", cp, 1);
        } else if (g_dexpath) {
            setenv("CLASSPATH", g_dexpath, 1);
        }
        execv(argv[0], argv);
        dprintf(STDERR_FILENO, "camshot: execv %s failed: %s\n", argv[0], strerror(errno));
        _exit(127);
    }
    if (p < 0) { sigprocmask(SIG_SETMASK, &old, NULL); return -1; }
    g_child_pid = p;
    sigprocmask(SIG_SETMASK, &old, NULL);
    int s; while (waitpid(p, &s, 0) == -1 && errno == EINTR);
    g_child_pid = -1;
    return WIFEXITED(s) ? WEXITSTATUS(s) : -1;
}

/* 用 memfd 在内存中承载 DEX，经 /proc/self/fd/N 交给 app_process */
static int create_memfd_dex(void) {
#ifdef SYS_memfd_create
    int fd = (int)syscall(SYS_memfd_create, "CamShot.dex", 0);
    if (fd < 0) return -1;
    size_t sz = _binary_CamShot_dex_end - _binary_CamShot_dex_start;
    size_t off = 0;
    while (off < sz) {
        ssize_t w = write(fd, _binary_CamShot_dex_start + off, sz - off);
        if (w <= 0) { close(fd); return -1; }
        off += (size_t)w;
    }
    return fd;
#else
    return -1;
#endif
}

/* 定位 app_process（兼容 64 位路径） */
static const char *find_app_process(void) {
    static const char *cands[] = {
        "/system/bin/app_process",
        "/system/bin/app_process64",
        "/system/bin/app_process32"
    };
    for (size_t i = 0; i < sizeof(cands)/sizeof(cands[0]); i++)
        if (access(cands[i], X_OK) == 0) return cands[i];
    return "/system/bin/app_process";
}

/* 准备内嵌 dex：优先 memfd，回退磁盘 mkstemp */
static int prepare_dex(const char *tmpdir) {
    size_t sz = _binary_CamShot_dex_end - _binary_CamShot_dex_start;
    int dex_fd = -1;
    if (getenv("CAMSHOT_DISK_DEX") == NULL) {
        dex_fd = create_memfd_dex();
        if (dex_fd < 0)
            fprintf(stderr, "WARN: memfd unavailable (%s), falling back to disk dex\n", strerror(errno));
    }
    if (dex_fd < 0) {
        snprintf(g_dexpath_buf, sizeof(g_dexpath_buf), "%s/camshot_dex_XXXXXX", tmpdir);
        int dfd = mkstemp(g_dexpath_buf);
        if (dfd < 0) {
            fprintf(stderr, "ERROR: temp dir not writable: %s (%s)\n", tmpdir, strerror(errno));
            return -1;
        }
        g_dexpath = g_dexpath_buf;
        fchmod(dfd, S_IRUSR|S_IWUSR);
        size_t off = 0;
        while (off < sz) {
            ssize_t w = write(dfd, _binary_CamShot_dex_start + off, sz - off);
            if (w <= 0) { close(dfd); unlink(g_dexpath_buf);
                fprintf(stderr, "ERROR: write dex failed (%s)\n", strerror(errno)); return -1; }
            off += (size_t)w;
        }
        fsync(dfd); close(dfd);
    }
    return dex_fd;
}

/* SELinux 状态：1=强制(enforcing), 0=宽松(permissive), -1=不可用/未启用 */
static int selinux_state(void) {
    FILE *f = fopen("/sys/fs/selinux/enforce", "r");
    if (!f) return -1;
    int c = fgetc(f);
    fclose(f);
    if (c == '1') return 1;
    if (c == '0') return 0;
    return -1;
}

/* 切换为宽松模式；成功返回 0，失败返回 -1 */
static int selinux_set_permissive(void) {
    FILE *f = fopen("/sys/fs/selinux/enforce", "w");
    if (!f) return -1;
    int r = fputc('0', f);
    fclose(f);
    return (r == EOF) ? -1 : 0;
}

static void show_help(void) {
    /* 用 fputs 而不是 fprintf：帮助文本里有 "-%"，fprintf 会当格式符导致输出失败 */
    fputs(
        "camshot — 以 root 调用手机摄像头拍一张照片并保存 (Android)\n"
        "\n"
        "用法:\n"
        "  camshot [选项]\n"
        "\n"
        "选项:\n"
        "  -o <路径>    输出路径。为目录则保存为 <目录>/camshot_<时间戳>.jpg；\n"
        "               为文件路径则直接写该文件；为 '-' 则把 JPEG 写到标准输出(管道)。\n"
        "               默认: /tmp\n"
        "  -tmp <路径>  指定临时目录（存放内嵌 dex 的临时文件，仅磁盘回退模式用到）。\n"
        "               默认: /data/local/tmp\n"
        "  -c <id>      指定摄像头 ID（如 0=后置, 1=前置，因设备而异）。\n"
        "               默认: 自动选择后置摄像头\n"
        "  -w <宽度>    指定输出宽度（像素），需与 -h 搭配；如 1920\n"
        "  -h <高度>    指定输出高度（像素），需与 -w 搭配；如 1080\n"
        "               (分辨率会自动匹配相机最接近的可用 JPEG 尺寸)\n"
        "  -r <宽>x<高> 指定分辨率，如 1920x1080（等价于 -w 1920 -h 1080）\n"
        "  -e <毫秒>    手动曝光时间（快门），如 10=1/100s, 20=1/50s, 100=1/10s。\n"
        "               设置后会关闭自动曝光(AE)。默认: 自动曝光\n"
        "  -iso <值>    手动感光度 ISO（如 100/200/400/800）。可与 -e 搭配提亮。\n"
        "               默认: 自动\n"
        "  -ev <值>     曝光补偿 EV（自动曝光基础上提亮/压暗，可为负或小数，如\n"
        "               1、-2、0.5）。会按相机补偿步进自动换算。不能与 -e/-iso 同用\n"
        "  -rot <度>    顺时针旋转：0/90/180/270（部分摄像头画面方向不对时用）\n"
        "  -flip <模式> 镜像翻转：h=水平(左右), v=垂直(上下), hv=双向\n"
        "  -invert      颜色反转（负片效果）\n"
        "  -noaudio     不录制音频 / 播放时不播放音频（仅视频模式）\n"
        "  -a, --auto   失败时自动处理 SELinux：记录当前状态；若为宽松(permissive)\n"
        "               则直接报错（此时确实无法使用）；若为强制(enforcing)则自动切为\n"
        "               宽松并重试一次；仍失败再报错\n"
        "  -l, --list, --list-cameras\n"
        "               列出所有摄像头 ID、朝向及支持的全部 JPEG 分辨率后退出\n"
        "  -show <图片> 在终端用彩色字符显示一张图片（后面只允许跟一个文件，\n"
        "               或 '-' 从标准输入读取）。按终端宽度自适应，24 位真彩+半块字符\n"
        "  -h, --help   显示本帮助\n"
        "\n"
        "示例:\n"
        "  camshot                                  # 默认后置摄像头、最大分辨率、存到 /tmp\n"
        "  camshot -o /sdcard/DCIM/                 # 指定输出目录\n"
        "  camshot -c 1 -o /sdcard/front.jpg        # 前置摄像头\n"
        "  camshot -w 1920 -h 1080                  # 1080p 分辨率\n"
        "  camshot -c 0 -r 4000x3000 -o /sdcard/a.jpg\n"
        "  camshot -e 20 -iso 400 -o /sdcard/b.jpg  # 1/50s + ISO400 提亮\n"
        "  camshot -ev 1 -o /sdcard/b.jpg           # 自动曝光 +1EV 提亮\n"
        "  camshot --list                           # 列出摄像头及分辨率\n"
        "  camshot --auto -o /sdcard/a.jpg          # 失败时自动切 SELinux 宽松重试\n"
        "  camshot -show /tmp/photo.jpg             # 终端彩色显示图片\n"
        "  camshot -o - | cat > /tmp/a.jpg          # 拍照输出到管道\n"
        "  cat /tmp/a.jpg | camshot -show -         # 从管道读图显示\n"
        "  camshot -rot 180 -flip h -o /tmp/a.jpg   # 旋转180°+水平翻转\n"
        "\n"
        "说明:\n"
        "  - 需 root 运行；架构 arm64-v8a (aarch64)，Android 5.0+ (API 21)。\n"
        "  - 内嵌 dex 用 memfd 内存交付，不留磁盘残留。\n"
        "\n"
        "视频模式（用软链接名 camvid / vidshow 等调用，或 ln -s camshot camvid）:\n"
        "  camvid -time <秒> [-o 输出] [参数]            # 录真视频(H.264视频+WAV音频)\n"
        "  camvid -show <视频|-> [-% 百分比] [-noaudio]  # 终端彩色播放视频+音频\n"
        "  camvid -show v.cv -% 100 -noaudio            # 100%大小、不播音频\n"
        "  例: camvid -time 5 -c 0 -r 640x480 -o /tmp/v.cv\n"
        "      camvid -show /tmp/v.cv -% 120\n"
        "  说明: 自定义容器 CAMV2 = H.264 视频 + WAV/PCM 音频；MediaCodec 编解码，\n"
        "        播放按帧时间戳节奏(时长与录像一致)，自包含不依赖 miniplay。\n"
        "  -% <百分比>: 播放画面宽度占默认(44列)的百分比，如 1=1%、100=44列、200=88列。\n"
        "\n"
        "兼容性（本工具仅在部分机型真机验证，非所有手机都支持）:\n"
        "  - 拍照/列表/分辨率：大多数 Android 5.0+ 设备可用；新版 Android 的\n"
        "    CameraManager 读设置问题已内置绕过，旧版本无此问题自动忽略。\n"
        "  - -e 手动曝光 / -iso 感光度：需该摄像头支持手动模式(Manual Sensor)。\n"
        "    多数后置摄像头支持，部分前置/低端摄像头不支持。\n"
        "  - -ev 曝光补偿：需支持 AE 补偿(多数相机都支持)，与 -e/-iso 互斥。\n"
        "  - 若 -e/-iso 无效或报错：去掉它们改用自动曝光即可，不影响拍照。\n"
        "  - 若被 SELinux 阻挡：加 --auto 自动切宽松重试（退出前自动恢复）。\n"
        "  - 若架构是 32 位(armeabi-v7a)：本 ELF 无法运行，需另编 32 位版本。\n"
        "\n"
        "Author: deepseek v4 flash & @Chen9183 (github)\n", stdout);
}

static int is_uint(const char *s) {
    if (!s || !*s) return 0;
    for (; *s; s++) if (*s < '0' || *s > '9') return 0;
    return 1;
}

/* 允许负数/小数的数字，用于 -ev（如 1、-2、0.5、-1.5） */
static int is_signed_num(const char *s) {
    if (!s || !*s) return 0;
    if (*s == '-' || *s == '+') s++;
    if (!*s) return 0;
    int dot = 0;
    for (; *s; s++) {
        if (*s == '.') { if (++dot > 1) return 0; continue; }
        if (*s < '0' || *s > '9') return 0;
    }
    return 1;
}

/* 终端宽度（列）：取 stdout 的 winsize，失败默认 80 */
static int terminal_cols(void) {
    struct winsize ws;
    if (ioctl(STDOUT_FILENO, TIOCGWINSZ, &ws) == 0 && ws.ws_col > 0)
        return ws.ws_col;
    return 80;
}

int main(int argc, char **argv) {
    const char *outfile = "/tmp";
    const char *tmpdir = "/data/local/tmp"; /* -tmp：内嵌 dex 的临时目录（磁盘回退模式） */
    const char *camid = "";    /* 空 = 自动（优先后置） */
    long width = 0, height = 0;
    long exposure_ms = 0;      /* -e：手动曝光毫秒，0=自动 */
    long iso = 0;              /* -iso：手动感光度，0=自动 */
    const char *ev_str = NULL; /* -ev：曝光补偿（EV 档，可为负/小数），NULL=不设置 */
    long rot_deg = 0;          /* -rot：顺时针旋转 0/90/180/270 */
    const char *flip_mode = NULL; /* -flip：h/v/hv 镜像翻转 */
    int invert = 0;            /* -invert：颜色反转（负片） */
    int noaudio = 0;           /* -noaudio：不录制/不播放音频 */
    int size_pct = 100;        /* -% <百分比>：视频播放显示大小百分比 */
    const char *time_str = NULL; /* -time：录像时长（秒，可小数），仅视频模式 */

    int auto_selinux = 0;      /* --auto */
    int list_mode = 0;         /* --list */
    int show_mode = 0;         /* -show：终端彩色显示图片/视频 */
    const char *show_file = NULL;
    int video_mode = 0;        /* 以视频名(camvid 等)调用 → 视频模式 */

    /* 软链接名检测：名字含 "vid"/"video" 即进入视频模式（如 camvid、vidshow） */
    int author_mode = 0;       /* 名字含 "author" 即进入作者模式：忽略所有参数直接输出作者 */
    {
        const char *base = strrchr(argv[0], '/');
        base = base ? base + 1 : argv[0];
        if (strstr(base, "vid") || strstr(base, "VID") || strstr(base, "video") ||
            strstr(base, "Video"))
            video_mode = 1;
        if (strstr(base, "author") || strstr(base, "Author") || strstr(base, "AUTHOR"))
            author_mode = 1;
    }

    /* 作者模式：以 author 名调用时，不支持任何参数，直接输出作者并退出 */
    if (author_mode) {
        fputs("camshot — Android root 摄像头拍照/录像工具\n"
              "Author: deepseek v4 flash & @Chen9183 (github)\n", stdout);
        return 0;
    }

    for (int i = 1; i < argc; i++) {
        char *a = argv[i];
        if (!strcmp(a, "-o")) {
            if (i + 1 < argc) { outfile = argv[++i]; }
            else { fprintf(stderr, "ERROR: -o requires a path\n"); return 2; }
        } else if (!strcmp(a, "-tmp")) {
            if (i + 1 < argc) { tmpdir = argv[++i]; }
            else { fprintf(stderr, "ERROR: -tmp requires a temp directory path\n"); return 2; }
        } else if (!strcmp(a, "-c")) {
            if (i + 1 < argc) { camid = argv[++i]; }
            else { fprintf(stderr, "ERROR: -c requires a camera id\n"); return 2; }
        } else if (!strcmp(a, "-w")) {
            if (i + 1 < argc && is_uint(argv[i+1])) { width = strtol(argv[++i], NULL, 10); }
            else { fprintf(stderr, "ERROR: -w requires a positive integer width (pixels)\n"); return 2; }
        } else if (!strcmp(a, "-h")) {
            if (i + 1 < argc && is_uint(argv[i+1])) { height = strtol(argv[++i], NULL, 10); }
            else { fprintf(stderr, "ERROR: -h requires a positive integer height (pixels)\n"); return 2; }
        } else if (!strcmp(a, "-r")) {
            if (i + 1 < argc) {
                /* 解析 宽x高 */
                const char *r = argv[++i];
                const char *x = strchr(r, 'x');
                if (!x) x = strchr(r, 'X');
                if (!x) { fprintf(stderr, "ERROR: -r requires WxH, e.g. 1920x1080\n"); return 2; }
                char wb[64], hb[64];
                size_t wl = (size_t)(x - r);
                if (wl == 0 || wl >= sizeof(wb)) { fprintf(stderr, "ERROR: bad -r value: %s\n", r); return 2; }
                memcpy(wb, r, wl); wb[wl] = '\0';
                strncpy(hb, x + 1, sizeof(hb) - 1); hb[sizeof(hb)-1] = '\0';
                if (!is_uint(wb) || !is_uint(hb)) { fprintf(stderr, "ERROR: -r requires WxH, e.g. 1920x1080\n"); return 2; }
                width = strtol(wb, NULL, 10);
                height = strtol(hb, NULL, 10);
            } else { fprintf(stderr, "ERROR: -r requires WxH, e.g. 1920x1080\n"); return 2; }
        } else if (!strcmp(a, "-e") || !strcmp(a, "--exposure")) {
            if (i + 1 < argc && is_uint(argv[i+1])) { exposure_ms = strtol(argv[++i], NULL, 10); }
            else { fprintf(stderr, "ERROR: -e requires a positive integer exposure in milliseconds\n"); return 2; }
        } else if (!strcmp(a, "-iso")) {
            if (i + 1 < argc && is_uint(argv[i+1])) { iso = strtol(argv[++i], NULL, 10); }
            else { fprintf(stderr, "ERROR: -iso requires a positive integer ISO value\n"); return 2; }
        } else if (!strcmp(a, "-ev") || !strcmp(a, "--ev")) {
            if (i + 1 < argc && is_signed_num(argv[i+1])) { ev_str = argv[++i]; }
            else { fprintf(stderr, "ERROR: -ev requires a number in EV stops (can be negative/小数, e.g. 1, -2, 0.5)\n"); return 2; }
        } else if (!strcmp(a, "-rot") || !strcmp(a, "--rot")) {
            if (i + 1 < argc && is_uint(argv[i+1])) { rot_deg = strtol(argv[++i], NULL, 10); }
            else { fprintf(stderr, "ERROR: -rot requires 0/90/180/270 (clockwise degrees)\n"); return 2; }
            if (rot_deg % 90 != 0 || rot_deg > 270) {
                fprintf(stderr, "ERROR: -rot must be 0/90/180/270 (clockwise)\n"); return 2;
            }
        } else if (!strcmp(a, "-flip")) {
            if (i + 1 < argc && (!strcmp(argv[i+1],"h") || !strcmp(argv[i+1],"v") || !strcmp(argv[i+1],"hv"))) {
                flip_mode = argv[++i];
            } else {
                fprintf(stderr, "ERROR: -flip requires h (水平) / v (垂直) / hv (双向)\n"); return 2;
            }
        } else if (!strcmp(a, "-invert")) {
            invert = 1;
        } else if (!strcmp(a, "-noaudio") || !strcmp(a, "--noaudio")) {
            noaudio = 1;
        } else if (!strcmp(a, "-%") || !strcmp(a, "--percent")) {
            if (i + 1 < argc && is_uint(argv[i+1])) {
                size_pct = (int) strtol(argv[++i], NULL, 10);
                if (size_pct < 1) size_pct = 1;
            } else { fprintf(stderr, "ERROR: -%% 需要一个整数百分比（如 1、50、100）\n"); return 2; }
        } else if (!strcmp(a, "-time") || !strcmp(a, "--time")) {
            if (i + 1 < argc && is_signed_num(argv[i+1])) { time_str = argv[++i]; }
            else { fprintf(stderr, "ERROR: -time requires a duration in seconds (can be小数, e.g. 5, 2.5)\n"); return 2; }
        } else if (!strcmp(a, "-a") || !strcmp(a, "--auto")) {
            auto_selinux = 1;
        } else if (!strcmp(a, "-l") || !strcmp(a, "--list") || !strcmp(a, "--list-cameras")) {
            list_mode = 1;
        } else if (!strcmp(a, "-show") || !strcmp(a, "--show")) {
            /* -show 后面只允许跟一个图片文件 */
            if (show_mode) { fprintf(stderr, "ERROR: -show 只能指定一次\n"); return 2; }
            show_mode = 1;
            /* 允许 "-" 表示从 stdin 读取图片 */
            if (i + 1 < argc && (argv[i+1][0] != '-' || !strcmp(argv[i+1], "-"))) { show_file = argv[++i]; }
            else { fprintf(stderr, "ERROR: -show 后面必须跟一个图片文件路径（或 - 表示 stdin）\n"); return 2; }
        } else if (!strcmp(a, "--help") || !strcmp(a, "-help")) {
            show_help();
            return 0;
        } else if (a[0] == '-' && a[1] != '\0') {
            fprintf(stderr, "ERROR: unknown option: %s\n", a);
            return 2;
        } else {
            fprintf(stderr, "ERROR: unexpected argument: %s\n", a);
            return 2;
        }
    }
    /* 校验：-w 与 -h 必须成对 */
    if ((width > 0) != (height > 0)) {
        fprintf(stderr, "ERROR: -w and -h must be used together (or use -r WxH)\n");
        return 2;
    }
    /* -show 模式：只允许一个文件，不能与其它拍照参数混用 */
    if (show_mode) {
        if (width > 0 || height > 0 || exposure_ms > 0 || iso > 0 || ev_str != NULL ||
            auto_selinux || list_mode || strcmp(outfile, "/tmp") != 0 || camid[0]) {
            fprintf(stderr, "ERROR: -show 只能跟一个图片文件，不能与其它参数混用\n");
            return 2;
        }
        if (show_file == NULL) {
            fprintf(stderr, "ERROR: -show 后面必须跟一个图片文件路径\n");
            return 2;
        }
    }

    int dex_fd = prepare_dex(tmpdir);
    if (dex_fd < 0) return 3;

    signal(SIGINT,  on_signal);
    signal(SIGTERM, on_signal);
    signal(SIGHUP,  on_signal);

    const char *app = find_app_process();

    /* 列出模式：只列摄像头与分辨率后退出 */
    if (list_mode) {
        char *list_argv[] = { (char*)app, "/system/bin", "CamShot", "--list", NULL };
        int rc = run_argv(list_argv, dex_fd);
        if (dex_fd >= 0) close(dex_fd);
        if (g_dexpath) unlink(g_dexpath_buf);
        return rc;
    }

    /* 视频模式：camvid（软链接名触发）——录像 / 视频播放 */
    if (video_mode) {
        char wstr[16], hstr[16], estr[16], istr[16], evbuf[32];
        char rotstr[16], invstr[8], colstr[16];
        snprintf(wstr, sizeof(wstr), "%ld", width);
        snprintf(hstr, sizeof(hstr), "%ld", height);
        snprintf(estr, sizeof(estr), "%ld", exposure_ms);
        snprintf(istr, sizeof(istr), "%ld", iso);
        snprintf(evbuf, sizeof(evbuf), "%s", ev_str ? ev_str : "");
        snprintf(rotstr, sizeof(rotstr), "%ld", rot_deg);
        snprintf(invstr, sizeof(invstr), "%d", invert);
        /* 播放大小用 -% 百分比（默认 100）；录像无此参数 */
        snprintf(colstr, sizeof(colstr), "%d", size_pct);
        char *child_argv[16];
        int n = 0;
        char nastr[8];
        snprintf(nastr, sizeof(nastr), "%d", noaudio);
        if (show_mode) {
            /* 播放：camvid -show <文件|-> */
            child_argv[n++] = (char*)app; child_argv[n++] = (char*)"/system/bin";
            child_argv[n++] = (char*)"CamShot"; child_argv[n++] = (char*)"--play";
            child_argv[n++] = (char*)show_file; child_argv[n++] = colstr;
            child_argv[n++] = rotstr; child_argv[n++] = (char*)(flip_mode ? flip_mode : "");
            child_argv[n++] = invstr; child_argv[n++] = nastr;
        } else {
            /* 录像：camvid -time <秒> [-o <out>] ... */
            if (!time_str) { fprintf(stderr, "ERROR: 视频模式需要 -time <秒> 指定录制时长\n"); return 2; }
            child_argv[n++] = (char*)app; child_argv[n++] = (char*)"/system/bin";
            child_argv[n++] = (char*)"CamShot"; child_argv[n++] = (char*)"--record";
            child_argv[n++] = (char*)outfile; child_argv[n++] = (char*)camid;
            child_argv[n++] = wstr; child_argv[n++] = hstr;
            child_argv[n++] = (char*)time_str; child_argv[n++] = estr;
            child_argv[n++] = istr; child_argv[n++] = evbuf;
            child_argv[n++] = rotstr; child_argv[n++] = (char*)(flip_mode ? flip_mode : "");
            child_argv[n++] = invstr; child_argv[n++] = nastr;
        }
        child_argv[n] = NULL;
        int rc = run_argv(child_argv, dex_fd);
        if (dex_fd >= 0) close(dex_fd);
        if (g_dexpath) unlink(g_dexpath_buf);
        return rc;
    }

    /* 显示模式：camshot -show <图片>，终端彩色渲染（支持旋转/翻转/反转） */
    if (show_mode) {
        char colsstr[16], rotstr[16], invstr[8];
        snprintf(colsstr, sizeof(colsstr), "%d", terminal_cols());
        snprintf(rotstr, sizeof(rotstr), "%ld", rot_deg);
        snprintf(invstr, sizeof(invstr), "%d", invert);
        char *show_argv[] = {
            (char*)app, "/system/bin", "CamShot", "--show",
            (char*)show_file, colsstr, rotstr,
            (char*)(flip_mode ? flip_mode : ""), invstr, NULL
        };
        int rc = run_argv(show_argv, dex_fd);
        if (dex_fd >= 0) close(dex_fd);
        if (g_dexpath) unlink(g_dexpath_buf);
        return rc;
    }

    char wstr[16], hstr[16], estr[16], istr[16], evbuf[32];
    char rotstr[16], invstr[8];
    snprintf(wstr, sizeof(wstr), "%ld", width);
    snprintf(hstr, sizeof(hstr), "%ld", height);
    snprintf(estr, sizeof(estr), "%ld", exposure_ms);
    snprintf(istr, sizeof(istr), "%ld", iso);
    snprintf(evbuf, sizeof(evbuf), "%s", ev_str ? ev_str : "");
    snprintf(rotstr, sizeof(rotstr), "%ld", rot_deg);
    snprintf(invstr, sizeof(invstr), "%d", invert);
    char *child_argv[] = {
        (char*)app, "/system/bin", "CamShot",
        (char*)outfile, (char*)camid, wstr, hstr, estr, istr, evbuf,
        rotstr, (char*)(flip_mode ? flip_mode : ""), invstr, NULL
    };
    fprintf(stderr, "capturing -> %s (camera=%s, %ldx%ld%s%s%s%s)\n",
            outfile, camid[0] ? camid : "auto", width, height,
            exposure_ms > 0 ? ", exp_ms=" : "",
            exposure_ms > 0 ? estr : "",
            ev_str ? ", ev=" : "",
            ev_str ? ev_str : "");
    int rc = run_argv(child_argv, dex_fd);

    /* --auto：失败时按 SELinux 状态处理，退出前恢复原状 */
    int selinux_changed = 0;
    int orig_state = -1;
    if (auto_selinux && rc != 0) {
        int st = selinux_state();
        if (st == 0) {
            fprintf(stderr, "ERROR: SELinux 已是宽松(permissive)模式，拍照仍失败，确实无法使用\n");
        } else if (st == 1) {
            fprintf(stderr, "SELinux 为强制(enforcing)模式，自动切换为宽松(permissive)并重试...\n");
            orig_state = st;
            if (selinux_set_permissive() == 0) {
                selinux_changed = 1;
                fprintf(stderr, "已切换为 permissive，重试...\n");
                rc = run_argv(child_argv, dex_fd);
                if (rc != 0)
                    fprintf(stderr, "ERROR: 切换为宽松后仍失败 (exit=%d)\n", rc);
            } else {
                fprintf(stderr, "ERROR: 无法写入 /sys/fs/selinux/enforce 切换为宽松模式\n");
            }
        }
        /* st == -1：SELinux 不可用，无需处理 */
    }

    /* 退出前恢复 SELinux 原始状态（若本次曾切换为宽松） */
    if (selinux_changed) {
        FILE *f = fopen("/sys/fs/selinux/enforce", "w");
        if (f) {
            fputc(orig_state == 1 ? '1' : '0', f);
            fclose(f);
            fprintf(stderr, "SELinux 已恢复为 %s\n", orig_state == 1 ? "enforcing" : "permissive");
        } else {
            fprintf(stderr, "WARN: 无法恢复 SELinux（请手动 setenforce %d）\n", orig_state == 1 ? 1 : 0);
        }
    }

    if (dex_fd >= 0) close(dex_fd);
    if (g_dexpath) unlink(g_dexpath_buf);
    return rc;
}
