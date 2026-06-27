#!/usr/bin/env python3
"""Fail-closed EEX -> CCZ content-pack converter (first slice: dialogue + win/lose objectives).

Design philosophy applied:
- Only emit ops with high-confidence decode; everything else -> explicit `unsupported` with file+offset.
- Offline generator: emits content-pack JSON the existing ContentJsonLoader/ScenarioRunner consume.
- Faithful decode (real legacy text), engine-own target model (CCZ r_script/s_script schema).
"""
import json
import os
import re
import struct
import sys

import eex  # reuse the validated decoder (framing + string extraction)

# ---- map decoded win/lose Chinese text -> CCZ condition ops (fail-closed: unknown clause -> unsupported) ----

def parse_conditions(block, hero_name_to_id):
    """Parse a '胜利条件 ... 失败条件 ...' CommonInfo block into (win[], lose[], unsupported[])."""
    win, lose, unsupported = [], [], []
    section = None
    for raw in block.split("\n"):
        line = raw.strip().lstrip("★☆·· ").strip().rstrip("。.！! ").strip()
        if not line:
            continue
        if line.startswith("胜利条件"):
            section = "win"; continue
        if line.startswith("失败条件"):
            section = "lose"; continue
        cond, why = map_clause(line, hero_name_to_id)
        if cond is None:
            unsupported.append({"clause": line, "reason": why})
        elif section == "win":
            win.append(cond)
        elif section == "lose":
            lose.append(cond)
    return win, lose, unsupported

def map_clause(line, name_to_id):
    """One objective clause -> a CCZ WinLoseCondition op, or (None, reason) if not confidently mappable.
    Fail-closed: only the conditions whose CCZ semantics MATCH are emitted; near-misses are NOT forced."""
    if "全灭敌军" in line or "全歼" in line:
        return {"type": "annihilate_enemies"}, None
    m = re.match(r"(.+?)死亡$", line)
    if m:
        nm = m.group(1)
        uid = name_to_id.get(nm)
        if uid:
            return {"type": "protect_alive", "unit": uid}, None
        return None, "unit '%s' has no global dic_hero id (likely battle-local roster id)" % nm
    if re.search(r"回合数超过(\d+)", line):
        # Turn DEADLINE (lose if turns exceed N = must finish by N). NOT CCZ SurviveTurns, which is the
        # opposite (survive-to-N = WIN). No CCZ turn-limit-lose condition exists -> fail-closed, do not force.
        return None, "turn-deadline lose ('%s'): CCZ SurviveTurns is survive-to-win (opposite); needs a TurnLimit condition" % line
    if "到达" in line or "村庄" in line or "占领" in line:
        return None, "area/tile objective ('%s'): needs script map-binding (deferred)" % line
    return None, "no confident CCZ mapping for '%s'" % line

# ---- dialogue extraction: a contiguous opening scene -> r_script dialogue ops ----

def opening_scene_dialogue(data, lo, hi):
    """ActorTalk lines whose record offset falls in [lo,hi) -> [{speaker,text}] in order."""
    out = []
    for off, cmd, s in eex.extract_strings(data):
        if cmd in (0x14, 0x15) and lo <= off < hi:
            speaker, text = split_speaker(s)
            out.append({"speaker": speaker, "text": text, "_off": off})
    out.sort(key=lambda r: r["_off"])
    return out

def split_speaker(s):
    """Legacy marks speaker with a leading '&Name\\n...'; lines without it are narration (speaker None)."""
    if s.startswith("&"):
        parts = s[1:].split("\n", 1)
        name = parts[0].strip()
        text = parts[1].strip() if len(parts) > 1 else ""
        return name, text.replace("\n", "")
    return None, s.replace("\n", "")

def convert_stage(fn, lo, hi, scene_name):
    with open(os.path.join(eex.SCENES, fn), "rb") as fh:
        data = fh.read()
    hero = eex.index_by(eex.load_table("dic_hero.json"), "hid")
    name_to_id = {r["name"]: ("hero_%d" % r["hid"]) for r in hero.values() if "name" in r}

    # win/lose: take the LAST objective block (final battle objective; the multi-phase first block is intermediate)
    blocks = [s for off, cmd, s in eex.extract_strings(data) if "胜利条件" in s and "失败条件" in s]
    win, lose, cond_unsupported = ([], [], [])
    if blocks:
        win, lose, cond_unsupported = parse_conditions(blocks[-1], name_to_id)

    dlg = opening_scene_dialogue(data, lo, hi)
    ops = [{"type": "scene_transition", "target": scene_name}]
    for d in dlg:
        line = {"text": d["text"]}
        if d["speaker"]:
            line = {"speaker": d["speaker"], "text": d["text"]}
        ops.append({"type": "dialogue", "line": line})

    pack = {
        "_provenance": {
            "tool": "eex_convert.py (fail-closed first slice)",
            "source_file": fn,
            "dialogue_offset_range": [hex(lo), hex(hi)],
            "decoded_from": "real legacy EEX (decrypted), faithful text",
        },
        "r_script": {"id": "daxingshan_intro_real", "ops": ops},
        "s_script_objectives": {"win": win, "lose": lose},
        "unsupported": cond_unsupported,
    }
    return pack

if __name__ == "__main__":
    # 大兴山 opening (借粮 scene): dialogue lives in S_00 around 0x4100..0x4700 (程远志/邓茂/百姓/黄巾兵)
    pack = convert_stage("S_00.eex_new", 0x4100, 0x4700, "幽州·大兴山")
    print(json.dumps(pack, ensure_ascii=False, indent=2))
