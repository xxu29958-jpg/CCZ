# MOD Script Recon

## 0. Scope / Guardrails

- Package root: `C:\Users\Xy172\codex_diag\extracted`
- Repo: `D:\ccz_tactics_engine`
- Date: 2026-06-26, Asia/Shanghai
- Git status before: empty output from `git status --short`
- Git status after: `?? docs/recon/` from `git status --short`; `git status --short --untracked-files=all` resolves that to `?? docs/recon/mod-script-recon.md`
- Files changed: `docs/recon/mod-script-recon.md` only. `git diff --name-only` is empty because the only changed file is untracked.

Guardrails observed:

- No converter, mapper, production code, fixture, app content, or existing docs were modified.
- All byte claims below include path + offset + hex evidence.
- Text readability and structure recoverability are judged separately.
- Existing importer coverage is based on source inspection, not memory.

Verdict summary: **GREEN - Structured and convertible**, with a required opcode-ledger phase before converter work. The script bytes are not opaque; they are an `EEX` binary container with stable headers, segment offsets, small integer op streams, and readable UTF-8 text. Unknown semantics remain, but they are enumerable instead of being mixed into an unrecoverable blob.

## 1. Package Inventory

### 1.1 Directory Tree Summary

| Directory | Files | Total size | Notes |
|---|---:|---:|---|
| `<root>` | 1 | 66,833 | `Hexzmap.e5`; map bundle candidate. |
| `globalres` | 18 | 2,142,641 | UI/weather assets, not script. |
| `img` | 7,650 | 220,469,920 | PNG/JPG/BMP sprites, portraits, maps. |
| `json` | 44 | 4,114,138 | Tables: `dic_hero`, `dic_job`, `dic_gk`, `dic_turn`, skills, terrain, etc. |
| `Musics` | 70 | 71,370,838 | MP3 BGM. |
| `Scenes` | 316 | 26,936,626 | Primary R/S script candidates: `R_*.eex_new`, `S_*.eex_new`. |
| `Sounds` | 121 | 4,663,310 | MP3 SFX. |
| `terrainJson` | 210 | 3,199,673 | `terrainMap_1.json` .. `terrainMap_210.json`. |

Second-level directories observed:

```text
globalres/ui
globalres/WeatherEffect
img/Achievements
img/Activity
img/EffectArea
img/Email
img/Face
img/Face2
img/Gate
img/HitArea
img/HM
img/Items
img/Logo
img/Meff
img/Mmap
img/Pmapobj
img/Seid
img/Skill
img/Skill_act
img/Skill_min
img/Suit
img/UnitS
```

### 1.2 Extension / Size Summary

| Extension | Files | Total size | Remarks |
|---|---:|---:|---|
| `.jpg` | 605 | 137,701,112 | Image assets; valid JPEG magic. |
| `.png` | 6,905 | 84,377,577 | Image assets; valid PNG magic. |
| `.mp3` | 186 | 75,965,399 | Music/SFX; mostly ID3/MP3 frame magic. |
| `.eex_new` | 316 | 26,936,626 | Script candidates; all start `45 45 58 00` (`EEX\0`). |
| `.json` | 254 | 7,313,811 | Tables and terrain maps; mostly direct UTF-8 JSON. |
| `.bmp` | 162 | 602,308 | BMP assets. |
| `.e5` | 1 | 66,833 | `Hexzmap.e5`; structured binary map bundle candidate. |
| `.ini` | 1 | 313 | Config-like text. |

### 1.3 Largest Files

| File | Size |
|---|---:|
| `Musics/39-AudioTrack 39.mp3` | 3,575,513 |
| `Musics/64-AudioTrack 64.mp3` | 3,401,317 |
| `Musics/38-AudioTrack 38.mp3` | 3,175,592 |
| `Musics/40-AudioTrack 40.mp3` | 3,014,403 |
| `img/HM/119.jpg` | 1,707,718 |
| `json/dic_hero.json` | 1,596,155 |
| `Musics/54-AudioTrack 54.mp3` | 1,585,259 |
| `Musics/47-AudioTrack 47.mp3` | 1,579,617 |
| `Musics/50-AudioTrack 50.mp3` | 1,479,307 |
| `Musics/51-AudioTrack 51.mp3` | 1,465,827 |
| `img/HM/124.jpg` | 1,444,849 |
| `Musics/48-AudioTrack 48.mp3` | 1,442,004 |
| `img/HM/137.jpg` | 1,378,100 |
| `img/HM/165.jpg` | 1,360,347 |
| `img/HM/181.jpg` | 1,321,286 |
| `Musics/27-AudioTrack 27.mp3` | 1,310,974 |
| `Musics/65-AudioTrack 65.mp3` | 1,300,943 |
| `Musics/66-AudioTrack 66.mp3` | 1,300,943 |
| `Musics/12-AudioTrack 12.mp3` | 1,297,808 |
| `img/HM/162.jpg` | 1,292,029 |

### 1.4 Existing Inspector Coverage

| Directory/File Class | Covered by current importer? | Evidence | Notes |
|---|---:|---|---|
| `json/*.json` | Scan/decrypt yes; map yes for selected tables | `LegacyApkInspector.kt:157-167` classifies `/json/` as `TABLE_JSON`; `LegacyContentImporter.kt:82-89` maps classes, units, skills, terrain. | Current mappers cover `dic_job`, `dic_hero`, `dic_skill`, `map_terrain`, optional `dic_jobWalk`, `dic_jobTerrain`. |
| `terrainJson/*.json` | Scan/decrypt yes; map yes for selected map | `LegacyApkInspector.kt:164`; `LegacyBattleBuilder.kt:118-140`; `LegacyPackGenerator.kt:81`. | Existing generated battle crops `terrainMap_1`; full 210-map import is not a general stage converter yet. |
| `Scenes/*.eex_new` | Scan/decrypt yes; semantic import no | `LegacyApkInspector.kt:166` classifies `/Scenes/` as `SCENE_SCRIPT`; `LegacyBattleBuilder.kt:87-88` says legacy `Scenes` scripts are separate effort. | This is the main uncovered runtime content: R/S scripts are not converted today. |
| `Hexzmap.e5` | Scan/decrypt yes; semantic import no | `LegacyApkInspector.kt:167` classifies `.e5` as `MAP_BUNDLE`. | Not currently parsed into native maps/events. |
| `img`, `Musics`, `Sounds`, `globalres` | Mostly not semantic importer scope | Existing importer only classifies potential BGT1 payload extensions: `.json`, `.eex_new`, `.e5`. | Assets are usable separately but not needed to decide event structure. |
| Native event target format | Yes, target model exists | `EventDto.kt:25-207` defines `r_scripts`, `s_scripts`, triggers, battle ops, win/lose conditions. | Converter can target existing native schema with fail-closed unknown op handling. |

### 1.5 Uncovered Directories

| Directory/File | Why it matters | Status |
|---|---|---|
| `Scenes/R_*.eex_new` | R-script/cutscene/choice/dialogue candidates. 159 files, ids 0..159 missing 4. | Structured EEX, readable UTF-8 text; opcode semantics not yet mapped. |
| `Scenes/S_*.eex_new` | S-script/battle event/deployment candidates. 157 files: ids 0..159 missing 74, 80, 99, 105, plus `S_1010`. | Structured EEX, readable UTF-8 text; stage-event semantics need opcode ledger. |
| `Hexzmap.e5` | Possible global/battle map bundle or map index. | Structured binary; not needed for first stage conversion because `terrainJson` maps are directly readable. |
| `json/dic_gk.json` | Stage catalog: 160 rows, first row `大兴山之战`. | Direct UTF-8 JSON, likely bridges stage ids/names to S/terrain files. |
| `json/dic_turn.json` | 1,870 rows of hero appearance/turn metadata. | Direct UTF-8 JSON; can support stage/unit appearance metadata but not sufficient alone for event conversion. |

## 2. Candidate Stage / Script Files

| Candidate | Path | Suffix | Size | Suspected Role | Evidence | Status |
|---|---|---:|---:|---|---|---|
| Stage catalog | `json/dic_gk.json` | `.json` | 22,151 | Campaign/stage list | 160 rows; row 1 is `{"gkid":1,"gkname":"大兴山之战",...}`. | Usable, direct JSON. |
| Unit appearance table | `json/dic_turn.json` | `.json` | 166,355 | Hero stage/turn metadata | 1,870 rows; keys `hid,name,imgid,turn`. | Usable support table; semantics limited. |
| Battle maps | `terrainJson/terrainMap_*.json` | `.json` | 3,199,673 total | Map grids | 210 files, ids 1..210 no gaps; `terrainMap_1` is 23x16. | Usable, already partly mapped. |
| Battle/event scripts | `Scenes/S_*.eex_new` | `.eex_new` | 19,359,269 total | S scripts: battle setup, conditions, triggers, reinforcements, retreats, dialogue inside battle | `S_00` contains `大兴山`, `胜利条件`, `失败条件`, `全灭敌军`, `关羽撤退`, and structured op streams. | GREEN for structure alive; opcode mapping required. |
| Cutscene scripts | `Scenes/R_*.eex_new` | `.eex_new` | 7,577,357 total | R scripts: cutscene dialogue, choices, branches, global scenario events | `R_01` contains post-battle dialogue; `R_00` contains a choice prompt and options. | GREEN for structure alive; opcode mapping required. |
| Map bundle | `Hexzmap.e5` | `.e5` | 66,833 | Map bundle/index candidate | Header `4c 73 31 32` (`Ls12`) plus low-entropy structured bytes. | Not required for first converter path; unknown. |

No claim above relies only on file name. The decisive evidence is the EEX magic, stable header fields, segment offsets, readable script text at specific offsets, and repeated numeric op records.

## 3. Byte Shape Ledger

| File Class | Structure Fields | Text Fields | Evidence | Status |
|---|---|---|---|---|
| `Scenes/S_*.eex_new` | `EEX\0` magic; version `0x00000201`; S header-size always 22; two valid file offsets per file; repeated small integer op stream. | Readable UTF-8 Chinese dialogue, condition text, labels. | `S_00` offset `0x0`, `0x1a00`, `0x23100`, `0x27030`. | **Structured and convertible after opcode ledger**. |
| `Scenes/R_*.eex_new` | `EEX\0` magic; version `0x00000201`; variable header-size; offset table entries all within file; small integer op stream. | Readable UTF-8 dialogue, scene locations, choices/options. | `R_01` offset `0x0`, `0x0ee`; `R_00` offset `0x222`. | **Structured and convertible after opcode ledger**. |
| `json/*.json` | Direct JSON tables; row/key structure stable. | UTF-8 Chinese readable in current package. | `dic_hero` offset `0x27`; row counts below. | Usable; `dic_seid` strict JSON has literal newline inside string, not byte loss. |
| `terrainJson/*.json` | Direct JSON maps with `map_width`, `map_height`, `map_value`. | Mostly path/metadata text; UTF-8. | `terrainMap_1` offset `0x0`. | Usable; existing mapper already handles selected map. |
| `Hexzmap.e5` | Binary header `Ls12`, low entropy, many small values. | No obvious script text found in sample. | Offset `0x0`: `4c 73 31 32 ...`. | Structured but current role unknown; not a script blocker. |

### 3.1 Evidence Blocks - EEX Header

File class: `Scenes/S_*.eex_new`

- Sample path: `C:\Users\Xy172\codex_diag\extracted\Scenes\S_00.eex_new`
- Offset: `0x000000`
- Hex:

```text
45 45 58 00 01 02 00 00 00 00 16 00 00 00 28 6d
00 00 19 93 02 00 01 00 e9 02 02 00 05 00 e6 88
98 e5 89 8d e8 bf 87 e6 b8 a1 00 00 00 44 3e 44
```

- utf-8: binary header then `战前过渡`.
- gbk: header then mojibake (`鎴樺墠杩囨浮`).
- gb18030: same mojibake as GBK.
- latin1->gbk: same as GBK, not the correct path.
- latin1->gb18030: same as GB18030, not the correct path.
- Structure interpretation: bytes `45 45 58 00` = `EEX\0`; little-endian version-like field at `0x04` is `0x00000201`; little-endian header-size at `0x0a` is `22`; two little-endian offsets follow: `0x6d28` and `0x29319`, both inside the 168,758-byte file.
- Uncertainty: exact meaning of the two S offsets is not yet named.

Cross-file header check:

- All 157 S files have header-size `22`.
- R files have variable header-size values, with in-file offset tables. Examples:
  - `R_00`: header-size `46`, offsets include `0x2861c`, `0x28aad`, `0x294f4`.
  - `R_01`: header-size `30`, offsets include `0x0d5f`, `0x2672`, `0x4661`, `0x6108`.
  - `R_119`: header-size `14`, no offset-table entries, 143-byte minimal script.

Status: **clean structured header, not blob**.

### 3.2 Evidence Blocks - S Deployment / Setup Shape

File class: `Scenes/S_*.eex_new`

- Sample path: `C:\Users\Xy172\codex_diag\extracted\Scenes\S_00.eex_new`
- Offset: `0x001a00`
- Hex:

```text
00 00 00 01 00 02 00 05 00 48 45 58 e6 88 91 e5
86 9b e8 ae be e5 ae 9a 00 72 00 4b 00 04 00 00
00 00 00 04 00 00 00 00 00 04 00 03 00 00 00 2b
00 02 00 26 00 01 00 4b 00 04 00 01 00 00 00 04
00 00 00 00 00 04 00 04 00 00 00 2b 00 02 00 26
```

- utf-8: mixed structure plus `HEX我军设定`.
- gbk: structure plus mojibake.
- gb18030: structure plus mojibake.
- latin1->gbk: not correct.
- latin1->gb18030: not correct.
- Structure interpretation: immediately after label `HEX我军设定`, opcode-like value `0x004b` repeats with slot-like indexes `0,1,2,...` and coordinate-like small integers. In `S_00`, `terrainMap_1` is 23x16, while the observed setup values are in the 0..4 range and therefore map-bounds plausible. The same region repeats fixed records, not prose.
- Uncertainty: likely player deployment/setup slots, but exact field order and opcode name are not yet proven.

Status: **semi-structured, likely deployment/setup; needs opcode ledger before automatic mapping**.

### 3.3 Evidence Blocks - S AI / Equipment Shape

File class: `Scenes/S_*.eex_new`

- Sample path: `C:\Users\Xy172\codex_diag\extracted\Scenes\S_00.eex_new`
- Offset: `0x001bc0`
- Hex:

```text
00 01 00 02 00 05 00 41 49 e8 a3 85 e5 a4 87 00
50 00 48 00 02 00 b5 00 3b 00 00 00 49 00 00 00
3c 00 00 00 49 00 00 00 3d 00 3a 00 48 00 02 00
35 01 3b 00 00 00 49 00 00 00 3c 00 00 00 49 00
```

- utf-8: mixed structure plus `AI装备`.
- gbk / gb18030: text mojibake.
- latin1->gbk / latin1->gb18030: not correct.
- Structure interpretation: label `AI装备` followed by repeated `0x0048` records and field-like values (`0x00b5`, `0x0135`, `0x00e1`, `0x03b9` etc.), with repeating `0x003b/0x003c/0x003d/0x0049`. This is structured numeric data. It may contain equipment/unit metadata; not enough evidence to name every field.
- Uncertainty: exact semantic mapping to unit id, equipment id, AI state, or inventory is unknown.

Status: **semi-structured; not opaque**.

### 3.4 Evidence Blocks - S Conditions / Win-Lose Text

File class: `Scenes/S_*.eex_new`

- Sample path: `C:\Users\Xy172\codex_diag\extracted\Scenes\S_00.eex_new`
- Offset: `0x023100`
- Hex:

```text
00 00 19 00 05 00 e8 83 9c e5 88 a9 e6 9d a1 e4
bb b6 0a e2 98 85 c2 b7 e5 85 a8 e7 81 ad e6 95
8c e5 86 9b e3 80 82 0a 0a e5 a4 b1 e8 b4 a5 e6
9d a1 e4 bb b6 0a e2 98 86 c2 b7 e5 88 98 e5 a4
87 e6 ad bb e4 ba a1 e3 80 82 0a e2 98 86 c2 b7
e9 82 b9 e9 9d 96 e6 ad bb e4 ba a1 e3 80 82 0a
e2 98 86 c2 b7 e5 9b 9e e5 90 88 e6 95 b0 e8 b6
85 e8 bf 87 31 35 e3 80 82 00
```

- utf-8: `胜利条件\n★·全灭敌军。\n\n失败条件\n☆·刘备死亡。\n☆·邹靖死亡。\n☆·回合数超过15。`
- gbk / gb18030: mojibake.
- latin1->gbk / latin1->gb18030: mojibake.
- Structure interpretation: text clearly gives win/loss semantics. The surrounding structural values include `0x0019`, `0x001a`, and later small opcode/value sequences such as `35 00 01 00 e9 03 35 00 ...`. This shows the condition block is embedded in an op stream, not a freestanding text file.
- Uncertainty: exact numeric opcode for `annihilate_enemies`, `protect_alive`, and `survive_turns` is not proven from this sample alone.

Status: **win/loss semantics recoverable; structural opcode names still need mapping**.

### 3.5 Evidence Blocks - S Retreat / Event Shape

File class: `Scenes/S_*.eex_new`

- Sample path: `C:\Users\Xy172\codex_diag\extracted\Scenes\S_00.eex_new`
- Offset: `0x027030`
- Hex:

```text
00 00 00 09 00 02 00 05 00 e5 85 b3 e7 be bd e6
92 a4 e9 80 80 00 36 00 02 00 01 00 23 00 07 00
04 00 00 00 00 00 24 00 02 00 05 00 35 00 00 00
35 00 01 00 0c 00 00 00 83 00 14 00 05 00 26 e5
85 b3 e7 be bd 0a ef bc 88 e6 ad a4 e5 85 b3 e6
9f 90 e5 b9 b3 e7 94 9f e9 a6 96 e6 88 98 ef bc
8c 0a e7 ab 9f e7 84 b6 e8 b4 a5 e4 ba 8e e8 b4
bc e4 ba ba e4 b9 8b e6 89 8b
```

- utf-8: mixed structure plus `关羽撤退` and dialogue text.
- gbk / gb18030: mojibake for text.
- latin1->gbk / latin1->gb18030: not correct.
- Structure interpretation: event label `关羽撤退` is followed by small op/value fields (`0x0036`, `0x0023`, `0x0024`, `0x0035`, `0x0014`) and then dialogue. This is consistent with an event trigger/action block and a script/dialogue op.
- Uncertainty: exact trigger condition and battle op mapping is not yet proven.

Status: **trigger/event structure alive; opcode semantics unknown**.

### 3.6 Evidence Blocks - R Dialogue / Location Shape

File class: `Scenes/R_*.eex_new`

- Sample path: `C:\Users\Xy172\codex_diag\extracted\Scenes\R_01.eex_new`
- Offset: `0x0000ee`
- Hex:

```text
e8 93 9f e5 9f 8e 20 20 e5 88 ba e5 8f b2 e5 ba
9c 00 1c 00 09 00 04 00 05 00 00 00 14 00 05 00
26 e5 88 98 e7 84 89 0a e7 8e 84 e5 be b7 e6 9e
9c e7 84 b6 e4 b8 8d e8 b4 9f e6 88 91 e6 9c 9b
e3 80 82 00
```

- utf-8: `蓟城  刺史府` then op bytes then `&刘焉\n玄德果然不负我望。`
- gbk / gb18030: mojibake.
- latin1->gbk / latin1->gb18030: not correct.
- Structure interpretation: R scene text is not lost. Location and dialogue records are embedded in the op stream. The leading `&` appears in multiple dialogue text blocks before speaker names.
- Uncertainty: exact op value for scene transition/location vs dialogue needs mapping.

Status: **R dialogue convertible**.

### 3.7 Evidence Blocks - R Choice / Branch Shape

File class: `Scenes/R_*.eex_new`

- Sample path: `C:\Users\Xy172\codex_diag\extracted\Scenes\R_00.eex_new`
- Offset: `0x000222`
- Hex:

```text
fc 03 02 00 fc 03 05 00 e4 bd a0 e5 b7 b2 e5 ae
8c e6 88 90 e4 bd 8e e9 9a be e5 ba a6 34 e5 91
a8 e7 9b ae e6 8c 91 e6 88 98 ef bc 8c 0a e6 98
af e5 90 a6 e6 84 bf e6 84 8f e6 8c 91 e6 88 98
e5 9c b0 e7 8b b1 e9 9a be e5 ba a6 ef bc 9f 00
01 00 12 00 05 00 e4 b8 8d e4 ba 86 ef bc 8c e6
```

- utf-8: first bytes are structure, then `你已完成低难度4周目挑战，\n是否愿意挑战地狱难度？`; following text begins option `不了，我...`.
- gbk / gb18030: mojibake.
- latin1->gbk / latin1->gb18030: not correct.
- Structure interpretation: the prompt and options are adjacent to small op values (`0x0012`, `0x0013` observed nearby in the same choice block). This maps naturally to native `ScenarioOp.Choice`, but option target labels/set-vars are not decoded yet.
- Uncertainty: branch targets and set-var fields need opcode mapping.

Status: **choice text and structure present; converter needs R opcode map**.

### 3.8 Evidence Blocks - Table JSON

File class: `json/*.json`

- Sample path: `C:\Users\Xy172\codex_diag\extracted\json\dic_hero.json`
- Offset: `0x000027`
- Hex:

```text
e5 88 98 e5 a4 87 22 2c 0d 0a 20 20 20 20 22 66
61 63 65 5f 69 64 22 3a 20 31 2c 0d 0a 20 20 20
20 22 72 69 6d 67 5f 69 64 22 3a 20 31 2c 0d
```

- utf-8: `刘备",\r\n    "face_id": 1,\r\n    "rimg_id": 1,\r\n    "jobid": 1,\r`
- gbk / gb18030: mojibake.
- latin1->gbk / latin1->gb18030: not correct.
- Structure interpretation: direct UTF-8 JSON with expected table keys.
- Uncertainty: none for `dic_hero` shape.

Status: **usable table**.

`dic_seid.json` caveat:

- Strict JSON parse fails at char 31,567 because of a literal newline inside an `Info` string (`"Info": "【激昂】\n君主类..."` as raw newline, not escaped `\n`).
- UTF-8 decode has `0` U+FFFD replacements, `0` question-mark replacement bytes, `0` NUL bytes.
- This is a JSON-escaping problem, not byte loss.

Status: **table text readable; strict JSON repair/lenient parsing needed for `dic_seid`**.

### 3.9 Evidence Blocks - Terrain JSON

File class: `terrainJson/*.json`

- Sample path: `C:\Users\Xy172\codex_diag\extracted\terrainJson\terrainMap_1.json`
- Offset: `0x000000`
- Hex:

```text
7b 0d 0a 09 22 64 65 73 63 22 3a 20 22 74 65 72
72 61 69 6e 4d 61 70 5f 31 22 2c 0d 0a 09 22 69
64 22 3a 20 6e 75 6c 6c 2c 0d 0a 09 22 69 6d 67
22 3a 20 22 46 3a 5c 5c e4 b8 89 e5 9b bd
```

- utf-8: JSON object begins with `{"desc": "terrainMap_1", "id": null, "img": "F:\\三国...`.
- gbk / gb18030: path Chinese mojibake.
- Structure interpretation: direct JSON map; `terrainMap_1` has width 23 and height 16.
- Uncertainty: stage-to-map linkage appears ordinal/campaign-table driven; no direct literal map id was found in `S_00` sample.

Status: **usable map grid**.

### 3.10 Evidence Blocks - Hexzmap.e5

File class: `*.e5`

- Sample path: `C:\Users\Xy172\codex_diag\extracted\Hexzmap.e5`
- Offset: `0x000000`
- Hex:

```text
4c 73 31 32 20 20 20 20 20 20 20 20 20 20 20 20
00 04 0d 10 02 01 0f 05 03 17 07 0e 1d 0c 0a 06
09 0b 16 19 08 14 11 12 3c 48 18 60 78 54 15 13
1c 6c 22 23 24 25 26 27 28 29 2a 2b 2c 2d 2e 2f
```

- utf-8/gbk/gb18030/latin1: mostly binary/control and ASCII header `Ls12`.
- Structure interpretation: not random; header plus ordered byte values. Existing inspector classifies `.e5` as `MAP_BUNDLE`.
- Uncertainty: not enough evidence to map it into native CCZ; does not block event conversion because `terrainJson` maps are direct.

Status: **structured binary, currently optional/unknown**.

## 4. Table Lineage Check

| Table | Package Path | Repo Counterpart | Row Count | Sample IDs | Verdict | Evidence |
|---|---|---|---:|---|---|---|
| `dic_hero` | `json/dic_hero.json` | `ccz_daxingshan/campaign.json` subset + `LegacyUnitMapper` | 2,729 source; 5 in app subset | `hid` 1,2,3,226,227 | Same source for subset | Source `刘备/关羽/张飞/程远志/邓茂` stats exactly match app units: hp/atk/def/ints->mat/burst->res. SHA-256 prefix `1ddc9a58f41359bc`. |
| `dic_job` | `json/dic_job.json` | `ccz_daxingshan/campaign.json` subset + `LegacyClassMapper` | 189 source; 4 in app subset | `jobid` 1,4,7,68 | Same source for subset | Source move/growth match app classes: `move`, `atk`, `def`, `ints->mat`, `hp_up->hp`. SHA-256 prefix `2baf7b0b2bfd26a1`. |
| `map_terrain` | `json/map_terrain.json` | `ccz_daxingshan/campaign.json` terrain table | 30 source; 30 app terrain | terrain 1,3,4,5,22,23,24 | Same source | Names match exactly: `平原`, `树林`, `荒地`, `山地`, `村庄`, `兵营`, `民居`. |
| `terrainMap_1` | `terrainJson/terrainMap_1.json` | `ccz_daxingshan/campaign.json` cropped map | 23x16 source; 8x7 app crop | `terrainMap_1` | Same source, intentionally cropped | `LegacyPackGenerator.kt:17-30` documents crop; app map uses real terrain ids from source. SHA-256 prefix `3749ee5ef0bf31b0`. |
| `dic_skill` | `json/dic_skill.json` | `LegacySkillMapper` + app hand-authored effect skills | 166 source; 8 app skills | `skid` 1, manual skills 2-8 | Mixed | Damage skill import exists; `KNOWN_ISSUES.md` says effect skills are hand-added pending importer expansion. SHA-256 prefix `ff36d238659f5c5a`. |
| `dic_gk` | `json/dic_gk.json` | No direct native campaign table yet | 160 | `gkid` 1 `大兴山之战` | Same source, not mapped | Useful for stage catalog; not consumed by current app except hand-authored/generator metadata. SHA-256 prefix `dd82bedfd5c0607d`. |
| `dic_turn` | `json/dic_turn.json` | No direct native table yet | 1,870 | `hid` 1,2 | Same source, not mapped | Supports appearance/turn metadata; not enough alone for event conversion. SHA-256 prefix `e7f36d090d370470`. |
| `dic_jobWalk` | `json/dic_jobWalk.json` | app class `terrain_cost` via `LegacyClassMapper` | 152 | job classes | Same source for subset | Existing mapper folds per-class movement cost. |
| `dic_jobTerrain` | `json/dic_jobTerrain.json` | app class `terrain_affinity` via `LegacyClassMapper` | 152 | job classes | Same source for subset | Existing mapper folds per-class terrain combat affinity. |

Concrete subset comparisons:

| Source row | Source values | Native app values | Result |
|---|---|---|---|
| `dic_hero.hid=1 刘备` | `jobid=1`, hp 168, atk 78, def 87, ints 83, burst 85 | `hero_1`, `job_1`, hp 168, atk 78, def 87, mat 83, res 85 | Match. |
| `dic_hero.hid=2 关羽` | `jobid=4`, hp 170, atk 98, def 95, ints 77, burst 79 | `hero_2`, `job_4`, hp 170, atk 98, def 95, mat 77, res 79 | Match. |
| `dic_hero.hid=226 程远志` | `jobid=7`, hp 150, atk 79, def 81, ints 67, burst 73 | `hero_226`, `job_7`, hp 150, atk 79, def 81, mat 67, res 73 | Match. |
| `dic_job.jobid=68 黄巾军` | move 1, atk 3, def 2, ints 2, hp_up 5 | `job_68`, move 1, growth atk 3, def 2, mat 2, hp 5 | Match. |

## 5. Decryption Layer Check

| File Class | Evidence | Verdict |
|---|---|---|
| `.eex_new` | 316/316 start with `45 45 58 00` (`EEX\0`); scripts contain readable UTF-8 Chinese at fixed offsets; first-4KB entropy around 5.5, with many small integers/NULs. | Already decrypted into EEX binary script container. |
| `.json` | 254 files have JSON/BOM JSON headers; key tables parse as UTF-8; `dic_seid` has strict JSON newline issue but no replacement bytes. | Already decrypted; some JSON needs lenient repair. |
| `.e5` | Header `4c 73 31 32` (`Ls12`), low entropy, no `BGT1`. | Already decrypted/decoded to custom binary; semantic parser absent. |
| Images/audio | PNG/JPG/BMP/MP3 magic present; no `BGT1` magic in all files. | Already decrypted. |
| Whole tree | Header scan found `0` files starting `BGT1`. | Package is decrypted; no evidence of remaining BGT1 layer. |

Conclusion: **已解密**. The remaining work is semantic decoding of custom containers, not cryptographic decryption.

## 6. Native CCZ Mapping Feasibility

Only because structure is green enough:

| Legacy Concept | Evidence | Candidate CCZ Model | Certainty |
|---|---|---|---|
| Stage catalog | `dic_gk.json` has 160 named rows; row 1 `大兴山之战`; S/R names align with stage text. | Campaign manifest/entry table, future campaign index. | Medium. |
| Map reference | `terrainJson` has 210 maps; `terrainMap_1` is 23x16; `S_00` text and `dic_gk` row 1 both identify 大兴山. | `MapDef` plus S-script map binding. | Medium-low; direct byte field not found yet. |
| Player setup/deployment slots | `S_00` offset `0x1a00`, label `HEX我军设定`, repeated `0x004b` records with slot indexes and coordinate-like small values in map bounds. | `SScript.pre` `spawn_unit` / deployment metadata. | Medium-low until opcode field order is proven. |
| Enemy/equipment setup | `S_00` offset `0x1bc0`, label `AI装备`, repeated numeric records. | `spawn_unit`, initial inventory/loadout, AI metadata; some may be out-of-scope. | Low-medium. |
| Win condition | `S_00` offset `0x23100` text says `全灭敌军`; structural op stream surrounds it. | `WinLoseCondition.AnnihilateEnemies`. | Medium for first stage; opcode needs proof. |
| Lose condition | `S_00` offset `0x23100` text says `刘备死亡`, `邹靖死亡`, `回合数超过15`. | `ProtectAlive` / `UnitDead` / `SurviveTurns`. | Medium for first stage; exact unit ids need opcode map. |
| Mid-battle trigger/event | `S_00` offset `0x27030` has event label `关羽撤退`; `S_01` has `援军` occurrences near structured bytes. | `BattleTrigger` with `remove_unit`, `move_unit`, `script`, `spawn_unit`. | Medium-low. |
| Dialogue | `S_00`, `R_01` contain readable speaker/text records with repeated op framing. | `ScenarioOp.Dialogue`, `ScenarioOp.Portrait`. | High for text extraction; medium for speaker/portrait ids. |
| Choice branch | `R_00` offset `0x222` has prompt and two options adjacent to op markers. | `ScenarioOp.Choice`, `SetVar`, `Branch`, `Label`. | Medium. |
| Forced move / retreat | `关羽撤退` event block with surrounding op values. | `BattleOp.MoveUnit` or `RemoveUnit` plus scenario text. | Low-medium. |
| Set HP / status / item | Not proven in sampled bytes; likely present given opcode variety and old MOD semantics. | `set_hp`, `set_status`, `give_item`, or future ops. | Unknown. |
| Unsupported opcodes | Numeric op stream is enumerable. | Converter should fail closed on unknown opcode with source file + offset. | High. |

Initial field/opcode to CCZ event-model draft:

| Evidence token | Draft mapping | Fail-closed handling |
|---|---|---|
| EEX header magic/version/header-size/offset table | Parser framing only | Reject unsupported magic/version; reject offset outside file. |
| Text op marker observed as `0x0005` near UTF-8 strings | String operand / label / dialogue payload component | Reject invalid UTF-8 inside declared text region. |
| `0x004b` records after `HEX我军设定` | Deployment slot / placement candidate | Do not emit `spawn_unit` until field order proven by multiple stages. |
| `0x0019` condition text block | Condition/help text block candidate | Can extract text now; do not infer machine condition solely from prose. |
| `0x0012`/`0x0013` near choice text | Choice/option/branch candidate | Pause conversion on unknown branch target format. |
| `0x0035`, `0x004f`, `0x0050`, `0x0058` around event/action blocks | Battle op/action candidates | Keep as unknown opcode entries until mapped. |

Converter work estimate:

| Phase | Work | Estimate |
|---|---|---:|
| EEX framing parser + corpus dumper | Parse magic/version/header-size/offset table, split segments, dump op candidates with offsets and text regions. | 2-4 days |
| Opcode ledger for R scripts | Map dialogue, portrait/location, label, choice, branch, set-var, waits/BGM/fade using `R_01`, `R_00`, and 10-20 more samples. | 1-2 weeks |
| Opcode ledger for S scripts | Map deployment/setup, win/lose, trigger/action, reinforcements, retreats, forced moves, set HP/status if present. | 2-3 weeks |
| First fail-closed converter slice | Convert one stage (`S_00` + `R_01` + `terrainMap_1`) to native content, rejecting unknown ops with file+offset. | 1 week |
| Corpus coverage pass | Run all 316 scripts through parser, enumerate unknown opcodes/fields, decide supported subset. | 1-2 weeks |

Total initial path: **4-8 engineer-weeks**, depending on how many S op variants appear after the first 10 stages.

Known unsupported list for first converter slice:

- `Hexzmap.e5` semantic parser.
- Direct S file to terrain map binding, unless ordinal/campaign-table rule is proven.
- `S_1010.eex_new` role.
- Full AI/equipment semantics in `AI装备` blocks.
- Any opcode not mapped with multi-file evidence and unit/map/table cross-reference.
- Runtime ops not yet in native schema, if encountered: AoE, weather, poison/DoT, complex AI scripting, shop/global unlocks.

## 7. Unknown / Undecidable Ledger

| Item | Why Unknown | What Evidence Is Missing | Impact |
|---|---|---|---|
| Direct map reference inside S script | `S_00` aligns with `dic_gk` row 1 and `terrainMap_1` by name/content, but no literal `terrainMap_1` text was found in sampled S bytes. | More samples or known editor/export reference proving ordinal rule. | Converter can start with ordinal mapping but must mark source and fail closed on mismatch. |
| `S_1010.eex_new` | Numbering is outside normal 0..159 sequence. | Inspect role/text and references. | Could be special stage/tutorial/test script. |
| Exact `0x004b` field order | Looks like deployment/setup slot records but field order is not proven. | Correlate values with map dimensions, editor screenshots, or multiple stage deployment layouts. | Cannot emit authoritative `spawn_unit` until proven. |
| Enemy unit ids and equipment fields | `AI装备` is structured but not semantically decoded. | Cross-reference numeric ids with `dic_hero`, `dic_item`, `dic_job`, and visible stage enemies. | Initial converter may omit equipment or require manual mapping. |
| Win/lose condition opcodes | Human-readable condition text is clear; numeric condition records are not named. | Multi-stage correlation: all-annihilate, protect unit, turn limit, reach tile. | Must not infer machine conditions solely from display text. |
| Trigger condition opcodes | Event labels (`关羽撤退`, `援军`) are visible; trigger bytes not mapped. | Compare trigger labels with coordinate/unit changes across many S scripts. | Mid-battle event converter must fail closed until mapped. |
| R branch targets | Choice text is visible; branch target encoding not decoded. | Follow offsets/labels in R files; compare options to later text blocks. | R conversion can extract dialogue but cannot preserve branch logic until solved. |
| `dic_seid` strict JSON | Current bytes are UTF-8 and not lost, but literal newline inside strings breaks strict JSON. | Decide sanitizer/lenient parser strategy. | Skill/effect importer expansion needs a repair step. |
| Full text encoding status | Sampled script/table text is UTF-8 readable. Whole-file decode has U+FFFD where binary bytes are not UTF-8 text. | Text-region parser to separate binary from text before counting replacements. | Do not classify binary control bytes as text corruption. |
| Unsupported CCZ ops | Legacy may contain weather, AoE, poison, shop/global unlocks, scripted camera, tutorial UI. | Opcode ledger and corpus scan. | Unknown ops must become explicit unsupported list, not guesses. |

## 8. Final Verdict

### GREEN — Structured and convertible

This is green because:

- Stage/script files are located: `Scenes/S_*.eex_new` and `Scenes/R_*.eex_new`.
- They are not encrypted blobs: all are `EEX\0` custom binary containers with stable headers and in-file offset tables.
- Structure fields and text fields can be separated:
  - Structure: magic/version/header-size/offset table/repeated small integer op streams.
  - Text: valid UTF-8 Chinese strings embedded at known offsets.
- Key concepts are recoverable enough to start an opcode-ledger converter:
  - Deployment/setup candidates: `S_00` label `HEX我军设定` + repeated `0x004b` records.
  - Map grid: `terrainJson/terrainMap_1.json` and 209 sibling maps directly readable.
  - Win/lose: `S_00` condition text plus surrounding structured records.
  - Trigger/event: retreat and reinforcement labels occur inside S op streams.
  - Dialogue and choices: R/S script text and `R_00` choice block are readable and structured.
- Text problems do not block structure conversion:
  - Script and key table text samples are UTF-8 readable.
  - `dic_seid` has a JSON escaping problem, not byte loss.
  - No sampled text requires GBK recovery.
- Unknown opcodes are enumerable by file and offset; they are not mixed into high-entropy data.

This green verdict is **not** permission to write a broad converter immediately. The next engineering step is an EEX parser/dumper and an opcode ledger. The converter must reject unknown opcodes/fields with source file + offset.

Required initial mapping draft:

| Legacy field/opcode area | Candidate CCZ event model |
|---|---|
| R dialogue/location text | `RScript.ops: Dialogue`, `SceneTransition`, `Portrait` after speaker/portrait fields are proven. |
| R choice block | `ScenarioRunner Choice / Branch / SetVar / Label`. |
| S setup/deployment block | `SScript.pre` `SpawnUnit` and/or deployment metadata. |
| S win text/op block | `WinLoseCondition.AnnihilateEnemies` etc., after opcode proof. |
| S lose text/op block | `ProtectAlive`, `UnitDead`, `SurviveTurns`, after unit-id mapping proof. |
| S retreat/reinforcement blocks | `BattleTrigger` + `RemoveUnit`, `MoveUnit`, `SpawnUnit`, `Script`. |
| HP/status/item blocks if found | `SetHp`, `SetStatus`, `GiveItem`, or future explicit ops. |

Recommended terminology:

- Keep saying **automatic stage conversion is feasible after opcode-ledger proof**.
- Do not say **automatic stage converter exists**.
- Do not say **scripts are already decoded**; say **EEX framing and text are decoded, opcode semantics are partially unmapped**.
