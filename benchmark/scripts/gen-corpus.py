#!/usr/bin/env python3
"""Generate strictly-TOML-1.0 complex corpus files that both upstream ktoml (ec84e4b)
and toml-1.1 parse identically. Heavily exercises the hot paths changed on toml-1.1:
quoted/dotted keys (splitKeyToTokens), basic strings (validateQuotes/validateSymbols),
floats with underscores (TomlDouble), and offset datetimes (parseToDateTime/padOffsetSeconds).
"""
import os

OUT = "/Users/gildor/hobby/ktoml-agent/bench/corpus/complex"
os.makedirs(OUT, exist_ok=True)


def large_mixed(n_tables=60):
    lines = ["# large-mixed.toml — broad TOML 1.0 feature mix", ""]
    for i in range(n_tables):
        b = i % 2 == 0
        lines += [
            f"[server_{i}]",
            f"id = {i}",
            f'hostname = "host-{i}.example.com"',
            f'"display name" = "Server #{i} \\u2014 prod node"',
            f"'literal_path' = 'C:\\srv\\node{i}\\bin'",
            f"enabled = {'true' if b else 'false'}",
            f"ratio = {1000+i}.{i:03d}991228",
            "mass = 6.62607015e-34",
            f"big = 0xDEAD_{i:04X}",
            "perms = 0o755",
            "flags = 0b1010_1101",
            f"created = 1979-05-{(i%27)+1:02d}T07:32:00-08:00",
            f"updated = 2021-11-15T13:37:{(i%59)+1:02d}.250Z",
            f"local_dt = 1987-07-05T17:45:{(i%59)+1:02d}",
            f"local_d = 19{70+(i%30)}-07-05",
            "local_t = 07:32:00.5",
            f"ports = [ {8000+i}, {8001+i}, {8002+i} ]",
            "matrix = [ [1, 2], [3, 4], [5, 6] ]",
            'tags = [ "alpha", "beta", "gamma", "delta" ]',
            f'endpoint = {{ host = "10.0.{i//255}.{i%255}", port = 8080, tls = true }}',
            'physical.color = "gray"',
            'physical.shape = "round"',
            "config.limits.max = 1_000_000",
            "config.limits.min = -1_024",
            'description = "multi\\nline\\tvalue with a quote \\" and unicode \\u00e9\\u00f1\\u2603"',
            "",
        ]
    return "\n".join(lines) + "\n"


def datetime_heavy(n_tables=70):
    lines = ["# datetime-heavy.toml — stresses parseToDateTime/padOffsetSeconds and TomlDouble", ""]
    for i in range(n_tables):
        d = (i % 27) + 1
        s = (i % 59) + 1
        lines += [
            f"[measurements_{i}]",
            f"t_offset       = 1979-05-{d:02d}T07:32:{s:02d}-08:00",
            f"t_offset_frac  = 1979-05-{d:02d}T00:32:{s:02d}.999999-07:00",
            f"t_offset_plus  = 1979-05-{d:02d}T07:32:{s:02d}+09:30",
            f"t_utc          = 1979-05-{d:02d}T07:32:{s:02d}Z",
            f"t_local_dt     = 1979-05-{d:02d}T07:32:{s:02d}",
            f"t_local_date   = {1970+(i%40)}-05-{d:02d}",
            f"t_local_time   = 07:32:{s:02d}.500",
            f"v1 = {9_000_000+i}.445991228",
            "v2 = 6.62607015e-34",
            f"v3 = -2.5e+{10+(i%9)}",
            "v4 = 1000.000001",
            "v5 = 3.141592653589793",
            "v6 = 0.0000001",
            f"v7 = {i+1}000000.0005",
            "",
        ]
    return "\n".join(lines) + "\n"


def nested_structures(n=40):
    lines = ["# nested-structures.toml — arrays of tables, deep arrays, nested inline tables", ""]
    for i in range(n):
        lines += [
            "[[fruit]]",
            f'name = "fruit-{i}"',
            f"sku = {1000+i}",
            f"[fruit.physical]",
            f'color = "color-{i%7}"',
            'shape = "round"',
            f"weight.grams = {120 + (i % 10)}.5",
            "[[fruit.variety]]",
            f'name = "variety-{i}-a"',
            "ripe = true",
            "[[fruit.variety]]",
            f'name = "variety-{i}-b"',
            "ripe = false",
            "",
        ]
    # a few deeply-nested arrays / inline tables at top level
    lines += [
        "[deep]",
        "arr = [[[[1, 2], [3, 4]]], [[[5, 6], [7, 8]]], [[[9, 10]]]]",
        "mixed = [ { a = 1, b = [2, 3] }, { a = 4, b = [5, 6] } ]",
        "inline = { a = { b = 1 }, c = { d = 2 }, e = { f = 3 } }",
        'matrix = [ [ "x", "y" ], [ "z", "w" ] ]',
        "",
    ]
    return "\n".join(lines) + "\n"


def kv_count(s):
    # rough count of "key = value" lines (excludes table headers/blank/comment)
    c = 0
    for ln in s.splitlines():
        t = ln.strip()
        if not t or t.startswith("#") or t.startswith("["):
            continue
        if "=" in t:
            c += 1
    return c


files = {
    "large-mixed.toml": large_mixed(),
    "datetime-heavy.toml": datetime_heavy(),
    "nested-structures.toml": nested_structures(),
}
for name, content in files.items():
    path = os.path.join(OUT, name)
    with open(path, "w") as f:
        f.write(content)
    print(f"{name}: {len(content):>7} bytes, ~{kv_count(content)} key=value lines")
