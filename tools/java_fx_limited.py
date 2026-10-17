#!/usr/bin/env python3
"""0.16.0 한정 초월 룬 연출 그림 (rpg:rfx_* — 애드온 rfx 파티클과 같은 형식) → tools/java_fx (tools/java_fx_art.py 가 함께 부름).

  python3 tools/java_fx_limited.py      # 그림 · 파티클 정의를 tools/java_fx 에 씀
  python3 tools/addon_particles.py <애드온 폴더>   # 그다음 자바 파티클 정의 · 그림 · 이름표 (AddonFxTable) 를 다시 만듦

그림
- rfx_orb     만상귀일의 스탯 구슬 (128×128 칸, 8 × 2줄 — 흰색, 색을 입힘): 0~3 빛 (부드러운 구슬 + 세 갈래 소용돌이 — 칸마다 30° 돎),
              4~7 심지 (밝은 가운데 · 가는 고리 · 반짝임 넷 — 칸마다 22.5° 돎), 8 무리 (무늬 없이 가장자리까지 부드러운 원 — 칠성 강림의
              밤빛 무리: 어두운 색을 입혀 낮 하늘에서 별 둘레를 어둡게, 알파 섞기라 어두운 색도 보임). rotate_xyz (보는 사람을 향함).
              룬 연출에서 빛 (cell 0, frames 4) 과 심지 (cell 4, frames 4) 를 두 장 겹침 (RuneKit.rfx2 — 빛 = 스탯 색, 심지 = 흰빛)
- rfx_skyeye  천공의 눈 (256×256 × 4장 — 미리 칠한 색: 금빛 마법진 · 흰 눈 · 해빛 눈동자): 0 감음 → 1 반쯤 → 2 뜸 → 3 뜬 채 빛남.
              direction_z (dir 이 판이 보는 쪽 — 하늘에서 시전자 눈 쪽으로). 점 그림 (128 판을 2배)
"""
import math
import os
import sys

from PIL import Image, ImageDraw, ImageFilter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import java_fx_art  # noqa: E402 — 같은 파티클 정의 꼴 (particle) · 저장

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))


# ------------------------------------------------------------ 구슬

def _radial(size, cx, cy, r, inner, outer, power=1.8):
    """가운데 inner 알파 → r 에서 outer 알파 (흰색)"""
    img = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    px = img.load()
    for y in range(size):
        for x in range(size):
            d = math.hypot(x + 0.5 - cx, y + 0.5 - cy) / r
            if d >= 1:
                continue
            t = (1 - d) ** power
            a = outer + (inner - outer) * t
            px[x, y] = (255, 255, 255, int(max(0, min(255, a))))
    return img


def orb_glow(frame):
    """빛: 부드러운 구슬 (가장자리까지 옅게) + 세 갈래 소용돌이 팔 (칸마다 30° 돎) + 테두리 빛무리"""
    S = 128
    ss = 2
    big = S * ss
    c = big / 2
    img = _radial(big, c, c, big * 0.48, 210, 0, 1.25)
    arms = Image.new("RGBA", (big, big), (255, 255, 255, 0))
    d = ImageDraw.Draw(arms)
    rot = math.radians(frame * 30)
    for k in range(3):
        base = rot + k * math.tau / 3
        pts = []
        for i in range(40):
            t = i / 39
            ang = base + t * 2.6
            rr = big * (0.08 + 0.34 * t)
            pts.append((c + math.cos(ang) * rr, c + math.sin(ang) * rr))
        for i in range(len(pts) - 1):
            w = int(big * 0.05 * (1 - i / len(pts)) + 2)
            a = int(200 * (1 - i / len(pts)) + 40)
            d.line([pts[i], pts[i + 1]], fill=(255, 255, 255, a), width=w)
    arms = arms.filter(ImageFilter.GaussianBlur(big * 0.012))
    img.alpha_composite(arms)
    ring = Image.new("RGBA", (big, big), (255, 255, 255, 0))
    ImageDraw.Draw(ring).ellipse((c - big * 0.36, c - big * 0.36, c + big * 0.36, c + big * 0.36), outline=(255, 255, 255, 120), width=int(big * 0.02))
    img.alpha_composite(ring.filter(ImageFilter.GaussianBlur(big * 0.01)))
    return img.resize((S, S), Image.LANCZOS)


def orb_core(frame):
    """심지: 밝은 가운데 (작은 원 + 빛무리) · 가는 고리 · 네 갈래 반짝임 (칸마다 22.5° 돎)"""
    S = 128
    ss = 2
    big = S * ss
    c = big / 2
    img = _radial(big, c, c, big * 0.2, 255, 0, 1.4)
    dot = Image.new("RGBA", (big, big), (255, 255, 255, 0))
    dd = ImageDraw.Draw(dot)
    r0 = big * 0.07
    dd.ellipse((c - r0, c - r0, c + r0, c + r0), fill=(255, 255, 255, 255))
    rr = big * 0.27
    dd.ellipse((c - rr, c - rr, c + rr, c + rr), outline=(255, 255, 255, 170), width=max(2, int(big * 0.012)))
    rot = math.radians(frame * 22.5)
    for k in range(4):
        a = rot + k * math.pi / 2
        x0, y0 = c + math.cos(a) * big * 0.1, c + math.sin(a) * big * 0.1
        x1, y1 = c + math.cos(a) * big * 0.42, c + math.sin(a) * big * 0.42
        nx, ny = -math.sin(a) * big * 0.018, math.cos(a) * big * 0.018
        dd.polygon([(x0 + nx, y0 + ny), (x1, y1), (x0 - nx, y0 - ny)], fill=(255, 255, 255, 200))
    img.alpha_composite(dot.filter(ImageFilter.GaussianBlur(big * 0.004)))
    return img.resize((S, S), Image.LANCZOS)


def orb_haze():
    """무리: 무늬 없는 부드러운 원 (가운데 알파 235 → 가장자리 0, 칸 가장자리 전에 끝남)"""
    S = 128
    ss = 2
    big = S * ss
    c = big / 2
    return _radial(big, c, c, big * 0.48, 235, 0, 1.5).resize((S, S), Image.LANCZOS)


def orb_sheet():
    sheet = Image.new("RGBA", (128 * 8, 128 * 2), (255, 255, 255, 0))
    for i in range(4):
        sheet.paste(orb_glow(i), (i * 128, 0))
        sheet.paste(orb_core(i), ((4 + i) * 128, 0))
    sheet.paste(orb_haze(), (0, 128))
    return sheet


# ------------------------------------------------------------ 천공의 눈

GOLD = (255, 206, 92, 255)
GOLD_D = (176, 116, 30, 255)
GOLD_L = (255, 240, 170, 255)
WHITE = (255, 252, 240, 255)
SCLERA = (236, 246, 255, 255)
IRIS_O = (255, 150, 30, 255)
IRIS_I = (255, 214, 90, 255)
PUPIL = (46, 22, 10, 255)


def _eye_mask(S, cx, cy, w, h):
    """아몬드 눈 (가로 반 w · 세로 반 h — 위아래 원호)"""
    m = Image.new("L", (S, S), 0)
    px = m.load()
    for y in range(S):
        for x in range(S):
            u = (x + 0.5 - cx) / w
            if abs(u) >= 1:
                continue
            lim = h * (1 - u * u) ** 0.85
            if abs(y + 0.5 - cy) <= lim:
                px[x, y] = 255
    return m


def eye_frame(open_t, shine):
    """128 판: 금빛 이중 고리 + 눈금 12 + 네 방위 세모, 안쪽 고리, 가운데 눈 (open_t 0 = 감음 → 1 = 뜸), shine = 눈동자 빛"""
    S = 128
    c = S / 2
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    # 바깥 이중 고리
    d.ellipse((4, 4, S - 5, S - 5), outline=GOLD_D, width=3)
    d.ellipse((5, 5, S - 6, S - 6), outline=GOLD, width=1)
    d.ellipse((12, 12, S - 13, S - 13), outline=GOLD, width=1)
    # 고리 사이 룬 눈금 · 점
    for k in range(24):
        a = k * math.tau / 24
        r0, r1 = (S / 2 - 12, S / 2 - 6) if k % 2 == 0 else (S / 2 - 10, S / 2 - 8)
        d.line([(c + math.cos(a) * r0, c + math.sin(a) * r0), (c + math.cos(a) * r1, c + math.sin(a) * r1)], fill=GOLD_L if k % 6 == 0 else GOLD, width=1)
    # 네 방위 세모 (바깥으로)
    for k in range(4):
        a = k * math.pi / 2 - math.pi / 2
        tip = (c + math.cos(a) * (S / 2 - 1), c + math.sin(a) * (S / 2 - 1))
        l = (c + math.cos(a - 0.09) * (S / 2 - 9), c + math.sin(a - 0.09) * (S / 2 - 9))
        r = (c + math.cos(a + 0.09) * (S / 2 - 9), c + math.sin(a + 0.09) * (S / 2 - 9))
        d.polygon([tip, l, r], fill=GOLD_L, outline=GOLD_D)
    # 안쪽 고리 (눈 둘레) + 여덟 갈래 빛살
    d.ellipse((24, 24, S - 25, S - 25), outline=GOLD_D, width=2)
    for k in range(8):
        a = k * math.tau / 8 + math.tau / 16
        d.line([(c + math.cos(a) * 26, c + math.sin(a) * 26), (c + math.cos(a) * 36, c + math.sin(a) * 36)], fill=GOLD, width=1)
    # 눈
    w, hmax = 30.0, 17.0
    h = max(0.6, hmax * open_t)
    if open_t > 0.05:
        m = _eye_mask(S, c, c, w, h)
        sclera = Image.new("RGBA", (S, S), SCLERA)
        img.paste(sclera, (0, 0), m)
        # 눈동자 (가운데 · 감기면 눈꺼풀에 가려짐)
        ir = 11.5
        iris = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        ip = iris.load()
        for y in range(S):
            for x in range(S):
                dd = math.hypot(x + 0.5 - c, y + 0.5 - c)
                if dd > ir:
                    continue
                t = dd / ir
                col = tuple(int(IRIS_I[i] + (IRIS_O[i] - IRIS_I[i]) * t) for i in range(3))
                ang = math.atan2(y + 0.5 - c, x + 0.5 - c)
                if 0.35 < t < 0.95 and int((ang + math.pi) / (math.tau / 20)) % 2 == 0:
                    col = tuple(min(255, int(v * 1.08)) for v in col)
                ip[x, y] = col + (255,)
        pr = 4.6 * (1 - 0.35 * shine)
        ImageDraw.Draw(iris).ellipse((c - pr, c - pr * 1.5, c + pr, c + pr * 1.5), fill=PUPIL)
        hi = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        hd = ImageDraw.Draw(hi)
        hd.ellipse((c - 6.5, c - 7.5, c - 3, c - 4), fill=WHITE)
        iris.alpha_composite(hi)
        img.paste(iris, (0, 0), Image.composite(iris.split()[3], Image.new("L", (S, S), 0), m))
        # 눈꺼풀 테두리 (위 · 아래 원호)
        pts_up, pts_dn = [], []
        for i in range(61):
            u = -1 + 2 * i / 60
            lim = h * (1 - u * u) ** 0.85
            pts_up.append((c + u * w, c - lim))
            pts_dn.append((c + u * w, c + lim))
        d.line(pts_up, fill=GOLD_D, width=2)
        d.line(pts_dn, fill=GOLD_D, width=2)
        d.line(pts_up, fill=GOLD, width=1)
    else:
        pts = []
        for i in range(61):
            u = -1 + 2 * i / 60
            pts.append((c + u * w, c + 3 * (1 - u * u)))
        d.line(pts, fill=GOLD_D, width=3)
        d.line(pts, fill=GOLD, width=1)
    # 속눈썹 (위 눈꺼풀 위로 빛살 일곱)
    for k in range(7):
        u = -0.75 + 1.5 * k / 6
        lim = (max(0.6, hmax * open_t) if open_t > 0.05 else -3 * (1 - u * u)) * (1 - u * u) ** 0.85
        x0, y0 = c + u * w, c - lim - 1
        ang = -math.pi / 2 + u * 0.9
        L = 7 + 3 * (1 - abs(u))
        d.line([(x0, y0), (x0 + math.cos(ang) * L, y0 + math.sin(ang) * L)], fill=GOLD, width=1)
    out = img.resize((S * 2, S * 2), Image.NEAREST)
    # 빛무리 (금빛) · 빛날 때 눈동자 빛살
    glow = java_fx_art.glow_of(out, 6, (255, 200, 90), 0.7 + 0.5 * shine)
    res = Image.new("RGBA", out.size, (0, 0, 0, 0))
    res.alpha_composite(glow)
    res.alpha_composite(out)
    if shine > 0:
        fl = Image.new("RGBA", out.size, (0, 0, 0, 0))
        fd = ImageDraw.Draw(fl)
        C = S
        for k in range(8):
            a = k * math.pi / 4
            L = (46 if k % 2 == 0 else 26) * shine
            fd.line([(C, C), (C + math.cos(a) * L * 2, C + math.sin(a) * L * 2)], fill=(255, 250, 220, int(220 * shine)), width=3 if k % 2 == 0 else 2)
        fd.ellipse((C - 10, C - 10, C + 10, C + 10), fill=(255, 255, 240, int(240 * shine)))
        res.alpha_composite(fl.filter(ImageFilter.GaussianBlur(1.2)))
    return res


def skyeye_sheet():
    sheet = Image.new("RGBA", (256 * 4, 256), (0, 0, 0, 0))
    for i, (o, s) in enumerate(((0.0, 0.0), (0.45, 0.0), (1.0, 0.0), (1.0, 1.0))):
        sheet.paste(eye_frame(o, s), (i * 256, 0))
    return sheet


def main():
    java_fx_art.save("rfx_orb", orb_sheet(), 128, 128, "rotate_xyz")
    java_fx_art.save("rfx_skyeye", skyeye_sheet(), 256, 256, "direction_z")


if __name__ == "__main__":
    main()
