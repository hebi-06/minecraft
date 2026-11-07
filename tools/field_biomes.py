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
                        surface=['minecraft:delta', 'minecraft:small_basalt_columns', 'minecraft:large_basalt_columns'],
                        particles=[('minecraft:white_ash', 0.03), ('minecraft:ash', 0.01)], music=MUSIC('minecraft:music.nether.basalt_deltas'),
                        sounds={'additions': {'sound': 'minecraft:ambient.basalt_deltas.additions', 'tick_chance': 0.0111},
                                'loop': 'minecraft:ambient.basalt_deltas.loop',
                                'mood': {'block_search_extent': 8, 'offset': 2.0, 'sound': 'minecraft:ambient.basalt_deltas.mood', 'tick_delay': 6000}},
                        ores=False, wg_spawn=0.0),
}


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
    print(len(B), 'biomes →', os.path.relpath(OUT, ROOT))


if __name__ == '__main__':
    main()
