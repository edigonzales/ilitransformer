import type {
  IlimapDiagnosticSummary,
  IlimapEnumMapSummary,
  IlimapMappingInputSummary,
  IlimapMappingOutputSummary,
  IlimapMappingSummary,
  IlimapRuleSummary
} from './mappingOverviewMessages';

export function renderMappingOverviewHtml(summary: IlimapMappingSummary, nonce: string): string {
  const title = summary.available ? summary.mappingName || 'mapping' : 'Mapping unavailable';
  const diagnosticText = diagnosticsLabel(summary);
  return `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'nonce-${escapeAttribute(nonce)}';">
  <title>ILIMAP Mapping Overview</title>
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
  </style>
</head>
<body>
  <main>
    <div class="header">
      <div>
        <h1>ILIMAP Mapping Overview</h1>
        <div class="subtitle">${escapeHtml(title)}</div>
      </div>
      <div class="muted">${escapeHtml(diagnosticText)}</div>
    </div>
    ${summary.available ? renderAvailableSummary(summary) : renderUnavailable(summary)}
  </main>
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

function renderAvailableSummary(summary: IlimapMappingSummary): string {
  return `${renderMetrics(summary)}
    <div class="sections">
      ${renderInputs(summary.inputs)}
      ${renderOutputs(summary.outputs)}
      ${renderEnumMaps(summary.enumMaps)}
      ${renderRules(summary.rules)}
      ${renderDiagnostics(summary.diagnostics)}
    </div>`;
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
    input => input.id,
    input => [input.path, input.model, input.format].filter(Boolean).join(' · '),
    () => ''
  );
}

function renderOutputs(outputs: IlimapMappingOutputSummary[]): string {
  return renderSection(
    'Outputs',
    outputs,
    output => output.id,
    output => [output.path, output.model, output.format].filter(Boolean).join(' · '),
    () => ''
  );
}

function renderEnumMaps(enumMaps: IlimapEnumMapSummary[]): string {
  return renderSection(
    'Enum Maps',
    enumMaps,
    enumMap => enumMap.id,
    enumMap => `${enumMap.entryCount} entries`,
    () => ''
  );
}

function renderRules(rules: IlimapRuleSummary[]): string {
  return renderSection(
    'Rules',
    rules,
    rule => rule.id,
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
    diagnostic => diagnostic.message,
    diagnostic => `${diagnostic.code} · ${diagnostic.severity} · ${diagnostic.line + 1}:${diagnostic.character + 1}`,
    diagnostic => `<span class="tag ${statusClass(diagnostic.severity)}">${escapeHtml(diagnostic.severity)}</span>`
  );
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
          <div class="name">${escapeHtml(name(item))}</div>
          <div class="detail">${escapeHtml(detail(item))}</div>
        </div>
        ${tag(item)}
      </li>`
              )
              .join('')}</ul>`
      }
    </section>`;
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
