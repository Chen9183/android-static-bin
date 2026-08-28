#!/bin/bash
# camshot 构建脚本：CamShot.java -> classes.dex -> 嵌入静态 musl ELF (aarch64)
set -e
cd "$(dirname "$0")"

TOOL_DIR="/root/android-tools"
if [ -f "$TOOL_DIR/android-35.jar" ] && [ -f "$TOOL_DIR/r8-9.4.14.jar" ]; then
    echo "using tools: $TOOL_DIR"
    JAVAC="javac -source 8 -target 8 -classpath $TOOL_DIR/android-35.jar"
    D8="java -cp $TOOL_DIR/r8-9.4.14.jar com.android.tools.r8.D8"
    D8_LIB="$TOOL_DIR/android-35.jar"
else
    JAVAC="javac -source 8 -target 8 -classpath tools/android.jar"
    D8="java -cp tools/d8.jar com.android.tools.r8.D8"
    D8_LIB="tools/android.jar"
fi

# 1. 编译 CamShot.java
rm -rf out && mkdir -p out
$JAVAC -d out CamShot.java 2>&1 | sed 's/^/javac: /' || true

# 2. d8 -> classes.dex
rm -rf out-dex && mkdir -p out-dex
$D8 --lib "$D8_LIB" --release --min-api 21 --output out-dex $(find out -name '*.class')
cp out-dex/classes.dex classes.dex
# 嵌入文件名必须为 CamShot.dex，ld 依此生成 _binary_CamShot_dex_* 符号
cp classes.dex CamShot.dex

# 3. dex 嵌入为目标文件
ld -r -b binary -o CamShot.dex.o CamShot.dex

# 4. 静态链接 camshot ELF（aarch64）；页对齐压到 4K 以减小体积
musl-gcc -static -Os -s -Wl,-z,max-page-size=4096 -Wl,-z,common-page-size=4096 \
    -o camshot camshot.c CamShot.dex.o

echo "OK -> camshot ($(stat -c%s camshot) bytes), dex=$(stat -c%s classes.dex)"
