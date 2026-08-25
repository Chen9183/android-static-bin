# android-static-bin

> **安卓 / aarch64 静态编译 ELF 工具集** · 单文件 · 零依赖 · drop & run

给 **Android arm64 / Linux aarch64** 准备的静态编译单文件 ELF 集合。

- 基本全静态链接，不依赖系统 libc / linker
- root / adb / Shizuku / 裸终端都能跑
- 扔进目录 `chmod +x`，大多直接运行

## 这是什么

这里收录了我在 Android arm64 上编译、手工构造的各种静态 ELF 工具——从完整的多功能工具，到只有 168 字节的极限最小可执行文件，都在这个仓库里。

## 用法

```sh
chmod +x 文件名
./文件名 --help      # 或 -h / -V，自己试
```

需要 CA 证书、API Key、terminfo 等外部配置的，去查对应工具的上游文档。

## 版本

最新发布见右侧 **Releases**。二进制和源码随每个版本打包上传，按 tag 区分。

## 许可证

**本仓库脚本 & 文档**：MIT

**Release 内所有第三方二进制**：各自遵守上游许可证（MIT / Apache-2.0 / BSD / GPL / LGPL / Unlicense / Public Domain 等），具体协议看对应上游源码仓库，本仓库仅做静态打包。

## 状态说明

- 能用是缘分，不能用是命
- 不保证全设备适配，不随 Android 版本迭代
- 跑不起来别问，自己 `file` / `uname` / `getenforce`
