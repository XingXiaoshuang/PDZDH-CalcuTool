#!/usr/bin/env bash
# JVM 侧回归测试（不需要模拟器）：
#   1. QrEncodingTest —— 二维码中文编码自适应（UTF-8 / GB18030 / GBK / BIG5 乱码纠正）
#   2. QrImageTest    —— 相册图片解码路径（写 PNG → 读回 → 正片/反相各解一次）
#
# 用法：先 ./build.sh 生成 build/release/classes，再
#   tests/run.sh
set -e
cd "$(dirname "$0")/.."

JAVAC="${JAVAC:-javac}"
JAVA="${JAVA:-java}"
CP="libs/core-3.5.3.jar;build/release/classes"
OUT="build/tests"

if [ ! -d build/release/classes ]; then
  echo "先执行 ./build.sh 生成 build/release/classes" >&2
  exit 1
fi

mkdir -p "$OUT"
"$JAVAC" -encoding UTF-8 -cp "$CP" -d "$OUT" tests/QrEncodingTest.java tests/QrImageTest.java
"$JAVA" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT;$CP" QrEncodingTest
"$JAVA" -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -cp "$OUT;$CP" QrImageTest
