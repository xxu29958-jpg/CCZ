# General Engineering Rules

> 规则版本 v0.2.0，2026-06-22，自 xiaopiaojia 工程规范 v1.7.0 fork 后重锚到 CCZ。
> 本文件是跨项目通用规则。项目专属规则写到 `CCZ_ENGINE_RULES.md`，不要混进这里。
> 凡只对「后端 / 网络 API / 数据库」成立的规则，已下沉到文末 `## Appendix: Backend / Network API (deferred)`，CCZ 当前没有这些面（见 `docs/architecture/API.md`），引入前不启用。
> 变更管理见入口 `ENGINEERING_RULES.md`。

## Core Idea

旧债可以逐步收口，新债不能继续制造。

不要为了数字洁癖乱拆；但改到哪里，哪里要变清楚、可测、可复算。复杂度不是靠文档解释过去，而是靠分层、helper、policy、resolver、测试和 CI 关进笼子里。

能用测试 / validator / CI gate 检查的约束，优先做成机器门。**只有文字没有机器门的「必须」，是愿望不是约束**——要么补门，要么诚实标注「review 强制 / 尚未机器强制」。

## Python Quality Gate

适用范围：仓库内的 Python 代码（未来 converter / 运维脚本等）。当前 CCZ 主线无 Python；出现时按此配置，不依赖默认值。

工具：`ruff`

```text
select = E, W, F, I, N, UP, B, C4, SIM, C901
target-version = py311
line-length = 120
McCabe complexity <= 10 recommended
McCabe complexity <= 15 tolerated during staged cleanup
```

- 新代码不得新增 `# noqa: C901` 复杂度屏蔽。
- 旧代码超标按职责拆分逐步收口，不要求一次性全改。

官方依据：

```text
https://docs.astral.sh/ruff/configuration/
https://docs.astral.sh/ruff/rules/complex-structure/
```

## Kotlin / Android Quality Gate

工具：`detekt`（当前 pin 见 `android/build.gradle.kts`；用 alpha 预发布是显式例外，见 `docs/DECISIONS/0003-detekt-alpha-exception.md`，回收条件 = detekt 2.0 stable 即升正式）。

必须跑 **type-resolving** 任务，否则需要类型解析的规则（如 `LongParameterList`）会被**静默跳过**而非报错。当前 JVM/Kotlin 模块：

```powershell
.\gradlew.bat --no-daemon :game-core:detektMain :native-content:detektMain
.\gradlew.bat --no-daemon :game-core:detektTest :native-content:detektTest
```

未来 Android app 建立后跑（type-resolving 变体）：

```powershell
.\gradlew.bat --no-daemon :app:detektGrayDebug :app:detektGrayDebugUnitTest
```

启用的 complexity 规则与阈值的**唯一真相源**是 `android/config/detekt/detekt.yml`（当前六条：LongMethod / LargeClass / LongParameterList(function+constructor) / CyclomaticComplexMethod / NestedBlockDepth / TooManyFunctions）。规则文档**不复列数字**，避免漂移——改阈值改 `detekt.yml`。

- baseline 是冻结旧债，不是新代码许可。
- 新代码和被改代码必须达标。
- 超阈值的 baseline 冻结单元（文件 / 函数）可保持冻结，但**不得再往里加新功能**——要加先拆到达标边界再加。即「不在旧债之上叠新债」：baseline 是被动冻结，不是继续扩张的许可。
- 行数 / 参数数 / 函数数靠肉眼或对抗审都不可靠；以 type-resolving detekt 的判定为准。

官方依据：

```text
https://detekt.dev/docs/gettingstarted/gradle/
https://detekt.dev/docs/gettingstarted/type-resolution/
```

## Dependency Governance

- 版本集中管理：依赖清单 / 版本目录 / 锁文件 / Gradle version catalog 统一维护，不在散点写死版本。
- 禁止 alpha / beta / 停止维护 / 来源不清的依赖进入主线。**唯一例外必须有 ADR**，写明原因、风险、回收条件（当前唯一例外 = detekt 2.0 alpha，见 `0003-detekt-alpha-exception.md`）。
- 新增依赖前查官方文档、维护状态、许可证；结论写入 `docs/DECISIONS/`。
- 升级依赖必须跑：单测、关键构建、detekt/lint、依赖审计。

## Android Extra Gates

Android app 建立后必须补齐：

```text
lintGrayDebug
assertAndroidTestCountEqualsBaseline
Room schema drift gate（若用 Room）
R8 release build
apksigner fingerprint pin
emulator smoke test
```

### Version Baseline

具体版本基线（Kotlin / AGP / Gradle / compileSdk / JDK）见 `docs/runbook/LOCAL_DEV.md` 的 `## Version Baseline`（单一真相源，含官方 URL 与核实日期）。Google Play 的 targetSdk 最低线随平台滚动，建 `:app` 时按官方要求复核：

```text
https://developer.android.com/studio/write/lint
https://developer.android.com/training/data-storage/room/migrating-db-versions
https://developer.android.com/google/play/requirements/target-sdk
```

## PR Rules

- 一 PR 一议题，不混无关改动。
- 跨 surface 尽量拆 PR：game-core / native-content / app / converter / docs。
- 不设硬行数门槛；约束点是：单一议题、基线变更同 diff 声明、验证可复算、CI / audit lane / 对抗审能覆盖。

## Commit Rules

采用 Conventional Commits：`<type>[scope]: <description>`。

```text
feat fix docs refactor test chore build ci perf style
```

破坏性变更加 `!` 或 footer `BREAKING CHANGE: ...`。

## Module Boundaries

本项目强制 **高内聚、低耦合、边界清晰、可替换**：模块只暴露契约、隐藏实现；禁止跨级调用、把同一业务真相散进多处、把 UI / 业务 / IO 搅进一个文件。下列具体边界规则都挂在这条总纲之下。其中**模块间依赖方向**已机器门化 `[machine-gated]`：根 gradle `assertModuleDependencyDirection`（在 jvm-gate 与本地全量门跑）强制 `native-content / save-io / app → game-core` 单向 DAG，`game-core` 零内部依赖。模块集从 `settings.gradle.kts` 读取，新模块未登记进 allowed 即 fail（fail-closed），登记模块经 `project(":...")` 声明的反向/越界边即 fail。覆盖边界（诚实）：只识别 `include(":x")` / `project(":x")` 字面形式，不识别 type-safe `projects.*` accessor 等其它依赖形式——迁移须同步更新此门（见 `android/build.gradle.kts` 门注释）。内聚的其余面（同一业务真相不散多处、UI/业务/IO 不搅一处）仍 `[review-only]`——耦合的这些维度无法机器判定，detekt 的 LongMethod / LargeClass / TooManyFunctions 只是弱代理，靠 review 强制。

核心边界原则（CCZ 的固定分层在 `CCZ_ENGINE_RULES.md` §Game Core / §Native Content / 运行时分层）：

- **纯核模块持有权威逻辑**，不依赖框架（Android / JSON 库 / 网络）。CCZ 里是 `game-core`。
- **表现层只渲染 state/event、收集输入**，不持有第二套业务真相、不绕过核心改结果。
- **工具 / 转换器是离线的**，不进入运行时。
- 禁止跨级调用、把业务逻辑散进 `scripts/`、把 UI / 业务 / 基础设施搅进一个文件。

> 后端 `routes→services→models/providers` 与客户端 `Screen→ViewModel→Repository→ApiService/Dao/SecureStorage` 这两套网络型分层见文末附录——CCZ 客户端对话的是进程内 `game-core` 而非远端 API，引入网络后端前不适用。

## Model Boundaries

命名约定（有「远端模型 vs 本地存储 vs 领域模型」三态时适用）：

```text
XxxDto      远端 / 序列化模型
XxxEntity   本地持久化模型
Xxx         领域模型
XxxMapper   集中转换
```

禁止：原始 DTO / Entity 漏进 UI、跨层调用、UI 直接读写 IO。

> CCZ 当前是「单一离线内容包 + 内存战斗状态」，没有「远端 API vs 本地 DB」的双源问题，三态 Mapper 暂不强制；内容包模型见 `native-content`。

## Directory Rules

CCZ 真实顶层：

```text
ccz_tactics_engine/
  AGENTS.md
  HANDOFF.md
  README.md
  LICENSE
  .gitignore
  docs/
  scripts/            运维 / 构建 / 自检脚本（禁业务代码）
  skills/
  archive/            历史素材（当前空）
  android/            Gradle root
    settings.gradle.kts
    build.gradle.kts
    gradlew / gradlew.bat
    gradle/wrapper/
    config/detekt/
    game-core/
    native-content/
    app/              （未来）
  tools/              （未来）converter / validators
```

- **所有 gradle 命令从 `android/` 内运行**（`:game-core:detektMain` 等模块路径相对 `android/` 解析）。
- 文档分层：`docs/{rules,architecture,DECISIONS,runbook,roadmap,audits,assets}`。

> 通用 `backend/app/{routes,services,models,schemas,providers}` 目录树见文末附录。

## Naming Rules

- 目录 / 非源文件 / 字段 / 配置键：小写下划线。
- 类 / 类型：大驼峰。**Kotlin 源文件名随其主类用大驼峰**（如 `BattleResolver.kt`），这是「文件小写下划线」的语言级例外。
- 常量 / 环境变量：大写下划线。
- 关键规范文档：大写下划线 `.md`；普通文档：小写连字符 `.md`。

例子（CCZ 原生）：

```text
android/game-core/src/main/kotlin/com/ccz/core/battle/BattleResolver.kt
content_version          内容包版本字段
save_schema_version      存档 schema 版本字段
BattleRules              规则值对象类型
RNG_SEED                 常量
manifest.json units.json 内容包文件
docs/runbook/native-pack-validation-notes.md
```

## Comment Discipline

`[review-only]`（无机器门，靠 review 守）。注释服务可读性，不淹没代码。

- 注释解释 **WHY**——不变量、契约、fail-closed 的理由、为何不是显然写法；**不复述 WHAT**（代码已表达的别再用文字重说一遍）。
- 能用更清楚的命名 / 拆函数表达的，不靠注释补救——注释不是坏代码的创可贴。
- 承重的 WHY 锚到确定性契约（RNG 消费顺序、整数公式、save/replay 的 `rng_state` 随 state，见 `CCZ_ENGINE_RULES.md` §Battle Formula Rules / §Save / Replay）。范例：`Formula.kt` 的 `RULES_VERSION`（GoldenReplayTest 是 tripwire）、`BattleOps.kt` 的 `ScriptContext` KDoc（为何 map 可空、为何缺模板是拒不是崩）。
- 删代码同删其注释，不留孤儿注释 / 失效 TODO。

## Data & Determinism Rules

- **任何必须精确或可复现的值禁用 `float` / `double`，用整数**。在 CCZ 这条是承重不变量：战斗公式用浮点 = 回放不可复现，破坏回放契约（见 `CCZ_ENGINE_RULES.md` §Battle Formula Rules）。
- 单位 / 数值换算集中封装，UI 不散写 `÷N`、不散写取整。
- 稳定身份：内容包实体用显式 id 且**校验唯一**（见 `CCZ_ENGINE_RULES.md` §Native Content Pack）；版本身份用四元组 `engine_version / native_format_version / content_version / save_schema_version`（见 `docs/architecture/VERSION.md`）。普通 UI 不展示内部 id。

> 关系数据库主键 `id/public_id`、UTC 存储 + ISO 8601 API、`created_at/updated_at` 时间字段规则见文末附录——CCZ 无数据库、无返时间戳的 API。

## Contract Rules

CCZ 的「API」是进程内 command / event / save-schema 契约（见 `docs/architecture/API.md`）：

- 契约字段命名固定，不随 UI 文案变化。
- 契约 / 错误信息不返回本机路径、内部 URL、traceback、底层英文异常。
- 错误词汇锚到 CCZ 真实失败模式，不发明网络面错误码：

```text
unknown_opcode          未知 opcode（fail closed）
unsupported_version      内容包 / 存档版本不支持（拒绝）
content_validation_error 内容包校验失败（定位到文件 / 字段）
```

> HTTP 分页（`page/page_size/total/items`）、JSON 错误信封、网络错误码（`invalid_token/rate_limited/...`）见文末附录。

## Runtime Artifacts

不进 git：

```text
build/  dist/  logs/  .gradle/
本地虚拟环境 / 依赖缓存目录
converter 中间产物 / 生成的 native-pack 缓存
```

路径走 config，不写死。

## Forbidden

- 不在根目录散放代码、文档、图片。
- 不留 `v1/v2/old/backup/tmp` 残留。
- 不让 git 跟踪运行时产物。
- 不让业务代码进 `scripts/`。
- 不把 UI、业务、基础设施搅进一个文件。
- 不为「以后可能用得上」提前引入大框架。
- 二进制资产统一放 `docs/assets/`，不散落 `docs/` 根部或仓库根。

## Windows / PowerShell Rules

- `scripts/*.ps1` 必须 UTF-8 with BOM；`.env` 不带 BOM。
- PowerShell 读文件必须显式 `Get-Content -Encoding UTF8`（PS 5.1 无 BOM 默认按 ANSI 解析，中文乱码）。
- PowerShell 5.1 不能用 `&&` / `||` 链接；用 `; if ($?) { ... }`。注意：native 命令（含 git）写 stderr 会让 PS 把 `$?` 置 false，`if ($?)` 链可能误跳——git 操作优先用 Bash 或不靠 `$?` 链。
- 不依赖 PowerShell 7 / WSL / Docker / Linux shell。

## Kotlin / Android String Rules

（Android app 建立后适用）

- 用户可见中文字面量走 `res/values/strings.xml`，Compose 用 `stringResource`。
- 命名 `模块_位置_用途`。
- 只做 string-resourcing，不建第二语言目录、不等于完整 i18n（要翻译另开 ADR）。

---

## Appendix: Backend / Network API (deferred)

> 以下规则只对「有网络后端 / HTTP API / 关系数据库」的项目成立。CCZ 当前没有这些面（`docs/architecture/API.md`：no network API）。**引入 backend 前不启用；引入时先开 ADR**，并把相关条目上提到正文。保留在此是为了 fork 来源的完整性与未来可复用，不是当前约束。

### Backend Layering

固定分层 `routes → services → models / providers`：

- `routes`：参数解析、鉴权、调用 service、返回 schema；不写业务、不拼复杂 SQL、不返回原始异常。
- `services`：业务编排、事务、调用 provider；不依赖 HTTP 层、不写 UI 文案、不硬编码凭证。
- `models`：ORM 实体，不依赖上层。
- `schemas`：请求 / 响应结构，不放业务、不放 IO。
- `providers`：OCR / LLM / 分类 / 推送等可替换能力，只做识别或建议，不直接确认业务状态。

### Network Client Layering

`Screen → ViewModel → Repository → ApiService / Dao / SecureStorage`：

- `Repository` 协调远端、本地缓存、失败兜底，返回领域模型。
- `ApiService / Dao / SecureStorage` 纯 IO，不向 UI 暴露 DTO / Entity / Token。
- 凭证进系统级安全存储；客户端生物识别只解锁本地状态，不替代服务端鉴权。

### HTTP API Format

分页统一：

```text
page  page_size  total  items
```

统一错误：

```json
{ "error": "错误代码", "message": "中文说明" }
```

```text
invalid_token  invalid_request  not_found  method_not_allowed
file_too_large  unsupported_file_type  amount_required
state_conflict  rate_limited  server_error
```

API 必须有版本策略；破坏性变更 bump 大版本；排序 / 过滤字段白名单化；不返回本机路径 / traceback。

### Persistence & Time

- 内部主键 `id` + 外部稳定标识 `public_id`（UUID）；普通 UI 不展示。
- 金额全链路用最小货币单位整数（`amount_cents`），禁 `float/double`，换算集中封装。
- DB 存 UTC，API 返 ISO 8601；字段固定 `created_at / updated_at / confirmed_at` 等；客户端负责本地时区显示。
- schema 变更走迁移工具，附可执行回滚；新增非空列三步走（可空/默认 → 回填 → 收紧）。
