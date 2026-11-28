#!/usr/bin/env python3
"""원소신 NPC 다섯 (2026-11-28 — 사용자 "그냥 이런 느낌에 npc 넣기만 할래" → 고른 방식 ① 사람 모양 NPC + 스킨):
스킨 · 망토 (god_skins.py) · 소품 아이템 모델 (god_props.py) · 서버가 읽는 배치 (mmorpg/gods.json) 를 만듦.

  python3 tools/god_npcs.py            # 파일 쓰기
  python3 tools/god_npcs.py --preview  # + 미리보기 (build/god_preview/*.png — 앞 · 3/4 · 옆 · 뒤 · 얼굴)

만드는 것 (src/main/resources):
  assets/rpg/textures/entity/god/<신>.png        스킨 64 × 64 (마네킹 프로필 패치 texture — 리소스 팩으로 받음, 모장 서버 안 거침)
  assets/rpg/textures/entity/god/<신>_cape.png   망토 64 × 32 (패치 cape)
  assets/rpg/textures/item/god/<신>.png          소품 그림판
  assets/rpg/models/item/god/<소품>.json · assets/rpg/items/god/<소품>.json   (아이템 모델 rpg:god/<소품> — 종이에 item_model)
  mmorpg/gods.json                                이름 · 이명 · 인사말 · 스킨 · 장비 · 둘레 소품 (ops) · 파티클
둘레 소품 ops (j 공간 px, 발 = 원점, x+ 오른쪽 · y+ 위 · z+ 뒤 — 왼쪽부터 차례로 곱함):
  ["t", x, y, z] 옮김 · ["r", 축, 도] 돌림 · ["spin", 축, 도/틱, 처음 도] 계속 돎 · ["bob", 폭 px, 라디안/틱, 처음] 오르내림 · ["s", 배]
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import god_skins  # noqa: E402
import god_props  # noqa: E402
from god_npc_kit import (player_quads, model_quads, head_xf, hand_xf, j_xf, render, sheet, m_chain, m_t, m_r, m_s,  # noqa: E402
                         m_ident, mat_mul)

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
RES = os.path.join(ROOT, 'src', 'main', 'resources')
ASSETS = os.path.join(RES, 'assets', 'rpg')

GODS = {
    'laeta': {
        'name': '라에타', 'title': '번개 · 풍요의 신',
        'greeting': '왔구나! 마침 잘 왔어. 올해도 넘치게 여물었거든.|자, 이것도 받고, 저것도 받아.||…칭찬은 천천히 해도 돼. 아주 천천히.',
        'head': 'laeta_head', 'main': 'laeta_bachi', 'off': 'laeta_masu',
        'props': [{'model': 'laeta_ring', 'ops': [['t', 0, 21, 6.5], ['spin', 'z', 0.8, 0]]}] + [
            {'model': 'laeta_drum', 'ops': [['t', 0, 21, 6.5], ['spin', 'z', 0.8, 0], ['r', 'z', 45 * k], ['t', 0, 12, 0]]} for k in range(8)],
        'particles': [
            {'kind': 'dust', 'color': '#FFE14D', 'size': 0.8, 'every': 3, 'count': 1, 'at': [0, 21, 6.5], 'spread': [11, 11, 1]},
            {'kind': 'spark', 'every': 9, 'count': 2, 'at': [6, 25, -4], 'spread': [1.5, 3, 1.5]},
            {'kind': 'dust', 'color': '#FFF6D2', 'size': 0.6, 'every': 5, 'count': 1, 'at': [-6, 13, -5], 'spread': [1.5, 0.5, 1.5]},
        ],
    },
    'nivaria': {
        'name': '니바리아', 'title': '얼음 여왕',
        'greeting': '어서 와요. 발밑 조심해요. 그건 300년 된 재채기예요.||그 질문은 얼려서 보관해 둘게요.|나중에, 아주 나중에 꺼내 줄게요.',
        'head': 'nivaria_head', 'main': None, 'off': 'nivaria_staff',
        'props': [{'model': 'nivaria_keep_' + key, 'ops': [['t', 0, 15, 0], ['spin', 'y', 0.55, 90 * k], ['t', 13, 0, 0], ['bob', 0.9, 0.07, 1.3 * k],
                                                          ['spin', 'y', -1.1, 0]]} for k, key in enumerate(('candle', 'boat', 'soup', 'sneeze'))] + [
            {'model': 'nivaria_egg', 'ops': [['t', -10, 29, -1], ['bob', 0.8, 0.05, 0], ['spin', 'y', 1.2, 0]]},
            {'model': 'nivaria_book', 'ops': [['t', 8, 17, -6], ['bob', 0.6, 0.06, 1.0], ['r', 'y', -20], ['r', 'x', -35]]}],
        'particles': [
            {'kind': 'snow', 'every': 4, 'count': 1, 'at': [0, 26, 0], 'spread': [14, 10, 14]},
            {'kind': 'end_rod', 'every': 14, 'count': 1, 'at': [-6, 32, -4], 'spread': [1.5, 2, 1.5]},
        ],
    },
    'marea': {
        'name': '마레아', 'title': '생명의 신',
        'greeting': '이제 정말 갈게요.|…잠깐만요. 다치지 말아요. 그 말을 안 했네요.||이번엔 정말 가요.|…아, 그런데.',
        'head': 'marea_head', 'main': 'marea_ruyi', 'off': None,
        'props': [
            {'model': 'marea_water', 'ops': [['t', 0, 16, 0], ['r', 'x', 12], ['spin', 'y', 0.9, 0]]},
            {'model': 'marea_lingzhi', 'ops': [['t', -8, 17, -5], ['bob', 0.7, 0.07, 0], ['spin', 'y', 1.0, 0]]},
            {'model': 'marea_spiral', 'ops': [['t', -8, 15.5, -5], ['bob', 0.7, 0.07, 0], ['r', 'x', 10], ['spin', 'y', -2.4, 0]]},
            {'model': 'marea_fish_gold', 'ops': [['t', 0, 22, 0], ['spin', 'y', 1.4, 0], ['t', 15, 0, 0], ['bob', 0.7, 0.1, 0]]},
            {'model': 'marea_fish_jade', 'ops': [['t', 0, 11, 0], ['spin', 'y', 1.1, 180], ['t', 14, 0, 0], ['bob', 0.7, 0.1, 2]]},
        ],
        'particles': [
            {'kind': 'drip', 'every': 7, 'count': 1, 'at': [0, 19, 0], 'spread': [13, 1, 13]},
            {'kind': 'glow', 'every': 10, 'count': 1, 'at': [-8, 18, -5], 'spread': [2, 2, 2]},
        ],
    },
    'fortia': {
        'name': '포르티아', 'title': '전쟁의 신',
        'greeting': '물러서라고? 못 해. 그건 내가 모르는 말이다.||뭘 물어도 괜찮다.|괜찮다니까.',
        'head': 'fortia_head', 'main': 'fortia_spear', 'off': 'fortia_shield',
        'props': [{'model': 'fortia_arrows', 'ops': [['t', -6.5, 23.5, 0.5], ['r', 'z', 30], ['r', 'x', -25]]}],
        'particles': [
            {'kind': 'flame', 'every': 6, 'count': 1, 'at': [-7, 12, -6], 'spread': [1.5, 2, 0.5]},
            {'kind': 'dust', 'color': '#FF7A1A', 'size': 0.7, 'every': 4, 'count': 1, 'at': [0, 16, 0], 'spread': [9, 11, 7]},
            {'kind': 'flame', 'every': 9, 'count': 1, 'at': [6, 36, -3], 'spread': [0.6, 1, 0.6]},
        ],
    },
    'gravis': {
        'name': '그라비스', 'title': '규율의 신',
        'greeting': '…정하는 중이다.||무엇을 묻든, 한 번 정한 답은 바뀌지 않는다.|그러니 기다려라. 아직 짧다.',
        'head': 'gravis_head', 'main': 'gravis_pen', 'off': 'gravis_staff',
        'props': [{'model': 'gravis_stone_' + key, 'ops': [['t', 0, h, 0], ['spin', 'y', sp, ph], ['t', r, 0, 0], ['bob', 0.8, 0.05, ph / 40.0],
                                                           ['spin', 'x', 1.4, ph], ['s', sc]]}
                  for key, h, sp, ph, r, sc in (('a', 6, 0.5, 0, 15, 1.0), ('b', 13, -0.4, 60, 16, 0.8), ('c', 20, 0.45, 120, 14, 1.2),
                                                ('a', 27, -0.35, 200, 15, 0.9), ('b', 31, 0.5, 270, 12, 0.7), ('c', 10, 0.38, 320, 17, 1.1))],
        'particles': [
            {'kind': 'dust', 'color': '#E2B242', 'size': 0.7, 'every': 5, 'count': 1, 'at': [0, 18, 0], 'spread': [14, 14, 14]},
            {'kind': 'falling', 'every': 10, 'count': 1, 'at': [0, 22, 0], 'spread': [14, 8, 14]},
        ],
    },
}


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(obj, f, ensure_ascii=False, separators=(',', ':'))
        f.write('\n')


def build():
    out = {}
    models_all = {}
    for gid, spec in GODS.items():
        fn, slim = god_skins.GODS[gid]
        skin, cape = fn()
        tdir = os.path.join(ASSETS, 'textures', 'entity', 'god')
        os.makedirs(tdir, exist_ok=True)
        skin.save(os.path.join(tdir, gid + '.png'))
        if cape is not None:
            cape.save(os.path.join(tdir, gid + '_cape.png'))
        atlas = god_props.Atlas()
        models = god_props.GODS[gid](atlas)
        tex_id = 'rpg:item/god/' + gid
        idir = os.path.join(ASSETS, 'textures', 'item', 'god')
        os.makedirs(idir, exist_ok=True)
        atlas.img.save(os.path.join(idir, gid + '.png'))
        ks = {}
        for name, m in models.items():
            write_json(os.path.join(ASSETS, 'models', 'item', 'god', name + '.json'), m.json(tex_id))
            write_json(os.path.join(ASSETS, 'items', 'god', name + '.json'), {'model': {'type': 'minecraft:model', 'model': 'rpg:item/god/' + name}})
            ks[name] = m.k
        models_all[gid] = (skin, cape, slim, models, atlas)
        out[gid] = {
            'name': spec['name'], 'title': spec['title'], 'greeting': spec['greeting'],
            'skin': 'rpg:entity/god/' + gid, 'slim': slim, 'cape': ('rpg:entity/god/' + gid + '_cape') if cape is not None else None,
            'head': 'rpg:god/' + spec['head'], 'main': ('rpg:god/' + spec['main']) if spec['main'] else None,
            'off': ('rpg:god/' + spec['off']) if spec['off'] else None,
            'props': [{'model': 'rpg:god/' + p['model'], 'k': ks[p['model']], 'ops': p['ops']} for p in spec['props']],
            'particles': spec['particles'],
        }
        for p in spec['props'] + [{'model': spec[k]} for k in ('head', 'main', 'off') if spec[k]]:
            assert p['model'] in models, p['model']
    write_json(os.path.join(RES, 'mmorpg', 'gods.json'), {'_': '원소신 NPC (tools/god_npcs.py 가 만듦 — 고치려면 그 파일을)', 'gods': out})
    return out, models_all


# ------------------------------------------------------------ 미리보기

def ops_matrix(ops, t):
    import math
    M = m_ident()
    for op in ops:
        k = op[0]
        if k == 't':
            M = mat_mul(M, m_t(op[1], op[2], op[3]))
        elif k == 'r':
            M = mat_mul(M, m_r(op[1], op[2]))
        elif k == 'spin':
            M = mat_mul(M, m_r(op[1], op[3] + op[2] * t))
        elif k == 'bob':
            M = mat_mul(M, m_t(0, op[1] * math.sin(op[3] + op[2] * t), 0))
        elif k == 's':
            M = mat_mul(M, m_s(op[1]))
    return M


def preview(out, models_all, dest, t=0):
    os.makedirs(dest, exist_ok=True)
    rows = []
    for gid, (skin, cape, slim, models, atlas) in models_all.items():
        spec = out[gid]
        q = player_quads(skin.img, slim, cape_img=cape, arms={'rarm': -18 if spec['main'] else 0, 'larm': -18 if spec['off'] else 0})
        nm = lambda s: s.split('/')[-1]
        q += model_quads(models[nm(spec['head'])].json('x'), atlas.img, head_xf(models[nm(spec['head'])].json('x')['display']['head']))
        for hand, right in (('main', True), ('off', False)):
            if not spec[hand]:
                continue
            mj = models[nm(spec[hand])].json('x')
            d = mj['display']['thirdperson_righthand' if right else 'thirdperson_lefthand']
            q += model_quads(mj, atlas.img, hand_xf(d, right=right, slim=slim))
        for p in spec['props']:
            mj = models[nm(p['model'])].json('x')
            M = mat_mul(ops_matrix(p['ops'], t), m_s(p['k']))
            q += model_quads(mj, atlas.img, j_xf(M))
        imgs = [render(q, yaw=y, pitch=8, scale=7.0, size=(250, 400), center=(0, 19, 0)) for y in (0, 35, 90, 160)]
        imgs.append(render(q, yaw=20, pitch=6, scale=17, size=(270, 400), center=(0, 29, 0)))
        rows.append(sheet(imgs))
        rows[-1].convert('RGB').save(os.path.join(dest, gid + '.png'))
    return rows


if __name__ == '__main__':
    out, models_all = build()
    print('wrote', len(out), 'gods')
    if '--preview' in sys.argv:
        dest = os.path.join(ROOT, 'build', 'god_preview')
        preview(out, models_all, dest)
        print('preview →', dest)
