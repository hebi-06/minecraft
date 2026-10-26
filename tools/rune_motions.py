#!/usr/bin/env python3
"""0.46.0 룬 동작 (사용자 "룬 쓸때 권왕 같은 애들은 실제로 펀치 모션 나가게 해줘 … 연막탄이면 실제로 연막 날리는거 같은 모션으로 … 나머지 룬 전부"):
룬마다 그 룬이 하는 행동 (주먹 · 발차기 · 장풍 · 던지기 · 베기 · 부르기 · 기도 …) 의 동작을 자세 조각으로 짜서
assets/mmorpg/motion/poses.json 의 rn_* 동작으로 쓰고, rules_more.json MOTION 의 runeMotions (이름) · skills (rune:fn → 동작) 를 고침.
타격 시각은 룬 효과의 실제 틱 (CI rune-timing.txt 의 hits — 게임 시험 RpgRuneTimingTests) 에 맞춤.

  python3 tools/rune_motions.py          # 씀 (poses.json 의 rn_* 를 지우고 새로 · MOTION.runeMotions · skills 의 rune:*)
  python3 tools/rune_motions.py --check  # 쓸 것이 없는지
  python3 tools/rune_motions.py --list   # 룬 → 동작 표

동작 열쇠의 약속은 poses.json _comment (방향 [r, u, f] = 오른쪽 · 위 · 앞, body = [숙임, 오른쪽 비틂, 기욺] 도, 다리 rl · ll [x 뒤로 +, y, z] 도,
무릎 rk · lk 도, root [위, 앞] 칸, head [아래 +, 오른쪽 +]). 손 (hands): main · off = fist (맨손) · prop (소품 — hold · holdOff 를 holdAt · holdOffAt
구간에) — 1인칭 맨팔 · 소품은 fp (주 손) · fpo (왼손) 열쇠 {p: [오른쪽, 위, 뒤] 칸, r: [x, y, z] 도} (tools/hands_preview.py 로 봄).
"""
import copy
import json
import math
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import motion_arcs  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POSES = os.path.join(ROOT, "src/main/resources/assets/mmorpg/motion/poses.json")
RULES = os.path.join(ROOT, "core/src/main/resources/mmorpg/rules_more.json")
PREFIX = "rn_"


# ------------------------------------------------------------ 쓰기


def write(check=False):
    src = open(POSES, encoding="utf-8").read()
    data = json.loads(src)
    # 차례: 손으로 쓴 동작 → 룬 (rn_) → 직업 스킬 (jb_ — tools/job_motions.py) — 두 도구가 같은 꼴로 씀
    ms = {k: v for k, v in data["motions"].items() if not k.startswith(PREFIX) and not k.startswith("jb_")}
    for mo in MOTIONS:
        m = mo.build()
        motion_arcs.densify(mo.name, m)
        ms[mo.name] = m
    for k, v in data["motions"].items():
        if k.startswith("jb_"):
            ms[k] = v
    data["motions"] = ms
    out = motion_arcs.fmt(data)
    rules_src = open(RULES, encoding="utf-8").read()
    rules = json.loads(rules_src)
    motion = rules["MOTION"]
    names = [mo.name for mo in MOTIONS]
    skills = {k: v for k, v in motion["skills"].items() if not k.startswith("rune:")}
    skills.update(dict(sorted(TABLE.items())))
    rules_out = rules_src
    new_motion = dict(motion)
    new_motion["runeMotions"] = names
    new_motion["skills"] = skills
    if new_motion != motion:
        rules_out = K.write_motion_block(rules_src, new_motion)
    if check:
        bad = []
        if out != src:
            bad.append("poses.json")
        if rules_out != rules_src:
            bad.append("rules_more.json MOTION")
        if bad:
            print("rune motions differ: " + ", ".join(bad) + " — python3 tools/rune_motions.py")
            return 1
        print("rune motions ok (%d motions, %d rune keys)" % (len(names), len(TABLE)))
        return 0
    if out != src:
        open(POSES, "w", encoding="utf-8").write(out)
    if rules_out != rules_src:
        open(RULES, "w", encoding="utf-8").write(rules_out)
    print("wrote %d rune motions · %d rune keys" % (len(names), len(TABLE)))
    return 0


def listing():
    by = {}
    for k, v in TABLE.items():
        by.setdefault(v, []).append(k[5:])
    for mo in MOTIONS:
        print("%-22s %3dt  %-40s %s" % (mo.name, mo.ticks, ", ".join(by.get(mo.name, [])), mo.about))


# ------------------------------------------------------------ 동작 정의 (rune_motion_kit · rune_motions_*.py)

import rune_motion_kit as K  # noqa: E402
import rune_motions_fist  # noqa: E402,F401
import rune_motions_weapon  # noqa: E402,F401
import rune_motions_magic  # noqa: E402,F401
import rune_motions_misc  # noqa: E402,F401

MOTIONS = K.MOTIONS
TABLE = K.TABLE

if __name__ == "__main__":
    if "--list" in sys.argv:
        listing()
        sys.exit(0)
    sys.exit(write(check="--check" in sys.argv))
