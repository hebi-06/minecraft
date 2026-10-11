#!/usr/bin/env bash
# CI 로그를 ci-logs 브랜치 (작업 브랜치는 ci-logs-<브랜치>) 에 올림 (실행마다 덮어씀): 단계별 결과 SUMMARY.txt + 각 단계 로그 (긴 것은 앞 · 뒤만)
# GitHub API 로 바로 읽을 수 있게 (contents/<파일>?ref=ci-logs)
set -u
OUT=/tmp/ci-logs
rm -rf "$OUT" && mkdir -p "$OUT"
{
  echo "run: ${GITHUB_RUN_ID:-?}  attempt: ${GITHUB_RUN_ATTEMPT:-?}"
  echo "commit: ${GITHUB_SHA:-?}"
  # 일마다 단계 결과 (build 일 · client 일의 묶음마다 — ci-out/outcome-*.txt)
  if ls ci-out/outcome-*.txt > /dev/null 2>&1; then
    cat ci-out/outcome-*.txt
  else
    echo "build: (no outcome — build job did not finish)"
  fi
  echo "client (all groups): ${OUT_CLIENT_ALL:-?}"
} > "$OUT/SUMMARY.txt"
for f in ci-out/*; do
  [ -d "$f" ] && continue
  [ -f "$f" ] || continue
  name=$(basename "$f")
  lines=$(wc -l < "$f")
  if [ "$lines" -gt 4000 ]; then
    { head -n 400 "$f"; echo "… ($lines lines, middle cut) …"; tail -n 3000 "$f"; } > "$OUT/$name"
  else
    cp "$f" "$OUT/$name"
  fi
done
# 화면 시험 사진 (shots/*.png)
if [ -d ci-out/shots ]; then
  mkdir -p "$OUT/shots"
  cp ci-out/shots/* "$OUT/shots/" 2>/dev/null || true
fi
# 오류 줄만 모아서 (컴파일 오류 · 실패한 시험 · 예외)
grep -n -E "error:|warning: \[|FAILED|Exception|Caused by|GameTest|failed|Failed|FAIL" ci-out/*.txt ci-out/*.log 2>/dev/null | head -n 600 > "$OUT/ERRORS.txt" || true
# 브랜치: main · wip · mc-* 는 ci-logs, 다른 작업 브랜치는 ci-logs-<브랜치> (동시에 돈 실행이 서로 덮어쓰지 않게)
BR=ci-logs
case "${GITHUB_REF_NAME:-}" in
  main | wip | mc-* | "") ;;
  *) BR="ci-logs-${GITHUB_REF_NAME//\//-}" ;;
esac
cd "$OUT"
git init -q -b "$BR"
git config user.name "ci"
git config user.email "ci@users.noreply.github.com"
git add -A
git commit -q -m "logs: run ${GITHUB_RUN_ID:-?} (${GITHUB_SHA:-?})"
git push -q -f "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" "$BR" && echo "logs pushed to $BR"
