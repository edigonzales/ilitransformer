import type * as vscode from 'vscode';

import type { IlimapMappingSummary, IlimapRuleDetailSummary } from './mappingOverviewMessages';

export interface MappingOverviewPanelState {
  panel: vscode.WebviewPanel;
  uri: string;
  documentVersion?: number;
  summary?: IlimapMappingSummary;
  ruleDetailsById: Map<string, IlimapRuleDetailSummary>;
  activeRuleId?: string;
  lastUpdated?: string;
  refreshTimer?: ReturnType<typeof setTimeout>;
  loading: boolean;
  disposed: boolean;
  disposables: vscode.Disposable[];
}

export interface MappingOverviewRefreshOptions {
  reason: 'open' | 'save' | 'change' | 'activeEditor' | 'manual';
  debounceMs?: number;
}

export interface MappingOverviewRenderState {
  refreshState: 'idle' | 'loading' | 'stale' | 'error';
  lastUpdated?: string;
  errorMessage?: string;
}
