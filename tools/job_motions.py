#!/usr/bin/env python3
"""0.47.0 직업 스킬 동작 (사용자 "다른 룬들도 다 고퀄리티의 모델링을 가지면 좋겠음 스킬이나 탈것도 그렇고 평타 같은것들 다 포함해서" · "직업 스킬부터"):
직업 11종 × 스킬 5 = 55 스킬마다 그 스킬이 하는 행동 (베기 · 회전 · 돌진 · 던지기 · 활 · 부르기 · 기도 …) 의 동작을 자세 조각 (rune_motion_kit) 으로 짜서
assets/mmorpg/motion/poses.json 의 jb_* 동작으로 쓰고, rules_more.json MOTION 의 jobMotions (이름) · skills (스킬 id → 동작) 를 고침.
타격 시각은 스킬 효과의 실제 틱 (Java 코드의 later · every — jobs.json 값) 에 맞춤. 시간이 길거나 때가 바뀌는 스킬 (궁극기 · 돌진 끝의 내려찍기 ·
방패가 돌아옴) 은 단계 열쇠 (스킬 id:단계) 의 동작을 서버가 그 순간에 다시 보냄 (JobKit.motion).

  python3 tools/job_motions.py          # 씀 (poses.json 의 jb_* 를 지우고 새로 · MOTION.jobMotions · skills 의 직업 스킬 열쇠)
  python3 tools/job_motions.py --check  # 쓸 것이 없는지
  python3 tools/job_motions.py --list   # 스킬 → 동작 표
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import motion_arcs  # noqa: E402
import rune_motion_kit as K  # noqa: E402

K.PREFIX = "jb_"
import job_motions_a  # noqa: E402,F401
import job_motions_b  # noqa: E402,F401
import job_motions_c  # noqa: E402,F401

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
POSES = os.path.join(ROOT, "src/main/resources/assets/mmorpg/motion/poses.json")
RULES = os.path.join(ROOT, "core/src/main/resources/mmorpg/rules_more.json")
JOBS = os.path.join(ROOT, "core/src/main/resources/mmorpg/jobs.json")
PREFIX = "jb_"


def job_skill_ids():
    d = json.load(open(JOBS, encoding="utf-8"))
    out = []
    for jid in d["order"]:
        for k in d["jobs"][jid]["skills"]:
            out.append(k["id"])
    return out


def is_job_key(k, ids):
    return k in ids or (":" in k and k.split(":")[0] in ids)


def write(check=False):
    ids = job_skill_ids()
    missing = [i for i in ids if i not in K.TABLE]
    stray = [k for k in K.TABLE if not is_job_key(k, ids)]
    if missing or stray:
        print("job motions: missing %s · stray %s" % (missing, stray))
        return 1
    # 왼손이 맨손인 동작: 첫 1인칭 왼팔 열쇠가 늦으면 처음에 내려 둠 (바닐라 쉬는 자리의 큰 팔 덩이가 잠깐 보이지 않게)
    for mo in K.MOTIONS:
        if mo.head.get("hands", {}).get("off") == "fist" and not any(0 < t <= mo.t(1.6) for t in mo.fpos):
            K.fpl(mo, 0.3, K.FP_LOW)
    src = open(POSES, encoding="utf-8").read()
    data = json.loads(src)
    # 차례: 손으로 쓴 동작 → 룬 (rn_) → 직업 스킬 (jb_) → 무기 스킬 (wp_ — tools/weapon_motions.py) — 룬 · 무기 스킬 도구와 같은 꼴
    ms = {k: v for k, v in data["motions"].items() if not k.startswith(PREFIX) and not k.startswith("wp_")}
    for mo in K.MOTIONS:
        m = mo.build()
        motion_arcs.densify(mo.name, m)
        ms[mo.name] = m
    for k, v in data["motions"].items():
        if k.startswith("wp_"):
            ms[k] = v
    data["motions"] = ms
    out = motion_arcs.fmt(data)
    rules_src = open(RULES, encoding="utf-8").read()
    rules = json.loads(rules_src)
    motion = rules["MOTION"]
    names = [mo.name for mo in K.MOTIONS]
    # skills: 무기 스킬 (그대로) → 직업 스킬 (직업 · 스킬 차례, 단계 열쇠는 그 스킬 뒤) → 룬 (그대로)
    old = motion["skills"]
    skills = {k: v for k, v in old.items() if not k.startswith("rune:") and not is_job_key(k, ids)}
    order = {sid: i for i, sid in enumerate(ids)}
    for k in sorted(K.TABLE, key=lambda k: (order[k.split(":")[0]], k.count(":"), k)):
        skills[k] = K.TABLE[k]
    for k, v in old.items():
        if k.startswith("rune:"):
            skills[k] = v
    new_motion = dict(motion)
    new_motion["skills"] = skills
    new_motion["jobMotions"] = names
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
            print("job motions differ: " + ", ".join(bad) + " — python3 tools/job_motions.py")
            return 1
        print("job motions ok (%d motions, %d skill keys)" % (len(names), len(K.TABLE)))
        return 0
    if out != src:
        open(POSES, "w", encoding="utf-8").write(out)
    if rules_out != rules_src:
        open(RULES, "w", encoding="utf-8").write(rules_out)
    print("wrote %d job motions · %d skill keys" % (len(names), len(K.TABLE)))
    return 0


def listing():
    by = {}
    for k, v in K.TABLE.items():
        by.setdefault(v, []).append(k)
    for mo in K.MOTIONS:
        print("%-28s %3dt  %-40s %s" % (mo.name, mo.ticks, ", ".join(by.get(mo.name, [])), mo.about))


if __name__ == "__main__":
    if "--list" in sys.argv:
        listing()
        sys.exit(0)
    sys.exit(write(check="--check" in sys.argv))
