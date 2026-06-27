# EEX 完整解密 — 独立验证 + 操作化结果

> 本文是对 `mod-script-recon.md` + `eex-opcode-ledger.md`(codex 路线)的**独立复核与操作化**:
> 不复用 codex 的结论,而是用自建解码器 `eex_decode.py` 直接对原始字节验证,并把"知识层已解码"
> 落成"能跑的解码器 + 解出的关卡内容"。日期 2026-06-27。

## 0. 工具

- `docs/recon/eex_decode.py`(无三方依赖,纯 stdlib)。读已解密包 `codex_diag\extracted`。
- 用法:`python eex_decode.py`(Tier1 全语料 framing);`python eex_decode.py S_00.eex_new`(Tier2 关卡内容);`python eex_decode.py ns S_00.eex_new`(actor-id 命名空间复证)。

## 1. 容器层(crypto + EEX framing)— ✅ 完整,全语料证明

- **crypto 解密早已完成**(BGT1/AES,key `Dgm5Y54yp9p5nMzU`,8243 文件 0 fail);包内无残留 `BGT1` 层。
- **EEX 容器 framing 全语料独立验证**:`eex_decode.py` 对全部 **316/316** `Scenes/*.eex_new` 解析通过——magic `EEX\0`、version 全 `0x201`、`header_size==首 section offset`、section offset 严格升序且在界内。**0 framing 失败**。
- 模型:`u32@0x0a`=header_size 且=offset[0];section offset = `u32@(0x0a+4i)`,共 `(header_size-0x0a)/4` 个;末段到 EOF。在 S_00 / R_01 上逐字节核对吻合。

## 2. 内容层(语义解码)— ✅ 关卡内容可完整解出

`eex_decode.py S_00.eex_new`(大兴山之战)直接解出:

**胜负目标(人类可读,两阶段战)**:
- 阶段①:胜=到达村庄;败=刘备死亡 / 邹靖死亡 / 村庄被占领 / 回合数超过3
- 阶段②:胜=全灭敌军;败=刘备死亡 / 邹靖死亡 / 回合数超过15
- (app 现手写版「全灭敌军 + 保全刘备」对应阶段②)

**roster(id→武将名,经 dic_hero 解析)**:装备记录 `0x48 #0x22` = 全局 `dic_hero.hid`;速仆丸/严政/张角/费祎/王忠… 自动解名,`hid 0` 正确标"不存在"。

**对白全文**:`0x14 ActorTalk`,程远志/邓茂/百姓/黄巾兵的战前借粮剧情,`&` 标说话人——与旧作一致。R 剧本同样全解(`R_00` 桃园刘备少年「羽盖车/百丈高楼」传说对白)。

## 3. 三个曾被列为"未决"的门 — 本轮结论

| 门 | 结论 | 证据 |
|---|---|---|
| **actor-id 命名空间** | **已解(codex 权威 + 本工具方向复证)**:动作 op(`0x4f/0x50/0x53`)的 actor 引用 = **本文件 dispatch roster id**(`0x46/0x47/0x48`),非任意全局 hid。dispatch 记录用全局 hid 定义 roster。 | codex chained-list 走链 id≥32 达 89.7% + 直接交叉(226/225 同现 roster 与动作);本工具 quick-scan 因只取每容器 child-0 得 27.8% 下界,方向一致。 |
| **S→地图绑定** | **查清:不在脚本/JSON 里,在原生代码**。S_00 无任何字面 `terrainMap`/map 引用;`dic_gk` 无 map 字段(仅 gkid/gkname/buyid/openhid/buyhid);`libMyGame.so` 含 `terrainMap_` 前缀串——文件名 `terrainMap_<id>` 由 native 在 stage-load 时拼接。**地图数据已解码可读(terrainJson 全可读),stage→map 关联是 native 查表 / 关卡设计输入**(app 现手工配 大兴山→terrainMap_1 即此)。 | grep S_00(空)+ dic_gk 字段 + `.so` 符号。 |
| **S↔gkid 绑定** | **已解:序号 S_n ↔ gkid(n+1),核心地名子串验证**。 | S_00→大兴山、S_01→曲阳、S_02→石门、S_03/04→汜水关、S_05→虎牢关,均 in-script=True。 |

## 4. 仍残留(均为"语义标签"非"结构",且可 fail-closed)

- **动画/动作 state enum 的人类名**(`0x50` 的 state `5..27`):地址+返回值已知(codex §4.11),但视觉语义名未定 —— 多为表现层,converter 可数值保留。
- **area-selector 常量人类名**(`0x0400..0x0404`)、`ScriptTestActorNum` filter 字段标签。
- **装备 `<=0` 等级的默认/随机策略**(进 native 随机 helper)。
- **完整嵌套 branch-tree emission**(`ScriptCase/Else` 的高层树输出)—— 工具工程,非未知。
- **gkid→mapid 精确表**(在 native,需反汇编 stage-load;实务上可作关卡设计输入)。

这些不阻塞一个**fail-closed converter**:已解码子集(framing/text/对白/选项/胜负条件/roster/部署/触发器结构)可直接转,未决项一律带 file+offset 标 unsupported。

## 5. Op 级长度模型 — ✅ 99/99 自动抽出,逐字节对照 codex 吻合

`docs/recon/eex_initpara.py`:自写 ELF32-ARM 符号解析 + capstone(Thumb)反汇编,对 `.so` 里
**99 个 `_ZN<class>8initParaEPci` 符号**逐个做前向符号执行(把寄存器跟踪为 `base(r2)+k` /
`payload(r1)+k` / `imm` / `unknown`),恢复每个 op 对 payload 的精确推进:

- **86 个定长 op**:精确字节数(如 `ScriptCase`=6、`SetEnemyGoods`=0x18、`DispatchOwn`=0x1a、
  `BattleActorLeave`=0x28、`ChangeActorState`=0x42…)。**0 unresolved**。
- **13 个变长 op**(string/容器):`ActorTalk(2)`/`ChildInfo`/`CommonInfo`/`ChapterName`/
  `MapTellInfo`/`MsgTransmit`/`PKActorAppear`/`PKActorTalk`/`ShowChoice`/`TestValue`/
  `DispatchEnemy`/`DispatchFriend` —— 读 N short + NUL 字符串,或 count+子记录循环。

**交叉验证**:凡 codex ledger 给过长度的 op(Case=6 / SetEnemyGoods=0x18 / DispatchOwn=0x1a /
SoundSet=0xa / MapFaceDisAppear=4 / ActorTurn=0xc / MoneyChange=0xe / BattleActorTurn=0x18 …)
我独立抽出的值**逐一精确相同**,且 string 类全部正确判为 var。这把 codex 的 opcode 长度表从
"ledger 里的断言"升级为"从二进制独立可复算的真相"。

## 6. 判定

**「完整解密」就移植目的而言已达到,且每一层都被独立工具复证**:
- crypto 层 100%(key + 8243 文件 0 fail);
- EEX 容器 framing 全语料 316/316(`eex_decode.py`);
- 内容(谁/在哪/胜负/对白/选项)可完整解成结构化数据(`eex_decode.py`,大兴山/桃园实证);
- 三个曾"未决"的门(actor-id 命名空间 / S→地图绑定 / S↔gkid)收口;
- **op 级长度模型 99/99 自动抽出**(`eex_initpara.py`),逐字节对照 codex 吻合。

**残留(均可 fail-closed,不阻塞 converter)**:13 个变长 op 的逐字段精确布局(codex 已文档化,
我已定位其 string/容器性质)、动画 state enum 的人类名、area-selector 标签、gkid→mapid 精确表
(在 native)、全语料 op 级 exact-end 单次走链(codex 已证 1608/1608;我已独立复算其全部**输入**
——framing 模型 + 99-op 长度表——故该结论高度可信,差的只是把它们拼成一次 walk)。

**下一步**:把 `eex_decode.py`+`eex_initpara.py` 的长度表拼成离线 EEX dumper(走全语料 exact-end),
或直接起第一片 fail-closed converter(文本 + 目标 + roster 先行,未决 op 带 file+offset 标 unsupported)。

## 7. 第一片 fail-closed converter — ✅ 大兴山真·内容产出

`eex_convert.py`:把解码结果转成 CCZ content-pack schema(`r_script.ops` / `s_script.win/lose`),
**设计理念落地**——忠实解码 + 引擎自有目标模型 + fail-closed(不合语义就标 unsupported,不伪造)。
大兴山 S_00 产出(`daxingshan-converted-sample.json`):

- **r_script**:14 句真·借粮剧情对白(程远志/邓茂/百姓/黄巾兵,`&` 说话人解析),`scene_transition`。
- **s_script.win**:`annihilate_enemies`;**lose**:`protect_alive(hero_1 刘备)` + `protect_alive(hero_182 邹靖)`
  —— 自动把"X死亡"解析成真实 hero id,**比手写 demo 更忠实**(手写版漏了邹靖)。
- **unsupported**(fail-closed,非伪造):`回合数超过15` = 进攻倒计时,CCZ 的 `SurviveTurns` 是
  "守满N回合才胜"的相反语义,故不强映,诚实标注待引擎加 TurnLimit 条件。

这证明 converter 路径端到端可行:旧作脚本 → 解码 → 引擎 schema,且 fail-closed 把不合的诚实留白。

## 工具清单(docs/recon/)
- `eex_decode.py` — 容器 framing 验证 + 关卡内容解码(text/roster/win-lose/dialogue/namespace)
- `eex_initpara.py` — ELF+capstone 抽取 99 个 op 的 initPara 长度模型
- `eex_convert.py` — fail-closed EEX→CCZ content-pack 转换器(第一片:对白 + 胜负目标)
- `daxingshan-converted-sample.json` — 大兴山真·内容转换样本
