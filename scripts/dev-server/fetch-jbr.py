#!/usr/bin/env python3
import concurrent.futures
import os
import subprocess
import sys

URL = os.environ.get(
    "LIVE_DEV_JBR_URL",
    "https://d2xrhe97vsfxuc.cloudfront.net/jbrsdk-21.0.11-windows-x64-b1163.125.tar.gz",
)
SIZE = int(os.environ.get("LIVE_DEV_JBR_SIZE", "279324417"))
BASE = sys.argv[1] if len(sys.argv) > 1 else os.environ.get("LIVE_DEV_BASE", ".")
PARALLEL = int(os.environ.get("LIVE_DEV_JBR_PARALLEL", "200"))
CHUNK = 16384
archive = os.path.join(BASE, "jbrsdk.tar.gz")
chunks = os.path.join(BASE, ".jbrsdk-chunks")
os.makedirs(chunks, exist_ok=True)
count = (SIZE + CHUNK - 1) // CHUNK


def one(index):
    path = os.path.join(chunks, f"{index:05d}")
    start = index * CHUNK
    end = min(start + CHUNK, SIZE) - 1
    want = end - start + 1
    if os.path.exists(path) and os.path.getsize(path) == want:
        return index, want
    for _ in range(6):
        try:
            result = subprocess.run(
                ["curl", "-4", "-s", "--max-time", "20", "-r", f"{start}-{end}", "-o", path, URL],
                timeout=25,
            )
            if result.returncode == 0 and os.path.exists(path) and os.path.getsize(path) == want:
                return index, want
        except subprocess.TimeoutExpired:
            pass
    return index, -1


failed = 0
with concurrent.futures.ThreadPoolExecutor(max_workers=PARALLEL) as pool:
    for index, got in pool.map(one, range(count)):
        if got < 0:
            failed += 1
        if (index + 1) % 1000 == 0:
            print(f"JBR: {index + 1}/{count} фрагментов, ошибок {failed}", file=sys.stderr, flush=True)

if failed:
    print(f"JBR: не скачано фрагментов {failed}; повторите команду", file=sys.stderr)
    sys.exit(1)

with open(archive, "wb") as target:
    for index in range(count):
        with open(os.path.join(chunks, f"{index:05d}"), "rb") as part:
            target.write(part.read())
if os.path.getsize(archive) != SIZE:
    print(f"JBR: размер {os.path.getsize(archive)} вместо {SIZE}", file=sys.stderr)
    sys.exit(1)
print(f"JBR: архив {archive}", file=sys.stderr)
