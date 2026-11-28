#!/usr/bin/env python3
"""원소신 다섯의 스킨 (64 × 64) · 망토 (64 × 32) — 기준 그림: 사용자가 AI 로 만든 다섯 장 (2026-11-28, claude/god-concept-art.md).

규칙: 홍조 없음 (사용자 — 헬 때 뺌), 얼굴은 성숙한 쪽, 라에타 북 문장은 미츠도모에 대신 벼 이삭 (소품 — god_props.py).
라에타 · 니바리아 · 마레아 = 가는 팔 (slim), 포르티아 (근육질 전사) · 그라비스 (남) = 넓은 팔.
망토 칸 (바닐라 망토 — 스킨 패치 cape): 니바리아 = 땅까지 닿는 검푸른 머리, 마레아 = 긴 청록 포니테일, 포르티아 = 붉은 망토, 그라비스 = 테라코타 겉옷.
"""
from god_npc_kit import Skin, rgba, mix, shade, alpha
from PIL import Image


def cape_canvas():
    return Image.new('RGBA', (64, 32), (0, 0, 0, 0))


def cape_paint(img, outer, inner=None, edge=None):
    """망토 칸 칠하기: outer(x, y) → 색 (x 0..9 = 뒤에서 볼 때 왼쪽 → 오른쪽, y 0..15 위 → 아래), inner 는 몸 쪽 면"""
    px = img.load()
    for y in range(16):
        for x in range(10):
            px[1 + x, 1 + y] = rgba(outer(x, y))
            c = inner(x, y) if inner else shade(outer(9 - x, y), 0.72)
            px[12 + x, 1 + y] = rgba(c)
        e = edge(y) if edge else shade(outer(0, y), 0.8)
        px[0, 1 + y] = rgba(e)
        px[11, 1 + y] = rgba(e)
    for x in range(10):
        px[1 + x, 0] = rgba(outer(x, 0))
        px[11 + x, 0] = rgba(shade(outer(x, 15), 0.8))
    return img


# ============================================================ 라에타 (번개 · 풍요, 일본풍)

def laeta():
    s = Skin(slim=True)
    P = {
        'H': '#ECC76A', 'L': '#F9E6A6', 'h': '#CC9C42', 'd': '#A6772C',
        'S': '#F8DECB', 's': '#ECC4AD', 'n': '#D9A68C',
        'E': '#43B54C', 'e': '#1D6A2B', 'w': '#E8FFDA', 'K': '#4A2E1C',
        'm': '#8A403B', 'T': '#FFFFFF',
        'W': '#F7F7F4', 'X': '#E4E5EA', 'x': '#C9CAD3',
        'B': '#23232A', 'b': '#3B3B47',
        'I': '#27398A', 'i': '#18255C', 'J': '#4060B4',
        'G': '#F2C14E', 'g': '#B9872B', 'Y': '#FFE69C',
        'V': '#3CA44B', 'v': '#277736', 'U': '#F4D24C', 'u': '#C9A32C',
        'Z': '#F8F8FA', 'z': '#D8D9E2', 'O': '#26262D', 'o': '#45454F',
    }
    # ---- 머리 (몸 겹)
    s.fill_part('head', P['H'])
    s.grid('head', 'front', [
        'HHLHHLHH',
        'HLHHHhLH',
        'HhShhShH',
        'hKKSSKKh',
        'SweSSewS',
        'SEESSEES',
        'SSmTTmSS',
        'sSSSSSSs',
    ], P)
    for f in ('right', 'left'):
        s.grid('head', f, [
            'HHHHHHHH', 'HHLHHHHH', 'HHHHHHhH', 'hHHHHhSH', 'HhHHHsSH', 'hHHHHSSh', 'HhHHhSSH', 'dhhhhSSs',
        ] if f == 'right' else [
            'HHHHHHHH', 'HHHHHLHH', 'HhHHHHHH', 'HShHHHHh', 'HSsHHHhH', 'hSSHHHHh', 'HSShHHhH', 'sSShhhhd',
        ], P)
    s.grid('head', 'back', ['HHHLLHHH', 'HHLHHLHH', 'HhHHHHhH', 'HHhHHhHH', 'hHHhhHHh', 'HhHHHHhH', 'hhHhhHhh', 'dhhddhhd'], P)
    s.grid('head', 'top', ['HHHHHHHH', 'HHLHHLHH', 'HLHHHHLH', 'HHHLLHHH', 'HHLHHLHH', 'HLHHHHLH', 'HHHHHHHH', 'HHHHHHHH'], P)
    s.grid('head', 'bottom', ['hhhhhhhh', 'hhhhhhhh', 'hhhhhhhh', 'nhnnnnhn', 'nnnnnnnn', 'snnnnnns', 'ssnnnnss', 'ssssssss'], P)
    # ---- 머리 (겉 겹): 앞머리 부피 · 옆머리 · 뒷머리
    s.grid('head', 'front', [
        'HLHHHHLH',
        'HHLHhLHH',
        'H.h..h.H',
        'H......H',
        'h......h',
        'H......H',
        '........',
        '........',
    ], P, layer=1)
    s.grid('head', 'right', ['HHHHHHHH', 'HLHHHHHH', 'HHHhHHHH', 'HHHHHH.h', 'HhHHH..H', 'HHHHh..h', 'hHhHH...', 'hhhhh...'], P, layer=1)
    s.grid('head', 'left', ['HHHHHHHH', 'HHHHHHLH', 'HHHHhHHH', 'h.HHHHHH', 'H..HHHhH', 'h..hHHHH', '...HHhHh', '...hhhhh'], P, layer=1)
    s.grid('head', 'back', ['HHLHHLHH', 'HLHHHHLH', 'HHHhhHHH', 'HhHHHHhH', 'HHhHHhHH', 'hHHHHHHh', 'HhhHHhhH', 'hhhhhhhh'], P, layer=1)
    s.grid('head', 'top', ['HHHLHHHH', 'HLHHHHLH', 'HHHHHHHH', 'HHLHHLHH', 'HHHHHHHH', 'HLHHHHLH', 'HHHHHHHH', 'HHHHHHHH'], P, layer=1)

    # ---- 몸: 흰 홀터넥 (높은 깃 · 어깨 드러남) · 금줄 허리띠 · 남색 주름치마 위
    s.fill_part('body', P['W'])
    s.grid('body', 'front', [
        'SSWWWWSS',
        'SXWWWWXS',
        'XWWWWWWX',
        'WWWWWWWW',
        'WWWWWWWW',
        'XWWWWWWX',
        'XXWWWWXX',
        'GYGgGYGg',
        'IiJIIiJI',
        'IiJIIiJI',
        'iIiIiIiI',
        'IiIIiIIi',
    ], P)
    s.grid('body', 'back', [
        'SSXWWXSS', 'SSXWWXSS', 'XWWWWWWX', 'WWWWWWWW', 'WWWWWWWW', 'XWWWWWWX', 'xXWWWWXx',
        'GgGYGgGY', 'IJiIIJiI', 'IJiIIJiI', 'iIiIiIiI', 'IiIIiIIi',
    ], P)
    for f in ('right', 'left'):
        s.grid('body', f, ['SSSS', 'SSSS', 'XWWX', 'WWWW', 'WWWW', 'XWWX', 'xXXx', 'GYGg', 'IiJI', 'IiJI', 'iIiI', 'IiIi'], P)
    s.grid('body', 'top', ['SSXWWXSS', 'SSWWWWSS', 'SSWWWWSS', 'SSXWWXSS'], P)
    s.grid('body', 'bottom', ['IiIIiIIi', 'IiIIiIIi', 'IiIIiIIi', 'IiIIiIIi'], P)
    # 겉 겹: 높은 깃 · 금줄 매듭 · 치마 주름 부풀림 · 포니테일 (등)
    s.grid('body', 'front', [
        '..WWWW..', '........', '........', '........', '........', '........', '........',
        'GYGYYGYG',
        'IiJYgiJI',
        'IiJgYiJI',
        'iIiIiIiI',
        'IiIIiIIi',
    ], P, layer=1)
    s.grid('body', 'back', [
        '...HH...', '..HLHH..', '..HHLH..', '..hHHh..', '..HHLH..', '..hHHh..', '...HLh..',
        'GYGYGYGY', 'IJiHHJiI', 'IJihHJiI', 'iIiHhIiI', 'IiIhHIIi',
    ], P, layer=1)
    for f in ('right', 'left'):
        s.grid('body', f, ['....', '....', '....', '....', '....', '....', '....', 'YGYG', 'IiJI', 'IiJI', 'iIiI', 'IiIi'], P, layer=1)

    # ---- 오른팔 (그 사람의 오른쪽): 어깨 드러냄 · 검은 긴 장갑 (북채를 듦)
    s.fill_part('rarm', P['S'])
    for f in ('front', 'back', 'left', 'right'):
        w = 3 if f in ('front', 'back') else 4
        for y in range(3, 12):
            for x in range(w):
                c = P['b'] if (f == 'front' and x == 0) or (f == 'right' and x == 2) else P['B']
                s.set('rarm', f, x, y, c)
        for x in range(w):
            s.set('rarm', f, x, 3, P['b'])
    s.fill('rarm', 'bottom', P['B'])
    s.fill('rarm', 'top', P['S'])
    # ---- 왼팔: 넓은 남색 소매 (금 테 · 소맷부리), 손은 맨손 (됫박을 듦)
    s.fill_part('larm', P['S'])
    for f in ('front', 'back', 'left', 'right'):
        w = 3 if f in ('front', 'back') else 4
        for y in range(2, 12):
            for x in range(w):
                c = P['I'] if (x + y) % 3 else P['J']
                if y == 2 or y == 10:
                    c = P['G']
                if y == 11:
                    c = P['i']
                s.set('larm', f, x, y, c, layer=1)
    s.fill('larm', 'bottom', P['S'])
    s.fill('larm', 'bottom', P['i'], layer=1)
    for f in ('front', 'back', 'left', 'right'):
        w = 3 if f in ('front', 'back') else 4
        for x in range(w):
            s.set('larm', f, x, 11, P['S'])

    # ---- 다리: 치마 단 (남색 → 초록 → 금빛 → 남색) · 허벅지 · 흰 니하이 · 검은 신
    for leg in ('rleg', 'lleg'):
        s.fill_part(leg, P['Z'])
        for f in ('front', 'back', 'left', 'right'):
            for y in range(12):
                for x in range(4):
                    if y <= 3:
                        c = P['i']
                    elif y == 4:
                        c = P['S']
                    elif y == 5:
                        c = P['z']
                    elif y == 11:
                        c = P['O'] if (x + y) % 2 else P['o']
                    else:
                        c = P['Z'] if x not in (0,) else P['z']
                    s.set(leg, f, x, y, c)
        s.fill(leg, 'bottom', P['O'])
        s.fill(leg, 'top', P['i'])
    s.legs_ring(0, lambda i, n: P['I'] if i % 3 else P['J'], layer=1)
    s.legs_ring(1, lambda i, n: P['V'] if i % 3 else P['v'], layer=1)
    s.legs_ring(2, lambda i, n: P['U'] if i % 3 else P['u'], layer=1)
    s.legs_ring(3, lambda i, n: P['I'] if i % 2 else P['i'], layer=1)
    return s, None


# ============================================================ 니바리아 (얼음 여왕, 러시아풍)

def nivaria():
    s = Skin(slim=True)
    P = {
        'H': '#1D2442', 'h': '#121729', 'L': '#3B4C82', 'l': '#2B3663',
        'S': '#F8E6DE', 's': '#EACFC5', 'n': '#D9B4A8',
        'E': '#A8CCEE', 'e': '#5E80AC', 'K': '#1B1F33', 'm': '#C9878B',
        'J': '#BFEFFF', 'j': '#5FB4DE',
        'F': '#FAF8F3', 'f': '#E9E3D9', 'q': '#D6CEC1',
        'W': '#F5F8FC', 'X': '#D2DBE8', 'x': '#B5C2D4',
        'R': '#1E8189', 'r': '#12545A', 'Q': '#4CB2B6',
        'C': '#C4CEDC', 'c': '#8B97AB', 'G': '#D9B75C', 'g': '#A0823A',
    }
    s.fill_part('head', P['H'])
    s.grid('head', 'front', [
        'HHHlLHHH',
        'HHHSSHHH',
        'HHSSSSHH',
        'HKKSSKKH',
        'HEeSSeEH',
        'HSSSSSSH',
        'HSSmmSSH',
        'HsSSSSsH',
    ], P)
    s.grid('head', 'right', ['HHHHHHHH', 'HHLHHHHH', 'HHHHHHHH', 'HhHHHHSH', 'HHHHhSSH', 'HhHHHsSH', 'HHHhHSSH', 'hHhHHsSH'], P)
    s.grid('head', 'left', ['HHHHHHHH', 'HHHHHLHH', 'HHHHHHHH', 'HSHHHHhH', 'HSShHHHH', 'HSsHHHhH', 'HSSHhHHH', 'HSsHHhHh'], P)
    s.grid('head', 'back', ['HHHlLHHH', 'HHlHHLHH', 'HlHHHHLH', 'HHHHHHHH', 'HLHHHHlH', 'HHHHHHHH', 'HHlHHLHH', 'HHHHHHHH'], P)
    s.grid('head', 'top', ['HHHlLHHH', 'HHHlLHHH', 'HHlHHLHH', 'HHlHHLHH', 'HlHHHHLH', 'HlHHHHLH', 'HHHHHHHH', 'HHHlLHHH'], P)
    s.grid('head', 'bottom', ['HHHHHHHH', 'HHHHHHHH', 'HHHHHHHH', 'HnnnnnnH', 'HnnnnnnH', 'HsnnnnsH', 'HssnnssH', 'HssssssH'], P)
    # 겉 겹: 곧은 긴 머리 (얼굴 양옆으로 떨어짐)
    s.grid('head', 'front', ['HHHl.HHH', 'HHH..HHH', 'HH....HH', 'H......H', 'H......H', 'H......H', 'H......H', 'H......H'], P, layer=1)
    s.grid('head', 'right', ['HHHHHHHH', 'HLHHHHHH', 'HHHHHlHH', 'HHHHHH.H', 'HHHHH..H', 'HHHHH..H', 'HHHHH..H', 'HHHHH..H'], P, layer=1)
    s.grid('head', 'left', ['HHHHHHHH', 'HHHHHHLH', 'HHlHHHHH', 'H.HHHHHH', 'H..HHHHH', 'H..HHHHH', 'H..HHHHH', 'H..HHHHH'], P, layer=1)
    s.grid('head', 'back', ['HHHlLHHH', 'HHlHHLHH', 'HHHHHHHH', 'HLHHHHlH', 'HHHHHHHH', 'HHlHHLHH', 'HHHHHHHH', 'HHHHHHHH'], P, layer=1)
    s.grid('head', 'top', ['HHHlLHHH', 'HHHlLHHH', 'HHlHHLHH', 'HHlHHLHH', 'HlHHHHLH', 'HHHHHHHH', 'HHHHHHHH', 'HHHlLHHH'], P, layer=1)

    # 몸: 흰 드레스 · 청록 장미 · 은 사슬 허리띠 (열쇠) · 흰 담비 털 목도리 (겉 겹)
    s.fill_part('body', P['W'])
    s.grid('body', 'front', [
        'WWWWWWWW',
        'WWWWWWWW',
        'XRQWWRQX',
        'WrRWWrRW',
        'WWWWWWWW',
        'XWWRQWWX',
        'WWWrRWWW',
        'XWWWWWWX',
        'CcCcCcCc',
        'WGWWWWGW',
        'WgWRQWgW',
        'XWWrRWWX',
    ], P)
    s.grid('body', 'back', ['WWWWWWWW'] * 8 + ['cCcCcCcC', 'WWWWWWWW', 'XWWWWWWX', 'XXWWWWXX'], P)
    for f in ('right', 'left'):
        s.grid('body', f, ['WWWW', 'WWWW', 'XWWX', 'WRQW', 'WrRW', 'XWWX', 'WWWW', 'XWWX', 'CcCc', 'WWWW', 'XWWX', 'XXXX'], P)
    s.grid('body', 'front', [
        'FfFFFFfF',
        'fFFjJFFf',
        '.fF..Ff.',
    ], P, layer=1)
    s.grid('body', 'back', ['FfFFFFfF', 'fFFFFFFf', '.ffFFff.'], P, layer=1)
    for f in ('right', 'left'):
        s.grid('body', f, ['FFfF', 'fFFf', 'fFFf'], P, layer=1)
    s.grid('body', 'top', ['FFFFFFFF', 'FfFFFFfF', 'FfFFFFfF', 'FFFFFFFF'], P, layer=1)
    # 팔: 흰 긴 소매 (은 소맷부리) · 어깨에 털
    for arm in ('rarm', 'larm'):
        s.fill_part(arm, P['W'])
        for f in ('front', 'back', 'left', 'right'):
            w = 3 if f in ('front', 'back') else 4
            for x in range(w):
                s.set(arm, f, x, 9, P['C'])
                s.set(arm, f, x, 10, P['X'])
                s.set(arm, f, x, 11, P['S'])
                s.set(arm, f, x, 0, P['F'], layer=1)
                s.set(arm, f, x, 1, P['f'] if x % 2 else P['F'], layer=1)
                s.set(arm, f, x, 2, P['f'] if x % 2 == 0 else None, layer=1)
            if f == 'front':
                s.set(arm, f, 1, 5, P['R'])
                s.set(arm, f, 1, 6, P['r'])
        s.fill(arm, 'bottom', P['S'])
        s.fill(arm, 'top', P['F'], layer=1)
    # 다리: 짧은 앞치마 (장미) · 흰 허벅지 장화
    for leg in ('rleg', 'lleg'):
        s.fill_part(leg, P['W'])
        for f in ('front', 'back', 'left', 'right'):
            for x in range(4):
                s.set(leg, f, x, 3, P['S'] if f == 'front' else P['W'])
                s.set(leg, f, x, 4, P['X'])
                for y in range(5, 12):
                    s.set(leg, f, x, y, P['X'] if x == 0 or y == 11 else P['W'])
        s.fill(leg, 'bottom', P['x'])
    s.legs_ring(0, lambda i, n: P['W'] if i % 4 else P['X'], layer=1)
    s.legs_ring(1, lambda i, n: (P['R'] if i % 5 == 1 else P['Q'] if i % 5 == 2 else P['W']), layer=1)
    s.legs_ring(2, lambda i, n: P['X'] if i % 2 else P['W'], layer=1)

    # 망토 칸 = 땅까지 닿는 긴 머리 (검푸른 → 끝은 얼음빛)
    cape = cape_canvas()

    def hair(x, y):
        base = P['H'] if (x + (y // 3)) % 4 else P['l']
        if x in (2, 7) and y < 13:
            base = P['L'] if y % 5 < 3 else P['l']
        if y >= 13:
            base = mix(base, '#9FD2EE', (y - 12) / 4.5)
        return base

    cape_paint(cape, hair, lambda x, y: shade(hair(9 - x, y), 0.65))
    return s, cape


# ============================================================ 마레아 (물 · 생명, 중국풍 용왕궁)

def marea():
    s = Skin(slim=True)
    P = {
        'H': '#1F7B6F', 'h': '#155A52', 'L': '#41AA97', 'l': '#2C8F80', 'b': '#0F4842',
        'S': '#F9E4D8', 's': '#ECC9B9', 'n': '#DAAF9D',
        'E': '#38B8A4', 'e': '#1A6C61', 'w': '#E6FFF9', 'K': '#2E2526', 'm': '#C46F69', 'M': '#F2907E',
        'W': '#F8F6F2', 'X': '#DAD6D0', 'x': '#C2BDB6',
        'C': '#F6A98E', 'c': '#E07D66',
        'G': '#DDB95D', 'g': '#A8873A',
        'V': '#2F9F79', 'v': '#1F7458', 'D': '#86E6CF', 'd': '#3EAE94',
        'T': '#A5EBD5', 't': '#71D2B7', 'u': '#4BB497',
        'A': '#A6EEF2B4', 'a': '#D8FBFCC8',
    }
    s.fill_part('head', P['H'])
    s.grid('head', 'front', [
        'HHHhhHHH',
        'HHHhhHHH',
        'HhbSSbhH',
        'hKKSSKKh',
        'SweSSewS',
        'SEESSEES',
        'SSSmmSSS',
        'sSSSSSSs',
    ], P)
    s.grid('head', 'right', ['HHHHHHHH', 'HHLHHHHH', 'HHHHHHhH', 'HhHHHhSH', 'HHHHhsSH', 'HhHHHSSh', 'HHHhHSSH', 'hhhhhSSs'], P)
    s.grid('head', 'left', ['HHHHHHHH', 'HHHHHLHH', 'HhHHHHHH', 'HShHHHhH', 'HSshHHHH', 'hSSHHHhH', 'HSSHhHHH', 'sSShhhhh'], P)
    s.grid('head', 'back', ['HHHLLHHH', 'HHLHHLHH', 'HLHHHHLH', 'HHHhhHHH', 'HhHHHHhH', 'HHhHHhHH', 'hHHHHHHh', 'hhhHHhhh'], P)
    s.grid('head', 'top', ['HHHLLHHH', 'HHLHHLHH', 'HLHHHHLH', 'HHHlLHHH', 'HHHlLHHH', 'HHlHHLHH', 'HHHHHHHH', 'HHHlLHHH'], P)
    s.grid('head', 'bottom', ['hhhhhhhh', 'hhhhhhhh', 'hhhhhhhh', 'hnnnnnnh', 'nnnnnnnn', 'snnnnnns', 'ssnnnnss', 'ssssssss'], P)
    s.grid('head', 'front', ['HHLh.LHH', 'HLHh.HLH', 'Hh....hH', 'H......H', 'H......H', 'h......h', 'H......H', '........'], P, layer=1)
    s.grid('head', 'right', ['HHHHHHHH', 'HLHHHHHH', 'HHHlHHHH', 'HHHHHH.H', 'HhHHH..H', 'HHHHh..h', 'HhHHH..H', 'hhhh....'], P, layer=1)
    s.grid('head', 'left', ['HHHHHHHH', 'HHHHHHLH', 'HHHHlHHH', 'H.HHHHHH', 'H..HHHhH', 'h..hHHHH', 'H..HHHhH', '....hhhh'], P, layer=1)
    s.grid('head', 'back', ['HHHLLHHH', 'HHLHHLHH', 'HHHHHHHH', 'HhHHHHhH', 'HHHHHHHH', 'hHHhhHHh', 'HhhHHhhH', 'hhhhhhhh'], P, layer=1)
    s.grid('head', 'top', ['HHHLLHHH', 'HHLHHLHH', 'HLHHHHLH', 'HHHlLHHH', 'HHHlLHHH', 'HHlHHLHH', 'HHHHHHHH', 'HHHlLHHH'], P, layer=1)

    # 몸: 흰 깃 저고리 (엇갈린 깃) · 산호 구름 깃 (겉) · 초록 허리띠 · 옥 패물 · 민트 겹치마
    s.fill_part('body', P['W'])
    s.grid('body', 'front', [
        'WXWWWWXW',
        'WWXWWXWW',
        'XWWXXWWX',
        'WWWWXWWW',
        'WWWXWWWW',
        'XWWWWWWX',
        'VVvVVvVV',
        'VvVDDVvV',
        'TtTdDTtT',
        'tTTtTTtT',
        'TuTTuTTu',
        'uTtuTtuT',
    ], P)
    s.grid('body', 'back', ['WWWWWWWW', 'WXWWWWXW', 'WWWWWWWW', 'WWWXXWWW', 'WWWWWWWW', 'XWWWWWWX',
                            'VvVVVVvV', 'VVVVVVVV', 'TtTTtTTt', 'tTTtTTtT', 'TuTTuTTu', 'uTtuTtuT'], P)
    for f in ('right', 'left'):
        s.grid('body', f, ['WWWW', 'WWWW', 'XWWX', 'WWWW', 'WWWW', 'XWWX', 'VvVV', 'VVvV', 'TtTt', 'tTTt', 'TuTu', 'uTtu'], P)
    s.grid('body', 'front', ['CCCWWCCC', 'CCC..CCC', '.cC..Cc.', '........', '........', '........',
                             '........', '...DD...', '...dD...', '...gG...', '........', 'u.t..t.u'], P, layer=1)
    s.grid('body', 'back', ['CCCCCCCC', 'CCCCCCCC', '.cCccCc.', '........', '........', '........',
                            '........', '........', '........', '........', '........', 't.u..u.t'], P, layer=1)
    for f in ('right', 'left'):
        s.grid('body', f, ['CCCC', 'CCCC', '.cc.'], P, layer=1)
    s.grid('body', 'top', ['CCCCCCCC', 'CcCCCCcC', 'CcCCCCcC', 'CCCCCCCC'], P, layer=1)
    # 팔: 짧은 흰 소매 → 맨팔, 겉 겹 = 반투명 물소매
    for arm in ('rarm', 'larm'):
        s.fill_part(arm, P['S'])
        for f in ('front', 'back', 'left', 'right'):
            w = 3 if f in ('front', 'back') else 4
            for x in range(w):
                for y in range(0, 4):
                    s.set(arm, f, x, y, P['W'] if y < 3 else P['X'])
                for y in range(2, 11):
                    c = P['a'] if (y + x) % 4 == 0 or y == 10 else P['A']
                    s.set(arm, f, x, y, c, layer=1)
        s.fill(arm, 'top', P['W'])
        s.fill(arm, 'bottom', P['S'])
    # 다리: 겹치마 (겉) · 허벅지 · 흰 무릎 장화
    for leg in ('rleg', 'lleg'):
        s.fill_part(leg, P['W'])
        for f in ('front', 'back', 'left', 'right'):
            for x in range(4):
                for y in range(12):
                    if y <= 2:
                        c = P['t']
                    elif y <= 5:
                        c = P['S'] if f != 'back' else P['s']
                    elif y == 6:
                        c = P['X']
                    else:
                        c = P['X'] if x == 0 or y == 11 else P['W']
                    s.set(leg, f, x, y, c)
        s.fill(leg, 'bottom', P['x'])
    s.legs_ring(0, lambda i, n: P['T'] if i % 2 else P['t'], layer=1)
    s.legs_ring(1, lambda i, n: P['t'] if i % 3 else P['u'], layer=1)
    s.legs_ring(2, lambda i, n: (P['u'] if i % 2 else None), layer=1)

    cape = cape_canvas()

    def tail(x, y):
        # 가운데로 모이는 긴 포니테일 (가장자리는 물빛으로 풀림)
        edge = abs(x - 4.5)
        base = P['H'] if (x + y // 2) % 3 else P['l']
        if x in (3, 6) and y < 12:
            base = P['L']
        if edge > 3.5 - y * 0.08:
            base = mix(base, '#9BEBE2', 0.55)
        if y >= 12:
            base = mix(base, '#B6FFF0', (y - 11) / 5)
        return base

    cape_paint(cape, tail, lambda x, y: shade(tail(9 - x, y), 0.7))
    return s, cape


# ============================================================ 포르티아 (불 · 전쟁, 원주민풍 전사)

def fortia():
    s = Skin(slim=False)
    P = {
        'S': '#8C5634', 's': '#6E3F24', 'n': '#55301A', 'o': '#A9704A',
        'H': '#1A1615', 'h': '#2F2826', 'l': '#4C413D', 'y': '#F08A24',
        'E': '#F2A532', 'e': '#B4621A', 'w': '#FFF1C4', 'K': '#120C0A',
        'm': '#4E2016', 'T': '#FFFFFF',
        'P': '#A8171B', 'p': '#7E1013',
        'Q': '#30B8A9', 'q': '#1E7F75', 'O': '#F3ECDA',
        'B': '#1B1515', 'b': '#302727',
        'R': '#C8261C', 'r': '#8E1A14', 'Y': '#F08A24', 'u': '#C8601A',
        'C': '#C47442', 'c': '#8E4E2A', 'k': '#E39C64',
        'D': '#6B4226', 'd': '#4A2C18',
        'F': '#F3EFE7', 'f': '#CFC7BA',
        'X': '#140F10', 'x': '#2C2325',
    }
    s.fill_part('head', P['H'])
    s.grid('head', 'front', [
        'HhHlHhlH',
        'HlHHHHhH',
        'HSSSSSSH',
        'HKKSSKKH',
        'SeESSEeS',
        'SPSSSSPS',
        'PSmTTmSP',
        'SSSSSSSS',
    ], P)
    braid = lambda x, y: P['l'] if (x + y) % 3 == 0 else P['h'] if (x + y) % 3 == 1 else P['H']
    for f in ('right', 'left', 'back', 'top'):
        for y in range(8):
            for x in range(8):
                s.set('head', f, x, y, braid(x, y))
    s.grid('head', 'right', ['........', '........', '........', '......SS', '.....sSS', '.....SSS', '......SS', '......sS'], P)
    s.grid('head', 'left', ['........', '........', '........', 'SS......', 'SSs.....', 'SSS.....', 'SS......', 'Ss......'], P)
    s.grid('head', 'bottom', ['HHHHHHHH', 'HHHHHHHH', 'HHHHHHHH', 'HnnnnnnH', 'nnnnnnnn', 'nnnnnnnn', 'snnnnnns', 'ssssssss'], P)
    # 겉 겹: 굵은 땋은 머리 다발 (끝에 불씨)
    for f in ('right', 'left', 'back'):
        for y in range(8):
            for x in range(8):
                if f == 'right' and x >= 5 and y >= 3:
                    continue
                if f == 'left' and x <= 2 and y >= 3:
                    continue
                if (x % 2 == 0) or f == 'back':
                    s.set('head', f, x, y, braid(x + 1, y), layer=1)
    s.grid('head', 'front', ['HlHhHlHh', 'h......l', 'H......H', 'l......h', 'H......H', 'h......H', 'y......y', '........'], P, layer=1)
    for y in range(8):
        for x in range(8):
            s.set('head', 'top', x, y, braid(x + 2, y), layer=1)

    # 몸: 목걸이 (청록 · 뼈) · 검은 가슴띠 (계단 무늬) · 복근 · 가죽 허리띠 (구리 해 장식) · 계단 무늬 치마
    s.fill_part('body', P['S'])
    s.grid('body', 'front', [
        'SSSSSSSS',
        'SQqQQqQS',
        'SOQOOQOS',
        'BYQBBQYB',
        'BRbYYbRB',
        'BBBBBBBB',
        'SosSSsoS',
        'SsSosSsS',
        'SoSsSoSs',
        'DdDCkDdD',
        'YQBYYBQY',
        'BYuBBuYB',
    ], P)
    s.grid('body', 'back', ['SSSSSSSS', 'SSQqqQSS', 'SSSSSSSS', 'BYQBBQYB', 'BRbYYbRB', 'BBBBBBBB',
                            'SsSSSSsS', 'SSsSSsSS', 'SsSSSSsS', 'DdDDDDdD', 'YQBYYBQY', 'BYuBBuYB'], P)
    for f in ('right', 'left'):
        s.grid('body', f, ['SSSS', 'SQqS', 'SSSS', 'BYQB', 'BRbB', 'BBBB', 'SsSS', 'SSsS', 'SsSS', 'DdDd', 'QYBY', 'YuBu'], P)
    s.grid('body', 'top', ['SSSSSSSS', 'SSSSSSSS', 'SSSSSSSS', 'SSSSSSSS'], P)
    # 겉 겹: 앞으로 넘긴 땋은 머리 둘 · 등의 긴 땋은 머리 · 허리띠 해 장식
    s.grid('body', 'front', ['l......h', 'H......l', 'h......H', 'l......h', 'H......H', 'y......y',
                             '........', '........', '........', '...kC...', '...BB...', '...YY...'], P, layer=1)
    for y in range(12):
        for x in range(8):
            if y < 9 or x in (2, 3, 4, 5):
                s.set('body', 'back', x, y, braid(x, y) if y < 10 else (P['y'] if y == 11 and x % 2 else braid(x, y)), layer=1)
    # 오른팔: 맨팔 · 구리 팔찌 · 가죽 팔목 보호대
    for arm in ('rarm', 'larm'):
        s.fill_part(arm, P['S'])
        for f in ('front', 'back', 'left', 'right'):
            for x in range(4):
                s.set(arm, f, x, 4, P['C'])
                s.set(arm, f, x, 5, P['c'])
                for y in (8, 9, 10):
                    s.set(arm, f, x, y, P['D'] if (x + y) % 3 else P['C'])
                if f == 'front' and x in (1, 2):
                    s.set(arm, f, x, 2, P['o'])
    # 왼쪽 어깨: 흑요석 견갑 (빛나는 금) — 겉 겹
    for f in ('front', 'back', 'left', 'right', 'top'):
        w = 4
        h = 4 if f != 'top' else 4
        for y in range(h):
            for x in range(w):
                c = P['X'] if (x + y) % 3 else P['x']
                if (f in ('left', 'front') and (x, y) in ((1, 1), (2, 2), (1, 3))) or (f == 'top' and (x, y) in ((1, 1), (2, 2))):
                    c = P['Y']
                s.set('larm', f, x, y, c, layer=1)
    # 다리: 계단 무늬 치마 · 검은 앞가리개 (주황 갈매기) · 허벅지 · 털 테 장화
    for leg in ('rleg', 'lleg'):
        s.fill_part(leg, P['S'])
        for f in ('front', 'back', 'left', 'right'):
            for x in range(4):
                s.set(leg, f, x, 0, P['B'])
                s.set(leg, f, x, 1, P['Y'] if x % 2 else P['Q'])
                s.set(leg, f, x, 7, P['F'] if x % 2 else P['f'])
                for y in range(8, 12):
                    s.set(leg, f, x, y, P['D'] if (x + y) % 4 else P['C'])
                s.set(leg, f, x, 11, P['d'])
        s.fill(leg, 'bottom', P['d'])
    s.legs_ring(0, lambda i, n: P['B'] if i % 2 else P['b'], layer=1)
    s.legs_ring(1, lambda i, n: [P['Y'], P['Q'], P['B'], P['R']][i % 4], layer=1)
    s.legs_ring(2, lambda i, n: [P['u'], None, P['B'], None][i % 4], layer=1)
    s.legs_ring(7, lambda i, n: P['F'] if i % 2 else P['f'], layer=1)
    for y in range(0, 8):
        c = P['Y'] if y % 3 == 1 else P['B']
        s.legs_front(3, y, c, layer=1)
        s.legs_front(4, y, c, layer=1)
        s.legs_back(3, y, P['B'], layer=1)
        s.legs_back(4, y, P['B'], layer=1)

    cape = cape_canvas()

    def red(x, y):
        c = P['R'] if (x + y // 4) % 5 else P['r']
        if y == 0:
            c = P['r']
        if y >= 14 and (x + y) % 3 == 0:
            c = P['Y']
        if y == 15 and x % 2:
            c = P['r']
        return c

    cape_paint(cape, red, lambda x, y: shade(red(9 - x, y), 0.6))
    return s, cape


# ============================================================ 그라비스 (땅 · 규율, 페르시아풍, 남)

def gravis():
    s = Skin(slim=False)
    P = {
        'S': '#C9966D', 's': '#AA7850', 'n': '#8B5F3D',
        'H': '#2A1D16', 'h': '#1A120E', 'l': '#4D3627',
        'K': '#140D0A', 'E': '#E3A532', 'e': '#8C5A18', 'w': '#F6ECD8', 'm': '#5E3324',
        'G': '#E2B242', 'g': '#A87A20', 'Y': '#FFE08C',
        'L': '#2C4C9C', 'k': '#1E3572', 'j': '#4A6CC4',
        'T': '#CA7A5C', 't': '#9F5A42', 'u': '#E39C7C',
        'Q': '#2CB4A7', 'P': '#F2EAD8', 'p': '#D6CBB2', 'R': '#B8323A',
        'B': '#5A3A22', 'b': '#3E2716',
    }
    curl = lambda x, y: P['l'] if (x * 3 + y * 2) % 5 == 0 else P['h'] if (x + y * 3) % 4 == 0 else P['H']
    s.fill_part('head', P['H'])
    s.grid('head', 'front', [
        'HlHHHHlH',
        'HSSSSSSH',
        'HKKssKKH',
        'HwESSEwH',
        'SSSnnSSS',
        'SHHHHHHS',
        'HHHmmHHH',
        'hHHHHHHh',
    ], P)
    for f in ('right', 'left', 'back', 'top'):
        for y in range(8):
            for x in range(8):
                s.set('head', f, x, y, curl(x, y))
    s.grid('head', 'right', ['........', '........', '.......S', '......SS', '.....sSS', '......HS', '.....HHH', '....hHHH'], P)
    s.grid('head', 'left', ['........', '........', 'S.......', 'SS......', 'SSs.....', 'SH......', 'HHH.....', 'HHHh....'], P)
    s.grid('head', 'bottom', ['HHHHHHHH', 'HHHHHHHH', 'HHHHHHHH', 'HHHHHHHH', 'hHHHHHHh', 'hhHHHHhh', 'hhhhhhhh', 'hhhhhhhh'], P)
    # 겉 겹: 양옆으로 부푼 곱슬머리 · 수염 부피
    for f in ('right', 'left', 'back'):
        for y in range(8):
            for x in range(8):
                if f == 'right' and x >= 6 and 2 <= y <= 5:
                    continue
                if f == 'left' and x <= 1 and 2 <= y <= 5:
                    continue
                if (x + y) % 3 != 1 or f == 'back':
                    s.set('head', f, x, y, curl(x + 1, y + 1), layer=1)
    s.grid('head', 'front', ['l......l', 'H......H', 'h......h', 'H......H', '........', '........', 'h......h', '.hHHHHh.'], P, layer=1)

    # 몸: 금 넓은 깃 · 청금석 로브 (금 마름모 무늬 · 가운데 금 단) · 금 허리띠 · 두루마리 (겉)
    s.fill_part('body', P['L'])
    s.grid('body', 'front', [
        'GYGQQGYG',
        'gGYGGYGg',
        'LjLGGLjL',
        'LLGLLGLL',
        'LGLkGLGL',
        'LLLGLLLL',
        'LjLLGLjL',
        'LLGLLGLL',
        'GgGYYGgG',
        'TtTGGTtT',
        'LLLGLLLL',
        'LjLGLLjL',
    ], P)
    s.grid('body', 'back', ['GYGGGGYG', 'gGGGGGGg', 'LLLLLLLL', 'LjLLLLjL', 'LLLLLLLL', 'LLLjjLLL', 'LLLLLLLL',
                            'LjLLLLjL', 'GgGYYGgG', 'TtTTTTtT', 'LLLLLLLL', 'LLLLLLLL'], P)
    for f in ('right', 'left'):
        s.grid('body', f, ['GYGg', 'gGGg', 'LLLL', 'LjLL', 'LLLL', 'LLjL', 'LLLL', 'LjLL', 'GgGY', 'TtTt', 'LLLL', 'LLLL'], P)
    s.grid('body', 'top', ['GYGGGGYG', 'GGSSSSGG', 'GGSSSSGG', 'GYGGGGYG'], P)
    # 겉 겹: 테라코타 겉옷 (앞섶 가장자리 · 옆 · 뒤), 오른쪽 허리의 두루마리
    s.grid('body', 'front', ['........', '........', 'T......T', 'u......t', 'T......T', 't......T', 'T......T',
                             'u......t', 'T......T', 't......T', 'TPp....T', 'tpR....t'], P, layer=1)
    for f in ('right', 'left'):
        s.grid('body', f, ['....', '....', 'TuTt', 'TtTT', 'tTTu', 'TTtT', 'TuTT', 'tTTt', 'TTuT', 'TtTT', 'tTTu', 'TTtT'], P, layer=1)
    for y in range(2, 12):
        for x in range(8):
            s.set('body', 'back', x, y, P['T'] if (x + y) % 5 else P['t'], layer=1)
    # 팔: 청금석 소매 · 금 소맷부리 · 어깨에 겉옷
    for arm in ('rarm', 'larm'):
        s.fill_part(arm, P['L'])
        for f in ('front', 'back', 'left', 'right'):
            for x in range(4):
                s.set(arm, f, x, 0, P['G'])
                s.set(arm, f, x, 1, P['g'] if x % 2 else P['Y'])
                s.set(arm, f, x, 8, P['G'])
                s.set(arm, f, x, 9, P['Y'] if x % 2 else P['G'])
                s.set(arm, f, x, 10, P['S'])
                s.set(arm, f, x, 11, P['S'] if f != 'back' else P['s'])
                if 2 <= x <= 3 and f == 'front':
                    s.set(arm, f, x, 5, P['j'])
        s.fill(arm, 'top', P['G'])
        s.fill(arm, 'bottom', P['S'])
        for f in ('back', 'left' if arm == 'larm' else 'right', 'top'):
            for y in range(2 if f != 'top' else 0, 7 if f != 'top' else 4):
                for x in range(4):
                    s.set(arm, f, x, y, P['T'] if (x + y) % 4 else P['t'], layer=1)
    # 다리: 긴 로브 (금 단) · 갈색 신
    for leg in ('rleg', 'lleg'):
        s.fill_part(leg, P['L'])
        for f in ('front', 'back', 'left', 'right'):
            for x in range(4):
                s.set(leg, f, x, 8, P['G'])
                s.set(leg, f, x, 9, P['Y'] if x % 2 else P['g'])
                s.set(leg, f, x, 10, P['B'])
                s.set(leg, f, x, 11, P['b'])
                if f == 'front' and x == 1:
                    s.set(leg, f, x, 3, P['j'])
        s.fill(leg, 'bottom', P['b'])
    for y in range(0, 8):
        s.legs_front(3, y, P['G'] if y % 2 else P['Y'])
        s.legs_front(4, y, P['g'] if y % 2 else P['G'])
    # 겉옷 자락 (다리 바깥 · 뒤)
    for y in range(0, 9):
        for x in range(4):
            s.set('rleg', 'right', x, y, P['T'] if (x + y) % 4 else P['t'], layer=1)
            s.set('lleg', 'left', x, y, P['T'] if (x + y) % 4 else P['t'], layer=1)
        for X in range(8):
            s.legs_back(X, y, P['T'] if (X + y) % 5 else P['t'], layer=1)
    for X in (0, 7):
        for y in range(0, 9):
            s.legs_front(X, y, P['T'] if y % 3 else P['t'], layer=1)

    cape = cape_canvas()

    def mantle(x, y):
        c = P['T'] if (x + y) % 5 else P['t']
        if x in (0, 9):
            c = P['G']
        if y == 15:
            c = P['G'] if x % 2 else P['g']
        if y == 14 and 1 <= x <= 8:
            c = P['u'] if x % 2 else P['T']
        return c

    cape_paint(cape, mantle, lambda x, y: shade(mantle(9 - x, y), 0.7))
    return s, cape


GODS = {
    'laeta': (laeta, True),
    'nivaria': (nivaria, True),
    'marea': (marea, True),
    'fortia': (fortia, False),
    'gravis': (gravis, False),
}
