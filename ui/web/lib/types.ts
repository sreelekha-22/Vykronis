export type EvidenceType = 'TRACE' | 'LOG' | 'METRIC' | 'JFR' | 'DEPLOYMENT' | string;

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

export interface IncidentSummary {
  incidentId: string;
  serviceId: string;
  severity: string;
  status: string;
  title?: string;
  detectedAt: string;
}

export interface EvidenceRef {
  label: string;
  href: string;
}