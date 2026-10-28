#!/usr/bin/env python3
"""0.48.0 무기 스킬 동작 (사용자 "다른 룬들도 다 고퀄리티의 모델링을 가지면 좋겠음 스킬이나 탈것도 그렇고 평타 같은것들 다 포함해서" — 직업 스킬 다음 차례):
무기 스킬 32 (초월 22 · 영웅 10) 마다 그 스킬이 하는 행동 (X 자 베기 · 솟구쳐 내리꽂기 · 창을 던지고 받음 · 시계를 세움 …) 의 동작을 자세 조각
(rune_motion_kit) 으로 짜서 assets/mmorpg/motion/poses.json 의 wp_* 동작으로 쓰고, rules_more.json MOTION 의 weaponMotions (이름) · skills (스킬 id → 동작) 를 고침.
틱 0 = 스킬이 나간 틱 (효과와 같은 틱). 때가 바뀌는 스킬 (돌진이 멈춘 때 · 창이 돌아온 때 · 마지막 폭발) 은 단계 열쇠 (스킬 id:단계) 의 동작을
서버가 그 순간에 다시 보냄 (Kit.motion · motionLater).

  python3 tools/weapon_motions.py          # 씀 (poses.json 의 wp_* 를 지우고 새로 · MOTION.weaponMotions · skills 의 무기 스킬 열쇠)
  python3 tools/weapon_motions.py --check  # 쓸 것이 없는지
  python3 tools/weapon_motions.py --list   # 스킬 → 동작 표
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import motion_arcs  # noqa: E402
import rune_motion_kit as K  # noqa: E402

K.PREFIX = "wp_"
import weapon_motions_a  # noqa: E402,F401
import weapon_motions_b  # noqa: E402,F401
import weapon_motions_c  # noqa: E402,F401

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POSES = os.path.join(ROOT, "src/main/resources/assets/mmorpg/motion/poses.json")
RULES = os.path.join(ROOT, "core/src/main/resources/mmorpg/rules_more.json")
RULES0 = os.path.join(ROOT, "core/src/main/resources/mmorpg/rules.json")
JOBS = os.path.join(ROOT, "core/src/main/resources/mmorpg/jobs.json")
PREFIX = "wp_"


def weapon_skill_ids():
    """무기 스킬 id (무기 종류 차례 — 초월 둘씩 → 영웅). 규칙 rules_more 의 SKILLS 가 rules 의 같은 종류를 덮음"""
    base = json.load(open(RULES0, encoding="utf-8"))
    more = json.load(open(RULES, encoding="utf-8"))
    types = list(base.get("SKILLS", {}))
    for t in more.get("SKILLS", {}):
        if t not in types:
            types.append(t)
    out = []
    for t in types:
        lst = more.get("SKILLS", {}).get(t, base.get("SKILLS", {}).get(t))
        if isinstance(lst, list):
            out += [s["id"] for s in lst if isinstance(s, dict) and s.get("id")]
    for src in (base, more):
        for t, s in src.get("HERO_SKILLS", {}).items():
            if isinstance(s, dict) and s.get("id") and s["id"] not in out:
                out.append(s["id"])
    return out


def job_skill_ids():
    d = json.load(open(JOBS, encoding="utf-8"))
    return {k["id"] for jid in d["order"] for k in d["jobs"][jid]["skills"]}


def is_key_of(k, ids):
    return k in ids or (":" in k and k.split(":")[0] in ids)


def write(check=False):
    ids = weapon_skill_ids()
    missing = [i for i in ids if i not in K.TABLE]
    stray = [k for k in K.TABLE if not is_key_of(k, ids)]
    if missing or stray:
        print("weapon motions: missing %s · stray %s" % (missing, stray))
        return 1
    # 왼손이 맨손인 동작: 첫 1인칭 왼팔 열쇠가 늦으면 처음에 내려 둠 (직업 동작과 같음)
    for mo in K.MOTIONS:
        if mo.head.get("hands", {}).get("off") == "fist" and not any(0 < t <= mo.t(1.6) for t in mo.fpos):
            K.fpl(mo, 0.3, K.FP_LOW)
    src = open(POSES, encoding="utf-8").read()
    data = json.loads(src)
    # 차례: 손으로 쓴 동작 → 룬 (rn_) → 직업 스킬 (jb_) → 무기 스킬 (wp_)
    ms = {k: v for k, v in data["motions"].items() if not k.startswith(PREFIX)}
    for mo in K.MOTIONS:
        m = mo.build()
        motion_arcs.densify(mo.name, m)
        ms[mo.name] = m
    data["motions"] = ms
    out = motion_arcs.fmt(data)
    rules_src = open(RULES, encoding="utf-8").read()
    rules = json.loads(rules_src)
    motion = rules["MOTION"]
    names = [mo.name for mo in K.MOTIONS]
    # skills: 무기 스킬 (무기 차례, 단계 열쇠는 그 스킬 뒤) → 직업 스킬 (그대로) → 룬 (그대로)
    old = motion["skills"]
    jobs = job_skill_ids()
    order = {sid: i for i, sid in enumerate(ids)}
    skills = {}
    for k in sorted(K.TABLE, key=lambda k: (order[k.split(":")[0]], k.count(":"), k)):
        skills[k] = K.TABLE[k]
    for k, v in old.items():
        if not k.startswith("rune:") and not is_key_of(k, ids):
            if not is_key_of(k, jobs):
                print("weapon motions: unknown skill key %s (kept)" % k)
            skills[k] = v
    for k, v in old.items():
        if k.startswith("rune:"):
            skills[k] = v
    new_motion = dict(motion)
    new_motion["skills"] = skills
    new_motion["weaponMotions"] = names
    rules_out = rules_src
    if new_motion != motion or list(new_motion["skills"]) != list(motion["skills"]):
        rules_out = K.write_motion_block(rules_src, new_motion)
    if check:
        bad = []
        if out != src:
            bad.append("poses.json")
        if rules_out != rules_src:
            bad.append("rules_more.json MOTION")
        if bad:
            print("weapon motions differ: " + ", ".join(bad) + " — python3 tools/weapon_motions.py")
            return 1
        print("weapon motions ok (%d motions, %d skill keys)" % (len(names), len(K.TABLE)))
        return 0
    if out != src:
        open(POSES, "w", encoding="utf-8").write(out)
    if rules_out != rules_src:
        open(RULES, "w", encoding="utf-8").write(rules_out)
    print("wrote %d weapon motions · %d skill keys" % (len(names), len(K.TABLE)))
    return 0


def listing():
    by = {}
    for k, v in K.TABLE.items():
        by.setdefault(v, []).append(k)
    for mo in K.MOTIONS:
        print("%-34s %3dt  %-34s %s" % (mo.name, mo.ticks, ", ".join(by.get(mo.name, [])), mo.about))


if __name__ == "__main__":
    if "--list" in sys.argv:
        listing()
        sys.exit(0)
    sys.exit(write(check="--check" in sys.argv))
