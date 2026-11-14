// 2026-11-15 BlockFx 로컬 미리보기 (사용자 "로컬 미리보기를 만들어봐"): 게임의 BlockFx (src/main/java/mmorpg/fabric/vfx/BlockFx.java) 와 같은 꼴 —
// 무늬 없는 단색 상자를 축마다 늘이고 · 옮기고 · 돌리고 (표시 엔티티처럼 틱마다 곧은 보간) · 지움. 빛 번짐 없음 (바닐라와 같게), 면마다 마크 밝기
// (위 1 · 앞뒤 0.8 · 옆 0.6 · 아래 0.5). ?s=<장면> · window.renderTick(t) 로 틱 t 의 모습을 그림 (render.py 가 틱마다 찍어 영상으로).
import * as THREE from 'three';

const W = 1280, H = 720;
const q = new URLSearchParams(location.search);
const renderer = new THREE.WebGLRenderer({ antialias: true, preserveDrawingBuffer: true });
renderer.setSize(W, H);
renderer.setPixelRatio(1);
renderer.outputColorSpace = THREE.SRGBColorSpace;
document.body.appendChild(renderer.domElement);
const scene = new THREE.Scene();
const cam = new THREE.PerspectiveCamera(70, W / H, 0.05, 300);
let seed = 12345;
const rand = () => ((seed = (seed * 16807) % 2147483647) / 2147483647);
const V3 = (x, y, z) => new THREE.Vector3(x, y, z);
const UP = V3(0, 1, 0);

// ---------------------------------------------------------------- 시간 · 상자
let NOW = 0;
const EVENTS = [];
const BOXES = [];
function at(t, fn) { EVENTS.push({ t: Math.max(NOW, t), fn, id: EVENTS.length }); }
function runEvents(until) {
  for (;;) {
    EVENTS.sort((a, b) => a.t - b.t || a.id - b.id);
    const e = EVENTS.find(e => !e.done && e.t <= until);
    if (!e) break;
    e.done = true; NOW = e.t; e.fn();
  }
}
const FACE = [0.6, 0.6, 1.0, 0.5, 0.8, 0.8]; // +x −x +y −y +z −z (마크 블록 면 밝기)
const GEO = (() => {
  const g = new THREE.BoxGeometry(1, 1, 1);
  const cols = [];
  for (let f = 0; f < 6; f++) for (let v = 0; v < 4; v++) cols.push(FACE[f], FACE[f], FACE[f]);
  g.setAttribute('color', new THREE.Float32BufferAttribute(cols, 3));
  return g;
})();
class Box {
  constructor(color, pos, quat, scale) {
    this.born = NOW; this.dead = Infinity;
    this.tracks = { pos: [[NOW, NOW, pos.clone()]], quat: [[NOW, NOW, quat.clone()]], scale: [[NOW, NOW, scale.clone()]] };
    this.mesh = new THREE.Mesh(GEO, new THREE.MeshBasicMaterial({ color: new THREE.Color().setRGB(color[0], color[1], color[2], THREE.SRGBColorSpace), vertexColors: true })); // 게임의 물들임 색 = sRGB
    this.mesh.visible = false; scene.add(this.mesh); BOXES.push(this);
  }
  _set(k, v, dur) { this.tracks[k].push([NOW, NOW + Math.max(0, dur), v.clone()]); return this; }
  move(p, dur) { return this._set('pos', p, dur); }
  turn(qq, dur) { return this._set('quat', qq, dur); }
  stretch(sx, sy, sz, dur) { return this._set('scale', V3(sx, sy, sz), dur); }
  remove() { this.dead = Math.min(this.dead, NOW); }
  value(k, t) {
    const tr = this.tracks[k];
    let v = tr[0][2].clone();
    for (let i = 1; i < tr.length; i++) {
      const [t0, t1, to] = tr[i];
      if (t < t0) break;
      const u = t1 > t0 ? Math.min(1, (t - t0) / (t1 - t0)) : 1;
      if (k === 'quat') v = v.clone().slerp(to, u); else v = v.clone().lerp(to, u);
    }
    return v;
  }
  pose(t) {
    const on = t >= this.born && t < this.dead;
    this.mesh.visible = on;
    if (!on) return;
    this.mesh.position.copy(this.value('pos', t));
    this.mesh.quaternion.copy(this.value('quat', t));
    const s = this.value('scale', t);
    this.mesh.scale.set(Math.max(1e-4, s.x), Math.max(1e-4, s.y), Math.max(1e-4, s.z));
  }
}
function basis(x, y, z) { return new THREE.Quaternion().setFromRotationMatrix(new THREE.Matrix4().makeBasis(x, y, z)); }
function along(dir, side) {
  const y = dir.clone().normalize();
  let s = side || (Math.abs(y.y) < 0.9 ? UP.clone() : V3(1, 0, 0));
  let x = s.clone().sub(y.clone().multiplyScalar(s.dot(y)));
  if (x.length() < 1e-4) x = y.clone().cross(Math.abs(y.y) < 0.9 ? UP : V3(1, 0, 0));
  x.normalize();
  return basis(x, y, x.clone().cross(y).normalize());
}
const box = (c, rot, sx, sy, sz, color) => new Box(color, c, rot, V3(sx, sy, sz));
const WHITE = [1, 1, 1];

// ---------------------------------------------------------------- BlockFx (자바와 같은 꼴)
const BlockFx = {
  // 2026-11-15 빛 줄 2판: 세 마디 (머리 쪽 굵게 → 꼬리 가늘게) · 꼬리부터 사라짐 · 흰 심지
  beam(from, to, thick, color, life) {
    const d = to.clone().sub(from), len = d.length(); if (len < 0.05) return;
    const rot = along(d), L = Math.max(2, life), t0 = NOW, n = 3;
    for (let i = 0; i < n; i++) {
      const a = from.clone().lerp(to, i / n), b = from.clone().lerp(to, (i + 1) / n), mid = a.clone().lerp(b, 0.5), seg = len / n * 1.02;
      const th = thick * (0.45 + 0.55 * (i + 1) / n), life_i = Math.max(2, Math.round(L * (0.55 + 0.45 * (i + 1) / n)));
      const o = box(mid, rot, th, seg, th, color), k = box(mid, rot, th * 0.4, seg * 1.01, th * 0.4, WHITE);
      at(t0 + 1, () => { o.stretch(0, seg * 0.96, 0, life_i - 1); k.stretch(0, seg, 0, life_i - 1); });
      at(t0 + life_i + 1, () => { o.remove(); k.remove(); });
    }
  },
  // 2026-11-15 호 2판: 겉 색 (넓게) · 가운데 뜨거운 색 · 흰 심지 세 겹 초승달 · 그어진 차례대로 꼬리부터 사라짐
  arc(c, u, v, r, a0, a1, segs, thick, color, sweep, life) {
    const n = Math.max(4, segs), da = (a1 - a0) / n, t0 = NOW;
    const hot = [Math.min(1, color[0] * 0.4 + 0.6), Math.min(1, color[1] * 0.4 + 0.5), Math.min(1, color[2] * 0.4 + 0.3)];
    for (let i = 0; i < n; i++) {
      const a = a0 + da * (i + 0.5);
      const radial = u.clone().multiplyScalar(Math.cos(a)).add(v.clone().multiplyScalar(Math.sin(a)));
      let tan = u.clone().multiplyScalar(-Math.sin(a)).add(v.clone().multiplyScalar(Math.cos(a))); if (da < 0) tan.negate();
      const rot = basis(radial, tan, radial.clone().cross(tan).normalize());
      const w = thick * (0.25 + 0.75 * Math.pow(Math.sin(Math.PI * (i + 0.5) / n), 0.7)), segLen = Math.abs(da) * r * 1.12;
      const p = c.clone().add(radial.clone().multiplyScalar(r));
      const li = Math.max(2, Math.round(life * (0.45 + 0.55 * i / n)));
      at(t0 + Math.floor(sweep * i / n), () => {
        const o = box(p, rot, w * 0.3, segLen * 0.5, w * 0.25, color);
        const m = box(p.clone().add(radial.clone().multiplyScalar(w * 0.12)), rot, w * 0.2, segLen * 0.5, w * 0.2, hot);
        const k = box(p.clone().add(radial.clone().multiplyScalar(w * 0.22)), rot, w * 0.1, segLen * 0.5, w * 0.12, WHITE);
        o.stretch(w, segLen, w * 0.4, 1); m.stretch(w * 0.55, segLen * 1.01, w * 0.42, 1); k.stretch(w * 0.22, segLen * 1.02, w * 0.44, 1);
        at(NOW + 2, () => { o.stretch(0, segLen * 0.9, 0, li); m.stretch(0, segLen * 0.95, 0, Math.max(2, li - 1)); k.stretch(0, segLen, 0, Math.max(2, li - 2)); });
        at(NOW + li + 3, () => { o.remove(); m.remove(); k.remove(); });
      });
    }
  },
  // 2026-11-15 폭발 2판 (사용자 "너 하고 싶은거 다 해봐"): 번쩍 별 (흰 긴 상자 셋) · 흰 심 · 불덩이 상자 넷 (뜨거운 색 → 겉 색) · 가시 여덟 (겉 색 + 흰 심지 —
  // 뻗었다 끝으로 줄어듦) · 불티 열 (솟았다 떨어지며 작아짐)
  burst(c, size, color, core, life) {
    const L = Math.max(5, life), t0 = NOW;
    const hot = [Math.min(1, color[0] * 0.5 + core[0] * 0.5), Math.min(1, color[1] * 0.5 + core[1] * 0.5), Math.min(1, color[2] * 0.5 + core[2] * 0.5)];
    // 번쩍 별 (2 ~ 3틱)
    for (let k = 0; k < 3; k++) {
      const ax = [V3(1, 0.2, 0.1), V3(-0.2, 1, 0.3), V3(0.3, -0.1, 1)][k].normalize();
      const st = box(c, along(ax), size * 0.05, size * 0.4, size * 0.05, WHITE);
      st.stretch(size * 0.04, size * 2.2, size * 0.04, 1);
      at(t0 + 2, () => st.stretch(0, size * 2.8, 0, 2));
      at(t0 + 5, () => st.remove());
    }
    // 흰 심 · 불덩이
    const wq = new THREE.Quaternion().setFromEuler(new THREE.Euler(0.785, 0.615, 0));
    const w = box(c, wq, 0.05, 0.05, 0.05, core);
    w.stretch(size * 0.62, size * 0.62, size * 0.62, 2);
    at(t0 + 3, () => w.turn(wq.clone().multiply(new THREE.Quaternion().setFromAxisAngle(UP, 0.8)), 4).stretch(0, 0, 0, 5));
    at(t0 + 9, () => w.remove());
    for (let k = 0; k < 4; k++) {
      const rq = new THREE.Quaternion().setFromEuler(new THREE.Euler(rand() * 3, rand() * 3, rand() * 3));
      const off = V3(rand() - 0.5, rand() * 0.6 - 0.2, rand() - 0.5).multiplyScalar(size * 0.35);
      const sz = size * (0.55 + rand() * 0.35);
      const f = box(c.clone().add(off), rq, 0.05, 0.05, 0.05, k < 2 ? hot : color);
      at(t0 + 1, () => f.stretch(sz, sz, sz, 3).move(c.clone().add(off.clone().multiplyScalar(1.6)), 3));
      at(t0 + 4, () => f.turn(rq.clone().multiply(new THREE.Quaternion().setFromAxisAngle(V3(rand(), rand(), rand()).normalize(), 1.4)), L - 3)
        .move(c.clone().add(off.clone().multiplyScalar(2.2)).add(V3(0, size * 0.25, 0)), L - 3).stretch(0, 0, 0, L - 3));
      at(t0 + L + 2, () => f.remove());
    }
    // 가시 여덟 (뻗음 → 끝으로 줄어듦)
    for (let k = 0; k < 12; k++) {
      const d = V3(Math.cos(k * 0.524 + rand() * 0.3), rand() * 1.0 - 0.25, Math.sin(k * 0.524 + rand() * 0.3)).normalize();
      const len = size * (0.8 + rand() * 0.5);
      const mid0 = c.clone().add(d.clone().multiplyScalar(size * 0.3)), rot = along(d);
      const o = box(mid0, rot, size * 0.07, 0.05, size * 0.07, color), kk = box(mid0, rot, size * 0.028, 0.05, size * 0.028, WHITE);
      const mid1 = c.clone().add(d.clone().multiplyScalar(size * 0.3 + len * 0.5));
      o.move(mid1, 2).stretch(size * 0.07, len, size * 0.07, 2); kk.move(mid1, 2).stretch(size * 0.028, len * 1.02, size * 0.028, 2);
      const tip = c.clone().add(d.clone().multiplyScalar(size * 0.3 + len * 1.05));
      at(t0 + 3, () => { o.move(tip, L - 4).stretch(0, 0, 0, L - 4); kk.move(tip, L - 5).stretch(0, 0, 0, Math.max(2, L - 5)); });
      at(t0 + L + 1, () => { o.remove(); kk.remove(); });
    }
    // 불티 열 (솟았다 떨어짐)
    for (let k = 0; k < 10; k++) {
      const d = V3(rand() - 0.5, 0, rand() - 0.5).normalize(), e = size * (0.08 + rand() * 0.06);
      const pk = c.clone().add(d.clone().multiplyScalar(size * (0.6 + rand() * 0.8))).add(V3(0, size * (0.5 + rand() * 0.6), 0));
      const end = pk.clone().add(d.clone().multiplyScalar(size * 0.4)).add(V3(0, -size * (0.6 + rand() * 0.4), 0));
      const em = box(c, along(V3(rand(), rand(), rand())), e, e, e, k % 2 ? [1.0, 0.85, 0.4] : hot);
      em.move(pk, 4);
      at(t0 + 4, () => em.move(end, L).stretch(0, 0, 0, L));
      at(t0 + L + 5, () => em.remove());
    }
  },
  ring(c, r0, r1, segs, thick, color, life) {
    const n = Math.max(6, segs), L = Math.max(3, life), t0 = NOW;
    for (let i = 0; i < n; i++) {
      const a = Math.PI * 2 * i / n, radial = V3(Math.cos(a), 0, Math.sin(a)), tan = V3(-Math.sin(a), 0, Math.cos(a));
      const rot = basis(radial, tan, V3(0, -1, 0)), s0 = Math.PI * 2 * r0 / n * 1.12, s1 = Math.PI * 2 * r1 / n * 1.12;
      const p = box(c.clone().add(radial.clone().multiplyScalar(r0)), rot, thick, s0, thick * 0.35, color);
      at(t0 + 1, () => { p.move(c.clone().add(radial.clone().multiplyScalar(r1)), L - 1).stretch(0, s1, 0, L - 1); });
      at(t0 + L + 1, () => p.remove());
    }
  },
  ringHold(c, r, segs, thick, color, grow, hold) {
    const n = Math.max(8, segs), G = Math.max(1, grow), Hh = Math.max(1, hold), t0 = NOW;
    for (let i = 0; i < n; i++) {
      const a = Math.PI * 2 * i / n, radial = V3(Math.cos(a), 0, Math.sin(a)), tan = V3(-Math.sin(a), 0, Math.cos(a));
      const rot = basis(radial, tan, V3(0, -1, 0)), seg = Math.PI * 2 * r / n * 1.1;
      at(t0 + Math.floor(G * i / n), () => {
        const o = box(c.clone().add(radial.clone().multiplyScalar(r)), rot, 0.02, seg * 0.3, 0.02, color);
        const k = box(c.clone().add(radial.clone().multiplyScalar(r - thick * 0.15)).add(V3(0, thick * 0.12, 0)), rot, 0.01, seg * 0.3, 0.01, WHITE);
        o.stretch(thick, seg, thick * 0.3, 2); k.stretch(thick * 0.28, seg * 1.02, thick * 0.12, 2);
        at(t0 + G + Hh, () => { o.stretch(0, seg, 0, 4); k.stretch(0, seg, 0, 4); });
        at(t0 + G + Hh + 5, () => { o.remove(); k.remove(); });
      });
    }
  },
  shards(c, n, dist, size, color, life) {
    const L = Math.max(3, life), t0 = NOW;
    for (let i = 0; i < n; i++) {
      const d = V3(Math.cos(i * 2.399) * (0.6 + rand() * 0.4), rand() * 0.9 - 0.2, Math.sin(i * 2.399) * (0.6 + rand() * 0.4)).normalize();
      const rot = along(d), p = box(c.clone().add(d.clone().multiplyScalar(size * 0.3)), rot, size * 0.18, size, size * 0.1, color);
      const spin = rot.clone().multiply(new THREE.Quaternion().setFromAxisAngle(V3(1, 0, 0), rand() * 4 - 2));
      const to = c.clone().add(d.clone().multiplyScalar(dist * (0.7 + rand() * 0.6)));
      at(t0 + 1, () => { p.move(to, L - 1); p.turn(spin, L - 1); p.stretch(0, size * 0.3, 0, L - 1); });
      at(t0 + L + 1, () => p.remove());
    }
  },
  // 2026-11-15 불꽃 3판: 세운 긴 상자 넷 (아래 넓은 겉 색 → 위 좁은 속 색 · 흰 심) 이 흔들리며 솟고 가늘어짐 (y 축으로 늘인 기둥 — 마름모 상자는 상자로 보였음)
  flame(foot, h, outer, inner, life) {
    const L = Math.max(4, life), t0 = NOW;
    const hot = [Math.min(1, outer[0] * 0.5 + inner[0] * 0.5), Math.min(1, outer[1] * 0.5 + inner[1] * 0.5), Math.min(1, outer[2] * 0.5 + inner[2] * 0.5)];
    const layers = [[outer, 0.3, 0.55, 0.0], [hot, 0.22, 0.75, 0.12], [inner, 0.14, 0.9, 0.25], [WHITE, 0.07, 0.5, 0.15]];
    for (const [col, wk, hk, y0] of layers) {
      const w = h * wk, hh = h * hk, yaw = rand() * Math.PI;
      const r = new THREE.Quaternion().setFromAxisAngle(UP, yaw);
      const o = box(foot.clone().add(V3(0, h * y0 + hh * 0.5, 0)), r, w, hh, w, col);
      const sway = V3((rand() - 0.5) * h * 0.3, h * (y0 + 0.45) + hh * 0.5, (rand() - 0.5) * h * 0.3);
      at(t0 + 1, () => o.move(foot.clone().add(sway), L - 1).turn(new THREE.Quaternion().setFromAxisAngle(UP, yaw + 0.9), L - 1).stretch(0, hh * 1.3, 0, L - 1));
      at(t0 + L + 1, () => o.remove());
    }
  },
};

// ---------------------------------------------------------------- 세상 · 몸 · 칼
function world() {
  scene.background = new THREE.Color(0x9cc4ff);
  const g = new THREE.Mesh(new THREE.PlaneGeometry(200, 200), new THREE.MeshBasicMaterial({ color: 0x5f9a3a }));
  g.rotation.x = -Math.PI / 2; scene.add(g);
  for (let i = 0; i < 260; i++) { // 땅 결 (옅은 칸)
    const t = new THREE.Mesh(new THREE.PlaneGeometry(1, 1), new THREE.MeshBasicMaterial({ color: rand() < 0.5 ? 0x5a9436 : 0x67a242 }));
    t.rotation.x = -Math.PI / 2; t.position.set(Math.floor(rand() * 40 - 20) + 0.5, 0.002, Math.floor(rand() * 40 - 20) + 0.5); scene.add(t);
  }
}
const P = 1 / 16;
function stat(color, x, y, z, sx, sy, sz, rot) { const m = new THREE.Mesh(GEO, new THREE.MeshBasicMaterial({ color, vertexColors: true })); m.position.set(x, y, z); m.scale.set(sx, sy, sz); if (rot) m.rotation.set(...rot); scene.add(m); return m; }
function person(x, z, yaw) {
  const g = new THREE.Group(); scene.add(g); g.position.set(x, 0, z); g.rotation.y = yaw;
  const add = (c, px, py, pz, sx, sy, sz) => { const m = new THREE.Mesh(GEO, new THREE.MeshBasicMaterial({ color: c, vertexColors: true })); m.position.set(px * P, py * P, pz * P); m.scale.set(sx * P, sy * P, sz * P); g.add(m); };
  add(0x1f6e7a, -2, 6, 0, 4, 12, 4); add(0x1f6e7a, 2, 6, 0, 4, 12, 4); add(0xe08a3c, 0, 18, 0, 8, 12, 4); add(0xe9b38a, 0, 28, 0, 8, 8, 8); add(0x8a3a1c, 0, 31.5, 0, 8.4, 2, 8.4);
  add(0xe08a3c, -6, 20, -4, 4, 4, 12); add(0xe08a3c, 6, 20, -4, 4, 4, 12); add(0x6b4a2a, 6, 20, -12, 1.5, 18, 1.5);
  return g;
}
function draugr(x, z, yaw) {
  const g = new THREE.Group(); scene.add(g); g.position.set(x, 0, z); g.rotation.y = yaw;
  const add = (c, px, py, pz, sx, sy, sz) => { const m = new THREE.Mesh(GEO, new THREE.MeshBasicMaterial({ color: c, vertexColors: true })); m.position.set(px * P, py * P, pz * P); m.scale.set(sx * P, sy * P, sz * P); g.add(m); };
  add(0x3e4650, -2, 6, 0, 4, 12, 4); add(0x3e4650, 2, 6, 0, 4, 12, 4); add(0x56606c, 0, 18, 0, 9, 12, 5); add(0xc8c2b0, 0, 28, 0, 8, 8, 8); add(0x48505a, 0, 32.5, 0, 9, 3, 9);
  add(0x6ff0ff, -2, 28, -4.2, 1.5, 1.5, 1); add(0x6ff0ff, 2, 28, -4.2, 1.5, 1.5, 1); add(0x56606c, -6.5, 17, 0, 4, 12, 4); add(0x56606c, 6.5, 17, 0, 4, 12, 4);
  return g;
}
/** 투영 장검 (phantom_sword 꼴 — 칼 축 +y · 자루 끝 at · 길이 len): 상자 묶음 (같이 움직임) */
function sword(hilt, dir, len, side) {
  const rot = along(dir, side), s = len / 2.6;
  const parts = [ // [색, y 가운데, 크기 x(두께) y z(너비)] — 모델 픽셀 → 칸 (×s/16)
    [[0.24, 0.13, 0.1], -0.45, 1.3, 8.3, 1.3], [[0.88, 0.68, 0.28], 0.4, 2, 1.8, 10.8], [[0.62, 0.66, 0.74], 1.45, 1.6, 1.9, 5.2],
    [[0.91, 0.94, 0.98], 7.6, 1.2, 12.0, 6.8], [[0.91, 0.94, 0.98], 16.8, 1.2, 6.5, 5.6], [[0.91, 0.94, 0.98], 23.2, 1.2, 6.2, 3.8], [[0.95, 0.97, 1.0], 27.8, 1.16, 3.0, 1.6],
    [[1.0, 0.3, 0.22], 13.6, 1.44, 20.8, 1.3],
  ];
  const boxes = [];
  for (const [c, y, sx, sy, sz] of parts) {
    const off = V3(0, (y + 10.4) * s / 16, 0).applyQuaternion(rot);
    boxes.push({ b: box(hilt.clone().add(off), rot, sx * s / 16, sy * s / 16, sz * s / 16, c), y: (y + 10.4) * s / 16, sz: [sx * s / 16, sy * s / 16, sz * s / 16] });
  }
  return {
    to(h, d, dur, side2) { const r = along(d, side2 || side); for (const p of boxes) p.b.move(h.clone().add(V3(0, p.y, 0).applyQuaternion(r)), dur).turn(r, dur); return this; },
    remove() { for (const p of boxes) p.b.remove(); },
    reach: len,
  };
}

// ---------------------------------------------------------------- 장면 (자바 PhantomArcherSkills 의 틱을 따라감)
const CRIMSON = [0.92, 0.12, 0.14], CORE = [0.92, 0.96, 1.0], FIRE = [1.0, 0.36, 0.12], FIRE_CORE = [1.0, 0.86, 0.5], TRACE = [0.42, 0.78, 1.0], BLADE = [0.9, 0.95, 1.0], GEM = [1.0, 0.3, 0.22];
const PL = V3(3, 0, 0), FWD = V3(-1, 0, 0), RIGHT = V3(0, 0, -1);
function crowd() { person(PL.x, PL.z, Math.PI / 2); draugr(-3, 0, -Math.PI / 2); draugr(-4.6, -1.5, -Math.PI / 2); draugr(-4.4, 1.6, -Math.PI / 2); draugr(-6.2, 0.2, -Math.PI / 2); }
function trace(hilt, dir, len, life) {
  const d = dir.clone().normalize(), side = RIGHT.clone(), up = side.clone().cross(d).normalize();
  const guard = hilt.clone().add(d.clone().multiplyScalar(len * 0.22)), tip = hilt.clone().add(d.clone().multiplyScalar(len));
  const sa = guard.clone().add(d.clone().multiplyScalar(len * 0.05)).add(up.clone().multiplyScalar(len * 0.07)), sb = guard.clone().add(d.clone().multiplyScalar(len * 0.05)).add(up.clone().multiplyScalar(-len * 0.07));
  const th = 0.035 + len * 0.012;
  BlockFx.beam(hilt, guard, th, TRACE, life); BlockFx.beam(guard.clone().add(up.clone().multiplyScalar(len * 0.16)), guard.clone().add(up.clone().multiplyScalar(-len * 0.16)), th, TRACE, life);
  BlockFx.beam(sa, tip, th, TRACE, life); BlockFx.beam(sb, tip, th, TRACE, life);
}
function blast(atp, size) {
  BlockFx.burst(atp, size * 0.75, FIRE, FIRE_CORE, 12); BlockFx.burst(atp, size * 0.5, GEM, WHITE, 9);
  BlockFx.ring(V3(atp.x, 0.05, atp.z), size * 0.3, size * 1.25, 22, 0.32, FIRE, 11); BlockFx.shards(atp, 10, size * 1.6, size * 0.35, BLADE, 12);
}
const SCENES = {
  burst: { // 팬텀 버스트: 0 투영 (선 → 6 칼) · 14 쏨 → 15 꽂힘 → 25 터짐
    cam: [[5.2, 2.6, 4.2], [-1.4, 1.4, 0]], ticks: 46,
    run() {
      crowd();
      const hand = V3(PL.x - 0.4, 1.45, PL.z + 0.3), tgt = V3(-3, 1.25, 0);
      trace(hand.clone().add(FWD.clone().multiplyScalar(0.4)), FWD, 4.0, 8);
      let sw;
      at(6, () => { sw = sword(hand.clone().add(FWD.clone().multiplyScalar(0.4)), FWD, 4.0, UP); });
      at(14, () => {
        const d = tgt.clone().sub(hand).normalize(), hilt = tgt.clone().sub(d.clone().multiplyScalar(2.8));
        sw.to(hilt, d, 1);
        BlockFx.beam(hand, tgt, 0.16, CRIMSON, 7); BlockFx.beam(hand, tgt, 0.16, CRIMSON, 8);
      });
      at(15, () => { BlockFx.burst(tgt, 1.2 * 0.8, CRIMSON, WHITE, 6); BlockFx.shards(tgt, 6, 1.6, 0.45, BLADE, 8); });
      at(25, () => { sw.remove(); blast(tgt, 4 * 0.9); BlockFx.burst(tgt, 4.4 * 0.75, FIRE, FIRE_CORE, 12); });
    },
  },
  blades: { // 블레이드 레인: 예고 고리 · 하늘 고리 → 6 · 16 · 26 · 36 · 46 물결 → 56 터짐
    cam: [[6.5, 6.0, 8.5], [-2.0, 1.5, 0]], ticks: 70,
    run() {
      crowd();
      const c = V3(-3.5, 0, 0), r = 4.5, sky = 7.0;
      BlockFx.ringHold(V3(c.x, 0.04, c.z), r, 30, 0.24, CRIMSON, 5, 56); BlockFx.ring(V3(c.x, 0.05, c.z), r * 0.3, r, 24, 0.3, CRIMSON, 8);
      BlockFx.ringHold(V3(c.x, sky, c.z), r * 0.8, 26, 0.28, CRIMSON, 4, 48);
      for (let w = 0; w < 5; w++) {
        const hitAt = 6 + w * 10;
        at(hitAt - 7, () => {
          for (let i = 0; i < 6; i++) {
            const a = rand() * 6.28, rr = r * Math.sqrt(rand()), g = V3(c.x + Math.cos(a) * rr, 0, c.z + Math.sin(a) * rr);
            const tilt = V3((rand() - 0.5) * 0.35, -1, (rand() - 0.5) * 0.35).normalize();
            const top = g.clone().add(V3(0, sky, 0)), s = sword(top, tilt, 1.9, V3(1, 0, 0));
            at(NOW + 3, () => s.to(g.clone().sub(tilt.clone().multiplyScalar(1.9 * 0.62)), tilt, 4));
            at(56, () => s.remove());
          }
        });
        at(hitAt, () => BlockFx.ring(V3(c.x, 0.05, c.z), r * 0.85, r * 1.05, 24, 0.22, CRIMSON, 5));
      }
      at(56, () => { blast(V3(c.x, 0.8, c.z), r * 0.7); BlockFx.ring(V3(c.x, 0.05, c.z), r * 0.4, r + 1.2, 28, 0.4, FIRE, 10); });
    },
  },
  cross: { // 크로스 팽: 0 ~ 8 공중제비 · 쌍검이 바깥으로 휘어 8 에 X 자로 교차 → 18 돌아옴
    cam: [[2.5, 2.2, 5.5], [-1.8, 1.3, 0]], ticks: 34,
    run() {
      crowd();
      const tgt = V3(-3, 1.3, 0), start = V3(PL.x - 0.3, 1.2, PL.z);
      for (const sd of [-1, 1]) {
        const sw = sword(start.clone().add(RIGHT.clone().multiplyScalar(sd * 0.45)), FWD, 1.8, UP);
        let prev = null;
        for (let t = 2; t <= 8; t++) at(t, () => {
          const u = (t - 2) / 6, mid = start.clone().lerp(tgt, 0.5).add(RIGHT.clone().multiplyScalar(sd * 3.5)).add(V3(0, 0.6, 0));
          const p = start.clone().multiplyScalar((1 - u) * (1 - u)).add(mid.clone().multiplyScalar(2 * u * (1 - u))).add(tgt.clone().multiplyScalar(u * u));
          const d = FWD.clone().applyAxisAngle(UP, sd * t * 1.08);
          sw.to(p.clone().sub(d.clone().multiplyScalar(0.8)), d, 1, UP);
          if (prev) BlockFx.beam(prev, p, 0.14, CRIMSON, 6);
          prev = p;
        });
        at(20, () => sw.remove());
      }
      at(8, () => {
        const u = RIGHT.clone(), v = UP.clone(), size = 1.9, Rc = size * 2.2, span = size * 1.15 / Rc;
        for (let s2 = 0; s2 < 2; s2++) {
          const dd = (s2 === 0 ? u.clone().sub(v) : u.clone().negate().sub(v)).normalize();
          const n = (s2 === 0 ? u.clone().add(v) : u.clone().sub(v)).normalize();
          BlockFx.arc(tgt.clone().add(n.clone().multiplyScalar(Rc)), n.clone().negate(), dd, Rc, -span, span, 12, size * 0.26, CRIMSON, 3, 9);
        }
        BlockFx.burst(tgt, size * 0.45, CRIMSON, WHITE, 6);
      });
    },
  },
  ult: { // 소드 생추어리: 0 ~ 12 불꽃 고리가 번짐 · 칼 들판 → 24 ~ 74 물결 → 72 하늘로 → 86 내리꽂힘
    cam: [[11, 8.5, 12], [-1.5, 0.5, 0]], ticks: 100,
    run() {
      crowd();
      const c0 = V3(PL.x, 0, PL.z), R = 12, tgt = V3(-4, 0, 0);
      for (let i = 0; i <= 12; i += 2) {
        const ri = Math.max(1.5, R * i / 12);
        at(i, () => { BlockFx.ring(V3(c0.x, 0.06, c0.z), Math.max(0.5, ri - 1.8), ri, 30, 0.35, CRIMSON, 4); for (let qq = 0; qq < 8; qq++) { const a = rand() * 6.28; BlockFx.flame(V3(c0.x + Math.cos(a) * ri, 0, c0.z + Math.sin(a) * ri), 1.2 + rand() * 0.6, CRIMSON, FIRE_CORE, 9); } });
      }
      at(12, () => BlockFx.ringHold(V3(c0.x, 0.04, c0.z), R, 48, 0.32, CRIMSON, 4, 80));
      for (let t = 12; t < 86; t += 6) at(t, () => { for (let qq = 0; qq < 10; qq++) { const a = rand() * 6.28; BlockFx.flame(V3(c0.x + Math.cos(a) * R, 0, c0.z + Math.sin(a) * R), 1.4 + rand() * 0.7, CRIMSON, FIRE_CORE, 11); } });
      const field = [];
      for (let i = 0; i < 36; i++) {
        const rad = R * Math.sqrt((i + 0.5) / 36) * 0.95;
        at(3 + Math.floor(rad / R * 12) + (i % 3), () => {
          const a = rand() * 6.28, g = V3(c0.x + Math.cos(a) * rad, 0, c0.z + Math.sin(a) * rad), d = V3((rand() - 0.5) * 0.5, -1, (rand() - 0.5) * 0.5).normalize(), L = 1.8 + rand() * 0.8;
          field.push(sword(g.clone().sub(d.clone().multiplyScalar(L * 0.62)), d, L, V3(Math.cos(a), 0, Math.sin(a))));
        });
      }
      for (let w = 0; w < 6; w++) at(24 + w * 10, () => BlockFx.ring(V3(c0.x, 0.05, c0.z), R - 0.6, R + 0.4, 40, 0.25, CRIMSON, 5));
      at(86, () => { blast(V3(tgt.x, 1.0, tgt.z), 3.6); for (const f of field) f.remove(); });
    },
  },
};

const NAME = q.get('s') || 'burst';
const S = SCENES[NAME];
world();
S.run();
cam.position.set(...S.cam[0]); cam.lookAt(...S.cam[1]);
window.TICKS = S.ticks;
window.renderTick = (t) => {
  runEvents(t);
  for (const b of BOXES) b.pose(t);
  renderer.render(scene, cam);
  return true;
};
window.READY = true;
