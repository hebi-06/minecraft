#!/usr/bin/env python3
"""0.48.2 스킬 영상 모음 (사용자 "스킬들 모아서 나한테 보여줘 모델링하고 모션이 어떤지 봐야겠어"): CI 를 only=film,… 으로 돌리면
ci-logs 의 films/ 에 스킬마다 mp4 (1280×720 · 20fps — 틱마다 한 장, 몸 왼쪽 뒤 3/4 카메라) + json (이름 · 무기 / 직업 · 입력) 이 올라옴.
이 도구가 그것을 받아 이름을 얹고 (왼쪽 위 이름 · 무기 / 직업 · 입력, 오른쪽 위 순번) 무기 스킬 · 직업 스킬 영상 둘로 이음.

  CI: only = film,filmsk;film,filmw12,filmhero;film,filmjob1;film,filmjob2;film,filmjob3
  git fetch origin +ci-logs:refs/remotes/origin/ci-logs
  python3 tools/skill_film.py                       # → build/film/weapon_skills.mp4 · job_skills.mp4
  python3 tools/skill_film.py --src 폴더 --out 폴더  # 이미 받은 films 폴더

순서: 무기 = 칼 · 단검 · 도끼 · 방패 · 활 · 완드 · 대검 · 창 · 낫 · 망치 · 홀 (무기마다 영웅 → 초월 1 · 2), 직업 = jobs.json 의 order (스킬 1 ~ 5).
"""
import argparse
import glob
import json
import os
import shutil
import subprocess
import sys

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
W, H, FPS = 1280, 720, 20
ENC = ["-c:v", "libx264", "-preset", "medium", "-crf", "24", "-r", str(FPS), "-pix_fmt", "yuv420p", "-profile:v", "high", "-g", "40", "-an"]
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
    a = ap.parse_args()
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
    wo, jo = weapon_order(), job_order()
    weapons = sorted([c for c in clips if c[0]["id"] in wo], key=lambda c: wo[c[0]["id"]][:2])
    jobs = sorted([c for c in clips if c[0]["id"] in jo], key=lambda c: jo[c[0]["id"]][:2])
    other = [c[0]["id"] for c in clips if c[0]["id"] not in wo and c[0]["id"] not in jo]
    if other:
        print("unknown clips:", other)
    work = os.path.join(a.out, "scenes")
    os.makedirs(work, exist_ok=True)
    ver = ["mmorpg " + a.version] if a.version else []
    for name, items, title, lines in (
            ("weapon_skills", weapons, "무기 스킬 %d종" % len(weapons),
             ["무기마다 영웅 스킬 → 초월 스킬 1 · 2", "실제 속도 (20틱/초) · 카메라는 몸 왼쪽 뒤 3/4 에서 따라감", "과녁: 쓰러지지 않는 허스크 · 스탯 150"] + ver),
            ("job_skills", jobs, "직업 스킬 %d종" % len(jobs),
             ["직업마다 스킬 1 ~ 5 (5 = 궁극기)", "실제 속도 (20틱/초) · 카메라는 몸 왼쪽 뒤 3/4 에서 따라감", "태세 · 강화 스킬은 뒤에 평타까지"] + ver)):
        if not items:
            continue
        parts = []
        card = os.path.join(work, name + "_card.png")
        card_png(card, title, lines)
        parts.append(os.path.join(work, name + "_000_card.mp4"))
        scene_card(card, 2.4, parts[-1])
        for i, (m, mp4) in enumerate(items):
            ov = os.path.join(work, "%s_%03d.png" % (name, i + 1))
            overlay_png(ov, m.get("title", m["id"]), m.get("sub", ""), "%d / %d" % (i + 1, len(items)))
            parts.append(os.path.join(work, "%s_%03d.mp4" % (name, i + 1)))
            scene_clip(mp4, ov, parts[-1])
            print(name, i + 1, m["id"], m.get("title"))
        out = os.path.join(a.out, name + ".mp4")
        join(parts, out)
        print("→", out, "%.1f MB" % (os.path.getsize(out) / 1e6))


if __name__ == "__main__":
    main()
