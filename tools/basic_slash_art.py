#!/usr/bin/env python3
"""2026-11-14 기본 평타 참격 판 그림 (사용자 "근접 애들은 참격 나가게" · "참격 좀 큼직막하게" · "광역 애들은 광역 답게 넓게 참격 좀 두껍게 잘 보이게" ·
"날가롭게 얅은데 참격 앞을 보고 있으면 뒤에 참격 잔상이 두껍게 사진 같은 요런 느낌, 그렇다고 도트로 하지말고").

판 하나 = 위에서 본 몸 둘레 초승달 띠 (가운데 = 원점 = 휘두르는 몸 가운데, 바깥 가장자리 반지름 1 — Fx3d scale = 칸 반지름). 그림 위 (+Y) = 앞, +X = 오른쪽.
띠는 오른쪽 뒤 (THETA_S) 에서 앞 (+Y) 을 지나 왼쪽 뒤 (THETA_E) 로 휩씀 — 머리 (지금 칼끝) 는 가늘고 날카롭게 (흰 날 + 무기 색), 뒤로 갈수록 잔상이
두꺼워지며 어두워짐 (무기 색 → 짙은 색), 꼬리 끝은 사라짐. 바깥 가장자리는 또렷 (날) · 안쪽은 부드럽게 번짐. 도트가 아닌 매끈한 그림 (가장자리 반 픽셀 안티에일리어스).

칸 FRAMES 장: 0 ~ SWEEP-1 = 휘두름 (머리가 나아감) · 나머지 = 잔상이 꼬리부터 머리 쪽으로 거둬짐 (u 0.1 씩 — 판의 움직임 play: 휘두름 = 삶의 0.4).
겹 둘: 몸 (밝기 lum · 알파 — 빛깔 TINT = 무기 색) · 날 (알파 — 속빛 GLOW = 흰빛에 가까운 무기 색). mirror = 왼쪽 → 오른쪽 (그림을 좌우로 뒤집음).

  python3 tools/basic_slash_art.py --preview 폴더   # 칸마다 무기 색을 입혀 어두운 · 밝은 바탕에 그림 (확인용)
"""
import math
import os
import sys

import numpy as np

W, H = 256, 192  # 칸 픽셀 (아틀라스 칸 16 × 12 · 모드 없는 클라이언트 글자 한 장 — 글꼴 판 256 안)
XL, XR = -1.04, 1.04  # 판 너비 (모델 칸 — 반지름 1 + 날 빛 여유)
YB, YT = -0.52, 1.04  # 판 높이 (가로세로비 = W / H)
THETA_S, THETA_E = -28.0, 208.0  # 휘두름 시작 · 끝 (도 — +X 에서 앞 (+Y) 쪽으로)
FRAMES = 10
SWEEP = 4  # 휘두름 칸 수 (u 0 ~ 0.4)

W_THICK = 0.7  # 광역 무기 (마검 · 낫 · 전투 망치 · 창 — slash_basic_w) 의 가장 두꺼운 폭
W_MAX = 0.5  # 잔상 띠의 가장 두꺼운 폭 (반지름 몫)
W_HEAD = 0.006  # 머리 (칼끝) 의 폭
PHI_W = 34.0  # 머리 뒤 이 각도 만큼에서 폭이 63% 까지 (도)
RIM = 0.016  # 날 (흰 줄) 굵기
RIM_HEAD = 0.034  # 머리 가까이 날 굵기
PHI_RIM = 95.0  # 날이 옅어지는 각도 (도)


def _ss(e0, e1, x):
    t = np.clip((x - e0) / (e1 - e0), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def grid():
    xs = XL + (np.arange(W) + 0.5) / W * (XR - XL)
    ys = YT - (np.arange(H) + 0.5) / H * (YT - YB)
    x, y = np.meshgrid(xs, ys)
    r = np.hypot(x, y)
    th = np.degrees(np.arctan2(y, x))
    th = np.where(th < -90.0, th + 360.0, th)  # [-90, 270) — 띠 (−28 ~ 208) 가 끊기지 않게
    return r, th


PX = (XR - XL) / W  # 한 픽셀 (판 단위)


def frame_params(k):
    """칸 k → (머리 각, 꼬리 각, 폭 배율, 어둡게, 전체 알파, 날 알파)"""
    span = THETA_E - THETA_S
    if k < SWEEP:
        p = (k + 1) / SWEEP
        p = 1 - (1 - p) ** 1.35  # 끝으로 갈수록 느려짐 (휘두름이 멈춤)
        return THETA_S + span * p, THETA_S, 1.0, 0.0, 1.0, 1.0
    q = (k - SWEEP + 1) / (FRAMES - SWEEP)  # 1/6 ~ 1
    head = THETA_E + 7.0 * q
    tail = THETA_S + span * 0.9 * q ** 1.15
    return head, tail, 1.0 + 0.3 * q, 0.28 * q, (1 - q) ** 0.55 if q < 1 else 0.06, (1 - q) ** 1.6


def render(k, thick=False):
    """칸 k 의 (몸 밝기, 몸 알파, 날 알파) — H × W. thick = 광역 무기의 두꺼운 띠"""
    r, th = grid()
    head, tail, kw, dark, ga, ra = frame_params(k)
    w_max = W_THICK if thick else W_MAX
    L = max(1e-3, head - tail)
    phi = head - th  # 머리 뒤 각도
    inside = (phi >= -1.0) & (th >= tail - 2.0)
    phic = np.clip(phi, 0.0, None)
    w = (W_HEAD + (w_max - W_HEAD) * (1 - np.exp(-phic / PHI_W))) * kw
    r_out = 1.0 - 0.035 * np.clip(phic / 240.0, 0, 1) * kw
    rho = (r_out - r) / np.maximum(w, 1e-4)  # 0 = 바깥 날 · 1 = 안쪽
    # 가장자리: 바깥은 또렷 (반 픽셀) · 안쪽은 번짐 · 머리 끝은 날카롭게 · 꼬리 끝은 사라짐
    a_out = _ss(-0.5 * PX, 0.5 * PX, r_out - r)
    a_in = 1 - _ss(0.62, 1.0, rho)
    a_head = _ss(-0.6, 1.2, phi)
    a_tail = 1 - _ss(0.5 * L, 1.0 * L, phic)
    a_tail = a_tail * _ss(tail - 2.0, tail + 3.0, th)
    body_a = a_out * a_in * a_head * a_tail * ga * inside
    # 밝기: 날 쪽 · 머리 쪽이 밝고 안쪽 · 꼬리 쪽이 어두움 (무기 색 → 짙은 색)
    along = _ss(0.0, 1.0, phic / max(L, 70.0))
    # 2026-11-14 영상 (CI 43594058613): 짙게 (꼬리 0.16) 하면 붉은 · 주황 무기의 잔상이 풀밭 위에서 흙빛으로 보였음 → 짙어지는 몫을 줄이고 (꼬리 약 0.45) 대신 알파로 사라짐
    lum = (1 - 0.3 * np.clip(rho, 0, 1) ** 0.9) * (1 - 0.35 * along) * (1 - 0.6 * dark)
    lum = np.clip(lum + 0.25 * np.exp(-phic / 10.0) * (1 - np.clip(rho, 0, 1)), 0.4, 1.0)
    # 날 (흰 줄): 바깥 가장자리를 따라 — 머리 가까이 굵고 밝음, 뒤로 옅어짐
    t_rim = RIM + (RIM_HEAD - RIM) * np.exp(-phic / 14.0)
    d = r_out - r  # 바깥 가장자리에서 안쪽으로
    rim_a = _ss(-0.5 * PX, 0.5 * PX, d) * (1 - _ss(t_rim - 0.6 * PX, t_rim + 0.6 * PX, d))
    rim_a = rim_a * np.clip(np.exp(-phic / PHI_RIM) * 1.15, 0, 1) * a_head * _ss(tail, tail + 6.0, th) * ra * inside
    # 머리 섬광 (칼끝 — 날 안쪽으로 조금 더 넓게 번쩍)
    flare = np.exp(-(phic / 5.0) ** 2) * np.exp(-(d / 0.05) ** 2) * _ss(-1.5 * PX, 0.5 * PX, d) * a_head * ra * inside
    rim_a = np.clip(rim_a + 0.85 * flare, 0, 1)
    return lum, np.clip(body_a, 0, 1), rim_a


def frames(mirror=False, thick=False):
    out = []
    for k in range(FRAMES):
        lum, a, c = render(k, thick)
        if mirror:
            lum, a, c = lum[:, ::-1], a[:, ::-1], c[:, ::-1]
        out.append((lum, a, c))
    return out


def painter(arr):
    """아틀라스 칸 그리기 (fx_models Atlas.swatch — u · v 0 ~ 1 → 그 픽셀)"""
    h, w = arr[0].shape

    def paint(u, v, rnd):
        x = min(w - 1, max(0, int(round(u * (w - 1)))))
        y = min(h - 1, max(0, int(round(v * (h - 1)))))
        return float(arr[0][y, x]), float(arr[1][y, x])

    return paint


def preview(outdir):
    from PIL import Image
    os.makedirs(outdir, exist_ok=True)
    glow = np.array([0.47, 0.84, 1.0])  # 칼
    core = glow + (1 - glow) * 0.75
    for name, mirror, thick in (("r", False, False), ("l", True, False), ("w", False, True)):
        fr = frames(mirror, thick)
        for bgname, bg in (("dark", np.array([0.08, 0.09, 0.12])), ("grass", np.array([0.42, 0.55, 0.3]))):
            row = []
            for lum, a, c in fr:
                img = np.ones((H, W, 3)) * bg
                body = lum[..., None] * glow
                img = img * (1 - a[..., None]) + body * a[..., None]
                img = img * (1 - c[..., None]) + core * c[..., None]
                row.append(img)
            sheet = np.concatenate([np.concatenate(row[:5], axis=1), np.concatenate(row[5:], axis=1)], axis=0)
            Image.fromarray((np.clip(sheet, 0, 1) * 255 + 0.5).astype(np.uint8)).save(os.path.join(outdir, "bslash_%s_%s.png" % (name, bgname)))
    print(outdir)


if __name__ == "__main__":
    if "--preview" in sys.argv:
        preview(sys.argv[sys.argv.index("--preview") + 1])
