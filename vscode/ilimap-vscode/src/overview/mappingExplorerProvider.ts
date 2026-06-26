import * as vscode from 'vscode';

import type { IlimapLocation, IlimapMappingSummary } from '../webview/mappingOverviewMessages';

let instance: MappingExplorerProvider | undefined;

export function setMappingExplorerInstance(provider: MappingExplorerProvider): void {
  instance = provider;
}

export function getMappingExplorerInstance(): MappingExplorerProvider | undefined {
  return instance;
}

export type MappingExplorerItemKind =
  | 'root'
  | 'inputs'
  | 'input'
  | 'outputs'
  | 'output'
  | 'rules'
  | 'rule'
  | 'coverage'
  | 'coverageClass'
  | 'problems'
  | 'diagnostic';

export class MappingExplorerItem extends vscode.TreeItem {
  constructor(
    public readonly kind: MappingExplorerItemKind,
    label: string,
    public readonly nodeId?: string,
    public readonly sourceLocation?: IlimapLocation,
    collapsibleState?: vscode.TreeItemCollapsibleState
  ) {
    super(label, collapsibleState);
  }
}

export class MappingExplorerProvider implements vscode.TreeDataProvider<MappingExplorerItem> {
  private readonly onDidChangeTreeDataEmitter = new vscode.EventEmitter<MappingExplorerItem | undefined>();
  readonly onDidChangeTreeData = this.onDidChangeTreeDataEmitter.event;

  private summary?: IlimapMappingSummary;
  private currentUri?: string;

  refresh(summary?: IlimapMappingSummary, uri?: string): void {
    this.summary = summary;
    if (uri !== undefined) {
      this.currentUri = uri;
    }
    this.onDidChangeTreeDataEmitter.fire(undefined);
  }

  getCurrentUri(): string | undefined {
    return this.currentUri;
  }

  getTreeItem(element: MappingExplorerItem): vscode.TreeItem {
    return element;
  }

  async getChildren(element?: MappingExplorerItem): Promise<MappingExplorerItem[]> {
    if (!element) {
      return this.buildRootItems();
    }
    switch (element.kind) {
      case 'inputs':
        return this.buildInputItems();
      case 'outputs':
        return this.buildOutputItems();
      case 'rules':
        return this.buildRuleItems();
      case 'coverage':
        return this.buildCoverageItems();
      case 'problems':
        return this.buildProblemItems();
      default:
        return [];
    }
  }

  private buildRootItems(): MappingExplorerItem[] {
    if (!this.summary) {
      return [new MappingExplorerItem('root', 'No mapping overview loaded', undefined, undefined)];
    }

    const items: MappingExplorerItem[] = [];

    const inputsItem = new MappingExplorerItem(
      'inputs',
      `Inputs (${this.summary.inputCount})`,
      undefined,
      undefined,
      vscode.TreeItemCollapsibleState.Collapsed
    );
    items.push(inputsItem);

    const outputsItem = new MappingExplorerItem(
      'outputs',
      `Outputs (${this.summary.outputCount})`,
      undefined,
      undefined,
      vscode.TreeItemCollapsibleState.Collapsed
    );
    items.push(outputsItem);

    const rulesItem = new MappingExplorerItem(
      'rules',
      `Rules (${this.summary.ruleCount})`,
      undefined,
      undefined,
      vscode.TreeItemCollapsibleState.Collapsed
    );
    items.push(rulesItem);

    if (this.summary.coverageAvailable) {
      const classCount = this.summary.classCoverage?.length ?? 0;
      items.push(
        new MappingExplorerItem(
          'coverage',
          `Coverage (${classCount})`,
          undefined,
          undefined,
          vscode.TreeItemCollapsibleState.Collapsed
        )
      );
    }

    const problemCount = (this.summary.errorCount ?? 0) + (this.summary.warningCount ?? 0);
    if (problemCount > 0) {
      items.push(
        new MappingExplorerItem(
          'problems',
          `Problems (${problemCount})`,
          undefined,
          undefined,
          vscode.TreeItemCollapsibleState.Collapsed
        )
      );
    }

    return items;
  }

  private buildInputItems(): MappingExplorerItem[] {
    return (this.summary?.inputs ?? []).map(input => {
      const location = this.resolveLocation(input.location, input.line, input.character);
      const item = new MappingExplorerItem(
        'input',
        `${input.id} · ${input.format}`,
        input.nodeId,
        location
      );
      item.tooltip = `Input ${input.id}\nPath: ${input.path}\nModel: ${input.model}\nFormat: ${input.format}`;
      item.description = input.path;
      if (this.currentUri && location) {
        item.command = this.buildRevealCommand(this.currentUri, location);
      }
      return item;
    });
  }

  private buildOutputItems(): MappingExplorerItem[] {
    return (this.summary?.outputs ?? []).map(output => {
      const location = this.resolveLocation(output.location, output.line, output.character);
      const item = new MappingExplorerItem(
        'output',
        `${output.id} · ${output.format}`,
        output.nodeId,
        location
      );
      item.tooltip = `Output ${output.id}\nPath: ${output.path}\nModel: ${output.model}\nFormat: ${output.format}`;
      item.description = output.path;
      if (this.currentUri && location) {
        item.command = this.buildRevealCommand(this.currentUri, location);
      }
      return item;
    });
  }

  private buildRuleItems(): MappingExplorerItem[] {
    return (this.summary?.rules ?? []).map(rule => {
      const location = this.resolveLocation(rule.location, rule.line, rule.character);
      const item = new MappingExplorerItem(
        'rule',
        `${rule.id} · ${rule.targetClass}`,
        rule.nodeId,
        location
      );
      item.tooltip = `Rule ${rule.id}\nTarget: ${rule.targetOutput}/${rule.targetClass}\nSources: ${rule.sourceCount}\nAssignments: ${rule.assignmentCount}\nStatus: ${rule.status}`;
      item.description = rule.status;
      if (this.currentUri && location) {
        item.command = this.buildRevealCommand(this.currentUri, location);
      }
      if (rule.status === 'error') {
        item.iconPath = new vscode.ThemeIcon('error');
      } else if (rule.status === 'warning') {
        item.iconPath = new vscode.ThemeIcon('warning');
      }
      return item;
    });
  }

  private buildCoverageItems(): MappingExplorerItem[] {
    return (this.summary?.classCoverage ?? []).map(coverage => {
      const location = this.resolveLocation(coverage.location, coverage.line, coverage.character);
      const attributes = `${coverage.assignedAttributeCount}/${coverage.attributeCount}`;
      const label = coverage.mandatoryMissingCount > 0
        ? `${coverage.className} · ${attributes} · ${coverage.mandatoryMissingCount} missing`
        : `${coverage.className} · ${attributes}`;
      const item = new MappingExplorerItem(
        'coverageClass',
        label,
        coverage.nodeId,
        location
      );
      item.tooltip = `Class ${coverage.className}\nAssigned: ${attributes}\nMandatory missing: ${coverage.mandatoryMissingCount}\nRules: ${coverage.ruleIds.join(', ')}`;
      if (this.currentUri && location) {
        item.command = this.buildRevealCommand(this.currentUri, location);
      }
      if (coverage.mandatoryMissingCount > 0) {
        item.iconPath = new vscode.ThemeIcon('warning');
      }
      return item;
    });
  }

  private buildProblemItems(): MappingExplorerItem[] {
    const diagnostics = this.summary?.diagnostics ?? [];
    const filtered = diagnostics
      .filter(d => d.severity === 'error' || d.severity === 'warning')
      .slice(0, 200);
    return filtered.map(d => {
      const location = d.location ?? (
        d.line >= 0 && d.character >= 0
          ? { line: d.line, character: d.character }
          : undefined
      );
      const item = new MappingExplorerItem(
        'diagnostic',
        `${d.code}: ${d.message}`,
        d.nodeId,
        location
      );
      item.tooltip = `[${d.severity}] ${d.code}: ${d.message}\nLine: ${d.line}, Char: ${d.character}`;
      item.description = `${d.severity} L${d.line}`;
      if (this.currentUri && location) {
        item.command = this.buildRevealCommand(this.currentUri, location);
      }
      if (d.severity === 'error') {
        item.iconPath = new vscode.ThemeIcon('error');
      } else {
        item.iconPath = new vscode.ThemeIcon('warning');
      }
      return item;
    });
  }

  private resolveLocation(
    location?: IlimapLocation,
    line?: number,
    character?: number
  ): IlimapLocation | undefined {
    if (location) {
      return location;
    }
    if (line !== undefined && character !== undefined && line >= 0 && character >= 0) {
      return { line, character };
    }
    return undefined;
  }

  private buildRevealCommand(uri: string, location: IlimapLocation): vscode.Command {
    return {
      command: 'ilimap.mappingExplorer.revealInEditor',
      title: 'Reveal in Editor',
      arguments: [uri, location]
    };
  }
}
