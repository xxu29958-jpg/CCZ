# Security And Content Safety

> 标注约定与 `docs/rules/CCZ_ENGINE_RULES.md` 一致：`[enforced]` = 当前 `ContentValidator` 已实现并有测试；`[pending]` = 规则要求但代码尚未实现，**不写成现在时断言**。

## Content Loading

- Native content packs must be validated before runtime use.
- `[enforced]` Unknown native format versions are rejected (`native_format_version`).
- `[enforced]` Missing data references are rejected (unit→class/skill/item, class→counter/skill, map→terrain; `unknownReferencesFailClosed`).
- `[enforced]` Coordinates and map dimensions are bounds-checked (size, tile shape, spawn bounds).
- `[enforced]` Event reference integrity (unit / item references in S-scripts) is validated by `ContentEventValidator` (`eventReferencesFailClosed`). Op / trigger sets are whitelisted by Kotlin sealed types in memory; a string-keyed op whitelist applies at a future JSON decode boundary.
- `[pending]` Missing asset references (sprites / portraits / audio) must be rejected — not yet validated; to be enforced when an asset index exists.
- `[pending]` Unknown enums must be rejected at the JSON decode boundary — pending a JSON loader.

## Converter Safety

> `[pending]`：converter 模块尚未入仓。以下为 converter 落地时的契约，不是当前已运行的保护。

- Converter failures must fail closed.
- Unknown opcode must include source location in the error (复用 `ValidationIssue` 的 path-keyed 范式)。
- Partial output must not be treated as a valid content pack.
- Legacy tools and forum attachments are input sources, not runtime dependencies.

## IP Boundary

- Do not bundle original game assets in the engine.
- Do not bundle MOD assets unless the user has rights to do so.
- Do not copy Star engine code into runtime.
- Keep converter outputs separate from engine source unless using sample/free assets.
