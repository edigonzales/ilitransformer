import type { IlimapMappingSummary } from '../webview/mappingOverviewMessages';

export interface MappingReportOptions {
  includeMermaid?: boolean;
  includeDiagnostics?: boolean;
  includeCoverage?: boolean;
  includeSourceUsage?: boolean;
}

const DEFAULT_OPTIONS: MappingReportOptions = {
  includeMermaid: true,
  includeDiagnostics: true,
  includeCoverage: true,
  includeSourceUsage: true
};

export function renderMappingReportMarkdown(
  summary: IlimapMappingSummary,
  options?: MappingReportOptions
): string {
  const opts = options ? { ...DEFAULT_OPTIONS, ...options } : DEFAULT_OPTIONS;
  const sections: string[] = [];

  sections.push(renderHeader(summary));
  sections.push(renderSummarySection(summary));

  sections.push(renderInputsSection(summary));
  sections.push(renderOutputsSection(summary));
  sections.push(renderRulesSection(summary));

  if (opts.includeMermaid && summary.flowNodes?.length) {
    sections.push(renderMermaidSection(summary));
  }

  if (opts.includeCoverage) {
    sections.push(renderCoverageSection(summary));
  }

  if (opts.includeSourceUsage) {
    sections.push(renderSourceUsageSection(summary));
  }

  if (opts.includeDiagnostics) {
    sections.push(renderDiagnosticsSection(summary));
  }

  return sections.join('\n\n') + '\n';
}

function renderHeader(summary: IlimapMappingSummary): string {
  const name = mdInline(summary.mappingName || 'mapping');
  return `# ilimap Mapping Report: ${name}`;
}

function renderSummarySection(summary: IlimapMappingSummary): string {
  const totalDiagnostics =
    summary.errorCount + summary.warningCount + summary.informationCount + summary.hintCount;
  return `## Summary

- Inputs: ${summary.inputCount}
- Outputs: ${summary.outputCount}
- Rules: ${summary.ruleCount}
- Enum Maps: ${summary.enumMapCount}
- Bags: ${summary.bagCount}
- Refs: ${summary.refCount}
- Diagnostics: ${totalDiagnostics}
- Errors: ${summary.errorCount}
- Warnings: ${summary.warningCount}`;
}

function renderInputsSection(summary: IlimapMappingSummary): string {
  const lines: string[] = ['## Inputs'];
  if (summary.inputs.length === 0) {
    lines.push('');
    lines.push('None');
    return lines.join('\n');
  }
  lines.push('');
  lines.push('| ID | Path | Model | Format |');
  lines.push('| -- | ---- | ----- | ------ |');
  for (const input of summary.inputs) {
    lines.push(
      `| ${mdTable(input.id)} | ${mdTable(input.path)} | ${mdTable(input.model)} | ${mdTable(input.format)} |`
    );
  }
  return lines.join('\n');
}

function renderOutputsSection(summary: IlimapMappingSummary): string {
  const lines: string[] = ['## Outputs'];
  if (summary.outputs.length === 0) {
    lines.push('');
    lines.push('None');
    return lines.join('\n');
  }
  lines.push('');
  lines.push('| ID | Path | Model | Format |');
  lines.push('| -- | ---- | ----- | ------ |');
  for (const output of summary.outputs) {
    lines.push(
      `| ${mdTable(output.id)} | ${mdTable(output.path)} | ${mdTable(output.model)} | ${mdTable(output.format)} |`
    );
  }
  return lines.join('\n');
}

function renderRulesSection(summary: IlimapMappingSummary): string {
  const lines: string[] = ['## Rules'];
  if (summary.rules.length === 0) {
    lines.push('');
    lines.push('None');
    return lines.join('\n');
  }
  lines.push('');
  lines.push('| ID | Target Output | Target Class | Status | Sources | Assignments | Bags | Refs |');
  lines.push('| -- | ------------- | ------------ | ------ | ------- | ----------- | ---- | ---- |');
  for (const rule of summary.rules) {
    lines.push(
      `| ${mdTable(rule.id)} | ${mdTable(rule.targetOutput)} | ${mdTable(rule.targetClass)} | ${mdTable(rule.status)} | ${rule.sourceCount} | ${rule.assignmentCount} | ${rule.bagCount} | ${rule.refCount} |`
    );
  }
  return lines.join('\n');
}

function renderMermaidSection(summary: IlimapMappingSummary): string {
  const lines: string[] = ['## Flow', '', '```mermaid', 'flowchart LR'];
  const nodeMap = new Map<string, string>();

  for (const node of summary.flowNodes ?? []) {
    const safeId = mermaidId(node.nodeId);
    nodeMap.set(node.nodeId, safeId);
    const label = mermaidLabel(node.label, node.detail);
    const shape = mermaidShape(node.kind);
    lines.push(`  ${safeId}${shape}${label}`);
  }

  for (const edge of summary.flowEdges ?? []) {
    const from = nodeMap.get(edge.fromNodeId) ?? mermaidId(edge.fromNodeId);
    const to = nodeMap.get(edge.toNodeId) ?? mermaidId(edge.toNodeId);
    const label = edge.label ? `|${mermaidLabel(edge.label)}|` : '';
    lines.push(`  ${from} -->${label} ${to}`);
  }

  lines.push('```');
  return lines.join('\n');
}

function renderCoverageSection(summary: IlimapMappingSummary): string {
  const lines: string[] = ['## Coverage'];

  if (summary.coverageAvailable === false) {
    lines.push('');
    lines.push(mdInline(summary.coverageMessage || 'Coverage is not available.'));
    return lines.join('\n');
  }

  if (summary.ruleCoverage?.length) {
    lines.push('');
    lines.push('### Rule Coverage');
    for (const rc of summary.ruleCoverage ?? []) {
      const ruleId = mdInline(rc.ruleId);
      lines.push('');
      lines.push(`#### ${ruleId}`);
      lines.push('');
      lines.push(
        `Target: ${mdInline(rc.targetOutput)} / ${mdInline(rc.targetClass)}  ` +
        `Direct assignments: ${rc.directAssignmentCount}  Bags: ${rc.bagAssignmentCount}`
      );

      if (rc.attributes.length > 0) {
        lines.push('');
        lines.push('| Attribute | Status | Type | Mandatory | Source / Expression |');
        lines.push('| --------- | ------ | ---- | --------- | ------------------- |');
        for (const attr of rc.attributes) {
          const status = attr.status ?? derivedCoverageStatus(attr);
          const mandatoryStr = attr.mandatory ? 'yes' : 'no';
          const sourceStr = attr.expression || attr.sourceSummary || (attr.assigned ? '—' : '');
          lines.push(
            `| ${mdTable(attr.name)} | ${mdTable(status)} | ${mdTable(attr.type)} | ${mandatoryStr} | ${mdTable(sourceStr)} |`
          );
        }
      }

      if (rc.refs.length > 0) {
        lines.push('');
        lines.push(`Refs: ${rc.refs.map(r => mdInline(r)).join(', ')}`);
      }
    }
  }

  if (summary.classCoverage?.length) {
    lines.push('');
    lines.push('### Class Coverage');
    lines.push('');
    lines.push('| Class | Output | Targeted | Attributes | Assigned | Missing Mandatory |');
    lines.push('| ----- | ------ | -------- | ---------- | -------- | ----------------- |');
    for (const cc of summary.classCoverage ?? []) {
      const targetedStr = cc.targeted ? 'yes' : 'no';
      lines.push(
        `| ${mdTable(cc.className)} | ${mdTable(cc.outputId)} | ${targetedStr} | ${cc.attributeCount} | ${cc.assignedAttributeCount} | ${cc.mandatoryMissingCount} |`
      );
    }
  }

  if (!summary.ruleCoverage?.length && !summary.classCoverage?.length) {
    lines.push('');
    lines.push('No coverage data is available.');
  }

  return lines.join('\n');
}

function renderSourceUsageSection(summary: IlimapMappingSummary): string {
  const lines: string[] = ['## Source Usage'];

  if (summary.sourceUsage?.length) {
    for (const sc of summary.sourceUsage ?? []) {
      lines.push('');
      lines.push(`### ${mdInline(sc.sourceClass)}`);
      if (sc.aliases.length) {
        lines.push(`Aliases: ${sc.aliases.map(a => mdInline(a)).join(', ')}`);
      }
      if (sc.inputIds.length) {
        lines.push(`Inputs: ${sc.inputIds.map(i => mdInline(i)).join(', ')}`);
      }

      const attrs = sc.attributes ?? [];
      const roles = sc.roles ?? [];
      const allMembers = [...attrs, ...roles];

      if (allMembers.length > 0) {
        lines.push('');
        lines.push('| Member | Kind | Status |');
        lines.push('| ------ | ---- | ------ |');
        for (const m of allMembers) {
          lines.push(`| ${mdTable(m.name)} | ${mdTable(m.kind)} | ${mdTable(m.status)} |`);
        }
      }
    }
    return lines.join('\n');
  }

  const ruleCoverage = summary.ruleCoverage ?? [];
  if (ruleCoverage.length > 0) {
    for (const rc of ruleCoverage) {
      for (const source of rc.sources) {
        lines.push('');
        lines.push(`### ${mdInline(source.sourceClass || source.alias)}`);
        lines.push(`Rule: ${mdInline(rc.ruleId)}`);
        if (source.alias) {
          lines.push(`Alias: ${mdInline(source.alias)}`);
        }
        if (source.usedAttributes.length) {
          lines.push(`Used attributes: ${source.usedAttributes.map(a => mdInline(a)).join(', ')}`);
        }
        if (source.usedRoles.length) {
          lines.push(`Used roles: ${source.usedRoles.map(r => mdInline(r)).join(', ')}`);
        }
      }
    }
    return lines.join('\n');
  }

  lines.push('');
  lines.push('No source usage data is available.');
  return lines.join('\n');
}

function renderDiagnosticsSection(summary: IlimapMappingSummary): string {
  const lines: string[] = ['## Diagnostics'];

  if (summary.diagnostics.length === 0) {
    lines.push('');
    lines.push('No diagnostics.');
    return lines.join('\n');
  }

  lines.push('');
  lines.push('| Severity | Code | Message | Rule / Input / Output |');
  lines.push('| -------- | ---- | ------- | --------------------- |');
  for (const d of summary.diagnostics) {
    const owner = [
      d.ruleId ? `rule:${d.ruleId}` : '',
      d.inputId ? `input:${d.inputId}` : '',
      d.outputId ? `output:${d.outputId}` : ''
    ]
      .filter(Boolean)
      .join(' ') || '—';
    lines.push(
      `| ${mdTable(d.severity)} | ${mdTable(d.code)} | ${mdTable(d.message)} | ${mdTable(owner)} |`
    );
  }
  return lines.join('\n');
}

function mdTable(value: string): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\|/g, '\\|')
    .replace(/\n/g, ' ');
}

function mdInline(value: string): string {
  return String(value ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/\|/g, '\\|')
    .replace(/\n/g, ' ');
}

function mermaidId(nodeId: string): string {
  return nodeId.replace(/[^a-zA-Z0-9_-]/g, '_');
}

function mermaidLabel(label: string, detail?: string): string {
  let escaped = String(label ?? '')
    .replace(/[&"'`<>\n]/g, '')
    .replace(/\n/g, ' ');
  if (detail) {
    const escapedDetail = String(detail)
      .replace(/[&"'`<>\n]/g, '')
      .replace(/\n/g, ' ');
    escaped += `<br/>${escapedDetail}`;
  }
  return `["${escaped}"]`;
}

function mermaidShape(kind: string): string {
  return '[';
}

function derivedCoverageStatus(attr: {
  assigned: boolean;
  mandatory: boolean;
}): string {
  if (attr.assigned) {
    return 'mapped';
  }
  if (attr.mandatory) {
    return 'missing';
  }
  return 'unassigned';
}
