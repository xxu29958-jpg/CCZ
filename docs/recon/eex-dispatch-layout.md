# EEX dispatch-record 布局 RE — 真·旧作整关移植起点

> 为"忠实搬运整关旧作 大兴山"(真实 roster + 坐标 + 完整 23×16 地图)打的 RE 基础。从
> `libMyGame.so` 反汇编(capstone Thumb)dispatch op 的 `initPara`,定位部署记录的字段布局。
> 日期 2026-06-27。配套工具 `docs/recon/eex_initpara.py`。

## 0. 为什么需要这层(关键发现)

`LegacyPackGenerator` 现在的大兴山 demo 是**手工设计的简化对局**(桃园三兄弟 vs 黄巾二将,8×7 裁剪图),
**不是**旧作 S_00 那一关的真实部署。实证(`eex_decode.py` 扫真 S_00 的 0x46/0x47/0x4b):

- 旧作 S_00 真实部署的是**整关全套、跨多场子战斗**的大名单(水军/曹丕/黄巾兵2/费祎…,散在 `0x46f`/`0x24204`/… 等天差地别偏移),demo 那 5 人**一个都不在**。
- naive `<cmd> 02 00 <hid>` 扫描**不可靠**:有假阳性(`65535=0xffff` 字节巧合),且把多场子战斗的演出 actor 全混进来。

所以"从 dispatch 派生 demo roster"是伪命题。**忠实搬整关**才是 dispatch 解析的正确目标——需要本文件钉的布局 + 可靠的链式遍历。

## 1. 读取助手(native）

| 地址 | 作用 |
|---|---|
| `0x6400e6` | bytesToShort —— 读 2 字节 u16(`bl` 后 r0=短值) |
| `0x6400c8` | bytesToInt —— 读 4 字节 int |

## 2. `ScriptDispatchOwn`（0x4b）玩家部署 —— 直接字段,坐标可取

`ScriptDispatchOwn::initPara @0x7196f4`(size 138)直接顺序读字段(非 child 循环),写进对象偏移
`#0x20/#0x24/#0x28/#0x2c/#0x30/#0x34/…`:

| 对象字段 | 文件偏移(payload+) | 类型 | 含义 |
|---|---|---|---|
| `#0x20` | +2 | short | (待定) |
| `#0x24` | +6 | **int** | **own-force 列表索引**(非 hid！)—— 经 `0x4a ScriptConfigOwnForce` 列表二次解析成武将 |
| `#0x28` | +0xa? | short | (待定) |
| `#0x2c` | +0xc? | **int** | **X**(codex: `HistoryActor+0x10`) |
| `#0x30` | +0xe? | short | (待定) |
| `#0x34` | +0x12 | **int** | **Y**(codex: `HistoryActor+0x12`) |

整记录 native 长度 `0x1a`(见 `eex_initpara.py` 输出)。**坐标(x/y as int)在记录里、可取**;玩家身份须经
`0x4a ConfigOwnForce` 列表(0x4b 只给列表索引)。

## 3. `ScriptDispatchEnemy`（0x47）/ `ScriptDispatchFriend`（0x46）—— child 循环

两者 `initPara` 都是**固定 child 槽循环**,每个 child 是子对象、虚调用 `[childVtable+8]` 自解析:

| op | child 数 | child 对象 stride | 循环上界(`r4` 终值) |
|---|---:|---:|---|
| `0x47` 敌军 | **80** | `0x60` | `0xf0<<5 = 0x1e00` |
| `0x46` 友军 | **20** | `0x5c` | `0xe6<<3 = 0x730` |

文件记录形如 `47 00 02 00 <hid:u16> 26 00 <field> 26 00 <field> …`(codex S_00@0x881:`47 00 02 00 54 02
26 00 01 00 …` → child0 `#0x22`=0x254=596)。**hid 在 child 文件 +4**(`#0x22`,codex 交叉验证可靠);
x/y/level 在后续 `26 00`-tagged 字段里,但**child 子对象的精确 initPara 还没反汇编**(下一步)。

## 4. 真·整关移植 — 剩余步骤(下一轮起点)

1. **反汇编敌/友 child 子对象 initPara** —— 定位 x/y/level 在 child 文件记录的精确偏移(本文件只钉了 hid@+4)。
2. **解 `0x4a ScriptConfigOwnForce`** —— own-force 列表,把 0x4b 的列表索引映成武将(玩家身份)。
3. **可靠链式遍历**(防假阳性)—— 按 codex chained-parent 法(record native 长度落在下个候选/边界),
   不用 naive 扫;或用 hid∈dic_hero + child-count 守。
4. **装配** —— `LegacyRosterImporter`(dispatch → 真 roster:side+hid+x/y)→ 喂 `LegacyBattleBuilder`
   (完整 23×16 terrainMap,不裁剪——棋盘滚动 #96 已支持)→ pack → 可玩真·整关。
5. **坐标系** —— dispatch 的 x/y 是**全图 23×16** 坐标,直接用完整图(无需 demo 的 8×7 裁剪平移)。

布局来源全部可复算:`eex_initpara.py` + capstone 反汇编上述地址。Evidence Rule:x/y 字段在 child 布局
反汇编坐实前**不得猜**——故本轮只钉到结构层,下一轮反汇编 child 后再落坐标解析。
