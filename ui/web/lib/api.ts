import type { EvidenceItem, IncidentSummary } from '@/lib/types';

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

async function getJson(path: string): Promise<unknown> {
  const response = await fetch(`${API_URL}${path}`, { headers, cache: 'no-store' });
  if (!response.ok) {
    throw new ApiError(response.status, `${path} returned ${response.status}`);
  }
  return response.json() as Promise<unknown>;
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
}

function toIncident(dto: IncidentDto): IncidentSummary {
  return {
    incidentId: dto.incidentId ?? 'unknown',
    serviceId: dto.serviceId ?? 'unknown',
    severity: dto.severity ?? 'unknown',
    status: dto.status ?? 'UNKNOWN',
    title: dto.title,
    detectedAt: dto.detectedAt ?? new Date().toISOString(),
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