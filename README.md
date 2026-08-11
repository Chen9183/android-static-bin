<!-- Banner -->
<p align="center">
  <img src="assets/banner.png" alt="android-static-bin" width="650"/>
</p>

<!-- 仓库名：H1，最大 -->
<h1 align="center">android-static-bin</h1>

<!-- 副标题：加粗 + 引用，视觉上小一号 -->
<p align="center">
  <b>单文件 · 零依赖 · 静态融合 · 扔进去就能跑</b>
</p>

<!-- Badge 行：你原来的，一个不动 -->
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

<!-- 引用块：懒得数版 -->
> 把一堆 arm64 静态工具焊进一个 ELF 里了。
> 扔进目录、`chmod +x`、直接跑。
> 不依赖系统库，不需要 proot，不 fork 进程，Android 非 root 也能喘气。

### 一句话总结

<b>单文件。</b> <small>一个 inode 装下三套命令箱，塞太满懒得数。</small><br>
<b>零依赖。</b> <small>magic / AI / 动画 / 游戏全嵌体内，自己 `list` 看存货。</small><br>
<b>到处跑。</b> <small>Recovery / adb shell / Shizuku / 普通 Linux 终端一把梭。</small>

---

## 设计理念

| 原则 | 说明 |
|------|------|
| **单 ELF 架构** | 拒绝散装二进制，一个文件就是半个迷你发行版 |
| **三层执行兜底** | memfd / O_TMPFILE / 普通路径，Android 拦不住 |
| **自清场** | 跑完不甩临时垃圾，tmpfs 干干净净 |
| **盒子不分家** | 轻量 / 原生 / POSIX 三套箱随调随用 |
| **有电就跑** | 修盘、摸鱼、跑模型、打方块，一个文件全管 |

---

## 安装

### 三种方式

| 方式 | 适合场景 |
|------|---------|
| **临时使用** | 试试看、偶尔用 → 扔进 `/data/local/tmp` |
| **全局安装** | 长期使用 → 扔进 `$PATH` 或 `/system/bin` |
| **Magisk 模块** | 开机自动生效 → 下载 zip 刷入后重启 |

> 👉 前往 **[Releases](https://github.com/Chen9183/android-static-bin/releases)** 下载。

---

## 更新日志

> 完整记录见 **[CHANGELOG.md](CHANGELOG.md)**
>(虽然完整记录大概率也没有)
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
BUILD
> Build with very many tokens

-----


## 关于以后

以后这个仓库的mytools/mt就已经停留在v5.0.0了
但我还会继续发布一些好玩的安卓静态编译的elf的
