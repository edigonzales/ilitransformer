export const mappingSummaryRequest = 'ilimap/mappingSummary';

export interface IlimapMappingSummaryParams {
  uri: string;
}

export interface IlimapMappingSummary {
  available: boolean;
  message: string;
  mappingName: string;
  inputCount: number;
  outputCount: number;
  ruleCount: number;
  enumMapCount: number;
  bagCount: number;
  refCount: number;
  errorCount: number;
  warningCount: number;
  informationCount: number;
  hintCount: number;
  inputs: IlimapMappingInputSummary[];
  outputs: IlimapMappingOutputSummary[];
  enumMaps: IlimapEnumMapSummary[];
  rules: IlimapRuleSummary[];
  diagnostics: IlimapDiagnosticSummary[];
}

export interface IlimapMappingInputSummary {
  id: string;
  path: string;
  model: string;
  format: string;
}

export interface IlimapMappingOutputSummary {
  id: string;
  path: string;
  model: string;
  format: string;
}

export interface IlimapEnumMapSummary {
  id: string;
  entryCount: number;
}

export interface IlimapRuleSummary {
  id: string;
  targetOutput: string;
  targetClass: string;
  sourceCount: number;
  assignmentCount: number;
  bagCount: number;
  refCount: number;
  status: 'ok' | 'warning' | 'error' | string;
}

export interface IlimapDiagnosticSummary {
  code: string;
  severity: string;
  message: string;
  line: number;
  character: number;
}
