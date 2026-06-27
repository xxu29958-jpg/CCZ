# 0009 内容生成器框架:Content Pack 唯一 ABI,EEX 旧作生成器为第一插件

Date: 2026-06-27

Status: Accepted（Phase 1 EexCodec 起步）

## Context

旧作 `同人圣三国蜀汉传`（已解密）有 160 关 / 316 个 `.eex_new` 脚本可移植。把每关手写成 content-pack
JSON **不可扩展**——加一关、改一句对白、做一个 mod 都要手抠冗长 JSON。用户裁决:目标不是"一个旧作
转换器",而是一套 **内容生成器框架**,让以后加剧情 / 做 mod / 加动画都是"改源 → 跑生成器 → 产 pack →
validator 审 → runner 跑",而非改引擎或手抠 JSON。

EEX 解密路线已独立验证完整(见 `docs/recon/`:容器 framing 全 316 文件通过、99 个 op 的 `initPara`
长度模型从 `libMyGame.so` 逐字节复算、actor-id 命名空间 / S↔gkid / S→map 三门收口)。

## Decision

**1. Content Pack = 唯一 ABI。** 所有来源(EEX 旧作生成器、未来作者格式生成器、MOD 生成器、编辑器
导出器、AI 草案生成器)最终都只产出同一个 content-pack JSON,过同一个 `ContentValidator`。加一个来源
= 加一个生成器,**引擎零改**。

**2. game-core 永远纯净。** 它**不解析 EEX、不理解旧 VM、不为 converter 开后门**,只经
`Loader → Validator → Assembler → ScenarioRunner` 消费 pack。

**3. 旧作生成器分层(EexCodec 压到最低层)。**

| 层 | 职责 | 边界 |
|---|---|---|
| `EexCodec` | 字节级:framing / 记录读取 / offset 标注 | 只答"文件里有什么",**不做引擎设计判断** |
| `LegacyScriptDecoder` | EEX → legacy script AST | 旧作语义,仍非引擎形状 |
| `LegacySemanticMapper` | legacy AST → 引擎语义(op/objective/presentation) | fail-closed:不可映射→标 unsupported |
| `LegacyPackGenerator` | 产 content-pack draft | 复用现有 builder |
| `ContentValidator` | 判生死 | fail-closed 拒未知 |

`EEX bytes ≠ content pack`;链条是 `EEX bytes → legacy AST → semantic model → content pack`。

**4. sealed op = 引擎编译期能力;MOD 只能引用已声明 capability。** Kotlin sealed 子类编译期固定(官方
文档:密封层级外部不可新增实现),故第三方 MOD **不能运行时自创 op**,只能引用引擎已声明的
`requires_capabilities`;validator 对未知 op fail-closed。新增 sealed op = **引擎版本升级**,非 MOD 内容。

**5. 动画 ≠ 仿真。** 影响战斗结果的 → `ScenarioOp`(确定性 simulation,进 game-core);纯表现的(镜头 /
震屏 / 动画 / 音效 / 立绘入场)→ `PresentationEffect`(只在 `:app`)。动画**绝不污染**确定性
ScenarioRunner/Resolver。

**6. MOD = pack,分种类**(full campaign / overlay-patch / resource / fixture)。未来管线在 validator 前
插一个 `PackResolver/Merger`(`Loader → Resolver/Merger → Validator → Assembler → Runner`),冲突(两个
mod 改同一对白 id)**报错而非静默覆盖**。

**7. 作者 DSL / 可视化编辑器暂缓。** 现在只定义 Generator 插件接口 + content-pack 版本契约 + 极小作者
样例(反向约束),**不先拍完整 DSL/编辑器**——让 316 真脚本先逼出 op 集与 schema 形状。

## 分阶段(止损门)

**止损门(硬):大兴山 vertical slice 端到端跑通前,不广扫 316、不做编辑器、不写完整作者 DSL。**

1. **旧作生成器 vertical slice**(本阶段):`EEX → EexCodec → Legacy AST → LegacyPackGenerator →
   pack draft → ContentValidator → CampaignAssembler → ScenarioRunner → 大兴山可玩 + 对账手写版`。
2. content-pack 契约稳定(op 集 / objective / presentation 边界由真脚本逼定)。
3. overlay / mod pack 合并(`PackResolver`)。
4. 作者格式(高层 → pack 生成器)。
5. 可视化编辑器(作者格式的 UI 外壳)。
6. 运行时热加载 / 创意工坊生态。

## Consequences

- 来源可越长越多,入口只有一个 pack;改剧情/做 mod/做编辑器本质都不是改引擎。
- EexCodec 不越权做设计判断 → 旧作生成器不会把 pack 设计成"只适合旧作"的形状。
- 让真实数据逼出稳定契约,而非先拍 DSL 拍歪;契约稳定后作者/MOD/编辑器零返工叠上。
- 未定项(由数据逼出,不预先冻结):EEX 能稳定解多少 op、316 脚本是否暴露 ScenarioOp 缺口、
  TurnLimit/商店/动画/分支各进哪层、首版 pack schema 形状、作者格式终态(YAML/TOML/可视化/混合)。
