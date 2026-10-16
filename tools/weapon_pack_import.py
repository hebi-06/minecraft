#!/usr/bin/env python3
"""사용자 무기 디자인 리워크 (자바 리소스 팩 — 클로드 디자인 `weapon_pack`) 를 무기 디자인으로 넣기 (0.12.7).

  python3 tools/weapon_pack_import.py <weapon_pack.zip | weapon_pack 폴더>   # 가져오기 + 아이콘
  python3 tools/weapon_pack_import.py --icons                               # tools/weapon_art/*.java.json 에서 아이콘만 다시
  (그 뒤) python3 tools/weapon_models.py && python3 tools/java_fx_art.py && python3 tools/addon_particles.py

팩의 모양: assets/weapons/models/item/<디자인>.json (자바 아이템 모델 — elements · faces uv 0 ~ 16 · 텍스처 "#0" · light_emission ·
rotation 한 축 22.5°), textures/item/<디자인>.png (32 × 32 — 발광 맥동은 세로로 이은 프레임 + .png.mcmeta). 좌표 (팩 README):
위 +y · 날 · 타격면 -z · 손잡이 y 8 ~ 18 = 베드락 디자인 좌표 (tools/weapon_art/<디자인>.geo.json) + (14, 0, 8).

만드는 것 (tools/weapon_art):
- <디자인>.java.json   팩 모델 그대로 + `mmorpg` (자리 맞춤 — geo_tex.load_java 가 씀):
    anchor  팩 좌표의 손 자리 = grip (방패 handle) 상자 가운데
    to      그 자리가 갈 베드락 디자인 좌표 = 지금 디자인의 손 자리 (-6, 13, 0) — 활은 지금 활의 손잡이 (z 약 -3)
    scale   지금 디자인과 같은 크기 (y · z 대각선 길이 — 팩은 자바 상자 한계 -16 ~ 32 에 넣느라 디자인마다 줄이거나 내림: 그람 1/2 ·
            티르빙 0.42 · 간반테인 1/2 · 창 · 낫 · 홀은 손잡이를 9칸 아래로). 크기를 바꾸려면 이 값만 고치고 weapon_models.py
- <디자인>.java.png (+ .java.png.mcmeta)   텍스처 그대로 (프레임 · 반투명 픽셀 포함)
- <디자인>.icon.png 와 게임 아이템 아이콘 textures/item/<종류>/<무기 ID>.png — 모델에서 다시 그림 (옆모습 32 × 32, 끝이 왼쪽 위 ·
  날 (-z) 이 왼쪽 아래 — 지금 아이콘과 같은 방향, 활은 세움 (시위가 왼쪽), 방패는 앞면)
"""
import argparse
import glob
import json
import math
import os
import shutil
import sys
import tempfile
import zipfile

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo_tex  # noqa: E402
import java_model_view as jmv  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "weapon_art")
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "rpg")
PACK_NS = "weapons"
HAND = (-6.0, 13.0, 0.0)  # 베드락 디자인 좌표의 손 자리 (weapon 뼈 회전 중심)
PACK_OFFSET = np.array([14.0, 0.0, 8.0])  # 팩 좌표 = 베드락 디자인 좌표 + 이 값


def write_json(path, data, indent=1):
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(data, fh, ensure_ascii=False, indent=indent)
        fh.write("\n")


# ------------------------------------------------------------ 팩 읽기

def pack_root(src):
    """zip 이나 폴더 → assets/<PACK_NS> 폴더 (zip 은 임시 폴더에 풂)"""
    if os.path.isfile(src):
        tmp = tempfile.mkdtemp(prefix="weapon_pack_")
        with zipfile.ZipFile(src) as z:
            for info in z.infolist():
                parts = info.filename.split("/")
                if info.filename.startswith("/") or ".." in parts:
                    sys.exit(f"zip 안의 이상한 경로: {info.filename}")
            z.extractall(tmp)
        src = tmp
    hits = glob.glob(os.path.join(src, "**", "assets", PACK_NS), recursive=True)
    if not hits:
        sys.exit(f"{src}: assets/{PACK_NS} 가 없음")
    return hits[0]


def check_model(design, m):
    """팩 모델이 받을 수 있는 모양인지 (자바 아이템 모델 규칙 · 텍스처 하나)"""
    problems = []
    if m.get("parent"):
        problems.append(f"parent {m['parent']} — 상자 모델만 받음")
    els = m.get("elements") or []
    if not els:
        problems.append("elements 가 없음")
    for i, e in enumerate(els):
        where = f"상자 {i} ({e.get('name', '?')})"
        for k in ("from", "to"):
            if any(not (-16 <= float(x) <= 32) for x in e[k]):
                problems.append(f"{where}: {k} 가 -16 ~ 32 밖")
        r = e.get("rotation")
        if r and float(r.get("angle", 0)) not in geo_tex.ALLOWED_ANGLES:
            problems.append(f"{where}: 회전 {r.get('angle')}° (22.5° 단위 · ±45° 만)")
        for f, fc in (e.get("faces") or {}).items():
            if f not in geo_tex.FACES:
                problems.append(f"{where}: 면 {f}")
            if fc.get("texture") not in ("#0", "#texture", None):
                problems.append(f"{where}: 면 {f} 텍스처 {fc.get('texture')} — 한 장 (#0) 만")
            if int(fc.get("rotation", 0) or 0) % 90:
                problems.append(f"{where}: 면 {f} rotation {fc.get('rotation')}")
    return problems


def grip_of(m):
    """손 자리 상자: grip (방패 handle) — 띠 (band) 는 빼고 가장 긴 것"""
    els = m["elements"]
    cand = [e for e in els if e.get("name", "").startswith("grip") and "band" not in e.get("name", "")]
    if not cand:
        cand = [e for e in els if e.get("name", "") in ("handle",)]
    if not cand:
        return None
    e = max(cand, key=lambda e: abs(e["to"][1] - e["from"][1]))
    return jmv.Element(e).center()


# ------------------------------------------------------------ 자리 맞춤

def old_hand(design, old):
    """지금 디자인의 손 자리 (베드락 좌표): (-6, 13, z) — z 는 손 높이 (y 11 ~ 15) 에서 무기 축 (x -6) 을 지나는 상자들의 가운데 (긴 줄 · 자루 빼고).
    활 말고는 0 근처 → 0"""
    zs = []
    for c in old.cubes:
        (x0, y0, z0), (x1, y1, z1) = c.lo, c.hi
        if x0 - 0.01 <= HAND[0] <= x1 + 0.01 and y0 < 15 and y1 > 11 and (y1 - y0) < 20 and abs((z0 + z1) / 2) < 6:
            zs.append((z0 + z1) / 2)
    z = float(np.median(zs)) if zs else 0.0
    if abs(z) < 0.75:
        z = 0.0
    return [HAND[0], HAND[1], round(z, 3)]


def yz_diag(lo, hi):
    return math.hypot(hi[1] - lo[1], hi[2] - lo[2])


def transform_for(design, m, old):
    grip = grip_of(m)
    if grip is None:
        print(f"  경고 {design}: grip · handle 상자가 없음 — 팩 좌표의 손 자리 (8, 13, 8) 를 씀")
        grip = np.array([8.0, 13.0, 8.0])
    v = jmv.View(m["elements"], np.zeros((1, 1, 4)))
    lo, hi = v.bounds()
    olo = np.min([c.lo for c in old.cubes], axis=0)
    ohi = np.max([c.hi for c in old.cubes], axis=0)
    scale = yz_diag(olo, ohi) / yz_diag(lo, hi)
    return {
        "anchor": [round(float(x), 4) for x in grip],
        "to": old_hand(design, old),
        "scale": round(float(scale), 4),
        "note": "anchor (팩 좌표 손 자리 = grip 가운데) → to (베드락 디자인 좌표), 크기 scale 배 — 지금 디자인과 같은 y · z 대각선 "
                f"{yz_diag(olo, ohi):.1f} (팩 {yz_diag(lo, hi):.1f})",
    }


# ------------------------------------------------------------ 가져오기

def do_import(src):
    root = pack_root(src)
    meta = json.load(open(os.path.join(ART, "designs.json"), encoding="utf-8"))
    designs = sorted(meta["designs"])
    missing = []
    rows = []
    for d in designs:
        mp = os.path.join(root, "models", "item", f"{d}.json")
        tp = os.path.join(root, "textures", "item", f"{d}.png")
        if not (os.path.exists(mp) and os.path.exists(tp)):
            missing.append(d)
            continue
        m = json.load(open(mp, encoding="utf-8"))
        problems = check_model(d, m)
        if problems:
            sys.exit(f"{d}: " + "; ".join(problems[:6]))
        old = geo_tex.load(d, os.path.join(ART, f"{d}.geo.json"), os.path.join(ART, f"{d}.png"), java=False)
        tr = transform_for(d, m, old)
        out = {
            "credit": f"{m.get('credit', d)} — 사용자 디자인 리워크 (클로드 디자인 weapon_pack, tools/weapon_pack_import.py 가 가져옴)",
            "mmorpg": tr,
            "texture_size": m.get("texture_size", [32, 32]),
            "textures": {"0": f"rpg:item/model/{d}", "particle": f"rpg:item/model/{d}"},
            "elements": m["elements"],
            "display_pack": m.get("display", {}),
        }
        write_json(os.path.join(ART, f"{d}.java.json"), out)
        shutil.copy(tp, os.path.join(ART, f"{d}.java.png"))
        mc = tp + ".mcmeta"
        dst_mc = os.path.join(ART, f"{d}.java.png.mcmeta")
        if os.path.exists(mc):
            write_json(dst_mc, json.load(open(mc, encoding="utf-8")), indent=2)
        elif os.path.exists(dst_mc):
            os.remove(dst_mc)
        rows.append((d, tr, len(m["elements"]), sum(1 for e in m["elements"] if e.get("light_emission")), os.path.exists(mc)))
    for d, tr, n, g, anim in rows:
        print(f"{d:26s} 상자 {n:3d} 빛 {g:3d} {'맥동' if anim else '    '}  anchor {tr['anchor']} → {tr['to']}  scale {tr['scale']:.3f}")
    extra = sorted(os.path.basename(p)[:-5] for p in glob.glob(os.path.join(root, "models", "item", "*.json")) if os.path.basename(p)[:-5] not in designs)
    if extra:
        print("팩에만 있는 디자인 (무기 ID 연결이 없어 넣지 않음):", ", ".join(extra))
    if missing:
        print("팩에 없는 디자인 (지금 것 그대로):", ", ".join(missing))
    print(f"imported {len(rows)} designs → {os.path.relpath(ART, ROOT)}")


# ------------------------------------------------------------ 아이콘 (지금 아이콘과 같은 모양: 32 × 32 · 바깥 테두리)

ICON = 32
SS = 5
EDGE = (24, 14, 22, 255)


def best_frame(strip, mcmeta):
    """아이콘에 쓸 프레임: 맥동 텍스처는 가장 밝은 프레임"""
    w = strip.size[0]
    n = strip.size[1] // w if mcmeta else 1
    frames = [np.array(strip.crop((0, i * w, w, (i + 1) * w))) for i in range(max(1, n))]
    def bright(f):
        a = f[:, :, 3] > 0
        return f[:, :, :3][a].mean() if a.any() else 0
    return max(frames, key=bright)


def icon_of(design):
    jm = json.load(open(os.path.join(ART, f"{design}.java.json"), encoding="utf-8"))
    strip = Image.open(os.path.join(ART, f"{design}.java.png")).convert("RGBA")
    mc = os.path.join(ART, f"{design}.java.png.mcmeta")
    frame = best_frame(strip, os.path.exists(mc))
    v = jmv.View(jm["elements"], frame)
    lo, hi = v.bounds()
    kind = design.split("_")[0]
    if kind == "shield":
        mode, from_plus = "front", False  # 앞면 (-x 쪽 바깥 판) — 오른쪽 = +z
    elif kind == "bow":
        mode, from_plus = "upright", True  # 세움 — 오른쪽 = -z (시위가 왼쪽)
    else:
        mode, from_plus = "diag", True
    ym, zm = (lo[1] + hi[1]) / 2, (lo[2] + hi[2]) / 2
    length, width = hi[1] - lo[1], hi[2] - lo[2]
    if mode == "diag":
        scale = min(41.0 / length, 30.0 / max(1.0, width), 1.6)
    else:
        scale = min(30.0 / length, 30.0 / max(1.0, width), 1.6)
    r2 = 1 / math.sqrt(2)
    us, vs = [], []
    for py in range(ICON):
        for px in range(ICON):
            for sy in range(SS):
                for sx in range(SS):
                    us.append(px + (sx + 0.5) / SS - ICON / 2)
                    vs.append(py + (sy + 0.5) / SS - ICON / 2)
    u = np.array(us)
    w_ = np.array(vs)
    if mode == "diag":  # 끝 (+y) 이 왼쪽 위 · -z 가 왼쪽 아래 (weapon_art_more.icon 과 같은 방향)
        Y = ym + (-r2 * u - r2 * w_) / scale
        Z = zm + (r2 * u - r2 * w_) / scale
    elif mode == "upright":
        Y = ym - w_ / scale
        Z = zm - u / scale
    else:
        Y = ym - w_ / scale
        Z = zm + u / scale
    col, glow, _, front_el = jmv.grid_trace(v, Y, Z, from_plus_x=from_plus, with_element=True)
    col = col.reshape(ICON, ICON, SS * SS, 4)
    glow = glow.reshape(ICON, ICON, SS * SS)
    front_el = front_el.reshape(ICON, ICON, SS * SS)
    # 가는 상자 (화면에서 1.5 점보다 가는 것 — 활 시위 · 가는 자루 장식) 는 한 표본만 닿아도 그림 (안 그러면 사라짐)
    thin = set()
    for i, el in enumerate(v.els):
        pts = el.corners()
        dy = (pts[:, 1].max() - pts[:, 1].min()) * scale
        dz = (pts[:, 2].max() - pts[:, 2].min()) * scale
        if min(dy, dz) < 1.5 and max(dy, dz) >= 3:
            thin.add(i)
    img = Image.new("RGBA", (ICON, ICON), (0, 0, 0, 0))
    for py in range(ICON):
        for px in range(ICON):
            votes = {}
            cov = 0.0
            thin_hit = None
            for k in range(SS * SS):
                r, g, b, a = col[py, px, k]
                if a < 96:
                    continue
                cov += 1
                key = (int(round(r)), int(round(g)), int(round(b)))
                gl = glow[py, px, k]
                votes[key] = votes.get(key, 0.0) + (1.0 + 0.7 * gl)
                votes.setdefault(("glow", key), gl)
                if front_el[py, px, k] in thin:
                    thin_hit = key
            if cov < 0.3 * SS * SS:
                if thin_hit is None or cov < 2:
                    continue
                votes = {thin_hit: 1.0, ("glow", thin_hit): votes[("glow", thin_hit)]}
            best = max((k for k in votes if k[0] != "glow"), key=lambda k: votes[k])
            r, g, b = best
            if votes[("glow", best)] > 0.5:
                r, g, b = (min(255, int(x * 1.08 + 14)) for x in (r, g, b))
            img.putpixel((px, py), (r, g, b, 255))
    out = Image.new("RGBA", (ICON, ICON), (0, 0, 0, 0))
    src = img.load()
    for py in range(ICON):
        for px in range(ICON):
            if not src[px, py][3] and any(0 <= px + dx < ICON and 0 <= py + dy < ICON and src[px + dx, py + dy][3]
                                         for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                out.putpixel((px, py), EDGE)
    out.alpha_composite(img)
    return out


def do_icons():
    meta = json.load(open(os.path.join(ART, "designs.json"), encoding="utf-8"))
    n_design = n_item = 0
    for d in sorted(meta["designs"]):
        if not os.path.exists(os.path.join(ART, f"{d}.java.json")):
            continue
        ic = icon_of(d)
        ic.save(os.path.join(ART, f"{d}.icon.png"))
        n_design += 1
        for wid, des in meta["weapons"].items():
            if des != d:
                continue
            name = wid.split(":", 1)[1]
            t = name.split("_l")[0]
            path = os.path.join(ASSETS, "textures", "item", t, f"{name}.png")
            if os.path.exists(path):  # 모드에 있는 무기만 (아이콘이 이미 있는 것)
                ic.save(path)
                n_item += 1
    print(f"icons: {n_design} designs, {n_item} weapon items")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("src", nargs="?", help="weapon_pack zip 이나 폴더")
    ap.add_argument("--icons", action="store_true", help="아이콘만 다시 (가져오기 없이)")
    a = ap.parse_args()
    if a.src:
        do_import(a.src)
    elif not a.icons:
        ap.error("weapon_pack zip · 폴더를 주거나 --icons")
    do_icons()


if __name__ == "__main__":
    main()
