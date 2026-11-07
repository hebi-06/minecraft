#!/usr/bin/env python3
"""모드 없는 클라이언트의 몬스터 · 보스 · 소품 모델 (2026-11-07 로드맵 1번 2단계) — 베드락 모델 (assets/mmorpg/bedrock/entities.json)
의 뼈마다 자바 아이템 모델. 서버 (mmorpg.fabric.vanilla.VanillaModels) 가 뼈마다 아이템 표시 엔티티를 띄우고 동작 (core bedrock.Animator —
클라이언트 BedrockRenderer 와 같은 계산) 으로 틱마다 변환을 줌.

좌표 (j 공간 — weapon_models.to3 와 같은 x 거울: j = (-x, y, z) 베드락, 픽셀): 뼈 가운데 (pivot) 에서 본 상자.
모델 좌표 m = k · local + c0 (자바 상자 한계 -16 ~ 32 안 — k ≤ 1, 상자 묶음 가운데가 모델 가운데 8, 8, 8 에).
아이템 표시는 m 을 (m / 16 - 0.5) 블록에 그리므로 서버의 표시 변환 = 몸 · 뼈 사슬 (블록) · S(1 / (16 k)) · T(8 - c0) (픽셀).
면 UV 는 geo_tex.box_faces (베드락 면 이름 = 자바 면 이름 — x 거울로 east · west 가 맞음) 를 텍스처 UV 단위 → 0 ~ 16 으로.

만드는 것 (src/main/resources — 손으로 고치지 않음):
- assets/rpg/models/item/vmob/<모델>/<뼈>.json — 뼈의 상자 (텍스처 #0 = 그 모델의 첫 텍스처)
- assets/rpg/models/item/vmob/<모델>/<뼈>_glow.json — 빛나는 칸 겹 (같은 상자 0.02 픽셀 부풀림 · light_emission 15 · _emissive 텍스처) — 빛나는 텍스처만
- assets/rpg/models/item/vmob/<모델>/<텍스처>/<뼈>[_glow].json — 텍스처가 여럿인 엔티티의 다른 텍스처 (parent + 텍스처만)
- assets/rpg/items/vmob/... — 아이템 정의 (위 모델 하나씩)
- assets/rpg/textures/item/vmob/<텍스처>.png (+ _emissive.png) — assets/mmorpg/textures/entity 의 것을 아이템 그림 모음 (atlas) 안으로 복사
- mmorpg/vanilla_models.json — 서버용 목록 (모델마다 뼈 → 모델 경로 · k · c0 · 빛 겹, 텍스처 경로 → 이름)

  python3 tools/vanilla_models.py           # 만들기
  python3 tools/vanilla_models.py --check   # 만들지 않고 숫자만
"""
import argparse
import json
import math
import os
import re
import shutil
import sys

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo_tex  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
SRC = os.path.join(RES, "assets", "mmorpg", "bedrock", "entities.json")
TEX_SRC = os.path.join(RES, "assets", "mmorpg")
OUT_MODELS = os.path.join(RES, "assets", "rpg", "models", "item", "vmob")
OUT_ITEMS = os.path.join(RES, "assets", "rpg", "items", "vmob")
OUT_TEX = os.path.join(RES, "assets", "rpg", "textures", "item", "vmob")
OUT_MANIFEST = os.path.join(RES, "mmorpg", "vanilla_models.json")

EXTENT_MIN, EXTENT_MAX = -16.0, 32.0
GLOW_INFLATE = 0.02


def slug(s):
    s = re.sub(r"[^a-z0-9_]+", "_", s.lower()).strip("_")
    return s or "x"


def model_path(geo_id):
    """geometry.rpg.boss.surtr → boss_surtr"""
    short = geo_id.split("geometry.", 1)[-1]
    if short.startswith("rpg."):
        short = short[4:]
    return slug(short)


def tex_slug(tex_path):
    """textures/entity/rpg/bosses/surtr → bosses_surtr"""
    p = tex_path.split("textures/entity/", 1)[-1]
    if p.startswith("rpg/"):
        p = p[4:]
    return slug(p)


def to3(v):
    return np.array([-float(v[0]), float(v[1]), float(v[2])])


def bone_cubes(geo, b):
    """뼈의 상자 → [(f, t, faces)] j 공간 · 뼈 가운데에서 (픽셀). 상자 회전은 없음 (모든 모델 — 있으면 멈춤)"""
    P = to3(b.get("pivot") or [0, 0, 0])
    out = []
    for ci, c in enumerate(b.get("cubes") or []):
        rot = c.get("rotation")
        if rot and any(abs(float(x)) > 1e-9 for x in rot):
            raise SystemExit(f"{geo['description']['identifier']} 뼈 {b['name']} 상자 {ci}: 상자 회전은 아직 받지 않음")
        o = [float(x) for x in c["origin"]]
        s = [float(x) for x in c["size"]]
        g = float(c.get("inflate", 0) or 0)
        lo = [o[i] - g for i in range(3)]
        hi = [o[i] + s[i] + g for i in range(3)]
        uv = c.get("uv")
        if isinstance(uv, dict):
            faces = {}
            for f in geo_tex.FACES:
                fd = uv.get(f)
                if not fd:
                    continue
                fu, fv = (float(x) for x in fd["uv"])
                fw, fh = (float(x) for x in fd.get("uv_size", [1, 1]))
                faces[f] = (fu, fv, fw, fh)
        else:
            u, v = (float(x) for x in (uv or [0, 0]))
            faces = geo_tex.box_faces(u, v, s, bool(c.get("mirror", b.get("mirror", False))))
        a = to3(lo) - P
        z = to3(hi) - P
        out.append((np.minimum(a, z), np.maximum(a, z), faces))
    return out


def fit(cubes):
    lo = np.full(3, 1e9)
    hi = np.full(3, -1e9)
    for f, t, _ in cubes:
        lo, hi = np.minimum(lo, f), np.maximum(hi, t)
    extent = float(np.max(hi - lo))
    room = EXTENT_MAX - EXTENT_MIN - 1.0
    k = min(1.0, room / max(extent, 1e-6))
    c0 = 8.0 - k * (lo + hi) / 2
    return k, c0


def elements(cubes, k, c0, tw, th, glow_mask=None, inflate=0.0, light=0):
    su, sv = 16.0 / tw, 16.0 / th
    els = []
    for f, t, faces in cubes:
        mf = k * (f - inflate) + c0
        mt = k * (t + inflate) + c0
        assert np.all(mf >= EXTENT_MIN - 1e-6) and np.all(mt <= EXTENT_MAX + 1e-6), (mf, mt)
        fs = {}
        for name, (u, v, w, h) in faces.items():
            if glow_mask is not None and not glow_mask(u, v, w, h):
                continue
            fs[name] = {"uv": [round(u * su, 5), round(v * sv, 5), round((u + w) * su, 5), round((v + h) * sv, 5)], "texture": "#0"}
        if not fs:
            continue
        el = {"from": [round(float(x), 4) for x in mf], "to": [round(float(x), 4) for x in mt], "faces": fs}
        if light:
            el["light_emission"] = light
        els.append(el)
    return els


def glow_checker(img, tw, th):
    """면 사각형 안에 빛나는 픽셀 (알파 > 0) 이 있는지 — _emissive 텍스처"""
    a = np.array(img.convert("RGBA"))[:, :, 3]
    H, W = a.shape
    sx, sy = W / tw, H / th

    def has(u, v, w, h):
        x0, x1 = sorted((u * sx, (u + w) * sx))
        y0, y1 = sorted((v * sy, (v + h) * sy))
        x0, y0 = max(0, int(math.floor(x0))), max(0, int(math.floor(y0)))
        x1, y1 = min(W, int(math.ceil(x1))), min(H, int(math.ceil(y1)))
        return x1 > x0 and y1 > y0 and bool((a[y0:y1, x0:x1] > 0).any())
    return has


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as fh:
        json.dump(obj, fh, ensure_ascii=False, separators=(",", ":"))
        fh.write("\n")


def item_def(path, model_id):
    write_json(path, {"model": {"type": "minecraft:model", "model": model_id}})


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    data = json.load(open(SRC, encoding="utf-8"))
    models = data["models"]
    emissive = set(data.get("emissive") or [])
    # 모델마다 쓰는 텍스처 (엔티티 순서 — 첫 텍스처가 기본)
    tex_of = {}
    for eid, e in data["entities"].items():
        geos = list((e.get("geometry") or {}).values())
        texs = list((e.get("textures") or {}).values())
        for g in geos:
            lst = tex_of.setdefault(g, [])
            for t in texs:
                if t not in lst:
                    lst.append(t)

    if not args.check:
        for d in (OUT_MODELS, OUT_ITEMS, OUT_TEX):
            if os.path.isdir(d):
                shutil.rmtree(d)

    manifest = {"models": {}, "textures": {}}
    n_models = n_bones = n_glow = n_cubes = 0
    used_tex = set()
    for gid, geo in sorted(models.items()):
        texs = tex_of.get(gid)
        if not texs:
            continue
        desc = geo["description"]
        tw, th = float(desc.get("texture_width", 64)), float(desc.get("texture_height", 64))
        mp = model_path(gid)
        bones_out = {}
        names = set()
        glow_imgs = {}
        for t in texs:
            gp = os.path.join(TEX_SRC, t + "_emissive.png")
            if t in emissive and os.path.exists(gp):
                glow_imgs[t] = Image.open(gp)
        for b in geo["bones"]:
            cubes = bone_cubes(geo, b)
            if not cubes:
                continue
            bn = slug(b["name"])
            while bn in names:
                bn += "_"
            names.add(bn)
            k, c0 = fit(cubes)
            base_tex = texs[0]
            els = elements(cubes, k, c0, tw, th)
            n_cubes += len(cubes)
            rel = f"{mp}/{bn}"
            glow = False
            if not args.check:
                write_json(os.path.join(OUT_MODELS, rel + ".json"),
                           {"textures": {"0": f"rpg:item/vmob/{tex_slug(base_tex)}", "particle": f"rpg:item/vmob/{tex_slug(base_tex)}"},
                            "elements": els})
                item_def(os.path.join(OUT_ITEMS, rel + ".json"), f"rpg:item/vmob/{rel}")
            # 빛 겹: 기본 텍스처가 빛나면 (다른 텍스처는 같은 상자 · 그 텍스처의 빛)
            if glow_imgs:
                masks = [glow_checker(img, tw, th) for img in glow_imgs.values()]
                gels = elements(cubes, k, c0, tw, th, glow_mask=lambda u, v, w, h: any(m(u, v, w, h) for m in masks),
                                inflate=GLOW_INFLATE / max(k, 1e-6), light=15)
                if gels:
                    glow = True
                    n_glow += 1
                    if not args.check:
                        gt = base_tex if base_tex in glow_imgs else next(iter(glow_imgs))
                        write_json(os.path.join(OUT_MODELS, rel + "_glow.json"),
                                   {"textures": {"0": f"rpg:item/vmob/{tex_slug(gt)}_emissive", "particle": f"rpg:item/vmob/{tex_slug(gt)}_emissive"},
                                    "elements": gels})
                        item_def(os.path.join(OUT_ITEMS, rel + "_glow.json"), f"rpg:item/vmob/{rel}_glow")
            # 다른 텍스처
            if not args.check:
                for t in texs[1:]:
                    ts = tex_slug(t)
                    write_json(os.path.join(OUT_MODELS, f"{mp}/{ts}/{bn}.json"),
                               {"parent": f"rpg:item/vmob/{rel}", "textures": {"0": f"rpg:item/vmob/{ts}", "particle": f"rpg:item/vmob/{ts}"}})
                    item_def(os.path.join(OUT_ITEMS, f"{mp}/{ts}/{bn}.json"), f"rpg:item/vmob/{mp}/{ts}/{bn}")
                    if glow:
                        g = f"rpg:item/vmob/{ts}_emissive" if t in glow_imgs else f"rpg:item/vmob/{tex_slug(base_tex)}_emissive"
                        write_json(os.path.join(OUT_MODELS, f"{mp}/{ts}/{bn}_glow.json"),
                                   {"parent": f"rpg:item/vmob/{rel}_glow", "textures": {"0": g, "particle": g}})
                        item_def(os.path.join(OUT_ITEMS, f"{mp}/{ts}/{bn}_glow.json"), f"rpg:item/vmob/{mp}/{ts}/{bn}_glow")
            bones_out[b["name"]] = {"model": bn, "k": round(k, 6), "c0": [round(float(x), 5) for x in c0], "glow": glow}
            n_bones += 1
        for t in texs:
            used_tex.add(t)
        manifest["models"][gid] = {"path": mp, "textures": [tex_slug(t) for t in texs], "bones": bones_out}
        n_models += 1

    for t in sorted(used_tex):
        ts = tex_slug(t)
        manifest["textures"][t] = ts
        if args.check:
            continue
        src = os.path.join(TEX_SRC, t + ".png")
        os.makedirs(OUT_TEX, exist_ok=True)
        shutil.copy(src, os.path.join(OUT_TEX, ts + ".png"))
        gp = os.path.join(TEX_SRC, t + "_emissive.png")
        if t in emissive and os.path.exists(gp):
            shutil.copy(gp, os.path.join(OUT_TEX, ts + "_emissive.png"))

    if not args.check:
        write_json(OUT_MANIFEST, manifest)
    print(f"models {n_models}, bones {n_bones} (glow {n_glow}), cubes {n_cubes}, textures {len(used_tex)}")


if __name__ == "__main__":
    main()
