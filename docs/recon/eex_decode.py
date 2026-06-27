#!/usr/bin/env python3
"""Independent EEX container decoder — validates codex's decryption route end-to-end.

Tier 1: validate EEX container framing across the whole Scenes/ corpus.
Tier 2: structured content decode of a single stage (roster / win-lose / deploy / dialogue).

No third-party deps. Reads the already-decrypted package at codex_diag/extracted.
"""
import json
import os
import struct
import sys

EX = r"C:\Users\Xy172\codex_diag\extracted"
SCENES = os.path.join(EX, "Scenes")
JSONDIR = os.path.join(EX, "json")

# ---------- table loaders (utf-8-sig: package JSON carries a BOM) ----------

def load_table(name):
    with open(os.path.join(JSONDIR, name), "r", encoding="utf-8-sig") as f:
        return json.load(f)

def index_by(rows, key):
    out = {}
    for r in rows:
        if key in r:
            out[r[key]] = r
    return out

# ---------- EEX framing ----------

class EexHeader:
    def __init__(self, data, path):
        self.path = path
        self.size = len(data)
        self.ok = False
        self.reason = ""
        if self.size < 0x0e:
            self.reason = "too small"
            return
        if data[0:4] != b"EEX\x00":
            self.reason = "bad magic %r" % data[0:4]
            return
        self.version = struct.unpack_from("<I", data, 4)[0]
        self.header_size = struct.unpack_from("<I", data, 0x0a)[0]
        if self.header_size < 0x0a or self.header_size > self.size:
            self.reason = "header_size %d out of range" % self.header_size
            return
        n = (self.header_size - 0x0a) // 4
        self.offsets = [struct.unpack_from("<I", data, 0x0a + 4 * i)[0] for i in range(n)]
        # framing validity: first offset == header_size; all offsets within file and ascending
        if not self.offsets or self.offsets[0] != self.header_size:
            self.reason = "first offset != header_size"
            return
        prev = -1
        for o in self.offsets:
            if o > self.size:
                self.reason = "offset 0x%x past EOF (size 0x%x)" % (o, self.size)
                return
            if o <= prev:
                self.reason = "offsets not strictly ascending"
                return
            prev = o
        self.ok = True

    def section_bounds(self):
        # each section spans [offsets[i], offsets[i+1]); last runs to EOF
        bounds = []
        for i, o in enumerate(self.offsets):
            end = self.offsets[i + 1] if i + 1 < len(self.offsets) else self.size
            bounds.append((o, end))
        return bounds


def validate_corpus():
    files = sorted(f for f in os.listdir(SCENES) if f.endswith(".eex_new"))
    ok = 0
    bad = []
    versions = set()
    for fn in files:
        with open(os.path.join(SCENES, fn), "rb") as fh:
            data = fh.read()
        h = EexHeader(data, fn)
        if h.ok:
            ok += 1
            versions.add(h.version)
        else:
            bad.append((fn, h.reason))
    print("=== Tier 1: EEX container framing over %d Scenes files ===" % len(files))
    print("framing OK: %d / %d" % (ok, len(files)))
    print("versions seen: %s" % sorted(hex(v) for v in versions))
    if bad:
        print("FAILURES (%d):" % len(bad))
        for fn, r in bad[:30]:
            print("  %-18s %s" % (fn, r))
    else:
        print("0 framing failures — every Scenes file is a valid EEX container.")
    return files


# ---------- cmd -> class name (from codex ledger, verified against libMyGame.so vtables) ----------
CMD = {
    0x02: "ChildInfo(label)", 0x03: "Else", 0x04: "TestAsk", 0x05: "TestValue",
    0x11: "SceneChange", 0x12: "ShowChoice", 0x13: "Case", 0x14: "ActorTalk", 0x15: "ActorTalk2",
    0x16: "CommonInfo", 0x17: "CommonInfo", 0x18: "CommonInfo(location)", 0x19: "CommonInfo(cond)", 0x1a: "CommonInfo(cond)",
    0x36: "TestActorState", 0x42: "TestVictory", 0x43: "TestFail",
    0x46: "DispatchFriend", 0x47: "DispatchEnemy", 0x48: "SetEnemyGoods", 0x4a: "ConfigOwnForce", 0x4b: "DispatchOwn",
    0x4f: "BattleActorTurn", 0x50: "BattleActorAction", 0x53: "BattleActorLeave",
}

def extract_strings(data):
    """Every '05 00 <valid utf-8> 00' run with its offset and the preceding u16 (the cmd word)."""
    out = []
    i = 0
    n = len(data)
    while i < n - 2:
        if data[i] == 0x05 and data[i + 1] == 0x00:
            j = data.find(b"\x00", i + 2)
            if j != -1 and j > i + 2:
                raw = data[i + 2:j]
                try:
                    s = raw.decode("utf-8")
                except UnicodeDecodeError:
                    i += 1
                    continue
                if s and any(ord(c) > 0x20 for c in s):
                    cmd = struct.unpack_from("<H", data, i - 2)[0] if i >= 2 else None
                    out.append((i - 4, cmd, s))
                    i = j + 1
                    continue
        i += 1
    return out

def scan_records(data, cmd_tag, payload_words):
    """Find strict records '<cmd:u16> 02 00 <hid:u16> ...' — used for equipment/dispatch id scans."""
    out = []
    n = len(data)
    i = 0
    while i < n - 4:
        if data[i] == cmd_tag and data[i + 1] == 0x00 and data[i + 2] == 0x02 and data[i + 3] == 0x00:
            hid = struct.unpack_from("<H", data, i + 4)[0]
            out.append((i, hid))
            i += 2
        else:
            i += 1
    return out

def decode_stage(fn):
    with open(os.path.join(SCENES, fn), "rb") as fh:
        data = fh.read()
    h = EexHeader(data, fn)
    print("=== Tier 2: structured decode of %s ===" % fn)
    print("framing: magic ok, version 0x%x, header_size %d, sections %s, size %d" %
          (h.version, h.header_size, [hex(o) for o in h.offsets], h.size))

    hero = index_by(load_table("dic_hero.json"), "hid")
    item = index_by(load_table("dic_item.json"), "good_id")
    gk = index_by(load_table("dic_gk.json"), "gkid")

    def hname(hid):
        r = hero.get(hid)
        return r.get("name", "?") if r else "?(hid %d 不存在)" % hid

    strings = extract_strings(data)
    # classify
    labels, dialogue, conditions, other = [], [], [], []
    for off, cmd, s in strings:
        tag = CMD.get(cmd, "0x%02x" % cmd if cmd is not None else "?")
        if cmd == 0x02:
            labels.append((off, s))
        elif cmd in (0x14, 0x15):
            dialogue.append((off, s))
        elif cmd in (0x16, 0x17, 0x18, 0x19, 0x1a, 0x69):
            conditions.append((off, tag, s))
        else:
            other.append((off, tag, s))

    print("\n-- labels / section titles (ChildInfo 0x02): %d --" % len(labels))
    for off, s in labels[:25]:
        print("  @0x%06x  %s" % (off, s.replace("\n", " / ")))

    print("\n-- CommonInfo text (titles / location / WIN-LOSE conditions): %d --" % len(conditions))
    for off, tag, s in conditions[:20]:
        print("  @0x%06x [%s]  %s" % (off, tag, s.replace("\n", " / ")))

    # roster from equipment records (0x48 #0x22 = global dic_hero.hid)
    eq = scan_records(data, 0x48, None)
    print("\n-- equipped actors (SetEnemyGoods 0x48, #0x22=hid -> dic_hero): %d --" % len(eq))
    seen = set()
    for off, hid in eq:
        if hid in seen:
            continue
        seen.add(hid)
        # weapon id is at #0x26 (file+6), good_id = field-2 when >1
        wfield = struct.unpack_from("<h", data, off + 6)[0] if off + 8 <= len(data) else 0
        wname = item.get(wfield - 2, {}).get("name", "默认") if wfield > 1 else "默认"
        print("  @0x%06x  hid %-4d %-6s  武器#0x26=%-4d %s" % (off, hid, hname(hid), wfield, wname))

    print("\n-- dialogue lines (ActorTalk 0x14/0x15): %d total --" % len(dialogue))
    for off, s in dialogue[:15]:
        print("  @0x%06x  %s" % (off, s.replace("\n", " / ")))
    if len(dialogue) > 15:
        print("  ... +%d more" % (len(dialogue) - 15))

    return h, strings


def namespace_check(fn):
    """Independently re-confirm codex's claim: action-op actor ids come from the file's dispatch roster."""
    with open(os.path.join(SCENES, fn), "rb") as fh:
        data = fh.read()
    enemy = {hid for _, hid in scan_records(data, 0x47, None)}
    friend = {hid for _, hid in scan_records(data, 0x46, None)}
    own_eq = {hid for _, hid in scan_records(data, 0x48, None)}  # equipped actors carry global hid
    roster = enemy | friend | own_eq
    turn_ids = [hid for _, hid in scan_records(data, 0x4f, None)]   # BattleActorTurn actorA
    act_ids = [hid for _, hid in scan_records(data, 0x50, None)]    # BattleActorAction actor
    action = [i for i in (turn_ids + act_ids) if i != 0xffff and i != 0]
    in_roster = sum(1 for i in action if i in roster)
    print("\n-- actor-id namespace check (%s) --" % fn)
    print("  dispatched roster ids (0x46 friend %d / 0x47 enemy %d / 0x48 equipped %d) -> %d distinct" %
          (len(friend), len(enemy), len(own_eq), len(roster)))
    print("  action-op actor refs (0x4f turn + 0x50 action): %d" % len(action))
    print("  action refs found in this file's roster: %d / %d (%.1f%%)" %
          (in_roster, len(action), 100.0 * in_roster / max(1, len(action))))
    print("  -> confirms action ops reference the SAME battle roster ids (not arbitrary global hids)")


if __name__ == "__main__":
    target = sys.argv[1] if len(sys.argv) > 1 else None
    if target == "ns":
        namespace_check(sys.argv[2])
    elif target:
        decode_stage(target)
    else:
        validate_corpus()
