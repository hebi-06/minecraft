#!/usr/bin/env python3
"""gradle.properties 의 Fabric 버전 중 auto 인 것을 채우고, 고를 수 있는 버전을 로그에 남긴다 (CI 첫 단계).

- loader_version=auto → 그 마인크래프트 버전을 지원하는 가장 새 안정판 Fabric Loader (meta.fabricmc.net)
- fabric_version=auto → "+<마인크래프트 버전>" 으로 끝나는 가장 새 Fabric API (maven.fabricmc.net)
- 정해진 값 (숫자) 은 그대로 둠. 마지막에 쓴 값을 출력 (로그에서 확인해 gradle.properties 에 고정)
"""
import json
import re
import sys
import urllib.request

PROPS = "gradle.properties"


def get(url):
    req = urllib.request.Request(url, headers={"User-Agent": "mmorpg-ci"})
    with urllib.request.urlopen(req, timeout=60) as r:
        return r.read().decode("utf-8")


def vkey(v):
    """0.116.7+1.21.1 → (0, 116, 7) 처럼 숫자 마디로 정렬"""
    head = v.split("+", 1)[0]
    return tuple(int(x) if x.isdigit() else -1 for x in re.split(r"[.\-]", head))


def main():
    lines = open(PROPS, encoding="utf-8").read().split("\n")
    props = {}
    for ln in lines:
        if "=" in ln and not ln.lstrip().startswith("#"):
            k, v = ln.split("=", 1)
            props[k.strip()] = v.strip()
    mc = props["minecraft_version"]
    print(f"minecraft_version = {mc}")

    games = json.loads(get("https://meta.fabricmc.net/v2/versions/game"))
    print("stable game versions (newest 20):", ", ".join(g["version"] for g in games if g.get("stable"))[:400])

    loaders = json.loads(get(f"https://meta.fabricmc.net/v2/versions/loader/{mc}"))
    stable = [x["loader"]["version"] for x in loaders if x["loader"].get("stable")]
    print("loader versions for", mc, "(newest 8):", ", ".join(stable[:8]))

    api = re.findall(r"<version>([^<]+)</version>", get("https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/maven-metadata.xml"))
    api_mc = sorted((v for v in api if v.endswith("+" + mc)), key=vkey)
    print("fabric-api versions for", mc, "(newest 6):", ", ".join(api_mc[-6:]))
    # 다른 버전으로 옮길 때 참고: 버전마다 가장 새 Fabric API
    by_mc = {}
    for v in api:
        if "+" in v:
            by_mc.setdefault(v.split("+", 1)[1], []).append(v)
    recent = [g["version"] for g in games if g.get("stable")][:8]
    for g in recent:
        if g in by_mc:
            print(f"  latest fabric-api for {g}: {sorted(by_mc[g], key=vkey)[-1]}")

    loom = re.findall(r"<version>([^<]+)</version>", get("https://maven.fabricmc.net/net/fabricmc/fabric-loom/maven-metadata.xml"))
    print("fabric-loom versions (newest 12):", ", ".join(loom[-12:]))

    chosen = {}
    if props.get("loader_version") == "auto":
        if not stable:
            sys.exit(f"no stable loader for {mc}")
        chosen["loader_version"] = stable[0]
    if props.get("fabric_version") == "auto":
        if not api_mc:
            sys.exit(f"no fabric-api for {mc}")
        chosen["fabric_version"] = api_mc[-1]
    out = []
    for ln in lines:
        k = ln.split("=", 1)[0].strip() if "=" in ln else None
        out.append(f"{k}={chosen[k]}" if k in chosen else ln)
    open(PROPS, "w", encoding="utf-8").write("\n".join(out))
    final = {k: chosen.get(k, props.get(k)) for k in ("minecraft_version", "loader_version", "fabric_version", "loom_version")}
    print("USING", json.dumps(final))


if __name__ == "__main__":
    main()
