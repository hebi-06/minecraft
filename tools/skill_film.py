#!/usr/bin/env python3
"""0.48.2 스킬 영상 모음 (사용자 "스킬들 모아서 나한테 보여줘 모델링하고 모션이 어떤지 봐야겠어"): CI 를 only=film,… 으로 돌리면
ci-logs 의 films/ 에 스킬마다 mp4 (1280×720 · 20fps — 틱마다 한 장, 몸 왼쪽 뒤 3/4 카메라) + json (이름 · 무기 / 직업 · 입력) 이 올라옴.
이 도구가 그것을 받아 이름을 얹고 (왼쪽 위 이름 · 무기 / 직업 · 입력, 오른쪽 위 순번) 무기 스킬 · 직업 스킬 영상 둘로 이음.

  CI: only = film,filmsk;film,filmw12,filmhero;film,filmjob1;film,filmjob2;film,filmjob3
      룬: only = film,filmrune1;film,filmrune2;film,filmrune3;film,filmpair;film,filmlimited  (→ --only runes --crf 28)
  git fetch origin +ci-logs:refs/remotes/origin/ci-logs
  python3 tools/skill_film.py                       # → build/film/weapon_skills.mp4 · job_skills.mp4 · runes.mp4 (있는 것만)
  python3 tools/skill_film.py --src 폴더 --out 폴더  # 이미 받은 films 폴더

순서: 무기 = 칼 · 단검 · 도끼 · 방패 · 활 · 완드 · 대검 · 창 · 낫 · 망치 · 홀 (무기마다 영웅 → 초월 1 · 2), 직업 = jobs.json 의 order (스킬 1 ~ 5).
"""
import argparse
import glob
import json
import os
import re
import shutil
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
W, H, FPS = 1280, 720, 20
ENC = ["-c:v", "libx264", "-preset", "medium", "-crf", "24", "-r", str(FPS), "-pix_fmt", "yuv420p", "-profile:v", "high", "-g", "120", "-an"]
CRF_AT = ENC.index("-crf") + 1
TYPES = ["sword", "dagger", "axe", "shield", "bow", "wand", "greatsword", "lance", "scythe", "hammer", "scepter"]
TYPE_KO = {"sword": "칼", "dagger": "단검", "axe": "도끼", "shield": "방패", "bow": "활", "wand": "완드", "greatsword": "대검", "lance": "창", "scythe": "낫",
           "hammer": "망치", "scepter": "홀"}


def font(bold, size):
    for p in (["/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc"] if bold else ["/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc"]) + glob.glob(
            "/usr/share/fonts/**/*CJK*.tt*", recursive=True):
        if os.path.exists(p):
            return ImageFont.truetype(p, size)
    return ImageFont.load_default()


def weapon_order():
    """무기 스킬 id → (무기 순서, 영웅 0 · 초월 1 · 2, 무기 이름)"""
    rules = json.load(open(os.path.join(ROOT, "core/src/main/resources/mmorpg/rules.json"), encoding="utf-8"))
    more = json.load(open(os.path.join(ROOT, "core/src/main/resources/mmorpg/rules_more.json"), encoding="utf-8"))
    skills = {k: v for k, v in rules["SKILLS"].items() if isinstance(v, list)}
    skills.update({k: v for k, v in more.get("SKILLS", {}).items() if isinstance(v, list)})
    order = {}
    for ti, t in enumerate(TYPES):
        hero = more.get("HERO_SKILLS", {}).get(t)
        if isinstance(hero, dict):
            order[hero["id"]] = (ti, 0, t)
        for i, s in enumerate(skills.get(t, [])):
            order[s["id"]] = (ti, i + 1, t)
    return order


def job_order():
    jobs = json.load(open(os.path.join(ROOT, "core/src/main/resources/mmorpg/jobs.json"), encoding="utf-8"))
    order = {}
    for ji, j in enumerate(jobs["order"]):
        for si, s in enumerate(jobs["jobs"][j]["skills"]):
            order[s["id"]] = (ji, si, j)
    return order


def clean(text):
    """마인크래프트 글자 꾸밈 (§c · §l …) 과 무기 이름 뒤 레벨 ([40렙]) 을 뺌"""
    text = re.sub(r"§.", "", text or "")
    return re.sub(r"\s*\[\d+렙\]", "", text).strip()


def rune_order():
    """룬 id (계열_등급) → (계열 순서, 등급 순서): rules_more 의 RUNE_FAMILY_ORDER (단일 · 두 스탯 계열) 다음 한정 초월 룬 (RUNE_LIMITED_ORDER)"""
    more = json.load(open(os.path.join(ROOT, "core/src/main/resources/mmorpg/rules_more.json"), encoding="utf-8"))
    fams = list(more.get("RUNE_FAMILY_ORDER", [])) + [f for f in more.get("RUNE_LIMITED_ORDER", []) if f not in more.get("RUNE_FAMILY_ORDER", [])]
    grades = ["common", "rare", "noble", "hero", "transcend"]
    return {"%s_%s" % (f, g): (fi, gi) for fi, f in enumerate(fams) for gi, g in enumerate(grades)}


def overlay_png(path, title, sub, idx):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ft, fs, fi = font(True, 40), font(False, 22), font(True, 22)
    tw = max(d.textlength(title, font=ft), d.textlength(sub, font=fs))
    d.rounded_rectangle((20, 18, 20 + tw + 44, 18 + 98), radius=14, fill=(8, 10, 16, 165))
    d.text((42, 26), title, font=ft, fill=(255, 255, 255, 255))
    d.text((42, 78), sub, font=fs, fill=(205, 214, 228, 255))
    iw = d.textlength(idx, font=fi)
    d.rounded_rectangle((W - iw - 52, 18, W - 20, 58), radius=12, fill=(8, 10, 16, 150))
    d.text((W - iw - 36, 24), idx, font=fi, fill=(255, 226, 150, 255))
    im.save(path)


def card_png(path, title, lines):
    im = Image.new("RGB", (W, H), (14, 17, 24))
    d = ImageDraw.Draw(im)
    ft, fl = font(True, 64), font(False, 28)
    y = H // 2 - 70 - 22 * len(lines)
    d.text(((W - d.textlength(title, font=ft)) / 2, y), title, font=ft, fill=(255, 255, 255))
    y += 104
    for ln in lines:
        d.text(((W - d.textlength(ln, font=fl)) / 2, y), ln, font=fl, fill=(190, 200, 216))
        y += 44
    im.save(path)


def run(cmd):
    r = subprocess.run(cmd, capture_output=True, text=True)
    if r.returncode != 0:
        sys.stderr.write(r.stderr[-2000:])
        raise SystemExit("ffmpeg failed: " + " ".join(cmd[:6]))


def scene_clip(src_mp4, ov_png, out):
    tmp = out[:-4] + ".part.mp4"
    run(["ffmpeg", "-y", "-loglevel", "error", "-i", src_mp4, "-i", ov_png, "-filter_complex",
         "[0:v]scale=%d:%d,fps=%d,tpad=stop_mode=clone:stop_duration=0.4[v];[v][1:v]overlay=0:0,format=yuv420p" % (W, H, FPS)] + ENC + [tmp])
    os.replace(tmp, out)


def scene_card(png, secs, out):
    tmp = out[:-4] + ".part.mp4"
    run(["ffmpeg", "-y", "-loglevel", "error", "-framerate", str(FPS), "-i", png, "-vf", "format=yuv420p,loop=-1:1,trim=duration=%.2f" % secs] + ENC + [tmp])
    os.replace(tmp, out)


def join(parts, out):
    lst = out + ".txt"
    with open(lst, "w", encoding="utf-8") as fh:
        for p in parts:
            fh.write("file '%s'\n" % os.path.abspath(p))
    tmp = out[:-4] + ".part.mp4"
    run(["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", lst, "-c", "copy", "-movflags", "+faststart", tmp])
    os.replace(tmp, out)
    os.remove(lst)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--from-git", default="origin/ci-logs")
    ap.add_argument("--src")
    ap.add_argument("--out", default=os.path.join(ROOT, "build/film"))
    ap.add_argument("--version", default="")
    ap.add_argument("--crf", type=int, default=24, help="화질 (클수록 작은 파일 — 룬 123종은 28: 8분 · 약 50MB)")
    ap.add_argument("--only", default="", help="weapon_skills · job_skills · runes 중 만들 것 (쉼표, 비면 모두)")
    a = ap.parse_args()
    ENC[CRF_AT] = str(a.crf)
    want = set(x for x in a.only.split(",") if x)
    os.makedirs(a.out, exist_ok=True)
    src = a.src
    if not src:
        src = os.path.join(a.out, "src")
        shutil.rmtree(src, ignore_errors=True)
        os.makedirs(src)
        arc = subprocess.run(["git", "-C", ROOT, "archive", a.from_git, "films"], capture_output=True)
        if arc.returncode != 0:
            raise SystemExit("no films/ in " + a.from_git)
        subprocess.run(["tar", "-x", "-C", src], input=arc.stdout, check=True)
        src = os.path.join(src, "films")
    clips = []
    for js in sorted(glob.glob(os.path.join(src, "*.json"))):
        mp4 = js[:-5] + ".mp4"
        if os.path.exists(mp4):
            clips.append((json.load(open(js, encoding="utf-8")), mp4))
    wo, jo, ro = weapon_order(), job_order(), rune_order()
    weapons = sorted([c for c in clips if c[0]["id"] in wo], key=lambda c: wo[c[0]["id"]][:2])
    jobs = sorted([c for c in clips if c[0]["id"] in jo], key=lambda c: jo[c[0]["id"]][:2])
    runes = sorted([c for c in clips if c[0]["id"] in ro], key=lambda c: ro[c[0]["id"]])
    other = [c[0]["id"] for c in clips if c[0]["id"] not in wo and c[0]["id"] not in jo and c[0]["id"] not in ro]
    if other:
        print("unknown clips:", other)
    work = os.path.join(a.out, "scenes")
    os.makedirs(work, exist_ok=True)
    ver = ["mmorpg " + a.version] if a.version else []
    for name, items, title, lines in (
            ("weapon_skills", weapons, "무기 스킬 %d종" % len(weapons),
             ["무기마다 영웅 스킬 → 초월 스킬 1 · 2", "실제 속도 (20틱/초) · 카메라는 몸 왼쪽 뒤 3/4 에서 따라감", "과녁: 쓰러지지 않는 허스크 · 스탯 150"] + ver),
            ("job_skills", jobs, "직업 스킬 %d종" % len(jobs),
             ["직업마다 스킬 1 ~ 5 (5 = 궁극기)", "실제 속도 (20틱/초) · 카메라는 몸 왼쪽 뒤 3/4 에서 따라감", "태세 · 강화 스킬은 뒤에 평타까지"] + ver),
            ("runes", runes, ("초월 룬 %d종" if runes and all(c[0]["id"].endswith("_transcend") for c in runes) else "룬 %d종") % len(runes),
             ["계열 순서대로 초월 룬, 끝에 한정 초월 룬 (0돌)" if runes and all(c[0]["id"].endswith("_transcend") for c in runes)
              else "계열마다 일반 → 희귀 → 고귀 → 영웅 → 초월, 끝에 한정 초월 룬 (0돌)", "실제 속도 (20틱/초) · 마지막 피해 뒤 약 1초에서 끊음",
              "카메라는 몸 옆 뒤 (105°) 높은 곳에서 몸을 60% 따라감 (영웅 · 초월은 더 멀리)"] + ver)):
        if not items or (want and name not in want):
            continue
        parts = []
        card = os.path.join(work, name + "_card.png")
        card_png(card, title, lines)
        parts.append(os.path.join(work, name + "_000_card.mp4"))
        scene_card(card, 2.4, parts[-1])
        for i, (m, mp4) in enumerate(items):
            ov = os.path.join(work, "%s_%03d.png" % (name, i + 1))
            overlay_png(ov, clean(m.get("title", m["id"])), clean(m.get("sub", "")), "%d / %d" % (i + 1, len(items)))
            parts.append(os.path.join(work, "%s_%03d.mp4" % (name, i + 1)))
            scene_clip(mp4, ov, parts[-1])
            print(name, i + 1, m["id"], m.get("title"))
        out = os.path.join(a.out, name + ".mp4")
        join(parts, out)
        print("→", out, "%.1f MB" % (os.path.getsize(out) / 1e6))


if __name__ == "__main__":
    main()
