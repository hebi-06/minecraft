#!/usr/bin/env python3
"""0.50.0 그리스 무기 → 북유럽 (사용자 "그리스 무기를 북유럽 컨셉에 맞춰서 하자"): 크로노스의 낫 → 노른의 낫 — 시계판 그림 (rpg:rfx_clock) 을
운명의 물레로 다시 그림. 칸 · 크기 · 방향은 옛 시계판과 같음 (Kronos.java 의 DIAL · HOUR · MINUTE · GEAR 칸 그대로):

  칸 0 · 1  룬 고리판 (빛 · 심지): 바깥 두 겹 테 사이에 옛 푸타르크 룬 스물넷, 안쪽 테의 열두 눈금 (바늘이 30° 씩 감김 — 맨 위는 실을 끊는 자리의 세모),
            세 노른 (과거 · 현재 · 미래) 의 작은 고리 셋, 가운데 운명의 그물 (세로 · 가로 셋 · 대각 둘)
  칸 2 · 3  물렛가락 (짧은 바늘): 가운데에서 위로 곧은 가락 · 가락바퀴 (둥근 추) · 뾰족한 끝
  칸 4 · 5  운명의 실 (긴 바늘): 두 가닥이 꼬인 실 · 끝의 매듭
  칸 6 · 7  매듭 고리 (옛 톱니): 두 가닥이 번갈아 위아래로 엮인 고리

  python3 tools/weapon_fx_art_norse.py      # tools/java_fx 에 씀 → python3 tools/java_fx_add.py rfx_clock
"""
import math
import os
import sys

from PIL import Image, ImageDraw

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import java_fx_art as J  # noqa: E402

CW = 256
SS = 4
S = CW * SS
C = S / 2
W = (255, 255, 255, 255)

# 옛 푸타르크 24 (선분 — 칸 안 x −0.5 ~ 0.5 · y −1 (아래) ~ 1 (위), 가운데 맞춤 그림)
RUNES = [
    [[(0, -1), (0, 1)], [(0, 0.05), (0.5, 0.5)], [(0, 0.55), (0.5, 1.0)]],  # ᚠ
    [[(-0.3, -1), (-0.3, 1), (0.35, 0.4), (0.35, -1)]],  # ᚢ
    [[(-0.3, -1), (-0.3, 1)], [(-0.3, 0.5), (0.35, 0.0), (-0.3, -0.5)]],  # ᚦ
    [[(-0.2, -1), (-0.2, 1)], [(-0.2, 1), (0.4, 0.6)], [(-0.2, 0.45), (0.4, 0.05)]],  # ᚨ
    [[(-0.3, -1), (-0.3, 1), (0.35, 0.55), (-0.3, 0.1), (0.4, -1)]],  # ᚱ
    [[(0.3, 0.7), (-0.3, 0.0), (0.3, -0.7)]],  # ᚲ
    [[(-0.45, 1), (0.45, -1)], [(0.45, 1), (-0.45, -1)]],  # ᚷ
    [[(-0.3, -1), (-0.3, 1), (0.35, 0.5), (-0.3, 0.0)]],  # ᚹ
    [[(-0.35, -1), (-0.35, 1)], [(0.35, -1), (0.35, 1)], [(-0.35, 0.3), (0.35, -0.3)]],  # ᚺ
    [[(0, -1), (0, 1)], [(-0.35, 0.35), (0.35, -0.35)]],  # ᚾ
    [[(0, -1), (0, 1)]],  # ᛁ
    [[(-0.05, 0.9), (-0.45, 0.45), (-0.05, 0.0)], [(0.05, 0.0), (0.45, -0.45), (0.05, -0.9)]],  # ᛃ
    [[(0, -1), (0, 1)], [(0, 1), (0.38, 0.65)], [(0, -1), (-0.38, -0.65)]],  # ᛇ
    [[(-0.3, -1), (-0.3, 1)], [(-0.3, 1), (0.35, 0.55), (0.35, 1.0)], [(-0.3, -1), (0.35, -0.55), (0.35, -1.0)]],  # ᛈ
    [[(0, -1), (0, 1)], [(0, 0.3), (-0.45, 0.9)], [(0, 0.3), (0.45, 0.9)]],  # ᛉ
    [[(0.35, 1), (-0.35, 0.35), (0.35, -0.35), (-0.35, -1)]],  # ᛊ
    [[(0, -1), (0, 1)], [(-0.45, 0.5), (0, 1), (0.45, 0.5)]],  # ᛏ
    [[(-0.3, -1), (-0.3, 1), (0.35, 0.5), (-0.3, 0.0), (0.35, -0.5), (-0.3, -1)]],  # ᛒ
    [[(-0.35, -1), (-0.35, 1), (0, 0.4), (0.35, 1), (0.35, -1)]],  # ᛖ
    [[(-0.35, -1), (-0.35, 1)], [(0.35, -1), (0.35, 1)], [(-0.35, 1), (0.35, 0.2)], [(0.35, 1), (-0.35, 0.2)]],  # ᛗ
    [[(-0.2, -1), (-0.2, 1), (0.4, 0.5)]],  # ᛚ
    [[(0, 0.7), (0.4, 0), (0, -0.7), (-0.4, 0), (0, 0.7)]],  # ᛜ
    [[(-0.4, -1), (-0.4, 1), (0.4, -1), (0.4, 1), (-0.4, -1)]],  # ᛞ
    [[(0, 1), (0.4, 0.35), (-0.3, -1)], [(0, 1), (-0.4, 0.35), (0.3, -1)]],  # ᛟ
]


def pol(r, a):
    """가운데에서 반지름 r · 각 a (위가 0 · 시계 방향) 의 점"""
    return (C + math.sin(a) * r, C - math.cos(a) * r)


def rune(d, idx, r, a, h, w):
    """룬 하나: 반지름 r 에 서서 위 (바깥) 를 보게, 높이 h · 선 굵기 w"""
    up = (math.sin(a), -math.cos(a))
    rt = (math.cos(a), math.sin(a))
    cx, cy = pol(r, a)
    for stroke in RUNES[idx % len(RUNES)]:
        pts = [(cx + rt[0] * x * h * 0.5 + up[0] * y * h * 0.5, cy + rt[1] * x * h * 0.5 + up[1] * y * h * 0.5) for x, y in stroke]
        d.line(pts, fill=W, width=int(w), joint="curve")
        for p in (pts[0], pts[-1]):
            d.ellipse([p[0] - w / 2, p[1] - w / 2, p[0] + w / 2, p[1] + w / 2], fill=W)


def ring(d, r, w):
    d.ellipse([C - r, C - r, C + r, C + r], outline=W, width=int(w))


def wheel(k):
    """룬 고리판 (k = 굵기 몫 — 빛 겹은 굵게 그려 흐림)"""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    r0, r1, r2 = S * 0.47, S * 0.36, S * 0.315
    ring(d, r0, S * 0.011 * k)
    ring(d, r0 - S * 0.018, S * 0.004 * k)
    ring(d, r1, S * 0.008 * k)
    ring(d, r2, S * 0.005 * k)
    for i in range(24):
        rune(d, i, (r0 + r1) / 2 - S * 0.004, (i + 0.5) / 24 * math.tau, S * 0.072, S * 0.0075 * k)
    # 열두 눈금 (실이 30° 씩 감김) — 맨 위는 실을 끊는 자리의 세모
    for i in range(12):
        a = i / 12 * math.tau
        if i == 0:
            tip, l, rr = pol(r1 - S * 0.006, a), pol(r2 + S * 0.004, a - 0.05), pol(r2 + S * 0.004, a + 0.05)
            d.polygon([tip, l, rr], fill=W)
        else:
            d.line([pol(r2, a), pol(r1 - S * 0.012, a)], fill=W, width=int(S * 0.006 * k))
    # 세 노른 (과거 · 현재 · 미래): 작은 고리 · 가운데 점
    for i in range(3):
        a = math.pi / 3 + i / 3 * math.tau
        x, y = pol(S * 0.215, a)
        rr = S * 0.04
        d.ellipse([x - rr, y - rr, x + rr, y + rr], outline=W, width=int(S * 0.006 * k))
        rr2 = S * 0.012 * (0.8 + 0.2 * k)
        d.ellipse([x - rr2, y - rr2, x + rr2, y + rr2], fill=W)
    # 운명의 그물: 세로 · 가로 셋 · 대각 둘 (네모 안)
    h = S * 0.11
    w = S * 0.0055 * k
    for t in (-1, 0, 1):
        d.line([(C + t * h, C - h), (C + t * h, C + h)], fill=W, width=int(w))
        d.line([(C - h, C + t * h), (C + h, C + t * h)], fill=W, width=int(w))
    d.line([(C - h, C - h), (C + h, C + h)], fill=W, width=int(w))
    d.line([(C + h, C - h), (C - h, C + h)], fill=W, width=int(w))
    return img


def spindle(k):
    """물렛가락 (짧은 바늘): 가운데 아래 조금에서 위로 · 가락바퀴 · 뾰족한 끝"""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    top, base = C - S * 0.24, C + S * 0.045
    w = S * 0.011 * k
    d.polygon([(C - w, base), (C - w * 0.8, top + S * 0.03), (C, top), (C + w * 0.8, top + S * 0.03), (C + w, base)], fill=W)
    rr = S * 0.038 * (0.85 + 0.15 * k)
    yy = C - S * 0.03
    d.ellipse([C - rr, yy - rr, C + rr, yy + rr], fill=W)
    # 가락에 감긴 실 (비스듬한 줄 넷)
    for i in range(4):
        y = C - S * (0.09 + i * 0.03)
        d.line([(C - w * 2.2, y + S * 0.008), (C + w * 2.2, y - S * 0.008)], fill=W, width=int(S * 0.004 * k))
    return img


def thread(k):
    """운명의 실 (긴 바늘): 두 가닥이 꼬인 실 · 끝의 매듭"""
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    L = S * 0.36
    amp = S * 0.011
    for ph in (0, math.pi):
        pts = []
        for i in range(161):
            t = i / 160
            y = C + S * 0.03 - (L + S * 0.03) * t
            pts.append((C + amp * math.sin(t * math.tau * 6 + ph) * (0.4 + 0.6 * t), y))
        d.line(pts, fill=W, width=int(S * 0.0065 * k), joint="curve")
    tip = C - L
    rr = S * 0.017 * (0.85 + 0.15 * k)
    d.ellipse([C - rr, tip - rr, C + rr, tip + rr], fill=W)
    rr = S * 0.016
    d.ellipse([C - rr, C - rr, C + rr, C + rr], fill=W)
    return img


def knotring(k):
    """매듭 고리: 두 가닥이 번갈아 위아래로 엮임 (옛 톱니 자리 — 천천히 거꾸로 돎)"""
    n = 14
    rm, amp, w = S * 0.415, S * 0.034, S * 0.013 * k
    gap = S * 0.026

    def strand(sign):
        im = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        dd = ImageDraw.Draw(im)
        pts = []
        for i in range(721):
            a = i / 720 * math.tau
            pts.append(pol(rm + sign * amp * math.sin(n * a), a))
        dd.line(pts, fill=W, width=int(w), joint="curve")
        return im

    a_img, b_img = strand(1), strand(-1)
    da, db = ImageDraw.Draw(a_img), ImageDraw.Draw(b_img)
    for j in range(2 * n):
        a = j * math.pi / n
        x, y = pol(rm, a)
        box = [x - gap, y - gap, x + gap, y + gap]
        # 엇갈림마다 번갈아 한 가닥이 위 (아래 가닥은 그 자리를 비움)
        (db if j % 2 == 0 else da).ellipse(box, fill=(0, 0, 0, 0))
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.alpha_composite(a_img)
    out.alpha_composite(b_img)
    return out


def fate_sheet():
    cells = []
    for fn, k_core, k_glow, blur, strength in ((wheel, 1.0, 3.0, 0.012, 1.4), (spindle, 1.0, 2.2, 0.012, 1.5), (thread, 1.0, 2.6, 0.012, 1.5),
                                                (knotring, 1.0, 2.2, 0.015, 1.3)):
        core = fn(k_core)
        glow = J.glow_of(fn(k_glow), S * blur, (255, 255, 255), strength)
        cells += [glow, core]
    sheet = Image.new("RGBA", (CW * 4, CW * 2), (0, 0, 0, 0))
    for i, img in enumerate(cells):
        sheet.alpha_composite(J.shrink(img, (CW, CW)), ((i % 4) * CW, (i // 4) * CW))
    J.save("rfx_clock", sheet, CW, CW, "direction_z")
    return sheet


if __name__ == "__main__":
    fate_sheet()
