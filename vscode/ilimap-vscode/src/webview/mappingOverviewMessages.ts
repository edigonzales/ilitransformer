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
  coverageAvailable?: boolean;
  coverageMessage?: string;
  classCoverage?: IlimapCoverageClassSummary[];
  ruleCoverage?: IlimapRuleCoverageSummary[];
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

export interface IlimapCoverageClassSummary {
  outputId: string;
  className: string;
  targeted: boolean;
  ruleIds: string[];
  attributeCount: number;
  assignedAttributeCount: number;
  mandatoryMissingCount: number;
  line: number;
  character: number;
}

export interface IlimapCoverageAttributeSummary {
  name: string;
  type: string;
  cardinality: string;
  mandatory: boolean;
  assigned: boolean;
  line: number;
  character: number;
}

export interface IlimapSourceUsageSummary {
  alias: string;
  inputIds: string[];
  sourceClass: string;
  usedAttributes: string[];
  usedRoles: string[];
  line: number;
  character: number;
}

export interface IlimapRuleCoverageSummary {
  ruleId: string;
  targetOutput: string;
  targetClass: string;
  attributes: IlimapCoverageAttributeSummary[];
  sources: IlimapSourceUsageSummary[];
  refs: string[];
  directAssignmentCount: number;
  bagAssignmentCount: number;
  line: number;
  character: number;
}
