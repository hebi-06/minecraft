"""2026-11-16 분신 (남은 직업 위르드식 — 트릭스터 환영 · 전사 돌진 잔상 · 마검사 · 아케인 블레이드 순간 이동 잔상): 직업 묶음 빛으로 칠한 분신.

예전 그림 (rfx_shade — 흰 몸 · 흰 테 두 겹을 빛 두 개로 물들임) 과 **같은 틀**: 128 × 256 · 8칸 (자세 넷 — 0 섬 · 1 돌진 · 2 베기 · 3 찌르기 — × 왼쪽을 보는
뒤집은 장 4 ~ 7), 발 = 그림 아래 끝 · 머리 = 위. 예전 그림의 몸 · 테 모양 (알파) 을 그대로 쓰고 (코드의 자리 · 크기 · 자세 · 좌우 그대로) 빛만 묶음으로 칠함
(CI 43878674673 트릭스터 영상에 예전 rfx_shade 70장이 남았었음).

- 환영 (illusion — 트릭스터): 거울 유리 몸 (청록 — 위가 밝음 · 비스듬한 반사 줄 둘) · 자홍 테 · 흰 심 (거울 · 깨지는 거울 그림과 같은 결)
- 그 밖 (분노 · 룬 …): 묶음 빛 몸 (짙은 빛 → 가운데 빛 — 위가 밝음) · 밝은 빛 테 · 흰 심

  cd tools && python3 -m jobvfx.clone --preview 폴더 [--style illusion]
"""
import os
import sys

import numpy as np
from PIL import Image

from .base import WHITE, bloom, compose, preview, sstep, to_img

SHADE_FRAMES = 8
SRC = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "src", "main", "resources", "assets", "mmorpg", "textures", "particle", "addon",
                   "rpg", "fx", "rfx_shade.png")
W, H = 128, 256


def _masks():
    a = np.asarray(Image.open(SRC).convert("RGBA"), float) / 255
    out = []
    for c in range(SHADE_FRAMES):
        body = a[0:H, c * W:(c + 1) * W]
        line = a[H:2 * H, c * W:(c + 1) * W]
        B = body[..., 3]
        O = line[..., 3] * line[..., :3].mean(-1)
        out.append((B, O))
    return out


def shade_frame(S, B, O):
    yy = np.linspace(1, -1, H)[:, None] * np.ones((1, W))
    xx = np.ones((H, 1)) * np.linspace(-0.5, 0.5, W)[None, :]
    top = yy * 0.5 + 0.5
    if S.kind == "illusion":
        # 거울 유리: 청록 (accent) 몸 · 비스듬한 반사 줄 둘 · 자홍 테
        glint = np.exp(-(((xx * 1.2 + yy * 0.5) - 0.18) / 0.05) ** 2) + 0.6 * np.exp(-(((xx * 1.2 + yy * 0.5) + 0.12) / 0.03) ** 2)
        body = S.accent(np.clip(0.38 + 0.32 * top + 0.3 * glint, 0, 1))
        layers = [(body, B * 0.74), (WHITE, np.clip(glint, 0, 1) * B * 0.35), (S.main(0.72), O * 0.95), (WHITE, sstep(0.55, 0.9, O) * 0.75)]
    else:
        body = S.main(np.clip(0.28 + 0.34 * top, 0, 1))
        layers = [(body, B * 0.72), (S.accent(0.85), O * 0.95), (WHITE, sstep(0.55, 0.9, O) * 0.7)]
    rgb, a = compose(layers, (H, W))
    rgb, a = bloom(rgb, a, (H, W), thresh=0.72, sigma=0.012, gain=0.3)
    return rgb, a


def frames(S):
    return [to_img(*shade_frame(S, B, O)) for B, O in _masks()]


if __name__ == "__main__":
    if "--preview" in sys.argv:
        from .element import STYLES
        out = sys.argv[sys.argv.index("--preview") + 1]
        S = STYLES[sys.argv[sys.argv.index("--style") + 1] if "--style" in sys.argv else "illusion"]
        preview({"shade": frames(S)}, out)
