# EEX dispatch-record 布局 RE — 真·旧作整关移植起点

> 为"忠实搬运整关旧作 大兴山"(真实 roster + 坐标 + 完整 23×16 地图)打的 RE 基础。从
> `libMyGame.so` 反汇编(capstone Thumb)dispatch op 的 `initPara` / `HandleScript`,定位部署记录
> 的字段布局,**并对真 `S_00.eex_new` 逐字节核实坐实**。日期 2026-06-27(布局已坐实)。
> 配套工具:`docs/recon/eex_initpara.py`(op 记录长度模型)+ scratchpad 的 `fields2.py`(逐字段读取
> 追踪)/`roster2.py`(对真 S_00 解码核验)。落地实现:`LegacyRosterImporter`(mod-import)。

## 0. 为什么需要这层(关键发现)

`LegacyPackGenerator` 现在的大兴山 demo 是**手工设计的简化对局**(桃园三兄弟 vs 黄巾二将,8×7 裁剪图),
**不是**旧作 S_00 那一关的真实部署。实证(对真 S_00 解码):

- 旧作 S_00 真实部署的 **0x47 主敌军记录(@0x881)= 23 个敌方单位**(黄巾兵/邹靖部/将领…),分布在右上
  (x 18–21, y 0–5)与左侧(x 0–2, y 6–10)两团;**0x46 主友军记录(@0x46f)= 8 个友方 NPC**(散布全图)。
  demo 那 5 人(刘备/关羽/张飞/程远志/邓茂)的 hid 一个都不在主记录里。
- 另有两条敌方**增援波**(@0x1cc2 单兵、@0x2ebe 四兵 hid 839/966/967/968),是 S 脚本中途触发的后续
  dispatch,不属于开局部署。
- **玩家本方军 NOT in S_00**:旧作开战时玩家**交互式布阵**(选格落子),故文件里没有玩家固定坐标——
  0x4b `DispatchOwn` / 0x4a `ConfigOwnForce` 在 S_00 不出现。玩家 roster 是**战役设计输入**(与出场等级
  同性质,ADR 0006),不是可移植的旧作事实。
- naive `<cmd> 02 00 <hid>` 2 字节对齐扫描**不可靠**:记录是**字节粒度**对齐(主敌军 @0x881 是奇数偏移),
  且有假阳性。可靠定位靠**整条记录结构自洽校验**(见 §4)。

## 1. 读取助手(native)

| 地址 | 作用 |
|---|---|
| `0x6400e6` | `bytesToShort(base, off)` —— 读 `base+off` 处**有符号** LE int16(高字节 `ldrsb` 符号扩展) |
| `0x6400c8` | `bytesToInt(base, off)` —— 读 `base+off` 处**有符号** LE int32 |

调用约定:`Script*::initPara(this=r0, char* payload=r1, int startOffset=r2)`;字段偏移是 **`r1` 第二参**
(相对 `startOffset` 的 base-relative),不是指针本身。`startOffset` 指向 **cmd word(`<cmd:u16>`)之后**
那个字节,即记录体首字节(第一个 child 槽起点)。

## 2. 确认的记录布局(逐字段,对真 S_00 坐实)

三类 dispatch 的字段偏移**全部相对"记录体首字节"**(cmd word 之后 = 文件里 `<cmd> 00` 之后的 `02 00`,
其 `02 00` 本身是 child0 的 `+0x0` 字段):

| 记录 | cmd | 槽数 × 文件 stride | hid | X | Y | level |
|---|---|---|---|---|---|---|
| **敌军** `ScriptDispatchEnemy` | `0x47` | **80 × 0x38** | s16 `+0x2` | s16 `+0xe` | s16 `+0x14` | s16 `+0x1a` |
| **友军** `ScriptDispatchFriend` | `0x46` | **20 × 0x34** | s16 `+0x2` | s16 `+0xa` | s16 `+0x10` | s16 `+0x1a` |
| **玩家** `ScriptDispatchOwn` | `0x4b` | 0x1a 体(单条) | (索引)i32 `+0x2` | i32 `+0x8` | i32 `+0xe` | — |

### 2.1 当前 E 盘 APK opcode profile

上表的 `0x46/0x47` 是旧解密参考包的 native command id。2026-06-29 复核当前
`E:\trssgshz_reverse_repo\decrypted_full_apk\assets\GameResources\trssgshz` 后确认:payload 字段布局和偏移
未变,但脚本 opcode 被重排。`LegacyEexOpcodeProfile.TRSSGSHZ_CURRENT_APK` 明确记录当前包映射:

| 语义 | 旧 reference cmd | 当前 APK cmd |
|---|---:|---:|
| `ScriptDispatchFriend` | `0x46` | `0xdb` |
| `ScriptDispatchEnemy` | `0x47` | `0xde` |
| `ScriptChildInfo` label | `0x02` | `0x0f` |
| `ScriptActorTalk` dialogue | `0x14` | `0x45` |
| `ScriptCommonInfo` objective block | `0x19` | `0x54` |
| `ScriptArmyChange` actor state | `0x3b` | `0xba` |
| `ScriptSetActorVisible` actor visibility | `0x4c` | `0xed` |

当前 S_00 的主友军记录仍在 `@0x46f`,主敌军记录仍在 `@0x881`;只是 cmd word 从 `0x46/0x47`
变为 `0xdb/0xde`。因此生产 importer 必须先选择 opcode profile,不能同时扫所有历史 cmd,否则当前包里的旧
`0x47` 字节巧合会被误报为部署。

布局来源(三重交叉):
- **子对象 initPara**(`fields2.py` 逐字段追踪):`ScriptDispatchOneEnemy::initPara@0x71da92`(返回 payload+0x38)、
  `ScriptDispatchOneFriend::initPara@0x71dd00`(返回 +0x34)、`ScriptDispatchOwn::initPara@0x7196f4`(返回 +0x1a)。
  **关键:坐标存为 4 字节 `str`(int),不是 `strh`** —— 敌军 X=对象 `#0x30`=rec`+0xe`、Y=`#0x38`=rec`+0x14`;
  友军 X=`#0x2c`=rec`+0xa`、Y=`#0x34`=rec`+0x10`;玩家 X=`#0x2c`、Y=`#0x34`。
- **HandleScript actor 回填**(坐实哪个对象字段是 X/Y):友军 `HandleScript@0x7185dc`:`actor[+0x10]=child[#0x2c]`、
  `actor[+0x12]=child[#0x34]`;敌军 `HandleScript@0x718bc2`:`actor[+0x10]=child[#0x30]`、`actor[+0x12]=child[#0x38]`。
  codex 已知 `HistoryActor+0x10`=X、`+0x12`=Y。hid 经 `HandleScript@0x7189d4` 读 child `#0x22`(rec`+0x2`),负值=
  alias 索引(own-force 重映射)。level 受 `cmp #0x32` 钳到 50(child `#0x3e`=rec`+0x1a`)。
- **对真数据核实**(`roster2.py`):敌军 @0x881 23 槽全部 hid∈dic_hero 且坐标在 23×16 内;曾经误用 `strh`
  字段(rec`+0xc`/`+0x12`)解出"4 个单位同在 (4,4)"的不可能结果 —— 正是这条把坐标定到 int 字段的。

## 3. 父循环结构(80/20 固定槽,顺序解析)

`ScriptDispatchEnemy::initPara@0x71896a`:对 `this->#0x20` 的 child 数组按对象 stride `0x60` 循环 **80** 次
(`0xf0<<5 = 0x1e00` / `0x60` = 80),每个 child 虚调用 `[childVtable+8]`(= `DispatchOneEnemy::initPara`)
自解析,**`startOffset` 在 child 间链式递进**(上一个 child 返回值 = 下一个 child 起点),故 80 个 0x38 子记录
在文件里**连续紧排**,总长 `80*0x38 = 0x1180`。友军同构:20 × `0x5c`(对象)/ `0x34`(文件)= `0x410`。空槽 hid
取哨兵(-1/-2/0)。`getScriptByCmd` 等的偏移见 `eex-opcode-ledger.md`。

## 4. 可靠定位(防假阳性)+ 装配

记录**字节粒度**对齐,且 cmd word 后恒跟 `02 00` tag,故扫 `<cmd:u8> 00 02 00`。单纯字节匹配有假阳性,
**整条记录结构自洽**才接受(`LegacyRosterImporter.readRecord`,fail-closed):

1. 记录须完整落在 blob 内(`body + slots*stride <= size`);
2. **slot 0 必是实兵**(hid>0)—— 真 dispatch 开头就部署;
3. **每个非哨兵槽**(hid>0)必须匹配 dispatch child 的固定 tag schema(例如敌军槽内 `+0x04=0x26`,
   `+0x0c=0x04`, `+0x18=0x2b`, `+0x1c=0x3e` 等)。仅靠 hid/x/y 像坐标还不够;payload 内假阳性必须被拒。
4. **每个非哨兵槽**(hid>0)坐标都须在 `[0,W)×[0,H)` —— 出现一个越界非哨兵槽即判**字节巧合**,整条拒。
   随机字节通过此门概率 ≈ `0.5^80`,极强 fail-closed。代价:若真关卡有**图外待命单位**会被此门误拒
   (保守取舍——宁拒可疑也不纳噪声;S_00 全部部署槽在图内,已核实)。
5. **同侧取首条有效记录**(最低偏移)= 开局部署;其余有效记录计入 `reinforcementRecords`(增援波,不静默丢、
   也不混进开局)。hid→已知武将的解析与未知过滤交给调用方(`LegacyPackGenerator` 持 dic_hero)。

`LegacyStageMigrationPlanner` 的生成报告会把开局部署里的同格冲突展开为
`diagnostics.collision_groups[]`：每个冲突单位记录 side / hid / level / slot / record_offset / raw_words。
这些 raw 16-bit slot words 是继续识别旧作隐藏、替补、换位部署规则的证据；不能把它们直接当运行时内容。
同组还会输出 `script_refs[]`，列出本关 `ScriptSetActorVisible` / `ScriptArmyChange` 是否点名这些 hid。
当前真实报告中共有 163 个同格组 / 881 个组内单位，其中 125 个同格组带有这类脚本引用(285 条)，
并为每个有脚本线索的组输出 `script_ref_coverage`。互斥覆盖形态为：38 组无脚本线索、55 组全员被点名、
68 组只有一个单位实例未被点名、2 组多单位未点名混合；其中有任意未点名单元的脚本线索组共 70 组。
这 68 个单候选组会额外输出 `resolution_proposal.kind=opening_unit_with_deferred_actor_state_refs`：
未点名单位作为开局占格候选，被脚本点名单位作为延迟/隐藏候选。当前报告中是 68 个 proposed opening
units / 93 个 deferred units，覆盖 16 个关卡，其中 13 个碰撞关卡全部由这种 proposal 组成；这 13 关的
`collision_resolution_preview.status_after_proposals` 为 `ready`，但原始 `status` 仍保持 blocked。
例如第一关同格的 `hid 603` 被后续 `set_actor_visible` 点名，proposal 保留未点名的 `hid 226` 为开局候选；
这证明“显隐/后出场”是重要线索。2026-06-29 的 native 契约已把该线索落为
`events.deferred_deployments[]` metadata：`planLegacyStages` 对上述 13 个
`status_after_proposals=ready` 关卡执行 `trial_assembly`，逐关走 `ContentJsonLoader` →
`ContentValidator` → `CampaignAssembler`，13/13 通过，合计 57 个 deferred units。原始 `status` 仍保持
blocked，避免把 proposal 静默当成正式迁移；`trial_assembly.status=ready` 是可机器复验的下一步证据。

**装配下一步**(本层之上):`LegacyRosterImporter.Deployment` → `Placement(side+hid+x/y+level)` → `LegacyBattleBuilder.
buildBattleOnMap` 的**完整 23×16 terrainMap_1**(不裁剪——棋盘滚动 #96 已支持)→ pack → 可玩真·整关。玩家本方
roster(刘备/关羽/张飞)由设计提供并布于 deploy 区(旧作里本就交互布阵,不在文件)。

布局来源全部可复算:`fields2.py` + capstone 反汇编上述地址;`roster2.py` 对真 S_00 解码核验。Evidence Rule:
本文件每个偏移都经反汇编 + 真数据双向坐实(不再有"待定")。
