#!/usr/bin/env python3
"""12단계 자바 전용 연출 그림 (rpg:rfx_* — 애드온 rfx 파티클과 같은 형식) → tools/java_fx (tools/addon_particles.py 가 애드온 파티클과 함께 넣음).

  python3 tools/java_fx_art.py          # 그림 · 파티클 정의를 tools/java_fx 에 씀
  python3 tools/addon_particles.py      # 그다음 자바 파티클 정의 · 그림 · 이름표 (AddonFxTable) 를 다시 만듦

그림 (모두 미리 칠한 색 — 서버가 색을 [1, 1, 1] 로 주면 그대로, 시계 · 뱀은 빛 · 심지를 흰색으로 그려 색을 입힘)
- rfx_spiritking  정령왕 강림의 거대한 정령왕 (512×512 × 4장: 팔을 내림 → 반쯤 → 듦 → 든 채 빛남) — 네 원소 색 몸 · 원소 왕관 · 빛 날개
- rfx_relic       무기 8종을 모델 옆모습으로 (512×128 × 8칸 — 끝이 오른쪽, 날아가는 방향으로 눕는 그림: 던지는 창 · 망치 · 낫)
- rfx_relicspin   같은 무기를 비스듬히 (256×256 × 8칸 — 도는 그림: 묠니르 · 거대한 낫)
                  칸 순서 = RELIC (창 초월 (0.12.3 부터 궁니르 — 지금 쓰는 스킬은 없음) · 창 영웅 · 낫 초월 · 낫 영웅 · 망치 초월 · 망치 영웅 · 홀 초월 · 홀 영웅) — 모델을 바꾸면 이 스크립트를 다시 돌리면 따라 바뀜
- rfx_clock       크로노스의 시계 (256×256 × 8칸: 시계판 빛 · 심지, 시침 빛 · 심지, 분침 빛 · 심지, 톱니 빛 · 심지 — 바늘은 가운데에서 위로, 돌리면 됨)
- rfx_serpent     카두케우스의 빛의 뱀 (256×128 × 8칸: 꿈틀거림 빛 4장 · 심지 4장 — 머리가 오른쪽)
- 세계수의 도끼 · 스발린 (0.12.4) 그림은 tools/java_fx_art_more.py, 0.16.0 한정 초월 룬 그림 (rfx_orb · rfx_skyeye) 은 tools/java_fx_limited.py (이 스크립트가 함께 부름)
"""
import json
import math
import os
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import geo_tex  # noqa: E402 — 베드락 무기 모델 읽기

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ART = os.path.join(ROOT, "tools", "weapon_art")
OUT = os.path.join(ROOT, "tools", "java_fx")
TEX = os.path.join(OUT, "textures", "particle", "rpg", "fx")
PART = os.path.join(OUT, "particles", "rpg", "fx")

RELIC = ["lance_transcend_lv0", "lance_hero_lv0", "scythe_transcend_lv0", "scythe_hero_lv0", "hammer_transcend_lv0", "hammer_hero_lv0",
         "scepter_transcend_lv0", "scepter_hero_lv0"]  # (0.12.2 ~ 0.12.3 의 칸 8 반고의 도끼는 0.12.4 세계수의 도끼 — 던지는 도끼가 없어 뺌)
GLOW_ALPHA = 102


# ------------------------------------------------------------ 파티클 정의 (애드온 rpg:rfx_king 과 같은 꼴)

def uv_expr(cols, cw, ch):
    frame = ("(variable.cell + (variable.frames > 1 ? (variable.loop > 0 ? math.mod(math.floor(variable.particle_age * variable.fps + variable.phase), "
             "variable.frames) : math.min(variable.frames - 1, math.floor(variable.particle_age * variable.fps))) : 0))")
    return [f"math.mod({frame}, {cols}) * {cw}", f"math.floor({frame} / {cols}) * {ch}"]


def particle(name, tw, th, cw, ch, facing):
    grow = "(1 + variable.grow * (variable.particle_age / variable.particle_lifetime))"
    bb = {
        "size": [f"variable.size * 1.0000 * {grow}", f"variable.size * {ch / cw:.4f} * {grow}"],
        "facing_camera_mode": facing,
        "uv": {"texture_width": tw, "texture_height": th, "uv": uv_expr(tw // cw, cw, ch), "uv_size": [cw, ch]},
    }
    if facing in ("lookat_direction", "direction_z"):
        bb["direction"] = {"mode": "custom", "custom_direction": ["variable.dir.x", "variable.dir.y", "variable.dir.z"]}
    return {"format_version": "1.10.0", "particle_effect": {
        "description": {"identifier": f"rpg:{name}", "basic_render_parameters": {"material": "particles_blend", "texture": f"textures/particle/rpg/fx/{name}"}},
        "components": {
            "minecraft:emitter_rate_instant": {"num_particles": 1},
            "minecraft:emitter_lifetime_once": {},
            "minecraft:emitter_shape_point": {"direction": ["variable.move.x", "variable.move.y", "variable.move.z"]},
            "minecraft:particle_lifetime_expression": {"max_lifetime": "variable.life"},
            "minecraft:particle_initial_spin": {"rotation": "variable.spin", "rotation_rate": "variable.spin_rate"},
            "minecraft:particle_appearance_billboard": bb,
            "minecraft:particle_appearance_tinting": {"color": [
                "variable.color.r", "variable.color.g", "variable.color.b",
                "variable.color.a * math.clamp(variable.particle_age / 0.05, 0, 1) * (1 - math.clamp(((variable.particle_age / variable.particle_lifetime) - "
                "(1 - variable.fade)) / math.max(0.01, variable.fade), 0, 1))"]},
            "minecraft:particle_initial_speed": "variable.speed",
            "minecraft:particle_motion_dynamic": {},
        }}}


def save(name, img, cw, ch, facing):
    os.makedirs(TEX, exist_ok=True)
    os.makedirs(PART, exist_ok=True)
    img.save(os.path.join(TEX, f"{name}.png"))
    with open(os.path.join(PART, f"{name}.particle.json"), "w", encoding="utf-8") as fh:
        json.dump(particle(name, img.size[0], img.size[1], cw, ch, facing), fh, ensure_ascii=False, indent=2)
        fh.write("\n")
    print(f"{name}: {img.size[0]}x{img.size[1]} ({cw}x{ch} cells, {facing})")


# ------------------------------------------------------------ 그리기 도움

def rgba(c, a=255):
    return (int(c[0]), int(c[1]), int(c[2]), int(a))


def lerp(a, b, t):
    return tuple(a[i] + (b[i] - a[i]) * t for i in range(len(a)))


def glow_of(img, radius, color, strength=1.0):
    """알파를 흐리게 퍼뜨린 빛 (color)"""
    a = img.split()[3].filter(ImageFilter.GaussianBlur(radius))
    if strength != 1.0:
        a = a.point(lambda v: min(255, int(v * strength)))
    g = Image.new("RGBA", img.size, rgba(color, 0))
    g.putalpha(a)
    return g


def over(*layers):
    out = Image.new("RGBA", layers[0].size, (0, 0, 0, 0))
    for l in layers:
        out.alpha_composite(l)
    return out


def shrink(img, size):
    return img.resize(size, Image.LANCZOS)


# ------------------------------------------------------------ 무기 그림 (모델 옆모습)

def load_model(design):
    """무기 모델 (geo_tex.Model — 팔레트 · 다듬은 텍스처 모두)"""
    m = geo_tex.load(design, os.path.join(ART, f"{design}.geo.json"), os.path.join(ART, f"{design}.png"))
    for w in m.warnings:
        print(f"  경고 {design}: {w}")
    return m


def side_view_java(m, px):
    """자바 모델 디자인 (사용자 리워크 — 0.12.7): 광선 추적 (java_model_view) 으로 같은 옆모습 — 회전 상자 · 반투명 · 면 rotation 까지.
    맥동 텍스처는 가장 밝은 프레임, 빛나는 상자 (light_emission) 의 픽셀은 밝게, 빛 색 = 빛나는 상자 픽셀의 평균"""
    import numpy as np
    import java_model_view as jmv

    j = m.java
    strip = j["strip"]
    W, fh = j["frame"]
    n = strip.size[1] // fh
    frames = [np.array(strip.crop((0, i * fh, W, (i + 1) * fh))) for i in range(max(1, n))]
    frame = max(frames, key=lambda f: f[:, :, :3][f[:, :, 3] > 0].mean() if (f[:, :, 3] > 0).any() else 0)
    view = jmv.View(j["elements"], frame)
    ys = [c.lo[1] for c in m.cubes] + [c.hi[1] for c in m.cubes]
    zs = [c.lo[2] for c in m.cubes] + [c.hi[2] for c in m.cubes]
    y0, y1, z0, z1 = min(ys), max(ys), min(zs), max(zs)
    w, h = int(math.ceil((y1 - y0) * px)) + 2, int(math.ceil((z1 - z0) * px)) + 2
    gy = y0 + (np.arange(w) + 0.5 - 1) / px  # 베드락 디자인 좌표
    gz = z1 - (np.arange(h) + 0.5 - 1) / px
    A, B, k = j["anchor"], j["to"], j["scale"]
    Y, Z = np.meshgrid(A[1] + (gy - B[1]) / k, A[2] + (gz - B[2]) / k, indexing="xy")  # 팩 좌표 (같은 방향 — 베드락 +x = 팩 +x)
    col, glow, _ = jmv.grid_trace(view, Y, Z, from_plus_x=True)
    col = col.reshape(h, w, 4)
    glow = glow.reshape(h, w)
    lit = col.copy()
    g = glow > 0.5
    lit[g, :3] = np.minimum(255, lit[g, :3] * 1.1 + 30)
    img = Image.fromarray(lit.clip(0, 255).astype(np.uint8), "RGBA")
    sel = g & (col[:, :, 3] > 0)
    gc = tuple(col[sel, :3].mean(0)) if sel.any() else (255, 240, 200)
    return img, gc


def side_view(m, px):
    """옆모습 (x 축으로 봄): 가로 = y (끝이 오른쪽), 세로 = z — px = 모델 1칸의 점 수. (그림, 빛 색).
    팔레트 모델은 상자마다 한 색, 다듬은 텍스처는 보이는 면 (west) 의 그 자리 픽셀"""
    if m.java:
        return side_view_java(m, px)
    cubes = m.cubes
    ys = [c.lo[1] for c in cubes] + [c.hi[1] for c in cubes]
    zs = [c.lo[2] for c in cubes] + [c.hi[2] for c in cubes]
    y0, y1, z0, z1 = min(ys), max(ys), min(zs), max(zs)
    w, h = int(math.ceil((y1 - y0) * px)) + 2, int(math.ceil((z1 - z0) * px)) + 2
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    glow_sum = [0.0, 0.0, 0.0, 0.0]
    palette = m.palette

    def lit(col):
        r, g, b, a = col
        if a < 255:
            glow_sum[0] += r
            glow_sum[1] += g
            glow_sum[2] += b
            glow_sum[3] += 1
            r, g, b = (min(255, int(x * 1.1 + 30)) for x in (r, g, b))
        return r, g, b, 255

    for c in sorted(cubes, key=lambda q: q.hi[0]):
        u0 = 1 + (c.lo[1] - y0) * px
        u1 = 1 + (c.hi[1] - y0) * px
        v0 = 1 + (z1 - c.hi[2]) * px
        v1 = 1 + (z1 - c.lo[2]) * px
        if palette:
            col = m.side_color(c, (c.lo[1] + c.hi[1]) / 2, (c.lo[2] + c.hi[2]) / 2)
            if col is None:
                continue
            d.rectangle([u0, v0, u1 - 1, v1 - 1], fill=lit(col))
            continue
        for yy in range(int(math.floor(v0)), int(math.ceil(v1))):
            for xx in range(int(math.floor(u0)), int(math.ceil(u1))):
                if not (0 <= xx < w and 0 <= yy < h):
                    continue
                col = m.side_color(c, y0 + (xx + 0.5 - 1) / px, z1 - (yy + 0.5 - 1) / px)
                if col is None or col[3] == 0:
                    continue
                img.putpixel((xx, yy), lit(col))
    glow = tuple(x / glow_sum[3] for x in glow_sum[:3]) if glow_sum[3] else (255, 240, 200)
    return img, glow


def relic_cell(design, cw, ch, diagonal):
    m = load_model(design)
    ys = [c.lo[1] for c in m.cubes] + [c.hi[1] for c in m.cubes]
    zs = [c.lo[2] for c in m.cubes] + [c.hi[2] for c in m.cubes]
    length, width = max(ys) - min(ys), max(zs) - min(zs)
    ss = 4
    if diagonal:
        px = min((cw * 0.84 * ss) / length, (cw * 0.5 * ss) / max(1, width))
    else:
        px = min((cw - 24) * ss / length, (ch - 24) * ss / max(1, width))
    img, glow = side_view(m, px)
    if diagonal:
        img = img.rotate(45, resample=Image.BICUBIC, expand=True)
    canvas = Image.new("RGBA", (cw * ss, ch * ss), (0, 0, 0, 0))
    canvas.alpha_composite(img, ((cw * ss - img.size[0]) // 2, (ch * ss - img.size[1]) // 2))
    halo = glow_of(canvas, 9 * ss / 4, glow, 1.6)
    rim = glow_of(canvas, 2.5 * ss / 4, (255, 255, 255), 2.2)
    return shrink(over(halo, rim, canvas), (cw, ch))


def relic_sheets():
    cw, ch = 512, 128
    sheet = Image.new("RGBA", (cw * 2, ch * ((len(RELIC) + 1) // 2)), (0, 0, 0, 0))
    for i, d in enumerate(RELIC):
        sheet.alpha_composite(relic_cell(d, cw, ch, False), ((i % 2) * cw, (i // 2) * ch))
    save("rfx_relic", sheet, cw, ch, "lookat_direction")
    c2 = 256
    sheet = Image.new("RGBA", (c2 * 4, c2 * ((len(RELIC) + 3) // 4)), (0, 0, 0, 0))
    for i, d in enumerate(RELIC):
        sheet.alpha_composite(relic_cell(d, c2, c2, True), ((i % 4) * c2, (i // 4) * c2))
    save("rfx_relicspin", sheet, c2, c2, "rotate_xyz")


# ------------------------------------------------------------ 시계 (크로노스)

def clock_sheet():
    cw = 256
    ss = 4
    S = cw * ss
    c = S / 2
    cells = []

    def ring_layer(width_out, width_in, ticks, tick_len, tick_w, numerals):
        img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        r0 = S * 0.47
        d.ellipse([c - r0, c - r0, c + r0, c + r0], outline=(255, 255, 255, 255), width=int(width_out))
        r1 = S * 0.40
        d.ellipse([c - r1, c - r1, c + r1, c + r1], outline=(255, 255, 255, 220), width=int(width_in))
        for k in range(ticks):
            a = k / ticks * math.tau
            big = k % (ticks // 12) == 0
            l0 = r1 + (S * 0.012)
            l1 = r0 - (S * 0.012) if big else r0 - S * 0.04
            w = tick_w * (1.8 if big else 1)
            p = [(c + math.sin(a) * l0, c - math.cos(a) * l0), (c + math.sin(a) * l1, c - math.cos(a) * l1)]
            d.line(p, fill=(255, 255, 255, 255), width=int(w))
        if numerals:
            # 숫자 대신 작은 막대 묶음 (I · II · III … 을 막대 수로 — 멀리서 시계 숫자처럼)
            for k in range(12):
                a = k / 12 * math.tau
                rr = S * 0.33
                n = 1 + (k + 11) % 3
                for j in range(n):
                    off = (j - (n - 1) / 2) * S * 0.014
                    x = c + math.sin(a) * rr + math.cos(a) * off
                    y = c - math.cos(a) * rr + math.sin(a) * off
                    ln = S * 0.022
                    d.line([(x - math.sin(a) * ln, y + math.cos(a) * ln), (x + math.sin(a) * ln, y - math.cos(a) * ln)], fill=(255, 255, 255, 230),
                           width=int(S * 0.006))
        return img

    dial_core = ring_layer(S * 0.012, S * 0.006, 60, S * 0.03, S * 0.004, True)
    dial_glow = glow_of(ring_layer(S * 0.04, S * 0.025, 60, S * 0.03, S * 0.012, True), S * 0.012, (255, 255, 255), 1.4)
    cells += [dial_glow, dial_core]

    def hand(length, width, tail):
        img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        tip = (c, c - length)
        poly = [(c - width, c + tail), (c - width * 0.7, c - length * 0.75), tip, (c + width * 0.7, c - length * 0.75), (c + width, c + tail)]
        d.polygon(poly, fill=(255, 255, 255, 255))
        r = width * 1.15
        d.ellipse([c - r, c - r, c + r, c + r], fill=(255, 255, 255, 255))
        return img

    for length, width in ((S * 0.24, S * 0.024), (S * 0.36, S * 0.016)):
        h = hand(length, width, S * 0.05)
        cells += [glow_of(hand(length, width * 2.2, S * 0.06), S * 0.012, (255, 255, 255), 1.5), h]

    def gear(r_out, r_in, teeth, hole):
        img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        d = ImageDraw.Draw(img)
        pts = []
        for k in range(teeth * 4):
            a = k / (teeth * 4) * math.tau
            r = r_out if (k % 4) in (1, 2) else r_in
            pts.append((c + math.sin(a) * r, c - math.cos(a) * r))
        d.polygon(pts, fill=(255, 255, 255, 255))
        d.ellipse([c - hole, c - hole, c + hole, c + hole], fill=(0, 0, 0, 0))
        for k in range(6):
            a = k / 6 * math.tau
            d.line([(c + math.sin(a) * hole * 0.3, c - math.cos(a) * hole * 0.3), (c + math.sin(a) * hole, c - math.cos(a) * hole)], fill=(255, 255, 255, 255),
                   width=int(S * 0.02))
        d.ellipse([c - hole * 0.3, c - hole * 0.3, c + hole * 0.3, c + hole * 0.3], fill=(255, 255, 255, 255))
        return img

    g = gear(S * 0.44, S * 0.37, 14, S * 0.27)
    cells += [glow_of(gear(S * 0.46, S * 0.36, 14, S * 0.25), S * 0.015, (255, 255, 255), 1.3), g.point(lambda v: v)]
    sheet = Image.new("RGBA", (cw * 4, cw * 2), (0, 0, 0, 0))
    for i, img in enumerate(cells):
        sheet.alpha_composite(shrink(img, (cw, cw)), ((i % 4) * cw, (i // 4) * cw))
    save("rfx_clock", sheet, cw, cw, "direction_z")


# ------------------------------------------------------------ 빛의 뱀 (카두케우스)

def serpent_sheet():
    cw, ch = 256, 128
    ss = 4
    W, H = cw * ss, ch * ss
    sheet = Image.new("RGBA", (cw * 4, ch * 2), (0, 0, 0, 0))
    for f in range(4):
        phase = f / 4 * math.tau
        body = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        d = ImageDraw.Draw(body)
        pts = []
        n = 80
        for k in range(n + 1):
            t = k / n
            x = W * (0.06 + 0.8 * t)
            amp = H * 0.18 * (0.35 + 0.65 * (1 - t))
            y = H / 2 + math.sin(t * math.tau * 1.4 - phase) * amp
            pts.append((x, y))
        for k in range(n):
            t = k / n
            w = H * (0.035 + 0.075 * math.sin(min(1.0, t * 1.15) * math.pi * 0.5))
            (x0, y0), (x1, y1) = pts[k], pts[k + 1]
            d.line([(x0, y0), (x1, y1)], fill=(255, 255, 255, 255), width=int(w * 2))
            d.ellipse([x1 - w, y1 - w, x1 + w, y1 + w], fill=(255, 255, 255, 255))
        hx, hy = pts[-1]
        hw = H * 0.13
        d.polygon([(hx - hw * 0.4, hy - hw), (hx + hw * 1.6, hy - hw * 0.25), (hx + hw * 1.9, hy), (hx + hw * 1.6, hy + hw * 0.25), (hx - hw * 0.4, hy + hw)],
                  fill=(255, 255, 255, 255))
        core = body.copy()
        dc = ImageDraw.Draw(core)
        # 눈 · 비늘 무늬 (심지에서 빼서 어둡게 보이게)
        dc.ellipse([hx + hw * 0.55, hy - hw * 0.55, hx + hw * 0.95, hy - hw * 0.15], fill=(0, 0, 0, 0))
        for k in range(6, n - 4, 6):
            x, y = pts[k]
            dc.ellipse([x - H * 0.018, y - H * 0.018, x + H * 0.018, y + H * 0.018], fill=(0, 0, 0, 0))
        core = core.filter(ImageFilter.GaussianBlur(ss * 0.4))
        glow = over(glow_of(body, ss * 5, (255, 255, 255), 1.6), body)
        sheet.alpha_composite(shrink(glow, (cw, ch)), (f * cw, 0))
        inner = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        inner.alpha_composite(core)
        a = inner.split()[3].filter(ImageFilter.MinFilter(9))
        inner.putalpha(a)
        sheet.alpha_composite(shrink(inner, (cw, ch)), (f * cw, ch))
    save("rfx_serpent", sheet, cw, ch, "lookat_direction")


# ------------------------------------------------------------ 정령왕 (정령왕 강림)

FIRE = (255, 120, 40)
WATER = (70, 150, 255)
WIND = (90, 240, 150)
EARTH = (226, 162, 80)
GOLD = (255, 214, 120)
WHITE = (255, 250, 236)


def radial(size, center, radius, inner, outer, power=1.6):
    w, h = size
    img = Image.new("RGBA", size, (0, 0, 0, 0))
    px = img.load()
    cx, cy = center
    for y in range(max(0, int(cy - radius)), min(h, int(cy + radius) + 1)):
        for x in range(max(0, int(cx - radius)), min(w, int(cx + radius) + 1)):
            t = math.hypot(x - cx, y - cy) / radius
            if t >= 1:
                continue
            k = (1 - t) ** power
            c = lerp(outer, inner, k)
            px[x, y] = (int(c[0]), int(c[1]), int(c[2]), int(c[3]))
    return img


def gradient_fill(mask, top, bottom, left=None, right=None):
    """마스크 안을 위 → 아래 색으로, 양옆 (왼쪽 left · 오른쪽 right) 을 섞음"""
    w, h = mask.size
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    px = img.load()
    m = mask.load()
    box = mask.getbbox()
    if not box:
        return img
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        ty = (y - y0) / max(1, y1 - y0 - 1)
        base = lerp(top, bottom, ty)
        for x in range(x0, x1):
            a = m[x, y]
            if not a:
                continue
            tx = (x - x0) / max(1, x1 - x0 - 1)
            c = base
            if left is not None and tx < 0.5:
                c = lerp(c, left, (0.5 - tx) * 2 * (0.25 + 0.75 * ty))
            if right is not None and tx > 0.5:
                c = lerp(c, right, (tx - 0.5) * 2 * (0.25 + 0.75 * ty))
            px[x, y] = (int(c[0]), int(c[1]), int(c[2]), a)
    return img


def poly_mask(size, pts):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).polygon(pts, fill=255)
    return m


def axial_fill(mask, p0, p1, c0, c1):
    """마스크 안을 p0 → p1 축을 따라 c0 → c1 로 (알파는 마스크)"""
    w, h = mask.size
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    box = mask.getbbox()
    if not box:
        return img
    px = img.load()
    m = mask.load()
    dx, dy = p1[0] - p0[0], p1[1] - p0[1]
    ll = dx * dx + dy * dy or 1
    x0, y0, x1, y1 = box
    for y in range(y0, y1):
        for x in range(x0, x1):
            a = m[x, y]
            if not a:
                continue
            t = max(0.0, min(1.0, ((x - p0[0]) * dx + (y - p0[1]) * dy) / ll))
            c = lerp(c0, c1, t)
            px[x, y] = (int(c[0]), int(c[1]), int(c[2]), a)
    return img


def feather_mask(size, base, ang, length, width):
    """깃털 (밑동 base, 방향 ang 라디안, 길이 · 폭): 끝이 둥근 가늘고 긴 잎 꼴"""
    m = Image.new("L", size, 0)
    d = ImageDraw.Draw(m)
    ux, uy = math.cos(ang), math.sin(ang)
    nx, ny = -uy, ux
    pts = []
    n = 14
    for k in range(n + 1):
        t = k / n
        wd = width * math.sin(math.pi * min(1.0, t * 0.92 + 0.08)) * (0.55 + 0.45 * t)
        pts.append((base[0] + ux * length * t + nx * wd, base[1] + uy * length * t + ny * wd))
    for k in range(n, -1, -1):
        t = k / n
        wd = width * math.sin(math.pi * min(1.0, t * 0.92 + 0.08)) * (0.55 + 0.45 * t) * 0.55
        pts.append((base[0] + ux * length * t - nx * wd, base[1] + uy * length * t - ny * wd))
    d.polygon(pts, fill=255)
    return m


def king_frame(raise_t, pulse):
    """정령왕 한 장 (512×512, 2배로 그려 줄임). raise_t: 0 = 팔을 내림 → 1 = 어깨 위로 비스듬히 듦, pulse: 빛남 (0 ~ 1)"""
    S = 512 * 2
    size = (S, S)
    cx = S / 2
    hc = (cx, S * 0.25)  # 머리
    sh_y = S * 0.35  # 어깨
    layers = []
    # 1) 뒤의 큰 빛 + 네 원소 빛무리
    layers.append(radial(size, (cx, S * 0.45), S * 0.47, (255, 248, 222, 150 + 70 * pulse), (255, 230, 170, 0), 1.8))
    for (ex, ey), col in (((0.22, 0.34), FIRE), ((0.78, 0.34), WATER), ((0.24, 0.74), WIND), ((0.76, 0.74), EARTH)):
        layers.append(radial(size, (S * ex, S * ey), S * 0.19, (*col, 110 + 60 * pulse), (*col, 0), 1.5))
    # 2) 빛 날개: 어깨 뒤에서 부채꼴로 펼친 깃털 두 겹 (왼쪽 물 → 바람, 오른쪽 불 → 흙), 밑동은 흰빛
    wing = Image.new("RGBA", size, (0, 0, 0, 0))
    for layer, (count, length, width, a0, a1, alpha) in enumerate(((7, 0.36, 0.034, -62, 18, 215), (6, 0.25, 0.03, -40, 30, 235))):
        for side, c_hi, c_lo in ((-1, WATER, WIND), (1, FIRE, EARTH)):
            base = (cx + side * S * 0.06, sh_y + S * 0.01)
            for k in range(count):
                t = k / (count - 1)
                ang_deg = a0 + (a1 - a0) * t - raise_t * 8
                ang = math.radians(ang_deg)
                ux = side * math.cos(ang)
                uy = math.sin(ang)
                L = S * length * (1 - 0.25 * t)
                m = feather_mask(size, base, math.atan2(uy, ux), L, S * width)
                m = m.point(lambda v, al=alpha: int(v * al / 255))
                tip_col = lerp(c_hi, c_lo, t)
                f = axial_fill(m, base, (base[0] + ux * L, base[1] + uy * L), WHITE, tip_col)
                wing.alpha_composite(f)
    wing = wing.filter(ImageFilter.GaussianBlur(S * 0.003))
    layers.append(glow_of(wing, S * 0.025, (255, 236, 200), 1.0))
    layers.append(wing)
    # 3) 머리 뒤 후광 (금빛 고리 + 원소 보석 넷 + 빛살)
    halo = Image.new("RGBA", size, (0, 0, 0, 0))
    dh = ImageDraw.Draw(halo)
    hr = S * 0.11
    for k in range(16):
        a = k / 16 * math.tau
        r0, r1 = hr * 1.05, hr * (1.45 if k % 2 == 0 else 1.25)
        dh.line([(hc[0] + math.sin(a) * r0, hc[1] - math.cos(a) * r0), (hc[0] + math.sin(a) * r1, hc[1] - math.cos(a) * r1)], fill=(*GOLD, 150),
                width=int(S * 0.006))
    dh.ellipse([hc[0] - hr, hc[1] - hr, hc[0] + hr, hc[1] + hr], outline=(*GOLD, 255), width=int(S * 0.011))
    for k, col in enumerate((FIRE, WATER, WIND, EARTH)):
        a = k / 4 * math.tau + math.pi / 4
        x, y = hc[0] + math.sin(a) * hr, hc[1] - math.cos(a) * hr
        r = S * 0.016
        dh.ellipse([x - r, y - r, x + r, y + r], fill=(*col, 255))
    layers.append(glow_of(halo, S * 0.012, GOLD, 1.5))
    layers.append(halo)
    # 4) 옷자락 몸: 어깨에서 아래로 퍼지는 긴 옷 (물결 끝), 위는 흰 금빛 · 왼쪽 물 · 오른쪽 불 · 아래 바람 · 흙
    top = sh_y - S * 0.015
    pts = [(cx - S * 0.075, top), (cx + S * 0.075, top), (cx + S * 0.12, S * 0.5), (cx + S * 0.17, S * 0.72), (cx + S * 0.23, S * 0.95)]
    for k in range(13):
        t = k / 12
        pts.append((cx + S * 0.23 - t * S * 0.46, S * 0.95 + math.sin(t * math.pi * 5) * S * 0.012))
    pts += [(cx - S * 0.17, S * 0.72), (cx - S * 0.12, S * 0.5)]
    robe_mask = poly_mask(size, pts)
    robe = gradient_fill(robe_mask, (255, 251, 240), (246, 214, 160), left=WATER, right=FIRE)
    band = Image.new("L", size, 0)
    ImageDraw.Draw(band).rectangle([0, S * 0.76, S, S], fill=255)
    band = ImageChops.multiply(band.filter(ImageFilter.GaussianBlur(S * 0.035)), robe_mask)
    lr = gradient_fill(band, WIND, EARTH, left=WIND, right=EARTH)
    lr.putalpha(band.point(lambda v: int(v * 0.7)))
    robe.alpha_composite(lr)
    core = radial(size, (cx, S * 0.55), S * 0.3, (255, 255, 250, 150 + 60 * pulse), (255, 255, 250, 0), 1.3)
    robe.alpha_composite(ImageChops.multiply(core, Image.merge("RGBA", (robe_mask,) * 4)))
    robe.putalpha(robe_mask.point(lambda v: int(v * 0.92)))
    folds = Image.new("RGBA", size, (0, 0, 0, 0))
    df = ImageDraw.Draw(folds)
    for k in range(-3, 4):
        df.line([(cx + k * S * 0.014, S * 0.42), (cx + k * S * 0.058, S * 0.94)], fill=(255, 252, 240, 120), width=int(S * 0.004))
    folds = ImageChops.multiply(folds, Image.merge("RGBA", (robe_mask,) * 4))
    layers.append(glow_of(robe, S * 0.025, WHITE, 1.2))
    layers.append(robe)
    layers.append(folds)
    # 몸을 감고 오르는 원소 띠 넷 (가는 빛줄기)
    ribbons = Image.new("RGBA", size, (0, 0, 0, 0))
    dr = ImageDraw.Draw(ribbons)
    for k, col in enumerate((FIRE, WATER, WIND, EARTH)):
        pts2 = []
        for j in range(40):
            t = j / 39
            y = S * 0.93 - t * S * 0.5
            half = S * (0.2 - 0.12 * t)
            x = cx + math.sin(t * math.tau * 1.2 + k * math.pi / 2) * half
            pts2.append((x, y))
        dr.line(pts2, fill=(*col, 190), width=int(S * 0.006), joint="curve")
    layers.append(glow_of(ribbons, S * 0.006, WHITE, 1.2))
    layers.append(ribbons)
    # 5) 팔: 어깨 → 팔꿈치 → 손 (늘어진 소매) + 손 위의 원소 구슬
    arm = Image.new("RGBA", size, (0, 0, 0, 0))
    orbs = []
    for side, col_a, col_b in ((-1, WATER, WIND), (1, FIRE, EARTH)):
        sh = (cx + side * S * 0.085, sh_y + S * 0.005)
        a_up = math.radians(105 - raise_t * 140)  # 105° (아래) → -35° (위로 비스듬히)
        l1, l2 = S * 0.13, S * 0.12
        el = (sh[0] + side * l1 * math.cos(a_up), sh[1] + l1 * math.sin(a_up))
        a2 = a_up - math.radians(10 + 15 * raise_t)
        hand = (el[0] + side * l2 * math.cos(a2), el[1] + l2 * math.sin(a2))
        m = Image.new("L", size, 0)
        dm = ImageDraw.Draw(m)
        dm.line([sh, el], fill=255, width=int(S * 0.045))
        dm.line([el, hand], fill=255, width=int(S * 0.03))
        dm.ellipse([el[0] - S * 0.022, el[1] - S * 0.022, el[0] + S * 0.022, el[1] + S * 0.022], fill=255)
        # 늘어진 소매 (팔 아래로)
        drape = S * (0.09 - 0.03 * raise_t)
        dm.polygon([(el[0] - side * S * 0.01, el[1]), (hand[0] - side * S * 0.02, hand[1] + S * 0.012), (el[0] - side * S * 0.02, el[1] + drape)], fill=200)
        sleeve = axial_fill(m, sh, hand, (255, 250, 236), lerp(col_a, col_b, 0.5))
        arm.alpha_composite(sleeve)
        hm = Image.new("L", size, 0)
        ImageDraw.Draw(hm).ellipse([hand[0] - S * 0.016, hand[1] - S * 0.016, hand[0] + S * 0.016, hand[1] + S * 0.016], fill=255)
        arm.alpha_composite(axial_fill(hm, hand, hand, WHITE, WHITE))
        up = (math.cos(a2) * side, math.sin(a2))
        for j, col in enumerate((col_a, col_b)):
            off = S * (0.045 + 0.03 * j)
            orbs.append(((hand[0] + up[0] * off * 0.6 + side * S * 0.012 * (j * 2 - 1), hand[1] + up[1] * off * 0.6 - S * (0.04 + 0.03 * j)), col))
    layers.append(glow_of(arm, S * 0.015, WHITE, 1.0))
    layers.append(arm)
    # 6) 가슴의 네 원소 결정
    gem = Image.new("RGBA", size, (0, 0, 0, 0))
    dg = ImageDraw.Draw(gem)
    gc = (cx, S * 0.43)
    gs = S * 0.03
    for k, col in enumerate((FIRE, WATER, EARTH, WIND)):
        a = k / 4 * math.tau
        p0 = (gc[0] + math.sin(a) * gs * 1.7, gc[1] - math.cos(a) * gs * 1.7)
        p1 = (gc[0] + math.sin(a + 0.6) * gs * 0.5, gc[1] - math.cos(a + 0.6) * gs * 0.5)
        p2 = (gc[0] + math.sin(a - 0.6) * gs * 0.5, gc[1] - math.cos(a - 0.6) * gs * 0.5)
        dg.polygon([gc, p1, p0, p2], fill=(*col, 255))
    dg.ellipse([gc[0] - gs * 0.35, gc[1] - gs * 0.35, gc[0] + gs * 0.35, gc[1] + gs * 0.35], fill=(*WHITE, 255))
    layers.append(glow_of(gem, S * 0.02, WHITE, 1.6 + pulse))
    layers.append(gem)
    # 7) 머리: 빛 갈기 · 빛나는 얼굴 (눈만) · 원소 왕관
    head = Image.new("RGBA", size, (0, 0, 0, 0))
    dh = ImageDraw.Draw(head)
    for k in range(-5, 6):
        x = hc[0] + k * S * 0.012
        dh.line([(x, hc[1] - S * 0.03), (x + k * S * 0.016, hc[1] + S * 0.11)], fill=(255, 244, 214, 170), width=int(S * 0.01))
    fw, fh = S * 0.046, S * 0.06
    dh.ellipse([hc[0] - fw, hc[1] - fh, hc[0] + fw, hc[1] + fh], fill=(255, 252, 244, 255))
    for side in (-1, 1):
        ex = hc[0] + side * fw * 0.42
        ey = hc[1] - fh * 0.02
        dh.polygon([(ex - S * 0.012, ey + S * 0.002), (ex, ey - S * 0.005), (ex + S * 0.012, ey + S * 0.002), (ex, ey + S * 0.006)], fill=(110, 226, 255, 255))
    crown_y = hc[1] - fh * 0.78
    dh.rectangle([hc[0] - fw * 1.05, crown_y - S * 0.011, hc[0] + fw * 1.05, crown_y + S * 0.011], fill=(*GOLD, 255))
    for k, col in enumerate((FIRE, WATER, WIND, EARTH)):
        x = hc[0] + (k - 1.5) * fw * 0.62
        topy = crown_y - S * (0.05 if k in (1, 2) else 0.037)
        dh.polygon([(x - S * 0.013, crown_y - S * 0.009), (x, topy), (x + S * 0.013, crown_y - S * 0.009)], fill=(*GOLD, 255))
        r = S * 0.009
        dh.ellipse([x - r, topy - r * 0.3, x + r, topy + r * 1.7], fill=(*col, 255))
    layers.append(glow_of(head, S * 0.02, (255, 240, 200), 1.4))
    layers.append(head)
    # 8) 원소 구슬 (손 위)
    orb_l = Image.new("RGBA", size, (0, 0, 0, 0))
    for (x, y), col in orbs:
        r = S * (0.024 + 0.008 * pulse)
        orb_l.alpha_composite(radial(size, (x, y), r * 2.6, (*col, 220), (*col, 0), 1.2))
        orb_l.alpha_composite(radial(size, (x, y), r, (255, 255, 255, 255), (*col, 210), 0.8))
    layers.append(orb_l)
    return shrink(over(*layers), (512, 512))


def spirit_king_sheet():
    frames = [king_frame(0.0, 0.0), king_frame(0.45, 0.2), king_frame(1.0, 0.5), king_frame(1.0, 1.0)]
    sheet = Image.new("RGBA", (1024, 1024), (0, 0, 0, 0))
    for i, f in enumerate(frames):
        sheet.alpha_composite(f, ((i % 2) * 512, (i // 2) * 512))
    save("rfx_spiritking", sheet, 512, 512, "rotate_xyz")


if __name__ == "__main__":
    spirit_king_sheet()
    relic_sheets()
    clock_sheet()
    serpent_sheet()
    import java_fx_art_more  # 세계수의 도끼 · 스발린 (0.12.4)
    java_fx_art_more.main()
    import java_fx_limited  # 0.16.0 한정 초월 룬 (스탯 구슬 · 천공의 눈)
    java_fx_limited.main()
