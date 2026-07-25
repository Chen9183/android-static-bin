<p align="center">

<img src="assets/banner.png" alt="android-static-bin" width="650"/>

</p>

<p align="center">

</p>

<p align="center">

<a href="https://github.com/Chen9183/android-static-bin/releases"><img src="https://img.shields.io/badge/release-latest-blue?style=for-the-badge" alt="Latest Release"></a>

<a href="https://github.com/Chen9183/android-static-bin/blob/main/LICENSE"><img src="https://img.shields.io/badge/license-MIT-green?style=for-the-badge" alt="License"></a>

<a href="https://github.com/Chen9183/android-static-bin/issues"><img src="https://img.shields.io/badge/issues-welcome-orange?style=for-the-badge" alt="Issues"></a>

<img src="https://img.shields.io/badge/arch-arm64-eab308?style=for-the-badge" alt="ARM64">

<img src="https://img.shields.io/badge/root-optional-9cf?style=for-the-badge" alt="Root Optional">

</p>

这是什么？

15 个为 Android arm64 静态编译的单文件命令行工具。扔进目录、
"chmod +x"、直接跑。不依赖系统库，不需要 proot，不产生临时文件，不 fork 进程。
设计理念

 
单文件 每个工具都是独立的静态 ELF，不依赖任何 
".so"
零外部数据 magic 数据库、规则库等全部编译嵌入 ELF 体内
不 fork 降低内存开销，避免 SELinux 审计
无临时文件 不产生中间文件，不污染存储

安装

👉 前往 Releases (https://github.com/Chen9183/android-static-bin/releases) 下载所需工具。

方式 适合场景
临时使用 试试看、偶尔用 → 扔进 
"/data/local/tmp"
全局安装 长期使用、写脚本 → 扔进 
"/system/bin/"
Magisk 模块 开机自动生效 → 下载 zip 刷入后重启

更新日志

完整记录见 CHANGELOG.md

最新版本及下载：Releases (https://github.com/Chen9183/android-static-bin/releases)

许可证

各工具保留其原有上游许可证（GPL / LGPL / Apache / MIT / BSD）。仓库源码采用 MIT。构建方式详见 
"build/" 目录。

问题反馈

👉 Issues (https://github.com/Chen9183/android-static-bin/issues)

提交时请附带：设备型号 · Android 版本 · 工具名称与版本 · 完整报错输出
