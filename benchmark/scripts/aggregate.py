#!/usr/bin/env python3
"""Aggregate results.jsonl into a comparison table: upstream (ec84e4b) vs toml-1.1.

Reports two statistics per case:
  - median-of-forks p50  (central estimate)
  - min-of-forks    p50  (best/least-contended fork ~ contention-free lower bound)
The min-of-forks ratio is the most robust to background load.
"""
import json
import statistics
import sys
from collections import defaultdict

path = sys.argv[1] if len(sys.argv) > 1 else "/Users/gildor/hobby/ktoml-agent/bench/results.jsonl"
ROWS = [json.loads(l) for l in open(path) if l.strip()]

p50 = defaultdict(lambda: defaultdict(list))
bytes_of = {}
for r in ROWS:
    p50[r["case"]][r["label"]].append(r["p50_us"])
    bytes_of[r["case"]] = r["bytes"]

def sort_key(case):
    grp = 0 if case.startswith("complex/") else (2 if case.startswith("small-suite") else 1)
    return (grp, case)

cases = sorted(p50.keys(), key=sort_key)
forks = max(len(v["upstream"]) for v in p50.values())

print(f"# ktoml parser benchmark — upstream (ec84e4b) vs toml-1.1 (a0ca8f4)   forks={forks}\n")
print(f"{'case':<32} {'bytes':>6} | {'--- median-of-forks p50 (µs) ---':^34} | {'--- min-of-forks p50 (µs) ---':^31}")
print(f"{'':<32} {'':>6} | {'upstream':>10} {'toml-1.1':>10} {'Δ':>8} | {'upstream':>10} {'toml-1.1':>9} {'Δ':>8}")
print("-"*116)

def fmt(c):
    up_med = statistics.median(p50[c]["upstream"]); t_med = statistics.median(p50[c]["toml11"])
    up_min = min(p50[c]["upstream"]); t_min = min(p50[c]["toml11"])
    d_med = (t_med/up_med-1)*100
    d_min = (t_min/up_min-1)*100
    print(f"{c:<32} {bytes_of[c]:>6} | {up_med:>10.2f} {t_med:>10.2f} {d_med:>+7.1f}% | {up_min:>10.2f} {t_min:>9.2f} {d_min:>+7.1f}%")
    return d_med, d_min

complex_d, small_d, suite_d = [], [], []
for c in cases:
    dmed, dmin = fmt(c)
    if c.startswith("complex/"): complex_d.append((c, dmed, dmin))
    elif c.startswith("small-suite"): suite_d.append((c, dmed, dmin))
    else: small_d.append((c, dmed, dmin))

print("\n--- summary (min-of-forks Δ, the contention-robust metric) ---")
for c, dmed, dmin in complex_d:
    print(f"  {c:<30} {dmin:+.1f}%")
sm = [dmin for _,_,dmin in small_d]
print(f"  small files (n={len(sm)})         mean {statistics.mean(sm):+.1f}%  range [{min(sm):+.1f}%, {max(sm):+.1f}%]")
print(f"  {suite_d[0][0]:<30} {suite_d[0][2]:+.1f}%")

# per-fork detail for complex cases (to judge stability)
print("\n--- per-fork p50 (µs) for complex cases (stability check) ---")
for c in [x for x in cases if x.startswith("complex/")]:
    up = " ".join(f"{v:7.1f}" for v in p50[c]["upstream"])
    t  = " ".join(f"{v:7.1f}" for v in p50[c]["toml11"])
    print(f"  {c}")
    print(f"      upstream: {up}")
    print(f"      toml-1.1: {t}")
