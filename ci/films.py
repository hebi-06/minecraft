#!/usr/bin/env python3
"""0.48.2 스킬 영상: 화면 시험 (only=film,…) 이 찍은 films/<번호>_<스킬>/f0000.png … + manifest.json → mp4 하나씩.

사진은 한 틱에 한 장 (RpgFilm — 앞 틱 뒤 그림을 읽음) 이지만 manifest 의 사진마다 틱 간격대로 길이를 줌 (틱이 건너뛰면 그만큼 길게)
(한 틱 = 0.05초 → 20fps 로 고르게). 사진은 지움 (CI 디스크).

  python3 ci/films.py <films 폴더> <내보낼 폴더> [이름 앞머리]
"""
import json
import os
import shutil
import subprocess
import sys


def main():
    src, out = sys.argv[1], sys.argv[2]
    prefix = sys.argv[3] if len(sys.argv) > 3 else ""
    os.makedirs(out, exist_ok=True)
    made = 0
    for name in sorted(os.listdir(src)):
        d = os.path.join(src, name)
        man = os.path.join(d, "manifest.json")
        if not os.path.isfile(man):
            continue
        with open(man, encoding="utf-8") as fh:
            m = json.load(fh)
        ticks = m.get("ticks") or []
        frames = sorted(f for f in os.listdir(d) if f.startswith("f") and f.endswith(".png"))
        n = min(len(ticks), len(frames))
        if n == 0:
            print("film %s: no frames" % name)
            continue
        lst = os.path.join(d, "list.txt")
        with open(lst, "w", encoding="utf-8") as fh:
            for i in range(n):
                dur = (ticks[i + 1] - ticks[i]) * 0.05 if i + 1 < n else 0.05
                fh.write("file '%s'\nduration %.3f\n" % (frames[i], max(0.05, min(dur, 0.5))))
            fh.write("file '%s'\n" % frames[n - 1])  # concat: 마지막 사진의 길이가 들어가게 한 번 더
        mp4 = os.path.join(out, prefix + name + ".mp4")
        tmp = mp4[:-4] + ".part.mp4"
        cmd = ["ffmpeg", "-y", "-loglevel", "error", "-f", "concat", "-safe", "0", "-i", lst, "-vf", "fps=20,format=yuv420p", "-c:v", "libx264",
               "-preset", "veryfast", "-crf", "22", "-movflags", "+faststart", tmp]
        r = subprocess.run(cmd)
        if r.returncode == 0:
            os.replace(tmp, mp4)
            shutil.copy(man, os.path.join(out, prefix + name + ".json"))
            made += 1
            gaps = [ticks[i + 1] - ticks[i] for i in range(n - 1)]
            print("film %s: %d frames, %d ticks, gaps %s" % (name, n, ticks[n - 1] - ticks[0], sorted(set(gaps))))
        else:
            print("film %s: ffmpeg failed (%d)" % (name, r.returncode))
        shutil.rmtree(d, ignore_errors=True)
    print("films: %d made" % made)


if __name__ == "__main__":
    main()
