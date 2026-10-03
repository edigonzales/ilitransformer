# Embedded migration API

Publish `guru.interlis:ilitransformer-core:0.1.0-SNAPSHOT` with the regular Gradle
`publish` task, or use `check publishCorePublicationToMavenLocal` during development.
The thin jar excludes the CLI and language server entry points. It uses the same
compiler, plans and runtime as the CLI.

`MigrationDraftService` compiles source/target models, provides schema inventories,
compares same-name elements and prepares an explicit ilimap draft. Renames, incompatible
types, units and enum values remain review decisions; narrower target constraints
require data validation. Fingerprints cover actual model source contents. Drafts
must be reviewed before execution; no live auto-mapping takes place at runtime.

`MigrationService.prepare(Request)` returns the compiled job and diagnostics without
executing it. `execute(Request, Consumer<String>)` runs exactly one XTF input/output job
with optional path and model-directory overrides, strict failure policy, validation,
report directory and overwrite setting. Relative paths resolve against the mapping
file. Original/target aliases, unresolved paths and changes to the original are rejected.
Output is prepared beside its destination and validated before atomic publication.
No-overwrite publication uses an atomic hard link; unsupported filesystems fail safely.
Existing CLI publication defaults remain unchanged. Interrupts are checked between
processing steps and before commit. Compilation/ilivalidator use the ili2c Main.class
monitor so embedding hosts can coordinate global compiler state on that monitor.

The engine still holds source/target state in memory. One object is not an independent
job: references resolve across the entire input. Use a complete small sample for preview.
The API does not promise byte-identical XML or automatic preservation of unmapped fields.

`IlimapEditorDocument` applies source-range changes with revision checking and transactional
undo/redo. Parsing and failed graphical commands never rewrite unrelated source text.
`IlimapHighlighter` provides tolerant UTF-16 lexical spans independently from valid syntax.
These services have no SWT dependency. New embedded bag syntax is documented in
[ilimap-v2.md](ilimap-v2.md). Coordinate values now optionally retain Z; the existing
2D constructor remains source-compatible.
