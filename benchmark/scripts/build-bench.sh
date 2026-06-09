#!/bin/zsh
set -e
BENCH=/Users/gildor/hobby/ktoml-agent/bench
LIBS=$BENCH/libs
GRADLEW=/Users/gildor/hobby/ktoml-agent/ktoml/gradlew

echo "=== resolve runtime deps -> build/deps ==="
"$GRADLEW" -p "$BENCH" --console=plain copyDeps -PktomlJar="$LIBS/ktoml-core-upstream.jar"

echo "=== compile bench against UPSTREAM jar ==="
"$GRADLEW" -p "$BENCH" --console=plain clean jar -PktomlJar="$LIBS/ktoml-core-upstream.jar"
cp "$BENCH/build/libs/ktoml-bench.jar" "$LIBS/bench-upstream.jar"

echo "=== compile bench against TOML-1.1 jar ==="
"$GRADLEW" -p "$BENCH" --console=plain clean jar -PktomlJar="$LIBS/ktoml-core-toml11.jar"
cp "$BENCH/build/libs/ktoml-bench.jar" "$LIBS/bench-toml11.jar"

echo "=== DONE ==="
ls -la "$LIBS"/bench-*.jar
echo "deps:"; ls "$BENCH/build/deps"
