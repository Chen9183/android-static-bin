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

# android-static-bin

> aarch64 / Android · static ELF · drop & run

---

### 📦 这是什么
给 **Android arm64 / Linux aarch64** 用的静态编译单文件 ELF 集合。

- 基本全静态链接，不依赖系统 libc / linker
- root / adb / Shizuku / 裸终端都能喘气
- 扔进目录 `chmod +x`，大多直接跑

里面有什么、什么版本，自己 `ls` / `--version` 看，不维护动态清单。

---

### 🚀 用法

**sh**

*chmod +x 文件名*

***./文件名 --help        # 或 -h / -V，自己试***


需要 CA 证书、API Key、terminfo 等外部配置的，**去查对应工具的上游文档**。  
不教，不问。

---

### ⚖️ 许可证（一次性说死）

**本仓库脚本 & 文档**：MIT  

**Release 内所有第三方二进制**：各自遵守上游许可证，**包含但不限于**：

MIT License
Apache License 2.0
BSD License (2-Clause / 3-Clause)
ISC License
GNU GPL v2 / v3
GNU LGPL v2.1 / v3
GNU AGPL v3
Mozilla Public License (MPL) 2.0
Eclipse Public License (EPL)
Unlicense / Public Domain
Creative Commons (CC) 系列
各上游项目的自定义或专有开源许可证
其他 OSI 批准的开源许可证



> ⚠️ 不逐一列“工具名 ↔ 协议”映射。  
> 想知道具体协议去看上游源码仓库。  
> 本仓库仅做静态打包，不对上游合规性负责。

---

### 🛠️ 状态说明
- 能用是缘分，不能用是命
- 不保证全设备适配，不随 Android 版本迭代
- 跑不起来别问，自己 `file` / `uname` / `getenforce`

---


## 关于以后

以后这个仓库的mytools/mt就已经停留在v5.0.0了
但我还会继续发布一些好玩的安卓静态编译的elf的
