#!/usr/bin/env python3
"""0.27.0 직업 스킬 연출 그림 (메이플식 2D 그림 — 룬 연출의 바닥 문양 · 룬 글자 대신) → tools/java_fx (rpg:rfx_j* — 룬 연출 rfx 와 같은 꼴).

  python3 tools/job_fx_art.py                         # 그림 · 파티클 정의를 tools/java_fx 에 씀
  python3 tools/addon_particles.py <애드온 폴더>       # 그다음 자바 파티클 정의 · 그림 · 이름표를 다시 만듦
  python3 tools/job_fx_art.py --sheet <폴더>          # 확인용: 그림마다 칸을 늘어놓은 그림 (어두운 바탕 · 하늘색 / 흰색을 입힘)

모두 흰색 (밝기 = 알파) 으로 그려 게임에서 색을 입힘. 칸 = 빛 (넓고 흐린 겉) 장면들 다음에 심지 (가늘고 또렷한 속) 장면들 — 서버는 frames · fps 로 차례로 넘김
(JobFx — 빛 칸 0, 심지 칸 = 장 수).
- rfx_jslash  카메라를 보는 큰 베기 (256×128 × 8장): 왼쪽에서 아래를 지나 오른쪽으로 휘두르는 초승달이 그려지고 (머리가 굵음) 꼬리부터 사라짐, 속도 줄 · 머리의 반짝임
- rfx_jhit    맞은 자리 (128×128 × 6장): 번쩍 · 대각선 베인 줄 · 퍼지는 고리 · 튀는 조각
- rfx_jring   땅에 눕는 둥근 베기 (192×192 × 8장): 12시에서 시계 방향으로 한 바퀴 도는 빛 머리 + 뒤로 흐려지는 꼬리, 다 돌면 고리 전체가 빛나다 사라짐
- rfx_jdash   돌진 줄기 (방향으로 누움 — 오른쪽이 앞, 256×64 × 6장): 앞에서 뒤로 뻗는 빠른 줄 여럿, 앞부터 줄어듦
- rfx_jline   일섬 (방향으로 누움, 256×32 × 6장): 가는 선이 한 번에 그어지고 가운데가 부풀었다 가늘어지며 사라짐
- rfx_jburst  터짐 (카메라를 봄, 192×192 × 8장): 가운데 번쩍 · 퍼지는 충격 고리 · 빛 갈래 여덟 · 튀는 알갱이
- rfx_jaura   몸에서 솟는 빛 (세워 카메라 쪽으로 돎, 128×256 × 8장 — 이어 돌림): 발밑이 넓고 위로 갈수록 가는 불꽃 줄기가 위로 흐름
- rfx_jspeed  빠른 줄 (카메라를 봄, 256×256 × 4장 — 이어 돌림): 가장자리에서 가운데 쪽으로 뻗는 줄 (가운데는 비움) — 1인칭 눈앞에만 (돌진 · 도약)
"""
import math
import os
import random
import sys

from PIL import Image, ImageChops, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from java_fx_art import save  # noqa: E402 — 같은 꼴의 파티클 정의 · tools/java_fx 에 씀

SS = 2  # 슈퍼샘플


# ------------------------------------------------------------ 그리기 도움 (L 마스크 — 흰색 = 알파)

class Cell:
    def __init__(self, w, h):
        self.w, self.h = w, h
        self.im = Image.new("L", (w * SS, h * SS), 0)
        self.d = ImageDraw.Draw(self.im)

    def poly(self, pts, v=255):
        if len(pts) >= 3:
            self.d.polygon([(x * SS, y * SS) for x, y in pts], fill=int(v))

    def disc(self, cx, cy, r, v=255):
        if r > 0:
            self.d.ellipse([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], fill=int(v))

    def line(self, pts, w, v=255):
        if w > 0 and len(pts) >= 2:
            self.d.line([(x * SS, y * SS) for x, y in pts], fill=int(v), width=max(1, int(round(w * SS))), joint="curve")

    def ring(self, cx, cy, r, w, v=255):
        if r > 0 and w > 0:
            self.d.ellipse([(cx - r) * SS, (cy - r) * SS, (cx + r) * SS, (cy + r) * SS], outline=int(v), width=max(1, int(round(w * SS))))

    def out(self):
        return self.im.resize((self.w, self.h), Image.LANCZOS)


def lighter(*ims):
    out = ims[0]
    for im in ims[1:]:
        out = ImageChops.lighter(out, im)
    return out


def add(*ims):
    out = ims[0]
    for im in ims[1:]:
        out = ImageChops.add(out, im)
    return out


def scale(im, k):
    return im.point(lambda v: max(0, min(255, int(v * k))))


def blur(im, r):
    return im.filter(ImageFilter.GaussianBlur(r)) if r > 0 else im


def rgba(alpha, white=255):
    """알파 마스크 → 흰색 RGBA (색은 게임에서 입힘)"""
    img = Image.new("RGBA", alpha.size, (white, white, white, 0))
    img.putalpha(alpha)
    return img


def star(c, cx, cy, r, thin=0.18, rot=0.0, v=255):
    pts = []
    for i in range(8):
        a = math.radians(rot + i * 45)
        rr = r if i % 2 == 0 else r * thin
        pts.append((cx + math.cos(a) * rr, cy + math.sin(a) * rr))
    c.poly(pts, v)


def sheet(cells, cols):
    w, h = cells[0].size
    rows = (len(cells) + cols - 1) // cols
    out = Image.new("RGBA", (w * cols, h * rows), (0, 0, 0, 0))
    for i, c in enumerate(cells):
        out.alpha_composite(c, ((i % cols) * w, (i // cols) * h))
    return out


def ease_out(t):
    t = max(0.0, min(1.0, t))
    return 1 - (1 - t) ** 2


# ------------------------------------------------------------ 큰 베기 (rfx_jslash)

def band(c, pts_outer, normals, widths, v=255):
    """바깥 줄 pts_outer 에서 안쪽 (normals) 으로 widths 만큼 두께인 띠"""
    inner = [(x + nx * w, y + ny * w) for (x, y), (nx, ny), w in zip(pts_outer, normals, widths)]
    c.poly(list(pts_outer) + inner[::-1], v)


def slash_frame(f, frames=8, W=256, H=128, core=False):
    cx, cy, rx, ry = W / 2, H * 0.27, W * 0.455, H * 0.56
    head = min(1.0, (f + 1) / 3.5)
    tail = 0.0 if f < 3 else min(0.94, (f - 2) / (frames - 2.6))
    th0, th1 = 172.0, 8.0

    def P(u):
        a = math.radians(th0 + (th1 - th0) * u)
        return cx + math.cos(a) * rx, cy + math.sin(a) * ry

    def Nrm(u):
        x, y = P(u)
        dx, dy = cx - x, cy - y
        L = math.hypot(dx, dy) or 1
        return dx / L, dy / L

    n = 64
    us = [tail + (head - tail) * i / n for i in range(n + 1)]
    fade = 1.0 if f < frames - 3 else max(0.25, 1 - (f - (frames - 3)) * 0.28)
    Wmax = (H * (0.19 if core else 0.34)) * (1.0 if f < 4 else max(0.45, 1 - (f - 3) * 0.14))

    def prof(u):
        s = (u - tail) / max(1e-6, head - tail)
        return Wmax * (math.sin(math.pi * min(1.0, s * 1.08)) ** 0.55) * (0.3 + 0.7 * s)

    main = Cell(W, H)
    band(main, [P(u) for u in us], [Nrm(u) for u in us], [prof(u) for u in us], 255)
    layers = [main.out()]
    if not core:
        # 속도 줄: 바깥 (가운데 반대쪽) 가까이에 가는 호 둘 — 머리 쪽 일부만 (휘두른 쪽으로 남는 바람)
        sp = Cell(W, H)
        for k, (off, part, wk) in enumerate(((0.16, 0.55, 0.045), (0.34, 0.35, 0.03))):
            u0 = max(tail, head - (head - tail) * part)
            pts = []
            for i in range(41):
                u = u0 + (head - u0) * i / 40
                x, y = P(u)
                nx, ny = Nrm(u)
                pts.append((x - nx * Wmax * off, y - ny * Wmax * off))
            sp.line(pts, max(1.0, H * wk), 170 - 40 * k)
        layers.append(sp.out())
        g = lighter(*layers)
        return scale(add(scale(g, 0.75), scale(blur(g, 6), 1.1)), fade)
    # 심지: 머리의 번쩍 · 반짝임
    hx, hy = P(head)
    fl = Cell(W, H)
    if 1 <= f <= 5:
        k = 1 - abs(f - 2.5) / 3.0
        star(fl, hx, hy + Wmax * 0.3, H * (0.14 + 0.1 * k), 0.16, 0, 255)
        fl.disc(hx, hy + Wmax * 0.3, H * 0.05 * (0.6 + k), 255)
    if 2 <= f <= 6:
        rnd = random.Random(f * 31 + 7)
        for _ in range(4):
            u = rnd.uniform(tail, head)
            x, y = P(u)
            nx, ny = Nrm(u)
            r = H * rnd.uniform(0.03, 0.06)
            star(fl, x - nx * rnd.uniform(4, 14), y - ny * rnd.uniform(4, 14), r * 2.2, 0.2, 0, 220)
    return scale(lighter(*layers, fl.out()), fade)


def slash_sheet():
    frames = 8
    cells = [rgba(slash_frame(f, frames, core=False)) for f in range(frames)] + [rgba(slash_frame(f, frames, core=True)) for f in range(frames)]
    save("rfx_jslash", sheet(cells, 4), 256, 128, "rotate_xyz")
    return cells


# ------------------------------------------------------------ 맞은 자리 (rfx_jhit)

def hit_frame(f, frames=6, S=128, core=False):
    c = S / 2
    p = f / (frames - 1)
    rnd = random.Random(11)
    m = Cell(S, S)
    # 번쩍 (처음 둘)
    if f <= 2:
        m.disc(c, c, S * (0.1 + 0.12 * p) * (1.4 if not core else 1.0), 255 * (1 - f / 3.2))
    # 대각선 베인 줄 (가운데가 굵고 끝이 뾰족 — 처음에 길어지고 뒤에 가늘어짐)
    L = S * (0.28 + 0.2 * ease_out(min(1, f / 2.0)))
    w = S * (0.11 if not core else 0.055) * (1.0 if f <= 2 else max(0.15, 1 - (f - 2) * 0.3))
    a = math.radians(-35)
    ux, uy = math.cos(a), math.sin(a)
    px, py = -uy, ux
    m.poly([(c - ux * L, c - uy * L), (c + px * w, c + py * w), (c + ux * L, c + uy * L), (c - px * w, c - py * w)], 255 * (1 if f < 4 else 0.6))
    # 퍼지는 고리
    r = S * (0.12 + 0.3 * ease_out(p))
    m.ring(c, c, r, S * (0.05 if not core else 0.025) * (1 - p * 0.8), 230 * (1 - p))
    # 튀는 조각 (가는 세모)
    for i in range(7):
        ang = math.radians(i * 360 / 7 + rnd.uniform(-18, 18))
        r0 = S * (0.2 + 0.42 * ease_out(p))
        r1 = r0 + S * (0.1 - 0.05 * p)
        wd = S * 0.025 * (1 - p * 0.7)
        bx, by = c + math.cos(ang) * r0, c + math.sin(ang) * r0
        tx, ty = c + math.cos(ang) * r1, c + math.sin(ang) * r1
        qx, qy = -math.sin(ang) * wd, math.cos(ang) * wd
        m.poly([(bx + qx, by + qy), (tx, ty), (bx - qx, by - qy)], 255 * (1 - p * 0.85))
    out = m.out()
    if not core:
        out = add(scale(out, 0.7), scale(blur(out, 4), 1.2))
    return out


def hit_sheet():
    frames = 6
    cells = [rgba(hit_frame(f, frames, core=False)) for f in range(frames)] + [rgba(hit_frame(f, frames, core=True)) for f in range(frames)]
    save("rfx_jhit", sheet(cells, 6), 128, 128, "rotate_xyz")
    return cells


# ------------------------------------------------------------ 둥근 베기 (rfx_jring — 땅에 눕힘)

def ring_frame(f, frames=8, S=192, core=False):
    c = S / 2
    R = S * 0.44
    sweep = min(1.0, (f + 1) / 5.0)  # 머리가 간 몫 (한 바퀴 = 1)
    trail = 0.62  # 꼬리 길이 (바퀴의 몫)
    after = max(0, f - 4)  # 다 돈 뒤
    m = Cell(S, S)
    n = 120
    seg = []
    for i in range(n + 1):
        t = i / n
        if after:
            # 다 돈 뒤: 고리 전체가 고르게 빛나다 사라짐 (12시 쪽이 조금 밝음)
            seg.append((t, 0.45 + 0.4 * t ** 3))
            continue
        u = sweep - trail * (1 - t)  # 꼬리 → 머리
        if u < 0:
            continue
        seg.append((u, t ** 1.6))  # 머리 쪽이 밝고 굵음
    wmax = S * (0.07 if not core else 0.03) * (1 - after * 0.18)
    for (u0, k0), (u1, k1) in zip(seg, seg[1:]):
        a0 = math.radians(-90 + 360 * u0)
        a1 = math.radians(-90 + 360 * u1)
        w0, w1 = wmax * (0.25 + 0.75 * k0), wmax * (0.25 + 0.75 * k1)
        pts = [(c + math.cos(a0) * (R + w0 / 2), c + math.sin(a0) * (R + w0 / 2)), (c + math.cos(a1) * (R + w1 / 2), c + math.sin(a1) * (R + w1 / 2)),
               (c + math.cos(a1) * (R - w1 / 2), c + math.sin(a1) * (R - w1 / 2)), (c + math.cos(a0) * (R - w0 / 2), c + math.sin(a0) * (R - w0 / 2))]
        m.poly(pts, 255 * (0.3 + 0.7 * k1) * (1 - after * 0.2))
    # 머리의 반짝임 (돌고 있는 동안)
    if after == 0:
        ah = math.radians(-90 + 360 * sweep)
        star(m, c + math.cos(ah) * R, c + math.sin(ah) * R, S * (0.09 if core else 0.12), 0.18, 0, 255)
    # 안쪽 속도 고리 (흐림)
    if not core:
        m.ring(c, c, R * 0.82, S * 0.012, 70 * (1 - after * 0.2))
    out = m.out()
    if not core:
        out = add(scale(out, 0.75), scale(blur(out, 5), 1.15))
    return out


def ring_sheet():
    frames = 8
    cells = [rgba(ring_frame(f, frames, core=False)) for f in range(frames)] + [rgba(ring_frame(f, frames, core=True)) for f in range(frames)]
    save("rfx_jring", sheet(cells, 4), 192, 192, "direction_z")
    return cells


# ------------------------------------------------------------ 돌진 줄기 (rfx_jdash — 오른쪽이 앞)

def dash_frame(f, frames=6, W=256, H=64, core=False):
    rnd = random.Random(5)
    lines = [(rnd.uniform(0.12, 0.88), rnd.uniform(0.45, 0.95), rnd.uniform(0.0, 0.12), rnd.uniform(0.6, 1.0)) for _ in range(9)]
    grow = ease_out(min(1.0, (f + 1) / 2.5))
    shrink_ = 0.0 if f < 2 else (f - 1) / (frames - 1)
    m = Cell(W, H)
    for y, length, off, bright in lines:
        x1 = W * (1 - off) - W * length * shrink_ * 0.9
        x0 = W * (1 - off) - W * length * grow
        if x1 - x0 < 2:
            continue
        thick = H * (0.07 if not core else 0.035) * (1 - shrink_ * 0.6) * (0.6 + 0.4 * bright)
        yy = H * y
        m.poly([(x0, yy), (x1 - thick * 1.5, yy - thick), (x1, yy), (x1 - thick * 1.5, yy + thick)], 255 * bright * (1 - shrink_ * 0.5))
    out = m.out()
    if not core:
        out = add(scale(out, 0.7), scale(blur(out, 3), 1.2))
    return out


def dash_sheet():
    frames = 6
    cells = [rgba(dash_frame(f, frames, core=False)) for f in range(frames)] + [rgba(dash_frame(f, frames, core=True)) for f in range(frames)]
    save("rfx_jdash", sheet(cells, 4), 256, 64, "lookat_direction")
    return cells


# ------------------------------------------------------------ 일섬 (rfx_jline)

def line_frame(f, frames=6, W=256, H=32, core=False):
    m = Cell(W, H)
    c = H / 2
    if f == 0:
        th = H * 0.04
    elif f <= 2:
        th = H * (0.18 if f == 1 else 0.3) * (1 if not core else 0.5)
    else:
        th = H * 0.3 * (1 if not core else 0.5) * max(0.1, 1 - (f - 2) * 0.32)
    v = 255 if f < 4 else 255 * (1 - (f - 3) * 0.3)
    m.poly([(2, c), (W * 0.5, c - th), (W - 2, c), (W * 0.5, c + th)], v)
    if f >= 2 and not core:
        rnd = random.Random(f)
        for _ in range(6):
            x = rnd.uniform(W * 0.15, W * 0.85)
            star(m, x, c + rnd.uniform(-th, th), H * 0.18, 0.2, 0, 180 * (1 - (f - 2) * 0.25))
    out = m.out()
    if not core:
        out = add(scale(out, 0.8), scale(blur(out, 2.5), 1.3))
    return out


def line_sheet():
    frames = 6
    cells = [rgba(line_frame(f, frames, core=False)) for f in range(frames)] + [rgba(line_frame(f, frames, core=True)) for f in range(frames)]
    save("rfx_jline", sheet(cells, 2), 256, 32, "lookat_direction")
    return cells


# ------------------------------------------------------------ 터짐 (rfx_jburst)

def burst_frame(f, frames=8, S=192, core=False):
    c = S / 2
    p = f / (frames - 1)
    m = Cell(S, S)
    if f <= 3:
        m.disc(c, c, S * (0.12 + 0.1 * p) * (1.3 if not core else 0.8), 255 * (1 - f / 4.5))
    r = S * (0.1 + 0.36 * ease_out(p))
    m.ring(c, c, r, S * (0.06 if not core else 0.025) * (1 - p * 0.85), 255 * (1 - p ** 1.5))
    if f <= 4:
        q = f / 4.0
        for i in range(8):
            ang = math.radians(i * 45 + 22.5 * (i % 2))
            L0 = S * (0.05 + 0.3 * ease_out(q))
            L1 = L0 + S * (0.24 if i % 2 == 0 else 0.15) * (1 - q * 0.7)
            wd = S * (0.03 if not core else 0.015) * (1 - q * 0.7)
            bx, by = c + math.cos(ang) * L0, c + math.sin(ang) * L0
            tx, ty = c + math.cos(ang) * L1, c + math.sin(ang) * L1
            qx, qy = -math.sin(ang) * wd, math.cos(ang) * wd
            m.poly([(bx + qx, by + qy), (tx, ty), (bx - qx, by - qy)], 255 * (1 - q * 0.8))
    rnd = random.Random(23)
    for _ in range(12):
        ang = rnd.uniform(0, math.tau)
        rr = S * (0.12 + 0.4 * ease_out(p) * rnd.uniform(0.6, 1.0))
        m.disc(c + math.cos(ang) * rr, c + math.sin(ang) * rr, S * 0.012 * (1 - p * 0.6) * (1.5 if not core else 1), 220 * (1 - p))
    out = m.out()
    if not core:
        out = add(scale(out, 0.7), scale(blur(out, 5), 1.2))
    return out


def burst_sheet():
    frames = 8
    cells = [rgba(burst_frame(f, frames, core=False)) for f in range(frames)] + [rgba(burst_frame(f, frames, core=True)) for f in range(frames)]
    save("rfx_jburst", sheet(cells, 4), 192, 192, "rotate_xyz")
    return cells


# ------------------------------------------------------------ 솟는 빛 (rfx_jaura — 이어 돌림)

def aura_frame(f, frames=8, W=128, H=256, core=False):
    m = Cell(W, H)
    ph = f / frames
    cx = W / 2
    for k in range(5):
        base = (k - 2) * W * 0.16
        hgt = H * (0.55 + 0.35 * ((k * 37) % 10) / 10)
        wd = W * (0.16 if not core else 0.07) * (1.0 - abs(k - 2) * 0.12)
        pts_l, pts_r = [], []
        n = 40
        for i in range(n + 1):
            t = i / n  # 0 = 발밑, 1 = 끝
            y = H - 4 - t * hgt
            sway = math.sin((t * 2.2 - ph) * math.tau + k) * W * 0.05 * t
            w = wd * (1 - t) ** 0.8 * (0.7 + 0.3 * math.sin((t * 3 - ph) * math.tau + k * 2))
            x = cx + base * (1 - 0.5 * t) + sway
            pts_l.append((x - w, y))
            pts_r.append((x + w, y))
        m.poly(pts_l + pts_r[::-1], 200 if k % 2 == 0 else 150)
    if not core:
        m.d.ellipse([(cx - W * 0.42) * SS, (H - H * 0.07) * SS, (cx + W * 0.42) * SS, (H - 1) * SS], fill=170)
    out = m.out()
    if not core:
        out = add(scale(out, 0.6), scale(blur(out, 5), 1.25))
    # 위로 갈수록 흐림
    grad = Image.linear_gradient("L").resize((W, H))  # 위 0 → 아래 255
    out = ImageChops.multiply(out, grad.point(lambda v: min(255, int(60 + v * 0.9))))
    return out


def aura_sheet():
    frames = 8
    cells = [rgba(aura_frame(f, frames, core=False)) for f in range(frames)] + [rgba(aura_frame(f, frames, core=True)) for f in range(frames)]
    save("rfx_jaura", sheet(cells, 8), 128, 256, "lookat_y")
    return cells


# ------------------------------------------------------------ 빠른 줄 (rfx_jspeed — 1인칭 눈앞 화면 가장자리)

def speed_frame(f, frames=4, S=256, core=False):
    """화면 가장자리에서 가운데 쪽으로 뻗는 빠른 줄 (가운데는 비움 — 장마다 다른 줄이라 이어 돌리면 깜빡이며 흐름)"""
    rnd = random.Random(100 + f)
    m = Cell(S, S)
    c = S / 2
    for _ in range(28):
        ang = rnd.uniform(0, math.tau)
        r0 = c * rnd.uniform(0.6, 0.82)
        r1 = c * 1.08
        wd = c * rnd.uniform(0.01, 0.024) * (0.55 if core else 1.0)
        ux, uy = math.cos(ang), math.sin(ang)
        px, py = -uy, ux
        m.poly([(c + ux * r0, c + uy * r0), (c + ux * r1 + px * wd, c + uy * r1 + py * wd), (c + ux * r1 - px * wd, c + uy * r1 - py * wd)],
               rnd.uniform(150, 255))
    out = m.out()
    if not core:
        out = add(scale(out, 0.7), scale(blur(out, 2), 1.1))
    return out


def speed_sheet():
    frames = 4
    cells = [rgba(speed_frame(f, frames, core=False)) for f in range(frames)] + [rgba(speed_frame(f, frames, core=True)) for f in range(frames)]
    save("rfx_jspeed", sheet(cells, 4), 256, 256, "rotate_xyz")
    return cells


# ------------------------------------------------------------ 확인용 그림

def preview(name, cells, frames, tint, folder, k=1.0):
    """칸을 장 순서로 (위 = 빛 · 아래 = 심지 · 맨 아래 = 겹친 것) 어두운 바탕에"""
    w, h = cells[0].size
    tw, th = int(w * k), int(h * k)
    out = Image.new("RGBA", (tw * frames, th * 3), (18, 20, 30, 255))
    for i in range(frames):
        g = cells[i].resize((tw, th))
        c = cells[frames + i].resize((tw, th))
        gt = Image.new("RGBA", (tw, th), tint + (0,))
        gt.putalpha(g.split()[3])
        out.alpha_composite(gt, (i * tw, 0))
        out.alpha_composite(c, (i * tw, th))
        out.alpha_composite(gt, (i * tw, th * 2))
        out.alpha_composite(c, (i * tw, th * 2))
    out.save(os.path.join(folder, f"{name}.png"))


def main():
    made = {
        "rfx_jslash": (slash_sheet(), 8),
        "rfx_jhit": (hit_sheet(), 6),
        "rfx_jring": (ring_sheet(), 8),
        "rfx_jdash": (dash_sheet(), 6),
        "rfx_jline": (line_sheet(), 6),
        "rfx_jburst": (burst_sheet(), 8),
        "rfx_jaura": (aura_sheet(), 8),
        "rfx_jspeed": (speed_sheet(), 4),
    }
    if "--sheet" in sys.argv:
        folder = sys.argv[sys.argv.index("--sheet") + 1]
        os.makedirs(folder, exist_ok=True)
        for name, (cells, frames) in made.items():
            preview(name, cells, frames, (110, 200, 255), folder, 0.75 if cells[0].size[0] >= 192 else 1.0)


if __name__ == "__main__":
    main()
