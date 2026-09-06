import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';

import { RemediationPanel } from '@/components/RemediationPanel';
import type { IncidentSummary, OperatorSubject } from '@/lib/types';

const operator: OperatorSubject = { name: 'ops', roles: ['approver'], service: false };

const base: IncidentSummary = {
  incidentId: 'inc-2',
  serviceId: 'payment-service',
  severity: 'critical',
  status: 'AWAITING_APPROVAL',
  title: 'Error rate above threshold',
  detectedAt: '2026-09-03T10:00:00Z',
  env: 'PROD',
  policyDecision: 'REQUIRE_APPROVAL',
};

const ok = (body: unknown): Response =>
  new Response(JSON.stringify(body), { status: 200, headers: { 'Content-Type': 'application/json' } });

describe('RemediationPanel', () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders an Approve action on a prod incident awaiting approval and posts to the approve endpoint', async () => {
    const next: IncidentSummary = {
      ...base,
      status: 'AUTO_APPROVED',
      approvedBy: 'ops',
      approvedAt: '2026-09-06T10:00:00Z',
    };
    const fetchMock = vi.fn().mockResolvedValue(ok(next));
    vi.stubGlobal('fetch', fetchMock);

    render(<RemediationPanel incident={base} operator={operator} />);

    expect(screen.getByTestId('remediation-env')).toHaveTextContent('PROD');
    expect(screen.getByText(/REQUIRE_APPROVAL/)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: /^Approve$/ }));

    await waitFor(() =>
      expect(screen.getByTestId('remediation-status')).toHaveTextContent('Remediation auto-approved by ops'),
    );

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(init?.method).toBe('POST');
    expect(String(url)).toContain('/api/incidents/inc-2/approve');
    const body = JSON.parse(String(init?.body));
    expect(body.subject.name).toBe('ops');
  });

  it('does not render an Approve action on a non-prod auto-approved incident', () => {
    render(
      <RemediationPanel
        incident={{ ...base, env: 'DEV', status: 'AUTO_APPROVED', policyDecision: 'ALLOW' }}
        operator={operator}
      />,
    );

    expect(screen.queryByRole('button', { name: /Approve/ })).not.toBeInTheDocument();
    expect(screen.getByTestId('remediation-env')).toHaveTextContent('DEV');
  });

  it('requests remediation on a HYPOTHESIS_READY prod incident, then exposes the Approve action', async () => {
    const ready: IncidentSummary = { ...base, status: 'HYPOTHESIS_READY' };
    const awaiting: IncidentSummary = { ...base, status: 'AWAITING_APPROVAL' };
    const fetchMock = vi.fn().mockResolvedValueOnce(ok(awaiting));
    vi.stubGlobal('fetch', fetchMock);

    render(<RemediationPanel incident={ready} operator={operator} />);

    fireEvent.click(screen.getByRole('button', { name: /Request remediation/ }));

    await waitFor(() => expect(screen.getByRole('button', { name: /^Approve$/ })).toBeInTheDocument());

    expect(fetchMock).toHaveBeenCalledTimes(1);
    const [url, init] = fetchMock.mock.calls[0];
    expect(init?.method).toBe('POST');
    expect(String(url)).toContain('/api/incidents/inc-2/remediation');
    const body = JSON.parse(String(init?.body));
    expect(body.subject.roles).toContain('approver');
  });

  it('surfaces an approval failure to the user', async () => {
    const fetchMock = vi
      .fn()
      .mockResolvedValue(new Response(JSON.stringify({ status: 409, message: 'not allowed' }), { status: 409 }));
    vi.stubGlobal('fetch', fetchMock);

    render(<RemediationPanel incident={base} operator={operator} />);

    fireEvent.click(screen.getByRole('button', { name: /^Approve$/ }));

    await waitFor(() =>
      expect(screen.getByRole('alert')).toHaveTextContent('Approval failed (409)'),
    );
  });
});