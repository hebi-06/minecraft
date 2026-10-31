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


# 0.50.0 몸 움직임 (JobMove — 사용자 "플레이어 모션들이 역동적인게 내가 좋아하는 느낌"): 몸이 뜨거나 미끄러지는 동안의 다리. 동작 → [(시작 틱, 끝 틱, 꼴)] —
# 그 사이 열쇠의 다리 (rl · ll · rk · lk · root) 를 꼴로 덮고 양끝에 열쇠를 더함. 이 표에 있는 동작은 softLegs 를 끔 (몸이 옮겨지는 동안 걸음 다리가 나오지 않게)
AIR_LEGS = {
    "tuck": dict(rl=[-56, 0, 8], rk=96, ll=[-28, 0, -8], lk=82, root=[0.0, 0]),  # 뛰어올라 무릎을 당김
    "hop": dict(rl=[-34, 0, 6], rk=62, ll=[-12, 0, -6], lk=44, root=[0.0, 0]),  # 짧은 뜀
    "float": dict(rl=[-6, 0, 3], rk=16, ll=[8, 0, -3], lk=28, root=[0.0, 0]),  # 떠 있음 (발끝이 아래)
    "land": dict(rl=[-30, 0, 10], rk=62, ll=[-22, 0, -10], lk=56, root=[-0.2, 0]),  # 내려앉으며 웅크림
}
AIR = {
    "jb_swordsman_moon": [(2, 15, "tuck")],
    "jb_swordsman_ult": [(3, 20, "tuck")],
    "jb_swordsman_ult_volley": [(0, 10, "tuck")],
    "jb_swordsman_ult_gather": [(0, 9, "tuck"), (12, 14, "land")],
    "jb_warrior_crush": [(1, 5, "tuck"), (6, 9, "land")],
    "jb_spellblade_arcslash": [],
    "jb_spellblade_runedance": [(4, 34, "float")],
    "jb_spellblade_ult": [(3, 22, "tuck")],
    "jb_spellblade_ult_slash0": [(0, 12, "tuck")],
    "jb_spellblade_ult_slash1": [(0, 12, "tuck")],
    "jb_spellblade_ult_slash2": [(0, 12, "tuck")],
    "jb_spellblade_ult_thrust": [(0, 3, "tuck")],
    "jb_mage_bolts": [],
    "jb_mage_ult": [(3, 22, "float")],
    "jb_mage_ult_wave": [(0, 10, "float")],
    "jb_mage_ult_close": [(0, 8, "float")],
    "jb_archer_rapid": [(1, 6, "hop")],
    "jb_archer_rain": [(1, 9, "tuck")],
    "jb_archer_ult": [(2, 24, "tuck"), (26, 31, "land")],
    "jb_assassin_bloom": [],
    "jb_assassin_daggers": [(1, 10, "tuck")],
    "jb_lancer_triple": [],
    "jb_lancer_cyclone": [(2, 15, "tuck")],
    "jb_lancer_ult": [(2, 20, "tuck")],
    "jb_reaper_reap": [],
    "jb_reaper_burst": [(1, 6, "tuck"), (8, 12, "land")],
    "jb_reaper_ult": [(2, 14, "float")],
    "jb_reaper_ult_drain": [(0, 10, "float")],
    "jb_reaper_ult_close": [(0, 2, "float")],
    "jb_paladin_smite": [(1, 5, "tuck"), (6, 9, "land")],
    "jb_paladin_quake": [(2, 8, "hop"), (12, 18, "hop")],
    "jb_paladin_ult": [(3, 18, "tuck")],
    "jb_paladin_ult_smash": [(0, 3, "tuck")],
    "jb_priest_ult": [(3, 18, "float")],
    "jb_priest_ult_toll": [(0, 16, "float")],
    "jb_guardian_bash": [],
    "jb_guardian_ult": [(1, 9, "tuck"), (10, 14, "land")],
}


def air_legs():
    """AIR 표를 동작 열쇠에 덮음 (0.50.0)"""
    by = {mo.name: mo for mo in K.MOTIONS}
    for name, spans in AIR.items():
        mo = by.get(name)
        if mo is None:
            raise SystemExit("AIR: 없는 동작 " + name)
        mo.head["softLegs"] = False
        for a, b, style in spans:
            legs = AIR_LEGS[style]
            ta, tb = mo.t(a), mo.t(b)
            for t in (ta, tb):
                mo.keys.setdefault(t, {})
            for t, pose in mo.keys.items():
                if ta - 1e-9 <= t <= tb + 1e-9:
                    for k, v in legs.items():
                        pose[k] = list(v) if isinstance(v, list) else v


def write(check=False):
    ids = job_skill_ids()
    air_legs()
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
