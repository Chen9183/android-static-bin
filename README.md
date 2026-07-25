android-static-bin

单文件 · 零依赖 · 静态编译 · 给 Android 用
扔进 
"/data/local/tmp" 或 
"/system/bin"，
"chmod +x"，直接跑。

✨ 设计理念

- 单文件 — 不依赖任何 
".so"，每个工具都是独立的 ELF
- 零外部数据 — magic 数据库、规则库等全部编译嵌入 ELF 体内
- 不 fork — 降低内存开销，避免 SELinux 审计
- 无临时文件 — 不产生中间文件，不污染存储

📦 安装

👉 前往 Releases (https://github.com/Chen9183/android-static-bin/releases) 下载所需工具。

方式 操作
临时使用 下载 → 扔进 
"/data/local/tmp" → 
"chmod +x" → 直接用
全局安装 下载 → 扔进 
"/system/bin/" → 
"chmod 755"
Magisk 模块 下载 zip → Magisk Manager 刷入 → 重启生效

📝 更新日志

完整记录见 CHANGELOG.md

最新版本及下载：Releases (https://github.com/Chen9183/android-static-bin/releases)

📄 许可证

各工具保留其原有上游许可证（GPL / LGPL / Apache / MIT / BSD）

仓库源码（README、构建脚本等）采用 MIT

构建方式详见 
"build/" 目录

🐛 问题反馈

遇到问题？👉 Issues (https://github.com/Chen9183/android-static-bin/issues)

提交时请附带：设备型号 · Android 版本 · 工具名称与版本 · 完整报错
