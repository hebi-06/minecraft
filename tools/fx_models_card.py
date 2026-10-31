"""0.48.2 2D 그림 판 (사용자 "참격이 위쪽을 보고 있음 정면을 보면서 나가면 좋겠는데" → "다른 참격이 나가는 것들도 저렇게 바꿔줘"): tools/fx_models.py 가
add(fx) 로 부름.

모바일 (애드온) 2D 그림을 판 한 장에 그대로 붙인 것 — 입체 모양은 없고 그림만, 다만 파티클처럼 카메라를 따라 돌지 않고 세상의 정한 면에 놓임
(Fx3d 의 dir · face · roll 로 돌림). 축 (fx_models.py 와 같음): +Y = 그림의 위, +Z = 판의 면, +X = 오른쪽.

  참격 판 (slash_wave) — rfx_wave: 초승달의 불룩한 날이 +Y. 날아가는 쪽을 dir 로 주면 날이 앞을 보며 나감 (룬 · 평타 강화의 검기)
  직업 참격 판 (slash_job — 0.50.0) — rfx_jslash 여덟 장: 직업의 눈앞 큰 베기 · 날아가는 검기 (불룩한 날 +Y)
  휘두른 자국 판 (slash_arc · slash_heavy · slash_thin + _soft) — 휘두름 그림 (slash_<모양>_r_glow · _core · _aura, 5칸 넘김): 불룩한 쪽을 +Y 로
    돌려 붙임 (arc 0° · heavy 90° · thin 45° — r 그림의 불룩한 쪽, 위에서 반시계). 칸마다 부분 f0 ~ f4, 움직임 play (len 1 — 칸을 차례로 켬:
    animSpeed = 1 / 삶). _soft = 바깥 빛 (aura) 한 겹만 (빛깔 하나)
"""
import math

import numpy as np

fx = None  # tools/fx_models.py (add 가 채움)

FX_DIR = "src/main/resources/assets/mmorpg/textures/particle/addon/rpg/fx/"
SLASH_DIR = "src/main/resources/assets/mmorpg/textures/particle/addon/rpg/slash/"

# 휘두름 모양: 파티클 size 1 의 반너비 · 반높이 (addon_particles.json 의 size 식), r 그림의 불룩한 쪽 (위에서 반시계 도), 그림 칸 크기 (빛 · 바깥 / 심지 —
# 아틀라스 칸 16px 단위: 512 그림 한 장에 다섯 칸 × 두 겹)
SLASH_SHAPES = {
    "arc": (1.0616, 0.7133, 0.0, (8, 6), (12, 8)),
    "heavy": (1.0046, 1.2198, 90.0, (6, 8), (8, 10)),
    "thin": (0.5619, 0.5619, 45.0, (8, 8), (8, 8)),
}
FRAMES = 5


def add(mod):
    global fx
    fx = mod
    fns = [slash_wave, slash_job]
    for shape in SLASH_SHAPES:
        fns.append(_slash_fn(shape, False))
        fns.append(_slash_fn(shape, True))
    for f in fns:
        fx.MODELS[f.__name__] = f


def _slash_fn(shape, soft):
    def f():
        return slash_card(shape, soft)

    f.__name__ = "slash_" + shape + ("_soft" if soft else "")
    return f


def v3(*a):
    return np.array(a if len(a) == 3 else a[0], dtype=float)


def sampler(png, box, folder=FX_DIR):
    """그림 한 칸 (box = x0, y0, x1, y1 픽셀) 을 읽는 함수 (u · v 0 ~ 1 → R, G, B, A — 알파를 곱해 섞은 뒤 나눔: 가장자리가 검게 물들지 않게)"""
    from PIL import Image
    im = Image.open(fx.os.path.join(fx.ROOT, folder + png)).convert("RGBA")
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


def flat(m, uv, mat, w, h, z, deg=0.0):
    """판 한 장 (가운데가 원점, 너비 w · 높이 h, z 앞뒤 자리) — 그림의 위 = +Y. deg = 판을 Z 둘레로 돌림 (도, 반시계)"""
    u0, v0, u1, v1 = uv
    c, s = math.cos(math.radians(deg)), math.sin(math.radians(deg))

    def p(x, y):
        return v3(x * c - y * s, x * s + y * c, z)

    ps = [p(-w / 2, h / 2), p(-w / 2, -h / 2), p(w / 2, -h / 2), p(w / 2, h / 2)]
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


def _frame_box(png, k):
    from PIL import Image
    im = Image.open(fx.os.path.join(fx.ROOT, SLASH_DIR + png))
    w, h = im.size
    fh = h // FRAMES
    return (0, k * fh, w, (k + 1) * fh)


def _alpha_painter(smp):
    def paint(u, v, rnd):
        return 1.0, smp(u, v)[3]

    return paint


def _steps(k, frames=FRAMES):
    """칸 k 의 크기 열쇠 (0 ~ 1 중 k/n ~ (k+1)/n 에만 1 — 파티클 넘김 그림 stretch 와 같게 삶을 n 으로 나눔)"""
    a, b = k / frames, (k + 1) / frames
    e = 0.002
    if k == 0:
        return [[0, 1], [b - e, 1], [b, 0], [1, 0]]
    if k == frames - 1:
        return [[0, 0], [a - e, 0], [a, 1], [1, 1]]
    return [[0, 0], [a - e, 0], [a, 1], [b - e, 1], [b, 0], [1, 0]]


JOB_FRAMES = 8


def slash_job():
    """0.50.0 직업 참격 판 (사용자 "참격 방향 잡혀있는지 확인하고" — 직업의 눈앞 큰 베기 · 날아가는 검기가 카메라를 보던 것): 직업 큰 베기 그림
    (rfx_jslash — 256×128 칸, 빛 0 ~ 7 · 심지 8 ~ 15, 4칸씩 줄) 여덟 장을 부분 f0 ~ f7 에 — 빛 (TINT, 직업 겉빛) + 심지 (GLOW 앞 · 뒤 두 장, 직업 속빛).
    그림의 초승달은 아래로 불룩 → 180° 돌려 불룩한 날이 +Y (휘두르는 · 날아가는 쪽). 판 크기 = 파티클 size 1 (너비 2 · 높이 1), 움직임 play"""
    m = fx.Model("slash_job", tex=1024, cell=16)
    m.emissive = True
    for k in range(JOB_FRAMES):
        m.part("f%d" % k)
        g, c = k, JOB_FRAMES + k
        gbox = ((g % 4) * 256, (g // 4) * 128, (g % 4) * 256 + 256, (g // 4) * 128 + 128)
        cbox = ((c % 4) * 256, (c // 4) * 128, (c % 4) * 256 + 256, (c // 4) * 128 + 128)
        guv = m.atlas.swatch("g%d" % k, _alpha_painter(sampler("rfx_jslash.png", gbox)), 16, 8)
        cuv = m.atlas.swatch("c%d" % k, _alpha_painter(sampler("rfx_jslash.png", cbox)), 16, 8)
        flat(m, cuv, fx.GLOW, 2.0, 1.0, -0.01, 180.0)
        flat(m, guv, fx.TINT, 2.0, 1.0, 0.0, 180.0)
        flat(m, cuv, fx.GLOW, 2.0, 1.0, 0.01, 180.0)
    m.part("main")
    m.anims["play"] = {"len": 1.0, "tracks": {"f%d" % k: {"scale": _steps(k, JOB_FRAMES)} for k in range(JOB_FRAMES)}}
    return m


def slash_card(shape, soft):
    """휘두른 자국 판 (모양 shape): r 그림 다섯 칸을 부분 f0 ~ f4 에 — 빛 (TINT, 룬 색) + 심지 (GLOW 앞 · 뒤 두 장) 또는 soft = 바깥 빛 (TINT) 한 겹.
    판의 크기 = 파티클 size 1 (반너비 kx · 반높이 ky → 2kx × 2ky), 불룩한 쪽이 +Y 가 되게 Z 둘레로 -β 도 돌림"""
    kx, ky, beta, gcell, ccell = SLASH_SHAPES[shape]
    name = "slash_" + shape + ("_soft" if soft else "")
    m = fx.Model(name, tex=512, cell=16)
    m.emissive = True
    w, h = 2 * kx, 2 * ky
    for k in range(FRAMES):
        m.part("f%d" % k)
        if soft:
            png = "slash_%s_r_aura.png" % shape
            uv = m.atlas.swatch("a%d" % k, _alpha_painter(sampler(png, _frame_box(png, k), SLASH_DIR)), *gcell)
            flat(m, uv, fx.TINT, w, h, 0.0, -beta)
            continue
        gpng = "slash_%s_r_glow.png" % shape
        cpng = "slash_%s_r_core.png" % shape
        guv = m.atlas.swatch("g%d" % k, _alpha_painter(sampler(gpng, _frame_box(gpng, k), SLASH_DIR)), *gcell)
        cuv = m.atlas.swatch("c%d" % k, _alpha_painter(sampler(cpng, _frame_box(cpng, k), SLASH_DIR)), *ccell)
        flat(m, cuv, fx.GLOW, w, h, -0.01, -beta)
        flat(m, guv, fx.TINT, w, h, 0.0, -beta)
        flat(m, cuv, fx.GLOW, w, h, 0.01, -beta)
    m.part("main")
    m.anims["play"] = {"len": 1.0, "tracks": {"f%d" % k: {"scale": _steps(k)} for k in range(FRAMES)}}
    return m
