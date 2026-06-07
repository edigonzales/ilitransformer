# Rename Plan: ilinexus → ili-transformer

**Status:** Executed in Phase 0 (2026-06-07)

## Decision

The project is renamed from `ilinexus` to `ili-transformer`.

## Rationale

- `ili-transformer` is immediately understandable as a generic INTERLIS transformation tool
- Good discoverability
- Fits CLI name: `ili-transformer`
- Not limited to DM01/DMAV
- The old name `ilinexus` served as a codename during initial scaffolding

## Executed Changes

| Change | Before | After |
|---|---|---|
| Project name | `ilinexus` | `ili-transformer` |
| Java package | `guru.interlis.ilinexus` | `guru.interlis.transformer` |
| CLI name | `ilinexus` | `ili-transformer` |
| Main class | `guru.interlis.ilinexus.app.CliMain` | `guru.interlis.transformer.app.CliMain` |

## Files Changed

- `settings.gradle` — `rootProject.name`
- `build.gradle` — `mainClass`
- All 23 Java source files — package declarations and imports
- All 5 Java test files — package declarations and imports
- `src/main/java/guru/interlis/ilinexus/` → `src/main/java/guru/interlis/transformer/`
- `src/test/java/guru/interlis/ilinexus/` → `src/test/java/guru/interlis/transformer/`
- `README.md`

## Alternative Names Considered

| Name | Assessment |
|---|---|
| `ili-transformer` | **chosen** — clear and generic |
| `ili-transform` | good as CLI name, less descriptive as project name |
| `interlis-transformer` | very clear, but longer |
| `ili-mapper` | good, but more DSL/mapping-focused |
| `ili-bridge` | nice, but less technically precise |
| `ili-crosswalk` | fits correlation tables, but less generic |
| `ilinexus` | ok as codename, unclear as tool name |

## Notes

- The rename was done as a single commit without mixing in functional changes
- No backward compatibility is maintained (the old package name is removed entirely)
- The CLI usage text now shows `ili-transformer` instead of `ilinexus`
