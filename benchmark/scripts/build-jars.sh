#!/bin/zsh
set -e
LIBS=/Users/gildor/hobby/ktoml-agent/bench/libs
mkdir -p "$LIBS"

echo "=== [1/2] Building TOML-1.1 (a0ca8f4) jvmJar in main checkout ==="
cd /Users/gildor/hobby/ktoml-agent/ktoml
./gradlew :ktoml-core:jvmJar --console=plain --no-daemon
OURS=$(ls -t ktoml-core/build/libs/ktoml-core-jvm-*.jar | grep -v sources | grep -v javadoc | head -1)
cp "$OURS" "$LIBS/ktoml-core-toml11.jar"
echo "toml11 jar: $OURS -> $LIBS/ktoml-core-toml11.jar"

echo "=== [2/2] Building UPSTREAM (ec84e4b) jvmJar via local clone ==="
rm -rf /Users/gildor/hobby/ktoml-agent/clone-upstream
git clone --quiet /Users/gildor/hobby/ktoml-agent/ktoml /Users/gildor/hobby/ktoml-agent/clone-upstream
cd /Users/gildor/hobby/ktoml-agent/clone-upstream
git checkout --quiet ec84e4bec5743581354d5379a7751293fad8baee
./gradlew :ktoml-core:jvmJar --console=plain --no-daemon
UP=$(ls -t ktoml-core/build/libs/ktoml-core-jvm-*.jar | grep -v sources | grep -v javadoc | head -1)
cp "$UP" "$LIBS/ktoml-core-upstream.jar"
echo "upstream jar: $UP -> $LIBS/ktoml-core-upstream.jar"

echo "=== DONE ==="
ls -la "$LIBS"
