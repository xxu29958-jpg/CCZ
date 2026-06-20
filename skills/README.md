# Skills

可重复施工流程放这里。预期会被复用的过程沉淀成 skill，不要堆进 `HANDOFF.md` 或长聊天记录。

技能分两层（见 fork 自 xiaopiaojia 的方法论：可推导的 process 层端过来，挣来的领域层边跑边长）：

## Process 层（自 xiaopiaojia 跑通实践移植，领域无关）

- `ship-slice/` — 一个切片走完闭环：off 最新 base → 写码 → 跑真本地门全套 → 对抗审 → push → CI 分诊 → 合并（需授权）→ 同步文档。
- `adversarial-review/` — 外部评审缺位时多镜头自审 + finding 处置纪律（CCZ 第一镜头 = 确定性/回放）。
- `safe-code-change/` — 搬/改名/删/改签名前扫消费面 + 隐藏耦合（含确定性契约变更）。
- `ci-red-triage/` — 真红 vs flake 分诊，别 thrash PR。
- `android-detekt-discipline/` — 改 Kotlin 不踩 detekt 六阈值 / type-resolving / baseline 纪律。

## 领域层（CCZ 自己挣来，随项目长）

- `add-event-op/` — 新增 native event op：白名单校验 + 未知 op fail-closed + 两类测试 + 不猜 opcode。

> 新增领域 skill 的判据：一个流程在 CCZ 反复出现、且有「本地绿却悄悄咬人」的暗雷，就沉淀成 skill；候选（待真正反复出现再写）：内容包 schema 演进、回放不变量变更、Save/迁移、converter opcode 映射。
