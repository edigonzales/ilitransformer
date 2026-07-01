import type * as vscode from 'vscode';

import type { IlimapMappingSummary, IlimapRuleSummary } from '../webview/mappingOverviewMessages';

interface PositionLike {
  line: number;
}

export class MappingOverviewSelectionSync {
  private lastNodeId: string | undefined;

  constructor(
    private readonly getCurrentSummary: () => IlimapMappingSummary | undefined,
    private readonly requestNodeAtPosition: (uri: string, line: number, character: number) => Promise<string | undefined>,
    private readonly revealInWebview: (nodeId: string) => void
  ) {}

  async handleSelectionChange(event: vscode.TextEditorSelectionChangeEvent): Promise<void> {
    const editor = event?.textEditor;
    const selection = event?.selections?.[0];
    if (!editor || !selection) {
      return;
    }
    const uri = editor.document?.uri?.toString();

    let nodeId: string | undefined;
    try {
      nodeId = await this.requestNodeAtPosition(
        uri,
        selection.active.line,
        selection.active.character
      );
    } catch {
      nodeId = undefined;
    }

    if (!nodeId) {
      nodeId = this.findNodeAtPosition(uri, selection.active);
    }

    if (!nodeId || nodeId === this.lastNodeId) {
      return;
    }
    this.lastNodeId = nodeId;
    this.revealInWebview(nodeId);
  }

  findNodeAtPosition(uri: string | undefined, position: PositionLike | undefined): string | undefined {
    if (!uri || !position || typeof position.line !== 'number') {
      return undefined;
    }
    const summary = this.getCurrentSummary();
    if (!summary || !Array.isArray(summary.rules)) {
      return undefined;
    }

    const candidates = summary.rules
      .map(rule => ({ rule, start: ruleStartLine(rule) }))
      .filter((entry): entry is { rule: IlimapRuleSummary; start: number } => entry.start >= 0)
      .sort((a, b) => a.start - b.start);
    if (candidates.length === 0) {
      return undefined;
    }

    for (let i = 0; i < candidates.length; i++) {
      const { rule, start } = candidates[i];
      const explicitEnd = ruleEndLine(rule);
      const nextStart = i + 1 < candidates.length ? candidates[i + 1].start : Number.POSITIVE_INFINITY;
      const end = explicitEnd >= start ? explicitEnd : nextStart - 1;
      if (position.line >= start && position.line <= end) {
        return `rule:${rule.id}`;
      }
    }
    return undefined;
  }
}

function ruleStartLine(rule: IlimapRuleSummary): number {
  if (rule.location && rule.location.line >= 0) {
    return rule.location.line;
  }
  if (typeof rule.line === 'number' && rule.line >= 0) {
    return rule.line;
  }
  return -1;
}

function ruleEndLine(rule: IlimapRuleSummary): number {
  if (rule.location && typeof rule.location.endLine === 'number' && rule.location.endLine >= 0) {
    return rule.location.endLine;
  }
  return -1;
}
