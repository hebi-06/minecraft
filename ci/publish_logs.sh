#!/usr/bin/env bash
# CI 로그를 ci-logs 브랜치에 올림 (실행마다 덮어씀): 단계별 결과 SUMMARY.txt + 각 단계 로그 (긴 것은 앞 · 뒤만)
# GitHub API 로 바로 읽을 수 있게 (contents/<파일>?ref=ci-logs)
set -u
OUT=/tmp/ci-logs
rm -rf "$OUT" && mkdir -p "$OUT"
{
  echo "run: ${GITHUB_RUN_ID:-?}  attempt: ${GITHUB_RUN_ATTEMPT:-?}"
  echo "commit: ${GITHUB_SHA:-?}"
  echo "versions: ${OUT_VERSIONS:-?}"
  echo "core: ${OUT_CORE:-?}"
  echo "build: ${OUT_BUILD:-?}"
  echo "gametest: ${OUT_GAMETEST:-?}"
  echo "smoke: ${OUT_SMOKE:-?}"
} > "$OUT/SUMMARY.txt"
for f in ci-out/*; do
  [ -f "$f" ] || continue
  name=$(basename "$f")
  lines=$(wc -l < "$f")
  if [ "$lines" -gt 4000 ]; then
    { head -n 400 "$f"; echo "… ($lines lines, middle cut) …"; tail -n 3000 "$f"; } > "$OUT/$name"
  else
    cp "$f" "$OUT/$name"
  fi
done
# 화면 시험 사진 (shots/*.png) · 결과 (DONE 또는 FAILED)
if [ -d ci-out/shots ]; then
  mkdir -p "$OUT/shots"
  cp ci-out/shots/* "$OUT/shots/" 2>/dev/null || true
fi
# 오류 줄만 모아서 (컴파일 오류 · 실패한 시험 · 예외)
grep -h -n -E "error:|warning: \[|FAILED|Exception|Caused by|GameTest|failed|Failed|FAIL" ci-out/*.txt 2>/dev/null | head -n 600 > "$OUT/ERRORS.txt" || true
cd "$OUT"
git init -q -b ci-logs
git config user.name "ci"
git config user.email "ci@users.noreply.github.com"
git add -A
git commit -q -m "logs: run ${GITHUB_RUN_ID:-?} (${GITHUB_SHA:-?})"
git push -q -f "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" ci-logs && echo "logs pushed to ci-logs"
