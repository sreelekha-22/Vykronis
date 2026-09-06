import type { EvidenceItem, Hypothesis, IncidentSummary, OperatorSubject } from '@/lib/types';

const API_URL: string = process.env.VYKRONIS_API_URL ?? 'http://localhost:8080';

const headers = { 'Content-Type': 'application/json' };

export class ApiError extends Error {
  constructor(
    readonly status: number,
    message: string,
  ) {
    super(message);
    this.name = 'ApiError';
  }
}

async function jsonRequest(path: string, init: RequestInit = {}): Promise<unknown> {
  const response = await fetch(`${API_URL}${path}`, {
    ...init,
    headers,
    cache: 'no-store',
  });
  if (!response.ok) {
    throw new ApiError(response.status, `${path} returned ${response.status}`);
  }
  return response.json() as Promise<unknown>;
}

async function getJson(path: string): Promise<unknown> {
  return jsonRequest(path);
}

interface IncidentDto {
  incidentId?: string;
  serviceId?: string;
  severity?: string;
  status?: string;
  title?: string;
  detectedAt?: string;
  windowStart?: string;
  windowEnd?: string;
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
  hypothesis?: unknown;
}

function toHypothesis(raw: unknown): Hypothesis | undefined {
  if (raw === null || raw === undefined || typeof raw !== 'object') {
    return undefined;
  }
  const h = raw as {
    statement?: unknown;
    summary?: unknown;
    confidence?: unknown;
    affectedServiceId?: unknown;
    affectedServiceVersion?: unknown;
    source?: unknown;
    evidence?: unknown;
  };
  const evidence = Array.isArray(h.evidence)
    ? h.evidence.map((item) => {
        const e = item as { eventId?: unknown; type?: unknown; source?: unknown; summary?: unknown };
        return {
          eventId: String(e.eventId ?? 'unknown'),
          type: String(e.type ?? 'UNKNOWN'),
          source: String(e.source ?? 'unknown'),
          summary: e.summary === undefined ? undefined : String(e.summary),
        };
      })
    : [];
  return {
    statement: h.statement === undefined ? undefined : String(h.statement),
    summary: h.summary === undefined ? undefined : String(h.summary),
    confidence: h.confidence === undefined ? undefined : Number(h.confidence),
    affectedServiceId: h.affectedServiceId === undefined ? undefined : String(h.affectedServiceId),
    affectedServiceVersion:
      h.affectedServiceVersion === undefined ? undefined : String(h.affectedServiceVersion),
    source: h.source === undefined ? undefined : String(h.source),
    evidence,
  };
}

function toIncident(dto: IncidentDto): IncidentSummary {
  return {
    incidentId: dto.incidentId ?? 'unknown',
    serviceId: dto.serviceId ?? 'unknown',
    severity: dto.severity ?? 'unknown',
    status: dto.status ?? 'UNKNOWN',
    title: dto.title,
    detectedAt: dto.detectedAt ?? new Date().toISOString(),
    investigatedAt: dto.investigatedAt,
    env: dto.env,
    policyDecision: dto.policyDecision,
    requestedAt: dto.requestedAt,
    approvedAt: dto.approvedAt,
    approvedBy: dto.approvedBy,
    remediationCommandId: dto.remediationCommandId,
    remediationOutcome: dto.remediationOutcome,
    remediationCompletedAt: dto.remediationCompletedAt,
    resolvedAt: dto.resolvedAt,
    hypothesis: toHypothesis(dto.hypothesis),
  };
}

interface SearchHitDto {
  eventId?: string;
  source?: string;
  serviceId?: string;
  env?: string;
  type?: string;
  payload?: string;
  traceId?: string;
  timestamp?: string;
  status?: string;
}

function toEvidence(hit: SearchHitDto): EvidenceItem {
  return {
    eventId: hit.eventId ?? hit.source ?? 'unknown',
    source: hit.source ?? 'unknown',
    serviceId: hit.serviceId ?? 'unknown',
    env: hit.env ?? 'unknown',
    type: hit.type ?? 'UNKNOWN',
    payload: hit.payload,
    traceId: hit.traceId,
    timestamp: hit.timestamp ?? new Date().toISOString(),
    status: hit.status,
  };
}

export async function getIncident(incidentId: string): Promise<IncidentSummary> {
  return toIncident((await getJson(`/api/incidents/${incidentId}`)) as IncidentDto);
}

export async function getEvidence(
  incident: IncidentSummary,
): Promise<{ items: EvidenceItem[]; from: string; to: string }> {
  const detected = incident.detectedAt ? new Date(incident.detectedAt) : new Date();
  const from = new Date(detected.getTime() - 3600_000).toISOString();
  const to = new Date(detected.getTime() + 3600_000).toISOString();
  const params = new URLSearchParams({ from, to, serviceId: incident.serviceId });
  const hits = (await getJson(`/api/search/events?${params.toString()}`)) as SearchHitDto[];
  return { items: hits.map(toEvidence), from, to };
}

export async function investigateIncident(incidentId: string): Promise<Hypothesis> {
  return jsonRequest(`/api/incidents/${incidentId}/investigate`, { method: 'POST' }) as Promise<Hypothesis>;
}

export const DEMO_OPERATOR: OperatorSubject = { name: 'ops', roles: ['approver'], service: false };

function operatorBody(subject: OperatorSubject | undefined): string {
  return JSON.stringify({ subject: subject ?? null });
}

export async function requestRemediation(
  incidentId: string,
  subject?: OperatorSubject,
): Promise<IncidentSummary> {
  return jsonRequest(`/api/incidents/${incidentId}/remediation`, {
    method: 'POST',
    body: operatorBody(subject),
  }) as Promise<IncidentSummary>;
}

export async function approveRemediation(
  incidentId: string,
  subject?: OperatorSubject,
): Promise<IncidentSummary> {
  return jsonRequest(`/api/incidents/${incidentId}/approve`, {
    method: 'POST',
    body: operatorBody(subject),
  }) as Promise<IncidentSummary>;
}