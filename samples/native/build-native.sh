#!/bin/bash
# Build native image for beangle-data sample
set -e

GRAALVM_HOME="${GRAALVM_HOME:-${JAVA_HOME:-/home/chaostone/local/graalvm-jdk-21}}"
NATIVE_IMAGE="$GRAALVM_HOME/bin/native-image"
PROJECT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"
SAMPLE_DIR="$PROJECT_DIR/samples/native"
PATCHED_DIR="$PROJECT_DIR/target/out/jvm/u/beangle-data-sample-native/patched-jar"

# Step 1: Patch beangle-hibernate-core JAR
HIBERNATE_JAR=$(find "$HOME/.m2/repository/org/beangle/hibernate/beangle-hibernate-core/7.4.5.Final" -name "beangle-hibernate-core-7.4.5.Final.jar" | head -1)
PATCHED_JAR="$PATCHED_DIR/beangle-hibernate-core-7.4.5.Final.jar"

if [ ! -f "$PATCHED_JAR" ]; then
    echo "Patching beangle-hibernate-core JAR to replace BytecodeProvider SPI..."
    mkdir -p "$PATCHED_DIR"
    cd "$PATCHED_DIR"
    jar xf "$HIBERNATE_JAR"
    rm -f META-INF/services/org.hibernate.bytecode.spi.BytecodeProvider
    mkdir -p META-INF/services
    echo "org.hibernate.bytecode.internal.none.BytecodeProviderImpl" > META-INF/services/org.hibernate.bytecode.spi.BytecodeProvider
    jar cf "$PATCHED_JAR" .
    cd "$PROJECT_DIR"
    echo "Patched JAR: $PATCHED_JAR"
fi

# Step 2: Build classpath using sbt
CP_FILE="$PROJECT_DIR/target/out/jvm/u/beangle-data-sample-native/native-image-cp.txt"
sbt -batch "show sampleNative/Compile/fullClasspath" 2>&1 | grep "Attributed(" | sed 's/.*Attributed(//;s/)//;s/\* //;s/^ *//' | sed 's/>.*//' | \
    sed "s|\${OUT}|$PROJECT_DIR/target/out|g" | \
    sed "s|\${CSR_CACHE}|$HOME/.cache/coursier|g" | \
    sed "s|\${IVY_HOME}|$HOME/.ivy2|g" | \
    sed "s|/target/out/target/out/|/target/out/|g" | \
    sed "s|/target/out//|/target/out/|g" | \
    sed 's/>sha256-[a-f0-9]*\/[0-9]*//g' | \
    sed "s|beangle-hibernate-core-7.4.5.Final.jar\$|${PATCHED_JAR}|" | \
    grep -v "^$" > "$CP_FILE"

echo "Classpath entries: $(wc -l < "$CP_FILE")"
echo "First 3 entries:"
head -3 "$CP_FILE"

# Step 3: Run native-image
echo "Building native image..."
"$NATIVE_IMAGE" \
    --no-fallback \
    --enable-url-protocols=jar,resource \
    -H:+AddAllCharsets \
    -H:ReflectionConfigurationFiles="$SAMPLE_DIR/src/main/resources/native-image/reflect-config.json" \
    -H:ResourceConfigurationFiles="$SAMPLE_DIR/src/main/resources/native-image/resource-config.json" \
    --initialize-at-run-time=org.h2.Driver,org.ehcache,com.github.benmanes.caffeine,java.awt,sun.awt,javax.management,com.sun.jmx,org.beangle,org.hibernate,org.jboss,org.beangle.data.samples.nativeapp \
    -Dhibernate.bytecode.use_reflection_optimizer=true \
    -Dnet.bytebuddy.reproducible=true \
    -H:+ReportExceptionStackTraces \
    --report-unsupported-elements-at-runtime \
    -cp "$(cat "$CP_FILE" | tr '\n' ':')" \
    -o "$PROJECT_DIR/target/native/sample-native" \
    org.beangle.data.samples.nativeapp.NativeApp

echo "Native image built: $PROJECT_DIR/target/native/sample-native"
