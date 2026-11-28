#!/usr/bin/env python3
"""원소신 NPC 소품 (2026-11-28): 머리 장식 (투구 칸) · 두 손에 든 것 · 둘레에 뜬 것 (표시 엔티티) 의 아이템 모델.

좌표: 모든 소품 모델은 j 공간과 같은 방향 — x+ = 그 사람의 오른쪽 (east), y+ = 위, z+ = 뒤 (south), 앞 = 북 (−z).
  머리 장식: 머리 가운데 = 원점, 1 단위 = 머리 1 px (display.head 크기 1.6 — CustomHeadLayer 의 0.625 를 되돌림).
  손: 쥐는 자리 (grip) = 원점, 세로 (y) 가 세계에서 곧게 서게 thirdperson Rx(72°) — 마네킹이 무엇을 든 팔은 앞으로 18° (ArmPose.ITEM).
  둘레: 모델 가운데 = 원점 (서버 GodNpcs 가 j 공간 변환 — mmorpg/gods.json 의 ops).
그림판: 소품 하나에 64 × 64 그림 하나 (rpg:item/god/<신>) — 위 32 줄은 색 칸 (4 × 4, 가운데 2 × 2 만 씀), 아래는 무늬 칸 (북면 · 왕관 패널 …).
빛: light_emission 15 (밤에도 밝음 — 번개 · 불 · 얼음 빛 · 쌀 · 영지).
"""
import math
from PIL import Image
from god_npc_kit import rgba, mix, shade, alpha


class Atlas:
    def __init__(self):
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.px = self.img.load()
        self.cells = {}
        self.tx, self.ty, self.th = 0, 32, 0

    def c(self, color):
        """색 칸 → UV (0..16)"""
        key = rgba(color)
        if key not in self.cells:
            n = len(self.cells)
            if n >= 128:
                raise ValueError('색이 128 개를 넘음')
            cx, cy = n % 16, n // 16
            for y in range(4):
                for x in range(4):
                    self.px[cx * 4 + x, cy * 4 + y] = key
            self.cells[key] = (cx, cy)
        cx, cy = self.cells[key]
        return [cx + 0.25, cy + 0.25, cx + 0.75, cy + 0.75]

    def tile(self, w, h, fn):
        """무늬 칸 w × h (픽셀) — fn(x, y) → 색. UV 를 돌려줌"""
        if self.tx + w > 64:
            self.tx, self.ty, self.th = 0, self.ty + self.th, 0
        if self.ty + h > 64:
            raise ValueError('무늬 칸 자리가 없음')
        x0, y0 = self.tx, self.ty
        for y in range(h):
            for x in range(w):
                c = fn(x, y)
                if c is not None:
                    self.px[x0 + x, y0 + y] = rgba(c)
        self.tx += w
        self.th = max(self.th, h)
        return [x0 / 4, y0 / 4, (x0 + w) / 4, (y0 + h) / 4]


DIRS = ('north', 'south', 'east', 'west', 'up', 'down')


class Model:
    def __init__(self, atlas, name):
        self.a = atlas
        self.name = name
        self.els = []
        self.display = {}

    def box(self, lo, hi, col, rot=None, glow=False, faces=None, skip=()):
        """lo · hi: 원점 기준 좌표 (모델에는 +8), col = 모든 면의 색, faces = {면: uv 또는 색}, rot = (축, 각, 원점) — 각은 ±22.5 · ±45"""
        lo, hi = [min(lo[i], hi[i]) for i in range(3)], [max(lo[i], hi[i]) for i in range(3)]
        el = {'from': list(lo), 'to': list(hi), 'faces': {}}
        for d in DIRS:
            if d in skip:
                continue
            spec = (faces or {}).get(d, col)
            if spec is None:
                continue
            uv = spec if isinstance(spec, list) else self.a.c(spec)
            el['faces'][d] = {'uv': [round(u, 4) for u in uv], 'texture': '#0'}
        if rot:
            axis, ang, org = rot
            if ang not in (-45, -22.5, 0, 22.5, 45):
                raise ValueError('각 %s' % ang)
            if ang:
                el['rotation'] = {'angle': ang, 'axis': axis, 'origin': list(org)}
        if glow:
            el['light_emission'] = 15
        self.els.append(el)
        return el

    def cube(self, c, size, col, **kw):
        h = [s / 2 for s in size]
        return self.box([c[0] - h[0], c[1] - h[1], c[2] - h[2]], [c[0] + h[0], c[1] + h[1], c[2] + h[2]], col, **kw)

    def bar(self, c, length, thick, ang, col, plane='z', **kw):
        """막대 하나: 가운데 c, 길이 · 굵기, ang = 평면 안의 각 (도, 22.5 단위 아무 각 — 가로 / 세로 상자 + ±45 안의 회전으로 나눔).
        plane 'z' = xy 평면 (z 축 회전), 'x' = yz 평면, 'y' = xz 평면"""
        a = ang % 180
        if a > 112.5 + 1e-6:
            a -= 180
        vertical = 67.5 - 1e-6 <= abs(a) and abs(a) <= 112.5 + 1e-6
        rot = a - (90 if a > 0 else -90) if vertical else a
        rot = round(rot / 22.5) * 22.5
        t, L = thick / 2, length / 2
        if plane == 'z':
            size = (t * 2, L * 2, t * 2) if vertical else (L * 2, t * 2, t * 2)
            axis = 'z'
        elif plane == 'x':
            size = (t * 2, L * 2, t * 2) if vertical else (t * 2, t * 2, L * 2)
            axis = 'x'
            rot = -rot
        else:
            size = (t * 2, t * 2, L * 2) if vertical else (L * 2, t * 2, t * 2)
            axis = 'y'
            rot = -rot
        return self.cube(c, size, col, rot=(axis, rot, c) if rot else None, **kw)

    def ring(self, r, thick, col, n=16, plane='z', center=(0, 0, 0), depth=None, **kw):
        """고리 (n 조각 — 22.5° 단위): plane 'z' = xy 평면, 'y' = xz 평면"""
        L = 2 * math.pi * r / n + thick * 0.6
        for k in range(n):
            th = 360.0 * k / n
            ca, sa = math.cos(math.radians(th)), math.sin(math.radians(th))
            if plane == 'z':
                c = (center[0] + r * ca, center[1] + r * sa, center[2])
            else:
                c = (center[0] + r * ca, center[1], center[2] + r * sa)
            self.bar(c, L, thick, th + 90, col, plane=plane, **kw)

    def fit(self):
        """블록 모델 요소는 −16 ~ 32 (원점 기준 ±24) 안이어야 함 → 넘으면 k 배로 줄여 쓰고 display · 표시 변환에서 k 배로 키움"""
        m = 0.0
        for el in self.els:
            for v in el['from'] + el['to']:
                m = max(m, abs(v))
        self.k = 1.0 if m <= 23.5 else math.ceil(m / 23.5 * 4) / 4
        return self.k

    def json(self, tex_id):
        k = self.fit()
        els = []
        for el in self.els:
            e = dict(el)
            e['from'] = [round(v / k + 8, 4) for v in el['from']]
            e['to'] = [round(v / k + 8, 4) for v in el['to']]
            if 'rotation' in el:
                r = dict(el['rotation'])
                r['origin'] = [round(v / k + 8, 4) for v in r['origin']]
                e['rotation'] = r
            els.append(e)
        out = {'textures': {'0': tex_id, 'particle': tex_id}, 'elements': els}
        if self.display:
            disp = {}
            for ctx, d in self.display.items():
                d = {kk: list(vv) for kk, vv in d.items()}
                d['scale'] = [round(s * k, 4) for s in d['scale']]
                disp[ctx] = d
            out['display'] = disp
        return out


# ------------------------------------------------------------ display

HEAD_DISPLAY = {'head': {'rotation': [0, 0, 0], 'translation': [0, 0, 0], 'scale': [1.6, 1.6, 1.6]}}

HAND_RX = 72.0


def hand_display(grip=(0, 0, 0), fwd=1.0, scale=1.0, out=0.0, rx=HAND_RX):
    """쥐는 자리 grip (모델 원점 기준) 이 주먹에 오게: T = −R·S·g + (out, fwd, 0) — 단위 = 모델 단위 (1/16 블록)"""
    a = math.radians(rx)
    gx, gy, gz = (v * scale for v in grip)
    ry = gy * math.cos(a) - gz * math.sin(a)
    rz = gy * math.sin(a) + gz * math.cos(a)
    t = [round(-gx + out, 4), round(-ry + fwd, 4), round(-rz, 4)]
    d = {'rotation': [rx, 0, 0], 'translation': t, 'scale': [scale] * 3}
    return {'thirdperson_righthand': d, 'thirdperson_lefthand': dict(d)}


# ============================================================ 라에타

def laeta(A):
    gold, gold_d, gold_l = '#F2C14E', '#B9872B', '#FFE69C'
    hair, hair_l, hair_d = '#ECC76A', '#F9E6A6', '#CC9C42'
    paper, paper_d = '#FFFFFF', '#E3E3EA'
    rice, rice_l = '#F6E7A8', '#FFF6D2'
    out = {}

    # ---- 머리: 높은 포니테일 (뿌리) · 볏짚 금줄 · 벼 이삭 부채 · 시데 (흰 종이) · 이삭 비녀
    h = Model(A, 'laeta_head')
    h.box([-1.6, 3.6, 1.2], [1.6, 5.4, 4.4], hair)                       # 묶은 뿌리
    h.box([-1.9, 3.9, 1.0], [1.9, 4.9, 4.6], gold)                      # 금줄 (볏짚)
    h.box([-0.6, 4.9, 2.2], [0.6, 5.5, 3.4], gold_l)                    # 매듭
    h.box([-1.4, -1.0, 3.6], [1.4, 4.0, 6.2], hair, rot=('x', -22.5, (0, 4, 4)))   # 포니테일 줄기 (뒤로 흐름)
    h.box([-1.1, -4.0, 4.2], [1.1, -0.5, 6.0], hair_d, rot=('x', -22.5, (0, 4, 4)))
    h.box([-0.4, 0.0, 6.2], [0.4, 3.6, 6.5], hair_l, rot=('x', -22.5, (0, 4, 4)))
    ear_tile = A.tile(2, 6, lambda x, y: gold_l if (x + y) % 2 == 0 else gold)
    for k, ang in enumerate((-45, -22.5, 0, 22.5, 45)):
        a = math.radians(ang)
        L = 6.5 if ang == 0 else 5.6
        bx, by = -math.sin(a) * (L / 2 + 1.0), 5.2 + math.cos(a) * (L / 2 + 1.0)
        h.box([bx - 0.25, by - L / 2, 2.55], [bx + 0.25, by + L / 2, 3.05], gold_d, rot=('z', ang, (bx, by, 2.8)) if ang else None)
        gx, gy = -math.sin(a) * (L + 1.6), 5.2 + math.cos(a) * (L + 1.6)
        h.box([gx - 0.75, gy - 1.9, 2.15], [gx + 0.75, gy + 1.9, 3.45], gold, rot=('z', ang, (gx, gy, 2.8)) if ang else None,
              faces={'north': ear_tile, 'south': ear_tile}, glow=False)
    # 시데: 그 사람의 왼쪽 (−x) 에 지그재그로
    for i in range(4):
        x = -4.7 - (0.5 if i % 2 else 0)
        y = 3.6 - i * 1.6
        h.box([x - 0.35, y - 1.0, 0.4], [x + 0.35, y + 0.6, 1.9], paper if i % 2 == 0 else paper_d)
    h.box([-4.9, 2.2, -1.9], [-4.4, 3.0, 0.6], gold, rot=('x', 22.5, (-4.6, 2.6, -0.6)))   # 이삭 비녀
    h.box([-5.1, 2.7, -3.0], [-4.3, 4.6, -1.6], rice, rot=('x', 22.5, (-4.7, 3.6, -2.3)))
    h.display = HEAD_DISPLAY
    out['laeta_head'] = h

    # ---- 오른손: 북채 (바치) + 감도는 금빛 번개
    b = Model(A, 'laeta_bachi')
    wood, wood_d = '#D9B07A', '#9C6A3C'
    b.box([-0.55, -4.0, -0.55], [0.55, 9.5, 0.55], wood)
    b.box([-0.65, -4.6, -0.65], [0.65, -3.4, 0.65], wood_d)
    b.box([-0.7, 8.6, -0.7], [0.7, 10.2, 0.7], wood_d)
    bolt = '#FFF07A'
    for (x0, y0, x1, y1) in ((0.6, 3.0, 2.4, 4.0), (1.6, 4.0, 2.6, 6.2), (-0.4, 6.0, 2.2, 6.9), (-1.6, 6.9, -0.4, 9.0),
                             (-2.4, 9.0, 0.2, 9.8), (-0.6, 9.8, 0.6, 12.0), (0.2, 11.4, 1.8, 12.2)):
        b.box([x0, y0, -0.35], [x1, y1, 0.35], bolt, glow=True)
    b.display = hand_display(grip=(0, -2.0, 0))
    out['laeta_bachi'] = b

    # ---- 왼손: 됫박 (枡) 에 넘치는 빛나는 쌀
    m = Model(A, 'laeta_masu')
    wood2, wood2_d, wood2_l = '#D8B583', '#A07848', '#EBD3A8'
    crest = A.tile(6, 6, lambda x, y: (gold if (x == 2 or x == 3) and y >= 1 else gold_l if (x + y) % 3 == 0 and 1 <= y <= 4 else wood2))
    m.box([-3.0, -2.0, -3.0], [3.0, 2.0, 3.0], wood2, faces={'north': crest, 'south': crest, 'up': None})
    m.box([-3.2, 1.6, -3.2], [3.2, 2.2, -2.6], wood2_l)
    m.box([-3.2, 1.6, 2.6], [3.2, 2.2, 3.2], wood2_l)
    m.box([-3.2, 1.6, -2.6], [-2.6, 2.2, 2.6], wood2_l)
    m.box([2.6, 1.6, -2.6], [3.2, 2.2, 2.6], wood2_l)
    m.box([-3.05, -2.05, -3.05], [3.05, -1.6, 3.05], wood2_d)
    m.box([-2.6, 1.6, -2.6], [2.6, 3.0, 2.6], rice, glow=True)
    m.box([-1.8, 3.0, -1.8], [1.8, 4.0, 1.8], rice_l, glow=True)
    m.box([-0.8, 4.0, -0.8], [0.8, 4.6, 0.8], rice_l, glow=True)
    for (x, y, z) in ((2.9, 1.2, -1.0), (3.2, 0.2, 0.6), (-3.1, 0.8, 1.2), (1.0, 0.6, -3.2), (-1.4, -0.6, -3.3), (3.3, -1.4, -0.2)):
        m.cube((x, y, z), (0.6, 0.9, 0.6), rice_l, glow=True)
    m.display = hand_display(grip=(0, -2.6, 0), fwd=2.0)
    out['laeta_masu'] = m

    # ---- 등 뒤 북 고리: 고리 하나 + 북 여덟 (둘레 — 서버가 천천히 돌림)
    r = Model(A, 'laeta_ring')
    r.ring(12.0, 1.1, gold, n=16)
    r.ring(12.0, 0.5, gold_l, n=16, center=(0, 0, -0.45))
    out['laeta_ring'] = r
    d = Model(A, 'laeta_drum')
    lac, lac_d = '#B8432C', '#7E2A1C'
    skin_c, skin_d = '#F3E6C8', '#D9C79F'

    def drum_head(x, y):
        dx, dy = x - 5.5, y - 5.5
        rr = math.hypot(dx, dy)
        if rr > 5.6:
            return lac_d
        if rr > 4.7:
            return skin_d
        # 벼 이삭 문장 (미츠도모에 대신): 가운데 줄기 + 양쪽 낟알
        if abs(dx) < 0.8 and -4.0 < dy < 3.6:
            return gold_d
        if 0.8 <= abs(dx) < 2.2 and -3.6 < dy < 1.8 and (int(dy + 10) % 2 == 0):
            return gold
        return skin_c
    head_uv = A.tile(11, 11, drum_head)
    d.box([-2.8, -2.8, -1.5], [2.8, 2.8, 1.5], lac, faces={'north': head_uv, 'south': head_uv})
    d.box([-3.0, -1.8, -1.0], [3.0, 1.8, 1.0], lac_d)
    d.box([-1.8, -3.0, -1.0], [1.8, 3.0, 1.0], lac_d)
    for (x, y) in ((-2.9, 0), (2.9, 0), (0, 2.9), (0, -2.9)):
        d.cube((x, y, 0), (0.7, 0.7, 0.7), gold)
    out['laeta_drum'] = d
    return out


# ============================================================ 니바리아

def nivaria(A):
    ice, ice_l, ice_d = '#BFEFFFC8', '#E8FBFFDC', '#7FC9EECC'
    silver, silver_d = '#D6DEEA', '#9AA7BC'
    pearl = '#FFFDF6'
    teal, teal_d, teal_l = '#1E8189', '#12545A', '#4CB2B6'
    out = {}

    # ---- 머리: 얼음 코코시닉 (부챗살 얼음 조각) · 은 초승달 · 청록 장미 · 진주 줄 · 이마 보석
    h = Model(A, 'nivaria_head')
    h.box([-4.7, 2.6, -4.7], [4.7, 4.0, -3.9], silver)                  # 이마 띠 (앞)
    h.box([-4.7, 2.6, -3.9], [-3.9, 4.0, 3.0], silver)
    h.box([3.9, 2.6, -3.9], [4.7, 4.0, 3.0], silver)
    h.box([-4.4, 4.0, -1.2], [4.4, 5.2, 0.2], silver_d)                 # 머리 위 받침
    for k, ang in enumerate((-67.5, -45, -22.5, 0, 22.5, 45, 67.5)):
        a = math.radians(ang)
        L = (10.5, 9.0, 8.0, 7.0, 8.0, 9.0, 10.5)[k] - 2.5
        L = {0: 9.5, 1: 8.6, 2: 9.8, 3: 11.0, 4: 9.8, 5: 8.6, 6: 9.5}[k]
        cx, cy = -math.sin(a) * (L / 2 + 3.6), 3.2 + math.cos(a) * (L / 2 + 3.6)
        w = 1.6 if k % 2 == 0 else 1.2
        h.bar((cx, cy, -0.5), L, w, ang + 90, ice if k % 2 == 0 else ice_d)
        tx, ty = -math.sin(a) * (L + 3.6), 3.2 + math.cos(a) * (L + 3.6)
        h.bar((tx, ty, -0.5), 1.4, w * 0.6, ang + 90, ice_l, glow=True)
    for k, ang in enumerate((-56.25, -33.75, -11.25, 11.25, 33.75, 56.25)):
        a = math.radians(ang)
        L = 5.0
        cx, cy = -math.sin(a) * (L / 2 + 4.2), 3.2 + math.cos(a) * (L / 2 + 4.2)
        h.bar((cx, cy, -1.4), L, 0.9, round((ang + 90) / 22.5) * 22.5, ice_l)
    # 초승달 (은) + 장미
    h.box([-2.6, 6.0, -1.9], [2.6, 7.0, -1.1], silver)
    h.box([-3.4, 7.0, -1.9], [-2.4, 9.0, -1.1], silver)
    h.box([2.4, 7.0, -1.9], [3.4, 9.0, -1.1], silver)
    rose = A.tile(4, 4, lambda x, y: [teal_l, teal, teal, teal_d][(x + y) % 4] if (x, y) not in ((0, 0), (3, 0), (0, 3), (3, 3)) else teal_d)
    h.box([-1.6, 7.0, -2.3], [1.6, 10.2, -0.9], teal, faces={'north': rose, 'south': rose})
    # 진주 줄 (이마 띠 아래로 두 겹 · 옆으로 늘어짐)
    for i in range(9):
        x = -4.0 + i
        h.cube((x, 2.3 - (0.5 if 2 < i < 6 else 0), -4.55), (0.6, 0.6, 0.6), pearl)
    for side in (-1, 1):
        for i in range(4):
            h.cube((side * 4.75, 1.9 - i * 0.9, -2.8 + i * 0.5), (0.55, 0.55, 0.55), pearl)
    h.box([-0.45, 0.6, -4.42], [0.45, 2.1, -4.12], '#9FE4FF', glow=True)          # 이마 보석 (가운데 한 줄)
    h.display = HEAD_DISPLAY
    out['nivaria_head'] = h

    # ---- 오른손: 펼친 얼음 장부
    b = Model(A, 'nivaria_book')
    cover, cover_d = '#2B4B86', '#1B3260'
    page = A.tile(6, 8, lambda x, y: '#E9F8FF' if y % 2 else ('#9CC9E6' if 1 <= x <= 4 else '#E9F8FF'))
    b.box([-3.4, -0.3, -2.4], [0.0, 0.2, 2.4], cover, rot=('z', 22.5, (0, 0, 0)))
    b.box([0.0, -0.3, -2.4], [3.4, 0.2, 2.4], cover, rot=('z', -22.5, (0, 0, 0)))
    b.box([-3.1, 0.2, -2.1], [0.0, 0.7, 2.1], '#E9F8FF', rot=('z', 22.5, (0, 0, 0)), faces={'up': page})
    b.box([0.0, 0.2, -2.1], [3.1, 0.7, 2.1], '#E9F8FF', rot=('z', -22.5, (0, 0, 0)), faces={'up': page})
    b.box([-0.3, -0.4, -2.5], [0.3, 0.3, 2.5], cover_d)
    b.cube((0, 1.6, 0), (0.6, 0.6, 0.6), '#BFF3FF', glow=True)
    b.cube((1.2, 2.6, -0.6), (0.4, 0.4, 0.4), '#E6FCFF', glow=True)
    out['nivaria_book'] = b

    # ---- 왼손: 은 지팡이 + 얼음 결정 꽃 (빛)
    s = Model(A, 'nivaria_staff')
    s.box([-0.5, -11.0, -0.5], [0.5, 15.0, 0.5], silver)
    s.box([-0.7, -11.5, -0.7], [0.7, -10.5, 0.7], silver_d)
    s.box([-0.8, 14.0, -0.8], [0.8, 15.6, 0.8], silver_d)
    for (ang, L, w) in ((0, 6.0, 1.6), (22.5, 4.6, 1.2), (-22.5, 4.6, 1.2), (45, 3.6, 1.0), (-45, 3.6, 1.0)):
        a = math.radians(ang)
        cx, cy = -math.sin(a) * L / 2, 15.6 + math.cos(a) * L / 2
        s.bar((cx, cy, 0), L, w, ang + 90, ice, glow=True)
        s.bar((cx, cy, 0), L * 0.9, w * 0.9, ang + 90, ice_l, plane='x', glow=True)
    out['nivaria_staff'] = s
    s.display = hand_display(grip=(0, 0, 0))

    # ---- 둘레: 얼음 상자 속 보관품 넷 · 노을을 얼린 알
    keeps = {
        'candle': [((0, -0.6, 0), (0.8, 1.8, 0.8), '#F4EEE0', False), ((0, 0.7, 0), (0.5, 0.6, 0.5), '#FFB347', True)],
        'boat': [((0, -0.4, 0), (2.0, 0.5, 0.9), '#FFFFFF', False), ((0, 0.2, 0), (0.25, 1.2, 0.25), '#E2E2E8', False),
                 ((0.3, 0.3, 0), (0.9, 0.8, 0.15), '#FFFFFF', False)],
        'soup': [((0, -0.5, 0), (1.8, 0.9, 1.8), '#B07A4A', False), ((0, 0.0, 0), (1.4, 0.2, 1.4), '#E8C070', False)],
        'sneeze': [((0, 0, 0), (1.3, 1.0, 1.0), '#F2F6FA', False), ((0.6, 0.3, 0), (0.8, 0.7, 0.7), '#FFFFFF', False),
                   ((-0.5, -0.2, 0.2), (0.7, 0.6, 0.6), '#E6EEF6', False)],
    }
    for key, parts in keeps.items():
        k = Model(A, 'nivaria_keep_' + key)
        for c, sz, col, gl in parts:
            k.cube(c, sz, col, glow=gl)
        k.box([-1.6, -1.6, -1.6], [1.6, 1.6, 1.6], '#CFF4FF70')
        k.box([-1.7, 1.4, -1.7], [1.7, 1.7, 1.7], '#EFFFFF90')
        out['nivaria_keep_' + key] = k
    e = Model(A, 'nivaria_egg')
    sunset = A.tile(4, 6, lambda x, y: ['#FFD27A', '#FFB06A', '#FF8F6A', '#E86A7A', '#A85A9A', '#6A5AA8'][y])
    e.box([-1.3, -1.6, -1.3], [1.3, 1.6, 1.3], '#FF9A6A', glow=True, faces={d: sunset for d in ('north', 'south', 'east', 'west')})
    e.box([-1.8, -2.0, -1.8], [1.8, 2.2, 1.8], '#D8F6FF66')
    e.box([-1.3, 2.2, -1.3], [1.3, 2.8, 1.3], '#D8F6FF66')
    e.box([-1.2, -2.6, -1.2], [1.2, -2.0, 1.2], '#D8F6FF66')
    out['nivaria_egg'] = e
    return out


# ============================================================ 마레아

def marea(A):
    hair, hair_l, hair_d = '#1F7B6F', '#41AA97', '#155A52'
    gold, gold_l, gold_d = '#DDB95D', '#F6DE8E', '#A8873A'
    coral, coral_d = '#F06A5A', '#C24A40'
    pearl = '#FFFDF6'
    jade, jade_l, jade_d = '#2FB08A', '#7FE6C6', '#1A7A5E'
    out = {}
    h = Model(A, 'marea_head')
    h.box([-2.2, 4.0, -0.6], [2.2, 7.6, 3.4], hair)                     # 높은 쪽머리
    h.box([-1.6, 7.6, 0.0], [1.6, 8.6, 2.8], hair_l)
    h.box([-2.4, 4.0, 3.2], [2.4, 5.0, 4.4], hair_d)
    # 금 연꽃관 (쪽머리 앞)
    h.box([-1.0, 7.2, -1.6], [1.0, 8.8, -0.4], gold)
    for ang in (-45, -22.5, 22.5, 45):
        a = math.radians(ang)
        cx, cy = -math.sin(a) * 1.6, 7.6 + math.cos(a) * 1.0
        h.box([cx - 0.6, cy - 1.0, -1.7], [cx + 0.6, cy + 1.0, -0.6], gold_l, rot=('z', ang, (cx, cy, -1.1)))
    h.box([-0.5, 8.8, -1.4], [0.5, 9.8, -0.6], gold_l)
    # 산호 가지 (양옆)
    for side in (-1, 1):
        h.box([side * 2.2 - 0.3, 5.0, 0.6], [side * 2.2 + 0.3, 9.0, 1.2], coral, rot=('z', side * 22.5, (side * 2.2, 5.0, 0.9)))
        h.box([side * 3.4 - 0.25, 7.0, 0.6], [side * 3.4 + 0.25, 9.2, 1.1], coral, rot=('z', side * 45, (side * 3.4, 7.0, 0.9)))
        h.box([side * 2.8 - 0.25, 8.2, 0.6], [side * 2.8 + 0.25, 10.0, 1.1], coral_d)
    # 금 비녀 (그 사람의 왼쪽 = −x) · 연꽃 끝 · 늘어진 진주 셋
    h.box([-5.8, 5.6, 0.7], [1.0, 6.2, 1.3], gold, rot=('z', -22.5, (-2.4, 5.9, 1.0)))
    h.cube((-5.7, 7.0, 1.0), (1.6, 1.2, 1.6), gold_l)
    h.cube((-5.7, 7.8, 1.0), (0.8, 0.8, 0.8), '#F6A8B8')
    for i, dx in enumerate((-0.6, 0.0, 0.6)):
        for j in range(4 + i % 2):
            h.cube((-5.5 + dx, 6.0 - j * 0.95, 0.4 + dx * 0.5), (0.5, 0.5, 0.5), pearl)
    # 이마 꽃잎 (가운데 한 줄)
    h.box([-0.45, 1.2, -4.42], [0.45, 2.4, -4.12], '#F2907E')
    # 뒤로 흐르는 포니테일 머리 (망토와 이어짐)
    h.box([-1.6, -4.6, 4.2], [1.6, 4.0, 5.8], hair)
    h.box([-0.5, -4.0, 5.8], [0.5, 3.0, 6.1], hair_l)
    h.display = HEAD_DISPLAY
    out['marea_head'] = h

    # ---- 오른손: 옥 여의 (구름 머리) · 붉은 술
    r = Model(A, 'marea_ruyi')
    r.box([-0.45, -8.0, -0.45], [0.45, 9.0, 0.45], jade)
    r.box([-0.6, -2.0, -0.6], [0.6, 0.8, 0.6], gold)
    r.box([-1.8, 9.0, -1.0], [1.8, 11.0, 1.0], jade_l)
    r.box([-2.4, 10.0, -0.8], [-1.0, 12.4, 0.8], jade)
    r.box([1.0, 10.0, -0.8], [2.4, 12.4, 0.8], jade)
    r.box([-1.0, 11.0, -0.8], [1.0, 13.0, 0.8], jade)
    r.cube((0, 11.6, -0.9), (0.9, 0.9, 0.3), gold)
    r.box([-0.3, -11.0, -0.3], [0.3, -8.0, 0.3], '#C8323A')
    r.box([-0.6, -12.2, -0.6], [0.6, -11.0, 0.6], '#E04A50')
    r.display = hand_display(grip=(0, -1.0, 0))
    out['marea_ruyi'] = r

    # ---- 왼손: 물방울 속 빛나는 영지
    l = Model(A, 'marea_lingzhi')
    l.box([-0.5, -1.0, -0.5], [0.5, 2.0, 0.5], '#E8D8B0', glow=True)
    l.box([-2.4, 2.0, -2.0], [2.4, 2.8, 2.0], '#F5A84A', glow=True)
    l.box([-1.6, 2.8, -1.4], [1.6, 3.4, 1.4], '#FFD27A', glow=True)
    l.box([-2.8, 2.2, -1.0], [2.8, 2.6, 1.0], '#E8892E', glow=True)
    l.cube((0, -1.6, 0), (1.6, 0.6, 1.6), '#7FE8F0A0')
    out['marea_lingzhi'] = l
    sp = Model(A, 'marea_spiral')
    sp.ring(4.2, 0.7, '#8FEFF4B0', n=16, plane='y')
    sp.ring(3.2, 0.5, '#D8FFFFC8', n=16, plane='y', center=(0, 2.2, 0), glow=True)
    sp.ring(2.2, 0.4, '#D8FFFFC8', n=8, plane='y', center=(0, 4.2, 0), glow=True)
    out['marea_spiral'] = sp

    # ---- 둘레: 물 고리 · 빛나는 물고기 둘
    w = Model(A, 'marea_water')
    w.ring(13.0, 1.3, '#7FE8F0A0', n=16, plane='y')
    w.ring(13.0, 0.5, '#E0FFFFC8', n=16, plane='y', center=(0, 0.7, 0), glow=True)
    out['marea_water'] = w
    for key, body, fin in (('gold', '#FFC14A', '#FF8A3A'), ('jade', '#5FE6C0', '#2FA88A')):
        f = Model(A, 'marea_fish_' + key)
        f.box([-0.7, -0.8, -1.8], [0.7, 0.8, 1.2], body, glow=True)
        f.box([-0.4, -0.5, 1.2], [0.4, 0.5, 2.0], body, glow=True)
        f.box([-0.15, -1.2, 2.0], [0.15, 1.2, 3.0], fin, glow=True)
        f.box([-0.12, 0.8, -0.6], [0.12, 1.4, 0.8], fin, glow=True)
        f.cube((0.72, 0.3, -1.2), (0.1, 0.3, 0.3), '#102020')
        f.cube((-0.72, 0.3, -1.2), (0.1, 0.3, 0.3), '#102020')
        out['marea_fish_' + key] = f
    return out


# ============================================================ 포르티아

def fortia(A):
    copper, copper_d, copper_l = '#C47442', '#8E4E2A', '#E39C64'
    obs, obs_l = '#141012', '#3A3034'
    ember, ember_l = '#FF7A1A', '#FFC24A'
    out = {}
    h = Model(A, 'fortia_head')
    # 구리 머리띠 (이마 둘레)
    h.box([-4.75, 1.4, -4.75], [4.75, 2.8, -4.05], copper)
    h.box([-4.75, 1.4, -4.05], [-4.05, 2.8, 4.75], copper_d)
    h.box([4.05, 1.4, -4.05], [4.75, 2.8, 4.75], copper_d)
    h.box([-4.05, 1.4, 4.05], [4.05, 2.8, 4.75], copper_d)
    # 이마 장식 (방패 모양)
    h.box([-1.4, 1.0, -5.05], [1.4, 3.8, -4.55], copper_l)
    h.box([-0.6, 1.6, -5.25], [0.6, 3.2, -4.95], ember, glow=True)
    # 흑요석 칼날 일곱 (머리 위로 부챗살) · 끝은 불씨
    for k, ang in enumerate((-67.5, -45, -22.5, 0, 22.5, 45, 67.5)):
        a = math.radians(ang)
        L = (5.5, 7.0, 8.2, 9.4, 8.2, 7.0, 5.5)[k]
        r0 = 4.4
        cx, cy = -math.sin(a) * (L / 2 + r0), 2.0 + math.cos(a) * (L / 2 + r0)
        h.bar((cx, cy, -2.6), L, 1.5, ang + 90, obs)
        h.bar((cx, cy, -3.4), L * 0.7, 0.5, ang + 90, obs_l)
        tx, ty = -math.sin(a) * (L + r0 + 0.4), 2.0 + math.cos(a) * (L + r0 + 0.4)
        h.bar((tx, ty, -2.6), 1.2, 0.9, ang + 90, ember_l if k % 2 else ember, glow=True)
    # 칼날 뒤의 불길
    for (x, y, sz) in ((-4.5, 8.5, 1.8), (4.5, 8.5, 1.8), (-2.2, 11.5, 1.5), (2.2, 11.5, 1.5), (0, 13.2, 1.4), (-6.6, 6.0, 1.4), (6.6, 6.0, 1.4)):
        h.cube((x, y, -1.4), (sz, sz * 1.3, 0.8), ember, glow=True)
        h.cube((x, y - 0.3, -1.9), (sz * 0.55, sz * 0.7, 0.3), ember_l, glow=True)
    h.display = HEAD_DISPLAY
    out['fortia_head'] = h

    # ---- 오른손: 흑요석 창
    s = Model(A, 'fortia_spear')
    shaft, shaft_d = '#3A2A20', '#24180F'
    s.box([-0.55, -12.0, -0.55], [0.55, 15.0, 0.55], shaft)
    s.box([-0.7, -12.6, -0.7], [0.7, -11.6, 0.7], copper)
    s.box([-0.75, 14.2, -0.75], [0.75, 16.0, 0.75], copper)
    s.box([-1.8, 16.0, -0.45], [1.8, 20.0, 0.45], obs)
    s.box([-1.2, 20.0, -0.4], [1.2, 22.4, 0.4], obs)
    s.box([-0.6, 22.4, -0.35], [0.6, 24.2, 0.35], obs)
    s.box([-0.2, 16.4, -0.5], [0.2, 23.6, 0.5], obs_l)
    s.box([-0.6, 13.0, -0.62], [0.6, 14.2, 0.62], '#C8261C')
    for (x, y) in ((1.6, 18.5), (-1.4, 20.5), (0.9, 22.6), (-0.6, 24.6)):
        s.cube((x, y, 0), (0.9, 1.6, 0.9), ember, glow=True)
        s.cube((x, y + 0.9, 0), (0.5, 0.8, 0.5), ember_l, glow=True)
    s.display = hand_display(grip=(0, -1.0, 0))
    out['fortia_spear'] = s

    # ---- 왼손: 가운데가 화로처럼 타는 둥근 방패
    sh = Model(A, 'fortia_shield')
    face_uv = A.tile(14, 14, lambda x, y: (lambda r: ember_l if r < 1.9 else ember if r < 3.4 else '#C8301A' if r < 4.4 else
                                           (obs_l if (x + y) % 5 == 0 else obs))(math.hypot(x - 6.5, y - 6.5)))
    sh.box([-7.0, -4.0, -0.8], [7.0, 4.0, 0.6], obs, faces={'north': face_uv})
    sh.box([-4.0, -7.0, -0.8], [4.0, 7.0, 0.6], obs, faces={'north': face_uv})
    sh.box([-5.6, -5.6, -0.8], [5.6, 5.6, 0.6], obs, faces={'north': face_uv})
    sh.ring(6.6, 0.9, copper, n=16, center=(0, 0, -0.5))
    sh.box([-2.4, -2.4, -1.4], [2.4, 2.4, -0.8], ember, glow=True)
    sh.box([-1.4, -1.4, -1.9], [1.4, 1.4, -1.4], ember_l, glow=True)
    sh.box([-0.6, -3.0, 0.6], [0.6, 3.0, 1.3], copper_d)
    sh.display = hand_display(grip=(0, 0, 1.0), fwd=1.5, out=-1.0)
    out['fortia_shield'] = sh

    # ---- 둘레: 왼쪽 견갑에 박힌 화살 둘 (뽑지 않음)
    a = Model(A, 'fortia_arrows')
    for (dx, dz) in ((0.0, 0.0), (1.4, 1.2)):
        a.box([dx - 0.25, 0.0, dz - 0.25], [dx + 0.25, 9.0, dz + 0.25], '#5A4030')
        a.box([dx - 0.7, 7.4, dz - 0.08], [dx + 0.7, 9.6, dz + 0.08], '#E8E0D0')
        a.box([dx - 0.08, 7.4, dz - 0.7], [dx + 0.08, 9.6, dz + 0.7], '#C8261C')
    out['fortia_arrows'] = a
    return out


# ============================================================ 그라비스

def gravis(A):
    gold, gold_d, gold_l = '#E2B242', '#A87A20', '#FFE08C'
    turq, lapis = '#2CB4A7', '#2C4C9C'
    out = {}
    panel = A.tile(10, 12, lambda x, y: (gold_d if x in (0, 9) or y in (0, 11) else
                                         gold if x % 3 == 0 or y % 4 == 3 else
                                         (turq if (x // 3 + y // 4) % 2 == 0 else lapis) if (x % 3 != 0 and y % 4 != 3) else gold))
    h = Model(A, 'gravis_head')
    # 높은 원통 관: 두 네모 (0° · 45°) 를 겹쳐 팔각 기둥
    h.box([-4.4, 3.4, -4.4], [4.4, 13.6, 4.4], gold, faces={d: panel for d in ('north', 'south', 'east', 'west')})
    h.box([-4.4, 3.5, -4.4], [4.4, 13.5, 4.4], gold, rot=('y', 45, (0, 0, 0)), faces={d: panel for d in ('north', 'south', 'east', 'west')})
    h.box([-4.9, 2.9, -4.9], [4.9, 4.3, 4.9], gold_d)                  # 아래 띠
    h.box([-4.9, 2.95, -4.9], [4.9, 4.25, 4.9], gold_d, rot=('y', 45, (0, 0, 0)))
    h.box([-4.8, 13.4, -4.8], [4.8, 14.6, 4.8], gold_l)                # 위 테
    h.box([-4.8, 13.45, -4.8], [4.8, 14.55, 4.8], gold_l, rot=('y', 45, (0, 0, 0)))
    for k in range(8):                                                   # 위 톱니
        a = math.radians(22.5 + 45 * k)
        h.cube((math.sin(a) * 4.3, 15.1, math.cos(a) * 4.3), (1.3, 1.2, 1.3), gold)
    h.cube((0, 8.6, -5.0), (1.4, 1.4, 0.4), '#E8F4FF', glow=True)       # 앞 보석
    h.display = HEAD_DISPLAY
    out['gravis_head'] = h

    # ---- 오른손: 금 갈대 펜 (서명하지 못한 펜)
    p = Model(A, 'gravis_pen')
    p.box([-0.35, -3.0, -0.35], [0.35, 6.0, 0.35], gold)
    p.box([-0.5, -1.0, -0.5], [0.5, 1.0, 0.5], gold_d)
    p.box([-0.2, 6.0, -0.2], [0.2, 7.4, 0.2], '#2A2A30')
    p.display = hand_display(grip=(0, 0, 0), fwd=0.5)
    out['gravis_pen'] = p

    # ---- 왼손: 꼭대기에 저울 (두 접시가 늘 수평) 을 단 금 지팡이
    s = Model(A, 'gravis_staff')
    s.box([-0.55, -11.0, -0.55], [0.55, 17.0, 0.55], gold)
    s.box([-0.75, -11.6, -0.75], [0.75, -10.6, 0.75], gold_d)
    s.box([-0.8, 16.4, -0.8], [0.8, 18.2, 0.8], gold_d)
    s.cube((0, 18.9, 0), (1.2, 1.2, 1.2), '#E8F4FF', glow=True)
    s.box([-4.6, 17.6, -0.4], [4.6, 18.3, 0.4], gold)                 # 저울대
    for side in (-1, 1):
        x = side * 4.2
        for (dx, dz) in ((-1.0, 0), (1.0, 0), (0, -1.0), (0, 1.0)):
            s.box([x + dx * 0.9 - 0.1, 13.0, dz * 0.9 - 0.1], [x + dx * 0.9 + 0.1, 17.6, dz * 0.9 + 0.1], gold_l)
        s.box([x - 1.5, 12.4, -1.5], [x + 1.5, 13.0, 1.5], gold)       # 접시
        s.box([x - 1.1, 12.1, -1.1], [x + 1.1, 12.4, 1.1], gold_d)
    s.display = hand_display(grip=(0, 0, 0), out=-2.5)
    out['gravis_staff'] = s

    # ---- 둘레: 떠오르는 돌 셋 (무게 없는 곳)
    for key, base, top in (('a', '#8E8A7E', '#B0AA98'), ('b', '#A8906A', '#C9B08A'), ('c', '#6E7A70', '#929E92')):
        st = Model(A, 'gravis_stone_' + key)
        st.box([-1.4, -1.0, -1.2], [1.4, 1.2, 1.2], base)
        st.box([-1.0, 1.2, -0.9], [1.0, 1.7, 0.9], top)
        st.box([-0.8, -1.5, -0.6], [0.9, -1.0, 0.7], shade(base, 0.8))
        st.cube((1.2, 0.6, -1.1), (0.3, 0.3, 0.3), gold_l, glow=True)
        out['gravis_stone_' + key] = st
    return out


GODS = {'laeta': laeta, 'nivaria': nivaria, 'marea': marea, 'fortia': fortia, 'gravis': gravis}
