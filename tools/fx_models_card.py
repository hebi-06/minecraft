"""0.48.2 2D 그림 판 (사용자 "참격이 위쪽을 보고 있음 정면을 보면서 나가면 좋겠는데"): tools/fx_models.py 가 add(fx) 로 부름.

모바일 (애드온) 2D 그림을 판 한 장에 그대로 붙인 것 — 입체 모양은 없고 그림만, 다만 파티클처럼 카메라를 따라 돌지 않고 세상의 정한 면에 놓임
(Fx3d 의 dir · face · roll 로 돌림). 축 (fx_models.py 와 같음): +Y = 그림의 위, +Z = 판의 면, +X = 오른쪽.

  참격 판 (slash_wave) — rfx_wave: 초승달의 불룩한 날이 +Y. 날아가는 쪽을 dir 로 주면 날이 앞을 보며 나감 (룬 · 평타 강화의 검기)
"""
import numpy as np

fx = None  # tools/fx_models.py (add 가 채움)

FX_DIR = "src/main/resources/assets/mmorpg/textures/particle/addon/rpg/fx/"


def add(mod):
    global fx
    fx = mod
    for f in (slash_wave,):
        fx.MODELS[f.__name__] = f


def v3(*a):
    return np.array(a if len(a) == 3 else a[0], dtype=float)


def sampler(png, box):
    """그림 한 칸 (box = x0, y0, x1, y1 픽셀) 을 읽는 함수 (u · v 0 ~ 1 → R, G, B, A — 알파를 곱해 섞은 뒤 나눔: 가장자리가 검게 물들지 않게)"""
    from PIL import Image
    im = Image.open(fx.os.path.join(fx.ROOT, FX_DIR + png)).convert("RGBA")
    arr = np.asarray(im.crop(box), dtype=float) / 255.0
    pre = arr.copy()
    pre[:, :, :3] *= arr[:, :, 3:4]
    h, w = arr.shape[:2]

    def s(u, v):
        x, y = u * (w - 1), v * (h - 1)
        x0, y0 = int(x), int(y)
        x1, y1 = min(w - 1, x0 + 1), min(h - 1, y0 + 1)
        fu, fv = x - x0, y - y0
        c = pre[y0, x0] * (1 - fu) * (1 - fv) + pre[y0, x1] * fu * (1 - fv) + pre[y1, x0] * (1 - fu) * fv + pre[y1, x1] * fu * fv
        a = c[3]
        if a < 1e-4:
            return 0.0, 0.0, 0.0, 0.0
        return c[0] / a, c[1] / a, c[2] / a, a

    return s


def flat(m, uv, mat, w, h, z):
    """판 한 장 (가운데가 원점, 너비 w · 높이 h, z 앞뒤 자리) — 그림의 위 = +Y"""
    u0, v0, u1, v1 = uv
    ps = [v3(-w / 2, h / 2, z), v3(-w / 2, -h / 2, z), v3(w / 2, -h / 2, z), v3(w / 2, h / 2, z)]
    m.quad(ps, [(u0, v0), (u0, v1), (u1, v1), (u1, v0)], mat, [v3(0, 0, 1)] * 4)


def slash_wave():
    """참격 판 — 모바일 초승달 참격 그림 (rfx_wave: 위 칸 = 빛 초승달 + 뒤로 남는 물결 세 줄 · 아래 칸 = 가는 흰 날) 을 판에 (가운데가 원점, 너비 2 · 높이 1 —
    파티클 rfx_wave 의 size 1 과 같은 크기). 초승달의 불룩한 날이 +Y (날아가는 쪽), 물결은 -Y (뒤). 빛깔 (TINT — 위 칸, 룬 색) · 속빛 (GLOW — 아래 칸 날,
    앞 · 뒤 두 장 — 어느 쪽에서 보아도 날이 위). 스스로 빛남"""
    m = fx.Model("slash_wave", tex=256, cell=32)
    m.emissive = True
    s0 = sampler("rfx_wave.png", (0, 0, 256, 128))
    s1 = sampler("rfx_wave.png", (0, 128, 256, 256))

    def body(u, v, rnd):
        r, g, b, a = s0(u, v)
        return min(1.0, (r + g + b) / 3 * 1.05), a

    def core(u, v, rnd):
        r, g, b, a = s1(u, v)
        return 1.0, a

    body_uv = m.atlas.swatch("wave_body", body, 8, 4)
    core_uv = m.atlas.swatch("wave_core", core, 8, 4)
    flat(m, core_uv, fx.GLOW, 2.0, 1.0, -0.01)
    flat(m, body_uv, fx.TINT, 2.0, 1.0, 0.0)
    flat(m, core_uv, fx.GLOW, 2.0, 1.0, 0.01)
    return m
