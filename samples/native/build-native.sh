#!/bin/bash
# Build native image for beangle-data sample.
# Usage: ./build-native.sh [mainClass] [outputName]
#   default mainClass: org.beangle.data.samples.nativeapp.NativeApp
#   default output:    target/native/sample-native
set -e

GRAALVM_HOME="${GRAALVM_HOME:-${JAVA_HOME:-/home/chaostone/local/graalvm-jdk-21}}"
NATIVE_IMAGE="$GRAALVM_HOME/bin/native-image"
PROJECT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SAMPLE_DIR="$PROJECT_DIR/samples/native"
cd "$PROJECT_DIR"
MAIN_CLASS="${1:-org.beangle.data.samples.nativeapp.NativeApp}"
OUTPUT="${2:-$PROJECT_DIR/target/native/sample-native}"

# Step 1: 先打包刷新子项目 jar，再取 classpath。
# sbt 2 的 fullClasspath 对子项目返回 CAS 内容寻址 jar（target/out/.../*.jar 是指向
# ~/.cache/sbt/v2/cas/sha256-*/ 的符号链接），compile 只更新 classes/resource_managed，
# 不会重建 jar；若不先 packageBin，native 构建会用到旧 classes（见 docs/sbt2-cas-jar.md）。
sbt -batch "model/Compile/packageBin; hibernate/Compile/packageBin; sampleNative/Compile/packageBin" || exit 1
CP_FILE="$PROJECT_DIR/target/out/jvm/u/beangle-data-sample-native/native-image-cp.txt"
sbt -batch "show sampleNative/Compile/fullClasspath" 2>&1 | grep "Attributed(" | sed 's/.*Attributed(//;s/)//;s/\* //;s/^ *//' | sed 's/>.*//' | \
    sed "s|\${OUT}|$PROJECT_DIR/target/out|g" | \
    sed "s|\${CSR_CACHE}|$HOME/.cache/coursier/v1|g" | \
    sed "s|\${IVY_HOME}|$HOME/.ivy2|g" | \
    sed "s|/target/out/target/out/|/target/out/|g" | \
    sed "s|/target/out//|/target/out/|g" | \
    sed 's/>sha256-[a-f0-9]*\/[0-9]*//g' | \
    grep -v "^$" > "$CP_FILE"

echo "Classpath entries: $(wc -l < "$CP_FILE")"
echo "Main class: $MAIN_CLASS"

mkdir -p "$(dirname "$OUTPUT")"

# Step 2: Run native-image.
# 库侧配置（reflect/resource/initialize）由 beangle-data-hibernate.jar 内嵌的
# META-INF/native-image 自动发现；以下显式文件只补 sample 自身的手写项。
"$NATIVE_IMAGE" \
    --no-fallback \
    --enable-url-protocols=jar,resource \
    -H:+AddAllCharsets \
    -H:ResourceConfigurationFiles="$SAMPLE_DIR/src/main/resources/native-image/resource-config.json" \
    -H:+ReportExceptionStackTraces \
    --report-unsupported-elements-at-runtime \
    -cp "$(cat "$CP_FILE" | tr '\n' ':')" \
    -o "$OUTPUT" \
    "$MAIN_CLASS"

echo "Native image built: $OUTPUT"
