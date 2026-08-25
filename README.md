<div align="center">

# android-static-bin

**安卓 / aarch64 静态编译 ELF 工具集**  
单文件 · 零依赖 · 扔进去就能跑

[![release](https://img.shields.io/badge/release-latest-blue?style=for-the-badge)](https://github.com/Chen9183/android-static-bin/releases)
[![license](https://img.shields.io/badge/license-MIT-green?style=for-the-badge)](https://github.com/Chen9183/android-static-bin/blob/main/LICENSE)
[![arch](https://img.shields.io/badge/arch-arm64-eab308?style=for-the-badge)]()
[![root](https://img.shields.io/badge/root-optional-9cf?style=for-the-badge)]()

</div>

---

## 📦 这是什么

一个给 **Android arm64 / Linux aarch64** 用的**静态编译单文件 ELF 集合**。

从功能完整的重型多功能工具，到只有 **168 字节** 的手工构造极限最小可执行文件，都收在这里。每一个都是独立的单文件 ELF，不依赖运行时、不污染系统，拿来即用。

## ✨ 特点

- **全静态链接**：不依赖系统 libc / linker，`ldd` 之下没有任何动态依赖
- **单文件分发**：每个工具就是一个文件，拷贝 / 推送即完成部署
- **跨环境可跑**：root / adb / Shizuku / 普通 shell / 裸终端都能运行
- **零系统污染**：不写系统目录、不改环境，用完即删，干净利落

## 🚀 用法

```sh
chmod +x 文件名
./文件名 --help        # 或 -h / -V，自己试
```

### 几条通用提示

1. **先看架构**：本仓库 ELF 均为 aarch64，用 `uname -m` / `file 文件名` 确认你的设备架构
2. **提权方式**：`adb shell`（root 设备）、`su -c`、Shizuku、终端模拟器均可
3. **外部配置**：需要 CA 证书、API Key、terminfo 等外部配置的工具，去查**对应上游文档**，本仓库不教、不问

## 📦 内容怎么找

仓库体量越来越大，具体工具清单不在此逐一罗列（避免过时）。按以下方式查找：

- **Releases**：所有二进制 + 源码按版本打包，每个 tag 对应一次发布，带标题和说明
- **各目录**：部分工具源码直接放在仓库对应子目录（如 `lns/`、`build/`）
- **文件本身**：`file` / `--version` / `--help` 自己看

## 🏷️ 版本节奏

- 新工具、大改动 → 递增主/次版本号，单独发 Release
- 每个 Release 附带**对应版本的 ELF 二进制**和**源码**
- 老版本在 Releases 里随时可回溯

## ⚖️ 许可证

**本仓库脚本 & 文档**：MIT

**Release 内所有第三方二进制**：各自遵守上游许可证，包括但不限于：

MIT · Apache-2.0 · BSD(2/3-Clause) · ISC · GPL v2/v3 · LGPL · AGPL · MPL-2.0 · EPL · Unlicense / Public Domain · CC 系列 · 各上游自定义开源协议

> ⚠️ 不逐一列「工具名 ↔ 协议」映射，想知道具体协议请查上游源码仓库。本仓库仅做静态打包，不对上游合规性负责。

## 🛠️ 状态说明

- 能用是缘分，不能用是命
- 不保证全设备适配，不随 Android 版本迭代
- 跑不起来别问，自己 `file` / `uname` / `getenforce`
