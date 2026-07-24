# android-static-bin
Single-file static binaries for Android arm64. No containers, no temp files, just works.
# android-static-bin

安卓 arm64 静态编译二进制工具集。丢到手机里，`chmod +x`，直接跑。

## 设计理念

- **无容器**：不依赖 proot、不依赖 chroot
- **无临时文件**：不产生中间文件，不污染存储
- **不 fork 进程**：降低内存开销，避免 SELinux 审计
- **单文件**：每个工具都是一个独立的静态二进制，不依赖任何系统 `.so`
- **零依赖**：扔到任何 arm64 安卓手机上都能直接运行

## 包含工具

| 工具 | 用途 |
|------|------|
| ffmpeg / ffprobe | 音视频处理（LGPL，原生 AAC 编码器） |
| magick | 图像处理（ImageMagick） |
| sox / soxi | 音频处理与信息探测 |
| rg | 极速内容搜索 |
| fd | 快速文件查找 |
| bat | 带语法高亮的文件查看器 |
| fzf | 交互式模糊查找 |
| jq | JSON 处理工具 |
| duf | 磁盘使用情况查看 |
| iperf3 | 网络带宽测试 |
| lz4 / zstd | 极速压缩 / 高压缩比 |
| file | 文件类型识别（内嵌 magic 数据库） |
| tree | 目录树结构展示 |

## 使用要求

- 安卓 arm64（aarch64）设备
- 建议 root（可放入 `/system/bin` 全局调用），无 root 也可直接放 `/data/local/tmp` 使用

## 使用方法
工具使用说明

**bat** — 带语法高亮的文件查看器（`cat` 增强版）
`bat 文件名`    高亮显示; `bat -A 文件名` 显示所有字符; `bat --list-themes` 列出主题

**duf** — 磁盘使用情况查看器
`duf`            列出所有挂载点使用情况; `duf /sdcard` 查看特定目录

**fd** — 快速文件搜索（`find` 替代）
`fd 关键字`     快速搜索文件; `fd -e txt 关键字` 按扩展名搜索; `fd -H 关键字` 搜索隐藏文件

**ffmpeg** — 音视频处理全能工具
`ffmpeg -i 输入.mp4 输出.mp3`       格式转换; `ffmpeg -i 输入.mp4 -s 1280x720 输出.mp4` 调整分辨率; `ffmpeg -i 输入.mp4 -ss 00:01:00 -t 30 -c copy 片段.mp4` 截取片段

**ffprobe** — 音视频文件信息查看器
`ffprobe 视频.mp4`        查看媒体文件详细信息; `ffprobe -v error -show_entries format=duration -of default=noprint_wrappers=1:nokey=1 视频.mp4` 仅获取时长

**file** — 文件类型识别
`file 文件名`    识别文件真实类型; `file -b 文件名` 仅输出类型描述

**fzf** — 模糊搜索选择器（交互式）
`命令 | fzf`     从管道输出中交互式选择; `fzf --preview 'cat {}'` 带预览的选择; `cd $(find * -type d | fzf)` 快速跳转目录

**iperf3** — 网络性能测试
`iperf3 -c 服务器IP`        客户端测速; `iperf3 -s` 启动服务器模式; `iperf3 -c 服务器IP -u` UDP 模式测速

**jq** — JSON 命令行处理器
`cat 文件.json | jq '.'`                  格式化 JSON; `jq '.key' 文件.json` 提取字段; `jq '.[] | select(.name=="xxx")' 文件.json` 条件过滤

**lz4** — 高速压缩/解压
`lz4 源文件`                        压缩（生成 .lz4）; `lz4 -d 文件.lz4` 解压; `lz4 源文件 输出.lz4` 指定输出名

**rg** (ripgrep) — 超快文本搜索（`grep` 替代）
`rg 关键字`                         搜索文件内容; `rg -i 关键字` 忽略大小写; `rg -l 关键字` 仅输出文件名; `rg -C 3 关键字` 显示上下文各3行

**sox** — 音频处理瑞士军刀
`sox 输入.wav 输出.wav trim 0 10`         截取前10秒; `sox 输入.mp3 -r 44100 输出.wav` 重采样; `sox -m 音频1.wav 音频2.wav 混合.wav` 混合音频

**soxi** — 音频文件信息查看
`soxi 音频.wav` 查看音频文件格式、采样率、时长等信息

**tree** — 目录树展示
`tree`              展示目录树; `tree -L 2` 限制深度2层; `tree -h` 显示文件大小

**zstd** — Zstandard 压缩/解压（高压缩比）
`zstd 文件`                             压缩（生成 .zst）; `zstd -d 文件.zst` 解压; `zstd -b 文件` 基准测试压缩比


## 许可证说明

各工具保留原有许可证：

- ffmpeg：LGPL 2.1+（构建参数见 `build/` 目录）
- 其余工具：MIT / Apache-2.0 / BSD

详见 `LICENSES/` 目录。

---

*三天、约 1.3 亿 tokens 训练出来的安卓工具链。容器没用上，但认知到位了。*
