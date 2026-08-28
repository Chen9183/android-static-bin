# camshot

在 Android 上以 **root** 权限调用手机摄像头拍一张照片并保存的**原生 ELF**。
单文件、静态链接（musl，aarch64），内嵌用 Camera2 抓拍的 DEX，通过 `app_process`
走 Android 相机框架，不依赖任何应用/APK。

## 用法

```sh
adb push camshot /data/local/tmp/
adb shell
su
chmod +x /data/local/tmp/camshot
/data/local/tmp/camshot                        # 默认后置摄像头、最大分辨率、存到 /tmp
/data/local/tmp/camshot -o /sdcard/DCIM/       # 保存到指定目录（自动生成文件名）
/data/local/tmp/camshot -o /sdcard/photo.jpg   # 保存到指定文件
/data/local/tmp/camshot -c 1 -o /sdcard/front.jpg  # 指定前置摄像头
/data/local/tmp/camshot -w 1920 -h 1080        # 指定 1080p 分辨率
/data/local/tmp/camshot -c 0 -r 4000x3000 -o /sdcard/a.jpg
/data/local/tmp/camshot --list                 # 列出所有摄像头及分辨率
/data/local/tmp/camshot --auto -o /sdcard/a.jpg  # 失败时自动处理 SELinux
/data/local/tmp/camshot --help                 # 详细帮助
```

### 参数

| 参数 | 说明 | 默认 |
|------|------|------|
| `-o <路径>` | 输出路径：为**目录**则保存为 `<目录>/camshot_<时间戳>.jpg`；为**文件路径**则直接写该文件；为 `-` 则把 JPEG 写到标准输出（管道） | `/tmp` |
| `-c <id>` | 指定摄像头 ID（如 0=后置、1=前置，因设备而异） | 自动选后置 |
| `-w <宽>` | 指定输出宽度（像素），须与 `-h` 搭配 | 自动（最大） |
| `-h <高>` | 指定输出高度（像素），须与 `-w` 搭配 | 自动（最大） |
| `-r <宽>x<高>` | 指定分辨率，如 `1920x1080`（等价于 `-w 1920 -h 1080`） | — |
| `-e <毫秒>` | 手动曝光（快门），如 `10`=1/100s、`20`=1/50s、`100`=1/10s。设置后关闭自动曝光 | 自动曝光 |
| `-iso <值>` | 手动感光度，如 `100/200/400/800`，可与 `-e` 搭配提亮 | 自动 |
| `-ev <值>` | 曝光补偿 EV（自动曝光基础上提亮/压暗），可为负或小数，如 `1`、`-2`、`0.5`。按相机补偿步进自动换算。与 `-e`/`-iso` 互斥 | 无 |
| `-rot <度>` | 顺时针旋转：`0/90/180/270`（部分摄像头画面方向不对时用） | 0 |
| `-flip <模式>` | 镜像翻转：`h`=水平(左右)、`v`=垂直(上下)、`hv`=双向 | 无 |
| `-invert` | 颜色反转（负片效果） | 关 |
| `-a`, `--auto` | 失败时自动处理 SELinux（见下） | 关 |
| `-l`, `--list`, `--list-cameras` | 列出所有摄像头 ID、朝向及全部 JPEG 分辨率后退出 | — |
| `-show <图片>` | 在终端用彩色字符显示一张图片（后面只允许跟一个文件，或 `-` 从标准输入读取） | — |
| `--help` | 显示详细帮助 | — |

> 分辨率会自动匹配相机**最接近**的可用 JPEG 尺寸（优先完全一致）。
> 曝光/ISO 会自动钳制到传感器支持范围（`SENSOR_INFO_EXPOSURE_TIME_RANGE` / `SENSOR_INFO_SENSITIVITY_RANGE`）；设置任一即关闭 AE。

### 旋转/翻转/反转（摄像头画面方向不对时）

```sh
camshot -rot 180 -o /sdcard/a.jpg            # 顺时针转 180°
camshot -flip h -o /sdcard/a.jpg             # 水平镜像（左右翻转）
camshot -flip v -o /sdcard/a.jpg             # 垂直翻转（上下）
camshot -invert -o /sdcard/a.jpg             # 颜色反转（负片）
camshot -rot 90 -flip h -invert -o /sdcard/a.jpg  # 可叠加
camshot -show /tmp/a.jpg -rot 90 -invert     # -show 同样支持这些参数
```

> 拍照和 `-show` 都支持 `-rot/-flip/-invert`，可叠加组合。拍照时会重新编码 JPEG（会去掉原 Exif）。

### 提亮示例（画面太暗时）

```sh
camshot -ev 1 -o /sdcard/bright.jpg    # 最简单：自动曝光 +1EV 提亮
camshot -ev 2 -o /sdcard/bright2.jpg   # +2EV 更亮
camshot -ev -1 -o /sdcard/dark.jpg     # -1EV 压暗
camshot -e 20 -iso 400 -o /sdcard/b.jpg   # 或手动 1/50s + ISO400
camshot -e 100 -o /sdcard/long.jpg        # 1/10s 长曝光（需稳定，防手抖）
```

> `-ev` 走自动曝光最省心（不会糊、噪点少）；`-e`/`-iso` 是手动模式。两者互斥。

### `--auto` 自动处理 SELinux

当抓拍失败且指定了 `--auto` 时，按当前 SELinux 状态处理：

1. **读取当前状态**（`/sys/fs/selinux/enforce`）：
   - **宽松 (permissive)** → 直接报错（说明并非 SELinux 阻挡，确实无法使用），不切换。
   - **强制 (enforcing)** → 记录原始状态，自动切换为宽松 (permissive)，**重试一次**。
   - SELinux 未启用 → 不做处理。
2. 重试仍失败 → 再次报错。
3. **无论成功与否，退出前自动恢复为 enforcing（原始状态）**。

> 例：`camshot --auto -o /sdcard/a.jpg`

## 说明

- **最低 Android 5.0 (API 21)+**，架构 aarch64（arm64-v8a，当前主流）。
- 需 root 运行；若设备 SELinux 为 enforcing 且拒绝相机访问，可先 `setenforce 0`。
- 抓拍走 **Camera2**（`android.hardware.camera2`），自动选择后置摄像头、最大 JPEG 尺寸。
- 内嵌 DEX 优先用 `memfd`（内存文件）交付，避免磁盘替换；个别旧内核不支持时可设
  `CAMSHOT_DISK_DEX=1` 强制磁盘模式。

### 关键技术点（在 Android 14+/16 上能跑通的原因）

root 裸进程没有注册为 app，而 Android 新版 `CameraManager` 在打开相机时会读
`Settings.Global`（旋转覆盖相关），这一步在无 app 身份时抛 `SecurityException`，
SELinux 宽松也救不了它。本工具通过反射把 `android.window.DesktopModeFlags`
的缓存字段 `sCachedToggleOverride` 预置为 `OVERRIDE_OFF`，让
`getToggleOverride()` 直接命中缓存、跳过 settings 读取，从而正常打开相机。
该逻辑封装在 `CamShot.patchDesktopModeFlags()`，对无此字段的旧 ROM 自动忽略。

### 已真机验证（Android 16 / OnePlus Ace 2）

- `camshot`：后置相机 4096x3072 拍照成功（带 Exif / GPS）。
- `camshot -c 1 -r 1920x1080`：前置相机 1080p 成功。
- `camshot -w 1280 -h 720 -o <目录>`：目录自动生成 `camshot_<时间戳>.jpg`。
- `camshot --list`：列出 5 个摄像头及全部 JPEG 分辨率。
- `camshot -e 20 -iso 400`：手动曝光 20ms + ISO400 成功。
- `camshot -e 30` / `-iso 800` / `-e 5000`（5s 长曝光）均成功。
- `camshot -ev 2`（→+12 步）、`-ev -1`（→-6 步）、`-ev 0.5`（→+3 步）均成功。
- `camshot -rot 90`：640x480 → 480x640（宽高交换）成功。
- `camshot -invert -flip h`：颜色反转 + 水平镜像成功（首像素红→青互补色）。
- `camshot -show ... -rot 90 -invert -flip h`：显示端变换同样生效。
- `camshot --auto`：成功时不动 SELinux；permissive 失败→报错；enforcing 失败→
  切 permissive 重试→仍失败→恢复 enforcing。

## 视频模式（`camvid` 软链接）

> 用软链接名 **`camvid`**（或名字含 `vid`/`video`）调用即进入视频模式。
> ```sh
> ln -s camshot camvid
> ```

**录像（真视频，自带 WAV 音频）**：
```sh
camvid -time 5 -c 0 -r 640x480 -o /tmp/v.cv   # 录 5 秒真视频
camvid -time 2 -o - > /tmp/v.cv               # 输出到 stdout
camvid -time 5 -noaudio -o /tmp/v.cv          # 不录音频
```

**播放（终端彩色 + 音频）**：
```sh
camvid -show /tmp/v.cv                          # 播放文件（100% 大小）
camvid -show /tmp/v.cv -% 120                   # 120% 大小
camvid -show /tmp/v.cv -% 50 -noaudio           # 50% 大小、不播音频
cat /tmp/v.cv | camvid -show -                  # 从管道播放
camvid -time 1 -o - | camvid -show -            # 拍即播管道链
```
> 播放为**固定窗口**：每帧回到左上角清屏原地重绘（`\033[H\033[J`），隐藏光标，不会滚动。
> 播放按**帧时间戳**节奏，时长与录像一致（不会 10 秒变 1 秒）。
> `-% <百分比>`：播放画面宽度占默认(44列)的百分比，如 `1`=1%、`100`=44列、`200`=88列。

### 视频格式说明（真视频 + WAV 音频，自包含不依赖外部工具）

- **自定义容器 CAMV2**：H.264 视频 + **WAV/PCM 音频**（16bit 44.1kHz 单声道）。
- 录制：Camera2 → H.264 硬件编码器（surface）；AudioRecord → PCM 直接写入，不转码，**音频一定能播**。
- 播放：MediaCodec 解码 H.264 帧 → 终端真彩；PCM → AudioTrack 播放。
- 参数与拍照同一套（`-c/-r/-e/-iso/-ev/--auto`），额外有 **`-time <秒>`**（录制时长）、
  **`-% <百分比>`**（播放大小）、**`-noaudio`**（不录/不播音频）。
- 旋转/翻转/反转 `-rot/-flip/-invert` 在**播放时**作用于画面。

### 已真机验证（OnePlus Ace 2）

- `camvid -time 3`：录出 CAMV2（79 个 H.264 块 + 32 个音频块，音频采样值>0 有真实声音）。
- `camvid -show v.cv`：渲染 78 帧，播放耗时 ≈3.75s（与 3s 录像一致），音频正常播放。
- 管道：`-o -` 输出、`-show -` 输入、`-o - | -show -` 拍即播均正常。
- `-% 50`、`-noaudio`（无 AUD0 块）均正常。

## 终端彩色显示图片（`-show`）

```sh
camshot -show /tmp/photo.jpg     # 显示一个文件
cat /tmp/photo.jpg | camshot -show -   # 或从管道读图显示
camshot -o - | camshot -show -         # 拍照直接管道显示（拍一张看一张）
camshot -o - > /tmp/a.jpg              # 拍照输出到 stdout 存文件
```

- 自动按**终端宽度**自适应（从 `TIOCGWINSZ` 读取列数，默认 80）。
- 用 **24 位真彩 ANSI + 上半块字符 `▀`** 渲染，每行字符显示 2 行像素（纵向分辨率翻倍）。
- 支持 JPEG 等 Android `BitmapFactory` 能解码的格式。
- 已在真机验证：160x120 彩图渲染出 80x30 个彩色块、数百种颜色。

## 支持与兼容（重要）

> 本工具目前**只在部分机型真机验证过**（如 OnePlus Ace 2 / Android 16），
> **并非所有手机都支持**。请按下面判断你的设备：

| 功能 | 支持条件 | 不支持怎么办 |
|------|---------|-------------|
| 拍照 / `--list` / 分辨率 | 架构 arm64-v8a，Android 5.0+，需 root | 32 位老手机需另编 arm 32 位版本 |
| `-e` 手动曝光 / `-iso` | 该摄像头支持 **Manual Sensor**（多数后置支持，部分前置/低端不支持） | 去掉 `-e`/`-iso` 用自动曝光即可，不影响拍照 |
| `-ev` 曝光补偿 | 支持 **AE 补偿**（多数相机都支持） | 去掉 `-ev` 用自动曝光 |
| 新版 Android 打开相机 | 已内置 `DesktopModeFlags` 绕过；旧版本无此问题自动忽略 | 若仍打不开，可能是 ROM 差异，可尝试 `--auto` 或换机型 |
| SELinux 阻挡 | 需 root 且 `/sys/fs/selinux/enforce` 可写 | 加 `--auto` 自动切宽松重试（退出前自动恢复） |

**判断你的摄像头是否支持手动曝光/ISO**：跑一次

```sh
camshot --list   # 列表只能看分辨率
```

手动参数是否生效看输出里的 `exposure:` / `iso:` 提示；若无效或报错，说明该摄像头不支持手动模式，请去掉 `-e`/`-iso`。

## 构建

依赖（本机已具备）：`javac`/`java`（JDK 17）、`d8/r8`（`/root/android-tools`）、
`musl-gcc`（aarch64）。

```sh
cd camshot
bash build_camshot.sh
# 产物: camshot (静态 aarch64 ELF), CamShot.dex
```

构建流程与同思路的 `miniplay` 一致：
`CamShot.java -> classes.dex -> ld -r -b binary 嵌入 -> musl-gcc 静态链接 -> camshot`。
