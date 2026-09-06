export type EvidenceType = 'TRACE' | 'LOG' | 'METRIC' | 'JFR' | 'DEPLOYMENT' | string;
export type HypothesisSource = 'ai' | 'fallback' | string;

export interface EvidenceItem {
  eventId: string;
  source: string;
  serviceId: string;
  env: string;
  type: EvidenceType;
  payload?: string;
  traceId?: string;
  timestamp: string;
  status?: string;
}

export interface HypothesisEvidence {
  eventId: string;
  type: EvidenceType;
  source: string;
  summary?: string;
}

export interface Hypothesis {
  incidentId?: string;
  statement?: string;
  summary?: string;
  confidence?: number;
  affectedServiceId?: string;
  affectedServiceVersion?: string;
  source?: HypothesisSource;
  evidence: HypothesisEvidence[];
}

export interface OperatorSubject {
  name: string;
  roles: string[];
  service?: boolean;
}

export interface IncidentSummary {
  incidentId: string;
  serviceId: string;
  severity: string;
  status: string;
  title?: string;
  detectedAt: string;
  hypothesis?: Hypothesis;
  investigatedAt?: string;
  env?: string;
  policyDecision?: string;
  requestedAt?: string;
  approvedAt?: string;
  approvedBy?: string;
  remediationCommandId?: string;
  remediationOutcome?: string;
  remediationCompletedAt?: string;
  resolvedAt?: string;
}

export interface EvidenceRef {
  label: string;
  href: string;
}