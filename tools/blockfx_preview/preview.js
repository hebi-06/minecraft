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
  beam(from, to, thick, color, life) {
    const d = to.clone().sub(from), len = d.length(); if (len < 0.05) return;
    const mid = from.clone().lerp(to, 0.5), rot = along(d), L = Math.max(2, life), t0 = NOW;
    const o = box(mid, rot, thick, len, thick, color), k = box(mid, rot, thick * 0.38, len * 1.01, thick * 0.38, WHITE);
    at(t0 + 1, () => { o.stretch(0, len * 0.96, 0, L - 1); k.stretch(0, len, 0, L - 1); });
    at(t0 + L + 1, () => { o.remove(); k.remove(); });
  },
  arc(c, u, v, r, a0, a1, segs, thick, color, sweep, life) {
    const n = Math.max(2, segs), da = (a1 - a0) / n, nrm = u.clone().cross(v).normalize(), t0 = NOW;
    for (let i = 0; i < n; i++) {
      const a = a0 + da * (i + 0.5);
      const radial = u.clone().multiplyScalar(Math.cos(a)).add(v.clone().multiplyScalar(Math.sin(a)));
      let tan = u.clone().multiplyScalar(-Math.sin(a)).add(v.clone().multiplyScalar(Math.cos(a))); if (da < 0) tan.negate();
      const p = c.clone().add(radial.clone().multiplyScalar(r));
      const w = thick * (0.3 + 0.7 * Math.sin(Math.PI * (i + 0.5) / n)), segLen = Math.abs(da) * r * 1.18;
      const rot = basis(radial, tan, radial.clone().cross(tan).normalize());
      at(t0 + Math.floor(sweep * i / n), () => {
        const o = box(p, rot, w * 0.2, segLen * 0.4, w * 0.35, color);
        const k = box(p.clone().add(radial.clone().multiplyScalar(w * 0.15)), rot, w * 0.08, segLen * 0.4, w * 0.15, WHITE);
        o.stretch(w, segLen, w * 0.55, 1); k.stretch(w * 0.32, segLen * 1.02, w * 0.3, 1);
        at(NOW + 2, () => { o.stretch(0, segLen * 0.9, 0, life); k.stretch(0, segLen, 0, life); });
        at(NOW + life + 2, () => { o.remove(); k.remove(); });
      });
    }
  },
  burst(c, size, color, core, life) {
    const L = Math.max(4, life), t0 = NOW;
    const r1 = new THREE.Quaternion().setFromEuler(new THREE.Euler(rand() * 3, rand() * 3, rand() * 3));
    const r2 = new THREE.Quaternion().setFromEuler(new THREE.Euler(rand() * 3, rand() * 3, rand() * 3));
    const r3 = new THREE.Quaternion().setFromEuler(new THREE.Euler(0.785, 0.615, 0));
    const a = box(c, r1, 0.05, 0.05, 0.05, color), b = box(c, r2, 0.05, 0.05, 0.05, color), w = box(c, r3, 0.05, 0.05, 0.05, core);
    at(t0 + 1, () => { a.stretch(size * 0.85, size * 0.85, size * 0.85, 3); b.stretch(size * 0.7, size * 0.7, size * 0.7, 3); w.stretch(size * 0.55, size * 0.55, size * 0.55, 2); });
    at(t0 + 4, () => {
      a.turn(r1.clone().multiply(new THREE.Quaternion().setFromAxisAngle(UP, 1.2)), L - 3).stretch(0, 0, 0, L - 3);
      b.turn(r2.clone().multiply(new THREE.Quaternion().setFromAxisAngle(V3(1, 0, 0), -1.0)), L - 3).stretch(0, 0, 0, L - 3);
      w.stretch(0, 0, 0, Math.max(2, Math.floor(L / 2)));
    });
    at(t0 + L + 2, () => { a.remove(); b.remove(); w.remove(); });
    for (let i = 0; i < 6; i++) {
      const d = V3(rand() - 0.5, rand() - 0.35, rand() - 0.5).normalize();
      BlockFx.beam(c.clone().add(d.clone().multiplyScalar(size * 0.2)), c.clone().add(d.clone().multiplyScalar(size * (0.9 + rand() * 0.5))), size * 0.07, color, Math.max(3, L - 2));
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
  flame(foot, h, outer, inner, life) {
    const L = Math.max(4, life), t0 = NOW, w = h * 0.38;
    const r = new THREE.Quaternion().setFromEuler(new THREE.Euler(0.615, rand() * Math.PI, 0.785));
    const o = box(foot.clone().add(V3(0, w * 0.5, 0)), r, w, w, w, outer), k = box(foot.clone().add(V3(0, w * 0.45, 0)), r, w * 0.55, w * 0.55, w * 0.55, inner);
    const r2 = r.clone().multiply(new THREE.Quaternion().setFromAxisAngle(UP, 1.4));
    at(t0 + 1, () => { o.move(foot.clone().add(V3(0, h, 0)), L - 1).turn(r2, L - 1).stretch(0, 0, 0, L - 1); k.move(foot.clone().add(V3(0, h * 0.75, 0)), L - 1).turn(r2, L - 1).stretch(0, 0, 0, Math.max(2, L - 3)); });
    at(t0 + L + 1, () => { o.remove(); k.remove(); });
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
const CRIMSON = [1.0, 0.34, 0.24], CORE = [0.92, 0.96, 1.0], FIRE = [1.0, 0.42, 0.2], FIRE_CORE = [1.0, 0.9, 0.72], TRACE = [0.42, 0.78, 1.0], BLADE = [0.9, 0.95, 1.0], GEM = [1.0, 0.3, 0.22];
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
