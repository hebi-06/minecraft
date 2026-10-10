#!/usr/bin/env python3
"""Molang 대조 시험 자료: 애드온 파티클 식 (assets/mmorpg/addon_particles.json) 을 이 파일의 따로 짠 계산기로 풀어
core/src/test/resources/molang_cases.json 에 (식 · 변수 값 · 답) — core 의 MolangTest 가 자바 Molang 과 비교.

  python3 tools/molang_cases.py

math.random 이 든 식은 값이 늘 달라서 읽기만 확인 (답 없음).
"""
import json
import math
import os
import random
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFS = os.path.join(ROOT, "src", "main", "resources", "assets", "mmorpg", "addon_particles.json")
OUT = os.path.join(ROOT, "core", "src", "test", "resources", "molang_cases.json")

TOKEN = re.compile(r"\s*(?:(\d+\.?\d*|\.\d+)|([A-Za-z_][\w.]*)|(&&|\|\||==|!=|<=|>=|[-+*/()<>!?:,]))")


def tokens(s):
    out = []
    pos = 0
    s = s.strip()
    while pos < len(s):
        m = TOKEN.match(s, pos)
        if not m or m.end() == pos:
            raise ValueError(f"bad token at {pos}: {s!r}")
        out.append(m.group(1) or m.group(2) or m.group(3))
        pos = m.end()
        while pos < len(s) and s[pos].isspace():
            pos += 1
    return out


class Calc:
    """재귀 하강 계산기 (자바 Molang 과 따로 짬): 읽으면서 바로 값을 냄"""

    def __init__(self, s, vals):
        self.t = tokens(s)
        self.i = 0
        self.vals = vals

    def peek(self):
        return self.t[self.i] if self.i < len(self.t) else None

    def take(self, x=None):
        tok = self.peek()
        if x is not None and tok != x:
            raise ValueError(f"expected {x} got {tok}")
        self.i += 1
        return tok

    def run(self):
        v = self.cond()
        assert self.peek() is None, self.t[self.i:]
        return v

    def cond(self):
        c = self.lor()
        if self.peek() == "?":
            self.take()
            a = self.cond()
            self.take(":")
            b = self.cond()
            return a if c != 0 else b
        return c

    def lor(self):
        v = self.land()
        while self.peek() == "||":
            self.take()
            w = self.land()
            v = 1.0 if (v != 0 or w != 0) else 0.0
        return v

    def land(self):
        v = self.eq()
        while self.peek() == "&&":
            self.take()
            w = self.eq()
            v = 1.0 if (v != 0 and w != 0) else 0.0
        return v

    def eq(self):
        v = self.rel()
        while self.peek() in ("==", "!="):
            op = self.take()
            w = self.rel()
            v = 1.0 if ((v == w) if op == "==" else (v != w)) else 0.0
        return v

    def rel(self):
        v = self.add()
        while self.peek() in ("<", "<=", ">", ">="):
            op = self.take()
            w = self.add()
            v = 1.0 if {"<": v < w, "<=": v <= w, ">": v > w, ">=": v >= w}[op] else 0.0
        return v

    def add(self):
        v = self.mul()
        while self.peek() in ("+", "-"):
            op = self.take()
            w = self.mul()
            v = v + w if op == "+" else v - w
        return v

    def mul(self):
        v = self.un()
        while self.peek() in ("*", "/"):
            op = self.take()
            w = self.un()
            v = v * w if op == "*" else (0.0 if w == 0 else v / w)
        return v

    def un(self):
        if self.peek() == "-":
            self.take()
            return -self.un()
        if self.peek() == "+":
            self.take()
            return self.un()
        if self.peek() == "!":
            self.take()
            return 1.0 if self.un() == 0 else 0.0
        return self.prim()

    def prim(self):
        tok = self.take()
        if tok == "(":
            v = self.cond()
            self.take(")")
            return v
        if tok[0].isdigit() or tok[0] == ".":
            return float(tok)
        name = tok.lower()
        if name.startswith("math."):
            fn = name[5:]
            args = []
            self.take("(")
            if self.peek() != ")":
                args.append(self.cond())
                while self.peek() == ",":
                    self.take()
                    args.append(self.cond())
            self.take(")")
            return {
                "floor": lambda a: float(math.floor(a)),
                "mod": lambda a, b: 0.0 if b == 0 else math.fmod(a, b),
                "clamp": lambda x, lo, hi: lo if x < lo else hi if x > hi else x,
                "min": min,
                "max": max,
                "sin": lambda d: math.sin(math.radians(d)),
                "cos": lambda d: math.cos(math.radians(d)),
                "abs": abs,
            }[fn](*args)
        if name.startswith("variable.") or name.startswith("v."):
            return float(self.vals.get(name.split(".", 1)[1], 0.0))
        raise ValueError(f"unknown name {tok}")


def exprs_of(o, out):
    if isinstance(o, str):
        out.add(o)
    elif isinstance(o, list):
        for x in o:
            exprs_of(x, out)
    elif isinstance(o, dict):
        for k, v in o.items():
            if k in ("sprite", "material", "facing", "type", "dir") and isinstance(v, str):
                continue
            exprs_of(v, out)


def main():
    defs = json.load(open(DEFS, encoding="utf-8"))
    exprs = set()
    for d in defs["particles"].values():
        exprs_of(d, exprs)
    rng = random.Random(20261010)
    cases = []
    for e in sorted(exprs):
        names = sorted(set(re.findall(r"variable\.([A-Za-z_][\w.]*)", e)))
        if "math.random" in e:
            cases.append({"expr": e, "vars": {}, "expect": None})
            continue
        for k in range(6):
            vals = {}
            for n in names:
                if n in ("frames", "loop", "cell", "frame", "kind", "seg"):
                    vals[n] = float(rng.randint(0, 6))
                elif n == "progress":
                    vals[n] = rng.choice([-1.0, 0.0, 0.3, 0.75, 1.0, 1.5])
                else:
                    vals[n] = round(rng.uniform(-0.5, 3.0), 4) if k % 2 else round(rng.uniform(0, 2), 4)
            cases.append({"expr": e, "vars": vals, "expect": Calc(e, vals).run()})
    # 손으로 정한 것 (우선순위 · 삼항 · 비교 · 0 으로 나눔)
    hand = [
        ("1 + 2 * 3", {}, 7.0), ("(1 + 2) * 3", {}, 9.0), ("-2 * -3", {}, 6.0), ("10 - 4 - 3", {}, 3.0), ("8 / 4 / 2", {}, 1.0),
        ("1 < 2 ? 5 : 6", {}, 5.0), ("0 ? 1 : 0 ? 2 : 3", {}, 3.0), ("1 ? 0 ? 7 : 8 : 9", {}, 8.0), ("!0 + !5", {}, 1.0),
        ("2 > 1 && 3 > 4 || 1", {}, 1.0), ("2 == 2", {}, 1.0), ("2 != 2", {}, 0.0), ("5 / 0", {}, 0.0), ("math.mod(-7, 3)", {}, -1.0),
        ("math.clamp(5, 0, 1)", {}, 1.0), ("math.sin(90)", {}, 1.0), ("math.cos(180)", {}, -1.0), ("variable.unknown_name * 3 + 1", {}, 1.0),
        ("v.size * 2", {"size": 1.5}, 3.0), ("variable.color.r + variable.dir.z", {"color.r": 0.25, "dir.z": -1.0}, -0.75),
        ("math.floor(-1.5)", {}, -2.0), ("math.max(1, 2) - math.min(-1, 4)", {}, 3.0), (".5 + 1.25", {}, 1.75),
    ]
    for e, vals, want in hand:
        assert abs(Calc(e, vals).run() - want) < 1e-12, (e, Calc(e, vals).run(), want)
        cases.append({"expr": e, "vars": vals, "expect": want})
    with open(OUT, "w", encoding="utf-8") as fh:
        json.dump({"source": "tools/molang_cases.py (애드온 파티클 식 + 손으로 정한 것)", "cases": cases}, fh, ensure_ascii=False, indent=0)
        fh.write("\n")
    print(f"{len(exprs)} expressions, {len(cases)} cases → {os.path.relpath(OUT, ROOT)}")


if __name__ == "__main__":
    main()
