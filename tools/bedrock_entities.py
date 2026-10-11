#!/usr/bin/env python3
"""몬스터 · 보스 · 보스 소품 (5단계) · 탈것 (6단계) 자원 만들기: 애드온의 엔티티를 자바 모드 자원으로.

tools/bedrock_entities.py <애드온 폴더 (rpg_addon)>

- 수치 (서버 · 클라이언트 공통): RPG_BP/entities/rpg_monsters · rpg_bosses · rpg_mounts/*.json
  → src/main/resources/mmorpg/entities.json  {엔티티 ID: {group, properties, components, component_groups, events}} (애드온 JSON 그대로 —
  자바 쪽 mob.MobData 가 체력 · 이동 · 충돌 상자 · 크기 · 중력 · 무리 · 보스 막대 · 속성 · 효과 면역 · 행동을 읽음)
- 그리기 (클라이언트): RPG_RP/entity/... (그리는 법) · models/entity/... (geo) · animations/... · render_controllers/rpg_boss...
  → assets/mmorpg/bedrock/entities.json  {entities: {ID: 설명}, models: {geometry ID: geometry}, animations: {이름: 동작},
  render_controllers: {이름: 컨트롤러}, emissive: [빛나는 칸이 있는 텍스처 경로]} — 애드온 JSON 그대로, 클라이언트 BedrockModels 가 읽음
- 텍스처: RPG_RP/textures/entity/rpg/... → assets/mmorpg/textures/entity/rpg/....png
  · entity_emissive 재질 (베드락: 알파가 낮을수록 빛남 — 색 × mix(1, 빛, 알파), 늘 불투명): 바탕은 알파를 모두 255 로 (불투명),
    모델이 쓰는 칸 중 알파 < 255 인 칸이 있으면 <이름>_emissive.png (같은 색, 알파 = 255 - 원래 알파) — 클라이언트가 바탕 위에 빛나게 덧그림
    (덧그린 결과 = 밝은 색 × (1 - a) + 빛 받은 색 × a = 베드락과 같음)
  · entity_alphatest (잘라내기) · entity_alphablend (반투명) 는 그대로
- 소환 알 (몬스터 6종 — 애드온 spawn_egg 색): assets/rpg/textures/item/<키>_spawn_egg.png · 아이템 정의 · 모델
- 이름 (엔티티 · 소환 알): assets/rpg/lang/ko_kr.json · en_us.json 에 더함
"""
import json
import os
import sys

from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dummy_model import egg_icon  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "src", "main", "resources")
RPG = os.path.join(RES, "assets", "rpg")
MM = os.path.join(RES, "assets", "mmorpg")
GROUPS = ["rpg_monsters", "rpg_bosses", "rpg_mounts"]

# 보스 소품 · 분신 이름 (엔티티 이름 — 머리 위 이름표는 스크립트가 정함)
PROP_NAMES = {
    "rpg:decoy_scarecrow_king": "허수아비 킹 (분신)",
    "rpg:prop_jorm_ring": "요르문간드의 고리",
    "rpg:prop_kraken_tentacle": "크라켄의 촉수",
    "rpg:prop_label": "보스 글자",
    "rpg:prop_nid_roots": "세계수 뿌리의 올가미",
    "rpg:prop_pumpkin_bomb": "호박 폭탄",
    "rpg:prop_straw_cage": "짚단 감옥",
    "rpg:prop_surtr_core": "수르트의 핵",
    "rpg:prop_surtr_gauge": "업화",
    "rpg:prop_ymir_ice": "이미르의 얼음",
}


def load(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write_json(path, data, indent=None):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(data, f, ensure_ascii=False, indent=indent, separators=None if indent else (",", ":"))
        f.write("\n")


def rgb(h):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


def covered(geo, tw, th):
    """모델이 쓰는 텍스처 칸 (x, y) 집합 — 상자 UV 는 펼친 여섯 면, 면 UV 는 면마다 사각형"""
    cells = set()

    def rect(u, v, w, h):
        x0, x1 = sorted((u, u + w))
        y0, y1 = sorted((v, v + h))
        for y in range(int(y0), int(-(-y1 // 1))):
            for x in range(int(x0), int(-(-x1 // 1))):
                if 0 <= x < tw and 0 <= y < th:
                    cells.add((x, y))

    for b in geo.get("bones", []):
        for c in b.get("cubes", []):
            uv = c.get("uv", [0, 0])
            sx, sy, sz = c.get("size", [0, 0, 0])
            if isinstance(uv, dict):
                for face in uv.values():
                    u, v = face["uv"]
                    w, h = face.get("uv_size", [sx, sy])
                    rect(u, v, w, h)
            else:
                u, v = uv
                fx, fy, fz = int(sx // 1), int(sy // 1), int(sz // 1)
                rect(u + fz, v, fx, fz)  # 위
                rect(u + fz + fx, v, fx, fz)  # 아래
                rect(u, v + fz, fz, fy)
                rect(u + fz, v + fz, fx, fy)
                rect(u + fz + fx, v + fz, fz, fy)
                rect(u + 2 * fz + fx, v + fz, fx, fy)
    return cells


def main():
    addon = sys.argv[1]
    bp = os.path.join(addon, "RPG_BP")
    rp = os.path.join(addon, "RPG_RP")
    rules = load(os.path.join(ROOT, "core", "src", "main", "resources", "mmorpg", "rules.json"))

    # ---------------------------------------------------------------- 수치 (행동 팩)
    server = {}
    for g in GROUPS:
        d = os.path.join(bp, "entities", g)
        for f in sorted(os.listdir(d)):
            e = load(os.path.join(d, f))["minecraft:entity"]
            desc = e["description"]
            server[desc["identifier"]] = {
                "group": g.replace("rpg_", ""),
                "properties": desc.get("properties", {}),
                "components": e.get("components", {}),
                "component_groups": e.get("component_groups", {}),
                "events": e.get("events", {}),
            }
    write_json(os.path.join(RES, "mmorpg", "entities.json"), server)

    # ---------------------------------------------------------------- 그리기 (리소스 팩)
    geos = {}
    for root, _, files in os.walk(os.path.join(rp, "models", "entity")):
        for f in files:
            if f.endswith(".geo.json"):
                for gg in load(os.path.join(root, f)).get("minecraft:geometry", []):
                    geos[gg["description"]["identifier"]] = gg
    anims_all = {}
    for root, _, files in os.walk(os.path.join(rp, "animations")):
        for f in files:
            if f.endswith(".json"):
                anims_all.update(load(os.path.join(root, f)).get("animations", {}))
    controllers = load(os.path.join(rp, "render_controllers", "rpg_boss.render_controllers.json"))["render_controllers"]
    # 6단계 탈것 (rpg_mounts — 다이어울프 · 앞으로 더할 탈것): 탈것마다 렌더 컨트롤러 파일
    mount_rc = os.path.join(rp, "render_controllers", "rpg_mounts")
    if os.path.isdir(mount_rc):
        for f in sorted(os.listdir(mount_rc)):
            if f.endswith(".json"):
                controllers.update(load(os.path.join(mount_rc, f)).get("render_controllers", {}))

    entities, models, anims, emissive = {}, {}, {}, []
    textures = {}  # 텍스처 경로 → (재질, [geometry])
    for g in GROUPS:
        d = os.path.join(rp, "entity", g)
        for f in sorted(os.listdir(d)):
            desc = load(os.path.join(d, f))["minecraft:client_entity"]["description"]
            eid = desc["identifier"]
            if eid not in server:
                continue
            entities[eid] = desc
            geo_id = desc["geometry"]["default"]
            models[geo_id] = geos[geo_id]
            for a in desc.get("animations", {}).values():
                if a in anims_all:
                    anims[a] = anims_all[a]
            mat = desc.get("materials", {}).get("default", "entity_alphatest")
            for path in desc.get("textures", {}).values():
                textures.setdefault(path, (mat, []))[1].append(geos[geo_id])

    out_tex = os.path.join(MM, "textures")
    for path, (mat, gs) in sorted(textures.items()):
        src = os.path.join(rp, path + ".png")
        img = Image.open(src).convert("RGBA")
        rel = path[len("textures/"):] if path.startswith("textures/") else path
        dst = os.path.join(out_tex, rel + ".png")
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        if mat != "entity_emissive":
            img.save(dst)
            continue
        tw, th = img.size
        cells = set()
        for gg in gs:
            gw = gg["description"].get("texture_width", tw)
            gh = gg["description"].get("texture_height", th)
            for (x, y) in covered(gg, gw, gh):
                cells.add((x * tw // gw, y * th // gh))
        px = img.load()
        base = img.copy()
        bp_ = base.load()
        glow = Image.new("RGBA", img.size, (0, 0, 0, 0))
        gp = glow.load()
        n = 0
        for y in range(th):
            for x in range(tw):
                r, g_, b, a = px[x, y]
                bp_[x, y] = (r, g_, b, 255)
                if a < 255 and (x, y) in cells:
                    gp[x, y] = (r, g_, b, 255 - a)
                    n += 1
        base.save(dst)
        if n:
            glow.save(os.path.join(out_tex, rel + "_emissive.png"))
            emissive.append(path)
        print(f"{path}: emissive texels {n}")

    write_json(os.path.join(MM, "bedrock", "entities.json"),
               {"entities": entities, "models": models, "animations": anims, "render_controllers": controllers, "emissive": emissive})

    # ---------------------------------------------------------------- 소환 알 · 이름
    names = {}
    monsters = rules["MONSTERS"]
    for key in rules["MONSTER_ORDER"]:
        eid = "rpg:" + key
        names["entity.rpg." + key] = monsters[key]["name"]
        egg = entities[eid].get("spawn_egg")
        if not egg:
            continue
        base, over = rgb(egg["base_color"]), rgb(egg["overlay_color"])
        spots = {}
        for (x, y) in [(5, 5), (9, 4), (11, 7), (4, 9), (8, 9), (10, 12), (6, 12), (7, 6)]:
            spots[(x, y)] = over
        for (x, y) in [(6, 5), (10, 4), (5, 9), (9, 9), (11, 12)]:
            spots[(x, y)] = tuple(int(c * 0.8) for c in over)
        os.makedirs(os.path.join(RPG, "textures", "item"), exist_ok=True)
        egg_icon(os.path.join(RPG, "textures", "item", f"{key}_spawn_egg.png"), base, spots)
        write_json(os.path.join(RPG, "items", f"{key}_spawn_egg.json"),
                   {"model": {"type": "minecraft:model", "model": f"rpg:item/{key}_spawn_egg"}}, 2)
        write_json(os.path.join(RPG, "models", "item", f"{key}_spawn_egg.json"),
                   {"parent": "minecraft:item/generated", "textures": {"layer0": f"rpg:item/{key}_spawn_egg"}}, 2)
    for key, b in rules["BOSSES"].items():
        for u in b.get("units", []) or []:
            names["entity.rpg.boss_" + u["key"]] = u["name"]
        if not b.get("units"):
            names["entity.rpg.boss_" + key] = b["name"]
    for eid, n in PROP_NAMES.items():
        names["entity.rpg." + eid.split(":")[1]] = n
    # 탈것: 이름은 src/main/resources/mmorpg/mounts.json (탈것 목록 — 앞으로 더할 탈것도 여기에)
    mounts = load(os.path.join(RES, "mmorpg", "mounts.json"))
    for key, m in mounts["mounts"].items():
        names["entity.rpg." + m["entity"].split(":")[1]] = m["name"]
    for lang in ("ko_kr", "en_us"):
        p = os.path.join(RPG, "lang", lang + ".json")
        d = load(p)
        d.update(names)
        write_json(p, d, 2)
    missing = [e for e in server if "entity.rpg." + e.split(":")[1] not in names]
    print(f"entities {len(server)} (client {len(entities)}), models {len(models)}, animations {len(anims)}, "
          f"controllers {len(controllers)}, textures {len(textures)} (emissive {len(emissive)}), names missing {missing}")


if __name__ == "__main__":
    main()
