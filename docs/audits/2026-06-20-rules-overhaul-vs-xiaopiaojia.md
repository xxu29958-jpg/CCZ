# 2026-06-20 规则体系审计与改造（对照 xiaopiaojia）

## 背景

CCZ 的工程规则体系是从上游 Godot 战棋模板 fork、再套用 xiaopiaojia（一个 FastAPI 后端 + Android 记账 app）的规则体系改写而来。xiaopiaojia 是**实际跑通、被 CI 与实战反复打磨**的工作流，是值得开采的参考金矿；但它的领域（后端 / 网络 API / 金额 / OCR）与 CCZ（离线单机确定性战棋：Kotlin game-core + native-content + 未来 Android 壳 + 离线 converter）不同。

指导原则（**能造 ≠ 能复现**）：成熟规则集分两半——**可推导半**（从十几条原则顺下来即可生成）直接从源项目移植；**挣来半**（每条 = 一次 CI 红 / 一个咬人的 bug）凭记忆掏不出，只能边跑边捕。改造 = 删领域残留（artifact 层）+ 移植被验证的 process 层 + 给 CCZ 装捕获回路边跑边长。

## 方法

多代理审计（4 规则对比镜头 + 3 联网核实探针）→ 合成 → 逐条对照真实仓库核验。所有声明已实证：无 `backend/`、`ContentValidator` 确实不读 events、Save 模型代码里不存在、零 CI、detekt pin alpha.3、`API.md` 自承无网络 API。

## 发现 → 处置

| ID | 问题 | 处置 | commit 主题 |
|---|---|---|---|
| P1-1 | GENERAL 整段 backend 分层 / HTTP API 格式 / 网络错误码与「无网络 API」冲突 | 下沉到「Appendix: Backend/Network API (deferred)」，正文留泛化横切原则 | rules |
| P1-2 | float 禁令被框成货币规则，错过其对战斗确定性的承重意义 | 拆分，去货币措辞，重锚到「确定性/精确值禁浮点」+ 交叉引用战斗公式 | rules |
| P1-3 | 无依赖治理段却 pin alpha detekt，自相矛盾 | 补 Dependency Governance + 新建 ADR-0003 记录 alpha 例外与回收条件 | rules / decisions |
| P1-4 | 「Priority」是阅读顺序非冲突裁决梯 | 拆成 Reading Order + Conflict Resolution Order（回放正确性>…>UI） | rules |
| P1-5 | SECURITY 现在时断言「未知 event op 已拒绝」，但校验代码不存在 | 先诚实化标 pending（rules/arch）→ 实现 `ContentEventValidator`（引用完整性）→ 翻转标记 | rules / core |
| P1-6 | Save/Replay 版本模型纯文档，代码里无 Save 类 | 标 `[aspirational]` + TODO（Save 类落地同 PR 附拒未来版本测试） | rules |
| P1-7 | 零 CI 却写「必须过」 | 改「push 前本地必跑，CI pending」；CI.md 已自承未配置 | rules |
| P2-1/3 | Forbidden 禁残留 vs 根目录 78MB Godot 模板；archive 空壳 | 用户拍板彻底删除：移除 Godot 模板 + `git filter-branch` 抹掉全历史 78MB + 删 upstream remote，重建干净历史 | chore |
| P2-2 | HANDOFF 谎报「已落位」但全 untracked | bootstrap commit 入库 | bootstrap |
| P2-4 | 命名示例全是小票夹后端/OCR/Python 符号 | 换 CCZ 原生符号 + Kotlin PascalCase 文件名例外 | rules |
| P2-5/6/7 | Client 层写死网络链 / id+UTC 时间 / backend 目录树 | 改写为 CCZ 进程内核链 / 内容包身份四元组 / android-rooted Gradle 树；网络细节入附录 | rules |
| P2-8 | RELEASE.md 占位、无回滚 | Release Gate 硬清单 + 多版本轴 Rollback | runbook |
| P2-9 | 三份规则无版本头/变更纪律 | 各加版本头 + 入口加 SemVer 变更管理段 | rules |
| P2-10 | #6「每个规则有单测」无机器门 | `assertTestCountEqualsBaseline`（@Test 严格等值，baseline=12） | core |
| P2-11 | 「公式常量变=规则版本变」无锚 | `BattleRules.RULES_VERSION` + GoldenReplayTest 作 tripwire | core |
| P2-12 | RNG 消费顺序契约无测试 | RngContractTest 钉死计数（命中 4/miss 短路 1，splitmix64 delta） | core |
| P2-13 | 无 golden/跨版本回放 | GoldenReplayTest（固定种子→事件流+终态 golden） | core |
| P2-14 | ADR-0002 引擎授权事实无日期 | 加「External Facts (2026-06 核实)」注释 | decisions |
| P3-1 | `uploads/` 离线引擎残留 | 删，换 gradle/converter 产物 | rules |
| P3-2 | detekt 阈值四处重复 | 收口到 `detekt.yml` 单一真相源 | rules |
| P3-4 | converter fail-closed 规则无实现可对齐 | 标 `[aspirational]`（converter 未入仓） | rules |

skills 层金矿移植（process 层端过来，领域无关）：新增 `adversarial-review` / `safe-code-change` / `android-detekt-discipline`，`ship-slice` / `ci-red-triage` 从薄壳升真深度，`add-event-op` 增强为领域 skill。

## 联网核实结论（2026-06）

- **detekt 2.0 仍无 stable，最新 alpha.5**；`detektMain`/`detektTest` 是 type-resolving（CCZ 用法对）→ alpha 例外真实必要（ADR-0003）。升 alpha.5 须独立验证 slice，未盲 bump。
- **工具链基线**（建 `:app` 时按官方 URL 复核）：Kotlin 2.3.20（现 2.2.21）、AGP 9.2.0、Gradle 9.6.0（现 wrapper 9.4.1）、compileSdk/targetSdk 37、JDK 17 够用不必升 21。记入 `LOCAL_DEV.md` Version Baseline。
- **ADR-0002 引擎授权事实仍准确**：Unity runtime fee 已取消、Godot MIT、Unreal 5%——结论不动（论证基于问题形态非引擎价格）。

## 验证

全套本地门绿（`android/` 内）：`:game-core:test :native-content:test`、两模块 `runSelfTest`、`detektMain`/`detektTest`（0 findings）、`assertTestCountEqualsBaseline`(12)。改动走分支 `chore/rules-overhaul`，每批独立 commit。

## 未做 / 后续 slice（各有 cold-start 基准，不阻塞）

- **JSON loader 的 op 字符串白名单**：内存 op 已由 sealed type 白名单化；JSON 解码边界落地时补 loader 白名单 + 测试。
- **Save/Replay 信封**：Save 类、`save_schema_version`、拒未来版本测试、`rng_state` 序列化、rulesVersion 入信封——首个 Save 类同 PR 落地。
- **Converter 模块**：opcode 映射、fail-closed 定位、Evidence Rule 机器化——等真实 MOD 样本（不猜 opcode）。
- **CI 接线**：已落地（`.github/workflows/ci.yml`，JVM gate 在每次 push/PR 跑全套门）。
- **工具链升级**：Kotlin/Gradle/AGP/detekt-alpha.5 升级各走独立验证 slice。
