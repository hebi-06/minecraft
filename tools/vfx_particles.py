#!/usr/bin/env python3
"""2026-11-12 모드 없는 클라이언트의 파티클 다리 (vfx/PBridge — 사용자 "룬 스킬 다 만들어보자", 서버에 접속해 리소스만 받으면 됨):
모드 클라이언트가 그리는 애드온 파티클 (rpg:* — assets/mmorpg/addon_particles.json) 과 2D 효과 판 (Fx3d 의 slash_* 카드) 을 모드 없는 클라이언트에서는
서버가 글자 표시 엔티티로 띄움. 이 도구는 그 그림을 글꼴 rpg:vfxp 의 글자로 옮김.

  python3 tools/vfx_particles.py          # 씀
  python3 tools/vfx_particles.py --check  # 쓸 것이 없는지

만드는 것
- src/main/resources/assets/rpg/textures/font/vfxp/<판>.png   파티클 그림의 칸 (uv 칸 하나 = 글자 하나 — 긴 변 CAP 픽셀까지 줄임, 알파를 곱해 줄여 가장자리가 검지 않게)
- src/main/resources/assets/rpg/font/vfxp.json                 글꼴 (판마다 bitmap — 높이 H · ascent H/2 − 2: 글자 가운데가 엔티티 자리, 뒤로 옮김 space)
- src/main/resources/mmorpg/vfx_particles.json                 서버 목록: 파티클 ID → 판, 판 → 첫 글자 · 칸 수 · uv 칸 크기 · 글자 너비 (advance), 카드 → 칸마다 겹

글자는 그림 그대로 (흰 그림 · 칠한 그림) — 서버가 파티클의 색 (식) 을 글자 색으로 곱함 (파티클과 같음).
칸마다 오른쪽 위 구석에 알파 1 점: 마인크래프트가 비트맵 글자의 너비를 빈 열만큼 줄이지 않게 (너비 = 칸 전체 → 가운데가 맞음 · 서버가 너비를 앎).
직업 스킬 그림 (rfx_j*) — 2026-11-14 모두 (사용자 "우리 만든 참격 이펙트는 어디가고 왜 다 파티클로 만들고 있음?" → "1번부터 진행": 전에는 룬이 쓰는 여섯 (RUNE_J)
만 옮겨 직업 스킬의 참격 판 · 큰 검기 · 눈앞 베기 · 회전 베기가 모드 없는 클라이언트에서 통째로 빠졌음). 쓰지 않는 그림 (DEAD_J) 은 뺌. 직업 그림은 칸의 긴 변을
줄여 넣음 (cap_for — 참격 · 검기 무리 HI_J 는 룬과 같은 128, 3D 몹을 못 세울 때만 쓰는 그림 LOW_J 는 64, 나머지는 그림 크기의 0.45 · 56 ~ 96) — 글꼴 그림은
모두 메모리에 올라감. 직업 참격 판 (slash_job*) 도 카드에.
2026-11-14 직업 스킬의 2D 물체 판 (JobFx3d.CARDS — card_*) 도 카드에 · 카드마다 움직임 (모델 JSON anims → 구간마다 보이는 칸 — cardAnims) · 입체 모델
(사신 황천문의 명계의 문 · 망자의 손) 은 앞에서 본 그림으로 구워 (tools/vfx_bake3d.py — 칸마다 한 겹 mat 2, 보는 사람 쪽으로 돌림 cardBill).
"""
import json
import os
import sys
from io import BytesIO

import numpy as np
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import vfx_bake3d  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src/main/resources")
DEFS = os.path.join(RES, "assets/mmorpg/addon_particles.json")
PARTICLE_TEX = os.path.join(RES, "assets/mmorpg/textures/particle")
MODEL_DIR = os.path.join(RES, "assets/mmorpg/fxmodel")
OUT_TEX = os.path.join(RES, "assets/rpg/textures/font/vfxp")
OUT_FONT = os.path.join(RES, "assets/rpg/font/vfxp.json")
OUT_INDEX = os.path.join(RES, "mmorpg/vfx_particles.json")

H = 64  # 글자 높이 (글꼴 단위 — 글자 표시 엔티티 1 단위 = 0.025칸 → 1.6칸, 크기는 변환으로)
CAP = 128  # 칸의 긴 변 (픽셀) — 글꼴 그림은 모두 메모리에 올라감 (룬 묶음 약 700만 픽셀)
FIRST = 0xE000  # 글자 (사용자 영역 E000 ~ F8FF)
SPACE_FIRST = 0xF800  # 뒤로 옮김 글자 (−1 · −2 · −4 … −1024)
COLS = 8  # 판 한 줄의 칸 수

# 룬이 쓰는 직업 그림 (rfx_j*) — 2026-11-12 첫 판에 옮긴 여섯 (그때 census — VanillaProbe vcensus: 룬 120 이 보낸 파티클 가운데 직업 그림 — 원소 표식 · 터짐 ·
# 타격 · 웅덩이 · 연기 · 깨짐 표식). 2026-11-14 부터는 직업 그림 모두 (DEAD_J 빼고)
RUNE_J = {"rpg:rfx_jbreak", "rpg:rfx_jelem", "rpg:rfx_jburst2", "rpg:rfx_jhits", "rpg:rfx_jpool", "rpg:rfx_jsmoke2"}
# 쓰는 곳이 없는 직업 그림 (2026-11-14 — 정령 궁수의 늑대 · 사슴 · 부엉이 · 정령 꽃은 궁수 계열을 다시 만들며 빠짐 · 얼음 감옥 · 단두대 · 금서는 3D 로 바뀜)
DEAD_J = {"rpg:rfx_jswolf", "rpg:rfx_jswolf1p", "rpg:rfx_jstag", "rpg:rfx_jstagf", "rpg:rfx_jowl", "rpg:rfx_jowlside", "rpg:rfx_jsbloom", "rpg:rfx_jicecage",
          "rpg:rfx_jgallows", "rpg:rfx_jtome"}
# 참격 · 검기 무리 (화면에 크게 · 자주 — 룬 그림과 같은 128)
HI_J = {"rpg:rfx_jslash", "rpg:rfx_jslash2", "rpg:rfx_jslash_rp", "rpg:rfx_jslash_sb", "rpg:rfx_jslash_sw", "rpg:rfx_jslash_wa", "rpg:rfx_jwave", "rpg:rfx_jspin",
        "rpg:rfx_jspin2", "rpg:rfx_jxmark", "rpg:rfx_jcross", "rpg:rfx_jcross2", "rpg:rfx_jstab", "rpg:rfx_jstab2", "rpg:rfx_jsmash", "rpg:rfx_jsmash2", "rpg:rfx_jcuts",
        "rpg:rfx_jmaulswing"}
# 3D 몹을 못 세울 때만 쓰는 그림 (드물게 — 작게)
LOW_J = {"rpg:rfx_jphoenix"}
# 칸이 많은 작은 그림은 더 줄임 (타격 그림 132칸 — 화면에서 한 칸 남짓)
CAP_OF = {"rpg:rfx_jhits": 64}
# Fx3d 카드 (룬 휘두름 판 — RuneKit.arc · waveCard · groundCrescent) · 2026-11-14 직업 참격 판 (JobFx.slashCard — 공통 · 검사 · 전사 · 사신 · 마검사)
# · 2026-11-14 직업 스킬의 2D 물체 판 (JobFx3d.CARDS — 환영 검 · 대검 · 도끼 · 창 · 화살 · 비수 · 마탄 · 구체 · 금서 · 책장 · 망치 · 종 · 방패 · 연 방패 · 활:
# 전에는 카드 목록에 없어 모드 없는 클라이언트에서 통째로 빠졌음 — 헬름 브레이커의 머리 위 도끼 · 포비든 그리모어의 책 · 책장 …)
CARDS = ["slash_wave", "slash_arc", "slash_arc_soft", "slash_heavy", "slash_heavy_soft", "slash_thin", "slash_thin_soft",
         "slash_job", "slash_job_sw", "slash_job_wa", "slash_job_rp", "slash_job_sb",
         "card_sword", "card_greatsword", "card_axe", "card_spear", "card_arrow", "card_dagger", "card_bolt", "card_orb", "card_tome", "card_page",
         "card_maul", "card_bell", "card_buckler", "card_aegis", "card_bow"]


def wanted(pid):
    return pid not in DEAD_J


def cap_for(pid, native):
    """칸의 긴 변 (픽셀) — native = 그림의 칸 긴 변"""
    if pid in CAP_OF:
        return CAP_OF[pid]
    if "rfx_j" not in pid or pid in RUNE_J or pid in HI_J:
        return CAP
    if pid in LOW_J:
        return 64
    return max(56, min(96, round(native * 0.45)))


def shrink(cell, cap):
    """긴 변을 cap 까지 (알파를 곱해 줄임 — 투명한 곳의 색이 번지지 않게)"""
    w, h = cell.size
    k = min(1.0, cap / max(w, h))
    if k >= 1.0:
        return cell
    nw, nh = max(1, round(w * k)), max(1, round(h * k))
    a = np.asarray(cell, dtype=np.float32) / 255.0
    pm = a.copy()
    pm[..., :3] *= pm[..., 3:4]
    img = Image.fromarray((pm * 255 + 0.5).astype(np.uint8), "RGBA").resize((nw, nh), Image.LANCZOS)
    b = np.asarray(img, dtype=np.float32) / 255.0
    al = b[..., 3:4]
    rgb = np.where(al > 1e-4, b[..., :3] / np.maximum(al, 1e-4), 0)
    out = np.concatenate([np.clip(rgb, 0, 1), al], axis=-1)
    return Image.fromarray((out * 255 + 0.5).astype(np.uint8), "RGBA")


def particle_sheets(defs):
    """파티클 ID → 판 이름, 판 이름 → (칸 그림 목록, uv 칸 [uw, vh], 칸 수 [cols, rows])"""
    sheets = {}
    of = {}
    for pid in sorted(defs):
        if not wanted(pid):
            continue
        d = defs[pid]
        sprite = d["sprite"]
        if not sprite.startswith("mmorpg:"):
            continue  # 바닐라 그림 (generic · flame) — 서버가 바닐라 파티클로
        uv = d["uv"]
        uw, vh = (uv["flipbook"]["size"] if "flipbook" in uv else uv["size"])
        tw, th = uv["tw"], uv["th"]
        name = sprite.split(":", 1)[1].replace("addon/rpg/", "").replace("/", "_")
        key = "%s_%dx%d" % (name, uw, vh)
        of[pid] = key
        if key in sheets:
            continue
        path = os.path.join(PARTICLE_TEX, sprite.split(":", 1)[1] + ".png")
        im = Image.open(path).convert("RGBA")
        W, Hh = im.size
        sx, sy = W / tw, Hh / th
        cols, rows = round(tw / uw), round(th / vh)
        cells = []
        cap = cap_for(pid, max(uw * sx, vh * sy))
        for r in range(rows):
            for c in range(cols):
                x0, y0 = round(c * uw * sx), round(r * vh * sy)
                x1, y1 = round((c + 1) * uw * sx), round((r + 1) * vh * sy)
                cells.append(shrink(im.crop((x0, y0, x1, y1)), cap))
        sheets[key] = (cells, [uw, vh], [cols, rows])
    return of, sheets


def scale_at(keys, u):
    """FxModels.sample 의 scale (smoothstep 열쇠 사이)"""
    if not keys:
        return 1.0
    if u <= keys[0][0]:
        return keys[0][1]
    for a, b in zip(keys, keys[1:]):
        if u <= b[0]:
            k = (u - a[0]) / max(1e-5, b[0] - a[0])
            k = k * k * (3 - 2 * k)
            return a[1] + (b[1] - a[1]) * k
    return keys[-1][1]


def card_anims(m):
    """카드 모델의 움직임 → {이름: {len, seq: [[u, 칸]]}} (칸 = 그림이 있는 부분의 차례 — card_layers 와 같음). 모드는 움직임의 부분마다 scale 열쇠로 한 칸만
    켬 (1) · 나머지는 끔 (0): u 구간마다 켜진 칸 (구간 가운데에서 0.5 넘는 것 — 바뀌는 짧은 틈은 앞 칸 그대로). 열쇠가 없는 부분은 늘 켜짐"""
    names = [p["name"] for p in m["parts"] if p["q"]]
    out = {}
    for an, a in (m.get("anims") or {}).items():
        tracks = a.get("tracks", {})
        cuts = {0.0, 1.0}
        for nm in names:
            for key in (tracks.get(nm, {}).get("scale") or []):
                if 0 <= key[0] <= 1:
                    cuts.add(round(key[0], 5))
        cuts = sorted(cuts)
        seq = []
        for a0, a1 in zip(cuts, cuts[1:]):
            if a1 - a0 < 1e-6:
                continue
            mid = (a0 + a1) / 2
            best, bv = -1, 0.5
            for fi, nm in enumerate(names):
                tr = tracks.get(nm)
                v = 1.0 if tr is None or "scale" not in tr else scale_at(tr["scale"], mid)
                if v > bv:
                    best, bv = fi, v
            if best < 0 and seq:
                continue
            if seq and seq[-1][1] == best:
                continue
            seq.append([round(a0, 4), best])
        out[an] = {"len": float(a.get("len", 1.0)), "seq": seq}
    return out


def card_layers():
    """카드 모델 → 칸 (anim play 의 f0 … 또는 main 하나) 마다 겹 [(그림, mat, 가운데 x y z, 반 너비 · 반 높이)] — 앞뒤 빛 겹 (z ±) 은 하나로"""
    out = {}
    for name in CARDS:
        m = json.load(open(os.path.join(MODEL_DIR, name + ".json")))
        tex = m["tex"].split(":", 1)[1]
        im = Image.open(os.path.join(RES, "assets/mmorpg", tex)).convert("RGBA")
        W, Hh = im.size
        frames = []
        for p in m["parts"]:
            if not p["q"]:
                continue
            layers = []
            seen = set()
            for q in p["q"]:
                xs = [q[i * 8] for i in range(4)]
                ys = [q[i * 8 + 1] for i in range(4)]
                us = [q[i * 8 + 3] for i in range(4)]
                vs = [q[i * 8 + 4] for i in range(4)]
                mat = int(q[32])
                rect = (round(min(us) * W), round(min(vs) * Hh), round(max(us) * W), round(max(vs) * Hh))
                if (mat, rect) in seen:
                    continue  # 뒤쪽 빛 겹 (같은 그림) — 서버는 한 장
                seen.add((mat, rect))
                # 판 (모델) 의 가로세로 비로 늘임 — 모드는 그림 칸을 판에 늘여 붙임 (몸 · 빛 그림의 비가 달라도 같은 판). 한 글줄에 겹쳐 그리므로 겹마다 같은 비
                qa = (max(xs) - min(xs)) / max(1e-6, max(ys) - min(ys))
                crop = im.crop(rect)
                ch_px = crop.size[1]
                crop = crop.resize((max(1, round(ch_px * qa)), ch_px), Image.LANCZOS)
                cell = shrink(crop, CAP)
                cx, cy = (min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2
                layers.append((cell, mat, [round(cx, 4), round(cy, 4), 0.0], [round((max(xs) - min(xs)) / 2, 4), round((max(ys) - min(ys)) / 2, 4)]))
            # 그리는 차례: 몸 (mat 0) 먼저, 빛 (mat 1) 을 위에 (모델에서 빛 겹이 몸 앞뒤에 있음)
            layers.sort(key=lambda l: l[1])
            frames.append(layers)
        out[name] = frames
    return out


def build():
    defs = json.load(open(DEFS))["particles"]
    of, sheets = particle_sheets(defs)
    cards = card_layers()
    files = {}
    providers = []
    index_sheets = {}
    code = FIRST

    def sheet_png(key, cells):
        """칸 목록 → 판 그림 (칸 크기를 가장 큰 칸에 맞춤 — 한 판의 칸은 같은 크기) · 글자들 · advance"""
        nonlocal code
        cw = max(c.size[0] for c in cells)
        ch = max(c.size[1] for c in cells)
        cols = min(COLS, len(cells))
        rows = (len(cells) + cols - 1) // cols
        img = Image.new("RGBA", (cols * cw, rows * ch), (0, 0, 0, 0))
        lines = []
        chars = []
        for r in range(rows):
            line = ""
            for c in range(cols):
                i = r * cols + c
                if i < len(cells):
                    cell = cells[i]
                    if cell.size != (cw, ch):
                        cell = cell.resize((cw, ch), Image.LANCZOS)
                    cell = cell.copy()
                    px = cell.load()
                    if px[cw - 1, 0][3] == 0:
                        px[cw - 1, 0] = (255, 255, 255, 1)  # 너비가 줄지 않게 (보이지 않음 — 0.1 아래는 버림)
                    img.paste(cell, (c * cw, r * ch))
                    chars.append(code)
                    line += chr(code)
                    code += 1
                else:
                    line += "\u0000"
            lines.append(line)
        buf = BytesIO()
        img.save(buf, "PNG", optimize=True)
        files[os.path.join(OUT_TEX, key + ".png")] = buf.getvalue()
        providers.append({"type": "bitmap", "file": "rpg:font/vfxp/%s.png" % key, "height": H, "ascent": H // 2 - 2, "chars": lines})
        scale = H / ch
        advance = int(0.5 + cw * scale) + 1
        return chars, cw, ch, advance

    for key in sorted(sheets):
        cells, uvc, grid = sheets[key]
        chars, cw, ch, adv = sheet_png(key, cells)
        index_sheets[key] = {"first": chars[0], "n": len(chars), "cols": grid[0], "rows": grid[1], "uw": uvc[0], "vh": uvc[1], "px": [cw, ch], "adv": adv}
    index_cards = {}
    index_anims = {}
    for name in CARDS:
        frames = []
        for fi, layers in enumerate(cards[name]):
            fr = []
            for li, (cell, mat, c, half) in enumerate(layers):
                chars, cw, ch, adv = sheet_png("card_%s_f%d_%d" % (name, fi, li), [cell])
                fr.append({"c": chars[0], "mat": mat, "at": c, "half": half, "adv": adv})
            frames.append(fr)
        index_cards[name] = frames
        anims = card_anims(json.load(open(os.path.join(MODEL_DIR, name + ".json"))))
        if anims:
            index_anims[name] = anims
    # 2026-11-14 입체 모델을 구운 판 (tools/vfx_bake3d.py — 명계의 문 · 망자의 손): 칸마다 한 겹 (mat 2 = 흰 글자 — 색을 구워 넣음) · 보는 사람 쪽으로 돌림 (bill)
    bill = []
    for name in sorted(vfx_bake3d.BAKE):
        cells, centre, half, anims = vfx_bake3d.bake(name)
        chars, cw, ch, adv = sheet_png("card_%s" % name, cells)
        index_cards[name] = [[{"c": c, "mat": 2, "at": [centre[0], centre[1], 0.0], "half": half, "adv": adv}] for c in chars]
        index_anims[name] = anims
        bill.append(name)
    assert code < SPACE_FIRST, "glyphs overflow %x" % code
    # 뒤로 옮김 (서버가 여러 겹을 한 글줄에 겹쳐 그림)
    adv = {chr(SPACE_FIRST + b): -(1 << b) for b in range(11)}
    providers.append({"type": "space", "advances": adv})
    font = {"providers": providers}
    idx = {
        "_doc": "tools/vfx_particles.py 가 만듦 (손으로 고치지 않음) — 글꼴 rpg:vfxp: 파티클 ID → 판 (uv 칸 하나 = 글자 하나, 칸 번호 = 줄 × cols + 칸), "
                "카드 (Fx3d 판) → 칸마다 겹 (글자 · mat 0 몸 / 1 빛 · 모델 자리 · 반 크기). 글자 높이 h 글꼴 단위 (1 단위 = 0.025칸), adv = 글자 너비 (뒤로 옮김 space "
                "글자 space+b 가 −2^b)",
        "font": "rpg:vfxp", "h": H, "space": SPACE_FIRST,
        "particles": of, "sheets": index_sheets, "cards": index_cards, "cardAnims": index_anims, "cardBill": bill,
    }
    files[OUT_FONT] = (json.dumps(font, ensure_ascii=False, indent=1) + "\n").encode("utf-8")
    files[OUT_INDEX] = (json.dumps(idx, ensure_ascii=False, indent=1, sort_keys=True) + "\n").encode("utf-8")
    return files, idx


def main():
    check = "--check" in sys.argv
    files, idx = build()
    bad = []
    for path, data in files.items():
        old = open(path, "rb").read() if os.path.exists(path) else None
        if old != data:
            if check:
                bad.append(os.path.relpath(path, ROOT))
            else:
                os.makedirs(os.path.dirname(path), exist_ok=True)
                open(path, "wb").write(data)
    # 지난 판 (이제 쓰지 않는 그림) 지우기
    if os.path.isdir(OUT_TEX):
        for f in os.listdir(OUT_TEX):
            p = os.path.join(OUT_TEX, f)
            if p not in files:
                if check:
                    bad.append(os.path.relpath(p, ROOT))
                else:
                    os.remove(p)
    if check:
        if bad:
            print("vfx particles differ: " + ", ".join(bad[:8]) + (" …" if len(bad) > 8 else "") + " — python3 tools/vfx_particles.py")
            return 1
        print("vfx particles ok (%d particles, %d sheets, %d cards)" % (len(idx["particles"]), len(idx["sheets"]), len(idx["cards"])))
        return 0
    png = sum(len(v) for k, v in files.items() if k.endswith(".png"))
    px = 0
    for k, v in files.items():
        if k.endswith(".png"):
            w, h = Image.open(BytesIO(v)).size
            px += w * h
    print("wrote %d particles → %d sheets, %d cards; png %.1f MB, %.1f M px (RGBA %.0f MB)" % (len(idx["particles"]), len(idx["sheets"]), len(idx["cards"]),
                                                                                         png / 1e6, px / 1e6, px * 4 / 1e6))
    return 0


if __name__ == "__main__":
    sys.exit(main())
