import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  ApiError,
  approveRemediation,
  getEvidence,
  getIncident,
  investigateIncident,
  requestRemediation,
} from '@/lib/api';
import type { Hypothesis, IncidentSummary } from '@/lib/types';

const ok = (body: unknown): Response =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });

const incident: IncidentSummary = {
  incidentId: 'inc-1',
  serviceId: 'payment-service',
  severity: 'critical',
  status: 'OPEN',
  title: 'Error rate above threshold',
  detectedAt: '2026-09-03T10:00:00Z',
};

function stubFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('api client', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('maps incident detail DTO onto IncidentSummary', async () => {
    stubFetch(
      ok({
        incidentId: 'inc-1',
        serviceId: 'payment-service',
        severity: 'critical',
        status: 'OPEN',
        title: 'Error rate above threshold',
        detectedAt: '2026-09-03T10:00:00Z',
      }),
    );

    const result = await getIncident('inc-1');

    expect(result).toEqual(incident);
  });

  it('throws ApiError with the status for a missing incident', async () => {
    stubFetch(new Response(null, { status: 404 }));

    await expect(getIncident('missing')).rejects.toEqual(expect.any(ApiError));
  });

  it('searches a window around detection and maps hits to evidence', async () => {
    const fetchMock = stubFetch(
      ok([
        {
          eventId: 'evt-t',
          source: 'payment-service',
          serviceId: 'payment-service',
          env: 'PROD',
          type: 'TRACE',
          payload: '{"spanId":"s1"}',
          traceId: 'abc123',
          timestamp: '2026-09-03T10:01:00Z',
          status: 'OPEN',
        },
      ]),
    );

    const { items, from, to } = await getEvidence(incident);

    expect(items).toHaveLength(1);
    expect(items[0]).toEqual({
      eventId: 'evt-t',
      source: 'payment-service',
      serviceId: 'payment-service',
      env: 'PROD',
      type: 'TRACE',
      payload: '{"spanId":"s1"}',
      traceId: 'abc123',
      timestamp: '2026-09-03T10:01:00Z',
      status: 'OPEN',
    });
    expect(from).toBe('2026-09-03T09:00:00.000Z');
    expect(to).toBe('2026-09-03T11:00:00.000Z');

    const calledUrl = fetchMock.mock.calls[0][0] as string;
    expect(calledUrl).toContain('/api/search/events?');
    expect(calledUrl).toContain('serviceId=payment-service');
  });

  it('maps hypothesis fields onto IncidentSummary', async () => {
    stubFetch(
      ok({
        incidentId: 'inc-1',
        serviceId: 'payment-service',
        severity: 'critical',
        status: 'HYPOTHESIS_READY',
        title: 'Error rate above threshold',
        detectedAt: '2026-09-03T10:00:00Z',
        investigatedAt: '2026-09-05T12:00:00Z',
        hypothesis: {
          statement: 'suspected deployment',
          confidence: 0.5,
          affectedServiceId: 'payment-service',
          affectedServiceVersion: '1.2.3',
          source: 'fallback',
          evidence: [{ eventId: 'ev-1', type: 'LOG', source: 'payment', summary: 'latency_ms=1200' }],
        },
      }),
    );

    const result = await getIncident('inc-1');

    expect(result.status).toBe('HYPOTHESIS_READY');
    expect(result.investigatedAt).toBe('2026-09-05T12:00:00Z');
    expect(result.hypothesis?.affectedServiceVersion).toBe('1.2.3');
    expect(result.hypothesis?.evidence).toEqual([
      { eventId: 'ev-1', type: 'LOG', source: 'payment', summary: 'latency_ms=1200' },
    ]);
  });

  it('posts an investigation to the incident endpoint and maps the hypothesis', async () => {
    const hypothesis: Hypothesis = {
      incidentId: 'inc-1',
      statement: 'suspected deployment',
      confidence: 0.5,
      affectedServiceId: 'payment-service',
      affectedServiceVersion: '1.2.3',
      source: 'fallback',
      evidence: [],
    };
    const fetchMock = stubFetch(ok(hypothesis));

    const result = await investigateIncident('inc-1');

    expect(result.statement).toBe('suspected deployment');
    expect(result.affectedServiceVersion).toBe('1.2.3');

    const [url, init] = fetchMock.mock.calls[0];
    expect(init?.method).toBe('POST');
    expect(String(url)).toContain('/api/incidents/inc-1/investigate');
    expect(init?.cache).toBe('no-store');
  });

  it('throws ApiError when the investigation is not allowed', async () => {
    stubFetch(new Response('{ "status": 409 }', { status: 409 }));

    await expect(investigateIncident('inc-1')).rejects.toEqual(expect.any(ApiError));
  });

  it('falls back to defaults for a sparse incident DTO', async () => {
    stubFetch(ok({ hypothesis: null }));

    const result = await getIncident('inc-sparse');

    expect(result.incidentId).toBe('unknown');
    expect(result.serviceId).toBe('unknown');
    expect(result.severity).toBe('unknown');
    expect(result.status).toBe('UNKNOWN');
    expect(result.hypothesis).toBeUndefined();
    expect(result.detectedAt).toMatch(/^\d{4}-\d{2}-\d{2}T/);
  });

  it('maps a partial hypothesis and tolerates non-array evidence', async () => {
    stubFetch(
      ok({
        hypothesis: { statement: 'statement-only', evidence: 'not-an-array' },
      }),
    );

    const result = await getIncident('inc-hypothesis');

    expect(result.hypothesis?.statement).toBe('statement-only');
    expect(result.hypothesis?.confidence).toBeUndefined();
    expect(result.hypothesis?.summary).toBeUndefined();
    expect(result.hypothesis?.source).toBeUndefined();
    expect(result.hypothesis?.evidence).toEqual([]);
  });

  it('maps a primitive hypothesis to undefined', async () => {
    stubFetch(ok({ hypothesis: 'plain-string' }));

    const result = await getIncident('inc-primitive');

    expect(result.hypothesis).toBeUndefined();
  });

  it('uses the current time when the incident has no detectedAt for the window', async () => {
    stubFetch(ok([]));
    const before = Date.now();

    const { from, to } = await getEvidence({ incidentId: 'x', serviceId: 'pay' } as IncidentSummary);

    expect(new Date(from).getTime()).toBeGreaterThanOrEqual(before - 3_700_000);
    expect(new Date(to).getTime()).toBeGreaterThanOrEqual(before + 3_600_000);
  });

  it('maps sparse search hits onto evidence defaults', async () => {
    stubFetch(ok([{}, { traceId: 'tr-9' }, { eventId: 'e', source: 'src' }]));

    const { items } = await getEvidence({
      detectedAt: '2026-09-03T10:00:00Z',
      serviceId: 'pay',
    } as IncidentSummary);

    expect(items[0].eventId).toBe('unknown');
    expect(items[0].source).toBe('unknown');
    expect(items[0].serviceId).toBe('unknown');
    expect(items[0].env).toBe('unknown');
    expect(items[0].type).toBe('UNKNOWN');
    expect(items[0].timestamp).toMatch(/^\d{4}-\d{2}-\d{2}T/);
    expect(items[0].status).toBeUndefined();
    expect(items[0].payload).toBeUndefined();
    expect(items[1].traceId).toBe('tr-9');
    expect(items[2].eventId).toBe('e');
    expect(items[2].source).toBe('src');
  });

  it('sends the operator subject body and a null subject for remediation flows', async () => {
    const firstMock = stubFetch(ok({ status: 'REQUESTED' }));

    await requestRemediation('inc-1', { name: 'ops', roles: ['approver'] });
    const first = firstMock.mock.calls[0];
    expect(String(first[0])).toContain('/remediation');
    expect(JSON.parse(String(first[1]?.body))).toEqual({
      subject: { name: 'ops', roles: ['approver'] },
    });

    const secondMock = stubFetch(ok({ status: 'APPROVED' }));
    await approveRemediation('inc-1');
    const second = secondMock.mock.calls[0];
    expect(String(second[0])).toContain('/approve');
    expect(JSON.parse(String(second[1]?.body))).toEqual({ subject: null });
  });
});