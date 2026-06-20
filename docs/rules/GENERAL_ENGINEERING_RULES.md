# General Engineering Rules

本文件是跨项目通用规则。项目专属规则写到对应专属规则文件，不要混进这里。

## Core Idea

旧债可以逐步收口，新债不能继续制造。

不要为了数字洁癖乱拆；但改到哪里，哪里要变清楚、可测、可复算。复杂度不是靠文档解释过去，而是靠分层、helper、policy、resolver、测试和 CI 关进笼子里。

## Python / Backend Quality Gate

工具：`ruff`

规则：

```text
select = E, W, F, I, N, UP, B, C4, SIM, C901
target-version = py311
line-length = 120
McCabe complexity <= 10 recommended
McCabe complexity <= 15 tolerated during staged cleanup
```

要求：

- 新代码不得新增 `# noqa: C901` 复杂度屏蔽。
- 旧代码超标按职责拆分逐步收口，不要求一次性全改。
- 新增 Python 项目必须显式配置 Ruff，不能依赖默认值。

Ruff 官方依据：

```text
https://docs.astral.sh/ruff/configuration/
https://docs.astral.sh/ruff/rules/complex-structure/
```

## Kotlin / Android Quality Gate

工具：`detekt 2.0.0-alpha.3`

必须跑 type-resolving 任务。当前 JVM/Kotlin 模块：

```powershell
.\gradlew.bat --no-daemon :game-core:detektMain :native-content:detektMain
.\gradlew.bat --no-daemon :game-core:detektTest :native-content:detektTest
```

未来 Android app 建立后必须跑：

```powershell
.\gradlew.bat --no-daemon :app:detektGrayDebug :app:detektGrayDebugUnitTest
```

只启用 6 条 complexity 规则：

```text
LongMethod: 60
LargeClass: 600
LongParameterList.functionThreshold: 5
LongParameterList.constructorThreshold: 6
CyclomaticComplexMethod: 14
NestedBlockDepth: 4
TooManyFunctions.thresholdInFiles: 11
```

规则：

- baseline 是冻结旧债，不是新代码许可。
- 新代码和被改代码必须达标。
- 当前没有 `:app` 时，不伪造 app 任务；先让已有模块通过 type-resolving detekt。

Detekt 官方依据：

```text
https://detekt.dev/docs/gettingstarted/gradle/
https://detekt.dev/docs/gettingstarted/type-resolution/
```

## Android Extra Gates

Android app 建立后必须补齐：

```text
lintGrayDebug
assertAndroidTestCountEqualsBaseline
Room schema drift gate
R8 release build
apksigner fingerprint pin
```

Android lint / Room 官方依据：

```text
https://developer.android.com/studio/write/lint
https://developer.android.com/training/data-storage/room/migrating-db-versions
```

## PR Rules

- 一 PR 一议题。
- 不混无关改动。
- 跨 surface 尽量拆 PR：backend / Android / web / tools。
- 不设硬行数门槛。

约束点：

```text
单一议题
基线变更同 diff 声明
验证可复算
CI / audit lane / 对抗审能覆盖
```

## Commit Rules

采用 Conventional Commits：

```text
<type>[scope]: <description>
```

允许的 type：

```text
feat
fix
docs
refactor
test
chore
build
ci
perf
style
```

破坏性变更加 `!` 或 footer：

```text
BREAKING CHANGE: ...
```

## Backend Layering

固定分层：

```text
routes -> services -> models / providers
```

`routes`：

- 只做参数解析、鉴权、调用 service、返回 schema。
- 不写业务。
- 不拼复杂 SQL。
- 不返回原始异常。

`services`：

- 业务编排、事务、调用 provider。
- 不依赖 HTTP 层。
- 不写 UI 文案。
- 不硬编码凭证。

`models`：

- ORM 实体。
- 不依赖上层。

`schemas`：

- 请求/响应结构。
- 不放业务逻辑。
- 不放 IO。

`providers`：

- OCR / LLM / 分类 / 推送等可替换能力。
- 只做识别或建议。
- 不直接确认业务状态。

## Client Layering

固定分层：

```text
Screen -> ViewModel -> Repository -> ApiService / Dao / SecureStorage
```

`Screen`：

- 只做 UI 渲染和输入收集。

`ViewModel`：

- 管理 UI State。
- 调 Repository。

`Repository`：

- 协调远端、本地缓存、失败兜底。
- 返回领域模型。

`ApiService / Dao / SecureStorage`：

- 纯 IO。
- 不向 UI 暴露 DTO / Entity / Token。

## Model Boundaries

命名：

```text
XxxDto      远端 API 模型
XxxEntity   本地数据库模型
Xxx         领域模型
XxxMapper   集中转换
```

禁止：

- DTO / Entity 进 UI。
- UI 直连网络。
- route 直查 DB。
- 跨层调用。
- 业务逻辑散进 scripts。

## Directory Rules

通用顶层：

```text
project-root/
  README.md
  LICENSE
  .gitignore
  .env.example
  docs/
  scripts/
  backend/
  android/ 或 web/ 或 desktop/ 或 client/
  tests/
```

后端：

```text
backend/
  app/
    routes/
    services/
    models/
    schemas/
    providers/
    config.py 或 config/
    main.py 或 entrypoints/
  tests/
  migrations/
  scripts/
  requirements.txt
```

客户端：

```text
client/
  ui/{module}/
  viewmodel/{module}/
  repository/
  data/
    api/
    db/
    storage/
  domain/
  di/
  util/
```

文档：

```text
docs/
  rules/
  architecture/
  DECISIONS/
  runbook/
  roadmap/
  audits/
  assets/
```

## Naming Rules

- 目录 / 文件 / 字段 / API 路径：小写下划线。
- 类 / 类型：大驼峰。
- 常量 / 环境变量：大写下划线。
- 关键规范文档：大写下划线 `.md`。
- 普通文档：小写连字符 `.md`。

例子：

```text
backend/app/services/receipt_parse_amount.py
amount_cents
public_id
created_at
OCR_PROVIDER
ENGINEERING_RULES.md
postgres-migration-notes.md
```

## Data Format Rules

- 内部主键：`id`。
- 外部稳定标识：`public_id`。
- 普通 UI 不展示 id / UUID。

金额：

- 全链路用最小货币单位整数。
- 禁止 `float` / `double`。
- 单位换算集中封装。
- 禁止 UI 散写 `/ 100`。

时间：

- 数据库存 UTC。
- API 返回 ISO 8601。
- 字段固定 `created_at` / `updated_at` / `confirmed_at` 等。
- 客户端负责本地时区展示。

## API Format Rules

- 请求/响应字段命名固定，不跟 UI 文案变化。
- 排序 / 过滤字段必须白名单。
- API 不返回本机路径、内部 URL、traceback、底层英文异常。

分页统一：

```text
page
page_size
total
items
```

统一错误：

```json
{
  "error": "错误代码",
  "message": "中文说明"
}
```

常见错误码：

```text
invalid_token
invalid_request
not_found
method_not_allowed
file_too_large
unsupported_file_type
amount_required
state_conflict
rate_limited
server_error
```

## Runtime Artifacts

这些不能进 git：

```text
uploads/
data/
logs/
build/
dist/
本地虚拟环境
依赖缓存目录
```

路径必须走 config，不能写死。

## Forbidden

- 不在根目录散放代码、文档、图片。
- 不留 `v1` / `v2` / `old` / `backup` / `tmp` 残留。
- 不让 git 跟踪运行时产物。
- 不让业务代码进 scripts。
- 不让二进制文件散落 docs 根部，统一放 `docs/assets/`。
- 不把 UI、业务、基础设施搅进一个文件。
- 不靠前端隐藏代替后端鉴权。
- 不为“以后可能用得上”提前引入大框架。

## Windows / PowerShell Rules

- `scripts/*.ps1` 和 `backend/scripts/*.ps1` 必须 UTF-8 with BOM。
- `.env` 不带 BOM。
- PowerShell 读文件必须显式 `Get-Content -Encoding UTF8`。
- PowerShell 5.1 脚本不能用 `&&` / `||`。
- 用 `; if ($?) { ... }`。
- 不依赖 PowerShell 7 / WSL / Docker / Linux shell。

## Android String Rules

- 用户可见中文字面量走 `res/values/strings.xml`。
- Compose 中用 `stringResource`。
- 命名：`模块_位置_用途`。
- 只做 string-resourcing，不等于完整 i18n。
- 不建第二语言目录，除非另开 ADR。
