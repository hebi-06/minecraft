#!/usr/bin/env python3
"""2026-11-16 그림판 크기 모으기: CI 영상 로그 (ci-logs 가지 · 받아 둔 폴더) 의 jobcensus "|| sheets 이름=수@크기" → tools/jobvfx/sheet_sizes.json
(그림판마다 가장 큰 크기 — 조각 그림은 이름_rc 를 뺀 바탕 이름. 있던 값보다 클 때만 바꿈 → 여러 영상을 차례로 넣어도 됨)

  python3 tools/jobvfx/sheet_sizes.py <로그 폴더 · 파일> …
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "sheet_sizes.json")


def scan(paths):
    found = {}
    for p in paths:
        files = [os.path.join(d, f) for d, _, fs in os.walk(p) for f in fs] if os.path.isdir(p) else [p]
        for f in files:
            try:
                text = open(f, encoding="utf-8", errors="ignore").read()
            except OSError:
                continue
            for m in re.finditer(r"\|\| sheets ([^|\n]*)", text):
                for tok in m.group(1).split():
                    mm = re.match(r"^([a-z0-9_]+)=\d+@([0-9.]+)$", tok)
                    if not mm:
                        continue
                    base = re.sub(r"_\d\d$", "", mm.group(1))
                    found[base] = max(found.get(base, 0.0), float(mm.group(2)))
    return found


def main():
    doc = json.load(open(OUT)) if os.path.exists(OUT) else {}
    found = scan(sys.argv[1:])
    changed = 0
    for k, v in sorted(found.items()):
        if v > doc.get(k, 0.0):
            doc[k] = round(v, 3)
            changed += 1
    open(OUT, "w").write(json.dumps(doc, ensure_ascii=False, indent=1, sort_keys=True) + "\n")
    print("sheet sizes: %d found, %d changed, %d total" % (len(found), changed, len([k for k in doc if not k.startswith("_")])))


if __name__ == "__main__":
    main()
