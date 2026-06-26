import type {
  IlimapAssignmentSummary,
  IlimapBagSummary,
  IlimapCoverageAttributeSummary,
  IlimapCoverageClassSummary,
  IlimapDiagnosticSummary,
  IlimapEnumMapSummary,
  IlimapExpressionDependencySummary,
  IlimapExpressionSummary,
  IlimapJoinSummary,
  IlimapLocation,
  IlimapLossSummary,
  IlimapMappingInputSummary,
  IlimapMappingOutputSummary,
  IlimapMappingSummary,
  IlimapMetadataSummary,
  IlimapRefSummary,
  IlimapRuleCoverageSummary,
  IlimapRuleDetailSummary,
  IlimapRuleSummary,
  IlimapSourceDetailSummary,
  IlimapTargetDetailSummary,
  IlimapWithLocation
} from './mappingOverviewMessages';
import type { MappingOverviewRenderState } from './mappingOverviewState';

export function renderMappingOverviewHtml(
  summary: IlimapMappingSummary,
  nonce: string,
  renderState?: MappingOverviewRenderState,
  selectedRuleDetail?: IlimapRuleDetailSummary
): string {
  const title = summary.available ? summary.mappingName || 'mapping' : 'Mapping unavailable';
  const diagnosticText = diagnosticsLabel(summary);
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${escapeAttribute(nonce)}'; script-src 'nonce-${escapeAttribute(nonce)}';">
  <title>ilimap Mapping Overview</title>
  <style nonce="${escapeAttribute(nonce)}">
    :root {
      color-scheme: light dark;
    }

    body {
      margin: 0;
      padding: 24px;
      color: var(--vscode-foreground);
      background: var(--vscode-editor-background);
      font-family: var(--vscode-font-family);
      font-size: var(--vscode-font-size);
    }

    main {
      max-width: 1120px;
      margin: 0 auto;
    }

    h1,
    h2 {
      margin: 0;
      font-weight: 600;
      letter-spacing: 0;
    }

    h1 {
      font-size: 24px;
      line-height: 1.25;
    }

    h2 {
      font-size: 15px;
      line-height: 1.35;
    }

    .header {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 16px;
      align-items: baseline;
      justify-content: space-between;
      margin-bottom: 20px;
    }

    .subtitle,
    .muted {
      color: var(--vscode-descriptionForeground);
    }

    .summary-grid {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(128px, 1fr));
      gap: 8px;
      margin-bottom: 24px;
    }

    .metric {
      min-height: 72px;
      padding: 12px;
      border: 1px solid var(--vscode-panel-border);
      border-radius: 6px;
      background: var(--vscode-editorWidget-background);
    }

    .metric-value {
      display: block;
      margin-top: 8px;
      font-size: 24px;
      line-height: 1;
      font-weight: 650;
    }

    .sections {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
      gap: 16px;
    }

    section {
      min-width: 0;
      padding-top: 12px;
      border-top: 1px solid var(--vscode-panel-border);
    }

    ul {
      list-style: none;
      margin: 10px 0 0;
      padding: 0;
    }

    li {
      display: grid;
      grid-template-columns: minmax(0, 1fr) auto;
      gap: 8px;
      padding: 8px 0;
      border-bottom: 1px solid var(--vscode-panel-border);
    }

    .name,
    .detail {
      min-width: 0;
      overflow-wrap: anywhere;
    }

    .name {
      font-weight: 600;
    }

    a.name {
      color: var(--vscode-textLink-foreground);
      text-decoration: none;
    }

    a.name:hover {
      text-decoration: underline;
    }

    .detail {
      margin-top: 2px;
      color: var(--vscode-descriptionForeground);
      font-size: 12px;
      line-height: 1.4;
    }

    .tag {
      align-self: start;
      min-width: 54px;
      padding: 2px 7px;
      border: 1px solid var(--vscode-badge-background);
      border-radius: 999px;
      color: var(--vscode-badge-foreground);
      background: var(--vscode-badge-background);
      text-align: center;
      font-size: 11px;
      line-height: 1.4;
      text-transform: uppercase;
    }

    .tag.warning {
      border-color: var(--vscode-editorWarning-foreground);
      color: var(--vscode-editorWarning-foreground);
      background: transparent;
    }

    .tag.error {
      border-color: var(--vscode-editorError-foreground);
      color: var(--vscode-editorError-foreground);
      background: transparent;
    }

    .empty {
      margin: 10px 0 0;
      color: var(--vscode-descriptionForeground);
    }

    .status-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 8px 16px;
      align-items: baseline;
      justify-content: space-between;
      margin: -8px 0 20px;
    }

    .status {
      color: var(--vscode-descriptionForeground);
      font-size: 12px;
      line-height: 1.4;
      overflow-wrap: anywhere;
    }

    .status.status-loading {
      color: var(--vscode-foreground);
    }

    .status.status-stale {
      color: var(--vscode-editorWarning-foreground);
    }

    .status.status-error {
      color: var(--vscode-editorError-foreground);
    }

    a.refresh-link {
      color: var(--vscode-textLink-foreground);
      text-decoration: none;
      font-size: 12px;
    }

    a.refresh-link:hover {
      text-decoration: underline;
    }

    .rule-inspector {
      margin-top: 24px;
      padding-top: 12px;
      border-top: 2px solid var(--vscode-panel-border);
    }

    .rule-inspector h2 {
      margin-bottom: 12px;
    }

    .inspector-section {
      margin-bottom: 16px;
    }

    .inspector-section h3 {
      margin: 0 0 6px;
      font-size: 13px;
      font-weight: 600;
      color: var(--vscode-descriptionForeground);
    }

    .inspector-section p {
      margin: 0;
    }

    .inspector-section code {
      font-family: var(--vscode-editor-font-family, monospace);
      font-size: 12px;
      background: var(--vscode-textCodeBlock-background);
      padding: 1px 4px;
      border-radius: 3px;
    }

    .inspector-section ul {
      margin: 4px 0 0;
    }

    .inspector-section li {
      display: flex;
      flex-wrap: wrap;
      gap: 6px;
      align-items: baseline;
      padding: 4px 0;
    }

    .inspector-section li code {
      white-space: pre-wrap;
      word-break: break-all;
    }

    .tag-copy {
      border-color: var(--vscode-charts-green);
      color: var(--vscode-charts-green);
    }

    .tag-computed {
      border-color: var(--vscode-charts-blue);
      color: var(--vscode-charts-blue);
    }

    .tag-constant {
      border-color: var(--vscode-charts-purple);
      color: var(--vscode-charts-purple);
    }

    .tag-enumMap {
      border-color: var(--vscode-charts-orange);
      color: var(--vscode-charts-orange);
    }

    .tag-null {
      border-color: var(--vscode-descriptionForeground);
      color: var(--vscode-descriptionForeground);
    }

    .tag-unknown {
      border-color: var(--vscode-descriptionForeground);
      color: var(--vscode-descriptionForeground);
    }

    .bag-item {
      margin-left: 16px;
      margin-bottom: 8px;
    }

    .bag-item p {
      margin: 0 0 4px;
    }

    .loss-section li {
      color: var(--vscode-editorWarning-foreground);
    }

    .tag-mapped {
      border-color: var(--vscode-charts-green);
      color: var(--vscode-charts-green);
    }

    .tag-default {
      border-color: var(--vscode-charts-yellow);
      color: var(--vscode-charts-yellow);
    }

    .tag-bag,
    .tag-ref {
      border-color: var(--vscode-charts-blue);
      color: var(--vscode-charts-blue);
    }

    .tag-missing {
      border-color: var(--vscode-editorError-foreground);
      color: var(--vscode-editorError-foreground);
      background: transparent;
    }

    .rule-coverage,
    .source-usage {
      margin-top: 24px;
      padding-top: 12px;
      border-top: 1px solid var(--vscode-panel-border);
    }

    .filter-bar {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 12px;
      margin: 10px 0 4px;
    }

    a.filter-link {
      color: var(--vscode-textLink-foreground);
      text-decoration: none;
      font-size: 12px;
    }

    a.filter-link:hover {
      text-decoration: underline;
    }

    a.filter-link.active {
      font-weight: 700;
      text-decoration: underline;
    }

    .coverage-rule {
      margin-top: 16px;
    }

    .coverage-rule-header {
      display: flex;
      flex-wrap: wrap;
      gap: 6px 12px;
      align-items: baseline;
      margin-bottom: 6px;
    }

    table.coverage-matrix {
      width: 100%;
      border-collapse: collapse;
      font-size: 12px;
    }

    table.coverage-matrix th,
    table.coverage-matrix td {
      padding: 4px 8px;
      text-align: left;
      vertical-align: top;
      border-bottom: 1px solid var(--vscode-panel-border);
      overflow-wrap: anywhere;
    }

    table.coverage-matrix th {
      color: var(--vscode-descriptionForeground);
      font-weight: 600;
    }

    table.coverage-matrix td code {
      font-family: var(--vscode-editor-font-family, monospace);
      background: var(--vscode-textCodeBlock-background);
      padding: 1px 4px;
      border-radius: 3px;
      white-space: pre-wrap;
      word-break: break-all;
    }

    tr.coverage-row-missing {
      background: color-mix(in srgb, var(--vscode-editorError-foreground) 12%, transparent);
    }

    .req-marker {
      color: var(--vscode-editorError-foreground);
      font-weight: 700;
    }

    .rule-coverage[data-active-filter="missing"] tbody tr:not([data-missing]),
    .rule-coverage[data-active-filter="mandatory"] tbody tr:not([data-mandatory]),
    .source-usage[data-active-filter="used"] li[data-usage-status="unused"],
    .source-usage[data-active-filter="unused"] li[data-usage-status="used"] {
      display: none;
    }

    .usage-group {
      margin-top: 14px;
    }

    .usage-header {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 10px;
      align-items: baseline;
      margin-bottom: 4px;
    }

    .usage-members {
      margin: 2px 0 2px 12px;
    }

    .usage-title {
      color: var(--vscode-descriptionForeground);
      font-size: 12px;
    }

    ul.usage-list {
      display: flex;
      flex-wrap: wrap;
      gap: 4px 10px;
      margin: 2px 0 0;
    }

    ul.usage-list li {
      display: inline-flex;
      gap: 4px;
      align-items: baseline;
      padding: 2px 0;
      border: 0;
    }
  </style>
</head>
<body>
  <main>
    <div class="header">
      <div>
        <h1>ilimap Mapping Overview</h1>
        <div class="subtitle">${escapeHtml(title)}</div>
      </div>
      <div class="muted">${escapeHtml(diagnosticText)}</div>
    </div>
    ${renderStatusBar(renderState)}
    ${summary.available ? renderAvailableSummary(summary, selectedRuleDetail) : renderUnavailable(summary)}
  </main>
  <script nonce="${escapeAttribute(nonce)}">
    const vscode = typeof acquireVsCodeApi === 'function' ? acquireVsCodeApi() : undefined;
    document.addEventListener('click', event => {
      if (!(event.target instanceof Element)) {
        return;
      }
      const filterTarget = event.target.closest('[data-filter-value]');
      if (filterTarget) {
        event.preventDefault();
        const wrapperClass = filterTarget.getAttribute('data-filter-target');
        const wrapper = wrapperClass ? filterTarget.closest('.' + wrapperClass) : null;
        if (wrapper) {
          wrapper.setAttribute('data-active-filter', filterTarget.getAttribute('data-filter-value') || 'all');
          wrapper.querySelectorAll('.filter-bar [data-filter-value]').forEach(link => link.classList.remove('active'));
          filterTarget.classList.add('active');
        }
        return;
      }
      if (!vscode) {
        return;
      }
      const refreshTarget = event.target.closest('[data-action="refresh"]');
      if (refreshTarget) {
        event.preventDefault();
        vscode.postMessage({ type: 'refresh' });
        return;
      }
      const inspectTarget = event.target.closest('[data-action="inspect-rule"]');
      if (inspectTarget) {
        event.preventDefault();
        vscode.postMessage({
          type: 'requestRuleDetail',
          ruleId: inspectTarget.getAttribute('data-rule-id')
        });
        return;
      }
      const target = event.target.closest('[data-nav-line]');
      if (!target) {
        return;
      }
      event.preventDefault();
      if (target.hasAttribute('data-nav-end-line')) {
        vscode.postMessage({
          type: 'navigateToLocation',
          location: {
            line: Number(target.getAttribute('data-nav-line')),
            character: Number(target.getAttribute('data-nav-character')),
            endLine: Number(target.getAttribute('data-nav-end-line')),
            endCharacter: Number(target.getAttribute('data-nav-end-character'))
          }
        });
      } else {
        vscode.postMessage({
          type: 'navigate',
          line: Number(target.getAttribute('data-nav-line')),
          character: Number(target.getAttribute('data-nav-character'))
        });
      }
    });
  </script>
</body>
</html>`;
}

export function escapeHtml(value: unknown): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

function escapeAttribute(value: unknown): string {
  return escapeHtml(value);
}

function renderAvailableSummary(summary: IlimapMappingSummary, selectedRuleDetail?: IlimapRuleDetailSummary): string {
  return `${renderMetrics(summary)}
    <div class="sections">
      ${renderInputs(summary.inputs)}
      ${renderOutputs(summary.outputs)}
      ${renderEnumMaps(summary.enumMaps)}
      ${renderRules(summary.rules)}
      ${renderDiagnostics(summary.diagnostics)}
      ${renderCoverage(summary)}
    </div>
    ${renderRuleCoverageSection(summary)}
    ${renderSourceUsage(summary)}
    ${renderRuleInspector(selectedRuleDetail)}`;
}

function renderUnavailable(summary: IlimapMappingSummary): string {
  return `<section>
      <h2>Overview</h2>
      <p class="empty">${escapeHtml(summary.message || 'No mapping summary is available.')}</p>
    </section>`;
}

function renderMetrics(summary: IlimapMappingSummary): string {
  const metrics: [string, number | string][] = [
    ['Inputs', summary.inputCount],
    ['Outputs', summary.outputCount],
    ['Rules', summary.ruleCount],
    ['Enum Maps', summary.enumMapCount],
    ['Bags', summary.bagCount],
    ['Refs', summary.refCount],
    ['Diagnostics', summary.errorCount + summary.warningCount + summary.informationCount + summary.hintCount]
  ];
  return `<div class="summary-grid">
      ${metrics
        .map(
          ([label, value]) => `<div class="metric">
        <span class="muted">${escapeHtml(label)}</span>
        <span class="metric-value">${escapeHtml(value)}</span>
      </div>`
        )
        .join('')}
    </div>`;
}

function renderInputs(inputs: IlimapMappingInputSummary[]): string {
  return renderSection(
    'Inputs',
    inputs,
    input => renderNavName(input.id, input),
    input => [input.path, input.model, input.format].filter(Boolean).join(' · '),
    () => ''
  );
}

function renderOutputs(outputs: IlimapMappingOutputSummary[]): string {
  return renderSection(
    'Outputs',
    outputs,
    output => renderNavName(output.id, output),
    output => [output.path, output.model, output.format].filter(Boolean).join(' · '),
    () => ''
  );
}

function renderEnumMaps(enumMaps: IlimapEnumMapSummary[]): string {
  return renderSection(
    'Enum Maps',
    enumMaps,
    enumMap => renderNavName(enumMap.id, enumMap),
    enumMap => `${enumMap.entryCount} entries`,
    () => ''
  );
}

function renderRules(rules: IlimapRuleSummary[]): string {
  return renderSection(
    'Rules',
    rules,
    rule => `${renderNavName(rule.id, rule)} ${renderInspectLink(rule.id)}`,
    rule =>
      [
        rule.targetClass ? `target ${rule.targetClass}` : '',
        `${rule.sourceCount} sources`,
        `${rule.assignmentCount} assignments`,
        `${rule.bagCount} bags`,
        `${rule.refCount} refs`
      ]
        .filter(Boolean)
        .join(' · '),
    rule => `<span class="tag ${statusClass(rule.status)}">${escapeHtml(rule.status)}</span>`
  );
}

function renderDiagnostics(diagnostics: IlimapDiagnosticSummary[]): string {
  return renderSection(
    'Diagnostics',
    diagnostics,
    diagnostic => renderNavName(diagnostic.message, diagnostic),
    diagnostic => `${diagnostic.code} · ${diagnostic.severity} · ${diagnostic.line + 1}:${diagnostic.character + 1}`,
    diagnostic => `<span class="tag ${statusClass(diagnostic.severity)}">${escapeHtml(diagnostic.severity)}</span>`
  );
}

function renderCoverage(summary: IlimapMappingSummary): string {
  if (!summary.coverageAvailable) {
    return `<section>
      <h2>Coverage</h2>
      <p class="empty">${escapeHtml(summary.coverageMessage || 'Model coverage unavailable.')}</p>
    </section>`;
  }
  return renderClassCoverage(summary.classCoverage ?? []);
}

function renderRuleCoverageSection(summary: IlimapMappingSummary): string {
  if (!summary.coverageAvailable) {
    return '';
  }
  return renderRuleCoverage(summary.ruleCoverage ?? []);
}

function renderClassCoverage(classes: IlimapCoverageClassSummary[]): string {
  return renderSection(
    'Class Coverage',
    classes,
    item => renderNavName(item.className, item),
    item =>
      [
        item.outputId,
        item.targeted ? `rules ${item.ruleIds.join(', ')}` : 'not targeted',
        `${item.assignedAttributeCount}/${item.attributeCount} attributes`,
        item.mandatoryMissingCount > 0 ? `${item.mandatoryMissingCount} mandatory missing` : ''
      ]
        .filter(Boolean)
        .join(' · '),
    item => `<span class="tag ${item.mandatoryMissingCount > 0 ? 'warning' : ''}">${item.targeted ? 'mapped' : 'open'}</span>`
  );
}

function renderRuleCoverage(rules: IlimapRuleCoverageSummary[]): string {
  if (rules.length === 0) {
    return `<section class="rule-coverage" data-active-filter="all">
      <h2>Rule Coverage</h2>
      <p class="empty">None</p>
    </section>`;
  }
  return `<section class="rule-coverage" data-active-filter="all">
      <h2>Rule Coverage</h2>
      ${renderCoverageFilterBar()}
      ${rules.map(renderRuleCoverageItem).join('')}
    </section>`;
}

function renderCoverageFilterBar(): string {
  return `<div class="filter-bar">
      ${filterLink('rule-coverage', 'all', 'All attributes', true)}
      ${filterLink('rule-coverage', 'missing', 'Missing only', false)}
      ${filterLink('rule-coverage', 'mandatory', 'Mandatory only', false)}
    </div>`;
}

function renderRuleCoverageItem(rule: IlimapRuleCoverageSummary): string {
  const missing = rule.attributes.filter(attribute => attribute.mandatory && !attribute.assigned);
  return `<div class="coverage-rule">
        <div class="coverage-rule-header">
          <span class="name">${renderNavName(rule.ruleId, rule)}</span>
          <span class="detail">${escapeHtml([
            rule.targetClass,
            `${rule.directAssignmentCount} direct assignments`,
            `${rule.bagAssignmentCount} bag assignments`,
            rule.refs.length > 0 ? `refs ${rule.refs.join(', ')}` : ''
          ].filter(Boolean).join(' · '))}</span>
          <span class="tag ${missing.length > 0 ? 'warning' : ''}">${missing.length > 0 ? 'gaps' : 'ok'}</span>
        </div>
        ${renderTargetCoverageMatrix(rule)}
      </div>`;
}

function renderTargetCoverageMatrix(rule: IlimapRuleCoverageSummary): string {
  if (rule.attributes.length === 0) {
    return '<p class="empty">No target attributes.</p>';
  }
  return `<table class="coverage-matrix">
        <thead>
          <tr>
            <th>Attribute</th>
            <th>Status</th>
            <th>Type</th>
            <th>Cardinality</th>
            <th>Source / Expression</th>
          </tr>
        </thead>
        <tbody>
          ${rule.attributes.map(renderCoverageAttributeRow).join('')}
        </tbody>
      </table>`;
}

function renderCoverageAttributeRow(attribute: IlimapCoverageAttributeSummary): string {
  const status = coverageStatus(attribute);
  const missing = attribute.mandatory && !attribute.assigned;
  const rowAttrs = `${missing ? ' data-missing="true"' : ''}${attribute.mandatory ? ' data-mandatory="true"' : ''}`;
  const source = attribute.sourceSummary || attribute.expression || '';
  return `<tr class="${missing ? 'coverage-row-missing' : ''}"${rowAttrs}>
            <td>${renderNavName(attribute.name, attribute)}${
              attribute.mandatory ? ' <span class="req-marker" title="mandatory">*</span>' : ''
            }</td>
            <td><span class="tag ${escapeAttribute(coverageStatusClass(status))}">${escapeHtml(status)}</span></td>
            <td class="detail">${escapeHtml(attribute.type)}</td>
            <td class="detail">${escapeHtml(attribute.cardinality)}</td>
            <td>${source ? `<code>${escapeHtml(source)}</code>` : '<span class="detail">—</span>'}</td>
          </tr>`;
}

function coverageStatus(attribute: IlimapCoverageAttributeSummary): string {
  if (attribute.status) {
    return attribute.status;
  }
  if (attribute.assigned) {
    return 'mapped';
  }
  if (attribute.mandatory) {
    return 'missing';
  }
  return 'unknown';
}

function coverageStatusClass(status: string): string {
  if (status === 'missing') {
    return 'tag-missing warning';
  }
  return `tag-${status}`;
}

interface NormalizedUsageMember extends IlimapWithLocation {
  name: string;
  kind: string;
  status: string;
}

interface NormalizedSourceUsageGroup extends IlimapWithLocation {
  inputIds: string[];
  sourceClass: string;
  aliases: string[];
  attributes: NormalizedUsageMember[];
  roles: NormalizedUsageMember[];
}

function renderSourceUsage(summary: IlimapMappingSummary): string {
  const groups = sourceUsageGroups(summary);
  if (groups.length === 0) {
    return `<section class="source-usage" data-active-filter="all">
      <h2>Source Usage</h2>
      <p class="empty">None</p>
    </section>`;
  }
  return `<section class="source-usage" data-active-filter="all">
      <h2>Source Usage</h2>
      ${renderSourceUsageFilterBar()}
      ${groups.map(renderSourceUsageGroup).join('')}
    </section>`;
}

function renderSourceUsageFilterBar(): string {
  return `<div class="filter-bar">
      ${filterLink('source-usage', 'all', 'All', true)}
      ${filterLink('source-usage', 'used', 'Used only', false)}
      ${filterLink('source-usage', 'unused', 'Unused only', false)}
    </div>`;
}

function sourceUsageGroups(summary: IlimapMappingSummary): NormalizedSourceUsageGroup[] {
  const grouped = summary.sourceUsage ?? [];
  if (grouped.length > 0) {
    return grouped.map(group => ({
      inputIds: group.inputIds ?? [],
      sourceClass: group.sourceClass,
      aliases: group.aliases ?? [],
      location: group.location,
      attributes: (group.attributes ?? []).map(toNormalizedMember),
      roles: (group.roles ?? []).map(toNormalizedMember)
    }));
  }
  const map = new Map<string, NormalizedSourceUsageGroup>();
  for (const rule of summary.ruleCoverage ?? []) {
    for (const source of rule.sources ?? []) {
      const inputIds = source.inputIds ?? [];
      const key = `${source.sourceClass}\n${inputIds.join(',')}`;
      let group = map.get(key);
      if (!group) {
        group = {
          inputIds,
          sourceClass: source.sourceClass,
          aliases: [],
          location: source.location,
          attributes: [],
          roles: []
        };
        map.set(key, group);
      }
      if (source.alias && !group.aliases.includes(source.alias)) {
        group.aliases.push(source.alias);
      }
      for (const name of source.usedAttributes ?? []) {
        if (!group.attributes.some(member => member.name === name)) {
          group.attributes.push({ name, kind: 'attribute', status: 'used' });
        }
      }
      for (const name of source.usedRoles ?? []) {
        if (!group.roles.some(member => member.name === name)) {
          group.roles.push({ name, kind: 'role', status: 'used' });
        }
      }
    }
  }
  return Array.from(map.values());
}

function toNormalizedMember(member: {
  name: string;
  kind: string;
  status: string;
  location?: IlimapLocation;
}): NormalizedUsageMember {
  return { name: member.name, kind: member.kind, status: member.status, location: member.location };
}

function renderSourceUsageGroup(group: NormalizedSourceUsageGroup): string {
  const detail = [
    group.inputIds.length > 0 ? `input ${group.inputIds.join(', ')}` : '',
    group.aliases.length > 0 ? `alias ${group.aliases.join(', ')}` : ''
  ]
    .filter(Boolean)
    .join(' · ');
  return `<div class="usage-group">
        <div class="usage-header">
          <span class="name">${renderNavName(group.sourceClass, group)}</span>
          ${detail ? `<span class="detail">${escapeHtml(detail)}</span>` : ''}
        </div>
        ${renderUsageMembers('Attributes', group.attributes)}
        ${renderUsageMembers('Roles', group.roles)}
      </div>`;
}

function renderUsageMembers(title: string, members: NormalizedUsageMember[]): string {
  if (members.length === 0) {
    return `<div class="usage-members"><span class="usage-title">${escapeHtml(title)}:</span> <span class="detail">None</span></div>`;
  }
  return `<div class="usage-members">
        <span class="usage-title">${escapeHtml(title)}:</span>
        <ul class="usage-list">
          ${members.map(renderUsageMember).join('')}
        </ul>
      </div>`;
}

function renderUsageMember(member: NormalizedUsageMember): string {
  const status = member.status || 'used';
  const filterStatus = status === 'unused' ? 'unused' : 'used';
  return `<li data-usage-status="${escapeAttribute(filterStatus)}">
            <span class="name">${renderNavName(member.name, member)}</span>
            <span class="tag ${escapeAttribute(usageStatusClass(status))}">${escapeHtml(status)}</span>
          </li>`;
}

function usageStatusClass(status: string): string {
  if (status === 'unused') {
    return 'tag-unknown';
  }
  if (status === 'used') {
    return 'tag-mapped';
  }
  return 'tag-computed';
}

function filterLink(target: string, value: string, label: string, active: boolean): string {
  return `<a href="#" class="filter-link${active ? ' active' : ''}" data-filter-target="${escapeAttribute(
    target
  )}" data-filter-value="${escapeAttribute(value)}">${escapeHtml(label)}</a>`;
}

function renderInspectLink(ruleId: string): string {
  return `<a href="#" class="muted" data-rule-id="${escapeAttribute(ruleId)}" data-action="inspect-rule">Inspect</a>`;
}

function renderRuleInspector(detail?: IlimapRuleDetailSummary): string {
  if (!detail) {
    return '';
  }
  let content: string;
  if (!detail.available) {
    content = `<p class="empty">${escapeHtml(detail.message || 'Rule detail unavailable.')}</p>`;
  } else {
    content = [
      renderTargetDetail(detail.target),
      renderSourceDetails(detail.sources),
      renderJoinDetails(detail.joins),
      renderIdentityDetails(detail.identity),
      renderAssignmentDetails('Assignments', detail.assignments),
      renderAssignmentDetails('Defaults', detail.defaults),
      renderBagDetails(detail.bags),
      renderRefDetails(detail.refs),
      renderLossDetails(detail.losses),
      renderMetadataDetail(detail.metadata),
      renderDiagnostics(detail.diagnostics)
    ].join('');
  }
  return `<section class="rule-inspector">
    <h2>Rule ${escapeHtml(detail.ruleId)}</h2>
    ${content}
  </section>`;
}

function renderTargetDetail(target?: IlimapTargetDetailSummary): string {
  if (!target) {
    return '<p class="empty">Target: None</p>';
  }
  return `<div class="inspector-section">
    <h3>Target</h3>
    <p>${escapeHtml(target.className)} → ${renderNavName(target.outputId, target)}</p>
  </div>`;
}

function renderSourceDetails(sources: IlimapSourceDetailSummary[]): string {
  if (sources.length === 0) {
    return '<p class="empty">Sources: None</p>';
  }
  return `<div class="inspector-section">
    <h3>Sources</h3>
    <ul>${sources.map(source => `<li>
      <span>${escapeHtml(source.alias)}</span>
      <span class="detail">${escapeHtml(source.className)} from ${escapeHtml(source.inputIds.join(', '))}${source.where ? ` where ${escapeHtml(source.where)}` : ''}</span>
    </li>`).join('')}</ul>
  </div>`;
}

function renderJoinDetails(joins: IlimapJoinSummary[]): string {
  if (joins.length === 0) {
    return '';
  }
  return `<div class="inspector-section">
    <h3>Joins</h3>
    <ul>${joins.map(join => `<li>
      <span class="tag">${escapeHtml(join.type)}</span>
      <span>${escapeHtml(join.leftAlias)} ⟕ ${escapeHtml(join.rightAlias)}</span>
      <span class="detail"><code>${escapeHtml(join.condition)}</code></span>
    </li>`).join('')}</ul>
  </div>`;
}

function renderIdentityDetails(identities: IlimapExpressionSummary[]): string {
  if (identities.length === 0) {
    return '';
  }
  return `<div class="inspector-section">
    <h3>Identity</h3>
    <ul>${identities.map(id => `<li><code>${escapeHtml(id.expression)}</code></li>`).join('')}</ul>
  </div>`;
}

function renderAssignmentDetails(title: string, assignments: IlimapAssignmentSummary[]): string {
  if (assignments.length === 0) {
    return `<p class="empty">${escapeHtml(title)}: None</p>`;
  }
  return `<div class="inspector-section">
    <h3>${escapeHtml(title)}</h3>
    <ul>${assignments.map(assignment => {
      const deps = assignment.dependencies.length > 0
        ? `<span class="detail">${assignment.dependencies.map(d => escapeHtml(d.alias ? `${d.alias}.${d.member || ''}` : d.enumMapId || d.literal || d.kind)).join(', ')}</span>`
        : '';
      return `<li>
        <span>${escapeHtml(assignment.targetAttribute)}</span>
        <span class="tag tag-${escapeAttribute(assignment.kind)}">${escapeHtml(assignment.kind)}</span>
        <code>${escapeHtml(assignment.expression)}</code>
        ${deps}
      </li>`;
    }).join('')}</ul>
  </div>`;
}

function renderBagDetails(bags: IlimapBagSummary[]): string {
  if (bags.length === 0) {
    return '';
  }
  return `<div class="inspector-section">
    <h3>Bags</h3>
    ${bags.map(bag => renderBagItem(bag, 0)).join('')}
  </div>`;
}

function renderBagItem(bag: IlimapBagSummary, depth: number): string {
  const indent = '  '.repeat(depth);
  const sourceLine = bag.source
    ? `<span class="detail">from ${escapeHtml(bag.source.alias)} (${escapeHtml(bag.source.className)})` +
      `${bag.source.where ? ` where ${escapeHtml(bag.source.where)}` : ''}</span>`
    : '';
  const bagAssignments = bag.assignments.length > 0
    ? `<ul>${bag.assignments.map(a => `<li>
        <span>${escapeHtml(a.targetAttribute)}</span>
        <span class="tag tag-${escapeAttribute(a.kind)}">${escapeHtml(a.kind)}</span>
        <code>${escapeHtml(a.expression)}</code>
      </li>`).join('')}</ul>`
    : '';
  const nested = bag.nestedBags.map(n => renderBagItem(n, depth + 1)).join('');
  return `${indent}<div class="bag-item">
    <p><strong>${escapeHtml(bag.name)}</strong>` +
    (bag.targetAttribute ? ` → ${escapeHtml(bag.targetAttribute)}` : '') +
    (bag.structureClass ? ` : ${escapeHtml(bag.structureClass)}` : '') +
    (bag.mode ? ` [${escapeHtml(bag.mode)}]` : '') +
    (bag.maxItems != null ? ` max ${bag.maxItems}` : '') +
    `</p>
    ${sourceLine}
    ${bagAssignments}
    ${nested}
  </div>`;
}

function renderRefDetails(refs: IlimapRefSummary[]): string {
  if (refs.length === 0) {
    return '';
  }
  return `<div class="inspector-section">
    <h3>Refs</h3>
    <ul>${refs.map(ref => `<li>
      <span>${escapeHtml(ref.name)}</span>
      ${ref.required ? '<span class="tag warning">required</span>' : ''}
      ${ref.association ? `<span class="detail">association ${escapeHtml(ref.association)}</span>` : ''}
      ${ref.role ? `<span class="detail">role ${escapeHtml(ref.role)}</span>` : ''}
      ${ref.targetRuleId ? `<span class="detail">→ rule ${escapeHtml(ref.targetRuleId)}</span>` : ''}
      ${ref.sourceRef ? `<span class="detail">source: <code>${escapeHtml(ref.sourceRef)}</code></span>` : ''}
    </li>`).join('')}</ul>
  </div>`;
}

function renderLossDetails(losses: IlimapLossSummary[]): string {
  if (losses.length === 0) {
    return '';
  }
  return `<div class="inspector-section loss-section">
    <h3>Loss</h3>
    <ul>${losses.map(loss => `<li>
      ${loss.sourcePath ? `<code>${escapeHtml(loss.sourcePath)}</code>` : ''}
      ${loss.reasonCode ? `<span class="tag warning">${escapeHtml(loss.reasonCode)}</span>` : ''}
      ${loss.description ? `<span class="detail">${escapeHtml(loss.description)}</span>` : ''}
      ${loss.when ? `<span class="detail">when: <code>${escapeHtml(loss.when)}</code></span>` : ''}
    </li>`).join('')}</ul>
  </div>`;
}

function renderMetadataDetail(metadata?: IlimapMetadataSummary): string {
  if (!metadata) {
    return '';
  }
  const parts = [
    metadata.direction ? `Direction: ${escapeHtml(metadata.direction)}` : '',
    metadata.roundtrip ? `Roundtrip: ${escapeHtml(metadata.roundtrip)}` : '',
    metadata.lossiness ? `Lossiness: ${escapeHtml(metadata.lossiness)}` : ''
  ].filter(Boolean);
  if (parts.length === 0) {
    return '';
  }
  return `<div class="inspector-section">
    <h3>Metadata</h3>
    <p>${parts.join(' · ')}</p>
  </div>`;
}

function renderSection<T>(
  title: string,
  items: T[],
  name: (item: T) => string,
  detail: (item: T) => string,
  tag: (item: T) => string
): string {
  return `<section>
      <h2>${escapeHtml(title)}</h2>
      ${
        items.length === 0
          ? '<p class="empty">None</p>'
          : `<ul>${items
              .map(
                item => `<li>
        <div>
          <div class="name">${name(item)}</div>
          <div class="detail">${escapeHtml(detail(item))}</div>
        </div>
        ${tag(item)}
      </li>`
              )
              .join('')}</ul>`
      }
    </section>`;
}

export function navLocation(target: IlimapWithLocation): IlimapLocation | undefined {
  if (target.location && target.location.line >= 0 && target.location.character >= 0) {
    return target.location;
  }
  if (target.line !== undefined
      && target.line >= 0
      && target.character !== undefined
      && target.character >= 0) {
    return {
      line: target.line,
      character: target.character
    };
  }
  return undefined;
}

export function isValidLocation(location: IlimapLocation | undefined): location is IlimapLocation {
  return location !== undefined
    && location.line >= 0
    && location.character >= 0;
}

function renderNavName(label: string, target: IlimapWithLocation): string {
  const location = navLocation(target);
  if (!isValidLocation(location)) {
    return escapeHtml(label);
  }
  const endAttrs = location.endLine !== undefined && location.endCharacter !== undefined
    ? ` data-nav-end-line="${escapeAttribute(location.endLine)}" data-nav-end-character="${escapeAttribute(location.endCharacter)}"`
    : '';
  return `<a href="#" class="name" data-nav-line="${escapeAttribute(location.line)}" data-nav-character="${escapeAttribute(location.character)}"${endAttrs}>${escapeHtml(label)}</a>`;
}

function renderStatusBar(renderState?: MappingOverviewRenderState): string {
  const state = renderState?.refreshState ?? 'idle';
  const text = statusText(renderState);
  return `<div class="status-bar">
      <span class="status status-${escapeAttribute(state)}">${escapeHtml(text)}</span>
      <a href="#" class="refresh-link" data-action="refresh">Refresh</a>
    </div>`;
}

function statusText(renderState?: MappingOverviewRenderState): string {
  const state = renderState?.refreshState ?? 'idle';
  if (state === 'loading') {
    return 'Loading mapping overview…';
  }
  if (state === 'stale') {
    return 'Stale: document changed; refreshing…';
  }
  if (state === 'error') {
    return `Failed to refresh: ${renderState?.errorMessage || 'unknown error'}`;
  }
  if (renderState?.lastUpdated) {
    return `Last updated: ${renderState.lastUpdated}`;
  }
  return '';
}

function diagnosticsLabel(summary: IlimapMappingSummary): string {
  const parts = [
    summary.errorCount === 1 ? '1 error' : `${summary.errorCount} errors`,
    summary.warningCount === 1 ? '1 warning' : `${summary.warningCount} warnings`
  ];
  return parts.join(', ');
}

function statusClass(status: string): string {
  if (status === 'error') {
    return 'error';
  }
  if (status === 'warning') {
    return 'warning';
  }
  return '';
}
