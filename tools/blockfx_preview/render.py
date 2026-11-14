#!/usr/bin/env python3
"""2026-11-15 BlockFx 로컬 미리보기 렌더 (사용자 "로컬 미리보기를 만들어봐"): 장면을 틱마다 (20 fps) 그려 out/<장면>.mp4 · out/<장면>_sheet.png.
CI (약 15분) 대신 몇 초 — 크기 · 밀도 · 겹 · 색을 여기서 맞춘 뒤 자바 (BlockFx · PhantomArcherSkills) 로 옮김.

  cd tools/blockfx_preview && npm install          # 처음 한 번 (three)
  python3 tools/blockfx_preview/render.py burst blades
  python3 tools/blockfx_preview/render.py "volley&cam=1.2,2.3,4.6,-0.6,1.5,0"   # 카메라 바꿔 보기 (자리 · 보는 곳)
"""
import http.server
import os
import re
import shutil
import socketserver
import subprocess
import sys
import tempfile
import threading

from playwright.sync_api import sync_playwright

D = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(D, "out")


class Quiet(http.server.SimpleHTTPRequestHandler):
    def __init__(self, *a, **k):
        super().__init__(*a, directory=D, **k)

    def log_message(self, *a):
        pass


def main(names):
    os.makedirs(OUT, exist_ok=True)
    srv = socketserver.TCPServer(("127.0.0.1", 0), Quiet)
    port = srv.server_address[1]
    threading.Thread(target=srv.serve_forever, daemon=True).start()
    with sync_playwright() as p:
        b = p.chromium.launch(args=["--use-gl=angle", "--use-angle=swiftshader", "--enable-unsafe-swiftshader", "--ignore-gpu-blocklist"])
        pg = b.new_page(viewport={"width": 1280, "height": 720})
        logs = []
        pg.on("pageerror", lambda e: logs.append("ERR " + str(e)))
        for n in names:
            pg.goto(f"http://127.0.0.1:{port}/index.html?s={n}")  # n 에 &cam=x,y,z,lx,ly,lz · &smoke=0 처럼 덧붙일 수 있음
            n = re.sub(r"[^A-Za-z0-9_-]+", "_", n)
            pg.wait_for_function("window.READY === true", timeout=60000)
            ticks = pg.evaluate("window.TICKS")
            tmp = tempfile.mkdtemp()
            for t in range(ticks):
                pg.evaluate(f"window.renderTick({t})")
                pg.locator("canvas").screenshot(path=os.path.join(tmp, f"f{t:04d}.png"))
            mp4 = os.path.join(OUT, f"{n}.mp4")
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-framerate", "20", "-i", os.path.join(tmp, "f%04d.png"), "-pix_fmt", "yuv420p", "-vf",
                            "scale=1280:-2", mp4], check=True)
            step = max(1, ticks // 12)
            sel = "+".join(f"eq(n\\,{i})" for i in range(0, ticks, step)[:12])
            subprocess.run(["ffmpeg", "-v", "error", "-y", "-i", mp4, "-vf", f"select='{sel}',scale=480:-1,tile=4x3", "-frames:v", "1",
                            os.path.join(OUT, f"{n}_sheet.png")], check=True)
            shutil.rmtree(tmp)
            print(mp4, ticks, "ticks")
        if logs:
            print("\n".join(logs[-6:]))
        b.close()
    srv.shutdown()


if __name__ == "__main__":
    main(sys.argv[1:] or ["burst"])
