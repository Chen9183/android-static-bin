<!-- Banner -->
<p align="center">
  <img src="assets/banner.png" alt="android-static-bin" width="650"/>
</p>

<!-- 仓库名：H1，最大 -->
<h1 align="center">android-static-bin</h1>

<!-- 副标题：加粗 + 引用，视觉上小一号 -->
<p align="center">
  <b>单文件 · 零依赖 · 静态编译 · 给 Android 用</b>
</p>

<!-- Badge 行：彩色小标签，自带视觉重量 -->
<p align="center">
  <a href="https://github.com/Chen9183/android-static-bin/releases"><img src="https://img.shields.io/badge/release-latest-blue?style=for-the-badge"></a>
  <a href="https://github.com/Chen9183/android-static-bin/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-MIT-green?style=for-the-badge"></a>
  <a href="https://github.com/Chen9183/android-static-bin/issues"><img src="https://img.shields.io/badge/issues-welcome-orange?style=for-the-badge"></a>
  <img src="https://img.shields.io/badge/arch-arm64-eab308?style=for-the-badge">
  <img src="https://img.shields.io/badge/root-optional-9cf?style=for-the-badge">
</p>

---

<!-- H2：章节大标题 -->
## 这是什么？

<!-- 引用块：视觉上缩进+左边框，天然"小一号旁白" -->
> 15 个为 Android arm64 静态编译的单文件命令行工具。
> 扔进目录、`chmod +x`、直接跑。
> 不依赖系统库，不需要 proot，不产生临时文件，不 fork 进程。

<!-- H3：比 H2 小一号 -->
### 一句话总结

<!-- b 加粗 + small 缩小，行内大小字对比 -->
<b>单文件。</b> <small>每个工具都是独立的静态 ELF。</small><br>
<b>零依赖。</b> <small>不依赖任何 .so，不需要外部数据文件。</small><br>
<b>直接跑。</b> <small>扔到任何 arm64 安卓设备都能用。</small>

---

## 设计理念

<!-- 表格：行内 b 加粗制造大小对比 -->
| 原则 | 说明 |
|------|------|
| **单文件** | 每个工具都是独立的静态 ELF，不依赖任何 `.so` |
| **零外部数据** | magic 数据库、规则库等全部编译嵌入 ELF 体内 |
| **不 fork** | 降低内存开销，避免 SELinux 审计 |
| **无临时文件** | 不产生中间文件，不污染存储 |

---

## 安装

<!-- H3 子标题 -->
### 三种方式

| 方式 | 适合场景 |
|------|---------|
| **临时使用** | 试试看、偶尔用 → 扔进 `/data/local/tmp` |
| **全局安装** | 长期使用 → 扔进 `/system/bin/` |
| **Magisk 模块** | 开机自动生效 → 下载 zip 刷入后重启 |

<!-- 引用块当"旁白小字" -->
> 👉 前往 **[Releases](https://github.com/Chen9183/android-static-bin/releases)** 下载所需工具。

---

## 更新日志

> 完整记录见 **[CHANGELOG.md](CHANGELOG.md)**
>
> 最新版本及下载：**[Releases](https://github.com/Chen9183/android-static-bin/releases)**

---

## 许可证

| 项目 | 许可证 |
|------|--------|
| **仓库源码** | MIT |
| ffmpeg / ffprobe | LGPL 2.1+ |
| magick | Apache 2.0 |
| sox / tree | GPL 2.0 |
| rg / fd / bat / fzf / jq / duf | MIT / Apache 2.0 |
| iperf3 / file / lz4 / zstd / uchardet | BSD |

> 构建方式详见 `build/` 目录。

---

## 问题反馈

> 👉 **[Issues](https://github.com/Chen9183/android-static-bin/issues)**
>
> 提交时请附带：设备型号 · Android 版本 · 工具名称与版本 · 完整报错输出

---

<!-- sub 标签：最小的脚注字 -->
<sub>Last updated: 2026-07 · Built with too many tokens 🎒</sub>
