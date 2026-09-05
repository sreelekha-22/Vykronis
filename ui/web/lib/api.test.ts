import { afterEach, describe, expect, it, vi } from 'vitest';

import { ApiError, getEvidence, getIncident } from '@/lib/api';
import type { IncidentSummary } from '@/lib/types';

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
});