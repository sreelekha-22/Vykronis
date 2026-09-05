import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, getEvidence, getIncident, investigateIncident } from '@/lib/api';
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
});