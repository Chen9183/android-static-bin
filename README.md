android-static-bin

单文件、零依赖的 arm64 静态二进制工具集，给 Android 用。

设计理念

- 单文件，不依赖任何 
".so"
- 不携带外部数据文件（magic、规则库等全部焊进 ELF）
- 不 fork 进程，不产生临时文件

安装

去 Releases (https://github.com/Chen9183/android-static-bin/releases) 下载需要的工具。

扔进 
"/data/local/tmp" 或 
"/system/bin"，
"chmod +x" 后直接使用。

Magisk 模块可直接在 Releases 页面下载 zip 包，Magisk Manager 刷入后重启。
如若模块未发布，请自行加载
更新日志

所有版本更新记录在 CHANGELOG.md。

最新版本及下载：Releases (https://github.com/Chen9183/android-static-bin/releases)

许可证

各工具保留其原有上游许可证（GPL / LGPL / Apache / MIT / BSD）。

仓库源码采用 MIT。

构建方式见 
"build/" 目录。

问题反馈

Issues (https://github.com/Chen9183/android-static-bin/issues)

Last updated: 2026-07
