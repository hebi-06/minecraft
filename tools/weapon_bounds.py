#!/usr/bin/env python3
"""0.48.0 무기 입체 모델의 크기 (서버가 무기 스킬의 입체 효과 물체 — 던진 묠니르 · 떨어지는 궁니르 · 거대한 크로노스의 낫 … — 를 놓을 때 씀):
무기 아이템 (assets/rpg/items/<무기 ID>.json) 의 손 · 땅에 쓰는 3D 모델 (assets/rpg/models/item/model/<디자인>.json — 사용자 디자인 그대로) 의
상자를 모두 돌려 (요소 회전 포함) 잰 크기 → src/main/resources/mmorpg/weapon_bounds.json.

좌표: 아이템을 손 · 땅 자세 없이 (ItemDisplayContext.NONE) 그릴 때의 칸 — 모델 좌표 / 16 − 0.5 (자바 ItemStackRenderState 가 −0.5 를 옮김).
+Y = 무기 끝 (칼끝 · 창끝 · 망치 머리), 손잡이는 −Y 쪽. 무기마다 {"min": [x, y, z], "max": [x, y, z], "hand": 3인칭 손에 든 크기 배율 (display),
"design": 디자인} — 손에 든 것과 같은 크기로 그리려면 hand 배.

  python3 tools/weapon_bounds.py          # 씀
  python3 tools/weapon_bounds.py --check  # 쓸 것이 없는지
"""
import glob
import json
import math
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ITEMS = os.path.join(ROOT, "src/main/resources/assets/rpg/items")
MODELS = os.path.join(ROOT, "src/main/resources/assets/rpg/models")
OUT = os.path.join(ROOT, "src/main/resources/mmorpg/weapon_bounds.json")


def fallback_model(item):
    m = item.get("model", {})
    if m.get("type") == "minecraft:select":
        m = m.get("fallback", {})
    return m.get("model") if m.get("type") == "minecraft:model" else None


def rotate(p, rot):
    if not rot or not rot.get("angle"):
        return p
    o = rot.get("origin", [8, 8, 8])
    a = math.radians(rot["angle"])
    c, s = math.cos(a), math.sin(a)
    x, y, z = (p[i] - o[i] for i in range(3))
    ax = rot.get("axis", "y")
    if ax == "x":
        y, z = y * c - z * s, y * s + z * c
    elif ax == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return [x + o[0], y + o[1], z + o[2]]


def bounds(model_path):
    o = json.load(open(model_path, encoding="utf-8"))
    hand = o.get("display", {}).get("thirdperson_righthand", {}).get("scale", [1, 1, 1])[0]
    lo = [1e9] * 3
    hi = [-1e9] * 3
    for e in o.get("elements", []):
        f, t = e["from"], e["to"]
        for cx in (f[0], t[0]):
            for cy in (f[1], t[1]):
                for cz in (f[2], t[2]):
                    p = rotate([cx, cy, cz], e.get("rotation"))
                    for i in range(3):
                        lo[i] = min(lo[i], p[i])
                        hi[i] = max(hi[i], p[i])
    if lo[0] > hi[0]:
        return None
    return [round(v / 16 - 0.5, 4) for v in lo], [round(v / 16 - 0.5, 4) for v in hi], round(hand, 4)


def build():
    out = {}
    cache = {}
    for p in sorted(glob.glob(os.path.join(ITEMS, "*.json"))):
        mid = fallback_model(json.load(open(p, encoding="utf-8")))
        if not mid or "/model/" not in mid:
            continue
        ns, path = mid.split(":", 1)
        if mid not in cache:
            mp = os.path.join(ROOT, "src/main/resources/assets", ns, "models", path + ".json")
            cache[mid] = bounds(mp) if os.path.exists(mp) else None
        b = cache[mid]
        if b is None:
            continue
        out["rpg:" + os.path.basename(p)[:-5]] = {"min": b[0], "max": b[1], "hand": b[2], "design": path.rsplit("/", 1)[1]}
    return out


def main():
    data = build()
    text = json.dumps({"_comment": __doc__.strip().splitlines()[0], "weapons": data}, ensure_ascii=False, indent=1, sort_keys=False) + "\n"
    old = open(OUT, encoding="utf-8").read() if os.path.exists(OUT) else None
    if "--check" in sys.argv:
        if old != text:
            print("weapon_bounds.json: out of date — python3 tools/weapon_bounds.py")
            return 1
        print("weapon bounds: %d weapons ok" % len(data))
        return 0
    if old != text:
        open(OUT, "w", encoding="utf-8").write(text)
    print("weapon bounds: %d weapons · %d designs" % (len(data), len({v["design"] for v in data.values()})))
    return 0


if __name__ == "__main__":
    sys.exit(main())
