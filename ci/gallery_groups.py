#!/usr/bin/env python3
"""화면 시험 (클라이언트 사진첩) 을 몇 묶음으로 나눠 동시에 돌릴지 — GitHub Actions 의 matrix (build.yml 의 client 일) 로.

인자: CI 수동 실행의 only 입력
  비움          → 기본 묶음 모두 (아래 DEFAULT — 한 묶음이 20분 안쪽이 되게)
  none          → 사진첩 없이 (client 일 건너뜀)
  a,b;c,d       → 세미콜론으로 묶음, 쉼표로 묶음 안의 사진첩 키 (prod 를 넣으면 그 묶음에서 배포 환경 클라이언트도)
출력: GITHUB_OUTPUT 줄 'groups=<JSON 목록>' — 항목 {name, only, prod}
사진첩 키: client weapon particle skill rune gear form nav mob arena boss monster tree
          scorpions kraken worm jorm nidhogg skoll ymir surtr mount pvp stattree runebreak mob9 hero
"""
import json
import sys

# (이름, 사진첩 키, 배포 환경 클라이언트도) — 걸린 시간 (2026-10 CI): rune 8분 · arena 7분 · 보스 사진첩은 하나에 4~7분
DEFAULT = [
    ("ui", "client,weapon,particle,skill,gear,form,nav,mob", True),
    ("rune", "rune,arena", False),
    ("world", "boss,monster,tree", False),
    ("sk", "scorpions,kraken,mob9", False),
    ("wj", "worm,jorm", False),
    ("ns", "nidhogg,skoll", False),
    ("ys", "ymir,surtr", False),
    ("six", "mount,pvp,stattree,runebreak", False),
    ("hero", "hero", False),
]


def groups(only):
    only = (only or "").strip()
    if not only:
        return [{"name": n, "only": o, "prod": p} for n, o, p in DEFAULT]
    if only.lower() == "none":
        return []
    out = []
    for i, part in enumerate(p for p in only.split(";") if p.strip()):
        keys = [k.strip() for k in part.split(",") if k.strip()]
        prod = "prod" in keys
        keys = [k for k in keys if k != "prod"]
        out.append({"name": "g%d" % (i + 1), "only": ",".join(keys) if keys else "none", "prod": prod})
    return out


if __name__ == "__main__":
    print("groups=" + json.dumps(groups(sys.argv[1] if len(sys.argv) > 1 else ""), separators=(",", ":")))
