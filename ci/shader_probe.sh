#!/bin/bash
# 2026-11-15 셰이더 시험 준비 (사용자 "너가 추천한거 다 넣어서 시범으로 만들어봐" — 리소스 팩 셰이더로 이펙트 명암 끄기 · 빛 번짐):
# 26.3 클라이언트 jar 의 셰이더 · 후처리 파일 목록과 선언 줄만 (본문은 찍지 않음 — 공개 저장소) · 글꼴 판 크기 상수 · 렌더 파이프라인 이름
set +e
J=""
for f in $(find .gradle "$HOME/.gradle" -name "*.jar" -path "*26.3*" 2>/dev/null); do
  if unzip -Z1 "$f" 2>/dev/null | grep -q "^assets/minecraft/shaders/"; then J="$f"; break; fi
done
echo "[shader-probe] jar ${J:-none}"
[ -n "$J" ] || exit 0
echo "----- shader · post_effect files"
unzip -Z1 "$J" | grep -E "^assets/minecraft/(shaders|post_effect)/" | sort
DECL='^\s*(#version|#extension|#moj_import|#define|#ifdef|#ifndef|#if |#if\(|#else|#elif|#endif|layout|uniform|in |out |flat |struct |(vec[234]|float|int|void|mat[34]|bool) [a-zA-Z_0-9]+\s*\()'
for f in $(unzip -Z1 "$J" | grep -E "^assets/minecraft/shaders/(core/(entity|rendertype_item|rendertype_entity_translucent|rendertype_text|text|item)|include/|post/(entity_outline|entity_sobel|box_blur|blit|blur|bloom))"); do
  echo "----- $f (선언 · 분기 줄만)"
  unzip -p "$J" "$f" | grep -E "$DECL"
done
for f in $(unzip -Z1 "$J" | grep -E "^assets/minecraft/post_effect/(entity_outline|blur|transparency)"); do
  echo "----- $f"
  unzip -p "$J" "$f"
  echo
done
echo "----- FontTexture · RenderPipelines (상수 · 필드 이름)"
"$JAVA_HOME/bin/javap" -constants -cp "$J" net.minecraft.client.gui.font.FontTexture 2>&1 | grep -E "static final|class" | head -20
P=$(find .gradle "$HOME/.gradle" -name "*.jar" -path "*26.3*" 2>/dev/null | head -20)
for f in $P; do
  if unzip -Z1 "$f" 2>/dev/null | grep -q "net/minecraft/client/renderer/RenderPipelines.class"; then
    "$JAVA_HOME/bin/javap" -cp "$f" net.minecraft.client.renderer.RenderPipelines 2>&1 | grep -E "ENTITY|ITEM|TEXT|OUTLINE|GLOW|static" | head -60
    break
  fi
done
echo "[shader-probe] done"
