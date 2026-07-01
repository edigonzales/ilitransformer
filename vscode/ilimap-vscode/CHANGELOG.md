# Changelog

## Unreleased

- **Rename refactoring (F2)**: safely rename local .ilimap symbols — input, output, rule, enum, source alias, join alias, bag, and ref — with scope-aware collision detection and automatic reference updates across the document.
- **Source-to-target trace**: click a target attribute in the coverage matrix or a source member in the source usage section to see which source attributes, roles, enum maps, and functions produce or consume it. The trace inspector shows dependencies, reverse usages, and flow steps.
- **Bidirectional editor-overview node-level sync**: moving the cursor to a target attribute, source alias, or `alias.member` expression highlights the corresponding node in the mapping overview. Fine-grained `data-node-id` markup enables reveal and scroll in both directions.

## 0.1.0

- Prepare the extension for official Visual Studio Marketplace and Open VSX publication.
- Document the supported ilimap editor features, local development workflow, and release process.
- Add hybrid Java runtime resolution with bundled-runtime fallback for published builds.
