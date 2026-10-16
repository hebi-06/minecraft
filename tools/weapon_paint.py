#!/usr/bin/env python3
"""칠하기용 무기 디자인: 팔레트 텍스처 (16 × 16 — 면마다 한 칸, 여러 면이 같은 칸을 같이 써서 한 면만 칠할 수 없음) 를 상자 면마다 따로 칸이 있는
텍스처로 바꿈 (모델 1단위 = px 픽셀, 지금 색을 칠해 둠). 모양 · 뼈대 · 회전 · fx 로케이터는 그대로라 자바 모델이 같게 나옴 (geo_tex_selftest 가 확인).

- 상자마다 면 6개를 상자 UV 처럼 펼쳐 붙임: 윗줄 위 (U) · 아래 (D), 아랫줄 동 (E) · 북 (N) · 서 (W) · 남 (S). 상자 사이는 1픽셀 띄움
- 텍스처는 정사각형 (16 · 32 · 64 · 128 …) 가운데 다 들어가는 가장 작은 것
- 면 UV 크기는 양수 (뒤집지 않음) — 원래 면이 뒤집힌 UV 였으면 픽셀을 뒤집어 옮겨 보이는 모습은 같음
- 방패의 왼손 weapon_off 뼈는 뺌 (애드온 전용 — 자바 모드는 weapon 뼈만 씀)
- <디자인>_텍스처_칸.png: 칸 지도 (크게 · 면마다 테두리 · N E S W U D 글자) — 그림 프로그램에서 칠할 때 봄

  python3 tools/weapon_paint.py <출력 폴더> <디자인> [...] [--px 2]
"""
import argparse
import copy
import json
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo_tex  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "weapon_art")
LETTER = {"north": "N", "east": "E", "south": "S", "west": "W", "up": "U", "down": "D"}
LEGEND = "N 북 (-z · 날 쪽)   S 남 (+z)   E 동 (-x)   W 서 (+x)   U 위   D 아래"


def _members(geo, bone="weapon"):
    """weapon 뼈와 그 아래 뼈 (geo_tex.load 와 같은 순서 — 상자 순서가 Model.cubes 와 맞음)"""
    bones = {b["name"].lower(): b for b in geo["bones"]}

    def under(b):
        seen = set()
        while b is not None and b["name"].lower() not in seen:
            if b["name"].lower() == bone:
                return True
            seen.add(b["name"].lower())
            b = bones.get((b.get("parent") or "").lower())
        return False

    return [b for b in geo["bones"] if under(b)]


def _dim(x, px):
    return max(1, int(math.floor(x * px + 0.5)))


def _net(size, faces, px):
    """상자 하나의 펼친 칸: 면 → (x, y, w, h) 펼침 안 픽셀 자리, 펼침 (너비, 높이)"""
    W, H, D = (_dim(abs(float(v)), px) for v in size)
    pos = {"up": (D, 0, W, D), "down": (D + W, 0, W, D), "east": (0, D, D, H), "north": (D, D, W, H), "west": (D + W, D, D, H),
           "south": (D + W + D, D, W, H)}
    return {f: pos[f] for f in faces}, (2 * (D + W), D + H)


def _pack(sizes, gutter=1):
    """선반 쌓기: 높은 것부터 줄마다 왼쪽 → 오른쪽. (가로 · 세로) 목록 → (정사각형 한 변, 자리 목록)"""
    order = sorted(range(len(sizes)), key=lambda i: (-sizes[i][1], -sizes[i][0], i))
    side = 16
    while True:
        x = y = row = 0
        pos = [None] * len(sizes)
        ok = True
        for i in order:
            w, h = sizes[i]
            if w > side:
                ok = False
                break
            if x + w > side:
                x, y, row = 0, y + row + gutter, 0
            pos[i] = (x, y)
            x += w + gutter
            row = max(row, h)
        if ok and y + row <= side:
            return side, pos
        side *= 2
        if side > 4096:
            raise ValueError("텍스처가 4096 을 넘음")


def paintable(design, art=ART, px=2):
    """→ (geo JSON dict, 텍스처 Image, 칸 목록 [(상자 번호, 면, (x, y, w, h))])"""
    path = os.path.join(art, f"{design}.geo.json")
    raw = json.load(open(path, encoding="utf-8"))
    m = geo_tex.load(design, path, os.path.join(art, f"{design}.png"), java=False)  # 베드락 디자인 파일 그대로 (자바 모델 디자인은 칠하기 묶음 밖)
    out = copy.deepcopy(raw)
    geo = out["minecraft:geometry"][0]
    geo["bones"] = [b for b in geo["bones"] if b["name"].lower() != "weapon_off"]
    cubes = [c for b in _members(geo) for c in b.get("cubes", [])]
    if len(cubes) != len(m.cubes):
        raise ValueError(f"{design}: 상자 수가 다름 {len(cubes)} vs {len(m.cubes)}")
    nets = [_net(c["size"], mc.faces, px) for c, mc in zip(cubes, m.cubes)]
    side, pos = _pack([n[1] for n in nets])
    img = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    cells = []
    for i, (c, mc, (faces, _), (ox, oy)) in enumerate(zip(cubes, m.cubes, nets, pos)):
        uv = {}
        for f, (fx, fy, fw, fh) in faces.items():
            X, Y = ox + fx, oy + fy
            r = mc.faces[f]
            for j in range(fh):
                for k in range(fw):
                    img.putpixel((X + k, Y + j), m.face_pixel(r, (k + 0.5) / fw, (j + 0.5) / fh))
            uv[f] = {"uv": [X, Y], "uv_size": [fw, fh]}
            cells.append((i, f, (X, Y, fw, fh)))
        c["uv"] = uv
        c.pop("mirror", None)
    desc = geo.setdefault("description", {})
    desc["texture_width"] = side
    desc["texture_height"] = side
    return out, img, cells


def cell_map(img, cells, font=None):
    """칸 지도: 크게 (체크무늬 위) · 면마다 테두리 · 글자"""
    side = img.size[0]
    k = max(3, min(12, 1024 // side))
    big = img.resize((side * k, side * k), Image.NEAREST)
    top = 58
    out = Image.new("RGBA", (max(side * k, 760), side * k + top), (34, 36, 44, 255))
    check = Image.new("RGBA", big.size, (0, 0, 0, 0))
    cd = ImageDraw.Draw(check)
    s = max(4, k * 2)
    for y in range(0, big.size[1], s):
        for x in range(0, big.size[0], s):
            cd.rectangle([x, y, x + s - 1, y + s - 1], fill=(70, 72, 82, 255) if (x // s + y // s) % 2 else (56, 58, 66, 255))
    check.alpha_composite(big)
    out.alpha_composite(check, (0, top))
    d = ImageDraw.Draw(out)
    if font:
        d.text((8, 6), f"텍스처 {side}×{side} · {LEGEND}", font=font(15), fill=(235, 235, 240, 255))
        d.text((8, 30), "반투명한 칸 = 빛나는 칸 (알파 102 — 그대로 두면 밤에도 빛남) · 체크무늬 = 빈 곳", font=font(14),
               fill=(190, 192, 200, 255))
    for _, f, (x, y, w, h) in cells:
        x0, y0, x1, y1 = x * k, top + y * k, (x + w) * k - 1, top + (y + h) * k - 1
        d.rectangle([x0, y0, x1, y1], outline=(16, 17, 22, 255), width=1)
        if font and w * k >= 16 and h * k >= 16:
            fs = max(9, min(16, min(w, h) * k // 2))
            d.text(((x0 + x1) / 2, (y0 + y1) / 2), LETTER[f], font=font(fs), fill=(255, 255, 255, 230), anchor="mm",
                   stroke_width=2, stroke_fill=(0, 0, 0, 200))
    return out


def write(design, out_dir, art=ART, px=2, font=None):
    geo, img, cells = paintable(design, art, px)
    with open(os.path.join(out_dir, f"{design}.geo.json"), "w", encoding="utf-8") as fh:
        json.dump(geo, fh, ensure_ascii=False, indent=1)
        fh.write("\n")
    img.save(os.path.join(out_dir, f"{design}.png"))
    cell_map(img, cells, font).save(os.path.join(out_dir, f"{design}_텍스처_칸.png"))
    return img.size[0], len(cells)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("out")
    ap.add_argument("designs", nargs="+")
    ap.add_argument("--px", type=int, default=2, help="모델 1단위당 픽셀")
    a = ap.parse_args()
    os.makedirs(a.out, exist_ok=True)
    import weapon_preview  # noqa: E402

    for d in a.designs:
        side, n = write(d, a.out, px=a.px, font=weapon_preview.font)
        print(f"{d}: 텍스처 {side}×{side}, 면 {n}")


if __name__ == "__main__":
    main()
