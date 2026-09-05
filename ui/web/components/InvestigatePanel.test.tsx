import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { InvestigatePanel } from '@/components/InvestigatePanel';
import type { Hypothesis, IncidentSummary } from '@/lib/types';

const incident: IncidentSummary = {
  incidentId: 'inc-1',
  serviceId: 'payment-service',
  severity: 'critical',
  status: 'OPEN',
  title: 'Error rate above threshold',
  detectedAt: '2026-09-03T10:00:00Z',
};

const hypothesis: Hypothesis = {
  incidentId: 'inc-1',
  statement: 'suspected deployment',
  confidence: 0.5,
  affectedServiceId: 'payment-service',
  affectedServiceVersion: '1.2.3',
  source: 'fallback',
  evidence: [],
};

const ok = (body: unknown): Response =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });

function stubFetch(response: Response) {
  const fetchMock = vi.fn().mockResolvedValue(response);
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

describe('InvestigatePanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders an Investigate action for an open incident and posts to the incident endpoint', async () => {
    const fetchMock = stubFetch(ok(hypothesis));

    render(<InvestigatePanel incident={incident} />);

    const button = screen.getByRole('button', { name: /^Investigate$/ });
    fireEvent.click(button);

    await waitFor(() => expect(screen.getByText('suspected deployment')).toBeInTheDocument());

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(init?.method).toBe('POST');
    expect(String(url)).toContain('/api/incidents/inc-1/investigate');
  });

  it('shows the stored hypothesis for a HYPOTHESIS_READY incident and allows re-investigation', () => {
    render(
      <InvestigatePanel
        incident={{ ...incident, status: 'HYPOTHESIS_READY', hypothesis, investigatedAt: '2026-09-05T12:00:00Z' }}
      />,
    );

    expect(screen.getByText('suspected deployment')).toBeInTheDocument();
    expect(screen.getByTestId('affected-version')).toHaveTextContent('1.2.3');
    expect(screen.getByRole('button', { name: /^Investigate$/ })).toBeInTheDocument();
  });

  it('surfaces the investigation failure to the user', async () => {
    stubFetch(new Response(JSON.stringify({ status: 409, message: 'not allowed' }), { status: 409 }));

    render(<InvestigatePanel incident={incident} />);

    fireEvent.click(screen.getByRole('button', { name: /^Investigate$/ }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Investigation failed (409)'),
    );
  });
});