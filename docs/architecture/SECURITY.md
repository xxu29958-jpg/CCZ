# Security And Content Safety

## Content Loading

- Native content packs must be validated before runtime use.
- Unknown schema versions are rejected.
- Unknown event ops are rejected.
- Missing asset or data references are rejected.
- Coordinates and map dimensions are bounds-checked.

## Converter Safety

- Converter failures must fail closed.
- Unknown opcode must include source location in the error.
- Partial output must not be treated as a valid content pack.
- Legacy tools and forum attachments are input sources, not runtime dependencies.

## IP Boundary

- Do not bundle original game assets in the engine.
- Do not bundle MOD assets unless the user has rights to do so.
- Do not copy Star engine code into runtime.
- Keep converter outputs separate from engine source unless using sample/free assets.

