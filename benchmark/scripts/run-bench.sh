#!/bin/zsh
set -e
cd /Users/gildor/hobby/ktoml-agent/bench
DEPS="build/deps/*"
JVM_FLAGS="-Xms2g -Xmx2g -XX:+UseParallelGC -XX:+AlwaysPreTouch"
export BENCH_WARMUP=500
export BENCH_MEASURE=1500
FORKS=8
OUT=/Users/gildor/hobby/ktoml-agent/bench/results.jsonl
: > "$OUT"

echo "java: $(java -version 2>&1 | head -1)"
echo "flags: $JVM_FLAGS  warmup=$BENCH_WARMUP measure=$BENCH_MEASURE forks=$FORKS"
echo ""

for fork in $(seq 1 $FORKS); do
  for v in upstream toml11; do
    echo "[fork $fork/$FORKS] $v ..."
    BENCH_LABEL=$v BENCH_FORK=$fork java $=JVM_FLAGS \
      -cp "libs/bench-$v.jar:libs/ktoml-core-$v.jar:$DEPS" BenchKt corpus \
      2>/dev/null | grep '^RESULT:' | sed 's/^RESULT://' >> "$OUT"
  done
done

echo ""
echo "wrote $(wc -l < "$OUT") result rows -> $OUT"
