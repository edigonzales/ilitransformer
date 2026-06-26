export const mappingSummaryRequest = 'ilimap/mappingSummary';
export const ruleDetailRequest = 'ilimap/ruleDetail';

export interface IlimapLocation {
  line: number;
  character: number;
  endLine?: number;
  endCharacter?: number;
}

export interface IlimapWithLocation {
  line?: number;
  character?: number;
  location?: IlimapLocation;
}

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
  sourceUsage?: IlimapSourceClassUsageSummary[];
}

export interface IlimapMappingInputSummary {
  id: string;
  path: string;
  model: string;
  format: string;
  nodeId?: string;
  location?: IlimapLocation;
  line?: number;
  character?: number;
}

export interface IlimapMappingOutputSummary {
  id: string;
  path: string;
  model: string;
  format: string;
  nodeId?: string;
  location?: IlimapLocation;
  line?: number;
  character?: number;
}

export interface IlimapEnumMapSummary {
  id: string;
  entryCount: number;
  nodeId?: string;
  location?: IlimapLocation;
  line?: number;
  character?: number;
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
  nodeId?: string;
  location?: IlimapLocation;
  line?: number;
  character?: number;
}

export interface IlimapDiagnosticSummary {
  code: string;
  severity: string;
  message: string;
  line: number;
  character: number;
  nodeId?: string;
  location?: IlimapLocation;
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
  nodeId?: string;
  location?: IlimapLocation;
}

export interface IlimapCoverageAttributeSummary {
  name: string;
  type: string;
  cardinality: string;
  mandatory: boolean;
  assigned: boolean;
  line: number;
  character: number;
  nodeId?: string;
  location?: IlimapLocation;
  status?:
    | 'mapped'
    | 'constant'
    | 'computed'
    | 'enumMap'
    | 'default'
    | 'bag'
    | 'ref'
    | 'missing'
    | 'documentedLoss'
    | 'unknown'
    | string;
  expression?: string;
  sourceSummary?: string;
}

export interface IlimapSourceUsageSummary {
  alias: string;
  inputIds: string[];
  sourceClass: string;
  usedAttributes: string[];
  usedRoles: string[];
  line: number;
  character: number;
  nodeId?: string;
  location?: IlimapLocation;
}

export interface IlimapSourceClassUsageSummary {
  inputIds: string[];
  sourceClass: string;
  aliases: string[];
  attributes: IlimapSourceAttributeUsageSummary[];
  roles: IlimapSourceAttributeUsageSummary[];
  location?: IlimapLocation;
}

export interface IlimapSourceAttributeUsageSummary {
  name: string;
  kind: 'attribute' | 'role' | string;
  status: 'used' | 'unused' | 'identity' | 'where' | 'join' | 'loss' | 'unknown' | string;
  usedBy: IlimapUsageReferenceSummary[];
  location?: IlimapLocation;
}

export interface IlimapUsageReferenceSummary {
  ruleId: string;
  context: 'assign' | 'where' | 'join' | 'identity' | 'ref' | 'bag' | 'loss' | string;
  targetAttribute?: string;
  location?: IlimapLocation;
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
  nodeId?: string;
  location?: IlimapLocation;
}

export interface IlimapRuleDetailParams {
  uri: string;
  ruleId: string;
}

export interface IlimapRuleDetailSummary {
  available: boolean;
  message: string;
  ruleId: string;
  nodeId?: string;
  location?: IlimapLocation;
  target?: IlimapTargetDetailSummary;
  sources: IlimapSourceDetailSummary[];
  joins: IlimapJoinSummary[];
  identity: IlimapExpressionSummary[];
  assignments: IlimapAssignmentSummary[];
  defaults: IlimapAssignmentSummary[];
  bags: IlimapBagSummary[];
  refs: IlimapRefSummary[];
  losses: IlimapLossSummary[];
  metadata?: IlimapMetadataSummary;
  diagnostics: IlimapDiagnosticSummary[];
}

export interface IlimapTargetDetailSummary {
  outputId: string;
  className: string;
  location?: IlimapLocation;
}

export interface IlimapSourceDetailSummary {
  alias: string;
  inputIds: string[];
  className: string;
  where?: string;
  location?: IlimapLocation;
}

export interface IlimapJoinSummary {
  type: 'inner' | 'left' | string;
  leftAlias: string;
  rightAlias: string;
  condition: string;
  location?: IlimapLocation;
}

export interface IlimapExpressionSummary {
  expression: string;
  location?: IlimapLocation;
}

export interface IlimapAssignmentSummary {
  targetAttribute: string;
  expression: string;
  kind: 'copy' | 'constant' | 'computed' | 'enumMap' | 'default' | 'null' | 'unknown' | string;
  dependencies: IlimapExpressionDependencySummary[];
  location?: IlimapLocation;
}

export interface IlimapExpressionDependencySummary {
  kind: 'sourceAttribute' | 'sourceRole' | 'enumMap' | 'function' | 'constant' | 'unknown' | string;
  alias?: string;
  member?: string;
  sourceClass?: string;
  enumMapId?: string;
  functionName?: string;
  literal?: string;
  location?: IlimapLocation;
}

export interface IlimapBagSummary {
  name: string;
  targetAttribute?: string;
  structureClass?: string;
  mode?: string;
  maxItems?: number;
  source?: IlimapSourceDetailSummary;
  assignments: IlimapAssignmentSummary[];
  nestedBags: IlimapBagSummary[];
  location?: IlimapLocation;
}

export interface IlimapRefSummary {
  name: string;
  association?: string;
  role?: string;
  required: boolean;
  targetRuleId?: string;
  sourceRef?: string;
  location?: IlimapLocation;
}

export interface IlimapLossSummary {
  sourcePath?: string;
  reasonCode?: string;
  description?: string;
  when?: string;
  location?: IlimapLocation;
}

export interface IlimapMetadataSummary {
  direction?: string;
  roundtrip?: string;
  lossiness?: string;
  location?: IlimapLocation;
}
