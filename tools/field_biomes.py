#!/usr/bin/env python3
"""섬 맵 바이옴 (2026-11-07): src/main/resources/data/mmorpg/worldgen/biome/*.json · 바이옴 꼬리표 #mmorpg:field.

26.3 바이옴 형식 (attributes = 환경 속성: 하늘 · 안개 · 물안개 색 · 떠다니는 입자 · 음악 · 소리 · 동물). 하늘 · 안개 · 물 색은 바이옴 데이터라
모드가 없는 클라이언트 (바닐라 접속) 에서도 보임 — 섬마다 분위기 (공허 = 보랏빛 · 헬 = 잿빛 안개 · 무스펠 = 붉은 하늘 · 빙하 = 눈발).
몬스터는 나오지 않음 (바닐라 몬스터 없음 — 이 게임의 몬스터는 사냥터에서). 지형 장식 (나무 · 풀 · 꽃 · 선인장 · 산호 · 빙산 · 현무암 기둥) 은 바닐라 장식 그대로.
바이옴 이름 목록은 core FieldTest.BIOMES 와 같아야 함.

쓰는 법: python3 tools/field_biomes.py
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')
OUT = os.path.join(ROOT, 'src/main/resources/data/mmorpg/worldgen/biome')
TAG = os.path.join(ROOT, 'src/main/resources/data/mmorpg/tags/worldgen/biome/field.json')
PRESET = os.path.join(ROOT, 'src/main/resources/data/mmorpg/worldgen/world_preset/field.json')

ORES = ['minecraft:ore_dirt', 'minecraft:ore_gravel', 'minecraft:ore_granite_upper', 'minecraft:ore_granite_lower', 'minecraft:ore_diorite_upper',
        'minecraft:ore_diorite_lower', 'minecraft:ore_andesite_upper', 'minecraft:ore_andesite_lower', 'minecraft:ore_tuff', 'minecraft:ore_coal_upper',
        'minecraft:ore_coal_lower', 'minecraft:ore_iron_upper', 'minecraft:ore_iron_middle', 'minecraft:ore_iron_small', 'minecraft:ore_gold',
        'minecraft:ore_gold_lower', 'minecraft:ore_redstone', 'minecraft:ore_redstone_lower', 'minecraft:ore_diamond', 'minecraft:ore_diamond_medium',
        'minecraft:ore_diamond_large', 'minecraft:ore_diamond_buried', 'minecraft:ore_lapis', 'minecraft:ore_lapis_buried', 'minecraft:ore_copper',
        'minecraft:underwater_magma', 'minecraft:disk_sand', 'minecraft:disk_clay', 'minecraft:disk_gravel']

MUSIC = lambda s: {'default': {'max_delay': 24000, 'min_delay': 12000, 'sound': s}}
OCEAN_MUSIC = {'creative': {'max_delay': 24000, 'min_delay': 12000, 'sound': 'minecraft:music.creative'},
               'default': {'max_delay': 24000, 'min_delay': 12000, 'sound': 'minecraft:music.game'},
               'underwater': {'max_delay': 24000, 'min_delay': 12000, 'sound': 'minecraft:music.under_water'}}


def spawns(creature=(), water=(), ambient=(), water_ambient=()):
    cat = {}
    def entries(lst):
        out = []
        for t, w, lo, hi in lst:
            out.append({'type': t, 'count': lo if lo == hi else {'type': 'minecraft:uniform', 'max_inclusive': hi, 'min_inclusive': lo}, 'weight': w})
        return out
    if creature:
        cat['creature'] = entries(creature)
    if water:
        cat['water_creature'] = entries(water)
    if ambient:
        cat['ambient'] = entries(ambient)
    if water_ambient:
        cat['water_ambient'] = entries(water_ambient)
    return {'argument': {'spawn_costs': {}, 'spawns_by_category': cat}, 'modifier': 'overlay'}


FARM = [('minecraft:sheep', 12, 2, 4), ('minecraft:pig', 10, 2, 4), ('minecraft:chicken', 10, 2, 4), ('minecraft:cow', 8, 2, 4)]
SEA_LIFE = [('minecraft:squid', 3, 1, 4), ('minecraft:dolphin', 1, 1, 2)]
FISH = [('minecraft:cod', 10, 3, 6), ('minecraft:salmon', 5, 1, 5)]
WARM_FISH = [('minecraft:tropical_fish', 25, 8, 8), ('minecraft:pufferfish', 15, 1, 3)]


def biome(temp, down, precip, water, sky, veg=(), local=(), surface=(), deco=(), fog=None, water_fog=None, grass=None, foliage=None, dry=None,
          grass_mod=None, particles=None, music=None, sounds=None, mobs=None, modifier=None, ores=True, wg_spawn=None):
    attrs = {'minecraft:gameplay/natural_mob_spawns': mobs or spawns()}
    attrs['minecraft:visual/sky_color'] = sky
    if fog:
        attrs['minecraft:visual/fog_color'] = fog
    if water_fog:
        attrs['minecraft:visual/water_fog_color'] = water_fog
    if particles:
        attrs['minecraft:visual/ambient_particles'] = {'argument': [{'particle': {'type': p}, 'probability': pr} for p, pr in particles], 'modifier': 'append'}
    if music:
        attrs['minecraft:audio/background_music'] = music
    if sounds:
        attrs['minecraft:audio/ambient_sounds'] = sounds
    if wg_spawn is not None:
        attrs['minecraft:gameplay/creature_world_gen_spawn_probability'] = wg_spawn
    eff = {'water_color': water}
    if grass:
        eff['grass_color'] = grass
    if foliage:
        eff['foliage_color'] = foliage
    if dry:
        eff['dry_foliage_color'] = dry
    if grass_mod:
        eff['grass_color_modifier'] = grass_mod
    steps = [[] for _ in range(11)]
    steps[2] = list(local)
    steps[4] = list(surface)
    steps[6] = list(ORES) if ores else []
    steps[7] = list(deco)
    steps[9] = list(veg)
    steps[10] = ['minecraft:freeze_top_layer']
    d = {'attributes': dict(sorted(attrs.items())), 'carvers': [], 'downfall': down, 'effects': eff, 'features': steps,
         'has_precipitation': precip, 'temperature': temp}
    if modifier:
        d['temperature_modifier'] = modifier
    return d


B = {
    # ---- 바다
    'open_sea': biome(0.5, 0.5, True, '#3f76e4', '#7ba4ff', veg=['minecraft:seagrass_normal', 'minecraft:kelp_cold'],
                      mobs=spawns(water=SEA_LIFE, water_ambient=FISH), music=OCEAN_MUSIC),
    'shallows': biome(0.6, 0.5, True, '#43d5ee', '#7ba4ff', water_fog='#041f33',
                      veg=['minecraft:warm_ocean_vegetation', 'minecraft:seagrass_warm', 'minecraft:sea_pickle'],
                      mobs=spawns(water=[('minecraft:squid', 2, 1, 4)], water_ambient=WARM_FISH), music=OCEAN_MUSIC),
    'frozen_shallows': biome(0.0, 0.5, True, '#3938c9', '#7fa1ff', local=['minecraft:iceberg_packed', 'minecraft:iceberg_blue'],
                             surface=['minecraft:blue_ice'], mobs=spawns(water=[('minecraft:squid', 1, 1, 4)], water_ambient=[('minecraft:salmon', 15, 1, 5)]),
                             modifier='frozen', music=OCEAN_MUSIC),
    'rift_shallows': biome(0.5, 0.0, False, '#2a1f5a', '#2a1640', fog='#3a2458', water_fog='#120a22',
                           particles=[('minecraft:reverse_portal', 0.004)], music=MUSIC('minecraft:music.overworld.deep_dark')),
    'ember_shallows': biome(1.2, 0.0, False, '#7a4a3a', '#7a3a26', fog='#8a4a2a', water_fog='#2a0f08',
                            particles=[('minecraft:white_ash', 0.02)], music=MUSIC('minecraft:music.nether.basalt_deltas')),
    # ---- 마을 섬
    'midgard': biome(0.7, 0.6, True, '#3f76e4', '#78a7ff', grass='#7cc25c', foliage='#5cb236',
                     veg=['minecraft:patch_tall_grass_2', 'minecraft:patch_grass_meadow', 'minecraft:flower_meadow', 'minecraft:wildflowers_meadow',
                          'minecraft:trees_meadow', 'minecraft:patch_firefly_bush_near_water'],
                     mobs=spawns(creature=[('minecraft:sheep', 10, 2, 3), ('minecraft:chicken', 8, 2, 3)]),
                     music=MUSIC('minecraft:music.overworld.meadow')),
    'midgard_grove': biome(0.7, 0.7, True, '#3f76e4', '#79a6ff', grass='#72b852', foliage='#58a832', local=['minecraft:forest_rock'],
                           veg=['minecraft:forest_flowers', 'minecraft:trees_birch_and_oak_leaf_litter', 'minecraft:patch_bush', 'minecraft:flower_default',
                                'minecraft:patch_grass_forest', 'minecraft:brown_mushroom_normal', 'minecraft:red_mushroom_normal',
                                'minecraft:patch_firefly_bush_near_water'],
                           mobs=spawns(creature=[('minecraft:rabbit', 4, 2, 3), ('minecraft:pig', 6, 2, 3)]), music=MUSIC('minecraft:music.overworld.forest')),
    'midgard_town': biome(0.7, 0.6, True, '#3f76e4', '#78a7ff', grass='#7cc25c', foliage='#5cb236',
                          veg=['minecraft:patch_grass_meadow', 'minecraft:flower_meadow', 'minecraft:wildflowers_meadow'],
                          mobs=spawns(creature=[('minecraft:chicken', 6, 1, 2)]), music=MUSIC('minecraft:music.overworld.meadow')),
    # ---- 농장 섬
    'harvest_fields': biome(0.8, 0.4, True, '#3f76e4', '#78a7ff', grass='#9bc65a',
                            veg=['minecraft:patch_tall_grass_2', 'minecraft:patch_bush', 'minecraft:trees_plains', 'minecraft:flower_plains',
                                 'minecraft:patch_grass_plain', 'minecraft:wildflowers_meadow', 'minecraft:patch_pumpkin', 'minecraft:patch_sugar_cane',
                                 'minecraft:patch_firefly_bush_near_water'],
                            mobs=spawns(creature=FARM), music=MUSIC('minecraft:music.overworld.meadow')),
    'harvest_woods': biome(0.7, 0.8, True, '#3f76e4', '#79a6ff',
                           veg=['minecraft:forest_flowers', 'minecraft:trees_birch_and_oak_leaf_litter', 'minecraft:patch_bush', 'minecraft:flower_default',
                                'minecraft:patch_grass_forest', 'minecraft:brown_mushroom_normal', 'minecraft:red_mushroom_normal', 'minecraft:patch_pumpkin',
                                'minecraft:patch_firefly_bush_near_water'],
                           mobs=spawns(creature=[('minecraft:pig', 10, 2, 4), ('minecraft:chicken', 10, 2, 4), ('minecraft:rabbit', 4, 2, 3)]),
                           music=MUSIC('minecraft:music.overworld.forest')),
    # ---- 사막 섬
    'dune_sea': biome(2.0, 0.0, False, '#3f76e4', '#6eb1ff',
                      veg=['minecraft:patch_dry_grass_desert', 'minecraft:patch_dead_bush_2', 'minecraft:patch_sugar_cane_desert', 'minecraft:patch_cactus_desert'],
                      mobs=spawns(creature=[('minecraft:rabbit', 4, 2, 3)]), music=MUSIC('minecraft:music.overworld.desert'), wg_spawn=0.03),
    'red_mesa': biome(2.0, 0.0, False, '#3f76e4', '#6eb1ff', grass='#90814d', foliage='#9e814d',
                      veg=['minecraft:patch_grass_badlands', 'minecraft:patch_dry_grass_badlands', 'minecraft:patch_dead_bush_badlands',
                           'minecraft:patch_cactus_decorated'],
                      mobs=spawns(), music=MUSIC('minecraft:music.overworld.badlands'), wg_spawn=0.0),
    'oasis': biome(1.6, 0.3, False, '#43d5ee', '#6eb1ff', grass='#6fbf4f', foliage='#4fae2a', water_fog='#0a3a4a',
                   veg=['minecraft:patch_tall_grass', 'minecraft:trees_savanna', 'minecraft:flower_warm', 'minecraft:patch_grass_savanna',
                        'minecraft:patch_sugar_cane', 'minecraft:patch_firefly_bush_near_water'],
                   mobs=spawns(creature=[('minecraft:rabbit', 4, 2, 3)]), music=MUSIC('minecraft:music.overworld.desert')),
    # ---- 바다 섬
    'fjord_coast': biome(0.3, 0.8, True, '#3d57d6', '#7da3ff', grass='#79a85d', foliage='#5e9a45', local=['minecraft:forest_rock'],
                         veg=['minecraft:patch_large_fern', 'minecraft:trees_taiga', 'minecraft:trees_meadow', 'minecraft:flower_default',
                              'minecraft:patch_grass_taiga_2', 'minecraft:patch_berry_common', 'minecraft:patch_firefly_bush_near_water'],
                         mobs=spawns(creature=[('minecraft:sheep', 8, 2, 4), ('minecraft:rabbit', 4, 2, 3), ('minecraft:fox', 6, 2, 3)]),
                         music=MUSIC('minecraft:music.overworld.old_growth_taiga')),
    'mire': biome(0.8, 0.9, True, '#617b64', '#78a7ff', fog='#a9b8a4', water_fog='#232317', foliage='#6a7039', dry='#7b5334', grass_mod='swamp',
                  veg=['minecraft:trees_swamp', 'minecraft:flower_swamp', 'minecraft:patch_grass_normal', 'minecraft:patch_dead_bush', 'minecraft:patch_waterlily',
                       'minecraft:brown_mushroom_swamp', 'minecraft:red_mushroom_swamp', 'minecraft:patch_sugar_cane_swamp', 'minecraft:patch_firefly_bush_swamp',
                       'minecraft:patch_firefly_bush_near_water_swamp', 'minecraft:seagrass_swamp'],
                  mobs=spawns(creature=[('minecraft:frog', 10, 2, 5)]), music=MUSIC('minecraft:music.overworld.swamp')),
    # ---- 북쪽 섬
    'frost_taiga': biome(-0.5, 0.4, True, '#3d57d6', '#839eff',
                         veg=['minecraft:patch_large_fern', 'minecraft:trees_taiga', 'minecraft:flower_default', 'minecraft:patch_grass_taiga_2',
                              'minecraft:patch_berry_rare'],
                         mobs=spawns(creature=[('minecraft:rabbit', 4, 2, 3), ('minecraft:fox', 8, 2, 4)]), music=MUSIC('minecraft:music.overworld.frozen_peaks')),
    'frost_peaks': biome(-0.7, 0.9, True, '#3f76e4', '#859dff', music=MUSIC('minecraft:music.overworld.frozen_peaks'), wg_spawn=0.0),
    'glacier': biome(-0.8, 0.6, True, '#3938c9', '#8aa6ff', fog='#dbe7ff', surface=['minecraft:ice_spike', 'minecraft:ice_patch'],
                     particles=[('minecraft:snowflake', 0.012)], music=MUSIC('minecraft:music.overworld.frozen_peaks'), wg_spawn=0.0),
    # ---- 공허 섬
    'void_rift': biome(0.5, 0.0, False, '#2a1f5a', '#2a1640', fog='#3a2458', water_fog='#120a22', grass='#4a3a6a', foliage='#3a2a5a',
                       particles=[('minecraft:reverse_portal', 0.006)], music=MUSIC('minecraft:music.overworld.deep_dark'), ores=False, wg_spawn=0.0),
    'helheim': biome(0.3, 0.0, False, '#2a3440', '#3c3f48', fog='#5c6470', water_fog='#10141a',
                     particles=[('minecraft:ash', 0.012), ('minecraft:soul', 0.0015)], music=MUSIC('minecraft:music.nether.soul_sand_valley'),
                     sounds={'additions': {'sound': 'minecraft:ambient.soul_sand_valley.additions', 'tick_chance': 0.0111},
                             'loop': 'minecraft:ambient.soul_sand_valley.loop',
                             'mood': {'block_search_extent': 8, 'offset': 2.0, 'sound': 'minecraft:ambient.soul_sand_valley.mood', 'tick_delay': 6000}},
                     ores=False, wg_spawn=0.0),
    # ---- 무스펠 섬
    'muspelheim': biome(2.0, 0.0, False, '#7a4a3a', '#7a3a26', fog='#8a4a2a', water_fog='#2a0f08', grass='#7a5a2a', foliage='#6a4a1a',
                        surface=['minecraft:delta', 'minecraft:small_basalt_columns'],
                        particles=[('minecraft:white_ash', 0.03), ('minecraft:ash', 0.01)], music=MUSIC('minecraft:music.nether.basalt_deltas'),
                        sounds={'additions': {'sound': 'minecraft:ambient.basalt_deltas.additions', 'tick_chance': 0.0111},
                                'loop': 'minecraft:ambient.basalt_deltas.loop',
                                'mood': {'block_search_extent': 8, 'offset': 2.0, 'sound': 'minecraft:ambient.basalt_deltas.mood', 'tick_delay': 6000}},
                        ores=False, wg_spawn=0.0),
    # ---- 2026-11-28 새 섬 셋 (다리 없이 이동 지점 돌로 감 — map-layout 2-5 · 2-7 · 2-9)
    # 눈꽃성 섬: 얼음 나라 수도 — 어스름 같은 낮은 하늘 · 눈발 · 드문 가문비 (호수는 freeze_top_layer 로 얼음)
    'snowcrown': biome(-0.9, 0.7, True, '#3938c9', '#5c6fb0', fog='#b8c4e6', veg=['minecraft:trees_snowy'],
                       particles=[('minecraft:snowflake', 0.02)], music=MUSIC('minecraft:music.overworld.frozen_peaks'), wg_spawn=0.0),
    # 균열 곶: 북쪽 끝 바위 곶 — 보랏빛 잿빛 하늘 · 재 · 드문 공허 빛. 눈 · 비는 내리지 않음 (눈이 쌓이면 북쪽 끝의 공허에 물든 땅이
    # 하얗게 덮임 — CI 46121652796 사진), 눈은 겉흙 (눈 블록 조각) 으로만
    'rift_cape': biome(0.6, 0.0, False, '#2a2f6a', '#4a4470', fog='#8a84a8', water_fog='#120a22',
                       particles=[('minecraft:white_ash', 0.01), ('minecraft:reverse_portal', 0.002)], music=MUSIC('minecraft:music.overworld.deep_dark'),
                       ores=False, wg_spawn=0.0),
    # 불 나라 섬: 붉은 노을빛 하늘 · 재 입자 · 마른 풀
    'shieldwall': biome(2.0, 0.0, False, '#3f76e4', '#c97a52', fog='#d99a6c', grass='#90814d', foliage='#9e814d',
                        veg=['minecraft:patch_dry_grass_badlands', 'minecraft:patch_dead_bush_badlands'],
                        particles=[('minecraft:white_ash', 0.006)], music=MUSIC('minecraft:music.overworld.badlands'), wg_spawn=0.0),
    # 검은 틈 들 (불 나라 동서): 공허가 스민 땅 — 어두운 붉은 하늘 · 공허 빛
    'black_fissures': biome(2.0, 0.0, False, '#2a1f5a', '#7a4a52', fog='#6a4a5c', water_fog='#120a22',
                            particles=[('minecraft:reverse_portal', 0.004), ('minecraft:ash', 0.006)], music=MUSIC('minecraft:music.overworld.badlands'),
                            ores=False, wg_spawn=0.0),
    # ---- 2026-11-28 나라 섬 둘 (사용자 "불의 나라나 땅의 나라 번개의 나라 같은거 추가 하고 싶으면 추가해도 됨")
    # 번개 나라 섬: 계단식 논 (논물에 해초 = 벼 싹 · 개구리) · 벚꽃 잎 · 대숲과 벚나무 · 산꼭대기는 먹구름 하늘과 전기 불꽃
    'inae_fields': biome(0.8, 0.8, True, '#3f8fe4', '#6f9ee6', fog='#c6d8ee', grass='#8fd05a', foliage='#62b83a',
                         veg=['minecraft:seagrass_swamp', 'minecraft:patch_tall_grass_2', 'minecraft:flower_cherry', 'minecraft:patch_sugar_cane'],
                         mobs=spawns(creature=[('minecraft:frog', 8, 2, 4), ('minecraft:chicken', 6, 2, 3)]),
                         music=MUSIC('minecraft:music.overworld.cherry_grove')),
    'inae_grove': biome(0.7, 0.8, True, '#3f8fe4', '#6f9ee6', fog='#c6d8ee', grass='#7fc455', foliage='#5aae36',
                        veg=['minecraft:bamboo_light', 'minecraft:trees_cherry', 'minecraft:flower_cherry', 'minecraft:patch_grass_forest'],
                        mobs=spawns(creature=[('minecraft:rabbit', 4, 2, 3)]), music=MUSIC('minecraft:music.overworld.bamboo_jungle')),
    'inae_peak': biome(0.6, 0.8, True, '#3f8fe4', '#4a5a8c', fog='#8f9cc0', grass='#86b862',
                       particles=[('minecraft:electric_spark', 0.006)], music=MUSIC('minecraft:music.overworld.jagged_peaks'), wg_spawn=0.0),
    # 바위 나라 섬: 맑은 하늘 · 따뜻한 모래빛 안개 · 드문 나무와 꽃 (정원), 무게 없는 곳은 옅은 보랏빛 하늘 · 떠오르는 빛 알갱이
    'sage_plateau': biome(1.0, 0.2, False, '#3f9fe4', '#78b4f0', fog='#ead9b4', grass='#9fb46a', foliage='#7fa04e',
                          veg=['minecraft:trees_plains', 'minecraft:flower_plains', 'minecraft:patch_grass_plain'],
                          mobs=spawns(creature=[('minecraft:sheep', 4, 2, 3)]), music=MUSIC('minecraft:music.overworld.desert')),
    'weightless': biome(0.9, 0.0, False, '#7f7fd6', '#b0a8e0', fog='#eae4ff', water_fog='#1a1640',
                        particles=[('minecraft:end_rod', 0.004), ('minecraft:reverse_portal', 0.002)], music=MUSIC('minecraft:music.overworld.deep_dark'),
                        ores=False, wg_spawn=0.0),
}


# 빈터 (거점 · 작은 마을 · 신전 자리 — core FieldTerrain.CLEARING): 바탕 바이옴과 하늘 · 안개 · 색 · 소리는 같고, 나무 · 선인장 · 호박 · 사탕수수 ·
# 덤불 · 버섯 · 바위 · 현무암 기둥 · 삼각주 · 얼음 가시 · 빙산 · 수련만 뺌 (CI 42359496849 사진: 항구 · 사냥막이 숲에, 불꽃 전초기지가 현무암 기둥에 묻힘)
CLEAR = {'harvest_clearing': 'harvest_fields', 'dune_clearing': 'dune_sea', 'oasis_clearing': 'oasis', 'fjord_clearing': 'fjord_coast',
         'mire_clearing': 'mire', 'frost_clearing': 'frost_taiga', 'glacier_clearing': 'glacier', 'muspel_clearing': 'muspelheim',
         'snowcrown_clearing': 'snowcrown', 'rift_cape_clearing': 'rift_cape', 'shieldwall_clearing': 'shieldwall',
         'inae_clearing': 'inae_fields', 'sage_clearing': 'sage_plateau'}
BANNED = ('trees', 'cactus', 'pumpkin', 'sugar_cane', 'berry', 'large_fern', 'forest_rock', 'basalt', 'delta', 'ice_spike', 'ice_patch', 'iceberg',
          'waterlily', 'bush', 'mushroom')


def clearing(base):
    d = json.loads(json.dumps(B[base]))
    d['features'] = [[f for f in step if not any(b in f for b in BANNED)] for step in d['features']]
    return d


for _k, _base in CLEAR.items():
    B[_k] = clearing(_base)


def main():
    os.makedirs(OUT, exist_ok=True)
    for f in os.listdir(OUT):
        if f.endswith('.json') and f[:-5] not in B:
            os.remove(os.path.join(OUT, f))
    for k, d in B.items():
        with open(os.path.join(OUT, k + '.json'), 'w', encoding='utf-8') as fh:
            json.dump(d, fh, ensure_ascii=False, indent=2)
            fh.write('\n')
    os.makedirs(os.path.dirname(TAG), exist_ok=True)
    with open(TAG, 'w', encoding='utf-8') as fh:
        json.dump({'values': ['mmorpg:' + k for k in B]}, fh, indent=2)
        fh.write('\n')
    # 월드 유형: 지상 세계 = 섬 맵 생성기 (바이옴은 목록으로 — 꼬리표는 레지스트리를 읽는 동안 아직 묶이지 않아 쓸 수 없음), 네더 · 엔드는 바닐라
    preset = {'dimensions': {
        'minecraft:overworld': {'type': 'minecraft:overworld', 'generator': {
            'type': 'mmorpg:field', 'biome_source': {'type': 'mmorpg:field', 'biomes': ['mmorpg:' + k for k in B]}}},
        'minecraft:the_end': {'type': 'minecraft:the_end', 'generator': {
            'type': 'minecraft:noise', 'biome_source': {'type': 'minecraft:the_end'}, 'settings': 'minecraft:end'}},
        'minecraft:the_nether': {'type': 'minecraft:the_nether', 'generator': {
            'type': 'minecraft:noise', 'biome_source': {'type': 'minecraft:multi_noise', 'preset': 'minecraft:nether'}, 'settings': 'minecraft:nether'}}}}
    os.makedirs(os.path.dirname(PRESET), exist_ok=True)
    with open(PRESET, 'w', encoding='utf-8') as fh:
        json.dump(preset, fh, indent=2)
        fh.write('\n')
    print(len(B), 'biomes →', os.path.relpath(OUT, ROOT), '· world preset', os.path.relpath(PRESET, ROOT))


if __name__ == '__main__':
    main()
