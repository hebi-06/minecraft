#!/usr/bin/env python3
"""geo_tex · weapon_models 자체 시험: 팔레트 디자인을 '다듬은 파일' 모양 (큰 텍스처 · 면 UV / 상자 UV · mirror · 회전) 으로 바꿔 넣어도
자바 모델이 같게 나오는지 (상자 자리 · 면마다 보이는 색 · 빛남) — 클로드 디자인 · Blockbench 로 다듬어 받은 파일이 그대로 들어가는지 미리 확인.

  python3 tools/geo_tex_selftest.py
"""
import copy
import json
import math
import os
import shutil
import sys
import tempfile

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo_tex  # noqa: E402
import weapon_models  # noqa: E402
import weapon_paint  # noqa: E402

# 칼 · 새 4종 (weapon 뼈 60°) + 활 (82°) · 완드 (30°) · 방패 (0° · 왼손 weapon_off 뼈) — 뼈대가 빠진 파일도 종류마다 같은 모델이 나오는지
DESIGNS = ["hammer_transcend_lv0", "scythe_hero_lv0", "lance_lv0", "scepter_rare_lv0", "sword_transcend_lv0", "bow_rare_lv0", "staff_lv0",
           "shield_hero_lv0"]
BAD = []


def check(ok, what):
    if not ok:
        BAD.append(what)


def weapon_bone(geo):
    return next(b for b in geo["minecraft:geometry"][0]["bones"] if b["name"].lower() == "weapon")


def cell_color(tex, cube):
    u, v = cube["uv"]["north"]["uv"]
    return tex.getpixel((int(u), int(v)))


def per_face(geo, tex, scale):
    """면 UV · 큰 텍스처 (면마다 따로 칸 — 크기 = 면 크기 × scale, 가장자리 1px 다른 색으로 번짐 확인)"""
    g = copy.deepcopy(geo)
    cubes = weapon_bone(g)["cubes"]
    rects = []
    for c in cubes:
        w, h, d = c["size"]
        sizes = {"north": (w, h), "south": (w, h), "east": (d, h), "west": (d, h), "up": (w, d), "down": (w, d)}
        rects.append({f: (max(1, math.ceil(a * scale)), max(1, math.ceil(b * scale))) for f, (a, b) in sizes.items()})
    # 줄로 쌓기
    width = 256
    x = y = row = 0
    place = []
    for r in rects:
        pf = {}
        for f, (a, b) in r.items():
            if x + a > width:
                x, y, row = 0, y + row, 0
            pf[f] = (x, y, a, b)
            x += a
            row = max(row, b)
        place.append(pf)
    height = y + row
    big = Image.new("RGBA", (width, max(1, height)), (0, 0, 0, 0))
    for c, pf in zip(cubes, place):
        col = cell_color(tex, c)
        for f, (px, py, a, b) in pf.items():
            for yy in range(py, py + b):
                for xx in range(px, px + a):
                    big.putpixel((xx, yy), col)
        c["uv"] = {f: {"uv": [px, py], "uv_size": [a, b]} for f, (px, py, a, b) in pf.items()}
    desc = g["minecraft:geometry"][0]["description"]
    desc["texture_width"], desc["texture_height"] = big.size
    return g, big


def box_uv(geo, tex, mirror_every=3):
    """상자 UV (정수 크기로 반올림 — 상자 UV 는 베드락처럼 내림) · 몇 개는 mirror"""
    g = copy.deepcopy(geo)
    cubes = weapon_bone(g)["cubes"]
    width = 256
    x = y = row = 0
    layout = []
    for c in cubes:
        w, h, d = (math.floor(v + 1e-9) for v in c["size"])
        # 얇은 상자 (내린 크기 0) 의 면은 띠 바로 옆 줄을 읽으므로 (베드락과 같음) 한 줄 · 한 칸 더 칠함
        bw, bh = 2 * (w + d) + 1, d + h + 1
        if x + bw > width:
            x, y, row = 0, y + row, 0
        layout.append((x, y, bw, bh))
        x += bw
        row = max(row, bh)
    big = Image.new("RGBA", (width, max(1, y + row)), (0, 0, 0, 0))
    for i, (c, (px, py, bw, bh)) in enumerate(zip(cubes, layout)):
        col = cell_color(tex, c)
        for yy in range(py, py + bh):
            for xx in range(px, px + bw):
                big.putpixel((xx, yy), col)
        c["uv"] = [px, py]
        if i % mirror_every == 0:
            c["mirror"] = True
    desc = g["minecraft:geometry"][0]["description"]
    desc["texture_width"], desc["texture_height"] = big.size
    return g, big


def convert(tmp, design, geo, tex):
    weapon_models.ART = tmp
    json.dump(geo, open(os.path.join(tmp, f"{design}.geo.json"), "w"))
    tex.save(os.path.join(tmp, f"{design}.png"))
    out_m = os.path.join(tmp, "m")
    out_t = os.path.join(tmp, "t")
    os.makedirs(out_m, exist_ok=True)
    os.makedirs(out_t, exist_ok=True)
    meta = json.load(open(os.path.join(ROOT_ART, "designs.json")))["designs"][design]
    weapon_models.convert(design, meta, out_m, out_t)
    model = json.load(open(os.path.join(out_m, f"{design}.json")))
    out_tex = Image.open(os.path.join(out_t, f"{design}.png")).convert("RGBA")
    return model, out_tex


def face_colors(model, tex):
    """면마다 자바 uv 사각형 안 픽셀들의 색 모음"""
    out = []
    w, h = tex.size
    for el in model["elements"]:
        fc = {}
        for f, fd in el["faces"].items():
            u0, v0, u1, v1 = fd["uv"]
            xs = sorted((u0 * w / 16, u1 * w / 16))
            ys = sorted((v0 * h / 16, v1 * h / 16))
            cols = set()
            for yy in range(int(math.floor(ys[0] + 1e-2)), max(int(math.floor(ys[0] + 1e-2)) + 1, int(math.ceil(ys[1] - 1e-2)))):
                for xx in range(int(math.floor(xs[0] + 1e-2)), max(int(math.floor(xs[0] + 1e-2)) + 1, int(math.ceil(xs[1] - 1e-2)))):
                    cols.add(tex.getpixel((min(w - 1, xx), min(h - 1, yy)))[:3])
            fc[f] = cols
        out.append(fc)
    return out


ROOT_ART = weapon_models.ART


def main():
    for design in DESIGNS:
        geo = json.load(open(os.path.join(ROOT_ART, f"{design}.geo.json")))
        tex = Image.open(os.path.join(ROOT_ART, f"{design}.png")).convert("RGBA")
        tmp = tempfile.mkdtemp(prefix="geo_tex_")
        try:
            base, base_tex = convert(tmp, design, geo, tex)
            base_cols = face_colors(base, base_tex)
            layouts = (("per-face x2", per_face(geo, tex, 2)), ("per-face x4", per_face(geo, tex, 4)), ("box uv", box_uv(geo, tex)),
                       ("paintable", weapon_paint.paintable(design, ROOT_ART)[:2]))  # 칠하기용 (weapon_paint — 넘겨 드리는 묶음)
            for label, (g, t) in layouts:
                model, mtex = convert(tmp, design, g, t)
                check(len(model["elements"]) == len(base["elements"]), f"{design} {label}: element count")
                cols = face_colors(model, mtex)
                for i, (a, b) in enumerate(zip(base["elements"], model["elements"])):
                    check(a["from"] == b["from"] and a["to"] == b["to"], f"{design} {label}: element {i} box")
                    check(("light_emission" in a) == ("light_emission" in b), f"{design} {label}: element {i} glow")
                    check(set(a["faces"]) == set(b["faces"]), f"{design} {label}: element {i} faces {set(a['faces'])} vs {set(b['faces'])}")
                    for f in a["faces"]:
                        if f in cols[i]:
                            check(cols[i][f] == base_cols[i][f], f"{design} {label}: element {i} {f} colors {cols[i][f]} vs {base_cols[i][f]}")
                check(model["display"] == base["display"], f"{design} {label}: display")
            # 뼈대 없이 weapon 뼈만 (부모 · 회전 중심 · 회전도 빠짐) → 애드온 뼈대 · 기본값을 채워 같은 모델
            g = copy.deepcopy(geo)
            wb = copy.deepcopy(weapon_bone(g))
            for k in ("parent", "pivot", "rotation"):
                wb.pop(k, None)
            g["minecraft:geometry"][0]["bones"] = [wb]
            model, _ = convert(tmp, design, g, tex)
            check(model["elements"] == base["elements"] and model["display"] == base["display"], f"{design}: weapon bone only → same model")
            # 회전: 상자 하나를 22.5° 돌림 → 자바 rotation · 이상한 각은 경고 + 가까운 값
            g = copy.deepcopy(geo)
            c0 = weapon_bone(g)["cubes"][0]
            c0["rotation"] = [0, 22.5, 0]
            c0["pivot"] = [c0["origin"][i] + c0["size"][i] / 2 for i in range(3)]
            c1 = weapon_bone(g)["cubes"][1]
            c1["rotation"] = [30, 0, 10]
            json.dump(g, open(os.path.join(tmp, "rot.geo.json"), "w"))
            tex.save(os.path.join(tmp, "rot.png"))
            m = geo_tex.load("rot", os.path.join(tmp, "rot.geo.json"), os.path.join(tmp, "rot.png"))
            check(m.cubes[0].rot is not None and m.cubes[0].rot[0] == "y" and m.cubes[0].rot[1] == 22.5, f"{design}: 22.5° y rotation kept {m.cubes[0].rot}")
            check(m.cubes[1].rot is not None and m.cubes[1].rot[0] == "x" and m.cubes[1].rot[1] == 22.5, f"{design}: 30°/10° → x 22.5 {m.cubes[1].rot}")
            check(len(m.warnings) == 2, f"{design}: two warnings {m.warnings}")
            model, _ = convert(tmp, design, g, tex)
            r0 = model["elements"][0].get("rotation")
            check(r0 is not None and r0["axis"] == "y" and r0["angle"] == -22.5, f"{design}: java rotation (three.js y = -bedrock) {r0}")
        finally:
            shutil.rmtree(tmp)
    weapon_models.ART = ROOT_ART
    print(f"geo_tex selftest: {len(DESIGNS)} designs, {len(BAD)} problems")
    for b in BAD[:40]:
        print("  FAIL", b)
    sys.exit(1 if BAD else 0)


if __name__ == "__main__":
    main()
