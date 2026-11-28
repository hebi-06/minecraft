#!/usr/bin/env python3
"""원소신 NPC 그림 도구 (2026-11-28): 64 × 64 사람 스킨을 부위 · 면 단위로 칠하는 붓 + 미리보기 렌더러.

스킨 판 (바닐라 1.8+ 형식 — 가는 팔은 폭 3): 부위 (head · body · rarm · larm · rleg · lleg) × 면 (top · bottom · right · front · left · back)
× 겹 (0 몸 · 1 겉옷). 면 그림의 방향 (바닐라 ModelPart.Cube 그대로 — 2026-11-28 확인):
  front: 왼쪽 = 그 사람의 오른쪽, back: 왼쪽 = 그 사람의 왼쪽, right (오른쪽 옆): 왼쪽 = 뒤, left (왼쪽 옆): 왼쪽 = 앞,
  top · bottom: 위 = 뒤, 왼쪽 = 그 사람의 오른쪽.
둘레 (ring): 한 줄을 앞 → 왼쪽 옆 → 뒤 → 오른쪽 옆 차례로 이어 칠함 (무늬 띠).

미리보기 좌표 (픽셀, 발 = y 0): x+ = 그 사람의 왼쪽 (보는 사람의 오른쪽), y+ = 위, z+ = 앞 (사람이 보는 쪽) — 몸 방향 0 인 마인크래프트 세계와 같음.
j 공간 (아이템 모델 · 표시 엔티티 · 머리 장식이 쓰는 몸 기준): x+ = 그 사람의 오른쪽, y+ = 위, z+ = 뒤 — 미리보기 = Ry(180°)(j).
"""
import math
from PIL import Image, ImageDraw

# ------------------------------------------------------------ 색


def rgba(c):
    if c is None:
        return None
    if isinstance(c, tuple):
        return c if len(c) == 4 else (c[0], c[1], c[2], 255)
    s = c.lstrip('#')
    if len(s) in (3, 4):
        s = ''.join(ch * 2 for ch in s)
    if len(s) == 6:
        return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), 255)
    if len(s) == 8:
        return (int(s[0:2], 16), int(s[2:4], 16), int(s[4:6], 16), int(s[6:8], 16))
    raise ValueError(c)


def mix(a, b, t):
    a, b = rgba(a), rgba(b)
    return tuple(int(round(a[i] + (b[i] - a[i]) * t)) for i in range(4))


def shade(c, k):
    c = rgba(c)
    return (max(0, min(255, int(c[0] * k))), max(0, min(255, int(c[1] * k))), max(0, min(255, int(c[2] * k))), c[3])


def alpha(c, a):
    c = rgba(c)
    return (c[0], c[1], c[2], a)


# ------------------------------------------------------------ 스킨 판

def part_geo(part, slim):
    """(폭, 높이, 깊이, 몸 겹 UV, 겉옷 겹 UV)"""
    aw = 3 if slim else 4
    return {
        'head': (8, 8, 8, (0, 0), (32, 0)),
        'body': (8, 12, 4, (16, 16), (16, 32)),
        'rarm': (aw, 12, 4, (40, 16), (40, 32)),
        'larm': (aw, 12, 4, (32, 48), (48, 48)),
        'rleg': (4, 12, 4, (0, 16), (0, 32)),
        'lleg': (4, 12, 4, (16, 48), (0, 48)),
    }[part]


def face_rect(part, face, layer, slim):
    w, h, d, b, o = part_geo(part, slim)
    u, v = b if layer == 0 else o
    return {
        'top': (u + d, v, w, d),
        'bottom': (u + d + w, v, w, d),
        'right': (u, v + d, d, h),
        'front': (u + d, v + d, w, h),
        'left': (u + d + w, v + d, d, h),
        'back': (u + d + w + d, v + d, w, h),
    }[face]


PARTS = ('head', 'body', 'rarm', 'larm', 'rleg', 'lleg')
FACES = ('top', 'bottom', 'right', 'front', 'left', 'back')


class Skin:
    def __init__(self, slim):
        self.slim = slim
        self.img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
        self.px = self.img.load()

    def rect(self, part, face, layer=0):
        return face_rect(part, face, layer, self.slim)

    def size(self, part):
        w, h, d, _, _ = part_geo(part, self.slim)
        return w, h, d

    def set(self, part, face, x, y, c, layer=0):
        u, v, w, h = self.rect(part, face, layer)
        if c is None or not (0 <= x < w and 0 <= y < h):
            return
        self.px[u + x, v + y] = rgba(c)

    def get(self, part, face, x, y, layer=0):
        u, v, w, h = self.rect(part, face, layer)
        return self.px[u + x, v + y]

    def clear(self, part, face, x, y, layer=0):
        u, v, w, h = self.rect(part, face, layer)
        if 0 <= x < w and 0 <= y < h:
            self.px[u + x, v + y] = (0, 0, 0, 0)

    def fill(self, part, face, c, layer=0, rows=None, cols=None):
        u, v, w, h = self.rect(part, face, layer)
        for y in range(h):
            if rows is not None and y not in rows:
                continue
            for x in range(w):
                if cols is not None and x not in cols:
                    continue
                self.px[u + x, v + y] = rgba(c)

    def fill_part(self, part, c, layer=0):
        for f in FACES:
            self.fill(part, f, c, layer)

    def grid(self, part, face, rows, pal, layer=0, x0=0, y0=0):
        """글자 그림: pal 의 글자 → 색, '.' · ' ' 는 그대로 둠, '_' 는 지움 (투명)"""
        for y, row in enumerate(rows):
            for x, ch in enumerate(row):
                if ch in '. ':
                    continue
                if ch == '_':
                    self.clear(part, face, x0 + x, y0 + y, layer)
                    continue
                self.set(part, face, x0 + x, y0 + y, pal[ch], layer)

    def ring_len(self, part):
        w, h, d = self.size(part)
        return 2 * (w + d)

    def ring_at(self, part, i):
        """둘레 i → (면, x) — 앞 → 왼쪽 옆 → 뒤 → 오른쪽 옆"""
        w, h, d = self.size(part)
        i %= 2 * (w + d)
        if i < w:
            return 'front', i
        i -= w
        if i < d:
            return 'left', i
        i -= d
        if i < w:
            return 'back', i
        i -= w
        return 'right', i

    def ring(self, part, y, color, layer=0):
        """한 줄 둘레: color = 색 또는 함수 (i, n, 면, x) → 색 (None 이면 건너뜀)"""
        n = self.ring_len(part)
        for i in range(n):
            f, x = self.ring_at(part, i)
            c = color(i, n, f, x) if callable(color) else color
            if c is not None:
                self.set(part, f, x, y, c, layer)

    def sides(self, part, c, layer=0, rows=None):
        """네 옆면 (앞 · 뒤 · 양옆) 을 같은 색으로"""
        for f in ('front', 'back', 'left', 'right'):
            self.fill(part, f, c, layer, rows=rows)

    # 다리 둘을 한 판으로 (치마 · 로브 무늬): X 0..7 — 앞은 보는 사람 왼쪽 = 오른다리, 뒤는 보는 사람 왼쪽 = 왼다리
    def legs_front(self, X, y, c, layer=0):
        if X < 4:
            self.set('rleg', 'front', X, y, c, layer)
        else:
            self.set('lleg', 'front', X - 4, y, c, layer)

    def legs_back(self, X, y, c, layer=0):
        if X < 4:
            self.set('lleg', 'back', X, y, c, layer)
        else:
            self.set('rleg', 'back', X - 4, y, c, layer)

    def legs_ring(self, y, color, layer=0):
        """다리 둘을 감싼 한 줄 (치마 단): 앞 8 → 왼다리 바깥 4 → 뒤 8 → 오른다리 바깥 4 (안쪽 옆은 겹 사이라 같은 색)"""
        n = 24
        for i in range(n):
            c = color(i, n) if callable(color) else color
            if c is None:
                continue
            if i < 8:
                self.legs_front(i, y, c, layer)
            elif i < 12:
                self.set('lleg', 'left', i - 8, y, c, layer)
            elif i < 20:
                self.legs_back(i - 12, y, c, layer)
            else:
                self.set('rleg', 'right', i - 20, y, c, layer)
        # 안쪽 옆 (다리 사이): 바깥과 같은 줄의 앞 끝 색
        for leg, f in (('rleg', 'left'), ('lleg', 'right')):
            for x in range(4):
                c0 = color(3 if leg == 'rleg' else 4, n) if callable(color) else color
                if c0 is not None:
                    self.set(leg, f, x, y, c0, layer)

    def save(self, path):
        self.img.save(path)


# ------------------------------------------------------------ 미리보기 렌더러 (직교 · 화가 알고리즘 · 텍셀마다 다각형)

class Quad:
    __slots__ = ('p', 'tex', 'uv', 'normal', 'glow')

    def __init__(self, p, tex, uv, normal, glow=False):
        self.p = p          # TL, TR, BR, BL (3D)
        self.tex = tex      # PIL 이미지 (RGBA)
        self.uv = uv        # (u0, v0, u1, v1) 텍셀 (뒤집기 = u1 < u0)
        self.normal = normal
        self.glow = glow


def _sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def _mul(a, k):
    return (a[0] * k, a[1] * k, a[2] * k)


def _cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def _norm(a):
    l = math.sqrt(a[0] ** 2 + a[1] ** 2 + a[2] ** 2) or 1
    return (a[0] / l, a[1] / l, a[2] / l)


def box_quads(lo, hi, tex, rects, inflate=0.0, glow=False):
    """사람 모델 상자 하나: rects = {면: (u, v, w, h)} (스킨 판 텍셀) — 미리보기 좌표 (x+ 왼쪽, z+ 앞)"""
    x0, y0, z0 = lo[0] - inflate, lo[1] - inflate, lo[2] - inflate
    x1, y1, z1 = hi[0] + inflate, hi[1] + inflate, hi[2] + inflate
    P = {
        'front': ((x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1), (0, 0, 1)),
        'back': ((x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0), (0, 0, -1)),
        'right': ((x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0), (-1, 0, 0)),
        'left': ((x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1), (1, 0, 0)),
        'top': ((x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1), (0, 1, 0)),
        'bottom': ((x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1), (0, -1, 0)),
    }
    out = []
    for f, (u, v, w, h) in rects.items():
        a, b, c, d, n = P[f]
        out.append(Quad((a, b, c, d), tex, (u, v, u + w, v + h), n, glow))
    return out


def player_quads(skin_img, slim, cape_img=None, overlay=True, arms=None):
    """사람 모델 (서 있는 자세 · 팔은 arms = {'rarm': x각, 'larm': x각} — 앞으로 든 각, 도)"""
    out = []
    aw = 3 if slim else 4
    ay = 0.5 if slim else 0.0
    geo = {
        'head': ((-4, 24, -4), (4, 32, 4)),
        'body': ((-4, 12, -2), (4, 24, 2)),
        'rarm': ((-4 - aw, 12 - ay, -2), (-4, 24 - ay, 2)),
        'larm': ((4, 12 - ay, -2), (4 + aw, 24 - ay, 2)),
        'rleg': ((-4, 0, -2), (0, 12, 2)),
        'lleg': ((0, 0, -2), (4, 12, 2)),
    }
    pivots = {'rarm': (-5, 22 - ay, 0), 'larm': (5, 22 - ay, 0)}
    for part, (lo, hi) in geo.items():
        for layer in ((0, 1) if overlay else (0,)):
            rects = {f: face_rect(part, f, layer, slim) for f in FACES}
            inf = 0.0 if layer == 0 else (0.5 if part == 'head' else 0.25)
            qs = box_quads(lo, hi, skin_img, rects, inf)
            if arms and part in arms and arms[part]:
                qs = [rotate_quad(q, pivots[part], 'x', arms[part]) for q in qs]
            out += qs
    if cape_img is not None:
        out += cape_quads(cape_img)
    return out


def rotate_point(p, origin, axis, deg):
    a = math.radians(deg)
    x, y, z = _sub(p, origin)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        y, z = y * c - z * s, y * s + z * c
    elif axis == 'y':
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return _add((x, y, z), origin)


def rotate_quad(q, origin, axis, deg):
    p = tuple(rotate_point(v, origin, axis, deg) for v in q.p)
    n = rotate_point(q.normal, (0, 0, 0), axis, deg)
    return Quad(p, q.tex, q.uv, n, q.glow)


def cape_quads(cape_img, tilt=6.0):
    """망토 (64 × 32 — 바닐라 망토 상자 10 × 16 × 1): 바깥 면 (1,1)-(11,17) 은 뒤에서 볼 때 왼쪽 = 그 사람의 왼쪽"""
    x0, x1, y0, y1, z0, z1 = -5, 5, 8, 24, -3, -2
    out = [
        Quad(((x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)), cape_img, (1, 1, 11, 17), (0, 0, -1)),
        Quad(((x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)), cape_img, (12, 1, 22, 17), (0, 0, 1)),
        Quad(((x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)), cape_img, (0, 1, 1, 17), (-1, 0, 0)),
        Quad(((x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)), cape_img, (11, 1, 12, 17), (1, 0, 0)),
        Quad(((x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)), cape_img, (11, 0, 21, 1), (0, -1, 0)),
    ]
    return [rotate_quad(q, (0, 24, -2), 'x', -tilt) for q in out]


# ------------------------------------------------------------ 아이템 모델 (블록 모델 요소) → 사각형

def element_quads(el, tex, tex_scale, xf, glow=False):
    """el = 블록 모델 요소 (from · to · faces · rotation), tex_scale = 텍스처 픽셀 / UV 단위, xf = 모델 좌표 → 미리보기 좌표 함수"""
    (x0, y0, z0), (x1, y1, z1) = el['from'], el['to']
    P = {
        'north': ((x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0), (0, 0, -1)),
        'south': ((x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1), (0, 0, 1)),
        'east': ((x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1), (1, 0, 0)),
        'west': ((x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0), (-1, 0, 0)),
        'up': ((x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1), (0, 1, 0)),
        'down': ((x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0), (0, -1, 0)),
    }
    rot = el.get('rotation')
    out = []
    for f, spec in el.get('faces', {}).items():
        a, b, c, d, n = P[f]
        pts = [a, b, c, d]
        if rot:
            pts = [rotate_point(p, tuple(rot['origin']), rot['axis'], rot['angle']) for p in pts]
            n = rotate_point(n, (0, 0, 0), rot['axis'], rot['angle'])
        o = xf((0, 0, 0))
        pts = [xf(p) for p in pts]
        n = _sub(xf(_add(n, (0, 0, 0))), o)
        u0, v0, u1, v1 = spec['uv']
        out.append(Quad(tuple(pts), tex, (u0 * tex_scale, v0 * tex_scale, u1 * tex_scale, v1 * tex_scale), _norm(n),
                        glow or bool(el.get('light_emission'))))
    return out


def mat_apply(m, p):
    return (m[0][0] * p[0] + m[0][1] * p[1] + m[0][2] * p[2] + m[0][3],
            m[1][0] * p[0] + m[1][1] * p[1] + m[1][2] * p[2] + m[1][3],
            m[2][0] * p[0] + m[2][1] * p[1] + m[2][2] * p[2] + m[2][3])


def mat_mul(a, b):
    return [[sum(a[i][k] * b[k][j] for k in range(4)) for j in range(4)] for i in range(4)]


def m_ident():
    return [[1, 0, 0, 0], [0, 1, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def m_t(x, y, z):
    return [[1, 0, 0, x], [0, 1, 0, y], [0, 0, 1, z], [0, 0, 0, 1]]


def m_s(x, y=None, z=None):
    y = x if y is None else y
    z = x if z is None else z
    return [[x, 0, 0, 0], [0, y, 0, 0], [0, 0, z, 0], [0, 0, 0, 1]]


def m_r(axis, deg):
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == 'x':
        return [[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]]
    if axis == 'y':
        return [[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]]
    return [[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]]


def m_chain(*ms):
    out = m_ident()
    for m in ms:
        out = mat_mul(out, m)
    return out


def display_matrix(disp):
    """블록 모델 display 변환 (ItemTransform.apply): T(t / 16) · Rx · Ry · Rz · S — 아이템 블록 단위, 모델 가운데 (−0.5)"""
    t = disp.get('translation', [0, 0, 0])
    r = disp.get('rotation', [0, 0, 0])
    s = disp.get('scale', [1, 1, 1])
    return m_chain(m_t(t[0] / 16, t[1] / 16, t[2] / 16), m_r('x', r[0]), m_r('y', r[1]), m_r('z', r[2]), m_s(s[0], s[1], s[2]),
                   m_t(-0.5, -0.5, -0.5), m_s(1 / 16))


J_TO_PREVIEW = m_r('y', 180)


def head_xf(disp):
    """머리에 쓴 아이템 (CustomHeadLayer): 아이템 공간 1 블록 = 머리 10 px, 머리 가운데 = 목 위 4 px → 미리보기"""
    D = display_matrix(disp)
    M = m_chain(J_TO_PREVIEW, m_t(0, 28, 0), m_s(10), D)
    return lambda p: mat_apply(M, p)


def hand_xf(disp, right=True, slim=False, arm_x=-18.0):
    """손에 든 아이템 (ItemInHandLayer): 모델 공간 (y 아래 · 앞 −z · 오른쪽 −x, 블록) 에서 셈한 뒤 미리보기로.
    팔 = 어깨 축 (±5, 2 | 가는 팔 2.5 · 손 자리 ±0.5) · Rx(팔 각), 그다음 Rx(−90) · Ry(180) · T(±1/16, 2/16, −10/16) · display"""
    sgn = -1 if right else 1
    ay = 2.5 if slim else 2.0
    px = sgn * 5 + ((-sgn) * 0.5 if slim else 0)
    D = display_matrix(disp)
    model = m_chain(m_t(px / 16, ay / 16, 0), m_r('x', arm_x), m_r('x', -90), m_r('y', 180),
                    m_t((1 if right else -1) / 16, 2 / 16, -10 / 16), D)
    to_preview = [[16, 0, 0, 0], [0, -16, 0, 24], [0, 0, -16, 0], [0, 0, 0, 1]]
    M = mat_mul(to_preview, model)
    return lambda p: mat_apply(M, p)


def j_xf(matrix_j_px):
    """j 공간 (픽셀) 변환 → 미리보기 (표시 엔티티: 모델 가운데 · 1 모델 단위 = 1 px × k 는 matrix 에)"""
    M = m_chain(J_TO_PREVIEW, matrix_j_px, m_t(-8, -8, -8))
    return lambda p: mat_apply(M, p)


def model_quads(model, tex, xf, glow=False):
    ts = tex.size[0] / 16.0
    out = []
    for el in model['elements']:
        out += element_quads(el, tex, ts, xf, glow)
    return out


def render(quads, yaw=0.0, pitch=10.0, scale=10.0, size=(360, 560), center=(0, 17, 0), bg=(226, 232, 240, 255), ss=2):
    """직교 사영: 카메라는 앞 (z+) 에서 yaw (도, 시계 방향 = 오른쪽으로 돌아 봄) · pitch (도, 위에서 내려다봄)"""
    W, H = size[0] * ss, size[1] * ss
    img = Image.new('RGBA', (W, H), bg)
    ya, pa = math.radians(yaw), math.radians(pitch)

    def view(p):
        x, y, z = p[0] - center[0], p[1] - center[1], p[2] - center[2]
        # 카메라를 yaw 만큼 돌림 = 세계를 −yaw
        x, z = x * math.cos(ya) - z * math.sin(ya), x * math.sin(ya) + z * math.cos(ya)
        y, z = y * math.cos(pa) - z * math.sin(pa), y * math.sin(pa) + z * math.cos(pa)
        return x, y, z

    polys = []
    for q in quads:
        n = view(_add(q.normal, center))
        o = view(center)
        if (n[2] - o[2]) <= 1e-4:
            continue
        pts = [view(p) for p in q.p]
        u0, v0, u1, v1 = q.uv
        nu = max(1, int(round(abs(u1 - u0))))
        nv = max(1, int(round(abs(v1 - v0))))
        tw, th = q.tex.size
        tp = q.tex.load()
        light = 1.0 if q.glow else max(0.55, min(1.0, 0.72 + 0.28 * (q.normal[1]) + 0.12 * (n[2] - o[2])))
        for j in range(nv):
            for i in range(nu):
                su = u0 + (u1 - u0) * (i + 0.5) / nu
                sv = v0 + (v1 - v0) * (j + 0.5) / nv
                tx, ty = int(math.floor(su)), int(math.floor(sv))
                if not (0 <= tx < tw and 0 <= ty < th):
                    continue
                c = tp[tx, ty]
                if c[3] < 8:
                    continue
                c = (int(c[0] * light), int(c[1] * light), int(c[2] * light), c[3])
                s0, s1, t0, t1 = i / nu, (i + 1) / nu, j / nv, (j + 1) / nv
                corners = []
                for s, t in ((s0, t0), (s1, t0), (s1, t1), (s0, t1)):
                    top = _add(pts[0], _mul(_sub(pts[1], pts[0]), s))
                    bot = _add(pts[3], _mul(_sub(pts[2], pts[3]), s))
                    p = _add(top, _mul(_sub(bot, top), t))
                    corners.append(p)
                depth = sum(p[2] for p in corners) / 4
                xy = [(W / 2 + p[0] * scale * ss, H / 2 - p[1] * scale * ss) for p in corners]
                polys.append((depth, xy, c))
    polys.sort(key=lambda t: t[0])
    draw = ImageDraw.Draw(img)
    for depth, xy, c in polys:
        if c[3] >= 250:
            draw.polygon(xy, fill=c)
        else:
            layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
            ImageDraw.Draw(layer).polygon(xy, fill=c)
            img = Image.alpha_composite(img, layer)
            draw = ImageDraw.Draw(img)
    return img.resize(size, Image.LANCZOS)


def sheet(images, pad=8, bg=(255, 255, 255, 255)):
    w = sum(i.size[0] for i in images) + pad * (len(images) + 1)
    h = max(i.size[1] for i in images) + pad * 2
    out = Image.new('RGBA', (w, h), bg)
    x = pad
    for i in images:
        out.paste(i, (x, pad), i)
        x += i.size[0] + pad
    return out
